package com.reForm.backend.ai.tool.handler.ui;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * TOOL HANDLER: renderDynamicUI
 * 
 * WHY THIS TOOL EXISTS:
 * Pushes interactive React UI components (buttons, rating stars, date pickers, sliders) into the browser
 * chat UI so respondents can click options instead of typing or speaking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenderDynamicUIToolHandler implements IToolCallHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getFunctionName() {
        return "renderDynamicUI";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String componentType = args.path("componentType").asText("BUTTONS");
        String options = args.path("options").asText("");
        String prompt = args.path("prompt").asText("");

        log.info("🎨 [RENDER DYNAMIC UI HANDLER] Component: {}, Prompt: {}, Options: {}", 
                 componentType, prompt, options);

        // Push DYNAMIC_UI_REQUESTED frame to client browser
        try {
            WebSocketSession safeClient = WebSocketSessionUtils.wrapSafeSession(clientSession);
            if (safeClient.isOpen()) {
                safeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "DYNAMIC_UI_REQUESTED",
                    "componentType", componentType,
                    "prompt", prompt,
                    "options", options
                ))));
            }
        } catch (IOException e) {
            log.error("Failed to send DYNAMIC_UI_REQUESTED to client browser", e);
        }

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "UI_RENDERED",
                "componentType", componentType
            ))
        );
    }
}
