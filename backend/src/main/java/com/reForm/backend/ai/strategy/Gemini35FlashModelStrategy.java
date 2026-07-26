package com.reForm.backend.ai.strategy;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GEMINI 3.5 FLASH STRATEGY
 * 
 * Model strategy for Gemini 3.5 Flash (high intelligence, fast text/multimodal schema model).
 */
@Component
public class Gemini35FlashModelStrategy implements IAiModelProviderStrategy {

    public static final String MODEL_KEY = "GEMINI_3_5_FLASH";

    @Override
    public boolean supports(String modelKey) {
        return MODEL_KEY.equalsIgnoreCase(modelKey);
    }

    @Override
    public String getModelId() {
        return "models/gemini-3.5-flash";
    }

    @Override
    public Map<String, Object> getGenerationConfig() {
        return Map.of(
            "responseModalities", List.of("TEXT")
        );
    }
}
