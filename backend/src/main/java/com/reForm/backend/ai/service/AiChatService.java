package com.reForm.backend.ai.service;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.parser.AiResponseParser;
import com.reForm.backend.ai.port.IAiChatClient;
import com.reForm.backend.ai.prompt.FormChatPromptBuilder;
import com.reForm.backend.ai.schema.BlockSchemaGenerator;
import com.reForm.backend.ai.schema.SchemaDialect;
import com.reForm.backend.ai.session.ChatRole;
import com.reForm.backend.ai.session.ChatSessionStore;
import com.reForm.backend.ai.session.ChatTurn;
import com.reForm.backend.auth.security.FormSecurity;
import com.reForm.backend.form.dto.FormResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Orchestrates one Part 2 "update an existing form" chat turn: validate ownership, load history,
// build the prompt, call Gemini, parse the result, apply it, persist the turn. Deliberately
// controller-independent (Authentication/UUIDs in, FormResponseDto out) so a future @EventListener
// entry point (voice mode's Layout Agent) could call this same method later without rework — which
// is also why ownership is checked here directly via FormSecurity rather than relying solely on a
// controller's @PreAuthorize: an event-driven caller wouldn't go through that annotation at all.
//
// sessionId is always formId.toString() here, not a client-supplied value — this is the Problem 1
// fix from the week3/mode2 docs (deterministic session identity, so returning to a form's builder
// resumes the same conversation instead of a fresh random one each visit). Safe to reuse formId
// this way specifically because ChatSessionStore additionally namespaces by userId, and formId
// itself is validated against real ownership below before anything happens.
//
// The create-a-new-form case (AiFormDto path) is deliberately not handled here yet: whether a
// multi-turn "describe, refine, then create" conversation folds into this same update path once
// the form exists (and under which sessionId) is still an open product decision, not a code gap —
// see week3/mode2 docs.
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatSessionStore chatSessionStore;
    private final FormChatPromptBuilder promptBuilder;
    private final IAiChatClient aiChatClient;
    private final BlockSchemaGenerator schemaGenerator;
    private final AiResponseParser responseParser;
    private final AiBlockApplicationService blockApplicationService;
    private final FormSecurity formSecurity;

    public FormResponseDto continueFormEditSession(Authentication authentication, UUID workspaceId,
                                                     UUID formId, String message) {
        if (!formSecurity.isMember(authentication, formId)) {
            throw new AccessDeniedException("Not a member of the workspace this form belongs to");
        }
        UUID userId = formSecurity.getCurrentUserId(authentication)
                .orElseThrow(() -> new AccessDeniedException("No authenticated user"));

        String sessionId = formId.toString();
        List<ChatTurn> history = chatSessionStore.getHistory(userId.toString(), sessionId);

        String systemInstruction = promptBuilder.buildSystemInstruction();
        List<Map<String, Object>> contents = promptBuilder.buildConversationContents(history, message);
        Map<String, Object> schema = schemaGenerator.generateBlocksArraySchema(SchemaDialect.GEMINI);

        String rawResponse = aiChatClient.generateFormResponse(systemInstruction, contents, schema);
        List<AiBlockDto> blocks = responseParser.parseBlocks(rawResponse);

        FormResponseDto result = blockApplicationService.updateFormFromAiBlocks(formId, workspaceId, blocks);

        chatSessionStore.appendTurn(userId.toString(), sessionId, new ChatTurn(ChatRole.USER, message));
        chatSessionStore.appendTurn(userId.toString(), sessionId, new ChatTurn(ChatRole.MODEL, rawResponse));

        return result;
    }
}
