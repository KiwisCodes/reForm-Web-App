package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.*;

import java.util.Set;
import java.util.UUID;

public interface IWorkspaceService {
    public WorkspaceResponseDto createWorkspace(UUID ownerId, WorkspaceCreateRequestDto workspaceCreateRequestDto);
    public WorkspaceResponseDto updateWorkspace(UUID ownerId, WorkspaceUpdateRequestDto workspaceUpdateRequestDto);
    public void deleteWorkspace(UUID userId);
    public WorkspaceResponseDto addMembers(UUID ownerId, WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto);
    public WorkspaceResponseDto deleteMembers(UUID ownerId, WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto);
    public WorkspaceResponseDto getWorkspace(UUID userId);
}
