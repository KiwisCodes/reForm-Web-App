package com.reForm.backend.ai.strategy.stt;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.util.Map;

/**
 * DEEPGRAM NOVA-3 STT STRATEGY
 *
 * Provides WebSocket URL and header construction for Deepgram's Nova-3 speech model.
 */
@Component
public class DeepgramNova3SttStrategy implements ISttProviderStrategy {

    public static final String STT_KEY = "DEEPGRAM_NOVA_3";

    @Override
    public boolean supports(String sttKey) {
        return STT_KEY.equalsIgnoreCase(sttKey) || "DEEPGRAM".equalsIgnoreCase(sttKey);
    }

    @Override
    public String getSttKey() {
        return STT_KEY;
    }

    @Override
    public String buildWebSocketUrl(String apiKey, Map<String, Object> options) {
        int sampleRate = (int) options.getOrDefault("sampleRate", 16000);
        int channels = (int) options.getOrDefault("channels", 1);
        int utteranceEndMs = (int) options.getOrDefault("utteranceEndMs", 1000);
        String encoding = (String) options.getOrDefault("encoding", "linear16");

        return String.format(
            "wss://api.deepgram.com/v1/listen?encoding=%s&sample_rate=%d&channels=%d&model=nova-3&punctuate=true&interim_results=true&utterance_end_ms=%d&no_delay=true",
            encoding, sampleRate, channels, utteranceEndMs
        );
    }

    @Override
    public WebSocketHttpHeaders buildHeaders(String apiKey) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Token " + apiKey);
        return headers;
    }
}
