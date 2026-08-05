package com.reForm.backend.ai.strategy.tts;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * CARTESIA SONIC 3.5 TTS STRATEGY
 *
 * Encapsulates synthesis frame construction and cancel payloads for Cartesia's Sonic 3.5 model.
 */
@Component
public class CartesiaSonic35TtsStrategy implements ITtsProviderStrategy {

    public static final String TTS_KEY = "CARTESIA_SONIC_3_5";
    private static final String CARTESIA_VERSION = "2024-06-10";
    private static final int DEFAULT_SAMPLE_RATE = 24000;

    @Override
    public boolean supports(String ttsKey) {
        return TTS_KEY.equalsIgnoreCase(ttsKey) || "CARTESIA".equalsIgnoreCase(ttsKey);
    }

    @Override
    public String getTtsKey() {
        return TTS_KEY;
    }

    @Override
    public String buildWebSocketUrl(String apiKey) {
        return String.format("wss://api.cartesia.ai/tts/websocket?api_key=%s&cartesia_version=%s", apiKey, CARTESIA_VERSION);
    }

    @Override
    public String buildSynthesisPayload(ObjectMapper objectMapper, String text, String voiceId, String contextId) throws Exception {
        Map<String, Object> cartesiaFrame = Map.of(
            "model_id", "sonic-3.5",
            "transcript", text,
            "voice", Map.of(
                "mode", "id",
                "id", voiceId
            ),
            "output_format", Map.of(
                "container", "raw",
                "encoding", "pcm_s16le",
                "sample_rate", DEFAULT_SAMPLE_RATE
            ),
            "context_id", contextId
        );
        return objectMapper.writeValueAsString(cartesiaFrame);
    }

    @Override
    public String buildCancelPayload(ObjectMapper objectMapper, String contextId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "context_id", contextId,
            "cancel", true
        ));
    }
}
