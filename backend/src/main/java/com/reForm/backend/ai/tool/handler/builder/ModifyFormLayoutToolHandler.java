package com.reForm.backend.ai.tool.handler.builder;

import com.reForm.backend.ai.event.FormLayoutModificationEvent;
import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.form.entity.block.AbstractBlock;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TOOL HANDLER: modifyFormLayout
 * 
 * WHY THIS TOOL EXISTS:
 * Core tool for Mode 4 Form Co-Building. Dispatches FormLayoutModificationEvent
 * to Spring Event Bus when Gemini issues a verbal request to modify canvas layout blocks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModifyFormLayoutToolHandler implements IToolCallHandler {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getFunctionName() {
        return "modifyFormLayout";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String formIdStr = (String) clientSession.getAttributes().get("formId");
        UUID formId = null;
        if (formIdStr != null && !formIdStr.isBlank()) {
            try {
                formId = UUID.fromString(formIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ Invalid UUID string for formId attribute: {}", formIdStr);
            }
        }

        String userIntent = functionCall.path("args").path("userIntent").asText();
        String sessionId = clientSession.getId();
        List<AbstractBlock> targetBlocks = Collections.emptyList();

        log.info("✏️ [MODIFY LAYOUT HANDLER] FormId: {}, Intent: {}, SessionId: {}", 
                 formId, userIntent, sessionId);

        // Publish Spring Event for LayoutAgent to consume asynchronously
        eventPublisher.publishEvent(new FormLayoutModificationEvent(formId, userIntent, targetBlocks, sessionId));

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "SUCCESS", "message", "Form layout modification event dispatched"))
        );
    }
}
