package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record WorkspaceAddMemberRequestDto(
        @NotBlank(message = "Must add at least 1 new member")
        Set<String> emails
        //do not put
        //when frontend send ot backend, it sends json, -> map to request dto
) {
}
