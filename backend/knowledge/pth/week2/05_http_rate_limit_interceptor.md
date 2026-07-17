# HTTP Boundary Gatekeeper: Interceptors & Route Mapping
**Document Version:** 1.1  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Why Do We Need an Interceptor?

In Spring Boot, incoming web requests flow through a multi-stage request processing pipeline:

```text
  HTTP Request ──► [ Servlet Filter Chain ] ──► [ Spring MVC Interceptor ] ──► [ Controller ]
                    (e.g., JWT Auth Filter)       (preHandle Gatekeeper)
```

If we place rate-limiting checks directly inside our REST Controller methods:
1.  **Duplicate Boilerplate:** We would have to copy-paste checking code into every endpoint.
2.  **Resource Waste:** By the time a request reaches a Controller, Spring has already completed heavy processing—parsing JSON payloads, binding parameters, and validating schemas. Rejecting a request at the Controller level wastes CPU and thread capacity.

### What the Interceptor Does
The `RateLimitInterceptor` acts as a centralized **firewall** at the entrance of Spring MVC:
*   It executes in the **`preHandle`** phase *before* the Controller is invoked.
*   If a user exceeds their limit, the interceptor blocks the request immediately, returns an HTTP `429 Too Many Requests` status, writes a standard JSON error response, and shuts down the request pipeline, saving database and server threads.

---

## 2. Technical Prerequisites for Building the Interceptor

To implement the interceptor, we must coordinate three distinct Spring subsystems:

### A. The Spring MVC Interceptor Lifecycle
We implement `HandlerInterceptor` and override **`preHandle()`**. If `preHandle()` returns `true`, the request is allowed to continue. If it returns `false`, Spring MVC halts processing and immediately sends the response back to the client.

### B. Spring Security Integration
Security Filters (like the JWT validation filter) execute before the MVC layer. When the request reaches the interceptor, Spring Security has already populated the **`SecurityContextHolder`**. The interceptor queries this context to determine if the caller is authenticated, allowing us to extract their User ID and Role clearance.

### C. Manual JSON Serialization
Since interceptors run outside the normal Controller response-mapping lifecycle, we cannot simply return a Java object and expect Spring to serialize it. We must inject Spring's **`ObjectMapper`** bean and write the JSON error payload directly to the `HttpServletResponse` output writer.

---

## 3. Reverse Proxies in Production vs. Local Development

A **Reverse Proxy** is a server (like Nginx, Cloudflare, or an AWS Load Balancer) that sits in front of your application servers to handle SSL certificates, serve static files, and balance traffic load.

```text
 PRODUCTION ROUTING FLOW:
 [ User Client ] ──► [ Cloudflare Proxy ] ──► [ Nginx Proxy ] ──► [ Spring Boot Tomcat ]
                                                                   (Port 8080)
```

### A. Is a Reverse Proxy Used in our Current Setup?
*   **Local Development:** No. The browser connects directly to the embedded Tomcat server on `localhost:8080` (or `localhost:3000` via Next.js dev server). `request.getRemoteAddr()` returns `127.0.0.1` or local network coordinates.
*   **Production Deployment:** Yes. In production, requests travel through multiple proxy hops (Cloudflare and Nginx) before reaching Spring Boot.

### B. The Proxy IP Problem & `X-Forwarded-For`
When a reverse proxy forwards a request, the source TCP IP of the packet becomes the proxy's IP. If your Java code reads `request.getRemoteAddr()`, it will see the IP of the Nginx server. 

If Nginx forwards requests for 10,000 users, **all 10,000 users will share the exact same rate-limiting bucket**. If one user exceeds their limit, Nginx's IP gets blocked, locking out every user in the world!

### C. Preserving Client IPs
To solve this, reverse proxies inject headers containing the original client IP:
*   **`X-Forwarded-For`**: A comma-separated list of IPs. Each proxy hop appends the sender's IP: `client_ip, proxy1_ip, proxy2_ip`. The client's real IP is always the first entry.
*   **`CF-Connecting-IP`**: A secure header injected by Cloudflare that directly contains the client's source IP.

The interceptor inspects these headers to extract the real user's IP address, ensuring IP-based buckets are accurate and isolated.
