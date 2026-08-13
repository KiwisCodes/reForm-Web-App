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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Every real dependency here is already independently verified elsewhere (ChatSessionStore against
// real Redis, GeminiChatClient against real Gemini, BlockSchemaGenerator structurally) — this test
// mocks them to verify AiChatService's own job: orchestration order and correct wiring between
// them, not whether each dependency itself works.
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock private ChatSessionStore chatSessionStore;
    @Mock private FormChatPromptBuilder promptBuilder;
    @Mock private IAiChatClient aiChatClient;
    @Mock private BlockSchemaGenerator schemaGenerator;
    @Mock private AiResponseParser responseParser;
    @Mock private AiBlockApplicationService blockApplicationService;
    @Mock private FormSecurity formSecurity;
    @Mock private WorkspaceSecurity workspaceSecurity;
    @Mock private Authentication authentication;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID formId = UUID.randomUUID();

    private AiChatService newService() {
        return new AiChatService(chatSessionStore, promptBuilder, aiChatClient,
                schemaGenerator, responseParser, blockApplicationService, formSecurity, workspaceSecurity);
    }

    @Test
    void rejectsTheTurnWithoutCallingAnythingElseWhenCallerIsNotAWorkspaceMember() {
        AiChatService service = newService();

        when(formSecurity.isMember(authentication, formId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.continueFormEditSession(authentication, workspaceId, formId, "Add a name field"));

        verifyNoInteractions(chatSessionStore, promptBuilder, aiChatClient, schemaGenerator,
                responseParser, blockApplicationService);
    }

    @Test
    void ownershipCheckedFirstUsesFormIdAsSessionIdAndPersistsBothTurnsInOrder() {
        AiChatService service = newService();

        String sessionId = formId.toString();
        List<ChatTurn> history = List.of(new ChatTurn(ChatRole.USER, "earlier message"));
        List<Map<String, Object>> contents = List.of(Map.of("role", "user", "parts", List.of()));
        Map<String, Object> schema = Map.of("type", "ARRAY");
        String rawResponse = "[{\"staticType\":\"SHORT_TEXT\",\"label\":\"Name\"}]";
        List<AiBlockDto> parsedBlocks = List.of();
        FormResponseDto expectedResult = new FormResponseDto(formId, "Title", null, List.of(), null, "slug");

        when(formSecurity.isMember(authentication, formId)).thenReturn(true);
        when(formSecurity.getCurrentUserId(authentication)).thenReturn(Optional.of(userId));
        when(chatSessionStore.getHistory(userId.toString(), sessionId)).thenReturn(history);
        when(promptBuilder.buildSystemInstruction()).thenReturn("system instruction");
        when(promptBuilder.buildConversationContents(history, "Add a name field")).thenReturn(contents);
        when(schemaGenerator.generateBlocksArraySchema(SchemaDialect.GEMINI)).thenReturn(schema);
        when(aiChatClient.generateFormResponse("system instruction", contents, schema)).thenReturn(rawResponse);
        when(responseParser.parseBlocks(rawResponse)).thenReturn(parsedBlocks);
        when(blockApplicationService.updateFormFromAiBlocks(formId, workspaceId, parsedBlocks))
                .thenReturn(expectedResult);

        FormResponseDto actualResult =
                service.continueFormEditSession(authentication, workspaceId, formId, "Add a name field");

        assertEquals(expectedResult, actualResult);

        ArgumentCaptor<ChatTurn> turnCaptor = ArgumentCaptor.forClass(ChatTurn.class);
        verify(chatSessionStore, times(2))
                .appendTurn(eq(userId.toString()), eq(sessionId), turnCaptor.capture());

        List<ChatTurn> appendedTurns = turnCaptor.getAllValues();
        assertEquals(new ChatTurn(ChatRole.USER, "Add a name field"), appendedTurns.get(0));
        assertEquals(new ChatTurn(ChatRole.MODEL, rawResponse), appendedTurns.get(1));
    }

    @Test
    void rejectsFormCreationWithoutCallingAnythingElseWhenCallerIsNotAWorkspaceMember() {
        AiChatService service = newService();
        String draftSessionId = UUID.randomUUID().toString();

        when(workspaceSecurity.isMember(authentication, workspaceId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.startFormCreationSession(authentication, workspaceId, draftSessionId, "A feedback form"));

        verifyNoInteractions(chatSessionStore, promptBuilder, aiChatClient, schemaGenerator,
                responseParser, blockApplicationService);
    }

    @Test
    void createsFormUsingFormSchemaThenMigratesDraftHistoryUnderTheNewFormsSessionId() {
        AiChatService service = newService();
        String draftSessionId = UUID.randomUUID().toString();

        List<ChatTurn> draftHistory = List.of(new ChatTurn(ChatRole.USER, "earlier refinement message"));
        List<Map<String, Object>> contents = List.of(Map.of("role", "user", "parts", List.of()));
        Map<String, Object> schema = Map.of("type", "OBJECT");
        String rawResponse = "{\"title\":\"Feedback\",\"blocks\":[]}";
        AiFormDto parsedForm = new AiFormDto("Feedback", List.of());
        FormResponseDto expectedResult = new FormResponseDto(formId, "Feedback", null, List.of(), null, "slug");

        when(workspaceSecurity.isMember(authentication, workspaceId)).thenReturn(true);
        when(workspaceSecurity.getCurrentUserId(authentication)).thenReturn(Optional.of(userId));
        when(chatSessionStore.getHistory(userId.toString(), draftSessionId)).thenReturn(draftHistory);
        when(promptBuilder.buildSystemInstruction()).thenReturn("system instruction");
        when(promptBuilder.buildConversationContents(draftHistory, "A feedback form")).thenReturn(contents);
        when(schemaGenerator.generateFormSchema(SchemaDialect.GEMINI)).thenReturn(schema);
        when(aiChatClient.generateFormResponse("system instruction", contents, schema)).thenReturn(rawResponse);
        when(responseParser.parseForm(rawResponse)).thenReturn(parsedForm);
        when(blockApplicationService.createFormFromAiBlocks(parsedForm, workspaceId, userId))
                .thenReturn(expectedResult);

        FormResponseDto actualResult =
                service.startFormCreationSession(authentication, workspaceId, draftSessionId, "A feedback form");

        assertEquals(expectedResult, actualResult);

        String formSessionId = formId.toString();
        ArgumentCaptor<ChatTurn> turnCaptor = ArgumentCaptor.forClass(ChatTurn.class);
        verify(chatSessionStore, times(3))
                .appendTurn(eq(userId.toString()), eq(formSessionId), turnCaptor.capture());
        verify(chatSessionStore, times(0)).appendTurn(eq(userId.toString()), eq(draftSessionId), any());

        List<ChatTurn> appendedTurns = turnCaptor.getAllValues();
        assertEquals(draftHistory.get(0), appendedTurns.get(0));
        assertEquals(new ChatTurn(ChatRole.USER, "A feedback form"), appendedTurns.get(1));
        assertEquals(new ChatTurn(ChatRole.MODEL, rawResponse), appendedTurns.get(2));
    }
}
