package com.reForm.backend.user.controller;


import com.reForm.backend.auth.service.CustomerUserDetails;
import com.reForm.backend.user.dto.*;
import com.reForm.backend.user.port.IWorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
@Validated
public class    WorkspaceController {
    private final IWorkspaceService workspaceService;

    @GetMapping("/{workspaceId}")
    // MODIFIED: Injected SpEL check. Rejects call before executing controller if not owner/member.
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(
            @PathVariable UUID workspaceId
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
//            @AuthenticationPrincipal CustomerUserDetails customerUserDetails
    ) {
        log.info("GET workspace by id: {}", workspaceId);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.getWorkspace(workspaceId);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping
//    @PreAuthorize("workspaceSecurity.isOwner()")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            @AuthenticationPrincipal CustomerUserDetails customerUserDetails,
            @RequestBody @Valid WorkspaceCreateRequestDto workspaceCreateRequestDto
    ) {
        log.info("POST request create workspace: {}", workspaceCreateRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.createWorkspace(customerUserDetails.getId(), workspaceCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceResponseDto);
    }

    @PatchMapping("/{workspaceId}")
    @PreAuthorize("@workspaceSecurity.isOwner(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspace(
//            @AuthenticationPrincipal CustomerUserDetails customerUserDetails,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        log.info("PATCH request update workspace: {}", workspaceUpdateRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.updateWorkspace(workspaceId, workspaceUpdateRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping("/{workspaceId}/members")
    @PreAuthorize("@workspaceSecurity.isOwner(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> addMember(
//            @AuthenticationPrincipal CustomerUserDetails customerUserDetails,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto) {
        log.info("PATCH request add member workspace: {}", workspaceAddMemberRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.addMembers(workspaceId, workspaceAddMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}/members")
    @PreAuthorize("@workspaceSecurity.isOwner(authentication, #workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> deleteMember(
//            @RequestParam UUID requesterId,
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto) {
        log.info("PATCH request delete member workspace: {}", workspaceDeleteMemberRequestDto);
        WorkspaceResponseDto workspaceResponseDto = workspaceService.deleteMembers(workspaceId, workspaceDeleteMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}")
    @PreAuthorize("@workspaceSecurity.isOwner(authentication, #workspaceId)")
    public ResponseEntity<Void> deleteWorkspace(
//            @RequestParam UUID requesterId,
            @PathVariable UUID workspaceId
            ) {
        log.info("DELETE request delete workspace: {}", workspaceId);
        workspaceService.deleteWorkspace(workspaceId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
