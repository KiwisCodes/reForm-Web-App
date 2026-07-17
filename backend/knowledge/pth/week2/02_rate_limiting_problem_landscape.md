# rate-limiting: The Problem Landscape & Architectural Choices
**Document Version:** 1.0  
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
| **Distributed Cache Size** | **Ultra-Low:** 2 fields (remaining tokens, last refill timestamp). | **Low:** 1 numeric counter. | **High:** Grows dynamically with traffic (thousands of timestamps). |
| **Burst Traffic Handling** | **Excellent:** Permits short-term burst traffic up to the bucket capacity. | **Poor:** Susceptible to boundary storms (double-bursts). | **Perfect:** Completely accurate, zero boundary issues. |
| **System Overhead** | Minimal CPU calculation. | Extremely low CPU. | High CPU (frequent log pruning and counting). |

---

### B. The reForm Decision: Token Bucket

For reForm, we choose the **Token Bucket Algorithm**. 

*   **UX Alignment:** Builders and fillers perform actions in bursts (e.g., saving form modules, clicking options, uploading assets). Token Bucket allows users to execute bursts of requests up to the maximum capacity of their bucket immediately, but prevents them from exceeding the average rate over the long run.
*   **Infrastructure Efficiency:** Storing limits in a distributed cache (Redis) requires minimal memory. Token Bucket only needs to save the remaining tokens count and the last transaction timestamp per key, keeping our Redis footprint tiny.

---

## 3. Decoupling the Gateway: Interceptors vs. Proxies

We position our rate-limiting check at the absolute HTTP boundary of the Spring container.

```text
                                [Tomcat Web Container Boundary]
                                              │
  [Client Request] ──► [Servlet Filter Chain] ┼──► [HandlerInterceptor] ──► [Controller]
                       (e.g., JWT Auth Filter)│    (RateLimitInterceptor)
```

### A. The Spring MVC HandlerInterceptor
An interceptor intercepts incoming HTTP requests **before** they reach Spring MVC's Controller. 
*   **Why we use it:** We want to reject blocked requests as early as possible. Running rate-limit checks inside the Controller is too late; Spring has already spent CPU cycles parsing JSON bodies, binding variables, and executing filters. Rejecting requests in `preHandle` saves substantial server capacity.

### B. Aspect-Oriented Programming (AOP) Proxies
Spring uses AOP proxies to inject concerns like `@Transactional` or `@PreAuthorize` at runtime by wrapping target classes. 
*   **Decoupling:** By keeping the HTTP rate-limiting logic in an Interceptor, we avoid cluttering our Controller classes with proxy annotations or procedural code, adhering to the Single Responsibility Principle.

---

## 4. Multi-Tenant Key & Capacity Segregation

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
