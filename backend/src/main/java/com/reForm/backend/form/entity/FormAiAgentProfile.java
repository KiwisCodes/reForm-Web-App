package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Form AI Agent Profile Entity.
 * Encapsulates persistent AI persona prompt templates, model selection,
 * voice configurations, temperature, and encrypted BYOK API keys mapped 1-to-1 with a Form.
 */
@Entity
@Table(
        name = "form_ai_agent_profiles",
        indexes = {
                @Index(name = "idx_form_ai_profile_form_id", columnList = "form_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_form_ai_profile_form_id", columnNames = {"form_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings({"JpaDataSourceORMInspection", "SpellCheckingInspection"})
public class FormAiAgentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false, unique = true)
    private Form form;

    @Column(nullable = false, length = 50)
    private String modelKey; // e.g. "GEMINI_3_1_LIVE"

    @Column(columnDefinition = "TEXT")
    private String systemPromptTemplate; // Custom persona prompt with {{placeholders}}

    @Column(length = 50)
    private String voiceName; // Prebuilt voice (e.g. "Puck", "Kore")

    @Column
    private Float temperature; // e.g. 0.7

    @Column(length = 512)
    private String byokApiKeyEncrypted; // AES-256-GCM encrypted BYOK API Key
}
