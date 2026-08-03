package com.reForm.backend.ai.prompt;

import com.reForm.backend.ai.session.ChatRole;
import com.reForm.backend.ai.session.ChatTurn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Assembles the Mode 2 prompt: system instruction + conversation contents, in the exact shape
// confirmed against real Gemini via the ai-test.html harness (contents: [{role, parts:[{text}]}]).
// Plain concrete class, not a port — kept independent of SessionContextService (see week3/mode2
// docs for the full reasoning): reuse via a shared method risked coupling to a file under active
// development elsewhere and would need extra work to strip out voice-only fields (tools) anyway,
// for no gain since FormAiAgentProfile integration is deliberately deferred either way.
// The system instruction is isolated behind one method rather than inlined, so swapping the
// hardcoded constant for a FormAiAgentProfile-sourced template later is a one-line change.
@Component
public class FormChatPromptBuilder {

    // Need more details
    private static final String SYSTEM_INSTRUCTION =
            "You are a form-building assistant. Based on the user's message and the form's "
                    + "current blocks (if any), decide the complete revised set of blocks. "
                    + "Only use fields and values allowed by the provided schema.";

    public String buildSystemInstruction() {
        return SYSTEM_INSTRUCTION;
    }

    public List<Map<String, Object>> buildConversationContents(List<ChatTurn> history, String newUserMessage) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            contents.add(toContentEntry(turn.role(), turn.content()));
        }
        contents.add(toContentEntry(ChatRole.USER, newUserMessage));
        return contents;
    }

    private Map<String, Object> toContentEntry(ChatRole role, String text) {
        String geminiRole = role == ChatRole.USER ? "user" : "model";
        return Map.of("role", geminiRole, "parts", List.of(Map.of("text", text)));
    }
}
