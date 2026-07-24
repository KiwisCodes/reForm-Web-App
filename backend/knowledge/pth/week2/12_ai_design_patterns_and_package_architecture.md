# 12. AI Design Patterns & Package Architecture
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Module Structure & Package Refactoring

The AI infrastructure is isolated in the root package **`com.reForm.backend.ai`**.

```text
com.reForm.backend.ai/
├── config/             (WebSocketConfig, JwtHandshakeInterceptor)
├── port/               (IAiVoiceAdapter, IAiModelProviderStrategy)
├── strategy/           (Gemini31LiveModelStrategy, Gemini35FlashModelStrategy)
├── service/            (SessionContextService, GeminiLiveVoiceAdapter, CascadedVoiceAdapter)
├── state/              (SessionTracker - Redis 2h TTL)
└── websocket/          (VoiceSyncWSHandler - TCP Lifecycle Manager)
```

### Why Package Refactoring Was Necessary:
Real-Time AI Voice is used by **Form Builders in `form/`** (Co-Builder Assistant) and **Form Fillers in `submission/`** (Candidate Interviewer). Placing AI code inside `submission/` forced `form/` to import from `submission/`, creating circular package dependencies. `com.reForm.backend.ai` acts as the clean, shared infrastructure module.

---

## 2. The 4 Core Design Patterns

```text
                                  [ Client Browser ]
                                          │
                                          ▼
                               [ VoiceSyncWSHandler ]
                                          │
                        Calls: VoiceAdapterFactory.getAdapter(form.getMode())
                                          │
                                          ▼
                                [ IAiVoiceAdapter ]  (Bridge / Adapter Pattern)
                                         / \
                                        /   \
                                       /     \
                                      ▼       ▼
           [ CascadedVoiceAdapter ]               [ GeminiLiveVoiceAdapter ]
                 (MODE 3)                               (MODE 4)
           ┌───────────────────────┐              ┌────────────────────────┐
           │ Deepgram STT (WSS)    │              │ Gemini 3.1 Live (WSS)  │
           │ Gemini 3.6 Flash      │              │ (Native Audio-to-Audio)│
           │ Cartesia TTS (WSS)    │              └────────────────────────┘
           └───────────────────────┘
```

1. **Bridge / Adapter Pattern (`IAiVoiceAdapter`):** Decouples low-level Spring WebSocket connection handling (`VoiceSyncWSHandler`) from specific AI vendor streaming protocols (Gemini Live vs. OpenAI Realtime vs. Cascaded Pipeline).
2. **Strategy Pattern (`IAiModelProviderStrategy`):** Resolves target AI model configurations (`Gemini31LiveModelStrategy`, `Gemini35FlashModelStrategy`) dynamically using functional stream matching, **eliminating all `if/else` statements**.
3. **Factory Pattern (`VoiceAdapterFactory`):** Inspects the target form mode at runtime and instantiates the correct adapter strategy bean.
4. **Single Responsibility Principle (SRP):** `VoiceSyncWSHandler` manages TCP sockets; `SessionContextService` manages prompt/tool assembly; `SessionTracker` manages Redis presence.

---

## 3. Scalability & Modifiability Evaluation (Open/Closed Principle)

### A. Scalability Evaluation
* **Horizontal Socket Scaling:** Physical `WebSocketSession` objects live in server RAM, but lightweight metadata (`session:{userId}`) is registered in Redis with a 2-hour TTL. Sticky sessions on Nginx/ALB route client frames to the correct node while any cluster node can verify user online status via Redis.
* **Lock-Free Rate Limiting:** High-frequency rate limiting uses Bucket4j + Lettuce CAS Lua scripts directly inside Redis, bypassing JVM thread locks.
* **Async Offloading:** Post-session candidate evaluation and scoring are offloaded to asynchronous background worker threads (`@Async`), keeping real-time voice streaming unblocked.

### B. Modifiability Evaluation (Open/Closed Principle)
* **Adding a New AI Model (e.g. Gemini 4.0):** Create 1 new class `Gemini40LiveModelStrategy implements IAiModelProviderStrategy` and annotate with `@Component`. Zero modifications required in `SessionContextService`, `VoiceSyncWSHandler`, or `GeminiLiveVoiceAdapter`.
* **Adding a New Voice Adapter (e.g. OpenAI Realtime):** Create 1 new class `OpenAiVoiceAdapter implements IAiVoiceAdapter`. Zero modifications required in socket handlers.
