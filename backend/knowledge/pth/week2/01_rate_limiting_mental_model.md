# Rate Limiting & Distributed State: Backtrack Mental Model
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Core Problem: Resource Exhaustion & Abuse
### ❓ Question:
*"My application is public-facing. What happens if a malicious script submits 10,000 forms per second, or opens millions of voice WebSockets? How do I prevent this from crashing my database or draining my expensive Gemini API credits?"*

### 💡 The Solution:
We need a gateway traffic throttle to limit the frequency of requests a client can make in a given timeframe. This is **Rate Limiting**.

```text
 [ Incoming Flood ] ──> [ GATEKEEPER ] ──( Allowed )──> [ App Resources ]
                              │
                        ( Exceeded )
                              ▼
                        [ HTTP 429 ]
```

---

## 2. Sub-Problem: Which Algorithm Decides "Allowed" vs. "Blocked"?
### ❓ Question:
*"How should the server decide if a request is allowed? Can I just block someone if they make more than 1 request per second?"*

### ⚠️ The Conflict:
Real-world users do not click buttons or speak in mathematically perfect intervals. They work in **bursts**—saving 3 form fields in a second, uploading 2 files, then remaining silent for minutes. A flat, rigid limit (e.g. 1 req/sec) creates a frustrating user experience.

### 💡 The Solution:
We choose the **Token Bucket Algorithm** (native to Bucket4j).
*   **How it solves the problem:** A virtual bucket holds a max capacity of tokens (e.g., 30). Each request consumes 1 token. If the bucket is empty, the request is blocked. Tokens are replenished continuously at a fixed rate (e.g., 5 tokens every 10 seconds).
*   **Why it's chosen:** It permits users to execute sudden bursts of traffic (up to the bucket's max capacity) while strictly enforcing a predictable average throughput over time.

---

## 3. Sub-Problem: Multi-Server Scaling (State Sharing)
### ❓ Question:
*"If I scale my Spring Boot backend horizontally (running 3 instances behind a load balancer), how do they share the token counts? If Server A blocks an IP, how do Server B and Server C know?"*

### ⚠️ The Conflict:
By default, standard rate limiters store token states in the JVM heap memory (local RAM). If Server A receives 10 requests from an IP, Server B remains unaware. The user can bypass rate limits by routing requests across different server nodes.

### 💡 The Solution:
We build a **Distributed Rate Limiter** by moving the bucket state storage outside of the backend server's JVM memory. We need a centralized, ultra-fast, in-memory database: **Redis**.

```text
               [ Load Balancer ]
               /       │       \
              ▼        ▼        ▼
          [App Node] [App Node] [App Node]
              \        │        /
               ▼       ▼       ▼
             [ Centralized Redis ] (Shares Rate Limits)
```

---

## 4. Sub-Problem: How Does Java Speak to Redis?
### ❓ Question:
*"I have a running Redis server on port 6379, and my Spring Boot application. How do I establish a socket connection and execute Redis commands in Java?"*

### 💡 The Solution:
We use the **Lettuce Client Driver**.
*   **Why Lettuce:** Lettuce is a high-performance, thread-safe Java Redis client built on top of Netty. It supports asynchronous, reactive, and non-blocking TCP socket communication. Spring Boot's data-redis starter imports Lettuce as the default driver.

---

## 5. Sub-Problem: Atomic Read-and-Update Operations
### ❓ Question:
*"If two requests from the same user hit Server A and Server B at the exact same millisecond, they will both query Redis. How do we prevent a race condition where both servers see 1 token left, consume it simultaneously, and let both requests pass (giving the user 1 free request)?"*

### 💡 The Solution:
We use Bucket4j's **`ProxyManager`** via **`Bucket4jLettuce`** utilizing the Compare-And-Swap (CAS) transaction model.
*   **How it solves the problem:** Instead of the Java code reading the token count, subtracting one, and saving it back (which takes multiple network steps), Bucket4j compiles the operation into a **Lua script** and sends it to Redis. 
*   **Under the Hood:** Redis is single-threaded. It executes the Lua script atomically: it reads, refills, checks, and decrements the token key in a single, isolated execution step. No other network connection can interrupt the transaction.

---

## 6. Sub-Problem: Eliminating Serialization Overhead
### ❓ Question:
*"Should we save the rate limit bucket states in Redis as JSON strings?"*

### ⚠️ The Conflict:
Converting Java objects to JSON text (serialization) and parsing it back (deserialization) on every HTTP request takes CPU time. If your server processes 5,000 requests per second, JSON mapping creates severe latency.

### 💡 The Solution:
We use **Raw Binary Serialization (`byte[], byte[]` connection)**.
*   **How it works:** We configure Lettuce to connect using the `ByteArrayCodec.INSTANCE` bean in [RedisConfig.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/core/config/RedisConfig.java). 
*   **Why:** Bucket4j serializes its state directly into compact byte arrays. Passing raw bytes over TCP bypasses all JSON parsing, maximizing network throughput.

---

## 7. Sub-Problem: Where Does the Check Run in the Request Pipeline?
### ❓ Question:
*"Do I call the RateLimitService inside my Controller classes?"*

### ⚠️ The Conflict:
Placing checks in the Controller violates the Single Responsibility Principle. Furthermore, by the time a request hits a Controller, Spring has already spent CPU cycles parsing JSON bodies, binding variables, and validating inputs. If the request is blocked, all of that work is wasted.

### 💡 The Solution:
We implement a Spring MVC **`HandlerInterceptor`** (`RateLimitInterceptor`).
*   **Why:** The `preHandle()` method executes *before* the request reaches the controller. If the rate limit is exceeded, we return `false` from `preHandle()` to abort the request immediately, protecting downstream application logic.

```text
 HTTP Request ──> [ Filters (JWT) ] ──> [ DispatcherServlet ] ──> [ Interceptor (preHandle) ] ──> [ Controller ]
                                                                        │
                                                                   (Blocked 429)
                                                                        ▼
                                                                  Halt Pipeline
```

---

## 8. Sub-Problem: How Do We Authenticate vs. Rate Limit?
### ❓ Question:
*"Does the rate limiter execute before or after checking the user's login token (JWT)?"*

### 💡 The Solution:
The **JWT Security Filter runs first**, and the **Rate Limit Interceptor runs second**.
*   **Why:** Spring's Security Filters operate at the Servlet container boundary (outside Spring MVC). They inspect the JWT token, validate it, and write the user's ID and Role to the `SecurityContext`.
*   **The Benefit:** When the request enters Spring MVC and triggers `RateLimitInterceptor.preHandle()`, the interceptor can inspect `SecurityContextHolder` to immediately find the user's Role and ID, applying the correct bucket limits.

---

## 9. Sub-Problem: Distinguishing Client Keys
### ❓ Question:
*"Who gets rate limited? If two users are behind the same office router, they share an external IP. Will they block each other?"*

### 💡 The Solution:
We implement **Role-Based Key Namespacing**:
1.  **Unauthenticated Users:** If the user is not logged in (e.g. accessing a public form), we identify them by their **IP Address** and use the `ANONYMOUS` config key. 
2.  **Authenticated Users:** If the user is logged in, we identify them by their unique **User UUID** and apply their role's limit (e.g., `FORM_BUILDER`).
3.  **The Formatting:** Keys are named `rate-limit:ROLE:identifier` (e.g., `rate-limit:FORM_FILLER:usr-123`). This isolates users completely, preventing an IP-based flood from blocking logged-in builders.

---

## 10. Sub-Problem: Avoiding Key Leakage in Redis
### ❓ Question:
*"If thousands of different guest IPs hit our public link over a year, Redis will collect thousands of rate-limiting keys. How do we prevent Redis from running out of memory?"*

### 💡 The Solution:
Configure an **Expiration Strategy** on the Proxy Manager:
```java
Bucket4jLettuce.casBasedBuilder(redisConnection)
    .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10L)))
```
*   **How it works:** When a bucket's keys are created or updated, Redis assigns them a TTL (Time-To-Live). If a guest IP does not make another request within 10 minutes, Redis automatically deletes the key, keeping cache memory clean.

---

## 11. Sub-Problem: The Double-Consumption Wait Time Bug
### ❓ Question:
*"If a user is blocked, I want to show them how long they have to wait (Retry-After header). If I call `tryConsume` again to get the wait time, will that take a token if one just refilled?"*

### 💡 The Solution:
Use **`EstimationProbe`** rather than `ConsumptionProbe` for read-only queries:
```java
proxyManager.builder()
    .build(keyBytes, () -> config)
    .estimateAbilityToConsume(1);
```
*   **How it works:** Calling `estimateAbilityToConsume(1)` inspects the token refill estimation timestamp in Redis but bypasses token consumption, guaranteeing that querying the wait time does not penalize the user.
