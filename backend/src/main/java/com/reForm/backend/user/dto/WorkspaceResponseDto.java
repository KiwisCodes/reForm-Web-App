package com.reForm.backend.user.dto;

import com.reForm.backend.user.entity.User;

import java.util.Set;
import java.util.UUID;

public record WorkspaceResponseDto(
        UUID uuid,
        String name,
        String description,
        User owner,
        Set<User> members
) {
}
