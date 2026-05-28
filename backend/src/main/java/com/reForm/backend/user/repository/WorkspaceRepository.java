package com.reForm.backend.user.repository;


import com.reForm.backend.user.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByWorkspaceId(UUID workspaceId) {
        return null;
    }

    private boolean existsByWorkspaceId(UUID workspaceId) {
        return false;
    }
}
