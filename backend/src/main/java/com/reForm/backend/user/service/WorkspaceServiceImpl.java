package com.reForm.backend.user.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.user.dto.*;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.entity.Workspace;
import com.reForm.backend.user.mapper.WorkspaceMapper;
import com.reForm.backend.user.port.IWorkspaceService;
import com.reForm.backend.user.repository.UserRepository;
import com.reForm.backend.user.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
//@Transactional class level is convienient, but for read only functions, like getWorkspace (which i dont even have, wrong) is bad for performance
@Slf4j
public class WorkspaceServiceImpl implements IWorkspaceService {
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;

    //everything here should need requester, for authorization, big word: tenant isolation
    @Override
    @Transactional
//    @ResponseStatus(HttpStatus.CREATED) this is wrong, this is the work of the controllers
    public WorkspaceResponseDto createWorkspace(
            UUID requester,
            WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        log.info("Create new workspace");
        User user = userRepository.findById(requester)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + requester));

        Workspace workspace = Workspace.builder()
                .owner(user)
                .name(workspaceCreateRequestDto.name())
                .description(workspaceCreateRequestDto.description())
                .build();
        log.info("Workspace created");

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto updateWorkspace(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        log.info("Update workspace");
        //this is fking redudant, if i want to check if user exists, should just use existsBy
        //the workspace code bellow has findbyownerid, which join the user table already
//        User owner = userRepository
//                .findById(requesterId)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + requesterId));
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));

        verifyOwner(workspace, requesterId);

        //because user can update either name or description or both
        //we need to check it first before we update
        //isBlank is dif from isEmpty, isEmpty true when it is like "", but if it is  "    ", then isBlank
        //return true while isEmpty return false;
        if(workspaceUpdateRequestDto.name() != null && !workspaceUpdateRequestDto.name().isBlank()) {
            workspace.setName(workspaceUpdateRequestDto.name());
        }
        if(workspaceUpdateRequestDto.description() != null && !workspaceUpdateRequestDto.description().isBlank()) {
            workspace.setDescription(workspaceUpdateRequestDto.description());
        }
        log.info("Workspace updated");
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    //there is nothing left to return, so must be void
    @Override
    @Transactional
    public void deleteWorkspace(
            UUID requesterId,
            UUID workspaceId) {
        log.info("Delete workspace");
//        User owner = userRepository
//                .findById(ownerId)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + ownerId));
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        verifyOwner(workspace, requesterId);
        workspaceRepository.delete(workspace);
        log.info("Workspace deleted");
    }
    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponseDto getWorkspace(
            UUID requesterId,
            UUID workspaceId){
        log.info("Get workspace by id");
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        verifyOwner(workspace, requesterId);
        return workspaceMapper.toWorkspaceResponseDto(workspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto addMembers(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto) {
        Set<String> emails = workspaceAddMemberRequestDto.emails();
        Workspace workspace =  workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        verifyOwner(workspace, requesterId);
        //this code under gets us N+1 problem
//        Set<User> newMembers = emails
//                .stream()
//                .map(String::toLowerCase)
//                .map(userRepository::findByEmail)
//                .filter(Optional::isPresent)//return the Optional objects from the last stream, not stream of true false, it will let the object goes through if the object goes through the isPresent and return true
//                .map(Optional::get)
//                .collect(Collectors.toSet()); //this never returns null, it returns at least an empty set
//        if(newMembers != null && !newMembers.isEmpty()) { so we dont need the check here
        Set<User> newMembers = userRepository.findAllByEmailIn(emails);
        workspace.getMembers().addAll(newMembers);
//        }
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto deleteMembers(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto) {
        Set<String> emails = workspaceDeleteMemberRequestDto.emails();
        log.info("Remove members from workspace");
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        verifyOwner(workspace, requesterId);
        //this code under gets us N+1 problem
//        Set<User> removeMembers = emails
//                .stream()
//                .map(String::toLowerCase)
//                .map(userRepository::findByEmail)
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .collect(Collectors.toSet());
        Set<User> removeMembers = userRepository.findAllByEmailIn(emails);
        workspace.getMembers().removeAll(removeMembers);

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info("Members removed");
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    // Helper method — ADDED: rather than copy-pasting the owner check in every method,
    // extract it. This is the authorization logic that Spring Security will replace later.
    // When you add Security, delete this method and handle it in a @PreAuthorize annotation.
    private void verifyOwner(Workspace workspace, UUID requesterId) {
        if (!workspace.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the workspace owner can perform this action");
        }
    }

}
