package com.reForm.backend.ai.websocket;

import com.reForm.backend.ai.agent.SessionStateAgent;
import com.reForm.backend.ai.domain.SessionStateRecoveryResult;
import com.reForm.backend.ai.domain.VoiceMode;
import com.reForm.backend.ai.factory.AiVoiceAdapterFactory;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * ARCHITECTURE REFACTOR (FACTORY PATTERN & ATTRIBUTE STORAGE):
 * 1. Uses AiVoiceAdapterFactory instead of hardcoding a single IAiVoiceAdapter bean.
 * 2. Stores resolved IAiVoiceAdapter strategy inside session.getAttributes() (Zero extra maps, zero memory leaks).
 * 3. Integrates SessionStateAgent for Hot Redis RAM state persistence & instant reconnection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceSyncWSHandler extends BinaryWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // SessionTracker Service (Redis Presence Management)
    private final SessionTracker sessionTracker;

    // SessionStateAgent Service (Hot Redis RAM Working Memory & Reconnection)
    private final SessionStateAgent sessionStateAgent;

    // REFACTOR NEW LINE: AiVoiceAdapterFactory resolves matching adapter strategy by VoiceMode (MODE_3 vs MODE_4)
    private final AiVoiceAdapterFactory adapterFactory;

    // LOCAL IN-MEMORY SESSION REGISTRY (Server RAM)
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * LIFECYCLE EVENT 1: SOCKET ESTABLISHED
     * Triggered automatically by Tomcat/Spring MVC immediately AFTER JwtHandshakeInterceptor.beforeHandshake.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Read identity and mode attributes set by JwtHandshakeInterceptor during handshake
        String userId = (String) session.getAttributes().get("userId");
        Role role = (Role) session.getAttributes().get("role");
        String formId = (String) session.getAttributes().get("formId");
        String modelKey = (String) session.getAttributes().get("modelKey");
        String voiceName = (String) session.getAttributes().get("voiceName");
        Boolean isReconnect = Boolean.TRUE.equals(session.getAttributes().get("reconnect"));

        // Defensive check: Reject unauthenticated connections
        if (userId == null) {
            log.warn("WebSocket connection rejected: Missing userId in session attributes.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        MDC.put("sessionId", userId);
        try {
            // Extract VoiceMode string set during handshake (e.g. MODE_3 vs MODE_4)
            String modeStr = (String) session.getAttributes().getOrDefault("mode", "MODE_4");
            VoiceMode mode = VoiceMode.valueOf(modeStr);

            // Wrap session in ConcurrentWebSocketSessionDecorator for Tomcat thread-safety
            WebSocketSession safeSession = WebSocketSessionUtils.wrapSafeSession(session);
            activeSessions.put(userId, safeSession);

            // Resolve strategy adapter based on chosen mode
            IAiVoiceAdapter adapter = adapterFactory.getAdapter(mode);
            safeSession.getAttributes().put("voiceAdapter", adapter);

            // Register distributed online presence in Redis
            sessionTracker.registerSession(userId, session.getId());

            // Initialize or Rehydrate Hot Redis RAM Working State
            if (isReconnect) {
                log.info("🔄 [RECONNECTING SESSION]: Attempting state recovery for session: {}", session.getId());
                SessionStateRecoveryResult recoveryResult = sessionStateAgent.recoverSessionState(session.getId());
                safeSession.getAttributes().put("recoveryResult", recoveryResult);
            } else {
                sessionStateAgent.initializeSession(
                    session.getId(), userId, formId,
                    role != null ? role.name() : "FORM_FILLER",
                    modelKey, voiceName
                );
            }

            // Start AI stream session using the resolved strategy adapter
            adapter.startSession(userId, safeSession);

            // Mark session as ACTIVE
            sessionStateAgent.markSessionActive(session.getId());

            log.info("WebSocket connection established for user: {} (Role: {}, Mode: {}, Session ID: {})", 
                     userId, role, mode, session.getId());
        } finally {
            MDC.remove("sessionId");
        }
    }

    /**
     * LIFECYCLE EVENT 2: BINARY AUDIO MESSAGE RECEIVED
     * Triggered ~50 times per second whenever client streams raw binary PCM audio frames.
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            MDC.put("sessionId", userId);
            try {
                // REFACTOR NEW LINE: Retrieve active voice adapter from session attributes
                IAiVoiceAdapter adapter = (IAiVoiceAdapter) session.getAttributes().get("voiceAdapter");
                
                if (adapter != null) {
                    ByteBuffer buffer = message.getPayload();
                    byte[] payload = new byte[buffer.remaining()];
                    buffer.get(payload);
                    
                    // REFACTOR NEW LINE: Forward binary PCM audio payload to the resolved adapter strategy
                    adapter.sendClientAudio(session, payload);
                }
            } finally {
                MDC.remove("sessionId");
            }
        }
    }

    /**
     * LIFECYCLE EVENT 3: TEXT MESSAGE RECEIVED (HEARTBEAT PINGS & TEXT PROMPTS)
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            MDC.put("sessionId", userId);
        }
        try {
            String payload = message.getPayload();
            
            // Intercept heartbeat ping frames
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

            // REFACTOR NEW LINE: Retrieve active voice adapter from session attributes
            IAiVoiceAdapter adapter = (IAiVoiceAdapter) session.getAttributes().get("voiceAdapter");
            if (adapter == null) return;

            // Handle JSON text input or raw text from client
            if (payload.trim().startsWith("{") && payload.trim().endsWith("}")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(payload);
                    if (jsonNode.has("text")) {
                        String text = jsonNode.get("text").asText();
                        adapter.sendClientText(session, text);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse client JSON text frame: {}", payload);
                }
            }

            if (!payload.isBlank()) {
                adapter.sendClientText(session, payload);
            }
        } finally {
            if (userId != null) {
                MDC.remove("sessionId");
            }
        }
    }

    /**
     * LIFECYCLE EVENT 4: TRANSPORT ERROR
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
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            MDC.put("sessionId", userId);
            try {
                // Remove socket handle from local server RAM map
                activeSessions.remove(userId);

                // Retrieve active adapter from session attributes and close stream
                IAiVoiceAdapter adapter = (IAiVoiceAdapter) session.getAttributes().get("voiceAdapter");
                if (adapter != null) {
                    adapter.closeSession(session);
                }

                // Handle graceful pause for potential reconnect in SessionStateAgent
                sessionStateAgent.handleDisconnect(session.getId());

                // Delete presence key from Redis
                sessionTracker.deregisterSession(userId);

                log.info("WebSocket connection closed for user: {} (Status: {})", userId, status);
            } finally {
                MDC.remove("sessionId");
            }
        }
    }
}
