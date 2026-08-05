package com.reForm.backend.ai.tool.handler.filler;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * TOOL HANDLER: lookupFormProgress
 * 
 * WHY THIS TOOL EXISTS:
 * Returns the current completion progress for long forms. Lets the AI answer "How many questions left?"
 * and announce progress ("We're 50% done!").
 */
@Slf4j
@Component
public class LookupFormProgressToolHandler implements IToolCallHandler {

    @Override
    public String getFunctionName() {
        return "lookupFormProgress";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        log.info("📈 [LOOKUP PROGRESS HANDLER] Querying form completion status");

        // Mock progress status (to be wired to submission goal tracker)
        int totalFields = 10;
        int answeredFields = 5;
        int percentComplete = 50;

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SUCCESS",
                "totalFields", totalFields,
                "answeredFields", answeredFields,
                "remainingFields", totalFields - answeredFields,
                "percentComplete", percentComplete
            ))
        );
    }
}
