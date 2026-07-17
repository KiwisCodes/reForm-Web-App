# rate-limiting: The Problem Landscape & Architectural Choices
**Document Version:** 1.1  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Production Threat Landscape

When exposing web applications to the public internet (especially ones containing generative AI capabilities like reForm's conversational interview block), we face three distinct systemic threats:

```text
                    THE PUBLIC WEB SYSTEM UNDER ATTACK
                    
      [ ATTACK A: DDoS Floods ] ────┐
                                    ▼
      [ ATTACK B: Credential brute ] ──► [ reForm Server ] ──► [ CPU Exhaustion ]
                                    ▲
      [ ATTACK C: API Key Scraping ] ┘
```

1.  **DDoS & Resource Exhaustion (Denial of Service):** A malicious script sending 5,000 rapid requests to public pages can exhaust Tomcat's thread pool, rendering the app unresponsive for legitimate users.
2.  **API Credit Drainage:** Conversational features run on the Gemini engine, which charges per token. An unthrottled loop calling these endpoints could consume hundreds of dollars of API credits in minutes.
3.  **Credential Brute-Forcing:** Attackers running password dictionaries against the `/api/v1/auth/login` endpoint.

To counter these threats, we must build a system-level guardian at the border of our application.

---

## 2. Choosing the Rate-Limiting Algorithm

We evaluate three core algorithms before implementation:

### A. Comparative Analysis Matrix

| Feature | Token Bucket | Fixed Window Counter | Sliding Window Log |
| :--- | :--- | :--- | :--- |
| **Logic Description** | Tokens are added to a bucket at a fixed rate. Requests consume tokens. | Counts requests within absolute time slots (e.g. 1-minute blocks). | Records a timestamp log for every request. Counts logs in a sliding window. |
| **Distributed Cache Size** | **Ultra-Low:** Stores only 2 fields (remaining tokens, last refill timestamp). | **Low:** 1 numeric counter. | **High:** Grows dynamically with traffic (thousands of timestamps). |
| **Burst Traffic Handling** | **Excellent:** Permits short-term burst traffic up to the bucket capacity. | **Poor:** Susceptible to boundary storms (double-bursts). | **Perfect:** Completely accurate, zero boundary issues. |
| **System Overhead** | Minimal CPU calculation. | Extremely low CPU. | High CPU (frequent log pruning and counting). |

---

### B. The reForm Decision: Token Bucket

For reForm, we choose the **Token Bucket Algorithm**. 

*   **UX Alignment:** Builders and fillers perform actions in bursts (e.g., saving form modules, clicking options, uploading assets). Token Bucket allows users to execute bursts of requests up to the maximum capacity of their bucket immediately, but prevents them from exceeding the average rate over the long run.
*   **Infrastructure Efficiency:** Storing limits in a distributed cache (Redis) requires minimal memory. Token Bucket only needs to save the remaining tokens count and the last transaction timestamp per key, keeping our Redis footprint tiny.

---

## 3. Decoupling the Gateway: Interceptors vs. Proxies

When routing traffic, we separate HTTP concerns from OOP/database concerns using Interceptors and Proxies.

```text
                                [Tomcat Web Container Boundary]
                                              │
  [Client Request] ──► [Servlet Filter Chain] ┼──► [HandlerInterceptor] ──► [Controller]
                       (e.g., JWT Auth Filter)│    (RateLimitInterceptor)
```

### A. The Spring MVC HandlerInterceptor
An interceptor intercepts incoming HTTP requests **before** they reach Spring MVC's Controller. 
*   **Why we use it:** We want to reject blocked requests as early as possible. Running rate-limit checks inside the Controller is too late; Spring has already spent CPU cycles parsing JSON bodies, binding variables, and executing filters. Rejecting requests in `preHandle` saves substantial server capacity.

---

### B. Deep Dive: Proxies (Pattern, Concept, Class, and Application)

The term **Proxy** is used frequently in our architecture. It represents a Design Pattern, an Architectural Concept, and a concrete JVM Java Class all at once.

```text
                               THE PROXY ARCHITECTURE
                               
   [ Client / Caller ] ──► [ PROXY (Intermediary Gatekeeper) ] ──► [ Real Object / DB ]
```

#### 1. What is a Proxy?
A Proxy is an intermediary or "stand-in" object that sits between a caller and a target object. It intercepts all calls to the target object, allowing you to execute logic (such as validation, logging, transaction initialization, or network requests) before forwarding the call to the actual subject.

#### 2. The Multi-Dimensional Nature of Proxies
*   **As a Design Pattern (OOP):** The Proxy Pattern is a structural design pattern. You define an interface, implement a real service, and then implement a proxy class that also implements the interface. The caller talks only to the proxy, which controls access to the real service.
*   **As a JVM Java Class:** JDK Dynamic Proxies (`java.lang.reflect.Proxy`) or Spring CGLIB classes are generated dynamically in server RAM at compile-time or runtime. When Spring sees annotations like `@Transactional` or `@PreAuthorize`, it wraps your concrete bean in a dynamically compiled Proxy class.
*   **As an Architectural Concept:** In network engineering, a **Reverse Proxy** (like Nginx or Cloudflare) sits at the border of your network. It shields your backend Spring Boot servers by accepting public traffic, handling SSL certificates, and forwarding sanitized requests to localhost port `8080`.

#### 3. Why and How Proxies are Used in this Module
We use proxies at two levels in our rate limiter:

*   **Spring AOP Proxies (Security):** 
    Spring wraps our controllers and services to handle authentication. When the Interceptor calls `SecurityContextHolder.getContext().getAuthentication()`, it is reading metadata set by the security proxy that intercepted the request in the servlet filter chain.
*   **Bucket4j `ProxyManager` (Database abstraction):**
    Bucket4j uses the class `ProxyManager` (and the Lettuce implementation `LettuceBasedProxyManager`). 
    *   *Why we need it:* Your Java code needs to check a rate-limiting bucket. The bucket state resides remotely in the Redis server. Instead of you writing raw Lettuce socket code to connect, query, and write, the `ProxyManager` acts as a local proxy for the remote Redis bucket.
    *   *How we use it:* You call `proxyManager.builder().build(key, Supplier)`. The proxy manager returns a local `Bucket` proxy object. When you call `bucket.tryConsume(1)`, the proxy intercepts the call, converts it into a binary Lettuce command, sends it over the network to Redis, and returns the result. To your code, it feels like a fast local memory call, shielding you from database operations.

---

## 5. Multi-Tenant Key & Capacity Segregation

We must distinguish between callers so one client's rate-limiting bucket does not impact another.

### A. Key Segregation
*   **IP-Based (Anonymous):** For unauthenticated guests hitting public forms, we track traffic by their IP address.
*   **ID-Based (Authenticated):** For logged-in users, we track traffic by their unique User UUID. Even if a logged-in user changes proxy IPs, their requests are still bound to their User ID bucket.

### B. Capacity Segregation
We define specific capacities and refill intervals based on the security Role:

*   **ANONYMOUS (Capacity: 10):** Low trust. Strict boundaries to prevent scraping and automated form spam.
*   **FORM_FILLER (Capacity: 30):** Medium trust. Higher capacity to handle conversational voice websocket packets.
*   **FORM_BUILDER (Capacity: 60):** High trust. Elevated limits to allow fast form editing and previewing.
*   **ADMIN (Capacity: 120):** Full trust. Elevated limits for administrative system actions.
