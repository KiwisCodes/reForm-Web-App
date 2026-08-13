package com.reForm.backend.ai.tool.handler.universal;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

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

    @Override
    public String getFunctionName() {
        return "endSession";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String reason = functionCall.path("args").path("reason").asText("USER_REQUESTED");
        String summary = functionCall.path("args").path("summary").asText("");
        log.info("🔴 [END SESSION HANDLER] AI triggered endSession. Reason: {}, Summary: {}", reason, summary);

        // Stage 1: Notify the browser that the session is ending
        try {
            WebSocketSession safeClient = WebSocketSessionUtils.wrapSafeSession(clientSession);
            if (safeClient.isOpen()) {
                String endPayload = objectMapper.writeValueAsString(Map.of(
                    "type", "SESSION_ENDED",
                    "reason", reason,
                    "summary", summary
                ));
                safeClient.sendMessage(new TextMessage(endPayload));
                log.info("✅ [SESSION_ENDED sent to browser]");
            }
        } catch (IOException e) {
            log.error("Failed to send SESSION_ENDED to browser", e);
        }

        // Stage 2: Schedule Socket 2 & Socket 1 teardown after 2s grace period on Virtual Thread
        Thread.ofVirtual().name("endSession-cleanup").start(() -> {
            try {
                // Grace period to allow Gemini to speak final goodbye audio
                Thread.sleep(2000);

                // Close Socket 2: Gemini WSS -> STOPS BILLING
                WebSocketSession geminiSession = (WebSocketSession) clientSession.getAttributes().get("geminiSession");
                if (geminiSession != null && geminiSession.isOpen()) {
                    geminiSession.close(CloseStatus.NORMAL);
                    log.info("✅ [Socket 2 CLOSED] Gemini WSS connection closed -> billing stopped");
                }

                // Close Socket 1: Browser WSS -> Triggers afterConnectionClosed & Redis cleanup
                if (clientSession.isOpen()) {
                    clientSession.close(CloseStatus.NORMAL);
                    log.info("✅ [Socket 1 CLOSED] Browser WSS connection closed -> Redis cleanup triggered");
                }
            } catch (Exception e) {
                log.error("Error during endSession socket teardown", e);
            }
        });

        // Stage 3: Return toolResponse frame so Gemini Live receives confirmation
        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "SESSION_ENDING", "message", "Session will close after final goodbye."))
        );
    }
}
