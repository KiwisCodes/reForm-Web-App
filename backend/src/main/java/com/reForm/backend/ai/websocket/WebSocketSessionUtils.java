package com.reForm.backend.ai.websocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * WEBSOCKET SESSION UTILITY
 * 
 * Centralizes thread-safe session wrapping for Spring WebSockets.
 * Wraps sessions in a ConcurrentWebSocketSessionDecorator with a 10MB buffer limit
 * and a 10-second send timeout to prevent concurrent write collisions (IllegalStateException).
 */
public class WebSocketSessionUtils {

    public static final int BUFFER_10MB = 10485760; // 10MB buffer limit
    public static final int SEND_TIMEOUT_MS = 10000;  // 10 second send timeout

    /**
     * Helper method to wrap a WebSocketSession in a thread-safe decorator.
     * Prevents double-wrapping if the session is already decorated.
     * 
     * @param session Raw or decorated WebSocketSession
     * @return Thread-safe decorated WebSocketSession
     */
    public static WebSocketSession wrapSafeSession(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            return session;
        }
        return new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, BUFFER_10MB);
    }
}
