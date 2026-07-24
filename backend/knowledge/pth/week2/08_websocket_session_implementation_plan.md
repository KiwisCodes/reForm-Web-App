# WebSocket & Session Management: Implementation Plan
**Document Version:** 11.0  
**Target Platform:** reForm (Modular Monolith)  
**Author:** Senior Technical Lead  

---

## Part 1 — Re-Evaluation & Architectural Necessity of File 8

File 8 serves as the **concrete step-by-step Java & Spring Boot implementation roadmap** for building the AI WebSocket subsystem. While Files 10–22 detail the architectural specification, File 8 maintains the code file layout, component dependencies, refactoring rationale, and implementation progress tracking.

---

## Part 2 — Architecture & Package Structure (`com.reForm.backend.ai`)

Originally, WebSocket voice handlers were placed under `submission.websocket`. However, Real-Time AI Voice (Modes 3 & 4) is a **shared platform capability** used by two distinct domains:

1. **Form Builder (`com.reForm.backend.form`):** Form creators (like John) use Mode 4 as a **Form Architect Co-Builder Assistant** to verbally design form layouts and create fields.
2. **Form Filler (`com.reForm.backend.submission`):** Candidates (like Sarah) use Modes 3 & 4 to take **AI-powered voice interviews** evaluating skills against goals set in PostgreSQL.

If AI streaming code remained inside `submission/`, the `form/` domain would have to import from `submission/`, introducing **circular package dependencies**.

```text
                                [ Shared AI Infrastructure ]
                                  com.reForm.backend.ai
                                     /             \
                                    /               \
                                   ▼                 ▼
                        [ Form Builder ]       [ Form Filler ]
                        (com.reForm.form)    (com.reForm.submission)
```

**Solution:** All WebSocket gateways, Redis presence state trackers, AI adapters, and model provider strategies live inside the unified **`com.reForm.backend.ai`** module.

---

## Part 3 — Complete Package Layout

```text
com.reForm.backend.ai/
│
├── config/
│   ├── WebSocketConfig.java              ← Routes /ws/v1/voice & JwtHandshakeInterceptor [COMPLETED]
│   └── JwtHandShakeInterceptor.java      ← One-time HTTP upgrade security gate [COMPLETED]
│
├── port/
│   ├── IAiVoiceAdapter.java              ← Voice streaming adapter contract [COMPLETED]
│   └── IAiModelProviderStrategy.java     ← Model provider strategy contract [COMPLETED]
│
├── strategy/
│   ├── Gemini31LiveModelStrategy.java    ← Gemini 3.1 Live strategy [COMPLETED]
│   └── Gemini35FlashModelStrategy.java    ← Gemini 3.5 Flash strategy [COMPLETED]
│
├── service/
│   ├── SessionContextService.java        ← Dynamic prompt/tool assembler via strategies [COMPLETED]
│   ├── GeminiLiveVoiceAdapter.java       ← Mode 4 outbound Google WSS proxy adapter [IN IMPLEMENTATION]
│   └── CascadedVoiceAdapter.java         ← Mode 3 Deepgram + Flash + Cartesia manager [PLANNED]
│
├── state/
│   └── SessionTracker.java               ← Redis presence registry with fixed 2h TTL [COMPLETED]
│
└── websocket/
    └── VoiceSyncWSHandler.java           ← TCP lifecycle manager with ConcurrentHashMap [COMPLETED]
```

---

## Part 4 — Core Component Architecture & Technical Rules

1. **Dependency Injection Standard:**  
   `@Autowired` field injection is strictly forbidden. All Spring `@Service` and `@Component` beans use Lombok `@RequiredArgsConstructor` with `private final` fields.
2. **Rate Limiting Engine (`RateLimitServiceImpl`):**  
   Uses Bucket4j 8.19.0 with `LettuceBasedProxyManager.builderFor(redisConnection).withExpirationStrategy(...)` for microsecond CAS Lua execution directly in Redis.
3. **Database Prompt Config Entity (`FormAgentConfig`):**  
   One-to-One binding with `Form` storing `modelKey`, `systemPrompt`, `voiceName`, `temperature`, and `byokApiKeyEncrypted` (AES-256-GCM).
4. **WebSocket Protocol:**  
   Raw binary WebSockets (RFC 6455) using `BinaryWebSocketHandler` over TCP, bypassing STOMP text overhead.

---

## Part 5 — Frontend VAD (Voice Activity Detection) Cost Optimization

Streaming continuous audio generates input tokens even during silence if the microphone remains open.

### The Client-Side Solution:
We implement **Voice Activity Detection (VAD)** on the Next.js frontend using a WebAssembly module (`@ricky0123/vad-web` / Web Audio API):
* **User Speaking:** VAD detects speech $\rightarrow$ frontend streams binary PCM packets over WSS.
* **User Silent:** VAD detects silence $\rightarrow$ frontend **pauses sending binary frames**.

```text
  [ Candidate Mic ] ──► [ Frontend VAD ] ──(User Silent)──► PAUSE (0 packets sent)
                              │
                        (User Speaking)
                              │
                              ▼
                     [ WSS Binary Frames ] ──► [ VoiceSyncWSHandler.handleBinaryMessage() ]
```

### Benefit:
Cuts Gemini audio input costs by **40% to 50%** during pauses without requiring a single line of backend Java code change!

---

## Part 6 — Full Pricing Breakdown & Feature Spectrum

| Feature Mode | Tech Stack / Models | Transport Protocol | Cost / Minute | Cost / 10 Mins | Latency | Key Strengths & Use Cases |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **Mode 1: Manual** | Drag-and-Drop Canvas / Standard Inputs | REST HTTP (Stateless) | **$0.00** | **$0.00** | N/A | **Zero AI Cost.** Standard static form fields (inputs, dropdowns, uploads). |
| **Mode 2: Text Chat** | Text Chatbot / Gemini 3.6 Flash | REST HTTP (Stateless) | **~$0.002** | **~$0.02** | ~400ms | **Text AI Co-Builder & Chat Interview.** Structured JSON schemas, low cost. |
| **Mode 3: Voice Cascaded** | Deepgram STT $\rightarrow$ Flash 3.6 LLM $\rightarrow$ Cartesia TTS | Raw WebSocket (WSS) | **~$0.0176** | **~$0.176** | ~700–900ms | **Budget Voice Real-Time.** 35% cheaper than Mode 4, 2-WS + 1 REST stateless LLM optimization. |
| **Mode 4: Voice Native Live** | Gemini 3.1 Flash Live (`gemini-3.1-flash-live-preview`) | Raw WebSocket (WSS) | **~$0.027** | **~$0.27** | **~300ms** | **Premium Native Voice Real-Time.** Acoustic tone detection, native barge-in, sub-second latency. |

---

## Part 7 — Implementation Progress Tracker

1. **`com.reForm.backend.ai.config.WebSocketConfig.java`**:  
   Registers `/ws/v1/voice` and executes `JwtHandshakeInterceptor`. Validates `?token=` parameter and extracts `userId` and `role`. [COMPLETED]
2. **`com.reForm.backend.ai.state.SessionTracker.java`**:  
   Uses `RedisTemplate` to write session hashes. Fixed TTL bug by calling `redisTemplate.expire(sessionKey, SESSION_TTL)` (2 hours). [COMPLETED]
3. **`com.reForm.backend.ai.port.IAiVoiceAdapter.java` & `IAiModelProviderStrategy.java`**:  
   Decoupled port contracts for streaming voice adapters and model choice strategies. [COMPLETED]
4. **`com.reForm.backend.ai.strategy.Gemini31LiveModelStrategy.java` & `Gemini35FlashModelStrategy.java`**:  
   Concrete strategies resolving model IDs and configs dynamically. [COMPLETED]
5. **`com.reForm.backend.ai.service.SessionContextService.java`**:  
   Scaffolded prompt/tool assembler resolving target model strategies dynamically using functional stream matching (no `if/else`). [COMPLETED]
6. **`com.reForm.backend.core.service.RateLimitServiceImpl.java`**:  
   Restored Bucket4j rate-limiting engine using `LettuceBasedProxyManager`. [COMPLETED]
7. **`com.reForm.backend.ai.websocket.VoiceSyncWSHandler.java`**:  
   Extends `BinaryWebSocketHandler`. Implements `afterConnectionEstablished`, `handleBinaryMessage`, `handleTextMessage` (heartbeat PING/PONG), `handleTransportError`, and `afterConnectionClosed`. [COMPLETED]
8. **`com.reForm.backend.ai.service.GeminiLiveVoiceAdapter.java`**:  
   Outbound WSS proxy connection logic targeting Google's Gemini Multimodal Live API endpoint (`wss://generativelanguage.googleapis.com/ws/.../BidiGenerateContent`). [NEXT FOCUS]
