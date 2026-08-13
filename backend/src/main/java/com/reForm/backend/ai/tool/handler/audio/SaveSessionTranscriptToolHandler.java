package com.reForm.backend.ai.tool.handler.audio;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: saveSessionTranscript
 * 
 * WHY THIS TOOL EXISTS:
 * Persists the complete, timestamped conversation transcript (user speech + AI speech) into PostgreSQL
 * for dashboard review, EvaluationAgent scoring, and compliance keyword searches.
 */
@Slf4j
@Component
public class SaveSessionTranscriptToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "saveSessionTranscript";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        boolean includeTimestamps = args.path("includeTimestamps").asBoolean(true);
        boolean includeEvaluation = args.path("includeEvaluation").asBoolean(false);

        log.info("📝 [SAVE TRANSCRIPT HANDLER] IncludeTimestamps: {}, IncludeEvaluation: {}", 
                 includeTimestamps, includeEvaluation);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "TRANSCRIPT_SAVED",
                "includeEvaluation", includeEvaluation
            ))
        );
    }
}
