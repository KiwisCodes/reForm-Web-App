# Mode 3 Cascaded Voice Architecture & Unified 4-Mode Integration
**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week3/09_mode3_cascaded_voice_architecture_and_design.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Executive Summary & Why Mode 3 Is Needed

While Mode 4 (Gemini 3.1 Live) provides native audio-to-audio streaming at ~300ms latency, **Mode 3 (Voice Cascaded Pipeline)** provides a budget-friendly real-time voice alternative that is **~35% cheaper** (~$0.0176/min vs ~$0.027/min).

Mode 3 decouples the voice pipeline into 3 best-of-breed microservices:
1. **Speech-to-Text (STT)**: Deepgram Nova-3 over WebSocket (`wss://api.deepgram.com`) for ultra-fast transcriptions (~100ms).
2. **LLM Reasoning**: Gemini 3.6 Flash via stateless HTTP REST API (~400ms). Shares the exact same LLM engine as **Mode 2 (Text Chat)**!
3. **Text-to-Speech (TTS)**: Cartesia Sonic over WebSocket (`wss://api.cartesia.ai`) for realistic audio synthesis (~200ms).

---

## 2. Technical Glossary for Mode 3

| Term / Component | Technical Type | Function & Role | Protocol / Format |
| :--- | :--- | :--- | :--- |
| **`CascadedVoiceAdapter`** | Spring `@Component` implementing `IAiVoiceAdapter` | Manages persistent WebSocket tunnels to Deepgram (STT) and Cartesia (TTS) while invoking Gemini 3.6 Flash (LLM). | Java Class |
| **Deepgram Nova-3** | External WSS Service | Real-time audio transcription service emitting text JSON frames (`is_final: true`). | WebSocket (`wss://api.deepgram.com`) |
| **Gemini 3.6 Flash** | External REST Service | Text reasoning engine used by both Mode 2 and Mode 3. Executes tool calls (`modifyFormLayout`, `searchUserDocument`). | HTTP REST |
| **Cartesia Sonic** | External WSS Service | Real-time text-to-speech audio synthesizer accepting text chunks and streaming binary PCM audio bytes. | WebSocket (`wss://api.cartesia.ai`) |
| **Manual Barge-in** | Protocol Signal | Triggered when Deepgram emits `speech_started: true`. `CascadedVoiceAdapter` sends `{"cancel": true}` to Cartesia and `FLUSH` frame to browser speaker. | JSON Socket Frame |

---

## 3. End-to-End Mode 3 Connection Lifecycle

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: SESSION HANDSHAKE & TUNNEL INITIALIZATION                          │
│ 1. Client connects: GET /ws/v1/voice?token=JWT&formId=UUID&mode=MODE_3     │
│ 2. JwtHandshakeInterceptor validates JWT & writes session attributes        │
│ 3. VoiceSyncWSHandler delegates to CascadedVoiceAdapter.startSession()     │
│ 4. CascadedVoiceAdapter opens WSS socket to Deepgram Nova-3 STT             │
│ 5. CascadedVoiceAdapter opens WSS socket to Cartesia Sonic TTS              │
│ 6. SessionContextService loads FormAiAgentProfile & compiles prompt template│
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 2: ACTIVE 3-STAGE VOICE PIPELINE LOOP                                 │
│ 1. Mic Audio (Client) ──► Raw PCM Bytes ──► Deepgram STT WSS                │
│ 2. Deepgram emits final text transcript ("I built microservices in Java")   │
│ 3. CascadedVoiceAdapter calls SessionContextService & Gemini 3.6 Flash (REST)│
│    └── Shares exact same REST service used by Mode 2 (Text Chat)!          │
│ 4. Gemini 3.6 Flash returns text response ("Great! Which database?")        │
│ 5. CascadedVoiceAdapter streams text response to Cartesia Sonic TTS WSS     │
│ 6. Cartesia streams binary PCM audio bytes ──► Client Speaker plays speech! │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                         (If User Interrupts Mid-Sentence)
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 3: MANUAL BARGE-IN INTERRUPTION                                       │
│ 1. Candidate speaks while Cartesia audio is playing                         │
│ 2. Deepgram STT detects voice ──► Emits event: {"speech_started": true}     │
│ 3. CascadedVoiceAdapter catches event ──► Sends cancel frame to Cartesia:   │
│    {"context_id": "c123", "cancel": true}                                   │
│ 4. Adapter sends FLUSH frame to Browser ──► Speaker stops mid-sentence!    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 4: SESSION CLEANUP                                                    │
│ 1. Client closes socket connection                                          │
│ 2. CascadedVoiceAdapter closes Deepgram and Cartesia WSS sockets cleanly    │
│ 3. Evaluation Agent runs @Async: computes candidate match score & report    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Unified Architecture: Sharing Components Across Modes 2, 3, & 4

To ensure we **do not rewrite code when Mode 2 is built later**, the architecture enforces strict reuse across all 3 modes:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ UNIFIED REUSE MATRIX ACROSS MODES 2, 3, & 4                                 │
│                                                                             │
│ Component / Subsystem              │ Mode 2 (Text) │ Mode 3 (Voice)│ Mode 4 (Live)│
├────────────────────────────────────┼───────────────┼───────────────┼──────────────┤
│ FormAiAgentProfile (PostgreSQL DB) │    REUSED     │    REUSED     │    REUSED    │
│ SessionContextService (Prompt/RAG) │    REUSED     │    REUSED     │    REUSED    │
│ Gemini 3.6 Flash Service (REST)    │  PRIMARY LLM  │  PRIMARY LLM  │ TOOL SUB-LLM │
│ IAiVoiceAdapter Interface          │      N/A      │  Cascaded     │  GeminiLive  │
│ FormLayoutModificationEvent (EDA)  │    REUSED     │    REUSED     │    REUSED    │
│ GuardrailAgent (pgvector)          │    REUSED     │    REUSED     │    REUSED    │
│ MemoryGoalAgent (Redis Hash)       │    REUSED     │    REUSED     │    REUSED    │
│ RagSearchAgent (searchUserDocument)│    REUSED     │    REUSED     │    REUSED    │
│ EvaluationAgent (Summary Report)   │    REUSED     │    REUSED     │    REUSED    │
└────────────────────────────────────┴───────────────┴───────────────┴──────────────┘
```

### Key Reuse Guarantees:
1. **Mode 2 (Text Chat)**: Uses `Gemini36FlashService` directly via REST. When the user writes a message, it calls `SessionContextService.buildSetupContext()`, queries `FormAiAgentProfile`, runs `GuardrailAgent` and `RagSearchAgent`, and returns text.
2. **Mode 3 (Voice Cascaded)**: Reuses `Deepgram` for STT, passes transcribed text into `Gemini36FlashService` (the exact same Mode 2 service!), and passes response text to `Cartesia` for TTS.
3. **Mode 4 (Voice Native Live)**: Uses `GeminiLiveVoiceAdapter` for direct native audio streaming over WebSockets, while sharing the exact same `FormAiAgentProfile`, `SessionContextService`, `RagSearchAgent`, and `LayoutAgent`!
