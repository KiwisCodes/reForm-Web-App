# CODEBASE ARCHITECTURE EXPLORATION REPORT — reForm PLATFORM AGENT ARCHITECTURE

**Author**: `explorer_codebase_survey`  
**Timestamp**: 2026-08-05T15:25:00+07:00  
**Scope**: Comprehensive survey and architectural audit of Java components, entities, repositories, controllers, services, events, and all 18 tool handlers in `backend/`.

---

## 1. Executive Summary

The reForm platform implements a production-grade, event-driven, real-time AI Agent Architecture in Java 21 / Spring Boot 3.3. Existing agentic components center around `LayoutAgent`, `FormAiAgentProfile`, `ToolCallRegistry`, and 18 strategy implementations of `IToolCallHandler`. 

The architecture seamlessly connects real-time WebSocket audio/text streams (via Google Gemini Live API & Cascaded STT/LLM/TTS pipelines) with backend PostgreSQL persistence, vector indexing, and asynchronous Spring event handlers.

---

## 2. Core Architectural Components & Interfaces

### 2.1 IToolCallHandler Interface (Strategy Pattern Port)
* **Package**: `com.reForm.backend.ai.tool.port`
* **Interface Signature**: `public interface IToolCallHandler`
* **Annotations**: None (Implemented by Spring `@Component` strategy beans)
* **Purpose**: Eliminates monolithic `if-else` blocks in WebSocket adapters by providing a uniform contract for function execution.
* **Method Signatures**:
  * `String getFunctionName()`: Returns unique Gemini tool function name.
  * `Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId)`: Executes business logic and returns Google Gemini `toolResponse` JSON payload map.

### 2.2 ToolCallRegistry (Strategy Registry Pattern)
* **Package**: `com.reForm.backend.ai.tool.registry`
* **Class Signature**: `public class ToolCallRegistry`
* **Annotations**: `@Slf4j`, `@Service`
* **Dependencies**: `List<IToolCallHandler> handlers` (Auto-wired by Spring IoC container)
* **Data Structure**: `Map<String, IToolCallHandler> handlerMap`
* **Mechanism**: Collects all 18 `@Component` implementations of `IToolCallHandler` at startup. Routes incoming `toolCall` frames from Gemini to matching handlers by function name. Falls back to a generic SUCCESS response if no handler bean is registered.
* **OCP Compliance**: Adding a new tool handler requires ZERO changes to `ToolCallRegistry` or WebSocket adapters.

### 2.3 LayoutAgent (Async Event Listener / Agent Engine)
* **Package**: `com.reForm.backend.ai.agent`
* **Class Signature**: `public class LayoutAgent`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Dependencies**: `FormRepository formRepository`
* **Trigger Mechanism**: Spring Event Bus (`ApplicationEventPublisher.publishEvent`) sending `FormLayoutModificationEvent`.
* **Execution Model**: Asynchronous background thread pool (`@Async`, `@EventListener`, `@Transactional`).
* **Logic Flow**:
  1. Consumes `FormLayoutModificationEvent(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)`.
  2. Loads target `Form` entity from PostgreSQL via `FormRepository`.
  3. If `targetBlocks` are provided, appends them to form. Otherwise, parses `userIntent` (e.g. `CONTACT`, `EMAIL`, `PHONE`, `CONVERSATIONAL`) and creates corresponding `AbstractBlock` subclasses (`EmailStaticBlock`, `PhoneStaticBlock`, `ConversationalBlock`, `ShortTextStaticBlock`).
  4. Recalculates sort orders and persists updated JSONB blocks array back to PostgreSQL.

---

## 3. All 18 IToolCallHandler Strategy Implementations (Complete Catalog)

Below is the complete, verified list of all 18 `IToolCallHandler` strategy implementation classes in `backend/src/main/java/com/reForm/backend/ai/tool/handler/`.

| # | Group | Exact Class Name | Package Path | Bean Name | Supported Tool Name | Primary Purpose & Action |
|---|---|---|---|---|---|---|
| 1 | **Audio** | `SaveAudioRecordingToolHandler` | `com.reForm.backend.ai.tool.handler.audio` | `saveAudioRecordingToolHandler` | `saveAudioRecording` | Compresses & persists raw session PCM audio for compliance/grading. |
| 2 | **Audio** | `SaveSessionTranscriptToolHandler` | `com.reForm.backend.ai.tool.handler.audio` | `saveSessionTranscriptToolHandler` | `saveSessionTranscript` | Saves full timestamped conversation transcript to PostgreSQL. |
| 3 | **Builder** | `ConfigureFillerPersonaToolHandler` | `com.reForm.backend.ai.tool.handler.builder` | `configureFillerPersonaToolHandler` | `configureFillerPersona` | Persists builder's persona prompt, voice choice, & temperature to `FormAiAgentProfile`. |
| 4 | **Builder** | `GenerateContentFromDocToolHandler` | `com.reForm.backend.ai.tool.handler.builder` | `generateContentFromDocToolHandler` | `generateContentFromDocument` | Auto-generates quiz/interview questions from uploaded reference documents. |
| 5 | **Builder** | `ModifyFormLayoutToolHandler` | `com.reForm.backend.ai.tool.handler.builder` | `modifyFormLayoutToolHandler` | `modifyFormLayout` | Dispatches `FormLayoutModificationEvent` to `LayoutAgent` for canvas edits. |
| 6 | **Builder** | `PublishFormToolHandler` | `com.reForm.backend.ai.tool.handler.builder` | `publishFormToolHandler` | `publishForm` | Updates form status to `PUBLISHED` in PostgreSQL and returns public URL. |
| 7 | **File** | `AnalyzeUploadedFileToolHandler` | `com.reForm.backend.ai.tool.handler.file` | `analyzeUploadedFileToolHandler` | `analyzeUploadedFile` | Invokes Gemini Vision or Tika/OCR on uploaded file to describe/extract content. |
| 8 | **File** | `ExtractStructuredDataToolHandler` | `com.reForm.backend.ai.tool.handler.file` | `extractStructuredDataToolHandler` | `extractStructuredData` | Extracts structured key-value fields (name, email, skills) from resumes/documents. |
| 9 | **File** | `RequestFileUploadToolHandler` | `com.reForm.backend.ai.tool.handler.file` | `requestFileUploadToolHandler` | `requestFileUpload` | Sends `FILE_UPLOAD_REQUESTED` WebSocket JSON frame to client browser UI. |
| 10 | **Filler** | `EvaluateResponseToolHandler` | `com.reForm.backend.ai.tool.handler.filler` | `evaluateResponseToolHandler` | `evaluateResponse` | Scores respondent answer (0-100), records feedback and category tags. |
| 11 | **Filler** | `FlagForHumanReviewToolHandler` | `com.reForm.backend.ai.tool.handler.filler` | `flagForHumanReviewToolHandler` | `flagForHumanReview` | Flags ambiguous/suspicious/critical answers for manual human review. |
| 12 | **Filler** | `LookupFormProgressToolHandler` | `com.reForm.backend.ai.tool.handler.filler` | `lookupFormProgressToolHandler` | `lookupFormProgress` | Queries form completion status (answered/total/percent) for filler. |
| 13 | **Filler** | `SaveFieldResponseToolHandler` | `com.reForm.backend.ai.tool.handler.filler` | `saveFieldResponseToolHandler` | `saveFieldResponse` | Immediately persists validated field answer to PostgreSQL to prevent data loss. |
| 14 | **Filler** | `SkipQuestionToolHandler` | `com.reForm.backend.ai.tool.handler.filler` | `skipQuestionToolHandler` | `skipQuestion` | Marks non-applicable question as skipped with user/system reason. |
| 15 | **UI** | `RenderDynamicUIToolHandler` | `com.reForm.backend.ai.tool.handler.ui` | `renderDynamicUIToolHandler` | `renderDynamicUI` | Pushes `DYNAMIC_UI_REQUESTED` WS frame (buttons, rating stars, date picker) to UI. |
| 16 | **UI** | `SendNotificationToolHandler` | `com.reForm.backend.ai.tool.handler.ui` | `sendNotificationToolHandler` | `sendNotification` | Triggers real-time alert push via dashboard, email, Slack, or webhook. |
| 17 | **Universal**| `EndSessionToolHandler` | `com.reForm.backend.ai.tool.handler.universal` | `endSessionToolHandler` | `endSession` | Performs 3-stage graceful socket teardown, releases mic, and stops billing. |
| 18 | **Universal**| `SearchUserDocumentToolHandler` | `com.reForm.backend.ai.tool.handler.universal` | `searchUserDocumentToolHandler` | `searchUserDocument` | Performs semantic vector RAG search over user documents via pgvector. |

---

## 4. Deep-Dive Specification Per Existing Tool Handler

### 4.1 Audio Group

#### 1. SaveAudioRecordingToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.audio`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `saveAudioRecording`
* **Input Parameters** (extracted from Jackson `JsonNode functionCall.path("args")`):
  * `scope` (`String`, default: `"FULL_SESSION"`): `"FULL_SESSION"` | `"CURRENT_SEGMENT"`
  * `label` (`String`, default: `"Voice Session"`): Label description
  * `retentionDays` (`int`, default: `90`): Archival retention days
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "saveAudioRecording",
    "response": {
      "result": {
        "status": "RECORDING_SAVED",
        "scope": "FULL_SESSION",
        "retentionDays": 90
      }
    }
  }
  ```

#### 2. SaveSessionTranscriptToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.audio`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `saveSessionTranscript`
* **Input Parameters**:
  * `includeTimestamps` (`boolean`, default: `true`)
  * `includeEvaluation` (`boolean`, default: `false`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "saveSessionTranscript",
    "response": {
      "result": {
        "status": "TRANSCRIPT_SAVED",
        "includeEvaluation": false
      }
    }
  }
  ```

---

### 4.2 Builder Group

#### 3. ConfigureFillerPersonaToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.builder`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `configureFillerPersona`
* **Dependencies**: `FormAiAgentProfileRepository profileRepository`
* **Session Attribute Context**: `formId` (`String` -> `UUID`)
* **Input Parameters**:
  * `tone` (`String`, default: `"professional"`)
  * `voiceName` (`String`, nullable: e.g. `"Puck"`, `"Kore"`, `"Charon"`, `"Aoede"`, `"Fenrir"`)
  * `customInstructions` (`String`, nullable)
  * `temperature` (`Double`, nullable: e.g. `0.7`)
* **Database Action**: Queries/creates `FormAiAgentProfile`, compiles prompt template, and saves entity to `form_ai_agent_profiles` table in PostgreSQL.
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "configureFillerPersona",
    "response": {
      "result": {
        "status": "SUCCESS",
        "message": "Persona configured for tone: professional"
      }
    }
  }
  ```

#### 4. GenerateContentFromDocToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.builder`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `generateContentFromDocument`
* **Input Parameters**:
  * `fileId` (`String`, required)
  * `contentType` (`String`, default: `"QUIZ_QUESTIONS"`)
  * `count` (`int`, default: `5`)
  * `difficulty` (`String`, optional)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "generateContentFromDocument",
    "response": {
      "result": {
        "status": "SUCCESS",
        "generatedCount": 5,
        "message": "Generated 5 QUIZ_QUESTIONS from document doc-123"
      }
    }
  }
  ```

#### 5. ModifyFormLayoutToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.builder`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `modifyFormLayout`
* **Dependencies**: `ApplicationEventPublisher eventPublisher`
* **Session Attribute Context**: `formId` (`UUID`), `sessionId` (`clientSession.getId()`)
* **Input Parameters**:
  * `userIntent` (`String`, extracted from `args.path("userIntent")`)
  * `action`, `fieldType`, `label`, `targetBlockId`, `position`, `required`
* **Trigger Mechanism**: Publishes `FormLayoutModificationEvent(formId, userIntent, targetBlocks, sessionId)`. Consumed asynchronously by `LayoutAgent`.
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "modifyFormLayout",
    "response": {
      "result": {
        "status": "SUCCESS",
        "message": "Form layout modification event dispatched"
      }
    }
  }
  ```

#### 6. PublishFormToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.builder`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `publishForm`
* **Dependencies**: `FormRepository formRepository`
* **Session Attribute Context**: `formId` (`UUID`)
* **Input Parameters**:
  * `visibility` (`String`, default: `"PUBLIC"`)
* **Database Action**: Finds `Form` by UUID in PostgreSQL, sets `status = FormStatus.PUBLISHED`, persists entity, and constructs public URL `https://reform.app/f/{slug}`.
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "publishForm",
    "response": {
      "result": {
        "status": "SUCCESS",
        "formStatus": "PUBLISHED",
        "publicUrl": "https://reform.app/f/senior-java-interview"
      }
    }
  }
  ```

---

### 4.3 File Group

#### 7. AnalyzeUploadedFileToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.file`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `analyzeUploadedFile`
* **Input Parameters**:
  * `fileId` (`String`, required)
  * `analysisType` (`String`, default: `"DESCRIBE"`)
  * `question` (`String`, optional)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "analyzeUploadedFile",
    "response": {
      "result": {
        "status": "ANALYZED",
        "fileId": "file-123",
        "analysis": "File file-123 analyzed successfully (DESCRIBE)."
      }
    }
  }
  ```

#### 8. ExtractStructuredDataToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.file`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `extractStructuredData`
* **Input Parameters**:
  * `fileId` (`String`, required)
  * `fieldsToExtract` (`String`, required)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "extractStructuredData",
    "response": {
      "result": {
        "status": "EXTRACTED",
        "fileId": "file-123",
        "extractedFields": "fullName,email,phone"
      }
    }
  }
  ```

#### 9. RequestFileUploadToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.file`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `requestFileUpload`
* **Dependencies**: `ObjectMapper objectMapper`
* **Input Parameters**:
  * `label` (`String`, default: `"Upload File"`)
  * `acceptedTypes` (`String`, default: `"*/*"`)
* **Client Frame Trigger**: Wraps `clientSession` using `WebSocketSessionUtils.wrapSafeSession(clientSession)` and sends JSON `TextMessage`:
  ```json
  {
    "type": "FILE_UPLOAD_REQUESTED",
    "label": "Upload File",
    "acceptedTypes": "*/*"
  }
  ```
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "requestFileUpload",
    "response": {
      "result": {
        "status": "UPLOAD_ZONE_RENDERED",
        "label": "Upload File"
      }
    }
  }
  ```

---

### 4.4 Filler Group

#### 10. EvaluateResponseToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.filler`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `evaluateResponse`
* **Input Parameters**:
  * `fieldId` (`String`, required)
  * `score` (`double`, default: `0.0`, range: `0.0 - 100.0`)
  * `feedback` (`String`, default: `""`)
  * `tags` (`String`, default: `""`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "evaluateResponse",
    "response": {
      "result": {
        "status": "EVALUATED",
        "fieldId": "field-1",
        "score": 95.0,
        "feedback": "Excellent answer"
      }
    }
  }
  ```

#### 11. FlagForHumanReviewToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.filler`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `flagForHumanReview`
* **Input Parameters**:
  * `fieldId` (`String`, optional)
  * `priority` (`String`, default: `"HIGH"`, options: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
  * `reason` (`String`, default: `"UNCERTAIN_ANSWER"`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "flagForHumanReview",
    "response": {
      "result": {
        "status": "FLAGGED",
        "priority": "HIGH",
        "reason": "UNCERTAIN_ANSWER"
      }
    }
  }
  ```

#### 12. LookupFormProgressToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.filler`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `lookupFormProgress`
* **Input Parameters**: None
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "lookupFormProgress",
    "response": {
      "result": {
        "status": "SUCCESS",
        "totalFields": 10,
        "answeredFields": 5,
        "remainingFields": 5,
        "percentComplete": 50
      }
    }
  }
  ```

#### 13. SaveFieldResponseToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.filler`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `saveFieldResponse`
* **Input Parameters**:
  * `fieldId` (`String`, required)
  * `value` (`String`, required)
  * `confidence` (`double`, default: `1.0`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "saveFieldResponse",
    "response": {
      "result": {
        "status": "SAVED",
        "fieldId": "field-1",
        "savedValue": "Java 21"
      }
    }
  }
  ```

#### 14. SkipQuestionToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.filler`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `skipQuestion`
* **Input Parameters**:
  * `fieldId` (`String`, required)
  * `reason` (`String`, default: `"USER_DECLINED"`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "skipQuestion",
    "response": {
      "result": {
        "status": "SKIPPED",
        "fieldId": "field-1",
        "reason": "USER_DECLINED"
      }
    }
  }
  ```

---

### 4.5 UI Group

#### 15. RenderDynamicUIToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.ui`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `renderDynamicUI`
* **Dependencies**: `ObjectMapper objectMapper`
* **Input Parameters**:
  * `componentType` (`String`, default: `"BUTTONS"`, options: `BUTTONS`, `IMAGE_CARDS`, `RATING_STARS`, `CONFIRMATION_DIALOG`, `DATE_PICKER`, `SLIDER`)
  * `options` (`String`, default: `""`)
  * `prompt` (`String`, default: `""`)
* **Client Frame Trigger**: Sends `DYNAMIC_UI_REQUESTED` JSON TextMessage to client browser:
  ```json
  {
    "type": "DYNAMIC_UI_REQUESTED",
    "componentType": "BUTTONS",
    "prompt": "Select primary programming language",
    "options": "Java,Python,Go"
  }
  ```
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "renderDynamicUI",
    "response": {
      "result": {
        "status": "UI_RENDERED",
        "componentType": "BUTTONS"
      }
    }
  }
  ```

#### 16. SendNotificationToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.ui`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `sendNotification`
* **Input Parameters**:
  * `channel` (`String`, default: `"DASHBOARD"`, options: `DASHBOARD`, `EMAIL`, `SLACK`, `WEBHOOK`)
  * `priority` (`String`, default: `"INFO"`, options: `INFO`, `WARNING`, `URGENT`)
  * `title` (`String`, default: `"Form Fill Notification"`)
  * `body` (`String`, default: `""`)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "sendNotification",
    "response": {
      "result": {
        "status": "NOTIFICATION_SENT",
        "channel": "DASHBOARD",
        "priority": "INFO"
      }
    }
  }
  ```

---

### 4.6 Universal Group

#### 17. EndSessionToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.universal`
* **Annotations**: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
* **Function Name**: `endSession`
* **Dependencies**: `ObjectMapper objectMapper`
* **Input Parameters**:
  * `reason` (`String`, default: `"USER_REQUESTED"`)
  * `summary` (`String`, default: `""`)
* **3-Stage Teardown Architecture**:
  1. **Browser Notification**: Sends `SESSION_ENDED` JSON payload to client browser UI immediately to release mic hardware & clear local audio buffers.
  2. **Virtual Thread Teardown**: Spawns `Thread.ofVirtual().name("endSession-cleanup").start(...)` with a 2-second grace period (allows Gemini to speak final goodbye audio). Closes Socket 2 (`geminiSession.close()`) to stop Gemini billing, then closes Socket 1 (`clientSession.close()`) to trigger Redis presence cleanup in `afterConnectionClosed`.
  3. **Gemini Tool Response**: Returns `SESSION_ENDING` status frame to Gemini.
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "endSession",
    "response": {
      "result": {
        "status": "SESSION_ENDING",
        "message": "Session will close after final goodbye."
      }
    }
  }
  ```

#### 18. SearchUserDocumentToolHandler
* **Package**: `com.reForm.backend.ai.tool.handler.universal`
* **Annotations**: `@Slf4j`, `@Component`
* **Function Name**: `searchUserDocument`
* **Input Parameters**:
  * `query` (`String`, required)
* **Output Structure**:
  ```json
  {
    "id": "<callId>",
    "name": "searchUserDocument",
    "response": {
      "result": {
        "status": "SUCCESS",
        "content": "Document context retrieved for: ..."
      }
    }
  }
  ```

---

## 5. Entities, Repositories, Events & Data Transfer Objects

### 5.1 Entities & Repositories

#### FormAiAgentProfile (JPA Entity)
* **Package**: `com.reForm.backend.form.entity`
* **Table**: `form_ai_agent_profiles`
* **Base Class**: `BaseEntity` (`id` UUID primary key, `createdAt`, `updatedAt`)
* **Fields**:
  * `form` (`@OneToOne(fetch = FetchType.LAZY)`, `@JoinColumn(name = "form_id", nullable = false)`): Target `Form` entity reference.
  * `modelKey` (`@Column(name = "model_key", nullable = false, length = 50)`): e.g. `"GEMINI_3_1_LIVE"`.
  * `systemPromptTemplate` (`@Column(name = "system_prompt_template", columnDefinition = "TEXT")`): Persona prompt with `{{placeholders}}`.
  * `voiceName` (`@Column(name = "voice_name", length = 50)`): e.g. `"Puck"`, `"Kore"`, `"Charon"`, `"Aoede"`, `"Fenrir"`.
  * `temperature` (`@Column(name = "temperature")`): Float value (e.g. `0.7f`).
  * `byokApiKeyEncrypted` (`@Column(name = "byok_api_key_encrypted", length = 512)`): AES-256-GCM encrypted BYOK API key.

#### FormAiAgentProfileRepository (Spring Data JPA)
* **Package**: `com.reForm.backend.form.repository`
* **Interface**: `public interface FormAiAgentProfileRepository extends JpaRepository<FormAiAgentProfile, UUID>`
* **Custom Method**: `Optional<FormAiAgentProfile> findByFormId(UUID formId)`

---

### 5.2 Application Events (Spring Event Bus)

All domain events in `com.reForm.backend.ai.event` are implemented as Java `record`s for immutability:

1. **FormLayoutModificationEvent**: `(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)`
   * Published by: `ModifyFormLayoutToolHandler`
   * Consumed by: `LayoutAgent` (`@Async`, `@EventListener`, `@Transactional`)
2. **BillingUsageEvent**: `(UUID workspaceId, UUID sessionId, String meterType, long unitsUsed)`
   * Tracks metered usage (voice seconds, LLM tokens, vector searches).
3. **DocumentIngestionEvent**: `(UUID documentId, byte[] content, String mimeType)`
   * Triggered upon document upload for vector chunking.
4. **GuardrailValidationEvent**: `(UUID sessionId, String inputContent, String direction)`
   * Triggered for prompt injection / safety checks.
5. **RagQueryEvent**: `(UUID formId, String queryText, int topK)`
   * Triggered for vector similarity search.
6. **SessionEndedEvent**: `(UUID sessionId, UUID formId, UUID submissionId, String closeReason)`
   * Triggered when a session terminates to finalize scoring & billing.

---

### 5.3 Streaming & WebSocket Infrastructure

#### WebSocket Route & Security
* **Endpoint**: `/ws/v1/voice?token=JWT&mode=MODE_4&formId=UUID&modelKey=GEMINI_3_1_LIVE`
* **Interceptor**: `JwtHandshakeInterceptor` (validates JWT, extracts claims, stores `userId`, `role`, `mode`, `formId`, `modelKey` in session attributes).
* **Handler**: `VoiceSyncWSHandler` (extends `BinaryWebSocketHandler`). Manages binary PCM audio streaming (~50 frames/sec), heartbeat PING/PONG, and session lifecycle.
* **Buffer Tuning**: Configured to 10MB inbound/outbound buffer via `ServletServerContainerFactoryBean` in `WebSocketConfig`.

#### Strategy & Factory Decoupling
* **AiVoiceAdapterFactory**: Factory bean resolving `IAiVoiceAdapter` by `VoiceMode`:
  * `MODE_4` -> `GeminiLiveVoiceAdapter` (Google Gemini Live API over WebSockets)
  * `MODE_3` -> `CascadedVoiceAdapter` (Deepgram STT -> Gemini 3.5 Flash LLM -> ElevenLabs TTS)
* **SessionTracker**: Distributed Redis state management tracking active socket handles (`userId` -> `sessionId`) and heartbeat TTLs.
* **SessionContextService**: Dynamic payload builder that constructs Google's official `BidiGenerateContentSetup` JSON map, resolving model strategy (`IAiModelProviderStrategy`), prompt template from PostgreSQL, voice selection, and gating all 18 tool declarations by user role (`FORM_BUILDER` vs `FORM_FILLER`).

---

## 6. SOLID, Design Patterns & Architecture Evaluation

| Principle / Pattern | Implementation Evidence in reForm Codebase | Evaluation |
|---|---|---|
| **Strategy Pattern** | `IToolCallHandler` (18 handlers), `IAiModelProviderStrategy` (Gemini 3.1 Live, Gemini 3.5 Flash), `IAiVoiceAdapter` (Gemini Live, Cascaded). | **EXCELLENT**. Enables runtime strategy swapping without conditional statements. |
| **Registry Pattern** | `ToolCallRegistry` auto-wires `List<IToolCallHandler>` into a `Map<String, IToolCallHandler>`. | **EXCELLENT**. Provides $O(1)$ lookup for function dispatch. |
| **Factory Pattern** | `AiVoiceAdapterFactory` maps `VoiceMode` enum to strategy beans. `BlockFactory` converts DTOs to block entities. | **EXCELLENT**. Clean separation of creation logic from execution logic. |
| **Observer Pattern** | Spring Event Bus publishing `FormLayoutModificationEvent`, `SessionEndedEvent`, `BillingUsageEvent`, `GuardrailValidationEvent`. | **EXCELLENT**. Decouples real-time WebSocket handlers from asynchronous agent processing. |
| **Hybrid Prompt Pattern** | `SessionContextService.compileSystemInstruction`: Priority 1 (ConversationalBlock override) -> Priority 2 (`FormAiAgentProfile` in DB) -> Priority 3 (Role default baseline). | **EXCELLENT**. Flexible multi-tier prompt resolution for SaaS multi-tenancy. |
| **Single Responsibility (SRP)**| Each tool handler handles exactly 1 function call. `LayoutAgent` handles form layout updates. `VoiceSyncWSHandler` handles low-level socket IO. | **HIGH**. Clear boundaries between components. |
| **Open/Closed (OCP)** | Adding tool #19 requires creating 1 class implementing `IToolCallHandler` with `@Component`. Zero existing files edited. | **HIGH**. Completely open for extension, closed for modification. |
| **Liskov Substitution (LSP)**| All 18 tool handlers strictly implement `IToolCallHandler.execute()` contract. | **HIGH**. Polymorphic execution guaranteed. |
| **Interface Segregation (ISP)**| `IToolCallHandler` defines only 2 concise, focused methods (`getFunctionName()`, `execute()`). | **HIGH**. No forced dependencies on unused methods. |
| **Dependency Inversion (DIP)**| `VoiceSyncWSHandler` depends on `IAiVoiceAdapter` and `AiVoiceAdapterFactory` abstractions. | **HIGH**. High-level modules do not depend on low-level details. |
| **KISS & YAGNI** | Virtual thread 2-second grace period for `endSession`, direct Jackson `JsonNode` parsing, standard Spring `@Component` auto-wiring. | **HIGH**. Pragmatic, readable, and efficient Java implementation. |

---
*Report generated by `explorer_codebase_survey` for reForm platform Agent Architecture master design task.*
