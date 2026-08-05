# Handoff Report — Step 0 Survey — Part 3: Form Filler Agent Suite & System Governance

**Agent ID:** teamwork_preview_explorer_survey_3  
**Working Directory:** `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_3`  
**Target Milestone:** Step 0 Survey — Part 3 (Form Filler Agent Suite & Governance, R1/R3 Requirements)  

---

## 1. Observation

1. **Original Request Path**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md`
   - Line 11-13: R1 requiring clear, decoupled Spring `@Component` service agents for platform processes (Form Builder, Form Filler, Guardrails, Memory/State, Evaluation, Billing).
   - Line 17-18: R3 requiring implementation of real-time and post-session agents (GuardrailAgent, MemoryGoalAgent, BillingAgent, RagSearchAgent, EvaluationAgent) across Modes 1-4.
   - Line 23-24: Acceptance criteria stating zero blocking calls on main WebSockets audio thread (`@Async` heavy LLM/vector ops).

2. **Existing Backend Java Code Architecture** (`backend/src/main/java/com/reForm/backend/ai/`):
   - `VoiceSyncWSHandler.java`: Manages WSS connection lifecycle, active sessions map (`activeSessions`), session tracking in Redis via `SessionTracker`, and forwards audio/text payloads to adapter strategies.
   - `GeminiLiveVoiceAdapter.java`: Mode 4 WSS proxy establishing outbound WSS tunnel to `wss://generativelanguage.googleapis.com`. Handles raw PCM audio bytes (24kHz/16kHz), setup frames, native barge-in (`INTERRUPTED`), and tool calls (`toolCall`).
   - `CascadedVoiceAdapter.java`: Mode 3 WSS adapter routing audio to Deepgram Nova-3 STT, text to Gemini 3.6 Flash LLM, and output text to Cartesia Sonic TTS.
   - `SessionContextService.java`: Builds BidiGenerateContentSetup Map for Google Gemini Live, handles persona compiling, and declares 18 function tools (gated by role and document/audio capability).
   - `SessionTracker.java`: Manages Redis Hashes (`session:{userId}`) with a 2-hour TTL lease refreshed on PING.
   - `LayoutAgent.java`: Located in `com.reForm.backend.ai.agent.LayoutAgent`. Annotated `@Component`, `@Async`, `@EventListener` listening to `FormLayoutModificationEvent`.
   - `ToolCallRegistry.java` & 18 `IToolCallHandler` implementations in `ai/tool/handler/` (`EvaluateResponseToolHandler`, `SaveFieldResponseToolHandler`, `SearchUserDocumentToolHandler`, `EndSessionToolHandler`, `ModifyFormLayoutToolHandler`, etc.).

3. **Architecture Documentation Specs** (`backend/knowledge/pth/week3/`):
   - `03_ai_form_filler_multiagent_workflow.md`: Catalogs the 6 specialized agents (Guardrail, Memory/Goal, Billing/VAD, RAG Search, Form Canvas Sync, Evaluation/Analytics).
   - `07_agent_mechanics_and_subagent_spawning.md`: Defines Spring `@Component` agent mechanics and dynamic sub-agent spawning (`SubAgentFactory`, `AsyncTaskExecutor`, `CodeAnalysisSubAgent`, `DocumentOcrSubAgent`, `ScoringSubAgent`).
   - `09_mode3_cascaded_voice_architecture_and_design.md`: Explains Mode 3 Cascaded Voice pipeline (~$0.0176/min) vs. Mode 4 Native Live (~$0.0270/min) and component reuse matrix across Modes 1-4.
   - `18_ai_provider_decoupling_and_4mode_unified_architecture.md`: Specifies provider decoupling with `IAiVoiceAdapter` and unified component reuse matrix across Modes 1-4.

---

## 2. Logic Chain

1. **Observation**: `VoiceSyncWSHandler` handles binary PCM audio frames at ~50 fps (~20ms interval).
   **Inference**: Any blocking REST call, database I/O, or LLM invocation on this thread will cause audio frame buffer overflow and broken audio output in client browsers.
   **Conclusion**: All heavy operations must be decoupled via Spring's `ApplicationEventPublisher` and executed asynchronously on dedicated background thread pools (`@Async`).

2. **Observation**: `LayoutAgent` demonstrates the pattern currently used in the codebase: a Spring `@Component` receiving an immutable event record (`FormLayoutModificationEvent`) annotated with `@Async @EventListener`.
   **Inference**: The remaining Form Filler and Governance agents (`GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `RagSearchAgent`, `EvaluationAgent`) can follow this exact same design pattern.
   **Conclusion**: Creating dedicated agent beans in `com.reForm.backend.ai.agent` using `@Component`, `@Async`, and `@EventListener` fulfills R1 and R3 requirements without introducing new architectural paradigms.

3. **Observation**: `SessionContextService` already registers 18 function tool declarations for Gemini Live and Mode 2/3 LLMs, and `ToolCallRegistry` routes execution to 18 `IToolCallHandler` beans.
   **Inference**: The tool calling handlers can easily delegate heavy processing (like document searching, response saving, and evaluation) to `RagSearchAgent`, `MemoryGoalAgent`, and `EvaluationAgent`.
   **Conclusion**: The existing tool handler framework aligns directly with the multi-agent design.

---

## 3. Caveats

- **External Services**: Real-time integration tests with live Google Gemini Live WSS, Deepgram STT, and Cartesia TTS require valid API keys configured in `application.yml`.
- **Database Schema**: `pgvector` extension must be enabled on PostgreSQL for vector similarity searches in `GuardrailAgent` and `RagSearchAgent`.

---

## 4. Conclusion

1. **Form Filler Agent Suite Architecture**:
   - `GuardrailAgent`: Spring `@Component` for real-time safety, input/output validation, and prompt injection detection using `text-embedding-004` and `pgvector` (<5ms latency).
   - `MemoryGoalAgent`: Spring `@Service` managing conversational goals and state in Redis (`opsForHash()`).
   - `BillingAgent`: Spring `@Component` monitoring VAD silence frames (>45s trigger) and managing token/audio usage billing.
   - `RagSearchAgent`: Spring `@Service` conducting vector semantic search over document embeddings.
   - `EvaluationAgent`: Spring `@Service` executing post-session candidate scoring and report generation via `@Async @EventListener` on socket close.

2. **Thread & Event Model**: Zero blocking calls on the WebSocket audio thread; all agent actions run on Spring `@Async` worker thread pools (`TaskExecutor`).

3. **Complete Survey Report**: Available at `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_3/survey_report_3.md`.

---

## 5. Verification Method

To verify the findings and findings report:
1. Inspect the survey report: `view_file /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_3/survey_report_3.md`
2. Inspect existing agent implementation pattern: `view_file /Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java`
3. Inspect WebSocket handler and adapters:
   - `view_file /Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/websocket/VoiceSyncWSHandler.java`
   - `view_file /Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/service/GeminiLiveVoiceAdapter.java`
