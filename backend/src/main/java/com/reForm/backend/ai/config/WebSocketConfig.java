package com.reForm.backend.ai.config;

import com.reForm.backend.ai.websocket.VoiceSyncWSHandler;
import com.reForm.backend.auth.port.ITokenProvider;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WEBSOCKET CONFIGURATION & PATH REGISTRY
 * 
 * Registers WebSocket handlers and configures handshake security interceptors.
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceSyncWSHandler voiceSyncWSHandler;
    private final ITokenProvider tokenProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceSyncWSHandler, "/ws/v1/voice")
                .addInterceptors(new JwtHandShakeInterceptor(tokenProvider))
                .setAllowedOrigins("*");
    }

    /**
     * CONFIG OVERRIDE: Increase Tomcat Inbound/Outbound WebSocket Buffer Limit to 10MB
     * Prevents WebSocket Code 1009 ("Buffer too small") errors when receiving large Gemini audio payloads.
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean createWebSocketContainer() {
        org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean container = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10485760); // 10MB
        container.setMaxBinaryMessageBufferSize(10485760); // 10MB
        container.setAsyncSendTimeout(10000L);
        return container;
    }

    /**
     * JWT HANDSHAKE INTERCEPTOR
     * 
     * Validates JWT token from the HTTP upgrade URL query parameter (?token=JWT)
     * during the initial Handshake before the protocol upgrades to WebSockets.
     */
    @RequiredArgsConstructor
    public static class JwtHandShakeInterceptor implements HandshakeInterceptor {

        private final ITokenProvider tokenProvider;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, 
                                        ServerHttpResponse response, 
                                        WebSocketHandler wsHandler, 
                                        Map<String, Object> attributes) throws Exception {
            
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String query = servletRequest.getServletRequest().getQueryString();
                
                if (query != null && query.contains("token=")) {
                    String token = extractParam(query, "token");
                    
                    if (token != null) {
                        // [DEV_TEST_TEMPORARY] Local browser testing token bypass (Remove/disable in production build)
                        if ("test_token".equals(token) || "test".equals(token)) {
                            attributes.put("userId", "test_user_id");
                            attributes.put("role", Role.FORM_BUILDER);
                            log.info("[DEV_TEST_TEMPORARY] WebSocket Handshake authenticated using test token for dev testing.");
                            return true;
                        }
                        if (tokenProvider.validateToken(token)) {
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
                    }
                }
            }
            
            log.warn("WebSocket Handshake rejected: Invalid or missing authentication token.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false; // Reject Handshake with HTTP 401
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
}
