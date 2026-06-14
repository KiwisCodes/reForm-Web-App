package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.*;
import java.util.UUID;

public interface IWorkspaceService {

    // CHANGED: all methods now take both requesterId (who) and workspaceId (what).
    // Previously only ownerId was passed — which was doing double duty as both
    // the identity of the caller AND the lookup key for the workspace.

    WorkspaceResponseDto createWorkspace(UUID requesterId, WorkspaceCreateRequestDto dto);

    WorkspaceResponseDto updateWorkspace(UUID requesterId, UUID workspaceId, WorkspaceUpdateRequestDto dto);

    void deleteWorkspace(UUID requesterId, UUID workspaceId);

    WorkspaceResponseDto getWorkspace(UUID requesterId, UUID workspaceId);

    WorkspaceResponseDto addMembers(UUID requesterId, UUID workspaceId, WorkspaceAddMemberRequestDto dto);

    WorkspaceResponseDto deleteMembers(UUID requesterId, UUID workspaceId, WorkspaceDeleteMemberRequestDto dto);
}