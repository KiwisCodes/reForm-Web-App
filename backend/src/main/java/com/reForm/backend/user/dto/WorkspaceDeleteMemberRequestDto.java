package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record WorkspaceDeleteMemberRequestDto(
        @NotEmpty(message = "Must delete at least 1 member")
        Set<String> emails
        //not blank can only be put on strings,
) {
}
