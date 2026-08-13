# 15: UI, Security & Background Pipeline Deep Dives

**Author**: Lead Technical Writer & Architect  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.ai`)  
**Target Document**: `backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md`  
**Date**: 2026-08-13  
**Version**: 2.0.0-RELEASE  

---

### Group F: Real-Time UI & Notification Tools (P1–P2)
*Agents interacting with client browsers and external notification channels.*

#### 34. `RenderDynamicUIToolHandler`
1. **Name & Role**: `RenderDynamicUIToolHandler` pushes interactive UI widget frames (buttons, star ratings, date pickers) to client browser screens during conversational filling.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `renderDynamicUI` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `componentType`, `options`, `prompt`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "UI_RENDERED"`). Client frame pushed: `{"type": "DYNAMIC_UI_REQUESTED", ...}`.
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Client Push Pattern**. *WHY*: Allows AI persona to dynamically request rich interactive frontend component rendering over WebSockets.
5. **Technology Choice**: **Spring WebSocket Text Frame (`WebSocketSessionUtils.wrapSafeSession`)** over static forms. *WHY*: Delivers real-time interactive widget render signals directly into React client state.
6. **SOLID + KISS Justification**:
   - *SRP*: Single focus of pushing dynamic UI rendering signals to client sessions.
   - *OCP*: New UI widget types (`BUTTONS`, `RATING_STARS`, `DATE_PICKER`, `SLIDER`) extend via ENUMs.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Concise interface dependencies.
   - *DIP*: Depends on `WebSocketSession` abstraction.
   - *KISS*: Sends JSON text frame containing widget component type and options string.
7. **Open Questions**: Should user interaction with a dynamic UI widget (e.g. clicking a star rating) emit a synthetic text frame back to Gemini as spoken dialogue input?


---

#### 35. `SendNotificationToolHandler`
1. **Name & Role**: `SendNotificationToolHandler` triggers real-time alerts and notifications across Dashboard, Email, Slack, or Webhook channels.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `sendNotification` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `channel`, `priority`, `title`, `body`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "NOTIFICATION_SENT"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Observer / Notification Pattern**. *WHY*: Decouples notification trigger logic from concrete channel notification handlers.
5. **Technology Choice**: **Spring `ApplicationEventPublisher` + Notification Dispatcher** over synchronous HTTP calls. *WHY*: Asynchronous event publication ensures external notification delivery delays (Slack HTTP API) never block conversational voice streams.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated purely to dispatching notification requests.
   - *OCP*: New notification channels (SMS, Push Notification) register as event listeners without editing tool code.
   - *LSP*: Satisfies `IToolCallHandler` contract.
   - *ISP*: Minimal interface dependency.
   - *DIP*: Depends on `ApplicationEventPublisher` interface.
   - *KISS*: Publishes notification event containing channel, priority, title, and body.
7. **Open Questions**: How should notification rate limits be enforced if an AI persona attempts to send 20 urgent alerts within 1 minute?


---

### Group G: Security, Safety & Compliance Pipeline (P2)
*Agents enforcing system safety, compliance, and guarding against prompt injections.*

#### 36. `GuardrailAgent`
1. **Name & Role**: `GuardrailAgent` intercepts real-time voice and text transcripts, executing sub-2ms embedding similarity checks against toxic/jailbreak prompt vectors before LLM context ingestion.
2. **Trigger Mechanism**: Synchronous invocation per turn in `GeminiLiveVoiceAdapter` / `VoiceSyncWSHandler`.
3. **Input / Output**:
   - **Input**: Transcript text chunk string.
   - **Output**: `GuardrailResult` (`boolean safe`, `float similarityScore`, `String category`).
4. **Design Pattern**: **Chain of Responsibility / Strategy Pattern**. *WHY*: Evaluates sequential security checks (regex blocklist $\rightarrow$ embedding similarity check $\rightarrow$ policy validator).
5. **Technology Choice**: **Google `text-embedding-004` (768d) + PostgreSQL `pgvector` Cosine Similarity Query (`<=>`)** over external moderation APIs. *WHY*: Sub-2ms in-database vector cosine checks eliminate external network latency during live voice sessions.
6. **SOLID + KISS Justification**:
   - *SRP*: Exclusive focus on safety, prompt injection filtering, and content policy validation.
   - *OCP*: New threat vector categories append to vector table without code edits.
   - *LSP*: Fulfills `IGuardrailChecker` contract.
   - *ISP*: Minimal interface dependency.
   - *DIP*: Depends on `IVectorGuardrailRepository` abstraction.
   - *KISS*: Reuses existing `pgvector` database infrastructure.
7. **Open Questions**: What similarity threshold (e.g. 0.85 cosine score) yields the optimal balance between false positive safety triggers and actual injection detection?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manual `pgvector` similarity query execution for toxic/jailbreak prompt vectors.
   - **Spring AI Solution**: Implement Spring AI `SafeGuardAdvisor` or query `PgVectorStore` for threat vector similarity thresholds (<2ms).
   - **Benefit vs. Current Design**: Integrates content moderation seamlessly into `ChatClient` advisor chains (`.advisors(new SafeGuardAdvisor(...))`).
   - **Migration Impact**: LOW — Extends existing guardrail strategy.
   - **Recommendation**: **USE** — Provides declarative safety advisor wrapping around LLM calls.


---

#### 37. `SecurityAuditAgent`
1. **Name & Role**: `SecurityAuditAgent` monitors active sessions for anomalous behavior, prompt injection attacks, unauthorized access attempts, PII leakage, and rate limit violations, writing immutable audit trails.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.SecurityAuditEvent` or interceptor filter triggers (`JwtHandshakeInterceptor`).
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.SecurityAuditEvent`
     ```java
     public record SecurityAuditEvent(
         UUID sessionId,
         UUID userId,
         String clientIp,
         String requestPath,
         AuditSeverity severity,
         String payloadSnippet,
         String detectedThreat
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.SecurityAuditResult`
     ```java
     public record SecurityAuditResult(
         UUID auditId,
         ActionTaken action,
         boolean sessionBlocked,
         String alertNotificationId,
         Instant loggedTimestamp
     ) {}
     ```
4. **Design Pattern**: **Interceptor Pattern** (intercepting active requests and events) combined with **Observer Pattern** (broadcasting security alerts). *WHY*: Guarantees zero modification to business logic code paths during security audit monitoring.
5. **Technology Choice**: **Spring Security Filter Chain + Logback MDC + OpenSearch Append-Only Audit Index** over database log tables. *WHY*: External log aggregators handle high-volume audit event streams without impacting transactional DB write performance.
6. **SOLID + KISS Justification**:
   - *SRP*: Solely dedicated to security inspection, threat flagging, and compliance audit trail generation.
   - *OCP*: Threat detection rules (SQLi patterns, XSS regex, PII detection) register as pluggable `ISecurityRule` beans.
   - *LSP*: All threat detectors return standardized `SecurityAuditResult` records.
   - *ISP*: Clean interface `ISecurityAuditor`.
   - *DIP*: Depends on abstract `IAuditLogRepository` interface.
   - *KISS*: Uses standard W3C log formatting with SLF4J MDC context variables (IP, SessionId, WorkspaceId).
7. **Open Questions**: Should detected PII (e.g. credit card numbers spoken in voice) be masked in RAM before reaching Gemini LLM or intercepted post-transcript generation?


---

### Group H: Background Processing & Post-Session Pipeline (P1–P3)
*Agents that run asynchronously to aggregate data, meter usage, and evaluate sessions.*

#### 38. `EvaluationAgent`
1. **Name & Role**: `EvaluationAgent` analyzes complete session transcripts post-call, computes overall candidate scores (0-100), generates executive summary reports, and persists `Submission` records.
2. **Trigger Mechanism**: Spring Event Bus `@EventListener` consuming `com.reForm.backend.ai.event.SessionEndedEvent`.
3. **Input / Output**:
   - **Input**: Complete timestamped session transcript array, form scoring rubric configuration.
   - **Output**: `Submission` entity persisted in PostgreSQL containing candidate score, summary report, strengths, and red flags.
4. **Design Pattern**: **Observer Pattern** (`@Async @EventListener`) + **Factory Pattern**. *WHY*: Heavy multi-criteria summary generation runs asynchronously post-call, maintaining zero voice call latency impact.
5. **Technology Choice**: **Gemini 3.6 Flash (Mode 2 REST) + Spring `@Async` + PostgreSQL** over real-time call evaluation. *WHY*: Gemini 3.6 Flash synthesizes full transcripts into structured PDF/Markdown summaries rapidly and cost-effectively post-call.
6. **SOLID + KISS Justification**:
   - *SRP*: Sole responsibility of post-session response analysis and evaluation summary generation.
   - *OCP*: Extensible to emit candidate summary emails or webhook notifications post-evaluation.
   - *LSP*: Fulfills `ISessionEvaluator` contract.
   - *ISP*: Exposes clean `evaluateSession()` interface.
   - *DIP*: Depends on `SubmissionRepository` interface.
   - *KISS*: Writes evaluated score and summary Markdown text directly to database `Submission` row.
7. **Open Questions**: Should candidate score calculations support weighted custom formula expressions defined by form creators?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manual transcript concatenation, prompt formatting, and raw JSON parsing of candidate evaluation scores.
   - **Spring AI Solution**: `ChatClient.prompt().system(evalRubricPrompt).user(transcript).entity(CandidateEvaluationReport.class)`.
   - **Benefit vs. Current Design**: Automatically deserializes evaluation scores (0-100), feedback tags, and summary points into strongly typed Java Records with zero custom JSON parsing.
   - **Migration Impact**: LOW — Async post-session worker bean.
   - **Recommendation**: **USE** — Excellent fit for structured post-session evaluation reporting.


---

#### 39. `TokenMeteringAgent`
1. **Name & Role**: `TokenMeteringAgent` tracks real-time LLM token consumption, speech synthesis audio duration (seconds), and vector storage usage per workspace, enforcing quota limits and updating billing balances atomically.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.BillingUsageEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.BillingUsageEvent`
     ```java
     public record BillingUsageEvent(
         UUID workspaceId,
         UUID sessionId,
         String meterType,
         long unitsUsed
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.TokenMeteringResult`
     ```java
     public record TokenMeteringResult(
         UUID workspaceId,
         long totalBalanceRemaining,
         boolean quotaExceeded,
         boolean warningThresholdReached,
         String currentTier
     ) {}
     ```
4. **Design Pattern**: **Observer Pattern** (listening to resource consumption events across the system) combined with **Token Bucket Pattern** (managing workspace quota buckets). *WHY*: Decouples metering logic cleanly from core application tool handlers.
5. **Technology Choice**: **Redis Lua Scripts (Atomic Decrby & Bucket Check) + Redisson Distributed Locks** over relational DB updates. *WHY*: High-frequency token events during voice streaming require sub-millisecond atomic decrements in RAM.
6. **SOLID + KISS Justification**:
   - *SRP*: Sole responsibility of tracking resource consumption and enforcing workspace quota balances.
   - *OCP*: New resource meters (OCR page count, video processing minutes) are added by registering new `MeterType` ENUM values.
   - *LSP*: Guarantees deterministic atomic quota calculation across all meter types.
   - *ISP*: Exposes minimal `IMeteringService` interface with `recordUsage()` and `checkQuota()`.
   - *DIP*: Injects abstract `IQuotaCacheRepository` interface.
   - *KISS*: Uses single Redis Lua script execution per usage event to eliminate race conditions.
7. **Open Questions**: When a workspace exhausts its credit quota mid-interview, should the agent gracefully terminate the session with a polite voice message or allow a small buffer overage?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manually tracking token usage by parsing raw API response metadata.
   - **Spring AI Solution**: Utilize Spring AI's native **Micrometer Observation API** integration (`gen_ai.client.token.usage` metrics).
   - **Benefit vs. Current Design**: Automatically records prompt, completion, and total token usage per LLM call via Spring Boot Actuator/Micrometer without manual metadata parsing.
   - **Migration Impact**: LOW — Binds Micrometer metrics listener to `TokenMeteringAgent` event bus.
   - **Recommendation**: **USE** — Automated token observability and billing metric emission.


---

#### 40. `BillingAgent`
1. **Name & Role**: `BillingAgent` monitors active voice stream silence (VAD signals), tracks open-mic duration, and protects platform tenants from runaway LLM token costs.
2. **Trigger Mechanism**: Continuous frontend VAD socket frame signals and Spring `@Scheduled` heartbeat timer.
3. **Input / Output**:
   - **Input**: VAD audio state signals, session start timestamp, active user balance.
   - **Output**: Warning prompt injection ("Are you still there?"), stream pause signal, or connection termination.
4. **Design Pattern**: **Observer / Timer Pattern**. *WHY*: Monitors session duration and audio silence thresholds continuously in the background.
5. **Technology Choice**: **Spring `@Scheduled` + Redis TTL + VAD Socket Signals** over blocking threads. *WHY*: Non-blocking scheduled checks inspect thousands of concurrent sessions with minimal CPU overhead.
6. **SOLID + KISS Justification**:
   - *SRP*: Focuses exclusively on voice stream metering, silence detection, and financial billing limits.
   - *OCP*: Billing thresholds (e.g. 45s silence warning, 120s auto-close) configure via application properties.
   - *LSP*: Fulfills `IBillingMonitor` contract.
   - *ISP*: Minimal interface dependency.
   - *DIP*: Depends on `SessionTracker` and `TokenMeteringAgent` abstractions.
   - *KISS*: Pauses socket stream if silence timer exceeds configured threshold.
7. **Open Questions**: Should silence warnings be spoken verbally by the AI voice persona or pushed as silent browser UI notifications first?

---

### 4.5 Session Lifecycle Pipeline Agents & Tool Handlers

#### 41. `AnalyticsAggregationAgent`
1. **Name & Role**: `AnalyticsAggregationAgent` asynchronously aggregates form completion metrics, block drop-off rates, average turn durations, and response distributions across workspace forms into Redis/PostgreSQL OLAP caches.
2. **Trigger Mechanism**: Scheduled Cron Trigger (`0 */5 * * * *` - every 5 mins) or Spring `com.reForm.backend.ai.event.SessionEndedEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.AnalyticsAggregationRequest`
     ```java
     public record AnalyticsAggregationRequest(
         UUID workspaceId,
         UUID formId,
         Instant startTime,
         Instant endTime
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.AnalyticsAggregationResult`
     ```java
     public record AnalyticsAggregationResult(
         UUID formId,
         long totalSessions,
         double completionRate,
         double avgDurationSeconds,
         Map<UUID, Double> blockDropoffRates,
         Map<String, Long> sentimentBreakdown
     ) {}
     ```
4. **Design Pattern**: **Aggregator Pattern** combined with **Batch Executor Pattern** (running scheduled map-reduce aggregation over session submissions). *WHY*: Prevents heavy analytical SQL queries from executing on active OLTP transaction threads.
5. **Technology Choice**: **PostgreSQL Window Functions + Redis Hash Caches (`analytics:form:{id}`)** over synchronous per-request aggregation. *WHY*: Background rollups prevent analytics queries from slowing down live transactional DB queries.
6. **SOLID + KISS Justification**:
   - *SRP*: Responsible purely for computing, caching, and serving workspace analytics metrics.
   - *OCP*: New metric calculators (e.g. voice engagement score) implement `IMetricCalculator` and register automatically.
   - *LSP*: All metric calculators return standardized metric data structures.
   - *ISP*: Segregated `IAnalyticsAggregator` interface.
   - *DIP*: Depends on `ISubmissionRepository` analytical abstraction.
   - *KISS*: Pre-aggregates hourly bucket totals into flat Redis Hashes for instant dashboard rendering.
7. **Open Questions**: Should real-time analytics for active live sessions be pushed via WebSockets or polled periodically by the dashboard UI?


---

#### 42. `CodeAnalysisSubAgent`
1. **Name & Role**: `CodeAnalysisSubAgent` is a worker sub-agent spawned during technical coding interviews to analyze, compile, and execute spoken or submitted candidate code inside a sandboxed container.
2. **Trigger Mechanism**: `SubAgentFactory.spawnCodeAnalysisSubAgent()` invoked upon candidate code submission.
3. **Input / Output**:
   - **Input**: Source code string, target programming language (Java, Python, JS), test input parameters.
   - **Output**: Execution output string, compilation status, memory footprint, runtime latency (ms).
4. **Design Pattern**: **Factory Pattern** (`SubAgentFactory`) + **Sandbox / Strategy Pattern**. *WHY*: Isolates untrusted candidate code execution from the main application server JVM.
5. **Technology Choice**: **Docker Java SDK + GraalVM Sandbox Container** over direct JVM `Runtime.getRuntime().exec()`. *WHY*: Container sandboxing enforces CPU/RAM memory limits and blocks unauthorized system calls.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated entirely to sandboxed code compilation, execution, and static analysis.
   - *OCP*: Additional programming language runners register via `ICodeRunnerStrategy`.
   - *LSP*: Satisfies `ISubAgentWorker` contract.
   - *ISP*: Minimal interface dependency (`analyzeCode()`).
   - *DIP*: Depends on `ISandboxContainerEngine` abstraction.
   - *KISS*: Returns a structured JSON result containing `stdout`, `stderr`, and `exitCode`.
7. **Open Questions**: What CPU time limits (e.g., 2000ms) should be enforced to prevent candidate code infinite loops from tying up container resources?


---

#### 43. `ArchivalAgent`
1. **Name & Role**: `ArchivalAgent` executes automated lifecycle policies for completed sessions, soft-deleted forms, and expired media files, archiving cold data into Amazon S3 Glacier and enforcing compliance data retention rules.
2. **Trigger Mechanism**: Scheduled Cron Trigger (`0 0 2 * * *` - daily at 2:00 AM) or Spring `com.reForm.backend.ai.event.DataArchivalTriggerEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.ArchivalJobConfig`
     ```java
     public record ArchivalJobConfig(
         UUID workspaceId,
         int retentionDays,
         boolean compressFiles,
         String targetStorageTier
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.ArchivalJobResult`
     ```java
     public record ArchivalJobResult(
         int sessionsArchived,
         int filesTransferredToGlacier,
         long bytesReclaimed,
         List<UUID> purgedSessionIds,
         boolean statusSuccess
     ) {}
     ```
4. **Design Pattern**: **Command Pattern** (encapsulating archival policies as executable batch jobs) combined with **Strategy Pattern** (storage provider tiering). *WHY*: Decouples execution timing from specific cloud storage providers.
5. **Technology Choice**: **AWS S3 Glacier Client + Spring Batch 5.0** over manual SQL deletion queries. *WHY*: Spring Batch provides chunk-based processing, transaction retry, and failure recovery for millions of database records.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated exclusively to data retention, cold storage archiving, and compliance purging.
   - *OCP*: Archival destinations (AWS S3, GCP Storage, Azure Blob) plug in via `IStorageArchiver` strategies.
   - *LSP*: Substitutable storage target strategies adhere to `IStorageArchiver`.
   - *ISP*: Exposes `IArchivalJobManager` interface.
   - *DIP*: Relies on abstract `ISessionArchivalRepository` and `IStorageArchiver`.
   - *KISS*: Uses standard date-partitioned storage keys (`archives/YYYY/MM/form_{id}.tar.gz`).
7. **Open Questions**: How should GDPR "Right to be Forgotten" hard-deletion requests override scheduled multi-year compliance archival retentions?
