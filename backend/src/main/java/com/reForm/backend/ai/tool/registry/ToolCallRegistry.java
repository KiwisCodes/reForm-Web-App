package com.reForm.backend.ai.tool.registry;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TOOL CALL REGISTRY (Strategy Pattern Registry)
 * 
 * ROLE IN ARCHITECTURE:
 * Automatically collects all Spring @Component beans implementing IToolCallHandler
 * into a lookup Map. Routes incoming toolCalls from Gemini Live to their matching handler.
 * 
 * WHY THIS IS SCALABLE:
 * Adding a new tool handler requires zero modifications to this class or GeminiLiveVoiceAdapter.
 * Simply create a new class implementing IToolCallHandler, annotate with @Component, and Spring
 * auto-wires it into this registry at startup!
 */
@Slf4j
@Service
public class ToolCallRegistry {

    private final Map<String, IToolCallHandler> handlerMap;

    public ToolCallRegistry(List<IToolCallHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(IToolCallHandler::getFunctionName, Function.identity()));
        log.info("✅ [TOOL REGISTRY INITIALIZED]: Registered {} AI tool call handlers: {}", 
                 handlerMap.size(), handlerMap.keySet());
    }

    /**
     * Executes the requested tool call or falls back to a generic SUCCESS response
     * if an explicit handler bean is not registered yet.
     */
    public Map<String, Object> executeTool(WebSocketSession clientSession, JsonNode functionCall, String callId, String functionName) {
        IToolCallHandler handler = handlerMap.get(functionName);

        if (handler != null) {
            log.info("🎯 Executing Tool Handler: {} (id: {})", functionName, callId);
            return handler.execute(clientSession, functionCall, callId);
        }

        log.warn("ℹ️ No explicit handler bean found for tool '{}' (id: {}). Returning generic fallback success.", functionName, callId);
        return Map.of(
            "id", callId,
            "name", functionName,
            "response", Map.of("result", Map.of("status", "SUCCESS"))
        );
    }
}
