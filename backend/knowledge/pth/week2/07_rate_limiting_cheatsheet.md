# Distributed Rate Limiting: Reference Cheatsheet
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Libraries & Framework Components

| Component / Library | Why We Need It | Core Capabilities | Where It Is Used |
| :--- | :--- | :--- | :--- |
| **`bucket4j-core`** | High-performance token-bucket rate limiter. | Manages tokens, refill intervals, and estimation logic in memory. | Core rate-limiting engine. |
| **`bucket4j-lettuce`** | Redis integration for Bucket4j. | Coordinates remote Redis storage calls using Lettuce async channels. | Distributed state coordination. |
| **`lettuce-core`** | Non-blocking, thread-safe Java Redis client driver. | Manages Redis connections, TCP sockets, and Netty thread pools. | Backend connection layer. |

---

## 2. Core Project Classes

| Class Name | Package/Location | What It Does | Why We Need It |
| :--- | :--- | :--- | :--- |
| **`RedisConfig`** | `core.config` | Configures Lettuce, `RedisTemplate`, and Netty lifecycles. | Establishes connection pool and custom serialization. |
| **`WebMvcConfig`** | `core.config` | Implements `WebMvcConfigurer` to register interceptors. | Registers `RateLimitInterceptor` for target path matching. |
| **`RateLimitProperties`** | `core.config` | Binds `app.rate-limiting.limits` configuration maps. | Dynamic limits loading following Open/Closed Principle. |
| **`IRateLimitService`** | `core.port` | SOLID contract interface for rate limiting. | Decouples Spring MVC from the Bucket4j library. |
| **`RateLimitServiceImpl`**| `core.service` | Implementation of `IRateLimitService`. | Generates Redis keys and manages token bucket execution. |
| **`RateLimitInterceptor`**| `core.interceptor` | Extends `HandlerInterceptor` to inspect HTTP traffic. | Boundary gatekeeper. Aborts execution and returns 429 errors. |

---

## 3. Bucket4j API Classes & Methods

| Class / Method | Signature / Details | What It Does | Why We Need It |
| :--- | :--- | :--- | :--- |
| **`Bucket4jLettuce`** | `casBasedBuilder(StatefulRedisConnection)` | Initializes a Compare-And-Swap (CAS) proxy builder. | Atomic, lock-free distributed synchronization. |
| **`ProxyManager`** | `builder().build(byte[] key, Supplier)` | Resolves or creates a bucket proxy associated with a key. | Abstraction layer mapping local calls to Redis state. |
| **`ExpirationAfterWriteStrategy`** | `basedOnTimeForRefillingBucketUpToMax(Duration)`| Sets a Time-To-Live (TTL) expiration on Redis keys. | Automatically cleans up idle keys to prevent memory leaks. |
| **`ConsumptionProbe`** | Returned by `.tryConsumeAndReturnRemaining(1)` | Represents a token-consumption execution report. | Tells us if a request is allowed and how many tokens remain. |
| **`EstimationProbe`** | Returned by `.estimateAbilityToConsume(1)` | Represents a read-only query report. | Fetches wait times without consuming tokens (avoids bugs). |
| **`probe.isConsumed()`** | Returns `boolean` | Checks if a token was successfully taken. | Direct validation condition for allowing requests. |
| **`probe.getRemainingTokens()`**| Returns `long` | Returns the remaining token balance. | Useful for API logging, headers, and traffic diagnostics. |
| **`probe.getNanosToWaitForRefill()`**| Returns `long` (nanoseconds) | Returns duration until a token is replenished. | Used to set the HTTP standard `Retry-After` header. |

---

## 4. Spring Security & Web MVC API

| Class / Method | What It Does | Why We Need It | Where It Is Used |
| :--- | :--- | :--- | :--- |
| **`SecurityContextHolder`** | Static access to the current security context. | Resolves authenticated session details. | `RateLimitInterceptor` |
| **`Authentication`** | Represents the token of an authenticated request. | Checks if authenticated (`isAuthenticated()`). | `RateLimitInterceptor` |
| **`auth.getPrincipal()`** | Returns the identity object of the caller. | We check against `"anonymousUser"` to detect guests. | `RateLimitInterceptor` |
| **`auth.getName()`** | Returns the unique string identity of the user. | Retrieves the User Database UUID to use as the client key. | `RateLimitInterceptor` |
| **`HandlerInterceptor.preHandle()`**| Executes before the request reaches the controller. | Centralized entry-gate traffic inspection. | `RateLimitInterceptor` |
| **`X-Forwarded-For`** | HTTP header preserving the client source IP. | Extracts client IPs through Nginx/Cloudflare reverse proxies. | `RateLimitInterceptor` |
| **`Retry-After`** | Standard HTTP header (contains backoff seconds). | Informs the client when they are permitted to retry. | `RateLimitInterceptor` |
| **`ObjectMapper`** | Serializes Java objects to JSON text. | Manually outputs JSON error payloads inside interceptors. | `RateLimitInterceptor` |
