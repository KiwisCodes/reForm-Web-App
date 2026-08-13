package com.reForm.backend.ai.controller;

import com.reForm.backend.ai.dto.ChatRequestDto;
import com.reForm.backend.ai.dto.ChatResponseDto;
import com.reForm.backend.ai.service.AiChatService;
import com.reForm.backend.form.dto.FormResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Thin HTTP layer over AiChatService: translates HTTP <-> the orchestrator, nothing more — same
// Single Responsibility split the week3/mode2 package-architecture doc calls for. Reuses
// BuilderController's exact auth patterns rather than inventing new ones: {formId} path variable +
// formSecurity for the update flow (mirrors {formId}/blocks), #workspaceId header + workspaceSecurity
// for the create flow (mirrors getAllForms's own workspace-membership gate, since no form/formId
// exists yet to check against).
@RestController
@RequestMapping("api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("chat/{formId}")
    @PreAuthorize("@formSecurity.isMember(authentication, #formId)")
    public ResponseEntity<ChatResponseDto> chat(
            @PathVariable UUID formId,
            @RequestHeader("X-Workspace-Id") UUID workspaceId,
            @RequestBody @Valid ChatRequestDto request,
            Authentication authentication) {
        FormResponseDto form = aiChatService.continueFormEditSession(
                authentication, workspaceId, formId, request.message());
        return ResponseEntity.status(HttpStatus.OK).body(new ChatResponseDto(form));
    }

    // draftSessionId is minted by the caller (e.g. once when a "create form" chat is opened), not
    // the server — see AiChatService.startFormCreationSession's own reasoning for why that's safe
    // (ChatSessionStore always namespaces by userId too). Kept as a path variable, same shape as
    // the update endpoint's {formId}, rather than folding it into ChatRequestDto, so both endpoints
    // share one "resource id in the path, message in the body" request shape.
    @PostMapping("chat/new/{draftSessionId}")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<ChatResponseDto> startNewForm(
            @PathVariable String draftSessionId,
            @RequestHeader("X-Workspace-Id") UUID workspaceId,
            @RequestBody @Valid ChatRequestDto request,
            Authentication authentication) {
        FormResponseDto form = aiChatService.startFormCreationSession(
                authentication, workspaceId, draftSessionId, request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ChatResponseDto(form));
    }
}
