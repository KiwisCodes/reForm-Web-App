# 15. Mode 4 Security Authentication & Interceptor Guards Specification

**Document Version:** 1.0  
**Target System:** reForm Monolith (`com.reForm.backend.ai` & Next.js Frontend)  
**Parent Specification:** [10_mode4_implementation_retrospective_and_js_to_java_mapping.md](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week3/10_mode4_implementation_retrospective_and_js_to_java_mapping.md)  

---

## 1. Security Architecture Overview

WebSocket endpoints cannot use standard HTTP headers (`Authorization: Bearer <JWT>`) during the initial HTTP GET upgrade request in standard browser JavaScript `new WebSocket(url)`.

Instead, authentication claims must be passed via query parameter:
```javascript
const ws = new WebSocket("ws://localhost:8080/ws/v1/voice?token=" + jwtToken);
```

Security is enforced at the network protocol layer by intercepting the HTTP handshake request **before** Tomcat returns `HTTP 101 Switching Protocols`.

---

## 2. Refactored `JwtHandshakeInterceptor.java` Architecture

`JwtHandshakeInterceptor` is extracted out of `WebSocketConfig` into its own dedicated class file:
[JwtHandshakeInterceptor.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/config/JwtHandshakeInterceptor.java).

### Flattened Control Flow (Inversion with Guard Clauses)
To eliminate deeply nested conditional branches, `beforeHandshake` uses early-return guard clauses:

```java
package com.reForm.backend.ai.config;

import com.reForm.backend.auth.provider.ITokenProvider;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * JWT HANDSHAKE INTERCEPTOR
 * Intercepts HTTP GET /ws/v1/voice upgrade requests before socket connection opens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final ITokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        // GUARD 1: Verify request is a Servlet HTTP Request
        if (!(request instanceof ServletServerHttpRequest)) {
            log.warn("Handshake rejected: Request is not a ServletServerHttpRequest");
            return false;
        }

        URI uri = request.getURI();
        String query = uri.getQuery();

        // GUARD 2: Check query parameter presence
        if (query == null || !query.contains("token=")) {
            log.warn("Handshake rejected: Missing 'token' query parameter in URI: {}", uri);
            return false;
        }

        // Extract token value from query string
        String token = extractParam(query, "token");
        if (token == null || token.isBlank()) {
            log.warn("Handshake rejected: Blank token value");
            return false;
        }

        /* 
         * =========================================================================
         * [PRODUCTION_ALERT_REMOVE_BEFORE_PROD] LOCAL TEST TOKEN BYPASS
         * REMOVE THIS BLOCK BEFORE DEPLOYING TO PRODUCTION!
         * =========================================================================
         */
        if ("test_token".equals(token)) {
            log.warn("[PRODUCTION_ALERT_REMOVE_BEFORE_PROD] Bypassing JWT validation for test_token");
            attributes.put("userId", "test-user-id");
            attributes.put("role", Role.FORM_BUILDER);
            attributes.put("formId", extractParam(query, "formId"));
            return true;
        }

        // GUARD 3: Validate JWT Signature & Expiration via ITokenProvider
        if (!tokenProvider.validateToken(token)) {
            log.warn("Handshake rejected: Invalid or expired JWT token");
            return false;
        }

        // Extract claims & populate session attributes memory map
        String userId = tokenProvider.getUserIdFromToken(token);
        Role role = tokenProvider.getRoleFromToken(token);
        String formId = extractParam(query, "formId");

        attributes.put("userId", userId);
        attributes.put("role", role);
        attributes.put("formId", formId);

        log.info("WebSocket Handshake Approved for user: {} (Role: {})", userId, role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Post-handshake cleanup if needed
    }

    private String extractParam(String query, String paramName) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equals(paramName)) {
                return kv[1];
            }
        }
        return null;
    }
}
```

---

## 3. Spring Security Integration (`SecurityConfig.java`)

WebSocket handshake endpoints must be permitted at the Spring Security filter chain level so `JwtHandshakeInterceptor` can process authentication query tokens:

```java
// Inside SecurityConfig.java:
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/ws/v1/voice/**").permitAll() // Security enforced by JwtHandshakeInterceptor
    .anyRequest().authenticated()
)
```
