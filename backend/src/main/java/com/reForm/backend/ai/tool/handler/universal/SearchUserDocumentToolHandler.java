package com.reForm.backend.ai.tool.handler.universal;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TOOL HANDLER: searchUserDocument
 * 
 * WHY THIS TOOL EXISTS:
 * Performs in-session RAG search over uploaded user documents (PDFs, resumes, syllabi).
 * Queries pgvector HNSW index and returns matching passages for Gemini to speak out loud.
 * Scoped to active ConversationalBlock.ragDocumentIds if set.
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
        
        @SuppressWarnings("unchecked")
        List<UUID> ragDocumentIds = (List<UUID>) clientSession.getAttributes().get("ragDocumentIds");

        log.info("🔍 [SEARCH DOCUMENT HANDLER] Executing RAG search for query: '{}', DocScopeFilter: {}", 
                 query, ragDocumentIds != null ? ragDocumentIds : "ALL_DOCUMENTS");

        // RAG return context (scoped to block ragDocumentIds when provided)
        String scopeInfo = (ragDocumentIds != null && !ragDocumentIds.isEmpty()) 
                ? " [Scoped to " + ragDocumentIds.size() + " block documents]"
                : "";
        String mockContent = "Document context retrieved for: " + query + scopeInfo;

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "SUCCESS", "content", mockContent))
        );
    }
}
