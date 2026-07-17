# HTTP Boundary Gatekeeper: Interceptors & Route Mapping
**Document Version:** 1.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The HTTP Gateway Gatekeeper

To protect our backend APIs, we implement a Spring MVC `HandlerInterceptor` that intercepts incoming HTTP requests **before** they reach our Controller endpoints.

This interceptor resolves the client's identity and clearance Role, queries the rate-limiting engine, and blocks the request with an HTTP `429 Too Many Requests` status if the rate limits are exceeded.

---

## 2. The Production-Ready Code: `RateLimitInterceptor.java`

Here is our verified interceptor implementation:

```java
package com.reForm.backend.core.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reForm.backend.core.port.IRateLimitService;
import com.reForm.backend.user.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final IRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientKey = resolveClientKey(request);
        Role role = resolveUserRole();

        boolean allowed = rateLimitService.tryConsume(clientKey, role);
        if(allowed) {
            return true; // Token available: Proceed to the Controller
        }

        long waitTime = rateLimitService.getWaitTimeInSeconds(clientKey, role);
        log.warn("Blocking request from client: [{}] (Role: {}). Rate limit exceeded. Backoff: {}s", clientKey, role, waitTime);

        buildBlockResponse(response, waitTime);
        return false; // Blocks request pipeline execution
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // principal is the Details object, while name is the unique String ID (UUID)
        if(authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName(); // Returns the unique user database UUID
        }

        // X-Forwarded-For header preserves client IP through reverse proxies
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if(ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr(); // Fallback to remote socket IP
        }

        // Splits comma-separated proxy lists to extract the original client IP (first element)
        if(ipAddress != null && ipAddress.contains(",")){
            ipAddress = ipAddress.split(",")[0].trim();
            return ipAddress;
        }

        return ipAddress;
    }

    private Role resolveUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null
        && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal())){
            return authentication
                    .getAuthorities()
                    .stream()
                    .map(grantedAuthority -> {
                        try{
                            String roleName = grantedAuthority
                                    .getAuthority()
                                    .replace("ROLE_", "");
                            return Role.valueOf(roleName);
                        } catch (IllegalArgumentException e){
                            return Role.FORM_FILLER;
                        }
                    })
                    .findFirst()
                    .orElse(Role.FORM_FILLER);
        }
        return null; // Guest user
    }

    private void buildBlockResponse(HttpServletResponse response, long waitTime) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP 429
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Retry-After header indicates the backoff wait duration in seconds
        response.setHeader("Retry-After", String.valueOf(waitTime));

        Map<String, Object> errorsDetails = Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Please try again after the backoff duration.",
                "retryAfterSeconds", waitTime
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorsDetails));
    }
}
```

---

## 3. Spring Security: `getPrincipal()` vs. `getName()`

During authentication evaluation, the interceptor queries Spring's `SecurityContextHolder`:
*   **`getPrincipal()`**: Returns the authenticated object instance (e.g. `UserDetails` carrying passwords and roles). If the request is unauthenticated, Spring Security populates this field with the string `"anonymousUser"`. We check against this string to identify guest sessions.
*   **`getName()`**: A helper method that returns the unique **string identifier** of the user principal (e.g. username or database UUID). We use this as our `clientKey` for logged-in users.

---

## 4. IP Tracking & Reverse Proxies: `X-Forwarded-For`

*   **HTTP Header vs. Body:**
    *   **Headers:** Metadata keys sent at the top of the HTTP request (e.g., `Content-Type`, `Authorization: Bearer <token>`).
    *   **Body:** The actual payload (usually a JSON string like `{"name": "HR Form"}`) containing target data fields.
*   **The Proxy Problem:** In production, requests hit a **Reverse Proxy** (like Nginx or Cloudflare) before forwarding to Spring Boot. The Spring Boot application only sees Nginx's IP address if it reads `request.getRemoteAddr()`, which would cause all users globally to share a single rate-limiting bucket.
*   **The Solution (`X-Forwarded-For`):** Reverse proxies append the user's original IP address to the `X-Forwarded-For` header. If a request travels through multiple proxies, it appends them as a comma-separated list: `client_ip, proxy1_ip, proxy2_ip`. The client IP is always the first element, which we extract using `ipAddress.split(",")[0].trim()`.

---

## 5. Mounting Route Registrations: `WebMvcConfig.java`

You must register the interceptor inside Spring MVC's config to bind it to specific request paths.

```java
package com.reForm.backend.core.config;

import com.reForm.backend.core.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                // Protects Thien's public form APIs and WebSocket handshakes
                .addPathPatterns("/api/v1/public/**")
                .addPathPatterns("/ws/**");
    }
}
```
*   `addPathPatterns()` tells Spring to route matched URLs (like `/api/v1/public/submit`) to our interceptor first, keeping non-public administrative or static assets unblocked.
