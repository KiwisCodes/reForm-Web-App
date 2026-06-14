package com.reForm.backend.user.controller;


import com.reForm.backend.user.dto.*;
import com.reForm.backend.user.port.IWorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
@Validated
public class WorkspaceController {
    private final IWorkspaceService workspaceService;

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(
            @PathVariable UUID workspaceId,
            @RequestParam UUID requesterId) {
        log.info("GET workspace by id: {}", workspaceId);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.getWorkspace(requesterId, workspaceId);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            @RequestParam UUID requesterId,
            @RequestBody @Valid WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        log.info("POST request create workspace: {}", workspaceCreateRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.createWorkspace(requesterId, workspaceCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceResponseDto);
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspace(
            @RequestParam UUID requesterId,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        log.info("PATCH request update workspace: {}", workspaceUpdateRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.updateWorkspace(requesterId, workspaceId, workspaceUpdateRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceResponseDto> addMember(
            @RequestParam UUID requesterId,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto) {
        log.info("PATCH request add member workspace: {}", workspaceAddMemberRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.addMembers(requesterId, workspaceId, workspaceAddMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceResponseDto> deleteMember(
            @RequestParam UUID requesterId,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto) {
        log.info("PATCH request delete member workspace: {}", workspaceDeleteMemberRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.deleteMembers(requesterId, workspaceId, workspaceDeleteMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable UUID workspaceId,
            @RequestParam UUID requesterId) {
        log.info("DELETE request delete workspace: {}", workspaceId);
        workspaceService.deleteWorkspace(requesterId, workspaceId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
