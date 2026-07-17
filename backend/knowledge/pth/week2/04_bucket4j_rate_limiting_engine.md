# Bucket4j: The Role-Based Distributed Rate-Limiting Engine
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Distributed Rate-Limiting Service

The core logic of our rate limiter is isolated inside the service layer, keeping it independent of the HTTP interceptor logic. 

We use the **Bucket4j** library with the **Lettuce** extension to store the token bucket state directly in Redis. This allows us to scale our application across multiple servers while enforcing consistent rate limits.

---

## 2. Implementing Non-Deprecated APIs (Version 8.19.0+)

Older Bucket4j code bases use deprecated builder patterns that create coupling issues. We implement the latest API design patterns:

1.  **`Bucket4jLettuce.casBasedBuilder(connection)`**: Replaces the deprecated `LettuceBasedProxyManager.builderFor` method. It initializes the Compare-And-Swap (CAS) manager using the Lettuce stateful byte connection.
2.  **`proxyManager.builder().build(key, Supplier)`**: Replaces the deprecated static `.build(key, config)` method. Passing a `Supplier<BucketConfiguration>` ensures the configuration is only fetched/computed when creating a new bucket (if it doesn't exist in Redis yet).
3.  **`ExpirationAfterWriteStrategy`**: Automatically assigns a Time-To-Live (TTL) to keys in Redis. If a client is inactive for 10 minutes, Redis automatically deletes the rate-limiting key, preventing cache memory leaks.

---

## 3. The Production-Ready Code: `RateLimitServiceImpl.java`

Here is our verified service implementation:

```java
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
import io.github.bucket4j.redis.lettuce.cas.Bucket4jLettuce;
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
        // Bucket4jLettuce.casBasedBuilder handles thread-safe CAS operations in Redis
        this.proxyManager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10L)))
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
```

---

## 4. Consumption Probe vs. Estimation Probe

Bucket4j distinguishes between **action** operations and **read-only inspection** operations to avoid race conditions and key state changes.

### A. ConsumptionProbe (Used in `tryConsume`)
*   **What it is:** The transaction report returned when you attempt to consume tokens.
*   **Why we need it:** It changes state. If tokens are available, it decrements the token count in Redis and records the success.
*   **Key methods:**
    *   `isConsumed()`: Returns `true` if the request is permitted.
    *   `getRemainingTokens()`: Tells us the remaining capacity.

### B. EstimationProbe (Used in `getWaitTimeInSeconds`)
*   **What it is:** A read-only lookup probe.
*   **Why we need it:** It does not change state. It inspects the bucket's refill metadata in Redis and calculates the remaining wait time *without* consuming any tokens. This prevents the **double-consumption bug**, where checking the wait time of a blocked user would consume a newly refilled token.
*   **Key methods:**
    *   `getNanosToWaitForRefill()`: Returns the remaining backoff duration in nanoseconds.

---

## 5. Client Key vs. Redis Key

*   **`clientKey` (In-App Context):** The raw identifier parsed from the request (e.g. client IP `"192.168.1.100"` or User UUID `"usr_98af"`).
*   **`redisKey` (In-Database Context):** The final namespace string built by the service: `rate-limit:ROLE:clientKey`.
*   **Redis Key Partitioning:** In Redis, keys are stored as binary bytes. The value contains the serialized double values for tokens remaining and the last consumption timestamp.
