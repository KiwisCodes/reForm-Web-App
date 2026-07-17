package com.reForm.backend.auth.security;

import com.reForm.backend.auth.service.CustomerUserDetails;
import com.reForm.backend.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("formSecurity")
@RequiredArgsConstructor
public class FormSecurity {

    private final FormRepository repository;

    private final WorkspaceSecurity workspaceSecurity;

    public boolean isMember(Authentication authentication, UUID formId) {
        return repository.findById(formId)
                .map(form -> workspaceSecurity.isMember(authentication, form.getWorkspaceId()))
                .orElse(false);
    }

    public boolean isOwner(Authentication authentication, UUID formId){
        return repository.findById(formId)
                .map(form -> workspaceSecurity.isOwner(authentication, form.getWorkspaceId()))
                .orElse(false);
    }

    public boolean isCreator(Authentication authentication, UUID formId){
        return  isOwner(authentication, formId)
                ||
                getCurrentUserId(authentication).map(currentUserId -> repository.existsByCreatorIdAndId(currentUserId, formId)).orElse(false);
    }

    public Optional<UUID> getCurrentUserId(Authentication auth){
        if(auth == null || !(auth.getPrincipal() instanceof CustomerUserDetails)) return Optional.empty();
        return Optional.of(((CustomerUserDetails) auth.getPrincipal()).getId());
    }

}
