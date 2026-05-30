package com.reForm.backend.user.dto;

import com.reForm.backend.user.entity.User;

import java.util.Set;
import java.util.UUID;


//so you need a object mapper, to map the java classes (workspace class, to workspaceresponsedto)
//but inside this thing, there is a nested response dto, so it wont know how to map
public record WorkspaceResponseDto(
        UUID uuid,
        String name,
        String description,
        UserResponseDto owner,
        Set<UserResponseDto> members
) {
}
