package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record WorkspaceAddMemberReqestDto(
        @NotBlank(message = "Must add at least 1 new member")
        Set<String> emails
) {
}
