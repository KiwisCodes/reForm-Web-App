package com.reForm.backend.user.port;

import java.util.UUID;

public interface IWorkspaceService {
    public WorkspaceResponseDto createWorkspace(UUID userUUID);
    public WorkspaceResponseDto updateWorkspace(WorkspaceUpdateRequestDto workspaceUpdateRequestDto);
    public WorkspaceResponseDto deleteWorkspace(UUID workspaceUUID);
}
