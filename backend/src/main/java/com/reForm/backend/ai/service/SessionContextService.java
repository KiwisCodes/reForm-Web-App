package com.reForm.backend.ai.service;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SESSION CONTEXT SERVICE
 * 
 * ROLE IN ARCHITECTURE:
 * Assembles dynamic AI setup payloads (target model strategy IDs, system instruction persona prompts,
 * prebuilt voice configs, and function calling tool schemas) for real-time AI sessions.
 * 
 * HOW IT WORKS WITH GOOGLE GEMINI LIVE API:
 * Google's Gemini Multimodal Live API over WebSockets expects an initial JSON setup frame:
 * {
 *   "setup": {
 *     "model": "models/gemini-3.1-flash-live-preview",
 *     "generationConfig": { "responseModalities": ["AUDIO"], "speechConfig": ... },
 *     "systemInstruction": { "parts": [{ "text": "Compiled system prompt..." }] },
 *     "tools": [ { "functionDeclarations": [ ... ] } ]
 *   }
 * }
 * 
 * This service builds that exact nested Map structure in Java. When GeminiLiveVoiceAdapter
 * converts this Map to JSON via Jackson ObjectMapper and sends it over the WebSocket, Google accepts it!
 * 
 * NOTE ON DB INSPECTIONS:
 * FormAiAgentProfile maps to PostgreSQL table 'form_ai_agent_profiles'. IntelliJ may display 
 * 'Cannot resolve table/column' IDE warnings until the PostgreSQL DDL script or Hibernate ddl-auto runs.
 * These are IDE database inspector notices, not Java code errors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionContextService {

    private final List<IAiModelProviderStrategy> modelStrategies;
    private final FormAiAgentProfileRepository profileRepository;

    /**
     * Overloaded helper method for 3-parameter calls.
     */
    public Map<String, Object> buildSetupContext(String userId, Role role, String requestedModelKey) {
        return buildSetupContext(userId, role, null, requestedModelKey);
    }

    /**
     * Constructs the setup configuration payload map for Google BidiGenerateContentSetup.
     * 
     * @param userId Connected user ID
     * @param role User security role (FORM_BUILDER vs FORM_FILLER)
     * @param formId Target form UUID
     * @param requestedModelKey Model key selected in UI (e.g. "GEMINI_3_1_LIVE")
     * @return Map structure representing Google's BidiGenerateContentSetup JSON payload
     */
    public Map<String, Object> buildSetupContext(String userId, Role role, String formId, String requestedModelKey) {
        log.info("Building setup context for userId: {}, role: {}, formId: {}, modelKey: {}", userId, role, formId, requestedModelKey);

        // Step 1: Query form agent profile from DB if formId is present
        FormAiAgentProfile profile = null;
        if (formId != null && !formId.isBlank()) {
            try {
                profile = profileRepository.findByFormId(UUID.fromString(formId)).orElse(null);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for formId: {}", formId);
            }
        }

        // Step 2: Resolve requested model strategy dynamically
        String activeModelKey = (profile != null && profile.getModelKey() != null) 
                ? profile.getModelKey() 
                : requestedModelKey;
        
        IAiModelProviderStrategy strategy = resolveModelStrategy(activeModelKey);
        String modelId = strategy != null ? strategy.getModelId() : "models/gemini-3.1-flash-live-preview";
        Map<String, Object> generationConfig = strategy != null ? strategy.getGenerationConfig() : Map.of();

        // Step 3: Compile persona system instruction dynamically using Hybrid Pattern
        String systemInstruction = compileSystemInstruction(role, profile, null);

        // Step 4: Build tool declarations (modifyFormLayout + searchUserDocument)
        List<Map<String, Object>> tools = buildToolDeclarations(role, true);

        // Step 5: Assemble setup payload Map according to Google's official Gemini Live specification
        Map<String, Object> setupMap = new HashMap<>();
        setupMap.put("model", modelId);
        if (generationConfig != null && !generationConfig.isEmpty()) {
            setupMap.put("generationConfig", generationConfig);
        } else {
            setupMap.put("generationConfig", Map.of("responseModalities", List.of("AUDIO")));
        }
        setupMap.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        
        if (!tools.isEmpty()) {
            setupMap.put("tools", tools);
        }

        return setupMap;
    }

    /**
     * Resolves requested modelKey against registered IAiModelProviderStrategy beans without hardcoded if/else.
     */
    public IAiModelProviderStrategy resolveModelStrategy(String requestedModelKey) {
        String targetKey = requestedModelKey != null ? requestedModelKey : "GEMINI_3_1_LIVE";
        return modelStrategies.stream()
                .filter(strategy -> strategy.supports(targetKey))
                .findFirst()
                .orElseGet(() -> modelStrategies.isEmpty() ? null : modelStrategies.get(0));
    }

    /**
     * Compiles persona system instructions using Hybrid Pattern:
     * Priority 1: Active ConversationalBlock level persona/prompt override
     * Priority 2: FormAiAgentProfile level baseline template from DB
     * Priority 3: Role-based baseline template fallback
     */
    public String compileSystemInstruction(Role role, FormAiAgentProfile profile, ConversationalBlock activeBlock) {
        // Priority 1: Active ConversationalBlock level override
        if (activeBlock != null && activeBlock.getPersona() != null && !activeBlock.getPersona().isBlank()) {
            String prompt = activeBlock.getPersona();
            if (activeBlock.getPrompt() != null && !activeBlock.getPrompt().isBlank()) {
                prompt += "\n\nTARGET QUESTIONS & GOALS:\n" + activeBlock.getPrompt();
            }
            return prompt;
        }

        // Priority 2: FormAiAgentProfile level default template from PostgreSQL
        if (profile != null && profile.getSystemPromptTemplate() != null && !profile.getSystemPromptTemplate().isBlank()) {
            return profile.getSystemPromptTemplate();
        }

        // Priority 3: Role-based baseline template
        // [DEV_TEST_TEMPORARY] Playful Cat Test Prompt for live WebAudio verification (Revert to production baseline prompt later)
        if (role == Role.FORM_BUILDER) {
            return "You are a helpful AI assistant who acts like a playful cat assisting a form builder. " +
                   "Answer everything clearly, help them build forms, and end every single sentence with the word 'meow'.";
        }

        return "You are an AI Recruiter interviewing a candidate, but you act like a friendly cat. " +
               "Ask concise questions to evaluate their background and end every single sentence with the word 'meow'.";
    }

    /**
     * Assembles function calling tool declarations JSON schema for Gemini setup.
     */
    public List<Map<String, Object>> buildToolDeclarations(Role role, boolean hasDocuments) {
        List<Map<String, Object>> functionDeclarations = new ArrayList<>();

        // Tool 1: modifyFormLayout (For Form Builders)
        if (role == Role.FORM_BUILDER) {
            functionDeclarations.add(Map.of(
                "name", "modifyFormLayout",
                "description", "Triggers structural modifications to the form layout canvas.",
                "parameters", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                        "userIntent", Map.of(
                            "type", "STRING",
                            "description", "The extracted user intent (e.g. 'ADD_CONTACT_SECTION', 'DELETE_BLOCK_2')"
                        ),
                        "targetBlockId", Map.of(
                            "type", "STRING",
                            "description", "Optional target block ID to modify or remove."
                        )
                    ),
                    "required", List.of("userIntent")
                )
            ));
        }

        // Tool 2: searchUserDocument (For In-Session RAG Document Retrieval)
        if (hasDocuments) {
            functionDeclarations.add(Map.of(
                "name", "searchUserDocument",
                "description", "Queries uploaded user documents, company handbooks, or resumes to retrieve exact answers.",
                "parameters", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                        "query", Map.of(
                            "type", "STRING",
                            "description", "The specific question or topic to search for in the user documents."
                        )
                    ),
                    "required", List.of("query")
                )
            ));
        }

        if (!functionDeclarations.isEmpty()) {
            return List.of(Map.of("functionDeclarations", functionDeclarations));
        }

        return List.of();
    }
}
