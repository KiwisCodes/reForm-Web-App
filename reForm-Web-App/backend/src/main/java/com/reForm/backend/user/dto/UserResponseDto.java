package com.reForm.backend.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String email,
        String username,
        String role,
        LocalDateTime createdAt
) {
}
