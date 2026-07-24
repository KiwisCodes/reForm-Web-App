package com.reForm.backend.ai.port;

import java.util.Map;

/**
 * AI MODEL PROVIDER STRATEGY INTERFACE (Strategy Pattern Port)
 * 
 * Defines the contract for different AI models (Gemini 3.1 Live, Gemini 3.5 Flash, etc.)
 * so the application can dynamically switch models based on user preference or billing tier
 * without using if/else statements.
 */
public interface IAiModelProviderStrategy {

    /**
     * Returns true if this strategy supports the requested model key (e.g. "GEMINI_3_1_LIVE").
     */
    boolean supports(String modelKey);

    /**
     * Returns the official model resource identifier recognized by the provider API.
     * Example: "models/gemini-3.1-flash-live-preview"
     */
    String getModelId();

    /**
     * Builds default generation and speech configurations specific to this target model family.
     */
    Map<String, Object> getGenerationConfig();
}
