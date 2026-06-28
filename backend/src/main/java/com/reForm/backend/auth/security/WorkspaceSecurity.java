package com.reForm.backend.auth.security;


import com.reForm.backend.auth.exception.CustomAccessDeniedHandler;
import com.reForm.backend.auth.service.CustomerUserDetails;
import com.reForm.backend.user.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component("workspaceSecurity")
@RequiredArgsConstructor
public class WorkspaceSecurity {

    private final WorkspaceRepository workspaceRepository;

    public boolean isOwner(Authentication auth, UUID workspaceId){
        return getCurrentUserId(auth)
                .map(currentUserId -> workspaceRepository.existsByIdAndOwnerId(workspaceId, currentUserId))
                .orElse(false);
    }

    public boolean isMember(Authentication auth, UUID workspaceId){
        return isOwner(auth, workspaceId) || getCurrentUserId(auth)
                .map(currentUserId -> workspaceRepository.existsByIdAndMembersId(workspaceId, currentUserId))
                .orElse(false);
    }


    //each request is 1 threadlocal, so we get the current user (who owns this current request)
    // from the security contextholder
    public Optional<UUID> getCurrentUserId(Authentication auth){
        if(auth == null || !(auth.getPrincipal() instanceof CustomerUserDetails)) return Optional.empty();
        return Optional.of(((CustomerUserDetails) auth.getPrincipal()).getId());
    }
}
