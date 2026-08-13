package com.reForm.backend.ai.service;

import com.reForm.backend.ai.dto.AiBlockDto;
import com.reForm.backend.ai.dto.AiFormDto;
import com.reForm.backend.ai.parser.AiResponseParser;
import com.reForm.backend.ai.port.IAiChatClient;
import com.reForm.backend.ai.prompt.FormChatPromptBuilder;
import com.reForm.backend.ai.schema.BlockSchemaGenerator;
import com.reForm.backend.ai.schema.SchemaDialect;
import com.reForm.backend.ai.session.ChatRole;
import com.reForm.backend.ai.session.ChatSessionStore;
import com.reForm.backend.ai.session.ChatTurn;
import com.reForm.backend.auth.security.FormSecurity;
import com.reForm.backend.auth.security.WorkspaceSecurity;
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
// The create-a-new-form case (AiFormDto path) is handled by startFormCreationSession below.
// Session identity for it is a genuinely different problem than the update flow's: there's no
// formId yet to derive a deterministic sessionId from, so the caller mints one (a "draft" id,
// e.g. a UUID generated once when the user opens a "create form" chat) and passes it in — safe to
// accept as client-supplied for the same reason formId-as-sessionId is safe for the update flow:
// ChatSessionStore additionally namespaces every key by userId, so a guessed or reused draft id
// only ever resolves to *your own* draft conversation, never another user's.
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
    private final WorkspaceSecurity workspaceSecurity;

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

    // Orchestrates one "create a new form from scratch" chat turn. Gated on workspace membership,
    // not form membership — the form doesn't exist yet, so FormSecurity has nothing to check
    // against. Once Gemini's title+blocks response is applied and a real form (with a real
    // formId) exists, the draft conversation is folded into that form's own deterministic
    // sessionId (formId.toString(), matching continueFormEditSession exactly) so the very next
    // follow-up edit already has full context instead of starting over. The draft sessionId's key
    // is left for Redis to expire on its own TTL rather than deleted explicitly, consistent with
    // every other session-cleanup decision in this package.
    public FormResponseDto startFormCreationSession(Authentication authentication, UUID workspaceId,
                                                       String draftSessionId, String message) {
        if (!workspaceSecurity.isMember(authentication, workspaceId)) {
            throw new AccessDeniedException("Not a member of this workspace");
        }
        UUID userId = workspaceSecurity.getCurrentUserId(authentication)
                .orElseThrow(() -> new AccessDeniedException("No authenticated user"));

        List<ChatTurn> draftHistory = chatSessionStore.getHistory(userId.toString(), draftSessionId);

        String systemInstruction = promptBuilder.buildSystemInstruction();
        List<Map<String, Object>> contents = promptBuilder.buildConversationContents(draftHistory, message);
        Map<String, Object> schema = schemaGenerator.generateFormSchema(SchemaDialect.GEMINI);

        String rawResponse = aiChatClient.generateFormResponse(systemInstruction, contents, schema);
        AiFormDto aiForm = responseParser.parseForm(rawResponse);

        FormResponseDto result = blockApplicationService.createFormFromAiBlocks(aiForm, workspaceId, userId);

        String formSessionId = result.id().toString();
        for (ChatTurn turn : draftHistory) {
            chatSessionStore.appendTurn(userId.toString(), formSessionId, turn);
        }
        chatSessionStore.appendTurn(userId.toString(), formSessionId, new ChatTurn(ChatRole.USER, message));
        chatSessionStore.appendTurn(userId.toString(), formSessionId, new ChatTurn(ChatRole.MODEL, rawResponse));

        return result;
    }
}
