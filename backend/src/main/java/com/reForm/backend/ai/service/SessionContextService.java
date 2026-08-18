package com.reForm.backend.ai.service;

import com.reForm.backend.ai.domain.SessionStateMemento;
import com.reForm.backend.ai.domain.SessionStateRecoveryResult;
import com.reForm.backend.ai.domain.TranscriptTurn;
import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import com.reForm.backend.ai.strategy.block.BlockExecutionRegistry;
import com.reForm.backend.ai.strategy.block.IBlockExecutionStrategy;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.entity.block.AbstractBlock;
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
    private final BlockExecutionRegistry blockExecutionRegistry;

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
        return buildSetupContext(userId, role, formId, requestedModelKey, null);
    }

    /**
     * Constructs rehydrated setup context for a RECONNECTED session.
     * Injects previous dialogue turns, answered fields, and resumption directives.
     */
    public Map<String, Object> buildReconnectionSetupContext(SessionStateRecoveryResult recoveryResult) {
        if (recoveryResult == null || recoveryResult.memento() == null) {
            log.warn("Recovery result or memento is null. Falling back to default setup context.");
            return buildSetupContext("unknown", Role.FORM_FILLER, "GEMINI_3_1_LIVE");
        }

        SessionStateMemento memento = recoveryResult.memento();
        Role role = memento.role() != null ? Role.valueOf(memento.role()) : Role.FORM_FILLER;

        Map<String, Object> setupMap = buildSetupContext(
            memento.userId(),
            role,
            memento.formId(),
            memento.modelKey()
        );

        // Enhance System Instruction with Reconnection Context
        String nl = System.lineSeparator();
        StringBuilder recap = new StringBuilder();
        recap.append(nl).append(nl).append("[SESSION RECONNECTION CONTEXT]").append(nl);
        recap.append("This is a RESUMED session (Session ID: ").append(memento.sessionId()).append(").").append(nl);
        recap.append("Completed turns so far: ").append(memento.turnCount()).append(nl);

        if (memento.answers() != null && !memento.answers().isEmpty()) {
            recap.append("Verified answers collected so far:").append(nl);
            memento.answers().forEach((field, val) ->
                recap.append("- Field '").append(field).append("': ").append(val).append(nl)
            );
        }

        if (recoveryResult.recentTurns() != null && !recoveryResult.recentTurns().isEmpty()) {
            recap.append(nl).append("[RECENT DIALOGUE HISTORY]").append(nl);
            for (TranscriptTurn turn : recoveryResult.recentTurns()) {
                recap.append(turn.role().equalsIgnoreCase("user") ? "Candidate: " : "AI: ")
                     .append(turn.text()).append(nl);
            }
        }

        recap.append(nl).append("[RESUMPTION INSTRUCTIONS]").append(nl);
        recap.append("1. Greet the user warmly and briefly acknowledge the reconnection (e.g., 'Welcome back! Let's continue where we left off.').").append(nl);
        if (memento.activeBlockId() != null && !memento.activeBlockId().isBlank()) {
            recap.append("2. Resume immediately from block '").append(memento.activeBlockId()).append("'.").append(nl);
        }
        recap.append("3. DO NOT repeat or re-ask questions that have already been answered.").append(nl);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemInstructionMap = (Map<String, Object>) setupMap.get("systemInstruction");
        if (systemInstructionMap != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) systemInstructionMap.get("parts");
            if (parts != null && !parts.isEmpty()) {
                String existingPrompt = (String) parts.get(0).get("text");
                parts.get(0).put("text", existingPrompt + recap);
            }
        }

        log.info("✅ Compiled rehydration setup context for reconnected session: {}", memento.sessionId());
        return setupMap;
    }

    public Map<String, Object> buildSetupContext(String userId, Role role, String formId, String requestedModelKey, AbstractBlock activeBlock) {
        log.info("Building setup context for userId: {}, role: {}, formId: {}, modelKey: {}, activeBlock: {}", userId, role, formId, requestedModelKey, activeBlock != null ? activeBlock.getId() : "null");

        // Step 1: Query form agent profile from DB if formId is present
        FormAiAgentProfile profile = null;
        if (formId != null && !formId.isBlank()) {
            try {
                profile = profileRepository.findByFormId(UUID.fromString(formId)).orElse(null);
                if (profile != null) {
                    log.info("✅ [POSTGRESQL DB PROMPT LOADED]: Found FormAiAgentProfile for formId: {}. Voice: {}, ModelKey: {}", 
                             formId, profile.getVoiceName(), profile.getModelKey());
                } else {
                    log.info("ℹ️ [DB PROMPT NOT FOUND]: No FormAiAgentProfile found for formId: {}. Using baseline prompt.", formId);
                }
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

        // Step 3: Compile persona system instruction dynamically using 3-Level Cascade & Strategy Pattern
        String systemInstruction = compileSystemInstruction(role, profile, activeBlock);

        // Step 4: Build tool declarations (gated by role + context + active block whitelist)
        List<Map<String, Object>> tools = buildToolDeclarations(role, true, true, activeBlock);

        // Step 5: Assemble setup payload Map according to Google's official Gemini Live specification
        Map<String, Object> setupMap = new HashMap<>();
        setupMap.put("model", modelId);
        
        Map<String, Object> finalGenConfig = new HashMap<>();
        if (generationConfig != null && !generationConfig.isEmpty()) {
            finalGenConfig.putAll(generationConfig);
        } else {
            finalGenConfig.put("responseModalities", List.of("AUDIO"));
        }

        // Step 6: Resolve Voice Cascade (Level 3 Block Voice > Level 2 Profile Voice > Level 1 Baseline)
        String voiceName = null;
        if (activeBlock != null) {
            voiceName = blockExecutionRegistry.resolve(activeBlock.getType()).resolveVoiceName(activeBlock);
        }
        if (voiceName == null && profile != null && profile.getVoiceName() != null && !profile.getVoiceName().isBlank()) {
            voiceName = profile.getVoiceName();
        }

        if (voiceName != null && !voiceName.isBlank()) {
            finalGenConfig.put("speechConfig", Map.of(
                "voiceConfig", Map.of(
                    "prebuiltVoiceConfig", Map.of("voiceName", voiceName)
                )
            ));
        }

        if (profile != null && profile.getTemperature() != null) {
            finalGenConfig.put("temperature", profile.getTemperature());
        }

        setupMap.put("generationConfig", finalGenConfig);
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
     * Priority 2: FormAiAgentProfile level baseline template from DB (configured by Form Builder / Mode 2)
     * Priority 3: Production Role-based baseline template fallback
     */
    public String compileSystemInstruction(Role role, FormAiAgentProfile profile, ConversationalBlock activeBlock) {
        return compileSystemInstruction(role, profile, (AbstractBlock) activeBlock);
    }

    /**
     * Compiles persona system instructions using 3-Level Cascade + SOLID Strategy Pattern:
     * Priority 1 (Level 3): Active block level persona/prompt override & strategy goal compilation
     * Priority 2 (Level 2): FormAiAgentProfile level baseline template from DB (configured by Form Builder / Mode 2)
     * Priority 3 (Level 1): Production Role-based baseline template fallback
     */
    public String compileSystemInstruction(Role role, FormAiAgentProfile profile, AbstractBlock activeBlock) {
        String nl = System.lineSeparator();
        String basePrompt = null;

        // Level 3 / Priority 1: Active Block level override
        if (activeBlock instanceof ConversationalBlock convBlock && convBlock.getPersona() != null && !convBlock.getPersona().isBlank()) {
            basePrompt = convBlock.getPersona();
            if (convBlock.getPrompt() != null && !convBlock.getPrompt().isBlank()) {
                basePrompt += nl + nl + "TARGET QUESTIONS & GOALS:" + nl + convBlock.getPrompt();
            }
        }

        // Level 2 / Priority 2: FormAiAgentProfile default template from DB
        if (basePrompt == null && profile != null && profile.getSystemPromptTemplate() != null && !profile.getSystemPromptTemplate().isBlank()) {
            basePrompt = profile.getSystemPromptTemplate();
        }

        // Level 1 / Priority 3: Role-based baseline fallbacks
        if (basePrompt == null) {
            if (role == Role.FORM_BUILDER) {
                basePrompt = "[ROLE & PERSONA]" + nl +
                       "You are an expert AI Form Architect Co-Pilot assisting a Form Creator in real time over voice." + nl + nl +
                       "[CAPABILITIES & TOOL CALLING]" + nl +
                       "You have access to the following tools:" + nl +
                       "- `modifyFormLayout`: Whenever the user asks to add, remove, or edit form fields, execute this tool." + nl +
                       "- `configureFillerPersona`: When the user describes how the AI should behave toward form fillers (tone, voice, temperature), save it." + nl +
                       "- `publishForm`: When the user says they are done and wants to publish, call this tool." + nl +
                       "- `generateContentFromDocument`: When the user uploads a document and wants questions generated from it, call this tool." + nl +
                       "- `requestFileUpload`: If you need a document from the user (job description, syllabus), request an upload." + nl +
                       "- `analyzeUploadedFile`: After a file is uploaded, analyze its contents to discuss or generate questions." + nl +
                       "- `endSession`: When the user indicates they are finished building (implicitly or explicitly), call this to gracefully end the session." + nl + nl +
                       "[VOICE CONVERSATION GUIDELINES]" + nl +
                       "1. Keep spoken responses short, natural, and under 2 sentences." + nl +
                       "2. Acknowledge user requests immediately and explain what field was added or updated on the canvas." + nl +
                       "3. Be attentive to implicit closure cues ('That's all for today', 'Looks complete', 'I'm good'). Acknowledge politely and invoke `endSession`.";
            } else {
                basePrompt = "[ROLE & PERSONA]" + nl +
                       "You are an AI Interviewer conducting an interactive voice interview for candidates." + nl + nl +
                       "[CAPABILITIES & TOOL CALLING]" + nl +
                       "You have access to the following tools:" + nl +
                       "- `saveFieldResponse`: After the user answers a question, save their validated response immediately." + nl +
                       "- `evaluateResponse`: Score the user's answer for quality, correctness, or urgency." + nl +
                       "- `skipQuestion`: If the user declines to answer or the question doesn't apply, skip it." + nl +
                       "- `lookupFormProgress`: Check how many questions remain and announce progress to the user." + nl +
                       "- `flagForHumanReview`: Flag ambiguous, suspicious, or critical answers for the form owner to review." + nl +
                       "- `requestFileUpload`: If you need a file from the user (photo, document, ID), request an upload." + nl +
                       "- `renderDynamicUI`: For multiple-choice or rating questions, render clickable buttons or stars." + nl +
                       "- `endSession`: Call this when all interview goals are fulfilled, or when the user signals completion/early exit." + nl + nl +
                       "[CONVERSATIONAL GUIDELINES]" + nl +
                       "1. Speak naturally, politely, and keep responses concise (under 25 words per turn)." + nl +
                       "2. Ask questions step-by-step to evaluate the candidate's background." + nl +
                       "3. If the candidate interrupts, stop speaking immediately and listen to their response." + nl +
                       "4. After every 5 questions, use `lookupFormProgress` and announce how many questions remain." + nl +
                       "5. When all goals in the interview checklist are verified and confirmed, or when the candidate indicates they want to wrap up ('That is all from me', 'We are done'), summarize accomplishments politely and invoke `endSession`.";
            }
        }

        // Delegate goal section compilation to BlockExecutionRegistry (Strategy Pattern)
        if (activeBlock != null) {
            IBlockExecutionStrategy strategy = blockExecutionRegistry.resolve(activeBlock.getType());
            String goalSection = strategy.compileGoalSection(activeBlock);
            if (goalSection != null && !goalSection.isBlank()) {
                basePrompt += nl + nl + goalSection;
            }
        }

        return basePrompt;
    }

    /**
     * Assembles function calling tool declarations JSON schema for Gemini setup.
     *
     * WHY THIS METHOD EXISTS:
     * Without function calling tools, the AI is just a text/voice chatbot — it can talk but it
     * cannot DO anything (save data, modify forms, analyze files, end sessions). Tools give the AI
     * "hands" to perform real backend actions. Google Gemini receives these schemas in the setup
     * payload and knows which tools it can call during the conversation.
     *
     * HOW TOOL SELECTION WORKS:
     * Tools are gated by two dimensions:
     * 1. Role-based:   FORM_BUILDER gets builder tools, FORM_FILLER gets filler tools, some are universal.
     * 2. Context-based: hasDocuments enables RAG search, hasAudioCapability enables audio recording.
     *
     * @param role           User security role (FORM_BUILDER vs FORM_FILLER)
     * @param hasDocuments    Whether uploaded documents are available for RAG search
     * @param hasAudioCapability Whether the session supports audio (Mode 3/4 voice sessions)
     * @return List of tool declaration maps for Gemini setup payload
     */
    public List<Map<String, Object>> buildToolDeclarations(Role role, boolean hasDocuments, boolean hasAudioCapability) {
        return buildToolDeclarations(role, hasDocuments, hasAudioCapability, null);
    }

    public List<Map<String, Object>> buildToolDeclarations(Role role, boolean hasDocuments, boolean hasAudioCapability, AbstractBlock activeBlock) {
        List<Map<String, Object>> functionDeclarations = new ArrayList<>();

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION A: UNIVERSAL TOOLS (Available to BOTH Form Builders and Form Fillers)
        // ═══════════════════════════════════════════════════════════════════════════

        // Tool 1: endSession (Intelligent Multi-Trigger Semantic Intent)
        // WHY: The AI currently cannot hang up. This tool lets the AI gracefully end the session,
        //      close WebSocket connections, stop billing, and trigger frontend cleanup when the session is complete.
        // WHEN: Triggered on:
        //       1. Task Complete: All required interview goals or form fields are collected and confirmed.
        //       2. Implicit or Explicit Wrap-Up: User says "That's all from me", "We're done", "I think that covers it", "Goodbye".
        //       3. Early Departure: User says "I have to jump to a meeting", "Let's stop here", "Cancel the rest".
        functionDeclarations.add(buildFunctionDeclaration(
            "endSession",
            "Gracefully terminates the voice or text session. Execute this tool when:\n" +
                "1. (Task Complete): All required interview goals or form fields have been successfully collected and confirmed with the user.\n" +
                "2. (Natural Wrap-Up): The user signals completion implicitly or explicitly (e.g., 'That is all from me', 'We are done', 'I think that covers it', 'Goodbye', 'Thanks for your help').\n" +
                "3. (Early Departure): The user expresses an intent to leave, pause, or cancel the session (e.g., 'I have to run to a meeting', 'Let's stop here', 'I don't have more time').\n" +
                "DO NOT call this tool if the user is merely answering a question, asking for clarification, or pausing temporarily to think.",
            Map.of(
                "reason", Map.of(
                    "type", "STRING",
                    "enum", List.of("COMPLETED_GOALS", "USER_WRAP_UP", "USER_ABORT_EARLY", "BUILDER_PUBLISHED_EXIT", "TIMEOUT"),
                    "description", "The classified semantic reason for session termination: COMPLETED_GOALS, USER_WRAP_UP, USER_ABORT_EARLY, BUILDER_PUBLISHED_EXIT, TIMEOUT"
                ),
                "summary", Map.of(
                    "type", "STRING",
                    "description", "A 1-2 sentence executive summary of what was accomplished during this session."
                ),
                "unresolvedItems", Map.of(
                    "type", "ARRAY",
                    "items", Map.of("type", "STRING"),
                    "description", "List of goals or fields left unanswered if the user exited early."
                )
            ),
            List.of("reason")
        ));

        // Tool 2: searchUserDocument (RAG Vector Search)
        // WHY: Large documents (handbooks, syllabi, resumes) don't fit in the context window.
        //      Instead of injecting 50-page PDFs into the prompt, we chunk and index them in
        //      pgvector, then let the AI search on-demand via this tool.
        // WHEN: User asks a question about uploaded content, or AI needs to reference documents.
        if (hasDocuments) {
            functionDeclarations.add(buildFunctionDeclaration(
                "searchUserDocument",
                "Searches uploaded documents (PDFs, resumes, syllabi, handbooks) using semantic " +
                    "vector search to retrieve relevant passages. Use when you need to answer " +
                    "questions about uploaded content.",
                Map.of(
                    "query", Map.of(
                        "type", "STRING",
                        "description", "The question or topic to search for in the uploaded documents."
                    )
                ),
                List.of("query")
            ));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION B: FORM BUILDER TOOLS (Mode 2 Text Co-Builder / Mode 4 Voice Co-Builder)
        // ═══════════════════════════════════════════════════════════════════════════

        if (role == Role.FORM_BUILDER) {

            // Tool 3: modifyFormLayout
            // WHY: The core tool that lets Form Builders verbally create forms. Without this,
            //      voice co-building is impossible — the AI could discuss forms but never
            //      actually create one. This dispatches FormLayoutModificationEvent to LayoutAgent.
            // WHEN: "Add a phone number field", "Delete question 3", "Move email above name".
            functionDeclarations.add(buildFunctionDeclaration(
                "modifyFormLayout",
                "Adds, removes, reorders, or edits form fields and sections on the live form canvas. " +
                    "Call this whenever the user asks to create, modify, or delete form fields.",
                Map.of(
                    "action", Map.of(
                        "type", "STRING",
                        "description", "The layout action: ADD_FIELD, REMOVE_FIELD, REORDER_FIELD, EDIT_FIELD, ADD_SECTION, REMOVE_SECTION"
                    ),
                    "fieldType", Map.of(
                        "type", "STRING",
                        "description", "Type of field to add: TEXT, EMAIL, PHONE, NUMBER, DATE, DROPDOWN, CHECKBOX, RADIO, FILE_UPLOAD, RATING, LONG_TEXT"
                    ),
                    "label", Map.of(
                        "type", "STRING",
                        "description", "Human-readable label for the field (e.g. 'Years of Experience', 'Email Address')."
                    ),
                    "targetBlockId", Map.of(
                        "type", "STRING",
                        "description", "ID of the existing block to modify, remove, or reorder."
                    ),
                    "position", Map.of(
                        "type", "INTEGER",
                        "description", "Target position index when reordering (0-based)."
                    ),
                    "required", Map.of(
                        "type", "BOOLEAN",
                        "description", "Whether this field is mandatory for form fillers."
                    )
                ),
                List.of("action")
            ));

            // Tool 4: configureFillerPersona
            // WHY: This is the "persona twisting" feature. When a Form Builder says "make the
            //      interview cheerful and welcoming", the AI saves that persona to PostgreSQL
            //      (FormAiAgentProfile) so all future Form Fillers inherit those settings.
            //      Without this, builders would have to manually configure persona in a settings UI.
            // WHEN: "Make it professional but friendly", "Use a female voice", "Set temperature to creative".
            functionDeclarations.add(buildFunctionDeclaration(
                "configureFillerPersona",
                "Configures the AI persona, voice, and behavior that Form Fillers will experience " +
                    "when they fill this form. Saves settings to the database for all future sessions.",
                Map.of(
                    "tone", Map.of(
                        "type", "STRING",
                        "description", "Desired conversation tone (e.g. 'professional', 'cheerful and welcoming', 'formal and serious')."
                    ),
                    "voiceName", Map.of(
                        "type", "STRING",
                        "description", "Prebuilt voice: Puck (energetic male), Kore (calm female), Charon (deep male), Aoede (warm female), Fenrir (authoritative male)."
                    ),
                    "customInstructions", Map.of(
                        "type", "STRING",
                        "description", "Additional custom behavior rules for the AI when talking to form fillers."
                    ),
                    "temperature", Map.of(
                        "type", "NUMBER",
                        "description", "Creativity level 0.0 (strict) to 1.0 (creative). Default 0.7."
                    )
                ),
                List.of("tone")
            ));

            // Tool 5: publishForm
            // WHY: After finishing form design, builders need to make it live and get a shareable
            //      link. Without this tool, publishing requires navigating to a separate UI page.
            // WHEN: "I'm done, publish this form", "Make it live and give me the link".
            functionDeclarations.add(buildFunctionDeclaration(
                "publishForm",
                "Publishes the current form, making it accessible to respondents. Returns a shareable " +
                    "public URL. Sets form status to PUBLISHED in the database.",
                Map.of(
                    "visibility", Map.of(
                        "type", "STRING",
                        "description", "Access level: PUBLIC (anyone with link), PRIVATE (requires login), RESTRICTED (specific emails only)."
                    )
                ),
                List.of()
            ));

            // Tool 14: generateContentFromDocument (Builder-only)
            // WHY: Teachers upload syllabi, HR uploads job descriptions, compliance officers upload
            //      handbooks — they all want the AI to auto-generate quiz/interview/survey questions
            //      FROM the uploaded material, not just search it.
            // WHEN: "Generate 10 quiz questions from the syllabus I uploaded".
            if (hasDocuments) {
                functionDeclarations.add(buildFunctionDeclaration(
                    "generateContentFromDocument",
                    "Generates form questions, quiz items, or interview prompts from an uploaded reference " +
                        "document (syllabus, job description, handbook). AI can then add these to the canvas.",
                    Map.of(
                        "fileId", Map.of(
                            "type", "STRING",
                            "description", "The unique ID of the source document."
                        ),
                        "contentType", Map.of(
                            "type", "STRING",
                            "description", "What to generate: QUIZ_QUESTIONS, INTERVIEW_QUESTIONS, SURVEY_QUESTIONS, CHECKLIST_ITEMS."
                        ),
                        "count", Map.of(
                            "type", "INTEGER",
                            "description", "Number of items to generate (e.g. 10 questions). Default: 5."
                        ),
                        "difficulty", Map.of(
                            "type", "STRING",
                            "description", "Difficulty level: EASY, MEDIUM, HARD, MIXED."
                        )
                    ),
                    List.of("fileId", "contentType")
                ));
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION C: FORM FILLER TOOLS (Mode 2 Text / Mode 3 Cascaded / Mode 4 Live)
        // ═══════════════════════════════════════════════════════════════════════════

        if (role == Role.FORM_FILLER) {

            // Tool 6: saveFieldResponse
            // WHY: When a Form Filler answers a question, that answer must be persisted to the
            //      database IMMEDIATELY — not buffered until session end. If the connection drops
            //      mid-interview, all prior answers would be lost without this tool.
            // WHEN: Candidate says "I have 5 years of Java experience" → AI saves { fieldId, value }.
            functionDeclarations.add(buildFunctionDeclaration(
                "saveFieldResponse",
                "Saves a validated form field response to the database. Call this after the user " +
                    "provides a clear answer to a form question. Persists immediately to prevent data loss.",
                Map.of(
                    "fieldId", Map.of(
                        "type", "STRING",
                        "description", "The unique block/field ID being answered."
                    ),
                    "value", Map.of(
                        "type", "STRING",
                        "description", "The validated response value to persist."
                    ),
                    "confidence", Map.of(
                        "type", "NUMBER",
                        "description", "AI confidence score 0.0-1.0 that this answer is valid and complete."
                    )
                ),
                List.of("fieldId", "value")
            ));

            // Tool 7: evaluateResponse
            // WHY: For quizzes, exams, interview scoring, and medical triage, the AI needs to
            //      JUDGE answer quality — not just collect answers. A teacher's quiz needs correct/
            //      incorrect scoring. An HR interview needs quality ratings. A medical intake needs
            //      urgency triage. This is what separates reForm from a simple form collector.
            // WHEN: Student answers quiz → score. Candidate answers technical → rate quality.
            functionDeclarations.add(buildFunctionDeclaration(
                "evaluateResponse",
                "Evaluates a respondent's answer against expected criteria. Used for quizzes, " +
                    "interviews, assessments, and triage scoring. Records score and optional feedback.",
                Map.of(
                    "fieldId", Map.of(
                        "type", "STRING",
                        "description", "The field/question being evaluated."
                    ),
                    "score", Map.of(
                        "type", "NUMBER",
                        "description", "Numeric score (0-100) or pass/fail (0 or 100)."
                    ),
                    "feedback", Map.of(
                        "type", "STRING",
                        "description", "Brief feedback explanation for the respondent."
                    ),
                    "tags", Map.of(
                        "type", "STRING",
                        "description", "Comma-separated category tags: CORRECT, INCORRECT, PARTIAL, NEEDS_FOLLOWUP, HIGH_PRIORITY, LOW_PRIORITY."
                    )
                ),
                List.of("fieldId", "score")
            ));

            // Tool 8: skipQuestion
            // WHY: Not every question is mandatory. Some questions don't apply to certain respondents
            //      (e.g. asking about military service when the respondent was never in the military).
            //      Without this, the AI either forces an answer or awkwardly moves on without recording why.
            // WHEN: "I'd rather not answer that", "Skip this one", "That doesn't apply to me".
            functionDeclarations.add(buildFunctionDeclaration(
                "skipQuestion",
                "Skips the current question and advances to the next one. Records the skip reason " +
                    "for the form owner to review.",
                Map.of(
                    "fieldId", Map.of(
                        "type", "STRING",
                        "description", "The field/question being skipped."
                    ),
                    "reason", Map.of(
                        "type", "STRING",
                        "description", "Why the question was skipped: USER_DECLINED, NOT_APPLICABLE, WILL_ANSWER_LATER."
                    )
                ),
                List.of("fieldId")
            ));

            // Tool 9: lookupFormProgress
            // WHY: During long forms (20+ questions), respondents feel lost without knowing how
            //      far along they are. This tool lets the AI proactively announce progress
            //      ("We're halfway done! 10 of 20 questions answered.") which reduces drop-off rates.
            // WHEN: "How many questions are left?", or AI checks every 5 questions proactively.
            functionDeclarations.add(buildFunctionDeclaration(
                "lookupFormProgress",
                "Returns the current form completion progress: how many fields are answered, " +
                    "how many remain, and estimated time left.",
                Map.of(),
                List.of()
            ));

            // Tool 10: flagForHumanReview
            // WHY: The AI should NOT make the final call on ambiguous, suspicious, or critical
            //      answers. A patient reporting chest pain needs a real doctor to see it immediately.
            //      A candidate whose answer contradicts their resume needs HR to investigate.
            //      This tool adds a FLAGGED annotation and notifies the form owner in real time.
            // WHEN: AI detects inconsistency, high-priority medical symptom, or uncertain answer.
            functionDeclarations.add(buildFunctionDeclaration(
                "flagForHumanReview",
                "Flags a specific response or the entire submission for human review by the form " +
                    "owner. Use when uncertain, detecting inconsistencies, or identifying high-priority situations.",
                Map.of(
                    "fieldId", Map.of(
                        "type", "STRING",
                        "description", "The specific field to flag, or omit to flag the entire submission."
                    ),
                    "priority", Map.of(
                        "type", "STRING",
                        "description", "Urgency level: LOW, MEDIUM, HIGH, CRITICAL."
                    ),
                    "reason", Map.of(
                        "type", "STRING",
                        "description", "Explanation of why this needs human review."
                    )
                ),
                List.of("priority", "reason")
            ));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION D: FILE & DOCUMENT INTERACTION TOOLS (Available to Both Roles)
        // These tools handle file uploads, visual analysis, OCR, and structured extraction.
        // ═══════════════════════════════════════════════════════════════════════════

        // Tool 11: requestFileUpload
        // WHY: During a live conversation, the AI sometimes needs a file from the user that wasn't
        //      anticipated upfront (e.g. insurance AI: "Upload a photo of the damage"). This tool
        //      dynamically materializes a drag-and-drop upload zone in the browser mid-conversation.
        // WHEN: AI needs a photo, document, code sample, or ID that wasn't pre-configured.
        functionDeclarations.add(buildFunctionDeclaration(
            "requestFileUpload",
            "Dynamically requests the user to upload a file during the conversation. Renders a " +
                "drag-and-drop upload zone in the chat UI. Use when you need the user to provide a " +
                "document, photo, or file mid-conversation.",
            Map.of(
                "label", Map.of(
                    "type", "STRING",
                    "description", "Instruction label displayed on the upload zone (e.g. 'Upload a photo of the damage')."
                ),
                "acceptedTypes", Map.of(
                    "type", "STRING",
                    "description", "MIME type filter: 'image/*' for images, 'application/pdf' for PDFs, '*/*' for any file."
                ),
                "required", Map.of(
                    "type", "BOOLEAN",
                    "description", "Whether the upload is mandatory before proceeding to the next question."
                ),
                "maxSizeMb", Map.of(
                    "type", "INTEGER",
                    "description", "Maximum file size in megabytes (default: 10)."
                )
            ),
            List.of("label", "acceptedTypes")
        ));

        // Tool 12: analyzeUploadedFile
        // WHY: When a user uploads a file mid-conversation, the AI needs to actually LOOK at it
        //      and understand its content — not just store it blindly. This routes images to Gemini
        //      Vision and documents to Apache Tika / Tesseract OCR for content extraction.
        // WHEN: Student uploads lecture notes, candidate uploads resume, patient uploads lab results.
        functionDeclarations.add(buildFunctionDeclaration(
            "analyzeUploadedFile",
            "Analyzes an uploaded file using vision AI (for images) or OCR/text extraction (for documents). " +
                "Returns a structured description or extracted text that you can discuss with the user.",
            Map.of(
                "fileId", Map.of(
                    "type", "STRING",
                    "description", "The unique ID of the uploaded file to analyze."
                ),
                "analysisType", Map.of(
                    "type", "STRING",
                    "description", "Type of analysis: DESCRIBE (visual description), EXTRACT_TEXT (OCR/parse), EXTRACT_DATA (structured fields like name, email, dates)."
                ),
                "question", Map.of(
                    "type", "STRING",
                    "description", "Optional specific question to answer about the file (e.g. 'What skills are listed on this resume?')."
                )
            ),
            List.of("fileId", "analysisType")
        ));

        // Tool 13: extractStructuredData
        // WHY: Sometimes the AI doesn't just need to "read" a file — it needs to pull out specific
        //      structured fields and auto-fill form responses from it. A resume contains name, email,
        //      skills, education — all of which map directly to form fields. This saves the user
        //      from manually re-typing information that's already in their documents.
        // WHEN: Resume PDF → auto-extract name/skills. Invoice → extract vendor/amount. ID card → extract DOB.
        functionDeclarations.add(buildFunctionDeclaration(
            "extractStructuredData",
            "Extracts specific structured data fields from an uploaded document (resume, invoice, " +
                "medical report, ID card). Returns key-value pairs for auto-filling form fields.",
            Map.of(
                "fileId", Map.of(
                    "type", "STRING",
                    "description", "The unique ID of the uploaded file to extract data from."
                ),
                "fieldsToExtract", Map.of(
                    "type", "STRING",
                    "description", "Comma-separated list of field names to extract (e.g. 'fullName,email,phone,yearsOfExperience,skills')."
                )
            ),
            List.of("fileId", "fieldsToExtract")
        ));

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION E: AUDIO RECORDING & SESSION TRANSCRIPT TOOLS
        // These tools persist voice recordings and conversation text for review and compliance.
        // Only registered when the session has audio capability (Mode 3/4 voice sessions).
        // ═══════════════════════════════════════════════════════════════════════════

        if (hasAudioCapability) {

            // Tool 15: saveAudioRecording
            // WHY: Form owners (HR recruiters, teachers, doctors, legal firms) need to store and
            //      replay voice session recordings for review, compliance, and audit purposes.
            //      This is a legal requirement in many industries (healthcare HIPAA, financial
            //      compliance, legal proceedings). Without it, voice sessions are ephemeral.
            // WHEN: Session ending, or AI marks a notable segment worth archiving.
            functionDeclarations.add(buildFunctionDeclaration(
                "saveAudioRecording",
                "Saves the current voice session audio recording for archival, review, or compliance " +
                    "purposes. Compresses PCM buffer and stores to persistent storage.",
                Map.of(
                    "scope", Map.of(
                        "type", "STRING",
                        "description", "What to save: FULL_SESSION (entire recording), CURRENT_SEGMENT (from last checkpoint)."
                    ),
                    "label", Map.of(
                        "type", "STRING",
                        "description", "Human-readable label for this recording (e.g. 'Spring Boot Technical Question')."
                    ),
                    "retentionDays", Map.of(
                        "type", "INTEGER",
                        "description", "Number of days to retain the recording before auto-deletion. Default: 90."
                    )
                ),
                List.of("scope")
            ));
        }

        // Tool 16: saveSessionTranscript
        // WHY: Even when audio isn't stored, a full text transcript of the conversation should
        //      be saved. Form builders need it for review, EvaluationAgent needs it for scoring,
        //      legal teams need it for keyword search, students need it for self-review.
        // WHEN: Session ending, or periodically during long conversations.
        functionDeclarations.add(buildFunctionDeclaration(
            "saveSessionTranscript",
            "Saves the full conversation transcript (user speech + AI speech) to the database " +
                "for review, evaluation, and compliance.",
            Map.of(
                "includeTimestamps", Map.of(
                    "type", "BOOLEAN",
                    "description", "Whether to include timestamps for each message. Default: true."
                ),
                "includeEvaluation", Map.of(
                    "type", "BOOLEAN",
                    "description", "Whether to trigger the EvaluationAgent to score and summarize the transcript. Default: false."
                )
            ),
            List.of()
        ));

        // ═══════════════════════════════════════════════════════════════════════════
        // SECTION F: DYNAMIC UI & NOTIFICATION TOOLS
        // These tools push interactive widgets and real-time alerts to the frontend.
        // ═══════════════════════════════════════════════════════════════════════════

        // Tool 17: renderDynamicUI
        // WHY: Sometimes speaking or typing an answer is slower than clicking a button. A quiz
        //      with options A/B/C/D is faster with clickable buttons. A rating question is faster
        //      with star widgets. A date question is faster with a date picker. This tool lets the
        //      AI dynamically push interactive UI elements into the browser mid-conversation.
        // WHEN: Multiple-choice questions, ratings, confirmations, date selections.
        functionDeclarations.add(buildFunctionDeclaration(
            "renderDynamicUI",
            "Renders interactive UI elements in the chat (buttons, rating stars, confirmation dialogs, " +
                "date pickers) for the user to interact with instead of typing or speaking.",
            Map.of(
                "componentType", Map.of(
                    "type", "STRING",
                    "description", "UI component: BUTTONS, IMAGE_CARDS, RATING_STARS, CONFIRMATION_DIALOG, DATE_PICKER, SLIDER."
                ),
                "options", Map.of(
                    "type", "STRING",
                    "description", "Comma-separated list of option labels for BUTTONS or IMAGE_CARDS (e.g. 'PostgreSQL,MySQL,MongoDB')."
                ),
                "prompt", Map.of(
                    "type", "STRING",
                    "description", "Instruction text displayed above the UI component."
                )
            ),
            List.of("componentType", "prompt")
        ));

        // Tool 18: sendNotification
        // WHY: Real-time alerts to the Form Builder while fillers are actively using their forms.
        //      Teachers watching quiz progress, recruiters getting flagged alerts, doctors receiving
        //      urgent triage notifications — all need instant push notifications without polling.
        // WHEN: Student finishes quiz, high-scoring candidate detected, critical patient flag,
        //       support ticket submitted, registration capacity reached.
        functionDeclarations.add(buildFunctionDeclaration(
            "sendNotification",
            "Sends a real-time notification to the form owner's dashboard, email, or connected " +
                "integrations (Slack, webhook). Use for important events during form filling sessions.",
            Map.of(
                "channel", Map.of(
                    "type", "STRING",
                    "description", "Notification channel: DASHBOARD, EMAIL, SLACK, WEBHOOK."
                ),
                "priority", Map.of(
                    "type", "STRING",
                    "description", "Priority level: INFO, WARNING, URGENT."
                ),
                "title", Map.of(
                    "type", "STRING",
                    "description", "Short notification title."
                ),
                "body", Map.of(
                    "type", "STRING",
                    "description", "Notification body message."
                )
            ),
            List.of("channel", "priority", "title")
        ));

        // Wrap all declarations into Gemini's expected { "functionDeclarations": [...] } structure
        List<Map<String, Object>> result = List.of();
        if (!functionDeclarations.isEmpty()) {
            result = List.of(Map.of("functionDeclarations", functionDeclarations));
        }

        // Apply active block tool whitelist filtering via Strategy Pattern
        if (activeBlock != null) {
            IBlockExecutionStrategy strategy = blockExecutionRegistry.resolve(activeBlock.getType());
            result = strategy.filterAllowedTools(activeBlock, result);
        }

        return result;
    }

    /**
     * HELPER: Builds a single function declaration map in Google Gemini's expected schema format.
     *
     * WHY THIS HELPER EXISTS:
     * Without this, every tool declaration repeats the same nested Map.of() boilerplate for
     * name/description/parameters/properties/required. This helper centralizes the structure
     * so each tool only specifies what's unique: its name, description, properties, and required fields.
     *
     * @param name         Function name (e.g. "endSession", "saveFieldResponse")
     * @param description  What the function does (Gemini uses this to decide WHEN to call it)
     * @param properties   Map of parameter names → their type/description schemas
     * @param required     List of required parameter names
     * @return A Map matching Google's FunctionDeclaration JSON schema
     */
    private Map<String, Object> buildFunctionDeclaration(
            String name, String description, Map<String, Object> properties, List<String> required) {

        Map<String, Object> declaration = new HashMap<>();
        declaration.put("name", name);
        declaration.put("description", description);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        parameters.put("required", required);
        declaration.put("parameters", parameters);

        return declaration;
    }
}
