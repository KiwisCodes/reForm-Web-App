package com.reForm.backend.ai.tool.handler.universal;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: searchUserDocument
 * 
 * WHY THIS TOOL EXISTS:
 * Performs in-session RAG search over uploaded user documents (PDFs, resumes, syllabi).
 * Queries pgvector HNSW index and returns matching passages for Gemini to speak out loud.
 */
@Slf4j
@Component
public class SearchUserDocumentToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "searchUserDocument";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String query = functionCall.path("args").path("query").asText();
        log.info("🔍 [SEARCH DOCUMENT HANDLER] Executing RAG search for query: {}", query);

        // Mock RAG return text (to be connected to RagSearchAgent / pgvector)
        String mockContent = "Document context retrieved for: " + query;

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "SUCCESS", "content", mockContent))
        );
    }
}
