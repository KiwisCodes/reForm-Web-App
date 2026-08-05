package com.reForm.backend.ai.tool.handler.builder;

import com.reForm.backend.ai.tool.port.IToolCallHandler;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
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
 * this handler persists the persona prompt, voice choice, and temperature to PostgreSQL
 * (FormAiAgentProfile) for all future Form Filler sessions.
 */
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.repository.FormRepository;

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
        Double temperature = args.has("temperature") ? args.path("temperature").asDouble() : null;

        log.info("🎭 [CONFIGURE PERSONA HANDLER] FormId: {}, Tone: {}, Voice: {}, Temp: {}", 
                 formId, tone, voiceName, temperature);

        if (formId != null && !formId.isBlank()) {
            try {
                UUID formUuid = UUID.fromString(formId);
                Form form = formRepository.findById(formUuid).orElse(null);
                if (form != null) {
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
