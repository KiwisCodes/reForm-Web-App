package com.reForm.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;

// HTTP-facing request body for POST /api/v1/ai/chat/{formId} — one turn of the Mode 2 create/update
// chat. formId travels as a path variable (not a body field), mirroring BuilderController's
// {formId}/blocks route, since it's the resource being acted on and needs to be available to
// @PreAuthorize before the method body runs. workspaceId/creatorId deliberately do NOT live here
// either — those are security-relevant and always sourced from the authenticated request context
// (X-Workspace-Id header + @AuthenticationPrincipal / Authentication), never from the request body,
// the same boundary AiFormDto enforces for AI-generated content.
public record ChatRequestDto(
        @NotBlank(message = "message must not be blank")
        String message
) {
}
