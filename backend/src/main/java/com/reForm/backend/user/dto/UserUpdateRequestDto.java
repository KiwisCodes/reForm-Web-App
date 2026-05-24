package com.reForm.backend.user.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequestDto(
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username
) {
}
