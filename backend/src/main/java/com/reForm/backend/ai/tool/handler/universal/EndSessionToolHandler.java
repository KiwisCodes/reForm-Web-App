package com.reForm.backend.ai.tool.handler.universal;

import com.reForm.backend.ai.event.SessionEndedEvent;
import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * TOOL HANDLER: endSession
 * 
 * WHY THIS TOOL EXISTS:
 * AI models do not natively know how to "hang up." When a user indicates they are done,
 * this handler performs a 3-stage teardown:
 * 1. Sends SESSION_ENDED to client browser (releases mic hardware, clears audio buffers).
 * 2. Returns SESSION_ENDING toolResponse to Gemini (allows final spoken goodbye).
 * 3. Schedules asynchronous socket teardown after a 2-second Virtual Thread delay
 *    (closes Socket 2 to stop billing, closes Socket 1, cleans up Redis presence).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndSessionToolHandler implements IToolCallHandler {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public String getFunctionName() {
        return "endSession";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String reason = functionCall.path("args").path("reason").asText("USER_WRAP_UP");
        String summary = functionCall.path("args").path("summary").asText("");
        JsonNode unresolvedItems = functionCall.path("args").path("unresolvedItems");
        log.info("🔴 [END SESSION HANDLER] AI triggered endSession. Reason: {}, Summary: {}, Unresolved: {}", 
                 reason, summary, unresolvedItems.isArray() ? unresolvedItems.size() : 0);

        // Tag session attribute so GeminiLiveVoiceAdapter executes dynamic teardown on turnComplete
        clientSession.getAttributes().put("isEndingSession", Boolean.TRUE);

        // Publish SessionEndedEvent to trigger SessionStateAgent transition to TERMINATED
        UUID sessionUuid = null;
        try {
            sessionUuid = UUID.fromString(clientSession.getId());
        } catch (Exception e) {
            sessionUuid = UUID.randomUUID();
        }
        String formIdStr = (String) clientSession.getAttributes().get("formId");
        UUID formUuid = null;
        if (formIdStr != null) {
            try {
                formUuid = UUID.fromString(formIdStr);
            } catch (Exception ignored) {}
        }
        applicationEventPublisher.publishEvent(new SessionEndedEvent(sessionUuid, formUuid, null, reason));

        // Stage 1: Notify the browser that the session is ending to immediately release mic hardware
        try {
            WebSocketSession safeClient = WebSocketSessionUtils.wrapSafeSession(clientSession);
            if (safeClient.isOpen()) {
                String endPayload = objectMapper.writeValueAsString(Map.of(
                    "type", "SESSION_ENDED",
                    "reason", reason,
                    "summary", summary
                ));
                safeClient.sendMessage(new TextMessage(endPayload));
                log.info("✅ [SESSION_ENDED sent to browser UI]");
            }
        } catch (IOException e) {
            log.error("Failed to send SESSION_ENDED to browser", e);
        }

        // Fallback Watchdog (Virtual Thread): If turnComplete is not received within 8 seconds (e.g. network stall),
        // safely force teardown so sockets and Redis state are not leaked.
        Thread.ofVirtual().name("endSession-watchdog-" + clientSession.getId()).start(() -> {
            try {
                Thread.sleep(8000);
                if (clientSession.isOpen() && clientSession.getAttributes().putIfAbsent("teardownExecuted", Boolean.TRUE) == null) {
                    log.warn("⚠️ [END SESSION WATCHDOG]: turnComplete not received within 8s ceiling. Forcing graceful teardown.");
                    WebSocketSession geminiSession = (WebSocketSession) clientSession.getAttributes().get("geminiSession");
                    if (geminiSession != null && geminiSession.isOpen()) {
                        geminiSession.close(CloseStatus.NORMAL);
                    }
                    clientSession.close(CloseStatus.NORMAL);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error during endSession watchdog execution", e);
            }
        });

        // Stage 2: Return toolResponse frame so Gemini Live receives confirmation and speaks final goodbye
        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "SESSION_ENDING", "message", "Session will close after final goodbye."))
        );
    }
}
