package com.reForm.backend.ai.tool.handler.filler;

import com.reForm.backend.ai.agent.SessionStateAgent;
import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: skipQuestion
 * 
 * WHY THIS TOOL EXISTS:
 * Handles optional or non-applicable questions when a respondent says "skip this" or "doesn't apply".
 * Records the skip reason in Hot Redis RAM via SessionStateAgent and advances the internal question pointer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipQuestionToolHandler implements IToolCallHandler {

    private final SessionStateAgent sessionStateAgent;

    @Override
    public String getFunctionName() {
        return "skipQuestion";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fieldId = args.path("fieldId").asText();
        String reason = args.path("reason").asText("USER_DECLINED");

        log.info("⏭️ [SKIP QUESTION HANDLER] FieldId: {}, Reason: {}", fieldId, reason);

        // Persist skipped field into Hot Redis RAM for state consistency
        sessionStateAgent.recordSkippedField(clientSession.getId(), fieldId, reason);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SKIPPED",
                "fieldId", fieldId,
                "reason", reason
            ))
        );
    }
}
