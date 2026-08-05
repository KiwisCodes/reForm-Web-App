package com.reForm.backend.ai.tool.handler.filler;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: saveFieldResponse
 * 
 * WHY THIS TOOL EXISTS:
 * When a Form Filler provides an answer during a voice or text interview, this tool persists
 * the response immediately to PostgreSQL to prevent data loss if the connection drops.
 */
@Slf4j
@Component
public class SaveFieldResponseToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "saveFieldResponse";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fieldId = args.path("fieldId").asText();
        String value = args.path("value").asText();
        double confidence = args.path("confidence").asDouble(1.0);

        log.info("💾 [SAVE FIELD RESPONSE HANDLER] FieldId: {}, Value: {}, Confidence: {}", 
                 fieldId, value, confidence);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SAVED",
                "fieldId", fieldId,
                "savedValue", value
            ))
        );
    }
}
