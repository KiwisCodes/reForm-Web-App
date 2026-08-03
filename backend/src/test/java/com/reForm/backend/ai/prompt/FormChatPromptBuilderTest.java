package com.reForm.backend.ai.prompt;

import com.reForm.backend.ai.session.ChatRole;
import com.reForm.backend.ai.session.ChatTurn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormChatPromptBuilderTest {

    private final FormChatPromptBuilder builder = new FormChatPromptBuilder();

    @Test
    void buildSystemInstructionReturnsNonBlankConstant() {
        String instruction = builder.buildSystemInstruction();
        assertTrue(instruction != null && !instruction.isBlank());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildConversationContentsMapsHistoryThenAppendsNewUserMessage() {
        List<ChatTurn> history = List.of(
                new ChatTurn(ChatRole.USER, "Add a name field"),
                new ChatTurn(ChatRole.MODEL, "Added a short text field for name")
        );

        List<Map<String, Object>> contents = builder.buildConversationContents(history, "Now add an email field");

        assertEquals(3, contents.size());

        assertEquals("user", contents.get(0).get("role"));
        assertEquals("Add a name field",
                ((Map<String, Object>) ((List<?>) contents.get(0).get("parts")).get(0)).get("text"));

        assertEquals("model", contents.get(1).get("role"));
        assertEquals("Added a short text field for name",
                ((Map<String, Object>) ((List<?>) contents.get(1).get("parts")).get(0)).get("text"));

        assertEquals("user", contents.get(2).get("role"));
        assertEquals("Now add an email field",
                ((Map<String, Object>) ((List<?>) contents.get(2).get("parts")).get(0)).get("text"));
    }

    @Test
    void buildConversationContentsWithEmptyHistoryOnlyHasTheNewMessage() {
        List<Map<String, Object>> contents = builder.buildConversationContents(List.of(), "Add a phone field");

        assertEquals(1, contents.size());
        assertFalse(contents.getFirst().isEmpty());
    }
}
