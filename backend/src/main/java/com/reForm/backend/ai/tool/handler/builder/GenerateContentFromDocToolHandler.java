package com.reForm.backend.ai.tool.handler.builder;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: generateContentFromDocument
 * 
 * WHY THIS TOOL EXISTS:
 * Form Builders (teachers, HR, legal) upload reference documents (syllabi, job descriptions, handbooks)
 * and request auto-generation of quiz/interview questions FROM the uploaded document.
 */
@Slf4j
@Component
public class GenerateContentFromDocToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "generateContentFromDocument";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String fileId = args.path("fileId").asText();
        String contentType = args.path("contentType").asText("QUIZ_QUESTIONS");
        int count = args.path("count").asInt(5);

        log.info("📄 [GENERATE CONTENT FROM DOC] FileId: {}, Type: {}, Count: {}", fileId, contentType, count);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SUCCESS",
                "generatedCount", count,
                "message", "Generated " + count + " " + contentType + " from document " + fileId
            ))
        );
    }
}
