# Bucket4j: The Role-Based Distributed Rate-Limiting Engine
**Document Version:** 1.2  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Distributed Rate-Limiting Service

The core logic of our rate limiter is isolated inside the service layer, keeping it independent of the HTTP interceptor logic. 

We use the **Bucket4j** library (`bucket4j_jdk17-lettuce:8.19.0`) with the **Lettuce** extension to store the token bucket state directly in Redis. This allows us to scale our application across multiple servers while enforcing consistent rate limits.

---

## 2. Implementing Non-Deprecated APIs (Version 8.19.0+)

Older Bucket4j code bases use deprecated static builder methods. We implement the latest Bucket4j Lettuce API design patterns:

1.  **`LettuceBasedProxyManager.builderFor(connection).withExpirationStrategy(...)`**: Initializes the Compare-And-Swap (CAS) manager using the Lettuce stateful byte connection and configures expiration TTL strategies.
2.  **`proxyManager.builder().build(key, Supplier)`**: Replaces deprecated static methods. Passing a `Supplier<BucketConfiguration>` ensures the configuration is only fetched/computed when creating a new bucket (if it doesn't exist in Redis yet).
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
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
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
```

---

## 4. Concurrency Synchronization & Compare-And-Swap (CAS)

In high-concurrency systems, multiple requests from the same user can hit our servers simultaneously. We must synchronize the token count updates to prevent race conditions. We evaluate the progression of synchronization strategies:

```text
               CONCURRENCY STRATEGY COMPARISON
               
  1. Synchronized Blocks   ──►  Locks local JVM thread. Fails in multi-node clusters.
  2. Database Locks        ──►  Locks DB row. Slow, exhausts connection pools.
  3. Distributed CAS       ──►  Lock-free. Sequential single-threaded Lua script in Redis.
```

### A. The Evolution of Concurrency Control

#### Level 1: Naive JVM locking (`synchronized` or `ReentrantLock`)
*   **How it works:** Locks Java threads locally within a single server instance.
*   **Why it fails:** In a multi-node deployment, Server A's JVM lock has no effect on Server B. Furthermore, blocking threads puts them to sleep, costing heavy OS context-switching overhead.

#### Level 2: Database Pessimistic Locking (`SELECT ... FOR UPDATE`)
*   **How it works:** Forces the database (PostgreSQL) to lock the target rate-limiting row.
*   **Why it fails:** Under a flood of requests, other database connections are forced to wait. This quickly exhausts your database connection pool, leading to overall application latency and deadlock risks.

#### Level 3: Distributed CAS (Compare-And-Swap) — Our Choice
*   **What is CAS:** An atomic instruction used to achieve lock-free synchronization. It works by taking three arguments: a memory location, the expected old value, and the new value. The location is updated **only** if its current value matches the expected old value.
*   **How it works in Redis:** Since Redis is single-threaded, it processes incoming commands sequentially. Bucket4j leverages Lettuce to send a Lua script containing the CAS logic to Redis. 
    1. The script reads the current bucket state (version, tokens, timestamp).
    2. It compares it to the expected state.
    3. If they match, it updates the values. If a concurrent request changed the state first, the CAS check fails, and the script retries the operation on the new state.
*   **Why we choose it:** It is **lock-free and non-blocking**. No thread is ever put to sleep or forced to wait for database locks. If a malicious client floods the system, requests are checked and rejected in microseconds, keeping our servers extremely responsive under high load.

---

## 5. Consumption Probe vs. Estimation Probe

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

## 6. Client Key vs. Redis Key

*   **`clientKey` (In-App Context):** The raw identifier parsed from the request (e.g. client IP `"192.168.1.100"` or User UUID `"usr_98af"`).
*   **`redisKey` (In-Database Context):** The final namespace string built by the service: `rate-limit:ROLE:clientKey`.
*   **Redis Key Partitioning:** In Redis, keys are stored as binary bytes. The value contains the serialized double values for tokens remaining and the last consumption timestamp.
