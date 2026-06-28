package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.*;
import java.util.UUID;

public interface IWorkspaceService {

    // CHANGED: all methods now take both requesterId (who) and workspaceId (what).
    // Previously only ownerId was passed — which was doing double duty as both
    // the identity of the caller AND the lookup key for the workspace.

    WorkspaceResponseDto createWorkspace(UUID requesterId, WorkspaceCreateRequestDto dto);

    WorkspaceResponseDto updateWorkspace(UUID workspaceId, WorkspaceUpdateRequestDto dto);

    void deleteWorkspace(UUID workspaceId);

    WorkspaceResponseDto getWorkspace(UUID workspaceId);

    WorkspaceResponseDto addMembers(UUID workspaceId, WorkspaceAddMemberRequestDto dto);

    WorkspaceResponseDto deleteMembers(UUID workspaceId, WorkspaceDeleteMemberRequestDto dto);
}