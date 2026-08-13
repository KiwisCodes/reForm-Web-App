# 13: Filler & Voice Pipeline Deep Dives

**Author**: Lead Technical Writer & Architect  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.ai`)  
**Target Document**: `backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md`  
**Date**: 2026-08-13  
**Version**: 2.0.0-RELEASE  

---

### 4.2 Form Filler Pipeline Agents & Tool Handlers

#### 11. `FormVersioningAgent`
1. **Name & Role**: `FormVersioningAgent` manages semantic versioning (v1.0.0 $\rightarrow$ v1.1.0), schema diffing, and in-flight respondent session migration when published forms are edited.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.FormPublishedEvent` or direct `publishForm` tool call.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FormPublishedEvent`
     ```java
     public record FormPublishedEvent(
         UUID formId,
         int previousVersion,
         int newVersion,
         List<AbstractBlock> oldSchema,
         List<AbstractBlock> newSchema,
         UUID editorUserId
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.FormVersionMigrationResult`
     ```java
     public record FormVersionMigrationResult(
         UUID formId,
         String semanticVersion,
         JsonNode schemaDiffJson,
         int activeSessionsMigrated,
         boolean breakingChangesDetected
     ) {}
     ```
4. **Design Pattern**: **Command Pattern** (encapsulating schema migrations as reversible commands) combined with **Observer Pattern** (notifying session agents on breaking changes). *WHY*: Enables atomic rollback of form schema deployments if live sessions report incompatibility.
5. **Technology Choice**: **java-diff-utils + Jackson JsonPatch (RFC 6902)** over database table cloning. *WHY*: RFC 6902 patches provide low-overhead PostgreSQL JSONB migrations and exact breaking change detection.
6. **SOLID + KISS Justification**:
   - *SRP*: Manages only form versioning history, schema diffing, and session migration compatibility.
   - *OCP*: Migration handlers for new block types register via `IBlockMigrationStrategy`.
   - *LSP*: All diff handlers adhere to `ISchemaDiffEngine`.
   - *ISP*: Clean separation between `IVersionPublisher` and `ISessionMigrationHandler`.
   - *DIP*: Injects abstract `IFormVersionRepository` for database persistence.
   - *KISS*: Emits standard RFC 6902 JSON Patch arrays (`[{"op": "add", "path": "/blocks/3", ...}]`).
7. **Open Questions**: If a published form deletion of a required block invalidates an ongoing 30-minute filler session, should the session auto-adapt or request user re-validation?


---

#### 12. `ThemeAgent`
1. **Name & Role**: `ThemeAgent` synthesizes custom CSS/Tailwind design systems, color palettes, and typography specs that strictly comply with WCAG 2.1 AA/AAA accessibility standards from natural language branding prompts.
2. **Trigger Mechanism**: Spring Event Bus consuming `com.reForm.backend.ai.event.FormThemeGenerationEvent` or builder tool call `generateFormTheme`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FormThemeGenerationEvent`
     ```java
     public record FormThemeGenerationEvent(
         UUID formId,
         String brandingPrompt,
         String baseColorHex,
         WCAGLevel targetCompliance
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.FormThemeResult`
     ```java
     public record FormThemeResult(
         UUID formId,
         Map<String, String> cssTokens,
         String tailwindConfigJson,
         boolean wcagCompliant,
         double contrastRatioScore
     ) {}
     ```
4. **Design Pattern**: **Builder Pattern** (step-by-step construction of complex UI themes) combined with **Template Method** (palette generation $\rightarrow$ token mapping $\rightarrow$ contrast audit). *WHY*: Theme generation requires strict sequence where contrast auditing must precede CSS output.
5. **Technology Choice**: **Tailwind CSS v4 Engine + Color4j + Gemini 3.6 Flash** over unconstrained raw CSS generation. *WHY*: Tailwind tokens guarantee consistent frontend rendering while Color4j mathematically verifies WCAG contrast ratios.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated entirely to color mathematics, design token generation, and contrast verification.
   - *OCP*: Pluggable theme renderers support new design systems (Shadcn UI, Material 3).
   - *LSP*: All theme generators produce valid `FormThemeResult` payloads.
   - *ISP*: Decouples `IThemeSynthesizer` from `IContrastAuditor`.
   - *DIP*: Depends on `IContrastAuditor` abstraction.
   - *KISS*: Emits flat key-value CSS variable maps directly injectable into DOM element styles.
7. **Open Questions**: How should high-frequency color picker adjustments over WebSockets be throttled to prevent LLM rate limit exhaustion?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manual prompt creation and raw string parsing to generate Tailwind v4 tokens and WCAG color maps.
   - **Spring AI Solution**: `ChatClient.prompt().user(brandingPrompt).entity(ThemeTokensRecord.class)`.
   - **Benefit vs. Current Design**: Guarantees CSS variable key-value JSON formatting via `BeanOutputConverter` without raw string regex parsing.
   - **Migration Impact**: LOW — Mode 2 REST endpoint.
   - **Recommendation**: **USE** — Clean structured output for UI theme tokens.


---

#### 13. `TranslationAgent`
1. **Name & Role**: `TranslationAgent` translates form question titles, descriptions, option labels, and error messages into 50+ languages while preserving localized validation rules.
2. **Trigger Mechanism**: HTTP POST `/api/v1/forms/{id}/translate` or Spring `com.reForm.backend.ai.event.FormTranslationEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FormTranslationEvent`
     ```java
     public record FormTranslationEvent(
         UUID formId,
         String targetLanguageCode,
         List<String> targetLanguages,
         boolean preservePlaceholders
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.FormTranslationResult`
     ```java
     public record FormTranslationResult(
         UUID formId,
         String languageCode,
         Map<UUID, TranslatedBlockText> blockTranslations,
         int totalTokensUsed
     ) {}
     ```
4. **Design Pattern**: **Flyweight Pattern** (reusing translation cache for static UI labels like "Submit") combined with **Decorator Pattern** (wrapping raw translations with locale formatting). *WHY*: Eliminates redundant LLM translation costs for common terms across tenants.
5. **Technology Choice**: **Gemini 3.6 Flash + Redis String Cache (`translation:cache:{hash}`)** over Google Cloud Translation API. *WHY*: Gemini contextualizes translations based on form domain (medical vs legal), while Redis caches identical strings.
6. **SOLID + KISS Justification**:
   - *SRP*: Handles only multi-language textual conversion and locale metadata enrichment.
   - *OCP*: Supports custom terminology glossaries per workspace via pluggable context strategies.
   - *LSP*: Implements `ITranslationEngine` contract interchangeable with fallback translation engines.
   - *ISP*: Exposes clean `translate()` method.
   - *DIP*: High-level service depends on `ITranslationCache` and `ILLMProvider` abstractions.
   - *KISS*: Operates on a simple flat dictionary map of block UUID to translated strings.
7. **Open Questions**: Should localized validation rules (e.g., US Zip Code vs UK Postcode regex) automatically swap when display language changes?
8. **Spring AI Integration Analysis**:
   - **Problem (Without Spring AI)**: Manual dictionary JSON construction and response parsing for 50+ target languages.
   - **Spring AI Solution**: `ChatClient.prompt().system(translationPrompt).entity(new ParameterizedTypeReference<Map<UUID, String>>() {})`.
   - **Benefit vs. Current Design**: `MapOutputConverter` automatically deserializes translated text dictionary maps bound to block UUIDs.
   - **Migration Impact**: LOW — Wraps behind `ITranslationEngine`.
   - **Recommendation**: **USE** — Simplifies dictionary map output mapping.


---

### Group C: Form Filler Pipeline (P0–P2)
*Agents that handle active conversational filling and real-time validation.*

#### 14. `SaveFieldResponseToolHandler`
1. **Name & Role**: `SaveFieldResponseToolHandler` immediately persists validated respondent field answers to PostgreSQL to prevent data loss during long voice calls.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `saveFieldResponse` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fieldId`, `value`, `confidence`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "SAVED", "savedValue": "Java 21"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Repository Pattern**. *WHY*: Guarantees transactional persistence per answered question turn.
5. **Technology Choice**: **PostgreSQL `FieldResponse` JPA Entity + Spring Data JPA** over bulk post-session inserts. *WHY*: Real-time persistence prevents response loss if the user's connection drops mid-call.
6. **SOLID + KISS Justification**:
   - *SRP*: Handles solely the persistence of field response values.
   - *OCP*: Extensible via Spring JPA entity listeners (`@PrePersist`, `@PostPersist`).
   - *LSP*: Complies strictly with `IToolCallHandler`.
   - *ISP*: Exposes only handler strategy methods.
   - *DIP*: Injects `FieldResponseRepository` interface.
   - *KISS*: Saves entity directly via repository `save()` call.
7. **Open Questions**: If a respondent revisits and updates a previously answered field, should past values be versioned or overwritten?


---

#### 15. `LookupFormProgressToolHandler`
1. **Name & Role**: `LookupFormProgressToolHandler` queries and calculates current form completion statistics (answered fields, remaining fields, completion percentage).
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `lookupFormProgress` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (No mandatory args).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"totalFields": 10, "answeredFields": 5, "percentComplete": 50`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`). *WHY*: Encapsulates session progress calculation logic.
5. **Technology Choice**: **Redis `opsForHash()` Session State Query** over heavy database SQL queries. *WHY*: Sub-millisecond RAM reads for active session answer counts.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated exclusively to progress percentage calculations.
   - *OCP*: Extensible to calculate section-level progress without breaking global progress contract.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Minimal method dependencies.
   - *DIP*: Depends on `SessionTracker` abstraction.
   - *KISS*: Divides answered count by total count and multiplies by 100.
7. **Open Questions**: How should skipped non-required fields factor into completion percentage calculations?


---

#### 16. `SkipQuestionToolHandler`
1. **Name & Role**: `SkipQuestionToolHandler` marks non-applicable form fields as skipped, recording skip reasons and advancing the active question pointer.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `skipQuestion` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fieldId`, `reason`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "SKIPPED", "fieldId": "field-1", "reason": "USER_DECLINED"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`). *WHY*: Encapsulates field skip execution logic.
5. **Technology Choice**: **PostgreSQL `FieldResponse` Status Flag (`SKIPPED`)** over deletion. *WHY*: Preserves complete audit trails of why questions were omitted.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated purely to handling question skip requests.
   - *OCP*: Skip reason metadata types extend cleanly without code changes.
   - *LSP*: Implements `IToolCallHandler` contract.
   - *ISP*: Minimal interface contract.
   - *DIP*: Depends on `FieldResponseRepository` abstraction.
   - *KISS*: Saves field response record marked with `status = SKIPPED`.
7. **Open Questions**: Should skipping a required field automatically prompt the AI interviewer to politely ask for confirmation before skipping?

---

### 4.3 File & Media Processing Pipeline Agents & Tool Handlers

#### 17. `EvaluateResponseToolHandler`
1. **Name & Role**: `EvaluateResponseToolHandler` evaluates and scores respondent answers against domain rubrics during active filler sessions.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `evaluateResponse` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fieldId`, `score`, `feedback`, `tags`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "EVALUATED", "score": 95.0`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`). *WHY*: Provides isolated handler execution for answer scoring calls.
5. **Technology Choice**: **Jackson JsonNode + PostgreSQL `FieldResponse` updates** over in-memory scoring maps. *WHY*: Persists evaluation scores immediately to audit tables.
6. **SOLID + KISS Justification**:
   - *SRP*: Dedicated strictly to recording answer evaluation scores and feedback tags.
   - *OCP*: Extensible to trigger low-score alert events without modifying tool code.
   - *LSP*: Strictly implements `IToolCallHandler`.
   - *ISP*: Minimal strategy interface dependency.
   - *DIP*: Injects `FieldResponseRepository` abstraction.
   - *KISS*: Updates score fields on existing field response record.
7. **Open Questions**: Should low evaluation scores automatically trigger prompt injections encouraging the interviewer to ask follow-up questions?


---

#### 18. `FlagForHumanReviewToolHandler`
1. **Name & Role**: `FlagForHumanReviewToolHandler` flags ambiguous, suspicious, or critical respondent answers for manual human review.
2. **Trigger Mechanism**: `ToolCallRegistry.executeTool()` handling Gemini `flagForHumanReview` function call frame.
3. **Input / Output**:
   - **Input**: `WebSocketSession clientSession`, Jackson `JsonNode functionCall`, `String callId`. (Extracted args: `fieldId`, `priority`, `reason`).
   - **Output**: `Map<String, Object>` Gemini `toolResponse` payload (`"status": "FLAGGED", "priority": "HIGH"`).
4. **Design Pattern**: **Strategy Pattern** (`IToolCallHandler`) + **Observer Pattern**. *WHY*: Encapsulates human-in-the-loop flagging logic and broadcasts notifications.
5. **Technology Choice**: **PostgreSQL Audit Entity + Spring Event Bus (`SendNotificationToolHandler`)** over simple log files. *WHY*: Guarantees persistent review queues for administrative dashboards.
6. **SOLID + KISS Justification**:
   - *SRP*: Sole responsibility of marking responses for administrative review.
   - *OCP*: Review priorities (`LOW`, `HIGH`, `CRITICAL`) extend cleanly via ENUMs.
   - *LSP*: Fulfills `IToolCallHandler` contract.
   - *ISP*: Implements concise tool handler methods.
   - *DIP*: Depends on `ReviewQueueRepository` interface.
   - *KISS*: Inserts a single flag record into the human review queue table.
7. **Open Questions**: Should flagging a response for human review pause the active conversational session or allow it to proceed?


---

#### 19. `ValidationAgent`
1. **Name & Role**: `ValidationAgent` executes real-time field validation, format checking, regex matching, and external API verification (address lookup, tax ID check) on user responses before updating session state.
2. **Trigger Mechanism**: Tool call `saveFieldResponse` or Spring `com.reForm.backend.ai.event.FieldValidationRequestEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FieldValidationRequestEvent`
     ```java
     public record FieldValidationRequestEvent(
         UUID sessionId,
         UUID blockId,
         String blockType,
         Object submittedValue,
         String validationRulesJson
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.FieldValidationResult`
     ```java
     public record FieldValidationResult(
         UUID sessionId,
         UUID blockId,
         boolean isValid,
         String errorMessage,
         Object normalizedValue,
         Map<String, Object> enrichedMetadata
     ) {}
     ```
4. **Design Pattern**: **Strategy Pattern** (field-specific validators) combined with **Decorator Pattern** (chaining static regex validators with external API validators). *WHY*: Combines lightweight static checks with heavy external HTTP verification cleanly.
5. **Technology Choice**: **Hibernate Validator + Resilience4j CircuitBreaker** over inline regex strings. *WHY*: CircuitBreakers prevent third-party external API outages from blocking live conversational filler sessions.
6. **SOLID + KISS Justification**:
   - *SRP*: Solitary responsibility of validating and normalizing submitted field values.
   - *OCP*: Custom domain validators (IBAN check, NPI check) plug in as new `@Component` implementations of `IFieldValidator`.
   - *LSP*: Substitutable `IFieldValidator` contracts ensure deterministic execution.
   - *ISP*: Exposes segregated `IFieldValidator` and `IExternalValidationProvider` interfaces.
   - *DIP*: Injects set of `IFieldValidator` beans via Spring IoC.
   - *KISS*: Short-circuits on first failing rule to minimize unnecessary external API calls.
7. **Open Questions**: How should asynchronous external validation (requiring 3 seconds) interact with low-latency Mode 4 conversational audio flow?


---

#### 20. `AdaptiveBranchingAgent`
1. **Name & Role**: `AdaptiveBranchingAgent` evaluates complex conditional logic rules, respondent sentiment, and previous answers in real time to dynamically update the active interview question path.
2. **Trigger Mechanism**: WebSocket frame `ANSWER_SUBMITTED` or Spring `com.reForm.backend.ai.event.FieldAnswerSubmittedEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FieldAnswerSubmittedEvent`
     ```java
     public record FieldAnswerSubmittedEvent(
         UUID sessionId,
         UUID formId,
         UUID blockId,
         Object answerValue,
         Map<UUID, Object> currentSessionAnswers
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.AdaptiveBranchingResult`
     ```java
     public record AdaptiveBranchingResult(
         UUID sessionId,
         List<UUID> blocksToSkip,
         List<AbstractBlock> dynamicBlocksToInsert,
         UUID nextBlockId,
         boolean branchCompleted
     ) {}
     ```
4. **Design Pattern**: **Interpreter Pattern** (evaluating boolean expression trees against session state) combined with **Directed Acyclic Graph (DAG) Traverser**. *WHY*: Questions represent DAG nodes where conditional expressions dictate edge traversal.
5. **Technology Choice**: **Spring Expression Language (SpEL) + JGraphT** over hardcoded nested `if-else` blocks. *WHY*: SpEL provides safe, high-performance in-memory evaluation of complex logic predicates (`#answers['income'] > 100000`).
6. **SOLID + KISS Justification**:
   - *SRP*: Focuses solely on evaluating question graph traversal and conditional branching rules.
   - *OCP*: New expression operators or context providers can be added without modifying the graph engine.
   - *LSP*: Implements `IBranchingEvaluator` consistently.
   - *ISP*: Exposes `evaluateNextStep()` without exposing internal graph structures.
   - *DIP*: Depends on abstract `ISessionAnswerProvider` to query state.
   - *KISS*: Evaluates expressions against a flat `Map<UUID, Object>` answer map.
7. **Open Questions**: How can we detect and prevent infinite loop cycles when form creators configure conflicting bi-directional conditional branching logic?


---

#### 21. `VoiceSpeechAgent`
1. **Name & Role**: `VoiceSpeechAgent` manages real-time bidirectional PCM audio streams, Voice Activity Detection (VAD), user barge-in suppression, and ambient noise filtering for low-latency Mode 4 sessions.
2. **Trigger Mechanism**: Inbound WebSocket audio binary frames (`wss://.../ws/v1/voice`) or Gemini Live Socket 2 frames.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.IncomingAudioFrame`
     ```java
     public record IncomingAudioFrame(
         UUID sessionId,
         byte[] pcmAudioData,
         int sampleRate,
         boolean isFinalFrame,
         long sequenceNumber
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.ProcessedAudioFrame`
     ```java
     public record ProcessedAudioFrame(
         UUID sessionId,
         byte[] cleanedPcmData,
         boolean speechDetected,
         boolean userBargeIn,
         double decibelLevel
     ) {}
     ```
4. **Design Pattern**: **Pipeline Architecture (Pipes & Filters)** combined with **Observer Pattern** (broadcasting VAD state changes to WebSocket handlers). *WHY*: Allows sequential audio transformations (decibel check $\rightarrow$ noise filter $\rightarrow$ VAD classification) with zero buffer copying.
5. **Technology Choice**: **Netty ByteBuf + Silero VAD + WebRTC AudioProcessing native bindings** over standard Java byte arrays. *WHY*: Off-heap `ByteBuf` allocations eliminate JVM GC pauses during high-frequency 20ms audio frame processing.
6. **SOLID + KISS Justification**:
   - *SRP*: Responsible strictly for raw audio signal processing, noise reduction, and VAD framing.
   - *OCP*: Audio filters (echo cancellation, pitch modulation) insert into Netty pipeline without altering stream logic.
   - *LSP*: All audio processors implement `IAudioFilter`.
   - *ISP*: Decoupled into `IVadDetector` and `IAudioStreamProcessor` interfaces.
   - *DIP*: Relies on `IAudioChannelHandler` abstractions.
   - *KISS*: Processes fixed 20ms PCM 16kHz audio chunks with direct ring-buffer memory.
7. **Open Questions**: What is the optimal decibel threshold and frame window to distinguish background room noise from intentional user voice barge-in?


---

#### 22. `ScoringSubAgent`
1. **Name & Role**: `ScoringSubAgent` is a worker sub-agent spawned during multi-criteria evaluations to score specific domain competencies (e.g. Technical Knowledge, Communication, Leadership) in parallel.
2. **Trigger Mechanism**: `SubAgentFactory.spawnScoringSubAgent()` invoked by `EvaluationAgent` or post-turn event.
3. **Input / Output**:
   - **Input**: Candidate turn transcript, target rubric criterion, scoring weights.
   - **Output**: Criterion score (0-100), rationale snippet, confidence score.
4. **Design Pattern**: **Factory Pattern** (`SubAgentFactory`) + **Worker Thread / Task Queue Pattern**. *WHY*: Offloads computationally heavy grading tasks to background thread pools.
5. **Technology Choice**: **Spring `AsyncTaskExecutor` + Gemini 3.6 Flash (Mode 2)** over monolithic single-prompt grading. *WHY*: Parallel sub-agents scoring isolated rubrics yield higher evaluation accuracy and faster throughput.
6. **SOLID + KISS Justification**:
   - *SRP*: Evaluates exactly one rubric criterion per sub-agent instance.
   - *OCP*: New evaluation rubrics register without altering sub-agent spawning infrastructure.
   - *LSP*: Implements `ISubAgentWorker` contract.
   - *ISP*: Exposes single `executeTask()` method.
   - *DIP*: Depends on `IScoringRubric` abstraction.
   - *KISS*: Returns a simple numerical score and structured explanation text.
7. **Open Questions**: Should scoring sub-agents incorporate past submission benchmark baselines when evaluating candidate responses?
