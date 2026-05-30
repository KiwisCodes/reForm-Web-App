package com.reForm.backend.user.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.user.dto.WorkspaceCreateRequestDto;
import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.dto.WorkspaceUpdateRequestDto;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.entity.Workspace;
import com.reForm.backend.user.mapper.WorkspaceMapper;
import com.reForm.backend.user.port.IWorkspaceService;
import com.reForm.backend.user.repository.UserRepository;
import com.reForm.backend.user.repository.WorkspaceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

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
    //everything here should need ownerId, for authorization, big word: tenant isolation
    @Override
    @Transactional
//    @ResponseStatus(HttpStatus.CREATED) this is wrong, this is the work of the controllers
    public WorkspaceResponseDto createWorkspace(UUID ownerId, WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        log.info("Create new workspace");
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + ownerId));

        Workspace workspace = Workspace.builder()
                .owner(user)
                .workspaceName(workspaceCreateRequestDto.name())
                .workspaceDescription(workspaceCreateRequestDto.description())
                .build();
        log.info("Workspace created");

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto updateWorkspace(UUID ownerId, WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        log.info("Update workspace");
        //this is fking redudant, if i want to check if user exists, should just use existsBy
        //the workspace code bellow has findbyownerid, which join the user table already
//        User owner = userRepository
//                .findById(ownerId)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + ownerId));
        Workspace workspace = workspaceRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + ownerId));

        //because user can update either name or description or both
        //we need to check it first before we update
        //isBlank is dif from isEmpty, isEmpty true when it is like "", but if it is  "    ", then isBlank
        //return true while isEmpty return false;
        if(workspaceUpdateRequestDto.name() != null && !workspaceUpdateRequestDto.name().isBlank()) {
            workspace.setWorkspaceName(workspaceUpdateRequestDto.name());
        }
        if(workspaceUpdateRequestDto.description() != null && !workspaceUpdateRequestDto.description().isBlank()) {
            workspace.setWorkspaceDescription(workspaceUpdateRequestDto.description());
        }
        log.info("Workspace updated");
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    //there is nothing left to return, so must be void
    @Override
    @Transactional
    public void deleteWorkspace(UUID ownerId) {
        log.info("Delete workspace");
//        User owner = userRepository
//                .findById(ownerId)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + ownerId));
        Workspace workspace = workspaceRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + ownerId));
        workspaceRepository.delete(workspace);
        log.info("Workspace deleted");
    }

    @Override
    public WorkspaceResponseDto getWorkspace(UUID ownerId){
        log.info("Get workspace by id");
        Workspace workspace = workspaceRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + ownerId));
        return workspaceMapper.toWorkspaceResponseDto(workspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto addMembers(UUID ownerId, Set<String> emails) {
        Workspace workspace =  workspaceRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + ownerId));
        Set<User> newMembers = emails
                .stream()
                .map(String::toLowerCase)
                .map(userRepository::findByEmail)
                .filter(Optional::isPresent)//return the Optional objects from the last stream, not stream of true false, it will let the object goes through if the object goes through the isPresent and return true
                .map(Optional::get)
                .collect(Collectors.toSet());
        if(newMembers != null && !newMembers.isEmpty()) {
            workspace.getMembers().addAll(newMembers);
        }
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto removeMembers(UUID ownerId,Set<String> emails) {
        log.info("Remove members from workspace");
        Workspace workspace = workspaceRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + ownerId));
        Set<User> removeMembers = emails
                .stream()
                .map(String::toLowerCase)
                .map(userRepository::findByEmail)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        if(removeMembers != null && !removeMembers.isEmpty()) {
            workspace.getMembers().removeAll(removeMembers);
        }

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info("Members removed");
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }
}
