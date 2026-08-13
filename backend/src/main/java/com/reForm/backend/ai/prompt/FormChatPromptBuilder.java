package com.reForm.backend.ai.prompt;

import com.reForm.backend.ai.session.ChatRole;
import com.reForm.backend.ai.session.ChatTurn;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Assembles the Mode 2 prompt: system instruction + conversation contents, in the exact shape
// confirmed against real Gemini via the ai-test.html harness (contents: [{role, parts:[{text}]}]).
// Plain concrete class, not a port — kept independent of SessionContextService (see week3/mode2
// docs for the full reasoning): reuse via a shared method risked coupling to a file under active
// development elsewhere and would need extra work to strip out voice-only fields (tools) anyway,
// for no gain since FormAiAgentProfile integration is deliberately deferred either way.
// Loads system instructions dynamically from /prompts/mode2_system_instruction.txt on classpath.
@Component
public class FormChatPromptBuilder {

    private final String systemInstruction;

    public FormChatPromptBuilder() {
        try (InputStream is = getClass().getResourceAsStream("/prompts/mode2_system_instruction.txt")) {
            if (is == null) {
                throw new IllegalStateException("Could not find /prompts/mode2_system_instruction.txt on classpath");
            }
            this.systemInstruction = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Mode 2 system instruction prompt resource", e);
        }
    }

    public String buildSystemInstruction() {
        return systemInstruction;
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
