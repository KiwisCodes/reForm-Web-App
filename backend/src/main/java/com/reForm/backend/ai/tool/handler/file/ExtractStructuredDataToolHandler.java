package com.reForm.backend.ai.tool.handler.file;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: extractStructuredData
 * 
 * WHY THIS TOOL EXISTS:
 * Extracts key-value fields (e.g., name, email, skills, dates) from uploaded resumes, invoices, or IDs
 * for auto-filling form fields.
 */
@Slf4j
@Component
public class ExtractStructuredDataToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "extractStructuredData";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fileId = args.path("fileId").asText();
        String fieldsToExtract = args.path("fieldsToExtract").asText("");

        log.info("📋 [EXTRACT STRUCTURED DATA HANDLER] FileId: {}, Fields: {}", fileId, fieldsToExtract);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "EXTRACTED",
                "fileId", fileId,
                "extractedFields", fieldsToExtract
            ))
        );
    }
}
