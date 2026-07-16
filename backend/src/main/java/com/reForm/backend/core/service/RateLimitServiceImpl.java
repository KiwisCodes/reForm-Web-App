package com.reForm.backend.core.service;

import com.reForm.backend.core.config.RateLimitProperties;
import com.reForm.backend.core.port.IRateLimitService;
import com.reForm.backend.user.entity.Role;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements IRateLimitService {
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final RateLimitProperties rateLimitProperties;
    private ProxyManager<byte[]> proxyManager;

    @PostConstruct
    public void init() {
        // Replacement for the deprecated LettuceBasedProxyManager.builderFor(redisConnection).build():
        // We use Bucket4jLettuce.casBasedBuilder to create our proxy manager.
        // CAS (Compare-And-Swap) ensures that when multiple backend instances query the same key,
        // updates are made atomically inside Redis without locks.
        // We configure an expiration strategy of 10 minutes so that idle rate-limit keys are automatically
        // evicted from the Redis database, preventing memory leaks over time.
        this.proxyManager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10L)))
                .build();
    }

    @Override
    public boolean tryConsume(String clientKey, Role role) {
        BucketConfiguration bucketConfiguration = getConfigurationForRole(role);
        byte[] keyBytes = generatedRedisKey(clientKey, role).getBytes();

        // Replacement for the deprecated build(keyBytes, bucketConfiguration):
        // We pass a Supplier () -> bucketConfiguration. This ensures the configuration
        // is only fetched/computed when creating a new bucket (if it doesn't exist in Redis yet).
        // tryConsumeAndReturnRemaining(1) atomically reads the bucket, refills it, consumes 1 token, 
        // writes the state back to Redis, and returns a ConsumptionProbe.
        //
        // WHAT IS A ConsumptionProbe AND WHY DO WE NEED IT:
        // A ConsumptionProbe is the "transaction receipt" returned by Bucket4j after a token-consumption attempt.
        // We need it because rate limiting is more than a simple true/false check. The probe holds crucial state metadata:
        // - isConsumed(): Indicates if a token was successfully taken (allowed = true, blocked = false).
        // - getRemainingTokens(): Tells us how many tokens are left, useful for custom API headers and metrics monitoring.
        // - getNanosToWaitForRefill(): Tells us how long to make the user wait if blocked, which we use to set standard Retry-After headers.
        ConsumptionProbe probe = proxyManager.builder()
                .build(keyBytes, () -> bucketConfiguration)
                .tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            log.debug("Token consumed successfully for key: [{}]. Remaining tokens: {}",
                    clientKey, probe.getRemainingTokens());
            return true;
        } else {
            log.warn("Rate limit breached for key: [{}]. Blocked request.", clientKey);
            return false;
        }
    }

    @Override
    public long getWaitTimeInSeconds(String clientKey, Role role) {
        BucketConfiguration bucketConfiguration = getConfigurationForRole(role);
        byte[] keyBytes = generatedRedisKey(clientKey, role).getBytes();

        // WHAT IS AN EstimationProbe AND WHY DO WE NEED IT:
        // An EstimationProbe is a read-only transaction receipt.
        // We need it to fetch the wait time without calling tryConsume() again, which would
        // accidentally consume a newly refilled token.
        // Calling estimateAbilityToConsume(1) queries the bucket's current state and calculates
        // the refill delay without modifying the token count in Redis.
        EstimationProbe probe = proxyManager.builder()
                .build(keyBytes, () -> bucketConfiguration)
                .estimateAbilityToConsume(1);

        return Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    }

    /**
     * CLIENT KEY vs. REDIS KEY EXPLANATION:
     * 
     * 1. clientKey (Raw User Input):
     *    - Represents the client identity.
     *    - For unauthenticated users: clientKey is the client IP (e.g. "192.168.1.1").
     *    - For authenticated users: clientKey is their User UUID (e.g. "usr_abc123").
     * 
     * 2. redisKey (Formatted Storage Key):
     *    - Formatted as: "rate-limit:ROLE:clientKey"
     *    - Namespace segregation prevents key collisions. If a user logs in, they transition to 
     *      their role-based bucket limit, instead of continuing to use the IP-based anonymous bucket.
     * 
     * 3. How it operates in Redis:
     *    - Lettuce writes the redisKey as binary bytes.
     *    - Redis maps this key to a byte array value representing the bucket status (tokens left + timestamp).
     *    - Bucket4j executes atomic checks via Lua scripts inside the Redis engine.
     */
    private String generatedRedisKey(String clientKey, Role role) {
        String rolePrefix = (role == null) ? "ANONYMOUS" : role.name();
        return "rate-limit:" + rolePrefix + ":" + clientKey;
    }

    private BucketConfiguration getConfigurationForRole(Role role) {
        String roleKey = (role == null) ? "ANONYMOUS" : role.name();
        RateLimitProperties.Rule rule = rateLimitProperties.getLimits().get(roleKey);

        if(rule == null) {
            log.warn("No rule found for role {}", roleKey);
            rule = rateLimitProperties.getLimits().get("ANONYMOUS");
        }

        return BucketConfiguration.builder()
                .addLimit(Bandwidth
                        .builder()
                        .capacity(rule.getCapacity())
                        .refillIntervally(
                                rule.getRefillTokens(),
                                Duration.ofSeconds(rule.getRefillSeconds()))
                        .build())
                .build();
    }
}
