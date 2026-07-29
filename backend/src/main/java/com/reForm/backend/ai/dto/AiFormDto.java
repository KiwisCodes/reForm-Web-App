package com.reForm.backend.ai.dto;

import java.util.List;

// What the AI returns for a whole form-creation turn: title + blocks. Deliberately does NOT
// carry workspaceId/creatorId — those are a security boundary (which workspace, whose account),
// and must always come from the authenticated request context, never from generated content.
// See FormFactory.
public record  AiFormDto(
        String title,
        List<AiBlockDto> blocks
) {
}
