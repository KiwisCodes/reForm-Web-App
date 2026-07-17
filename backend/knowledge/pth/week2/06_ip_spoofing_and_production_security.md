# IP Spoofing, Audit Logging & Production Security
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Threat: Client IP Spoofing

Our rate-limiting gatekeeper identifies unauthenticated guest users by reading the `X-Forwarded-For` HTTP header. 

### A. The Spoofing Vulnerability
Because HTTP headers are plain text sent from the client's browser, a malicious attacker can manually inject an `X-Forwarded-For` header populated with a rotating list of fake IP addresses (e.g., `X-Forwarded-For: 1.1.1.1`, `X-Forwarded-For: 2.2.2.2`).

```text
  [ Attacker ] ──( Injects: X-Forwarded-For: 1.1.1.1 )──► [ Spring Boot App ]
  ( Real IP: 99.88.77.66 )                                     │
                                                               ▼
                                                  ( Thinks user is 1.1.1.1 )
```

*   **The Result:** The application's interceptor extracts `1.1.1.1` as the client key. The attacker can execute unlimited requests by simply changing the header values in their attack script, bypassing the IP-based rate limiter completely.

---

## 2. Mitigation at the Reverse Proxy Layer

We must **never trust the client-supplied HTTP headers** directly. We resolve this security loophole by configuring our border servers (reverse proxies like Nginx or AWS Application Load Balancers) to strip or overwrite the client's custom header before it reaches Spring Boot.

### A. Nginx Configuration
If you run Nginx in front of your Spring Boot jar, configure it to **overwrite** the `X-Forwarded-For` header with Nginx's verified connection remote address, instead of appending client input:

```nginx
server {
    listen 80;
    server_name api.reform.app;

    location / {
        # Overwrites the X-Forwarded-For header with the actual TCP socket remote address of the user
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_pass http://localhost:8080;
    }
}
```
*   **Why this works:** Nginx ignores any custom `X-Forwarded-For` header sent by the client browser. It replaces it with the actual, verified TCP remote address of the connecting socket (`$remote_addr`). Spring Boot receives only the verified client IP.

### B. AWS Application Load Balancer (ALB)
AWS ALBs automatically manage the `X-Forwarded-For` header. In production, configure the ALB to:
*   **Remove** any user-supplied `X-Forwarded-For` headers at the entry point of your VPC.
*   Let the ALB append the real TCP source IP of the client before routing the request to your target group instances.

### C. Cloudflare Specifics (`CF-Connecting-IP`)
If your domain is protected by Cloudflare, Cloudflare appends a proprietary, highly secure header: **`CF-Connecting-IP`**. 
*   Because Cloudflare strips client-supplied custom headers matching this name, reading `request.getHeader("CF-Connecting-IP")` instead of `X-Forwarded-For` is a secure way to retrieve the real client IP.

---

## 3. Production Auditing & Request Logging

To monitor rate-limiting activities and detect ongoing attacks, we must implement request audit logging.

### A. What to Log
For every rate-limited check, we log:
1.  **Timestamp:** The exact time of evaluation.
2.  **Client Identifier:** The IP address (for guests) or User UUID (for members).
3.  **Authentication Role:** The role clearance.
4.  **Target URI & Method:** E.g., `POST /api/v1/public/forms/form_123/submit`.
5.  **Check Outcome:** Allowed (HTTP 200) vs. Blocked (HTTP 429).
6.  **Wait Time:** The backoff duration if blocked.

### B. Operational Value
*   **Tuning Limits:** If logs show thousands of legitimate `FORM_BUILDER` users hitting limits, it indicates our rules are configured too low, and we should increase the capacity in `application.yml`.
*   **Attack Diagnostics:** A sudden spike in logs showing consecutive `ANONYMOUS` blocks on the same `/api/v1/auth/login` path suggests a credential brute-forcing attack, allowing firewall systems to temporarily block those specific IPs.
*   **Security Compliance:** Provides audit trails for enterprise regulatory compliance.
