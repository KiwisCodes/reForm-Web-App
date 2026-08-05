# Step 0 Survey — Part 3: Form Filler Agent Suite & System Governance (R1, R3 Requirements)

**Document Version:** 1.0  
**Author:** teamwork_preview_explorer_survey_3  
**Date:** 2026-08-05  
**Target System:** reForm Monolith (`com.reForm.backend.ai` & WebSockets / Event Infrastructure)  

---

## 1. Executive Summary & Architecture Blueprint

The reForm platform requires a multi-agent architecture supporting both **Form Builder** (co-creation) and **Form Filler** (candidate interview & interactive submission) across 4 operational modes:
- **Mode 1 (Static/Dynamic Text REST)**: Traditional form loading, field rendering, and REST submission.
- **Mode 2 (Conversational Text Chat)**: Interactive text-based AI co-building and multi-turn interview execution via REST/WebSockets.
- **Mode 3 (Voice Cascaded Pipeline)**: High-speed, cost-effective 3-stage voice pipeline (Deepgram Nova-3 STT $\rightarrow$ Gemini 3.6 Flash LLM $\rightarrow$ Cartesia Sonic TTS) at ~$0.0176/min.
- **Mode 4 (Voice Native Multimodal Live Stream)**: Low-latency (~300ms) native audio-to-audio streaming via Google Gemini Multimodal Live API over WebSockets at ~$0.0270/min.

To ensure stability during real-time voice streaming (~50 audio PCM frames/sec), the architecture enforces **strict decoupling** of real-time audio socket I/O from background LLM reasoning, safety checks, vector searches, state persistence, and post-session scoring.

---

## 2. Decoupled Spring `@Component` Multi-Agent Architecture (R1, R3)

### Single Responsibility Principle (SRP) & Event-Driven Architecture (EDA)
Each agent in reForm operates as an independent, decoupled Spring `@Component` service bean. Communication between WebSocket streaming handlers (`VoiceSyncWSHandler`, `GeminiLiveVoiceAdapter`, `CascadedVoiceAdapter`) and background agents is entirely event-driven via Spring's `ApplicationEventPublisher`, `@EventListener`, and non-blocking `@Async` thread pools.

```
                     reForm MULTI-AGENT SYSTEM ARCHITECTURE
                     
                            [ Candidate / User Browser ]
                                         │
                                   (WSS Connection)
                                         ▼
                            [ VoiceSyncWSHandler ]
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 ▼                                               ▼
     [ GeminiLiveVoiceAdapter ]                       [ CascadedVoiceAdapter ]
       (Mode 4 Native Live)                             (Mode 3 STT/LLM/TTS)
                 │                                               │
                 └───────────────────────┬───────────────────────┘
                                         │ Event Bus (ApplicationEventPublisher)
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
 [ GuardrailAgent ]             [ MemoryGoalAgent ]              [ BillingAgent ]
 (Safety Moderation)            (Redis Goal Hash)                (VAD / Metering)
        │                                │                                │
        ▼                                ▼                                ▼
 [ RagSearchAgent ]             [ LayoutAgent ]                 [ EvaluationAgent ]
 (pgvector Context)             (Canvas Modification)            (@Async Post-Call)
```

---

## 3. Comprehensive Form Filler Agent Suite Specifications

### 1. `GuardrailAgent` (Safety, Content Moderation & Security)
- **Role & Purpose**: Real-time moderation protecting the system against prompt injection, jailbreak attempts, toxic language, and out-of-domain conversations (e.g., candidate attempting to trick interview AI into writing Python scripts or discussing politics).
- **Technical Architecture**: Spring `@Component` service using Google `text-embedding-004` embeddings and PostgreSQL `pgvector` HNSW cosine similarity search.
- **Latency Threshold**: $<5\text{ms}$ execution time per turn.
- **Supported Modes**: Modes 1, 2, 3, and 4 (Unified Safety Layer).
- **Input Interface**: `GuardrailCheckRequest(UUID sessionId, String userId, Role role, String transcriptText)`.
- **Output Interface**: `GuardrailResult(boolean safe, double maxSimilarityScore, String matchedCategory, String fallbackResponse)`.
- **Trigger Mechanism**: Synchronously or ultra-fast async check executed per candidate audio/text turn before sending to client or completing AI response generation.
- **State Persistence & Audit**: If `maxSimilarityScore > 0.85`, triggers an immediate turn cancellation frame (`INTERRUPTED` or safety fallback text) and logs a violation audit entry into `guardrail_audit_log` in PostgreSQL.

### 2. `MemoryGoalAgent` (Session Memory & Goal Tracking)
- **Role & Purpose**: Prevents AI interview loops and ensures all target evaluation criteria/questions defined in the form's `ConversationalBlock` are systematically covered during the session.
- **Technical Architecture**: Spring `@Component` service managing an active Redis Hash checklist (`opsForHash()`).
- **Supported Modes**: Modes 1, 2, 3, and 4.
- **Input Interface**: `MemoryStateUpdateRequest(UUID sessionId, String userTranscript, List<ConversationalGoal> definedGoals)`.
- **Output Interface**: `MemoryGoalTrackerState(Map<String, GoalStatus> goalMap, List<String> pendingGoalPrompts, double completionPercentage)`.
- **Trigger Mechanism**: Executed per user turn prior to compiling system setup context in `SessionContextService`.
- **State Persistence**: Redis key `session:{userId}:goals` with an explicit 2-hour TTL lease (refreshed on client `PING` frames). At session close, final goal completion states are persisted into PostgreSQL `FormResponse` / `Submission`.

### 3. `BillingAgent` (Usage Tracking & VAD Protection)
- **Role & Purpose**: Real-time metering of token usage, audio duration, account credit deductions, and Voice Activity Detection (VAD) silence handling to prevent runaway API costs during open-mic pauses.
- **Technical Architecture**: Spring `@Component` service integrating `@Scheduled` heartbeat jobs, Redis credit caches, and VAD WebSocket event handlers.
- **Supported Modes**: 
  - Modes 1 & 2: Token-based billing per REST payload.
  - Mode 3: Audio duration billing ($0.0176/min) + token metering.
  - Mode 4: Native Live audio billing ($0.0270/min) + token metering.
- **Input Interface**: `BillingUsageEvent(UUID userId, VoiceMode mode, long durationMs, int inputTokens, int outputTokens, boolean vadSilenceActive)`.
- **Output Interface**: `BillingStatus(boolean balanceValid, BigDecimal deductedAmount, BigDecimal remainingCredits, boolean disconnectTriggered)`.
- **Trigger Mechanism**:
  - Continuous VAD audio frame monitoring: If candidate silence $>45\text{s}$, issues a prompt ("Are you still there?") or pauses streaming.
  - Connection closed (`afterConnectionClosed`): Computes final call duration and deducts user wallet balance.
- **State Persistence**: Atomic balance update in PostgreSQL `user_wallet_ledger` and cache update in Redis (`user:credits:{userId}`).

### 4. `RagSearchAgent` (Semantic Document Retrieval)
- **Role & Purpose**: Performs high-speed semantic search over user-uploaded documents (job descriptions, resumes, syllabi, compliance handbooks) to answer candidate questions or ground AI interview questions.
- **Technical Architecture**: Spring `@Component` service querying PostgreSQL `document_embeddings` via `pgvector` HNSW vector indexes.
- **Supported Modes**: Modes 1, 2, 3, and 4 (Invoked via `searchUserDocument` tool or auto-RAG context compiler).
- **Input Interface**: `RagSearchQuery(UUID formId, String queryText, int topK, double minSimilarity)`.
- **Output Interface**: `RagSearchResult(List<DocumentChunk> matchingChunks, String synthesizedContext)`.
- **Trigger Mechanism**: Triggered asynchronously when Gemini Live / Cascaded Adapter invokes the `searchUserDocument` function tool call.
- **State Persistence**: Reads from pre-indexed `document_embeddings` table.

### 5. `EvaluationAgent` (Post-Session Evaluation & Scoring)
- **Role & Purpose**: Post-call candidate evaluation, rubric scoring (0-100), automated performance report generation, and submission record finalization. Runs strictly after the call ends so zero latency is added to the real-time voice stream.
- **Technical Architecture**: Spring `@Component` service listening for `@Async @EventListener` events when WebSocket sessions terminate.
- **Supported Modes**: Modes 1, 2, 3, and 4.
- **Input Interface**: `PostSessionEvaluationEvent(UUID sessionId, UUID formId, String candidateUserId, List<TranscriptTurn> fullTranscript, Map<String, Object> savedFieldResponses)`.
- **Output Interface**: `EvaluationResult(double overallScore, String candidateSummary, Map<String, Double> rubricBreakdown, String generatedReportUrl)`.
- **Trigger Mechanism**: Fired asynchronously upon socket teardown (`afterConnectionClosed`) or when `endSession` tool executes with `includeEvaluation=true`. Prompts **Gemini 3.6 Flash** for evaluation summary.
- **State Persistence**: Saves complete `Submission` entity record into PostgreSQL `submissions` table and updates form analytics.

---

## 4. WebSockets Event Handling & Audio Streaming Thread Architecture

### Zero Blocking Calls Mandate
In real-time audio applications (Mode 3 and Mode 4), Tomcat's WebSocket threads process binary PCM audio payloads received at **50 frames per second** (~20ms per frame). 

**Mandate**: The WebSocket event handler thread (`VoiceSyncWSHandler`, `GeminiLiveVoiceAdapter`, `CascadedVoiceAdapter`) MUST NOT execute any blocking DB queries, external REST calls, or heavy vector calculations. 

```
                               THREADING MODEL
                               
  [ Client Browser PCM Frames ] ──(50 fps)──► [ WebSocket IO Thread (Tomcat) ]
                                                        │
                                                        │ (Non-blocking forward)
                                                        ▼
                                           [ Outbound WSS Proxy ]
                                                        │
                                                        │ (Spring Event Bus)
                                                        ▼
                                           [ @Async Worker Thread Pool ]
                                              (TaskExecutor - 200 Workers)
                                                        │
                                 ┌──────────────────────┼──────────────────────┐
                                 ▼                      ▼                      ▼
                        [ LayoutAgent ]        [ RagSearchAgent ]    [ EvaluationAgent ]
```

### Event-Driven Triggers & Data Flow
1. **Tool Calls**: When Gemini Live issues a `toolCall` (e.g. `modifyFormLayout`, `searchUserDocument`, `evaluateResponse`), the adapter creates a Java 21 `Record` event (e.g. `FormLayoutModificationEvent`) and publishes it to `ApplicationEventPublisher`.
2. **Async Listeners**: Background `@Async` methods (e.g., `LayoutAgent.handleLayoutModification()`) pick up the event on a dedicated `TaskExecutor` thread pool.
3. **Canvas Push**: Updated layout blocks or evaluated scores are pushed back to the client UI asynchronously over STOMP/WebSocket or session response frames.

### Redis State Persistence & Handshake Interceptors
- `JwtHandshakeInterceptor`: Extracts JWT bearer token and connection query params (`mode`, `formId`, `modelKey`) before socket upgrade. Stores identity (`userId`, `role`) directly into `session.getAttributes()`.
- `SessionTracker`: Registers session metadata in Redis with a 2-hour TTL expiration. Heartbeat `PING` text frames trigger `sessionTracker.refreshTTL(userId)`.

---

## 5. Form Filler Modes 1-4 Feature & Interface Matrix

| Feature / Metric | Mode 1 (Static/Dynamic REST) | Mode 2 (Conversational Text Chat) | Mode 3 (Voice Cascaded Pipeline) | Mode 4 (Voice Native Live Stream) |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Use Case** | Form Filling / Survey REST | Multi-turn Text Interview | Candidate Voice Interview | Form Builder Co-Building & Live Voice Interview |
| **Transport Protocol** | HTTP REST JSON | HTTP REST / WebSocket Text | WebSocket (`VoiceSyncWSHandler`) | WebSocket (`VoiceSyncWSHandler`) |
| **Primary LLM Engine** | Gemini 3.6 Flash | Gemini 3.6 Flash | Gemini 3.6 Flash (Unary REST) | Gemini 3.1 Live (Bidi WSS) |
| **STT / TTS Services** | N/A | N/A | Deepgram Nova-3 (STT) & Cartesia Sonic (TTS) | Gemini Native Multimodal |
| **End-to-End Latency** | ~500ms (REST) | ~600ms (Text WSS) | ~700ms (Cascaded) | **~300ms (Native Live)** |
| **Barge-in Support** | N/A | N/A | Manual (`speech_started` $\rightarrow$ `FLUSH`) | **Native (`interrupted: true`)** |
| **GuardrailAgent** | Synchronous REST Check | Async REST Check | Real-time Transcript Check | Real-time Transcript Check |
| **MemoryGoalAgent** | DB Field State | Redis Goal Hash | Redis Goal Hash | Redis Goal Hash |
| **BillingAgent** | Per REST Call Token Fee | Per Message Token Fee | $0.0176/min + Token Metering | $0.0270/min + Token Metering |
| **RagSearchAgent** | Context Injection | Tool Call / Context | Tool Call (`searchUserDocument`) | Tool Call (`searchUserDocument`) |
| **EvaluationAgent** | Post-Submit REST | Post-Submit REST | Async Post-Call Teardown | Async Post-Call Teardown |

### Universal Component Reuse Architecture
All 4 modes share:
1. `FormAiAgentProfile`: Central database entity in PostgreSQL storing persona, temperature, voice name, and custom prompt templates.
2. `SessionContextService`: Central service compiling prompt templates and function calling tool declarations.
3. `ToolCallRegistry`: Unified strategy map resolving all 18 tool call handlers (`SaveFieldResponseToolHandler`, `EvaluateResponseToolHandler`, `ModifyFormLayoutToolHandler`, etc.).

---

## 6. System Governance & Dynamic Sub-Agent Spawning

### Security Governance
- **Authentication**: JWT token validation during WSS handshake (`JwtHandshakeInterceptor`).
- **Authorization**: Role-based tool access control (`Role.FORM_BUILDER` vs `Role.FORM_FILLER`). Form Filler sessions cannot invoke form modification or publishing tools.
- **Guardrail Enforcement**: Dynamic vector similarity checking against toxic/jailbreak prompt vectors.

### Dynamic Sub-Agent Spawning Framework
When a session encounters a heavy or domain-specific workload, the main agent delegates to specialized sub-agents managed by `SubAgentFactory` and executed via `AsyncTaskExecutor`:

```
                       DYNAMIC SUB-AGENT SPAWNING FLOW

   Main Voice Stream (Gemini Live WSS) ──► Candidate submits code / document
                                                      │
                                                      ▼
                                     SubAgentFactory.spawnSubAgent()
                                                      │
                                                      ▼
                                       Spring AsyncTaskExecutor Pool
                                                      │
                       ┌──────────────────────────────┼──────────────────────────────┐
                       ▼                              ▼                              ▼
            [ CodeAnalysisSubAgent ]       [ DocumentOcrSubAgent ]         [ ScoringSubAgent ]
            (Sandboxed Execution)          (Tesseract/Tika OCR)           (Multi-Criteria Score)
```

1. **`CodeAnalysisSubAgent`**: Spawned when candidates submit code during technical interviews. Executes code in sandboxed containers and returns results.
2. **`DocumentOcrSubAgent`**: Spawned when users upload PDFs/images. Runs Tesseract OCR / Apache Tika text extraction and updates vector index.
3. **`ScoringSubAgent`**: Spawned for multi-criteria rubric scoring across multiple domain competencies in parallel worker threads.

---

## 7. Gap Analysis & Required Components to Build/Refactor

Based on our survey of the existing Java codebase (`com.reForm.backend.ai`), the following Spring `@Component` service agents and event classes are specified in the architecture and need to be created/implemented:

1. **New Agent Classes (`com.reForm.backend.ai.agent`)**:
   - `GuardrailAgent.java`: Spring `@Component` with `pgvector` HNSW search and `text-embedding-004` integration.
   - `MemoryGoalAgent.java`: Spring `@Service` with Redis `opsForHash()` state management.
   - `BillingAgent.java`: Spring `@Component` with VAD silence monitoring and credit deduction.
   - `RagSearchAgent.java`: Spring `@Service` executing hybrid vector search over `document_embeddings`.
   - `EvaluationAgent.java`: Spring `@Service` with `@Async @EventListener` handling post-session scoring.

2. **New Event Classes (`com.reForm.backend.ai.event`)**:
   - `GuardrailViolationEvent.java`: Immutable record for safety violations.
   - `MemoryGoalUpdatedEvent.java`: Immutable record for goal status updates.
   - `BillingUsageEvent.java`: Immutable record for token/audio usage.
   - `PostSessionEvaluationEvent.java`: Immutable record for post-call evaluation.

3. **Sub-Agent Framework (`com.reForm.backend.ai.agent.subagent`)**:
   - `SubAgentFactory.java`: Factory bean managing async sub-agent instantiation.
   - `CodeAnalysisSubAgent.java`, `DocumentOcrSubAgent.java`, `ScoringSubAgent.java`.

---

## 8. Summary of Findings

1. **Architecture Integrity**: The project already possesses a clean foundation (`VoiceSyncWSHandler`, `GeminiLiveVoiceAdapter`, `CascadedVoiceAdapter`, `SessionContextService`, `LayoutAgent`, and 18 `IToolCallHandler` implementations).
2. **Decoupling Compliance**: Form Filler agents fit into the existing Event-Driven Architecture (EDA) via Spring `@Async @EventListener` and `ApplicationEventPublisher`.
3. **Zero-Blocking Guarantee**: Heavy LLM prompts, pgvector cosine similarity checks, RAG searches, and post-session evaluations are strictly offloaded from the WebSocket audio streaming thread to Spring worker thread pools (`AsyncTaskExecutor`).
