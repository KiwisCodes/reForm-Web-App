# Net-New Agent Discovery Report — reForm Platform Agent Architecture (2026)

**Author**: Net-New Agent Discovery Explorer  
**Date**: 2026-08-05  
**Target File**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/explorer_net_new_discovery/analysis.md`  
**Status**: Complete  

---

## Executive Summary

This report delivers a comprehensive audit of user journeys across all **5 reForm platform pipelines** and defines **15 Net-New Agents** required to transform reForm into an enterprise-grade agentic platform in 2026. 

While existing components (`LayoutAgent`, 18 `IToolCallHandler` strategy beans) and previously designed agents (`GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `EvaluationAgent`, `RagSearchAgent`) handle basic layout generation, security filtering, session teardown, and vector search, critical operational gaps remain across schema validation, UI styling, localization, adaptive branching, raw audio stream processing, virus scanning, chunking/embeddings, analytics, metering, archival, session failover, and security auditing.

This discovery report bridges those gaps by specifying 15 Net-New agents with complete technical rigor: exact Java 21 data types, trigger mechanisms, GoF design patterns with structural justification, technology selections with explicit rationale over alternatives, single-bullet SOLID + KISS compliance, and key architectural open questions.

---

## 1. User Journey Audit Across 5 reForm Pipelines

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    reForm 5-PIPELINE ARCHITECTURE                                │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
   │
   ├─► 1. Form Builder Pipeline (Prompt -> Schema -> Layout -> Theme -> Preview -> Publish -> Version)
   │
   ├─► 2. Form Filler Pipeline (Conversational Filling -> Field Validation -> Adaptive Branching -> Voice)
   │
   ├─► 3. File & Media Processing Pipeline (Upload -> Malware Scan -> OCR -> STT -> Chunk & Embed)
   │
   ├─► 4. Background Pipeline (Evaluation -> Analytics -> Billing/Metering -> Archival)
   │
   └─► 5. Session Lifecycle Pipeline (Init -> Memory & Goals -> Guardrails -> State -> Security Audit)
```

### Pipeline 1: Form Builder Pipeline
*User Journey*: A form creator uses natural language prompt (text/voice Mode 2/4) or reference documents to generate form schemas, customize block layouts, apply UI styling themes, preview interactively, publish shareable URLs, and manage form schema versions.
*Gap Analysis*: Existing `LayoutAgent` handles structural block addition/mutation via Gemini 3.6 Flash. However, the pipeline lacks dedicated agents for schema compilation validation (`SchemaAgent`), UI theme synthesis (`ThemeAgent`), multi-language localization (`TranslationAgent`), and breaking schema version migration (`FormVersioningAgent`).

### Pipeline 2: Form Filler Pipeline
*User Journey*: A respondent fills out a form via interactive chat (Mode 2) or real-time voice conversation (Mode 4). The platform validates field inputs, dynamically prunes/expands questions based on answer logic, processes raw audio streams with low latency, and calculates form completion progress.
*Gap Analysis*: Existing tool handlers (`ValidateFieldAnswerToolHandler`, `TriggerBranchingToolHandler`) act as simple dispatchers. Enterprise filler sessions require autonomous agents to evaluate complex DAG conditional rules (`AdaptiveBranchingAgent`), handle sub-50ms PCM VAD and audio frame stream processing (`VoiceSpeechAgent`), and execute cross-field/external API verification (`ValidationAgent`).

### Pipeline 3: File & Media Processing Pipeline
*User Journey*: Users upload external assets (PDF syllabi, job descriptions, resume scans, voice recordings) for form creation or interview response attachments. Uploads are scanned for threats, parsed via OCR, transcribed from speech to text, chunked, and embedded for vector RAG search.
*Gap Analysis*: Designed `RagSearchAgent` queries `pgvector`, but lacks background pipeline agents for binary malware inspection (`MalwareScanAgent`), speaker diarization and audio transcription (`AudioTranscriptionSubAgent`), and semantic text chunking & vector embedding generation (`DocumentChunkingEmbeddingAgent`).

### Pipeline 4: Background Pipeline
*User Journey*: Asynchronous background processing after session completion: evaluating respondent answers, aggregating workspace conversion/drop-off analytics, calculating token and voice usage billing, updating vector indices, and archiving cold data.
*Gap Analysis*: Designed `EvaluationAgent` scores submissions and `BillingAgent` tracks balances. Enterprise operations require sub-millisecond atomic quota metering (`TokenMeteringAgent`), pre-computed analytics rollups (`AnalyticsAggregationAgent`), and compliant data retention cold storage archiving (`ArchivalAgent`).

### Pipeline 5: Session Lifecycle Pipeline
*User Journey*: End-to-end management of filler and builder sessions: session initialization, user goal synthesis, multi-turn state retention, guardrail safety checking, rate limiting, connection drop recovery, session teardown, and compliance security auditing.
*Gap Analysis*: Designed `MemoryGoalAgent` synthesizes goals and `GuardrailAgent` validates text safety. However, the system lacks agents for multi-node WebSocket state snapshot recovery (`SessionStateAgent`) and real-time PII/anomalous access audit logging (`SecurityAuditAgent`).

---

## 2. Discovered Net-New Agents Deep Dive

Below are full technical specifications for all 15 discovered Net-New Agents across the 5 reForm pipelines.

---

### PIPELINE 1: FORM BUILDER PIPELINE AGENTS

#### Agent 1: `SchemaAgent` (Form Block Schema Compiler & Validator)
1. **Name & Role**: `SchemaAgent` validates, compiles, and normalizes AI-generated form block JSON definitions against reForm platform schema specifications and field constraint invariants before database persistence.
2. **Trigger Mechanism**: Published Spring ApplicationEvent `SchemaCompilationEvent` (emitted by `LayoutAgent` after Mode 2 LLM layout generation) or WebSocket client schema payload frame.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.SchemaCompilationEvent`
     ```java
     public record SchemaCompilationEvent(
         UUID formId,
         String rawJsonSchema,
         UUID tenantId,
         String builderSessionId
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.SchemaCompilationResult`
     ```java
     public record SchemaCompilationResult(
         UUID formId,
         List<AbstractBlock> validatedBlocks,
         List<SchemaValidationError> errors,
         boolean isValid
     ) {}
     ```
4. **Design Pattern**: **Strategy Pattern** (interchangeable validation rules per block type) combined with **Chain of Responsibility** (sequential validation pipeline: syntax check $\rightarrow$ constraint check $\rightarrow$ nesting depth check). *Why*: Decouples general JSON validation from block-specific domain rules (e.g. `ChoiceStaticBlock` vs `ConversationalBlock`).
5. **Technology Choice**: **Jackson `JsonSchema` + Hibernate Validator 8.0 / Jakarta Validation** over raw string regex regex parsing because standard Jackson JSON Schema Draft 2020-12 bindings provide strict structural type enforcement and native Java bean annotation validation.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Dedicated strictly to structural block schema compilation and constraint validation; isolated from LLM generation or DB persistence.
   - **Open/Closed**: New `AbstractBlock` subclasses register new validation strategies without modifying `SchemaAgent` core engine.
   - **Liskov Substitution**: All block validation strategies implement `IBlockValidatorStrategy`, allowing seamless substitution.
   - **Interface Segregation**: Exposes a minimal `ISchemaValidator` interface with `validate(SchemaCompilationEvent)` method.
   - **Dependency Inversion**: Depends on high-level `IBlockValidatorStrategy` abstractions injected via Spring `@Autowired List<IBlockValidatorStrategy>`.
   - **KISS**: Executes a single linear pass over block nodes using existing Jackson `ObjectMapper` without heavy external script execution.
7. **Open Questions**: Should custom client-side validation JavaScript snippets embedded in form blocks be compiled by GraalVM in sandbox mode or restricted to JSON-declarative validation rules?

---

#### Agent 2: `ThemeAgent` (AI Theme & WCAG Accessibility Synthesizer)
1. **Name & Role**: `ThemeAgent` synthesizes custom CSS/Tailwind design systems, color palettes, typography specs, and WCAG 2.1 AA/AAA compliant light/dark themes from natural language branding prompts.
2. **Trigger Mechanism**: Tool call `generateFormTheme` (invoked by builder persona in Mode 2/4) or Spring `FormThemeGenerationEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.FormThemeGenerationEvent`
     ```java
     public record FormThemeGenerationEvent(
         UUID formId,
         String brandingPrompt,
         String baseColorHex,
         WCAGLevel targetCompliance // ENUM: AA, AAA
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
4. **Design Pattern**: **Builder Pattern** (step-by-step construction of complex UI theme objects) combined with **Template Method** (defining theme synthesis sequence: palette generation $\rightarrow$ token mapping $\rightarrow$ contrast audit $\rightarrow$ serialization). *Why*: Theme generation requires strict sequential steps where contrast auditing must run before final CSS emission.
5. **Technology Choice**: **Tailwind CSS v4 Engine + Color4j / Culori library + Gemini 3.6 Flash** over unconstrained raw CSS generation because Tailwind design tokens guarantee consistent frontend UI rendering while Color4j mathematically calculates WCAG contrast ratios.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Dedicated exclusively to color math, design token generation, and accessibility contrast verification.
   - **Open/Closed**: Pluggable theme renderers support new design systems (Shadcn UI, Material 3) without modifying core synthesis logic.
   - **Liskov Substitution**: Any `IThemeSynthesizer` implementation produces compliant `FormThemeResult` payloads.
   - **Interface Segregation**: Decouples `IThemeSynthesizer` from `IContrastAuditor` interfaces.
   - **Dependency Inversion**: Injects abstract `IContrastAuditor` rather than concrete color math implementations.
   - **KISS**: Emits flat key-value CSS variable maps (`--color-primary`, `--color-bg`) directly injectable into DOM.
7. **Open Questions**: How should real-time live preview theme changes over WebSocket handle high-frequency sliding color pickers without overwhelming LLM token rate limits?

---

#### Agent 3: `TranslationAgent` (Multilingual Form Localization Agent)
1. **Name & Role**: `TranslationAgent` translates form question titles, descriptions, option labels, and error messages into 50+ languages while preserving localized validation rules and formatting.
2. **Trigger Mechanism**: HTTP POST request `/api/v1/forms/{id}/translate` or Spring `FormTranslationEvent`.
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
4. **Design Pattern**: **Flyweight Pattern** (reusing translation cache for universal UI labels like "Submit", "Required", "Next") combined with **Decorator Pattern** (wrapping raw translations with locale-specific formatting logic). *Why*: Minimizes redundant LLM translation costs for common form UI terms across tenants.
5. **Technology Choice**: **Gemini 3.6 Flash + Redis String Cache (`translation:cache:{hash}`)** over static Google Cloud Translation API because Gemini contextualizes translations based on form domain (e.g. medical vs legal context), while Redis eliminates duplicate translation calls.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Handles only multi-language textual conversion and locale metadata enrichment.
   - **Open/Closed**: Supports custom terminology glossaries per enterprise workspace via configurable translation context strategies.
   - **Liskov Substitution**: Implements `ITranslationEngine` contract interchangeable between LLM provider and static fallbacks.
   - **Interface Segregation**: Exposes a clean `translate(FormTranslationEvent)` contract.
   - **Dependency Inversion**: High-level translation service depends on abstract `ITranslationCache` and `ILLMProvider`.
   - **KISS**: Operates on a simple flat dictionary map of block UUID to translated strings.
7. **Open Questions**: Should localized validation rules (e.g. US Zip Code vs UK Postcode regex) automatically swap when the respondent switches form display language?

---

#### Agent 4: `FormVersioningAgent` (Form Schema Diffing & Migration Agent)
1. **Name & Role**: `FormVersioningAgent` manages semantic versioning (v1.0.0 $\rightarrow$ v1.1.0), schema diffing, and migration of active in-flight respondent submissions when a published form is edited by the creator.
2. **Trigger Mechanism**: Published Spring `FormPublishedEvent` or direct `publishForm` tool call execution.
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
4. **Design Pattern**: **Command Pattern** (encapsulating schema migration operations as reversible commands) combined with **Observer Pattern** (notifying active session state agents when breaking schema changes are published). *Why*: Enables atomic rollback of form schema deployments if active sessions report incompatibility.
5. **Technology Choice**: **java-diff-utils + Jackson JsonPatch (RFC 6902)** over full database snapshot duplication because RFC 6902 JSON Patch operations provide atomic, low-overhead PostgreSQL JSONB migrations and precise breaking change detection.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Manages only form versioning history, schema diff calculation, and session migration compatibility.
   - **Open/Closed**: Migration handlers for new block types register via `IBlockMigrationStrategy` without touching core diffing logic.
   - **Liskov Substitution**: All diff handlers adhere to `ISchemaDiffEngine`.
   - **Interface Segregation**: Clean separation between `IVersionPublisher` and `ISessionMigrationHandler`.
   - **Dependency Inversion**: Injects abstract `IFormVersionRepository` for database persistence.
   - **KISS**: Uses standard RFC 6902 JSON Patch arrays (`[{"op": "add", "path": "/blocks/3", ...}]`).
7. **Open Questions**: If a published form deletion of a required block invalidates an ongoing 30-minute filler session, should the session auto-adapt or freeze and request user re-validation?

---

### PIPELINE 2: FORM FILLER PIPELINE AGENTS

#### Agent 5: `AdaptiveBranchingAgent` (Dynamic Graph Pruning & Branching Agent)
1. **Name & Role**: `AdaptiveBranchingAgent` evaluates complex conditional logic, user sentiment, and multi-turn response data in real time to dynamically prune or insert form question blocks into the respondent's interview path.
2. **Trigger Mechanism**: WebSocket frame `ANSWER_SUBMITTED` or Spring `FieldAnswerSubmittedEvent`.
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
4. **Design Pattern**: **Interpreter Pattern** (evaluating boolean expression trees against session state) combined with **Directed Acyclic Graph (DAG) Traverser**. *Why*: Questions represent nodes in a DAG where conditional expressions dictate edge traversal.
5. **Technology Choice**: **Spring Expression Language (SpEL) / MVEL + JGraphT** over hardcoded `if-else` trees because SpEL provides safe, high-performance in-memory evaluation of complex logic predicates (`#answers['income'] > 100000`).
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Focuses solely on evaluating question graph traversal and conditional branching rules.
   - **Open/Closed**: New expression operators or context providers can be added without modifying the graph evaluation engine.
   - **Liskov Substitution**: Implements `IBranchingEvaluator`, guaranteeing consistent graph resolution across form modes.
   - **Interface Segregation**: Exposes `evaluateNextStep(FieldAnswerSubmittedEvent)` without exposing internal graph structures.
   - **Dependency Inversion**: Depends on abstract `ISessionAnswerProvider` to query session state.
   - **KISS**: Evaluates expressions against a flat `Map<String, Object>` answer state map.
7. **Open Questions**: How can we detect and prevent infinite loop cycles when form creators configure conflicting bi-directional conditional branching logic?

---

#### Agent 6: `VoiceSpeechAgent` (VAD & Low-Latency Audio Stream Processing Agent)
1. **Name & Role**: `VoiceSpeechAgent` manages real-time bidirectional PCM audio streams, Voice Activity Detection (VAD), barge-in audio suppression, and ambient noise filtering for low-latency Mode 4 sessions.
2. **Trigger Mechanism**: Inbound WebSocket audio binary frames (`wss://.../ws/live-filler`) or Gemini Live Socket 2 frames.
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
4. **Design Pattern**: **Pipeline Architecture (Pipes & Filters)** combined with **Observer Pattern** (broadcasting VAD state changes to WebSocket session handlers). *Why*: Allows sequential audio signal transformations (decibel calculation $\rightarrow$ noise suppression $\rightarrow$ VAD classification) with zero buffer copying.
5. **Technology Choice**: **Netty ByteBuf + Silero VAD / Java Sound API + WebRTC AudioProcessing native bindings** over standard Java byte arrays because direct off-heap `ByteBuf` allocations eliminate JVM Garbage Collection pauses during high-frequency 20ms audio frame processing.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Responsible strictly for raw audio signal processing, noise reduction, and VAD framing.
   - **Open/Closed**: Audio filters (echo cancellation, pitch modulation) can be inserted into the Netty pipeline without altering stream handling.
   - **Liskov Substitution**: All audio processors implement `IAudioFilter` interface.
   - **Interface Segregation**: Decoupled into `IVadDetector` and `IAudioStreamProcessor` interfaces.
   - **Dependency Inversion**: Relies on `IAudioChannelHandler` abstractions.
   - **KISS**: Processes fixed 20ms PCM 16kHz audio chunks with direct ring-buffer memory.
7. **Open Questions**: What is the optimal decibel threshold and frame window to distinguish background room chatter from intentional user voice barge-in during speech synthesis output?

---

#### Agent 7: `ValidationAgent` (Cross-Field Business & API Validation Agent)
1. **Name & Role**: `ValidationAgent` executes real-time field validation, format checking, regex matching, and external API verification (address autocomplete, VAT/TIN check) on user responses before updating session state.
2. **Trigger Mechanism**: Tool call `validateFieldAnswer` or Spring `FieldValidationRequestEvent`.
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
4. **Design Pattern**: **Strategy Pattern** (selecting distinct validator strategies per field type) combined with **Decorator Pattern** (chaining basic regex validators with external API lookup validators). *Why*: Enables combining lightweight static regex checks with heavy external HTTP verification.
5. **Technology Choice**: **Hibernate Validator + Resilience4j CircuitBreaker (for external HTTP lookup APIs)** over inline regex checks because CircuitBreaker prevents external third-party API outages from blocking live filler sessions.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Solitary responsibility of validating and normalizing submitted field values.
   - **Open/Closed**: Custom domain validators (e.g. IBAN validator, Healthcare NPI check) plug in as new `@Component` implementations of `IFieldValidator`.
   - **Liskov Substitution**: Substitutable `IFieldValidator` contracts ensure deterministic execution.
   - **Interface Segregation**: Exposes segregated `IFieldValidator` and `IExternalValidationProvider` interfaces.
   - **Dependency Inversion**: Injects set of `IFieldValidator` beans via Spring dependency injection.
   - **KISS**: Short-circuits on first failing rule to minimize unnecessary external API calls.
7. **Open Questions**: How should asynchronous external validation (e.g. identity verification requiring 3 seconds) interact with low-latency Mode 4 conversational audio flow?

---

### PIPELINE 3: FILE & MEDIA PROCESSING PIPELINE AGENTS

#### Agent 8: `MalwareScanAgent` (Security Virus & Sandbox Media Inspector Agent)
1. **Name & Role**: `MalwareScanAgent` inspects uploaded documents, images, and media files for binary malware signatures, suspicious embedded scripts, and container vulnerabilities before passing files to OCR or RAG indexing pipelines.
2. **Trigger Mechanism**: Published Spring `DocumentUploadedEvent` (emitted upon S3 upload).
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.DocumentUploadedEvent`
     ```java
     public record DocumentUploadedEvent(
         UUID documentId,
         UUID workspaceId,
         String fileKey,
         String contentType,
         long fileSizeBytes,
         byte[] headerBytes
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.MalwareScanResult`
     ```java
     public record MalwareScanResult(
         UUID documentId,
         ScanStatus status, // ENUM: CLEAN, INFECTED, SUSPICIOUS, ERROR
         String virusName,
         String fileHashSha256,
         boolean safeForProcessing
     ) {}
     ```
4. **Design Pattern**: **Chain of Responsibility** (passing file through fast magic header inspection $\rightarrow$ signature scan $\rightarrow$ sandboxed heuristic scan) combined with **Observer Pattern** (notifying workflow orchestrators on threat detection). *Why*: Fast-fails known malicious files at step 1 before incurring expensive engine overhead.
5. **Technology Choice**: **ClamAV REST Container / LibClamAV + Apache Tika (MIME spoofing detection)** over simple file extension checks because ClamAV provides enterprise open-source virus signature scanning while Tika prevents file extension spoofing attacks.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Exclusively handles file safety, virus detection, and binary integrity validation.
   - **Open/Closed**: Additional scan engines (e.g. VirusTotal API scanner) can be appended to the scan chain.
   - **Liskov Substitution**: All scan steps implement `IScanStage`.
   - **Interface Segregation**: Clean interface `IMalwareScanner` with `scan(DocumentUploadedEvent)`.
   - **Dependency Inversion**: Orchestration depends on high-level `IScanStage` interface.
   - **KISS**: Fast-fails immediately if MIME type headers contradict actual magic bytes.
7. **Open Questions**: Should infected files be immediately hard-deleted from temporary staging storage or retained in an isolated quarantine S3 bucket for forensic analysis?

---

#### Agent 9: `AudioTranscriptionSubAgent` (Speaker Diarization & Speech-to-Text Sub-Agent)
1. **Name & Role**: `AudioTranscriptionSubAgent` performs high-accuracy asynchronous transcription, speaker diarization, background noise separation, and sentiment tagging on long audio recordings and saved voice sessions.
2. **Trigger Mechanism**: Spring `AudioTranscriptionRequestEvent` or background task queue.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.AudioTranscriptionRequestEvent`
     ```java
     public record AudioTranscriptionRequestEvent(
         UUID audioId,
         UUID sessionId,
         String audioFileUrl,
         String audioFormat,
         String targetLanguage
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.AudioTranscriptionResult`
     ```java
     public record AudioTranscriptionResult(
         UUID audioId,
         String fullTranscriptText,
         List<TranscriptSegment> segments,
         String dominantSentiment,
         double wordConfidenceScore
     ) {}
     ```
4. **Design Pattern**: **Worker Thread / Task Queue Pattern** (offloading heavy audio processing) combined with **Adapter Pattern** (abstracting STT engine providers). *Why*: Long audio files take seconds/minutes to process and must run asynchronously on dedicated worker pools.
5. **Technology Choice**: **Whisper (via Deepgram API / Faster-Whisper local container) + Spring AMQP / RabbitMQ** over synchronous web API calls because message queues prevent HTTP timeouts and support retry backoff for long audio processing.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Dedicated to audio-to-text conversion, diarization, and confidence scoring.
   - **Open/Closed**: Pluggable `ISpeechToTextProvider` supports switching between Deepgram, Whisper, and Google Speech-to-Text.
   - **Liskov Substitution**: All STT adapters return unified `AudioTranscriptionResult` format.
   - **Interface Segregation**: Segregated `ITranscriptionEngine` and `IDiarizationEngine` interfaces.
   - **Dependency Inversion**: Depends on `ISpeechToTextProvider` abstraction.
   - **KISS**: Emits clean transcript segments with explicit start/end timestamps in milliseconds.
7. **Open Questions**: For multi-speaker interviews (e.g. interviewer and respondent), how can speaker diarization accurately map audio segments to session answer blocks?

---

#### Agent 10: `DocumentChunkingEmbeddingAgent` (Semantic Chunking & HNSW Vector Embedding Agent)
1. **Name & Role**: `DocumentChunkingEmbeddingAgent` parses extracted document text into semantic chunks, generates high-dimensional vector embeddings, and indexes them in PostgreSQL `pgvector` HNSW index for sub-session RAG retrieval.
2. **Trigger Mechanism**: Spring `DocumentIngestionEvent` (emitted after OCR and malware scanning complete).
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.DocumentIngestionEvent`
     ```java
     public record DocumentIngestionEvent(
         UUID documentId,
         UUID workspaceId,
         String extractedText,
         Map<String, String> metadata
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.DocumentEmbeddingResult`
     ```java
     public record DocumentEmbeddingResult(
         UUID documentId,
         int totalChunksCreated,
         int embeddingsStored,
         String vectorIndexName,
         long processingTimeMs
     ) {}
     ```
4. **Design Pattern**: **Pipeline Pattern** (Text Extraction $\rightarrow$ Semantic Chunking $\rightarrow$ Embedding Generation $\rightarrow$ Vector Storage) combined with **Strategy Pattern** (Chunking strategy: fixed-token, sentence-boundary, or markdown-header aware). *Why*: Decouples chunking logic from vector API client generation.
5. **Technology Choice**: **LangChain4j + Spring Data JPA (`pgvector` with HNSW index `vector_cosine_ops`) + Gemini text-embedding-004** over external vector DBs like Pinecone/Weaviate because storing embeddings inside PostgreSQL eliminates multi-database sync overhead and simplifies transactional consistency.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Handles semantic text chunking, embedding API invocation, and vector database indexing.
   - **Open/Closed**: Chunking strategies (`IChunkingStrategy`) can be extended for tabular data, code, or unstructured text without changing indexer.
   - **Liskov Substitution**: Substitutable `IEmbeddingModel` abstraction (Gemini, OpenAI, Cohere).
   - **Interface Segregation**: Exposes simple `IEmbeddingIndexer` interface.
   - **Dependency Inversion**: Depends on `IVectorRepository` and `IEmbeddingModel` abstractions.
   - **KISS**: Uses standard 768-dimensional float arrays stored in native PostgreSQL `vector` data type.
7. **Open Questions**: What is the optimal chunk size (e.g. 512 tokens with 64-token overlap) to maximize recall precision during real-time voice interview RAG queries?

---

### PIPELINE 4: BACKGROUND PIPELINE AGENTS

#### Agent 11: `AnalyticsAggregationAgent` (Asynchronous Drop-off & Conversion Analytics Agent)
1. **Name & Role**: `AnalyticsAggregationAgent` asynchronously aggregates form completion metrics, block drop-off rates, average turn duration, sentiment scores, and response distributions across workspace forms into Redis/PostgreSQL OLAP caches.
2. **Trigger Mechanism**: Scheduled Cron Trigger (`0 */5 * * * *` - every 5 minutes) or Spring `SessionEndedEvent`.
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
4. **Design Pattern**: **Aggregator Pattern** combined with **Batch Executer Pattern** (running scheduled map-reduce aggregation over session submissions). *Why*: Prevents heavy analytical SQL queries from executing synchronously on active transaction threads.
5. **Technology Choice**: **PostgreSQL Window Functions / DuckDB Embedded + Redis Hash Caches (`analytics:form:{id}`)** over synchronous per-request aggregation because background rollups prevent analytics queries from slowing down live database transactions.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Purely responsible for computing, caching, and serving workspace analytics metrics.
   - **Open/Closed**: New metric calculators (e.g. voice engagement score) implement `IMetricCalculator` and register automatically.
   - **Liskov Substitution**: All metric calculators return standardized metric data structures.
   - **Interface Segregation**: Segregated `IAnalyticsAggregator` interface for query and computation.
   - **Dependency Inversion**: Depends on `ISubmissionRepository` analytical abstraction.
   - **KISS**: Pre-aggregates hourly bucket totals into flat Redis Hashes for instant UI dashboard rendering.
7. **Open Questions**: Should real-time analytics for active live sessions be pushed via WebSocket or polled periodically by the dashboard?

---

#### Agent 12: `TokenMeteringAgent` (Real-Time Quota & Billing Metering Agent)
1. **Name & Role**: `TokenMeteringAgent` tracks real-time LLM token consumption, speech synthesis audio duration (seconds), and vector storage usage per workspace, enforcing quota limits and updating billing balances atomically.
2. **Trigger Mechanism**: Published Spring `BillingUsageEvent` (emitted by tool call handlers and audio streams).
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.BillingUsageEvent`
     ```java
     public record BillingUsageEvent(
         UUID workspaceId,
         UUID sessionId,
         String meterType, // ENUM: LLM_TOKENS, VOICE_SECONDS, VECTOR_QUERIES
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
4. **Design Pattern**: **Observer Pattern** (listening to resource consumption events across system) combined with **Token Bucket Pattern** (managing workspace quota buckets). *Why*: Ensures decouple metering logic from core application tool handlers.
5. **Technology Choice**: **Redis Lua Scripts (Atomic Decrby & Bucket Check) + Redisson distributed locks** over relational DB updates because high-frequency token events during voice streaming require sub-millisecond atomic decrement in RAM.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Sole responsibility of tracking resource consumption and enforcing workspace quota balances.
   - **Open/Closed**: New resource meters (e.g. OCR page count, video processing minutes) are added by registering new `MeterType` ENUMs.
   - **Liskov Substitution**: Guarantees deterministic atomic quota calculation across all meter types.
   - **Interface Segregation**: Exposes minimal `IMeteringService` interface with `recordUsage()` and `checkQuota()`.
   - **Dependency Inversion**: Injects abstract `IQuotaCacheRepository` interface.
   - **KISS**: Uses single Redis Lua script execution per usage event to eliminate race conditions.
7. **Open Questions**: When a workspace exhausts its credit quota mid-interview, should the agent gracefully terminate the session with a polite voice message or allow a buffer overage?

---

#### Agent 13: `ArchivalAgent` (Scheduled Data Retention & Cold Storage Lifecycle Agent)
1. **Name & Role**: `ArchivalAgent` executes automated lifecycle policies for completed sessions, soft-deleted forms, and expired media files, archiving data into cold storage and enforcing compliance retentions (GDPR/HIPAA).
2. **Trigger Mechanism**: Scheduled Cron Trigger (`0 0 2 * * *` - daily at 2:00 AM) or Spring `DataArchivalTriggerEvent`.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.ArchivalJobConfig`
     ```java
     public record ArchivalJobConfig(
         UUID workspaceId,
         int retentionDays,
         boolean compressFiles,
         String targetStorageTier // ENUM: S3_GLACIER, GCS_COLDLINE
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
4. **Design Pattern**: **Command Pattern** (encapsulating archival policies as executable batch jobs) combined with **Strategy Pattern** (storage provider tiering: S3 Standard $\rightarrow$ S3 Glacier / GCS Coldline). *Why*: Decouples execution timing from specific storage providers.
5. **Technology Choice**: **AWS SDK v2 S3 Lifecycle Rules / Google Cloud Storage Client + Spring Batch** over manual SQL deletion queries because Spring Batch provides chunk-based processing, transaction retry, and failure recovery for millions of records.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Dedicated exclusively to data retention, cold storage archiving, and compliance purging.
   - **Open/Closed**: Archival destinations (AWS S3, GCP Storage, Azure Blob) plug in via `IStorageArchiver` strategies.
   - **Liskov Substitution**: Substitutable storage target strategies adhere to `IStorageArchiver`.
   - **Interface Segregation**: Exposes `IArchivalJobManager` interface.
   - **Dependency Inversion**: Relies on abstract `ISessionArchivalRepository` and `IStorageArchiver`.
   - **KISS**: Uses standard date-partitioned storage keys (`archives/YYYY/MM/form_{id}.tar.gz`).
7. **Open Questions**: How should GDPR "Right to be Forgotten" hard-deletion requests override scheduled multi-year compliance archival retentions?

---

### PIPELINE 5: SESSION LIFECYCLE PIPELINE AGENTS

#### Agent 14: `SessionStateAgent` (State Snapshot & WebSocket Node Handover Agent)
1. **Name & Role**: `SessionStateAgent` maintains multi-turn conversation state snapshots, handles WebSocket connection drops, and orchestrates seamless session state migration across backend application cluster nodes.
2. **Trigger Mechanism**: Inbound WebSocket connection frame, client heartbeat ping, or socket reconnect event.
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.SessionStateSnapshotEvent`
     ```java
     public record SessionStateSnapshotEvent(
         UUID sessionId,
         UUID formId,
         UUID userId,
         String sessionPhase,
         Map<String, Object> stateVariables,
         int turnCount
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.SessionStateRecoveryResult`
     ```java
     public record SessionStateRecoveryResult(
         UUID sessionId,
         SessionStatus status, // ENUM: ACTIVE, RECOVERED, TIMED_OUT, TERMINATED
         Map<String, Object> restoredState,
         String targetNodeId,
         boolean reconnectedSuccessfully
     ) {}
     ```
4. **Design Pattern**: **Memento Pattern** (capturing and restoring session state snapshots) combined with **State Pattern** (managing session state transitions: INIT $\rightarrow$ ACTIVE $\rightarrow$ PAUSED $\rightarrow$ TERMINATED). *Why*: Enables restoring past state frames without exposing internal state fields.
5. **Technology Choice**: **Redis Hash (`session:state:{id}`) + Redisson Distributed State Engine + Spring Session** over local JVM heap storage because Redis RAM allows any backend server node to instantly resume a reconnected WebSocket session.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Focuses strictly on conversation state persistence, snapshotting, and cluster failover recovery.
   - **Open/Closed**: Session state serializers can support new serialization formats (Kryo, Protobuf, Jackson JSON) via `ISerializerStrategy`.
   - **Liskov Substitution**: Guaranteed state recovery contract across node failures.
   - **Interface Segregation**: Segregated `ISessionStateStore` interface.
   - **Dependency Inversion**: Interacts with abstract `ISessionCache` interface.
   - **KISS**: Stores session state as serialized JSON strings in Redis with explicit TTL (30 mins).
7. **Open Questions**: In high-frequency Mode 4 voice streams (100ms updates), should state snapshots be persisted synchronously after every turn or asynchronously via write-behind buffer?

---

#### Agent 15: `SecurityAuditAgent` (Anomalous Access & PII Leakage Guard Agent)
1. **Name & Role**: `SecurityAuditAgent` monitors active sessions for anomalous behavior, prompt injection attacks, unauthorized access attempts, PII leakage in voice/text, and rate limit violations, logging immutable audit trails.
2. **Trigger Mechanism**: Spring `SecurityAuditEvent` or interceptor filter trigger (`JwtHandshakeInterceptor`, `HttpRateLimitInterceptor`).
3. **Input / Output**:
   - **Input**: `com.reForm.backend.ai.event.SecurityAuditEvent`
     ```java
     public record SecurityAuditEvent(
         UUID sessionId,
         UUID userId,
         String clientIp,
         String requestPath,
         AuditSeverity severity, // ENUM: LOW, MEDIUM, HIGH, CRITICAL
         String payloadSnippet,
         String detectedThreat
     ) {}
     ```
   - **Output**: `com.reForm.backend.ai.event.SecurityAuditResult`
     ```java
     public record SecurityAuditResult(
         UUID auditId,
         ActionTaken action, // ENUM: LOGGED, THROTTLED, SESSION_TERMINATED, IP_BLOCKED
         boolean sessionBlocked,
         String alertNotificationId,
         Instant loggedTimestamp
     ) {}
     ```
4. **Design Pattern**: **Interceptor Pattern** (intercepting active requests and events) combined with **Observer Pattern** (notifying security operations and ops telemetry on threat detection). *Why*: Guarantees zero modification to business logic code paths during audit monitoring.
5. **Technology Choice**: **Spring Security Filter Chain + Logback / SLF4J MDC + Elasticsearch / OpenSearch Append-Only Audit Index** over simple database log tables because log aggregators handle high-volume audit event streams without impacting DB write performance.
6. **SOLID + KISS Justification**:
   - **Single Responsibility**: Solely dedicated to security inspection, threat flagging, and compliance audit trail generation.
   - **Open/Closed**: Threat detection rules (e.g. SQLi patterns, XSS regex, PII detection) are registered as pluggable `ISecurityRule` beans.
   - **Liskov Substitution**: All threat detectors return standardized `SecurityAuditResult`.
   - **Interface Segregation**: Clean interface `ISecurityAuditor` with `audit(SecurityAuditEvent)`.
   - **Dependency Inversion**: Depends on abstract `IAuditLogRepository` interface.
   - **KISS**: Uses standard W3C log formatting with SLF4J MDC context variables (IP, SessionId, WorkspaceId).
7. **Open Questions**: Should detected PII (e.g. credit card numbers spoken in voice) be masked in RAM before reaching Gemini LLM or intercepted post-transcript generation?

---

## 3. System Integration & Architectural Alignment

### Master Agent Classification Summary

| Pipeline | Agent Name | Status | Primary Pattern | Core Technology |
|---|---|---|---|---|
| **Form Builder** | `LayoutAgent` | **Built** | Observer + Strategy | Gemini 3.6 Flash + Spring `@Async` |
| **Form Builder** | `SchemaAgent` | **Net-New** | Strategy + Chain of Resp. | Jackson JsonSchema + Jakarta Validation |
| **Form Builder** | `ThemeAgent` | **Net-New** | Builder + Template Method | Tailwind CSS v4 + Color4j + Gemini 3.6 |
| **Form Builder** | `TranslationAgent` | **Net-New** | Flyweight + Decorator | Gemini 3.6 Flash + Redis String Cache |
| **Form Builder** | `FormVersioningAgent` | **Net-New** | Command + Observer | java-diff-utils + Jackson JsonPatch |
| **Form Filler** | 18 Tool Handler Beans | **Built** | Strategy Pattern | Spring `@Component` Strategy Registry |
| **Form Filler** | `AdaptiveBranchingAgent` | **Net-New** | Interpreter + DAG Traverser | SpEL / MVEL + JGraphT |
| **Form Filler** | `VoiceSpeechAgent` | **Net-New** | Pipeline + Observer | Netty ByteBuf + Silero VAD + WebRTC |
| **Form Filler** | `ValidationAgent` | **Net-New** | Strategy + Decorator | Hibernate Validator + CircuitBreaker |
| **File Processing** | `MalwareScanAgent` | **Net-New** | Chain of Responsibility | ClamAV REST + Apache Tika |
| **File Processing** | `AudioTranscriptionSubAgent` | **Net-New** | Worker Queue + Adapter | Whisper / Deepgram + RabbitMQ |
| **File Processing** | `DocumentChunkingEmbeddingAgent` | **Net-New** | Pipeline + Strategy | LangChain4j + `pgvector` HNSW + Gemini |
| **Background** | `EvaluationAgent` | **Designed** | Strategy + Template Method | Gemini 3.6 Flash + Spring `@Async` |
| **Background** | `BillingAgent` | **Designed** | Observer Pattern | Redis RAM + PostgreSQL |
| **Background** | `AnalyticsAggregationAgent` | **Net-New** | Aggregator + Batch Executer | PostgreSQL Window Functions + Redis |
| **Background** | `TokenMeteringAgent` | **Net-New** | Observer + Token Bucket | Redis Lua Scripts + Redisson |
| **Background** | `ArchivalAgent` | **Net-New** | Command + Strategy | AWS S3 Glacier + Spring Batch |
| **Session Lifecycle** | `GuardrailAgent` | **Designed** | Interceptor + Strategy | Llama-Guard / Gemini Flash + Spring Filter |
| **Session Lifecycle** | `MemoryGoalAgent` | **Designed** | State + Strategy | Redis JSON + PostgreSQL |
| **Session Lifecycle** | `RagSearchAgent` | **Designed** | Strategy Pattern | `pgvector` HNSW + Gemini 3.6 |
| **Session Lifecycle** | `SessionStateAgent` | **Net-New** | Memento + State Pattern | Redis Hash + Redisson + Spring Session |
| **Session Lifecycle** | `SecurityAuditAgent` | **Net-New** | Interceptor + Observer | Spring Security + OpenSearch / SLF4J |

---

## 4. Verification & Validation Protocol

To independently verify the architecture specifications in this discovery report:
1. **Inspect Target Paths**:
   - `backend/src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java`
   - `backend/src/main/java/com/reForm/backend/ai/tool/handler/` (18 tool handler files)
   - `backend/knowledge/pth/week4/08_layout_agent_and_full_tool_handler_catalog.md`
2. **Data Model Validation**: Verify compatibility of `AbstractBlock` with `SchemaCompilationResult` and `AdaptiveBranchingResult`.
3. **Event Contract Compliance**: Ensure all event payloads use immutable Java 21 `record` declarations residing in package `com.reForm.backend.ai.event`.
