package com.reForm.backend.form.repository;

import com.reForm.backend.form.entity.FormAiAgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * FORM AI AGENT PROFILE REPOSITORY (Step 2 - Data Access Layer)
 * 
 * Spring Data JPA Repository for querying FormAiAgentProfile entities from PostgreSQL.
 * Used by SessionContextService during WebSocket session startup to retrieve form-level AI agent settings.
 */
public interface FormAiAgentProfileRepository extends JpaRepository<FormAiAgentProfile, UUID> {
    
    /**
     * Retrieves the AI Agent Profile configuration attached to a specific form UUID.
     * 
     * @param formId Target form UUID
     * @return Optional containing FormAiAgentProfile if present
     */
    Optional<FormAiAgentProfile> findByFormId(UUID formId);
}
