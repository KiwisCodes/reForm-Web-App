package com.reForm.backend.ai.tool.handler.file;

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
 * TOOL HANDLER: requestFileUpload
 * 
 * WHY THIS TOOL EXISTS:
 * Dynamically requests the user to upload a file mid-conversation (e.g. photo of damage, resume, ID card).
 * Renders an inline drag-and-drop zone in the browser chat UI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestFileUploadToolHandler implements IToolCallHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getFunctionName() {
        return "requestFileUpload";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String label = args.path("label").asText("Upload File");
        String acceptedTypes = args.path("acceptedTypes").asText("*/*");

        log.info("📥 [REQUEST FILE UPLOAD HANDLER] Label: {}, AcceptedTypes: {}", label, acceptedTypes);

        // Emit FILE_UPLOAD_REQUESTED event to client browser UI
        try {
            WebSocketSession safeClient = WebSocketSessionUtils.wrapSafeSession(clientSession);
            if (safeClient.isOpen()) {
                safeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "FILE_UPLOAD_REQUESTED",
                    "label", label,
                    "acceptedTypes", acceptedTypes
                ))));
            }
        } catch (IOException e) {
            log.error("Failed to send FILE_UPLOAD_REQUESTED to client browser", e);
        }

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of("status", "UPLOAD_ZONE_RENDERED", "label", label))
        );
    }
}
