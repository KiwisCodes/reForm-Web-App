package com.reForm.backend.user.repository;

import com.reForm.backend.user.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    //this is wrong, it must be based on the attributes
    //Workspace extends BaseEntity, which has attribute id, so must be id, not uuid
    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByOwnerId(UUID id);
}
