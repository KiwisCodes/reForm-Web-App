package com.reForm.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        //jarkarta validation
        @NotBlank(message = "Email is required")
        @Email(message="Must be a valid email")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min=8, max=100, message = "Must be between 8-100 chars")
        String password
) {
}
