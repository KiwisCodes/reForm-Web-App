package com.reForm.backend.ai.strategy;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GEMINI 3.6 FLASH MODEL STRATEGY
 *
 * WHAT IS THIS CLASS?
 * Registers Google Gemini 3.6 Flash as a selectable AI model strategy
 * within the reForm IAiModelProviderStrategy registry pattern.
 *
 * WHY GEMINI 3.6 FLASH?
 * - Fastest reasoning response latency in the Gemini 3.x generation (~300-400ms REST).
 * - Used by Mode 3 (Cascaded Voice Pipeline) as the stateless LLM reasoning layer.
 * - Shared with Mode 2 (Text Chat Copilot) — no duplication.
 * - Supports function calling (tool declarations) natively over the REST API.
 *
 * USAGE:
 * - Set modelKey = "GEMINI_3_6_FLASH" on FormAiAgentProfile in PostgreSQL to select this strategy.
 * - GeminiFlashRestService references this model ID for generateContent REST calls.
 */
@Component
public class Gemini36FlashModelStrategy implements IAiModelProviderStrategy {

    public static final String MODEL_KEY = "GEMINI_3_6_FLASH";

    @Override
    public boolean supports(String modelKey) {
        return MODEL_KEY.equalsIgnoreCase(modelKey);
    }

    @Override
    public String getModelId() {
        return "models/gemini-3.6-flash";
    }

    @Override
    public Map<String, Object> getGenerationConfig() {
        return Map.of(
            "responseModalities", List.of("TEXT")
        );
    }
}
