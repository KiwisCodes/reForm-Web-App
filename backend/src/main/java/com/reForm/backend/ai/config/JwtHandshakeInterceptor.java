package com.reForm.backend.ai.config;

import com.reForm.backend.auth.port.ITokenProvider;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * JWT HANDSHAKE INTERCEPTOR
 * 
 * Validates JWT token from the HTTP upgrade URL query parameter (?token=JWT)
 * during the initial Handshake before the protocol upgrades to WebSockets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final ITokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, 
                                    ServerHttpResponse response, 
                                    WebSocketHandler wsHandler, 
                                    Map<String, Object> attributes) throws Exception {
        
        // Guard 1: Must be a Servlet Server HTTP Request
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WebSocket Handshake rejected: Not a Servlet request.");
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        String query = servletRequest.getServletRequest().getQueryString();

        // Guard 2: Query string must contain "token="
        if (query == null || !query.contains("token=")) {
            log.warn("WebSocket Handshake rejected: Missing token query parameter.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String token = extractParam(query, "token");

        // Guard 3: Token value must not be blank
        if (token == null || token.isBlank()) {
            log.warn("WebSocket Handshake rejected: Empty token value.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // =========================================================================================
        // [PRODUCTION_ALERT_REMOVE_BEFORE_PROD] START
        // Local Browser Test Token Bypass (FOR DEV TESTING ONLY - REMOVE OR DISABLE BEFORE PRODUCTION)
        // =========================================================================================
        if ("test_token".equals(token) || "test".equals(token)) {
            attributes.put("userId", "test_user_id");
            attributes.put("role", Role.FORM_BUILDER);
            log.info("[DEV_TEST_TEMPORARY] WebSocket Handshake authenticated using test token for dev testing.");
            return true;
        }
        // =========================================================================================
        // [PRODUCTION_ALERT_REMOVE_BEFORE_PROD] END
        // =========================================================================================

        // Guard 4: Validate token signature and expiration with ITokenProvider
        if (!tokenProvider.validateToken(token)) {
            log.warn("WebSocket Handshake rejected: Invalid or expired authentication token.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Token is valid -> Extract claims and attach to session attributes
        String userId = String.valueOf(tokenProvider.extractUserId(token));
        String roleStr = tokenProvider.extractRole(token);
        Role role = Role.FORM_BUILDER;
        try {
            role = Role.valueOf(roleStr);
        } catch (Exception ignored) {}

        attributes.put("userId", userId);
        attributes.put("role", role);

        log.info("WebSocket Handshake authenticated for user: {} (Role: {})", userId, role);
        return true; // Approve Handshake
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, 
                               ServerHttpResponse response, 
                               WebSocketHandler wsHandler, 
                               Exception exception) {
        // No post-handshake action required
    }

    private String extractParam(String query, String key) {
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equalsIgnoreCase(key)) {
                return pair[1];
            }
        }
        return null;
    }
}
