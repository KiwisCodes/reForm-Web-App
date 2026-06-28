package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDto(
        //we login with email and password, so the username here is just the metadata
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @NotBlank(message = "User name must not be blank")
        String username
) {
}
