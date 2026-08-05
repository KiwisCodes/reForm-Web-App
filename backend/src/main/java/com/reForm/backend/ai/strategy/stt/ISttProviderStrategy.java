package com.reForm.backend.ai.strategy.stt;

import org.springframework.web.socket.WebSocketHttpHeaders;

import java.util.Map;

/**
 * STT (SPEECH-TO-TEXT) PROVIDER STRATEGY INTERFACE
 *
 * ARCHITECTURE ROLE:
 * Encapsulates STT provider & model variations (Deepgram Nova-3, Nova-2, Whisper Cloud, etc.).
 * Allows CascadedVoiceAdapter (Mode 3) to switch STT models dynamically per user profile
 * or configuration without touching core pipeline orchestration logic.
 */
public interface ISttProviderStrategy {

    /**
     * Determines whether this strategy supports the requested STT key.
     *
     * @param sttKey Strategy identifier (e.g. "DEEPGRAM_NOVA_3", "DEEPGRAM_NOVA_2")
     * @return true if supported
     */
    boolean supports(String sttKey);

    /**
     * Returns the unique identifier key for this strategy.
     */
    String getSttKey();

    /**
     * Constructs the outbound WebSocket URL for connecting to the STT provider.
     *
     * @param apiKey STT provider API key
     * @param options Custom parameters (sampleRate, encoding, channels, etc.)
     * @return Fully formatted WebSocket URL string
     */
    String buildWebSocketUrl(String apiKey, Map<String, Object> options);

    /**
     * Constructs required WebSocket authentication headers.
     *
     * @param apiKey STT provider API key
     * @return Configured WebSocketHttpHeaders
     */
    WebSocketHttpHeaders buildHeaders(String apiKey);
}
