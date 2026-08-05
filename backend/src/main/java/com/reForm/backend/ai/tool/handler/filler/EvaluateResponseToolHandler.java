package com.reForm.backend.ai.tool.handler.filler;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: evaluateResponse
 * 
 * WHY THIS TOOL EXISTS:
 * Evaluates respondent answers against grading criteria for quizzes, technical interviews,
 * and medical triage. Records numeric score (0-100), feedback text, and category tags.
 */
@Slf4j
@Component
public class EvaluateResponseToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "evaluateResponse";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fieldId = args.path("fieldId").asText();
        double score = args.path("score").asDouble(0.0);
        String feedback = args.path("feedback").asText("");
        String tags = args.path("tags").asText("");

        log.info("📊 [EVALUATE RESPONSE HANDLER] FieldId: {}, Score: {}, Tags: {}", fieldId, score, tags);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "EVALUATED",
                "fieldId", fieldId,
                "score", score,
                "feedback", feedback
            ))
        );
    }
}
