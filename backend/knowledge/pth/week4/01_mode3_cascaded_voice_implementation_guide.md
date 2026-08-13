# Mode 3 Cascaded Voice Architecture & Integration Guide
**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week4/01_mode3_cascaded_voice_implementation_guide.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Executive Summary

Mode 3 (Cascaded Voice Pipeline) implements a 3-stage modular voice architecture:
1. **Speech-to-Text (STT)**: Deepgram Nova-3 over WebSocket (`wss://api.deepgram.com`) for low-latency transcription (~100ms).
2. **LLM Reasoning**: Gemini 3.6 Flash over stateless HTTP REST (`generativelanguage.googleapis.com`) for prompt evaluation and tool calling (~400ms).
3. **Text-to-Speech (TTS)**: Cartesia Sonic over WebSocket (`wss://api.cartesia.ai`) for audio synthesis (~200ms).

---

## 2. Environment Variables & Setup

Add API keys to environment variables or shell configuration:
```bash
export GEMINI_API_KEY="your-google-ai-studio-key"
export DEEPGRAM_API_KEY="your-deepgram-console-key"
export CARTESIA_API_KEY="your-cartesia-console-key"
```

---

## 3. Sequence Flow & Barge-in Handling

```text
[ Client Browser ] --- (PCM 16kHz Audio) ---> [ VoiceSyncWSHandler ]
                                                    |
                                                    v
                                      [ CascadedVoiceAdapter ]
                                      /          |           \
                                     /           |            \
                   [ Deepgram STT WSS ]   [ Gemini Flash REST ] [ Cartesia TTS WSS ]
                   (Transcript: "Hi") --> (Response: "Hello!") -> (PCM 24kHz Audio)
                                                                       |
[ Client Speaker ] <---------------- (PCM 24kHz Audio) --------------+
```

### Barge-In Signal Flow:
1. Deepgram emits `speech_started: true` or `UtteranceEnd` event when the candidate interrupts mid-AI-speaking turn.
2. `CascadedVoiceAdapter.triggerBargeIn()` sends a cancellation frame `{"context_id": "...", "cancel": true}` to Cartesia TTS.
3. `CascadedVoiceAdapter` emits `{"type": "FLUSH_AUDIO_BUFFER"}` to the client browser, immediately halting audio playback.
