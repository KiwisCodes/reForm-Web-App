package com.reForm.backend.ai.strategy;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GEMINI 3.1 FLASH LIVE STRATEGY
 * 
 * Model strategy for Gemini 3.1 Flash Live Preview (real-time voice streaming model).
 */
@Component
public class Gemini31LiveModelStrategy implements IAiModelProviderStrategy {

    public static final String MODEL_KEY = "GEMINI_3_1_LIVE";

    @Override
    public boolean supports(String modelKey) {
        return MODEL_KEY.equalsIgnoreCase(modelKey);
    }

    @Override
    public String getModelId() {
        return "models/gemini-3.1-flash-live-preview";
    }

    @Override
    public Map<String, Object> getGenerationConfig() {
        return Map.of(
            "responseModalities", List.of("AUDIO"),
            "speechConfig", Map.of(
                "voiceConfig", Map.of(
                    "prebuiltVoiceConfig", Map.of("voiceName", "Puck")
                )
            )
        );
    }
}
