package com.reForm.backend.ai.factory;

import com.reForm.backend.ai.domain.VoiceMode;
import com.reForm.backend.ai.port.IAiVoiceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI VOICE ADAPTER FACTORY (Factory Pattern)
 * 
 * WHY USE THE FACTORY PATTERN HERE?
 * Decouples VoiceSyncWSHandler from specific AI voice implementations.
 * Instead of hardcoding if/else checks inside VoiceSyncWSHandler for every mode,
 * this factory dynamically resolves the matching IAiVoiceAdapter bean based on VoiceMode.
 * 
 * SPRING BOOT AUTOMAGIC BEAN MAP:
 * Spring automatically populates Map<String, IAiVoiceAdapter> with all registered beans!
 * - "geminiLiveVoiceAdapter" -> GeminiLiveVoiceAdapter (Mode 4 Native Live Voice)
 * - "cascadedVoiceAdapter"   -> CascadedVoiceAdapter (Mode 3 Cascaded STT -> LLM -> TTS)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiVoiceAdapterFactory {

    // Spring automatically populates this map with all IAiVoiceAdapter beans keyed by bean name
    private final Map<String, IAiVoiceAdapter> voiceAdapters;

    /**
     * Resolves the matching IAiVoiceAdapter strategy for a given VoiceMode.
     * 
     * @param mode Selected VoiceMode (e.g. MODE_3 or MODE_4)
     * @return Resolved IAiVoiceAdapter implementation
     */
    public IAiVoiceAdapter getAdapter(VoiceMode mode) {
        log.info("[AiVoiceAdapterFactory]: Resolving strategy adapter for Mode: {}", mode);

        IAiVoiceAdapter adapter = switch (mode) {
            case MODE_4 -> voiceAdapters.get("geminiLiveVoiceAdapter");
            case MODE_3 -> voiceAdapters.get("cascadedVoiceAdapter");
            default -> throw new IllegalArgumentException("Unsupported Voice Mode for real-time WebSocket session: " + mode);
        };

        if (adapter == null) {
            log.error("[AiVoiceAdapterFactory]: No bean found for requested Mode: {}", mode);
            throw new IllegalStateException("Voice adapter strategy bean not found for mode: " + mode);
        }

        return adapter;
    }
}
