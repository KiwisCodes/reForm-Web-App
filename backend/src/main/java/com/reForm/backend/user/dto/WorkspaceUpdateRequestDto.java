package com.reForm.backend.user.dto;

import com.reForm.backend.user.entity.User;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record WorkspaceUpdateRequestDto(
        @Size(min = 3, max = 255)
        String name,
        @Size(max = 255)
        String description,
        Set<User> memebers
) {
}
