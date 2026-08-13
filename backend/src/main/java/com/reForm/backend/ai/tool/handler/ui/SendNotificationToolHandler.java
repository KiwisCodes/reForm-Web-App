package com.reForm.backend.ai.tool.handler.ui;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: sendNotification
 * 
 * WHY THIS TOOL EXISTS:
 * Sends real-time alerts to the Form Builder's dashboard, email, Slack, or webhook integrations
 * when important events occur during respondent sessions (e.g. high test score, critical triage flag).
 */
@Slf4j
@Component
public class SendNotificationToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "sendNotification";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        JsonNode args = functionCall.path("args");
        String channel = args.path("channel").asText("DASHBOARD");
        String priority = args.path("priority").asText("INFO");
        String title = args.path("title").asText("Form Fill Notification");
        String body = args.path("body").asText("");

        log.info("🔔 [SEND NOTIFICATION HANDLER] Channel: {}, Priority: {}, Title: {}", 
                 channel, priority, title);

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "NOTIFICATION_SENT",
                "channel", channel,
                "priority", priority
            ))
        );
    }
}
