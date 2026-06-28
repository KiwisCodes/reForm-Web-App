package com.reForm.backend.user.dto;

import com.reForm.backend.user.entity.User;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record WorkspaceUpdateRequestDto(
        @Size(min = 3, max = 255, message = "Name must be between 3-255 chars long")
        String name,
        @Size(max = 255, message = "Description must be between 3-255 chars long")
        String description
) {
}
