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
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * DISTRIBUTED RATE LIMIT SERVICE IMPLEMENTATION
 * 
 * Implements IRateLimitService using Bucket4j and Lettuce Redis CAS proxy manager.
 * Stores token buckets in Redis with an automatic 10-minute expiration after write TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements IRateLimitService {

    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final RateLimitProperties rateLimitProperties;
    private ProxyManager<byte[]> proxyManager;

    @PostConstruct
    public void init() {
        // LettuceBasedProxyManager handles thread-safe CAS operations in Redis via Lua scripts
        this.proxyManager = LettuceBasedProxyManager.builderFor(redisConnection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10L)))
                .build();
    }

    @Override
    public boolean tryConsume(String clientKey, Role role) {
        BucketConfiguration bucketConfiguration = getConfigurationForRole(role);
        byte[] keyBytes = generatedRedisKey(clientKey, role).getBytes();

        // Uses a Supplier to build the bucket only when it is initialized
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

        // estimateAbilityToConsume(1): Read-only probe check to prevent the double-consumption bug
        EstimationProbe probe = proxyManager.builder()
                .build(keyBytes, () -> bucketConfiguration)
                .estimateAbilityToConsume(1);

        return Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    }

    private String generatedRedisKey(String clientKey, Role role) {
        String rolePrefix = (role == null) ? "ANONYMOUS" : role.name();
        return "rate-limit:" + rolePrefix + ":" + clientKey;
    }

    private BucketConfiguration getConfigurationForRole(Role role) {
        String roleKey = (role == null) ? "ANONYMOUS" : role.name();
        RateLimitProperties.Rule rule = rateLimitProperties.getLimits().get(roleKey);

        if (rule == null) {
            log.warn("No rule found for role {}. Falling back to ANONYMOUS limits.", roleKey);
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
