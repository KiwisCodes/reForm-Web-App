# 15. Mode 4 Native Live Voice Architecture
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Mode 4 Overview: Native Audio-to-Audio

Mode 4 uses a single, direct, bidirectional WebSocket connection to Google's Gemini Multimodal Live API (`models/gemini-3.1-flash-live-preview`).

```text
                        MODE 4 NATIVE AUDIO-TO-AUDIO PIPELINE
                        
  [ Client Mic PCM Audio ] ──► [ VoiceSyncWSHandler ]
                                        │
                                        ▼
                            [ GeminiLiveVoiceAdapter ]
                                        │
                        (Outbound WSS Session Proxy)
                                        │
                                        ▼
             [ Google Gemini Live API: BidiGenerateContent ]
             (wss://generativelanguage.googleapis.com/ws/...)
                                        │
         ┌──────────────────────────────┴──────────────────────────────┐
         ▼                                                             ▼
  [ Native Audio Outbound ]                                 [ Tool Call Event ]
  (PCM 16-bit Audio Stream)                              (modifyFormLayout Trigger)
         │                                                             │
         ▼                                                             ▼
  [ Forward to Client Speaker ]                            [ Spring Event Bus ]
  (Sub-second latency ~300ms)                              (Dispatches to LayoutAgent)
```

---

## 2. Key Mode 4 Implementation Components

### A. Target Endpoint & Connection Setup
* **Endpoint:** `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=RESOLVED_KEY`
* **Handshake Payload (`BidiGenerateContentSetup`):** Transmits setup JSON containing system instructions, `modifyFormLayout` tool schema declarations, and voice name (`Puck`).

### B. Base64 Audio Tunneling
* **Client to Google:** PCM 16-bit 16kHz binary audio frames arriving at `VoiceSyncWSHandler` are encoded to Base64 and sent to Google via `realtimeInput`.
* **Google to Client:** Incoming Base64 audio chunks from Google are decoded and forwarded as raw binary frames (`BinaryMessage`) to the client speaker.

### C. Native Interruption Handling (Barge-in)
* Google Gemini Live auto-detects candidate speech natively.
* When the candidate speaks while the AI is outputting audio, Google flushes its internal generation buffer and sends an `interrupted: true` JSON signal.
* `GeminiLiveVoiceAdapter` forwards an audio clear frame to the client browser to immediately stop audio playback.
