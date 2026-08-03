# 15. Mode 4 Security Authentication & Interceptor Guards Specification

**Document Version:** 1.0  
**Target System:** reForm Monolith (`com.reForm.backend.ai` & Next.js Frontend)  
**Parent Specification:** [10_mode4_master_syllabus_and_table_of_contents.md](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week3/10_mode4_master_syllabus_and_table_of_contents.md)  

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

---

## 4. Spring `WebSocketSession` Deep Dive & Class Knowledge Framework

### 1. Systematic 4-Question Framework for `WebSocketSession`

#### (1) Problem that led to its invention:
Raw Java TCP Sockets (`java.net.Socket`) require manual HTTP handshake parsing, framing byte bitmasks, and low-level buffer management. Different application servers (Tomcat, Jetty, Undertow) use different internal socket objects (`WsSession`, `JettyWebSocketSession`).

#### (2) Historical Progression:
Low-level TCP Sockets (1995) $\rightarrow$ Servlet HTTP Long-Polling (2005) $\rightarrow$ Native Java EE JSR-356 `javax.websocket.Session` (2013) $\rightarrow$ Spring `WebSocketSession` abstraction (2014).

#### (3) How to use it & Receiving Mechanics (IoC Callback Model):
* **No Direct `.receive()` Method**: `WebSocketSession` does **NOT** have a `.receive()` or `.read()` method. WebSockets use an **Inversion of Control (IoC) Event-Driven Callback Model**.
* **Tomcat Event Callbacks**: When the client browser sends a frame, Tomcat intercepts the TCP packets and automatically invokes your registered `WebSocketHandler` callbacks:
  - `@Override handleTextMessage(session, message)` for JSON text frames.
  - `@Override handleBinaryMessage(session, message)` for binary audio frames.
* **Session Handle**: Tomcat passes the specific `session` instance into the callback as the first argument, representing **WHO** sent the frame and allowing responses via `session.sendMessage(...)`.
* **State & Attribute Access**:
  - Retrieve attributes: `session.getAttributes().get("userId")`
  - Check state: `session.isOpen()`
  - Send message: `session.sendMessage(new TextMessage(...))` or `session.sendMessage(new BinaryMessage(...))`
  - Close socket: `session.close(CloseStatus.NORMAL)`

#### (4) When to use it:
Always use `WebSocketSession` whenever you need to inspect attributes, verify connection state, or transmit frames over an active WebSocket connection in Spring Boot.

---

### 2. Lifespan Attributes Map (`session.getAttributes()`)

Attributes populated during handshake remain attached to `WebSocketSession` for its entire lifetime:

| Attribute Key | Java Type | Populated By | Purpose |
| :--- | :--- | :--- | :--- |
| `"userId"` | `String` (e.g. `"user_123"`) | `JwtHandshakeInterceptor` | User identifier |
| `"role"` | `Role` (e.g. `FORM_BUILDER`) | `JwtHandshakeInterceptor` | Role-based authorization |
| `"formId"` | `String` (e.g. `"form_99"`) | `JwtHandshakeInterceptor` | Canvas form UUID |
| `"mode"` | `String` (`"MODE_3"`/`"MODE_4"`) | `JwtHandshakeInterceptor` | Selected voice mode strategy |
| `"safeClientSession"` | `ConcurrentWebSocketSessionDecorator` | `VoiceSyncWSHandler` | Socket 1 (Inbound client socket) |
| `"geminiSession"` | `ConcurrentWebSocketSessionDecorator` | `GeminiLiveVoiceAdapter` | Socket 2 (Outbound WSS socket) |

