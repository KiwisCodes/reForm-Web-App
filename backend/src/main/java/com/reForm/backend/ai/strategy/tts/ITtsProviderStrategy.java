package com.reForm.backend.ai.strategy.tts;

import tools.jackson.databind.ObjectMapper;

/**
 * TTS (TEXT-TO-SPEECH) PROVIDER STRATEGY INTERFACE
 *
 * ARCHITECTURE ROLE:
 * Encapsulates TTS provider & model variations (Cartesia Sonic 3.5, Cartesia Multilingual, ElevenLabs, etc.).
 * Allows CascadedVoiceAdapter (Mode 3) to build synthesis frames & cancel payloads dynamically
 * without coupling pipeline code to specific 3rd-party JSON schemas.
 */
public interface ITtsProviderStrategy {

    /**
     * Determines whether this strategy supports the requested TTS key.
     *
     * @param ttsKey Strategy identifier (e.g. "CARTESIA_SONIC_3_5", "CARTESIA_SONIC_MULTI")
     * @return true if supported
     */
    boolean supports(String ttsKey);

    /**
     * Returns the unique identifier key for this strategy.
     */
    String getTtsKey();

    /**
     * Constructs outbound WebSocket URL for TTS provider.
     *
     * @param apiKey TTS provider API key
     * @return WebSocket URL string
     */
    String buildWebSocketUrl(String apiKey);

    /**
     * Constructs JSON text frame payload for requesting voice synthesis.
     *
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param text Text chunk to synthesize into audio
     * @param voiceId Prebuilt or custom voice ID
     * @param contextId Unique Turn/Context ID
     * @return Serialized JSON payload string
     */
    String buildSynthesisPayload(ObjectMapper objectMapper, String text, String voiceId, String contextId) throws Exception;

    /**
     * Constructs JSON text frame payload for barge-in cancellation.
     *
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param contextId Context ID to interrupt
     * @return Serialized JSON payload string
     */
    String buildCancelPayload(ObjectMapper objectMapper, String contextId) throws Exception;
}
