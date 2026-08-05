package com.reForm.backend.ai.tool.handler.filler;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: flagForHumanReview
 * 
 * WHY THIS TOOL EXISTS:
 * Flags suspicious, ambiguous, or high-priority answers (e.g. medical emergency, resume inconsistency)
 * for manual human review by the form owner.
 */
@Slf4j
@Component
public class FlagForHumanReviewToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "flagForHumanReview";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fieldId = args.path("fieldId").asText(null);
        String priority = args.path("priority").asText("HIGH");
        String reason = args.path("reason").asText("UNCERTAIN_ANSWER");

        log.info("🚩 [FLAG FOR HUMAN REVIEW] Priority: {}, Reason: {}, FieldId: {}", priority, reason, fieldId);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "FLAGGED",
                "priority", priority,
                "reason", reason
            ))
        );
    }
}
