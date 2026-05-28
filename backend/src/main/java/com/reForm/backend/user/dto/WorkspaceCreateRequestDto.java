package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkspaceCreateRequestDto(
        @NotNull(message = "Must have ID for owner")
        UUID uuid,
        @NotBlank(message="Every new workspace must have a name")
        @Size(min = 3, max = 255, message = "Name must be between 3-255 chars long")
        String name,
        @Size(min = 3, max = 255, message = "Description must be between 3-255 chars long")
        String description
) {
}
