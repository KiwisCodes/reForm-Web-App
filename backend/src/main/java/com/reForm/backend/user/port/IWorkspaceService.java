package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.WorkspaceCreateRequestDto;
import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.dto.WorkspaceUpdateRequestDto;

import java.util.Set;
import java.util.UUID;

public interface IWorkspaceService {
    public WorkspaceResponseDto createWorkspace(WorkspaceCreateRequestDto workspaceCreateRequestDto);
    public WorkspaceResponseDto updateWorkspace(WorkspaceUpdateRequestDto workspaceUpdateRequestDto);
    public WorkspaceResponseDto deleteWorkspace(UUID userId);
    public WorkspaceResponseDto addMembers(Set<String> emails);
    public WorkspaceResponseDto removeMembers(Set<String> emails);
}
