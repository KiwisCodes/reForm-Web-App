package com.reForm.backend.auth.dto;

import java.util.UUID;

public record AuthResponseDto(
        String accessToken,
        UUID userId,
        String username,
        String role) {
}

