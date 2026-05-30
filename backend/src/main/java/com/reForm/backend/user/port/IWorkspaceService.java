package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.WorkspaceCreateRequestDto;
import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.dto.WorkspaceUpdateRequestDto;

import java.util.Set;
import java.util.UUID;

public interface IWorkspaceService {
    public WorkspaceResponseDto createWorkspace(UUID ownerId, WorkspaceCreateRequestDto workspaceCreateRequestDto);
    public WorkspaceResponseDto updateWorkspace(UUID ownerId, WorkspaceUpdateRequestDto workspaceUpdateRequestDto);
    public void deleteWorkspace(UUID userId);
    public WorkspaceResponseDto addMembers(UUID ownerId, Set<String> emails);
    public WorkspaceResponseDto removeMembers(UUID ownerId, Set<String> emails);
    public WorkspaceResponseDto getWorkspace(UUID userId);
}
