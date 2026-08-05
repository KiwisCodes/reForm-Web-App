package com.reForm.backend.ai.domain;

/**
 * VOICE MODE ENUM
 * 
 * Defines the 4 AI modes supported by reForm:
 * - MODE_1: Manual Form Fill (Traditional HTML Inputs)
 * - MODE_2: Text Chat Form Assistant (Gemini 3.6 Flash via REST)
 * - MODE_3: Voice Cascaded Pipeline (Deepgram STT -> Gemini 3.6 Flash -> Cartesia TTS)
 * - MODE_4: Voice Native Live (Google Gemini Multimodal Live API over WSS)
 */
public enum VoiceMode {
    MODE_1,
    MODE_2,
    MODE_3,
    MODE_4
}
