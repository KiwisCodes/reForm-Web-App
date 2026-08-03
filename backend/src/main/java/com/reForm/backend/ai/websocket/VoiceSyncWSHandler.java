package com.reForm.backend.ai.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.ai.state.SessionTracker;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VOICE SYNC WEBSOCKET CONNECTION LIFECYCLE HANDLER
 * 
 * WHY IS THIS CLASS NEEDED?
 * Tomcat handles raw network TCP sockets, but it doesn't know what to do with incoming 
 * binary PCM audio frames, client disconnections, or heartbeat pings.
 * 
 * This class is the LOW-LEVEL CONNECTION MANAGER. It extends Spring's BinaryWebSocketHandler 
 * to handle binary audio streams (~50 frames/sec) and manage socket lifecycle events.
 * 
 * SYSTEM ARCHITECTURE ROLES:
 * 1. Local Server State: Stores physical WebSocketSession handles in server RAM.
 * 2. Distributed State: Calls SessionTracker to register presence and maintain Redis TTLs.
 * 3. AI Proxy Layer: Delegates client audio buffers to IAiVoiceAdapter (Gemini Live).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceSyncWSHandler extends BinaryWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Component 1: SessionTracker Service (Redis State Management)
    private final SessionTracker sessionTracker;

    // Component 2: IAiVoiceAdapter (Strategy/Adapter interface for AI streaming, e.g. Gemini Live Mode 4)
    private final IAiVoiceAdapter aiVoiceAdapter;

    /**
     * LOCAL IN-MEMORY SESSION REGISTRY (Server RAM)
     * 
     * WHY CONCURRENT HASH MAP?
     * VoiceSyncWSHandler is a Spring Singleton Bean servicing hundreds of concurrent connection threads.
     * A plain HashMap is not thread-safe and corrupts under concurrent writes.
     * ConcurrentHashMap uses segment-level locking for safe, high-performance concurrent access.
     */
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * LIFECYCLE EVENT 1: SOCKET ESTABLISHED
     * 
     * WHEN IS IT TRIGGERED?
     * Triggered automatically by Tomcat/Spring MVC immediately AFTER JwtHandshakeInterceptor.beforeHandshake
     * validates the token and Tomcat sends the "HTTP 101 Switching Protocols" upgrade response.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Step 1: Read identity attributes set by JwtHandshakeInterceptor during handshake
        String userId = (String) session.getAttributes().get("userId");
        Role role = (Role) session.getAttributes().get("role");

        // Defensive check: Reject unauthenticated connections that somehow bypassed security
        if (userId == null) {
            log.warn("WebSocket connection rejected: Missing userId in session attributes.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        MDC.put("sessionId", userId);
        try {
            // Step 2: Wrap session in ConcurrentWebSocketSessionDecorator for Tomcat thread-safety (10MB buffer limit)
            WebSocketSession safeSession = WebSocketSessionUtils.wrapSafeSession(session);
            activeSessions.put(userId, safeSession);

            // Step 3: Register distributed online presence in Redis (writes "session:{userId}" hash with 2h TTL)
            sessionTracker.registerSession(userId, session.getId());

            // Step 4: Initiate outbound AI stream (calls GeminiLiveVoiceAdapter to open WSS tunnel & send setup JSON)
            aiVoiceAdapter.startSession(userId, safeSession);

            log.info("WebSocket connection established for user: {} (Role: {}, Session ID: {})", 
                     userId, role, session.getId());
        } finally {
            MDC.remove("sessionId");
        }
    }

    /**
     * LIFECYCLE EVENT 2: BINARY AUDIO MESSAGE RECEIVED
     * 
     * WHEN IS IT TRIGGERED?
     * Triggered ~50 times per second whenever the candidate speaks into their microphone 
     * and streams raw binary PCM audio frames over the open WebSocket.
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            MDC.put("sessionId", userId);
            try {
                // Extract raw 16-bit PCM binary byte array cleanly from ByteBuffer
                ByteBuffer buffer = message.getPayload();
                byte[] payload = new byte[buffer.remaining()];
                buffer.get(payload);
                
                // Pass audio buffer directly to the AI adapter using candidate clientSession handle
                aiVoiceAdapter.sendClientAudio(session, payload);
            } finally {
                MDC.remove("sessionId");
            }
        }
    }

    /**
     * LIFECYCLE EVENT 3: TEXT MESSAGE RECEIVED (HEARTBEAT PINGS)
     * 
     * WHEN IS IT TRIGGERED?
     * Triggered when the client browser sends periodic text frames (e.g. "PING") every 30 seconds.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            MDC.put("sessionId", userId);
        }
        try {
            String payload = message.getPayload();
            
            // Intercept heartbeat ping frames from client
            if ("PING".equalsIgnoreCase(payload.trim()) || payload.contains("ping")) {
                if (userId != null) {
                    sessionTracker.refreshTTL(userId);
                    try {
                        session.sendMessage(new TextMessage("PONG"));
                    } catch (IOException e) {
                        log.error("Failed to send PONG response to user: {}", userId, e);
                    }
                }
                return;
            }

            // Handle JSON text input or raw text from client
            if (payload.trim().startsWith("{") && payload.trim().endsWith("}")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(payload);
                    if (jsonNode.has("text")) {
                        String text = jsonNode.get("text").asText();
                        aiVoiceAdapter.sendClientText(session, text);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse client JSON text frame: {}", payload);
                }
            }

            if (!payload.isBlank()) {
                aiVoiceAdapter.sendClientText(session, payload);
            }
        } finally {
            if (userId != null) {
                MDC.remove("sessionId");
            }
        }
    }

    /**
     * LIFECYCLE EVENT 4: TRANSPORT ERROR
     * 
     * WHEN IS IT TRIGGERED?
     * Triggered when network latency drops packets or a TCP socket breaks unexpectedly.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error on WebSocket session: {}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * LIFECYCLE EVENT 5: SOCKET CLOSED & CLEANUP
     * 
     * WHEN IS IT TRIGGERED?
     * Triggered when the candidate finishes the interview, closes the tab, or the connection drops.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            MDC.put("sessionId", userId);
            try {
                // 1. Remove socket handle from local server RAM map (Prevents JVM RAM leaks)
                activeSessions.remove(userId);

                // 2. Close outbound Gemini WSS connection attached to this clientSession
                aiVoiceAdapter.closeSession(session);

                // 3. Delete metadata key from Redis (Prevents Ghost State)
                sessionTracker.deregisterSession(userId);

                log.info("WebSocket connection closed for user: {} (Status: {})", userId, status);
            } finally {
                MDC.remove("sessionId");
            }
        }
    }
}
