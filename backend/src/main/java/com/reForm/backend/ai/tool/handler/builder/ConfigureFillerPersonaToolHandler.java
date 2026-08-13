package com.reForm.backend.ai.tool.handler.builder;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.form.repository.FormRepository;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

/**
 * TOOL HANDLER: configureFillerPersona
 * 
 * WHY THIS TOOL EXISTS:
 * Enables "persona twisting" during Form Builder co-building sessions. When a builder speaks
 * instructions like "Make the interviewer cheerful and welcoming" or "Use the Kore voice",
 * this handler persists the persona prompt, voice choice, and temperature to PostgreSQL:
 * 1. FormAiAgentProfile (Form-level defaults for all future sessions)
 * 2. ConversationalBlock (Block-level overrides when targetBlockId is specified)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigureFillerPersonaToolHandler implements IToolCallHandler {

    private final FormAiAgentProfileRepository profileRepository;
    private final FormRepository formRepository;

    @Override
    public String getFunctionName() {
        return "configureFillerPersona";
    }

    @Override
    public Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId) {
        String formId = (String) clientSession.getAttributes().get("formId");
        JsonNode args = functionCall.path("args");

        String tone = args.path("tone").asText("professional");
        String voiceName = args.path("voiceName").asText(null);
        String customInstructions = args.path("customInstructions").asText(null);
        String targetBlockId = args.path("targetBlockId").asText(null);
        Double temperature = args.has("temperature") ? args.path("temperature").asDouble() : null;

        log.info("🎭 [CONFIGURE PERSONA HANDLER] FormId: {}, TargetBlockId: {}, Tone: {}, Voice: {}, Temp: {}", 
                 formId, targetBlockId, tone, voiceName, temperature);

        if (formId != null && !formId.isBlank()) {
            try {
                UUID formUuid = UUID.fromString(formId);
                Form form = formRepository.findById(formUuid).orElse(null);
                if (form != null) {
                    // 1. Form-Level Profile Defaults (Level 2 Cascade)
                    FormAiAgentProfile profile = profileRepository.findByFormId(formUuid)
                            .orElseGet(() -> {
                                FormAiAgentProfile p = new FormAiAgentProfile();
                                p.setForm(form);
                                p.setModelKey("GEMINI_3_1_LIVE");
                                return p;
                            });

                    if (voiceName != null && !voiceName.isBlank()) {
                        profile.setVoiceName(voiceName);
                    }
                    if (temperature != null) {
                        profile.setTemperature(temperature.floatValue());
                    }

                    String compiledPrompt = "[ROLE & PERSONA]\nYou are an AI Interviewer. Conversation tone: " + tone;
                    if (customInstructions != null && !customInstructions.isBlank()) {
                        compiledPrompt += "\n\n[CUSTOM INSTRUCTIONS]\n" + customInstructions;
                    }
                    profile.setSystemPromptTemplate(compiledPrompt);

                    profileRepository.save(profile);
                    log.info("✅ Saved FormAiAgentProfile to PostgreSQL for FormId: {}", formId);

                    // 2. Block-Level Overrides (Level 3 Cascade) if targetBlockId specified
                    if (targetBlockId != null && !targetBlockId.isBlank()) {
                        for (AbstractBlock block : form.getBlocks()) {
                            if (block.getId() != null && block.getId().toString().equalsIgnoreCase(targetBlockId) 
                                    && block instanceof ConversationalBlock convBlock) {
                                convBlock.setPersona("[PERSONA]\nTone: " + tone);
                                if (voiceName != null && !voiceName.isBlank()) {
                                    convBlock.setVoiceName(voiceName);
                                }
                                if (customInstructions != null && !customInstructions.isBlank()) {
                                    convBlock.setPrompt(customInstructions);
                                }
                                log.info("✅ Updated Block-Level ConversationalBlock override for Block ID: {}", targetBlockId);
                                break;
                            }
                        }
                        formRepository.save(form);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update FormAiAgentProfile for formId: {}", formId, e);
            }
        }

        return Map.of(
            "id", callId,
            "name", getFunctionName(),
            "response", Map.of("result", Map.of(
                "status", "SUCCESS",
                "message", "Persona configured for tone: " + tone
            ))
        );
    }
}
