package com.reForm.backend.form.repository;

import com.reForm.backend.form.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository extends JpaRepository<Form, UUID> {

    List<Form> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    Optional<Form> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    Optional<Form> findBySlug(String slug);
    Boolean existsByCreatorIdAndId(UUID creatorId, UUID formId);
    Optional<Form> findByCreatorId(UUID creatorId);
}
