package com.reForm.backend.ai.service;

import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SESSION CONTEXT SERVICE (Pure Scaffold Blueprint)
 * 
 * ROLE: Assembles dynamic AI setup payloads (target models, persona prompts, goal rules, and tools)
 * for Gemini Live sessions based on user role and database configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionContextService {

    // TODO: Inject IAiModelProviderStrategy strategies list or factory for dynamic model resolution
    // TODO: Inject FormRepository or PromptRepository to fetch prompts, personas, and goals from PostgreSQL

    /**
     * Constructs the setup configuration payload map for the target AI model.
     * 
     * @param userId The ID of the connected user
     * @param role Security role (FORM_BUILDER vs FORM_FILLER)
     * @param requestedModelKey The model selected by the user in the UI (e.g. "GEMINI_3_1_LIVE")
     * @return A Map containing the setup configuration payload
     */
    public Map<String, Object> buildSetupContext(String userId, Role role, String requestedModelKey) {
        // TODO 1: Resolve requestedModelKey against registered IAiModelProviderStrategy strategy beans
        // TODO 2: If role is FORM_BUILDER, fetch Form Architect system prompt template and layout tool schemas
        // TODO 3: If role is FORM_FILLER, query PostgreSQL for John's form persona, target goals, and candidate UI tools
        // TODO 4: Assemble setup payload map (model, systemInstruction, tools, generationConfig) and return map
        return null;
    }
}
