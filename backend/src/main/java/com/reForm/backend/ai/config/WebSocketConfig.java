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
                    
                    if (token != null && tokenProvider.validateToken(token)) {
                        String userId = String.valueOf(tokenProvider.extractUserId(token));
                        String role = tokenProvider.extractRole(token);
                        
                        attributes.put("userId", userId);
                        attributes.put("role", role);
                        
                        log.info("WebSocket Handshake authenticated for user: {} (Role: {})", userId, role);
                        return true; // Approve Handshake
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
