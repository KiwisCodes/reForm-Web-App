package com.reForm.backend.ai.tool.registry;

import com.reForm.backend.ai.tool.port.ChatToolExecutionContext;
import com.reForm.backend.ai.tool.port.IMode2ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TOOL CALL REGISTRY — MODE 2 (Strategy Pattern Registry)
 *
 * Mirrors ToolCallRegistry exactly, one level down: Spring auto-collects every IMode2ToolHandler
 * bean into handlerMap at startup via constructor injection alone, same as ToolCallRegistry does
 * for IToolCallHandler — no separate @Configuration class needed for either.
 */
@Slf4j
@Service
public class Mode2ToolRegistry {

    private final Map<String, IMode2ToolHandler> handlerMap;

    public Mode2ToolRegistry(List<IMode2ToolHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(IMode2ToolHandler::getFunctionName, Function.identity()));
        log.info("✅ [MODE 2 TOOL REGISTRY INITIALIZED]: Registered {} tool handlers: {}",
                 handlerMap.size(), handlerMap.keySet());
    }

    /**
     * Executes the requested tool call or falls back to a generic SUCCESS response
     * if an explicit handler bean is not registered yet.
     */
    public Map<String, Object> executeTool(ChatToolExecutionContext context, JsonNode functionCall, String callId, String functionName) {
        IMode2ToolHandler handler = handlerMap.get(functionName);

        if (handler != null) {
            log.info("🎯 [MODE 2] Executing Tool Handler: {} (id: {})", functionName, callId);
            return handler.execute(context, functionCall, callId);
        }

        log.warn("ℹ️ [MODE 2] No handler bean found for tool '{}' (id: {}). Returning generic fallback success.", functionName, callId);
        return Map.of(
            "id", callId,
            "name", functionName,
            "response", Map.of("result", Map.of("status", "SUCCESS"))
        );
    }
}
