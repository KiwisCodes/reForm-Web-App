package com.reForm.backend.user.service;


import com.reForm.backend.user.dto.WorkspaceCreateRequestDto;
import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.dto.WorkspaceUpdateRequestDto;
import com.reForm.backend.user.port.IWorkspaceService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceService implements IWorkspaceService {
    @Override
    public WorkspaceResponseDto createWorkspace(WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        return null;
    }

    @Override
    public WorkspaceResponseDto updateWorkspace(WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        return null;
    }

    @Override
    public WorkspaceResponseDto deleteWorkspace(UUID userId) {
        return null;
    }

    @Override
    public WorkspaceResponseDto addMembers(Set<String> emails) {
        return null;
    }

    @Override
    public WorkspaceResponseDto removeMembers(Set<String> emails) {
        return null;
    }
}
