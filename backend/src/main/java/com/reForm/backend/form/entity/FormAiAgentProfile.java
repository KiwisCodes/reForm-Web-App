package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * FORM AI AGENT PROFILE ENTITY (Step 2 - Dynamic Prompt & Agent Profile Engine)
 * 
 * WHAT IS THIS CLASS?
 * JPA Domain Entity bound 1-to-1 with Form that stores production AI persona prompt templates,
 * model choice (GEMINI_3_1_LIVE), prebuilt voice selection (Puck/Kore), temperature, and BYOK API keys.
 * 
 * WHY IS IT NEEDED?
 * Replaces hardcoded system prompt string fallbacks in Java code. Production SaaS platforms (Vapi, Retell)
 * store persona prompt templates in PostgreSQL with variable placeholders ({{formTitle}}, {{persona}}).
 * 
 * HYBRID PATTERN ROLE:
 * Provides the Form-Level Defaults. If an active ConversationalBlock defines its own block-level
 * persona, prompt, or voiceName, SessionContextService uses the block-level override; otherwise
 * it falls back to this form-level profile.
 * 
 * NOTE ON IDE WARNINGS:
 * IntelliJ IDEA Database Inspector may highlight table 'form_ai_agent_profiles' and column names as 
 * 'Cannot resolve' until PostgreSQL DDL script or Hibernate ddl-auto executes. These are IDE notices,
 * not Java compilation errors.
 */
@Entity
@Table(name = "form_ai_agent_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormAiAgentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @Column(name = "model_key", nullable = false, length = 50)
    private String modelKey; // e.g. "GEMINI_3_1_LIVE"

    @Column(name = "system_prompt_template", columnDefinition = "TEXT")
    private String systemPromptTemplate; // Custom persona prompt with {{placeholders}}

    @Column(name = "voice_name", length = 50)
    private String voiceName; // Prebuilt voice (e.g. "Puck", "Kore")

    @Column(name = "temperature")
    private Float temperature; // e.g. 0.7

    @Column(name = "byok_api_key_encrypted", length = 512)
    private String byokApiKeyEncrypted; // AES-256-GCM encrypted BYOK API Key
}
