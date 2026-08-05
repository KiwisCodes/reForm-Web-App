# Step 0 Survey — Part 2: Form Builder Agent Suite (R2 Requirements)

**Author:** teamwork_preview_explorer_survey_2  
**Date:** 2026-08-05  
**Target Repository:** `/Users/apple/Coding-projects/reForm-Web-App`  
**Reference Document:** `/Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md`

---

## 1. Executive Summary

This survey report provides a comprehensive technical investigation of the **Form Builder Agent Suite** under Requirement **R2** of the reForm multi-agent architecture. The reForm platform provides an omni-modal form creation and candidate evaluation system operating across a 4-mode operational spectrum. This report focuses specifically on **Form Builder Modes 1, 2, and 4** and the four specialized agents that power form generation, layout modification, schema derivation, persona configuration, and document ingestion:

1. **LayoutAgent** — Dynamic form UI structure and block layout management.
2. **SchemaGeneratorAgent** — JSON schema generation, field derivation, and validation rule synthesis.
3. **PersonaConfigAgent** — Persona tuning, system prompt compilation, voice model selection, and temperature configuration for voice fill agents.
4. **DocumentIngestionAgent** — Multi-format document parsing (PDF, DOCX, XLSX, images), text extraction, and domain context generation.

---

## 2. Form Builder Modes Enumeration & Deep-Dive (Modes 1, 2, 4)

reForm implements a block-level operational spectrum where each form block can operate under a distinct mode, with `Form.defaultMode` serving as the fallback baseline.

```
+-----------------------------------------------------------------------------------+
|                            FORM BUILDER CREATION MODES                            |
+-----------------------------------+-----------------------------------------------+
| Mode 1: Manual Form Builder       | Drag-and-drop canvas over HTTP REST (Free)    |
| Mode 2: Text Chat Co-Builder      | Text LLM chat (Gemini 3.6 Flash) over HTTP/SSE|
| Mode 4: Voice Native Live Builder | Bi-directional voice (Gemini 3.1 Live) over WSS|
+-----------------------------------+-----------------------------------------------+
```

### 2.1 Mode 1: Manual Form Builder (Static Canvas)

* **Overview:** Traditional visual drag-and-drop builder interface (similar to Google Forms or Typeform). Creators manually select, arrange, and configure static form blocks without AI assistance.
* **Transport Protocol:** HTTP REST (`GET /api/v1/form/{formId}`, `PUT /api/v1/form/{formId}/blocks`, `POST /api/v1/form`).
* **Target Engine:** Client-side React rendering canvas (Zero LLM invocation).
* **Cost Profile:** $0.00 / minute.
* **Features:**
  * Drag-and-drop block positioning and re-ordering (`sortOrder`).
  * Manual configuration of standard inputs (Short Text, Long Text, Email, Phone, URL).
  * Manual setup of selection inputs (Radio Choice, Checkbox, Dropdown, Image Choice).
  * Setup of quantitative inputs (Star Rating, Number Rating, Opinion Scale).
  * Setup of complex inputs (Date/Time Picker, Matrix Grid, Signature Pad).
  * File Upload dropzone configuration (supported extensions, file size limits).
  * Manual addition of AI Conversational Blocks and baseline field metadata.
* **Data Models:**
  * `Form` (`forms` table in PostgreSQL)
  * `AbstractBlock` JSONB inheritance hierarchy (`ShortTextStaticBlock`, `EmailStaticBlock`, `PhoneStaticBlock`, `ChoiceStaticBlock`, `ConversationalBlock`, etc.)
  * `BlockValidationRule` (required, regex, min/max length/size)
* **Inputs & Outputs:**
  * *Input:* `FormCreateDto`, `FormUpdateDto` JSON payloads.
  * *Output:* `FormResponseDto` containing compiled `List<AbstractBlock>`.
* **Triggers:** User UI events (clicks, drags, property updates).
* **State Persistence:** PostgreSQL `forms` table (`blocks` JSONB column).
* **Dependencies:** `BuilderController`, `IFormBuilderService`, `FormRepository`, `AbstractBlockConverter`.

---

### 2.2 Mode 2: Text-Based AI Chatbot Helper (Co-Builder Chat)

* **Overview:** Interactive text-only chat pane where the creator communicates with an AI assistant via typing. The AI generates, populates, and mutates form structures in real-time alongside a split-screen preview canvas.
* **Transport Protocol:** HTTP REST / Server-Sent Events (SSE) (`POST /api/v1/builder/chat`).
* **Target Engine:** Gemini 3.6 Flash (`models/gemini-3.6-flash`).
* **Cost Profile:** ~$0.002 / minute (~0.2¢/min).
* **Features:**
  * Natural language requirement parsing (e.g., *"Build an HR recruitment form for Java engineers with 3 technical questions"*).
  * Document-driven form structure generation (uploading reference syllabi or job descriptions).
  * Iterative refinement loop: Creator hovers over a block in the preview, types inline suggestions/comments (e.g. *"Change file size limit to 10MB"* or *"Add a question on PostgreSQL indexing"*), and clicks **Update** to trigger schema regeneration.
  * Automated block type selection (static vs. conversational blocks).
* **Data Models:**
  * `Form`, `FormAiAgentProfile`
  * `AbstractBlock` JSONB array
  * Chat session message DTOs (`CoBuilderChatMessageDto`, `CoBuilderSessionState`)
* **Inputs & Outputs:**
  * *Input:* Text prompt strings, inline block comment DTOs, document attachment IDs.
  * *Output:* Streamed text responses (SSE) + updated `List<AbstractBlock>` JSON payload for split-screen preview rendering.
* **Triggers:** Builder text chat submit, document attachment event, block comment update trigger.
* **State Persistence:** PostgreSQL `forms.blocks` JSONB column, transient chat buffer in Redis / DB session logs.
* **Dependencies:** `SchemaGeneratorAgent`, `LayoutAgent`, `PersonaConfigAgent`, `DocumentIngestionAgent`, `Gemini35FlashModelStrategy`.

---

### 2.3 Mode 4: Voice-Conversational AI Helper (Native Live Co-Builder)

* **Overview:** Real-time, bi-directional voice chat environment. The creator speaks directly into their microphone, and Gemini 3.1 Flash Live speaks out loud in response while live-updating the form layout on the split-screen preview canvas.
* **Transport Protocol:** Raw WebSockets (`/ws/v1/voice`).
* **Target Engine:** Gemini 3.1 Flash Live (`models/gemini-3.1-flash-live-preview`).
* **Cost Profile:** ~$0.027 / minute (~2.7¢/min).
* **Latency:** ~300ms (sub-second natural conversational pace).
* **Features:**
  * Bi-directional raw PCM audio streaming (16kHz audio input from browser microphone, audio response output to browser speakers).
  * Native barge-in: Speech interruption auto-detected by Gemini Live, instantly flushing outbound audio buffers.
  * Parallel Function Calling / Tool Execution during live voice sessions:
    * `modifyFormLayout`: Emits `FormLayoutModificationEvent` for non-blocking execution by `LayoutAgent`.
    * `configureFillerPersona`: Emits persona update request for non-blocking execution by `PersonaConfigAgent`.
    * `generateContentFromDocument`: Invokes `DocumentIngestionAgent` and `SchemaGeneratorAgent`.
    * `publishForm`: Updates form status to `PUBLISHED` and generates public shareable URL.
  * Acoustic nuance processing (detecting tone, speed, and emphasis).
  * Real-time split-screen canvas update via WebSocket broadcast events.
* **Data Models:**
  * `Form`, `FormAiAgentProfile`, `AbstractBlock` array.
  * WebSocket frames (`BinaryMessage` for audio, `TextMessage` for JSON tool calls/responses).
* **Inputs & Outputs:**
  * *Input:* Bi-directional PCM binary audio frames (~50 frames/sec), JSON tool call invocations.
  * *Output:* Bi-directional PCM audio output stream, WebSocket canvas update events, persisted layout state in PostgreSQL.
* **Triggers:** WebSocket connection establishment, client audio frames, Gemini Live function calls (`modifyFormLayout`, `configureFillerPersona`, etc.).
* **State Persistence:** PostgreSQL (`forms`, `form_ai_agent_profiles`), Redis (`SessionTracker` online state).
* **Dependencies:** `VoiceSyncWSHandler`, `GeminiLiveVoiceAdapter`, `AiVoiceAdapterFactory`, `ToolCallRegistry`, `ModifyFormLayoutToolHandler`, `ConfigureFillerPersonaToolHandler`, `LayoutAgent`, `PersonaConfigAgent`, `SchemaGeneratorAgent`.

---

## 3. R2 Form Builder Agent Suite Analysis

Requirement **R2** mandates decoupled Spring `@Component` service agents for all Form Builder processes:

```
+-----------------------------------------------------------------------------------+
|                           R2 FORM BUILDER AGENT SUITE                             |
+------------------------+----------------------------------------------------------+
| LayoutAgent            | Listens to layout modification events & mutates canvas    |
| SchemaGeneratorAgent   | Translates prompts/docs into JSON schemas & field rules |
| PersonaConfigAgent     | Compiles system prompts, voice choice & agent profiles   |
| DocumentIngestionAgent | Parses PDF/DOCX/XLSX & extracts domain knowledge         |
+------------------------+----------------------------------------------------------+
```

---

### 3.1 LayoutAgent

#### A. Current Implementation State
* **File Location:** `src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java`
* **Status:** Existing Spring `@Component` listener.
* **Mechanism:** Subscribed via `@EventListener` to `FormLayoutModificationEvent` on a background thread pool (`@Async`).

```java
@Async
@EventListener
@Transactional
public void handleLayoutModification(FormLayoutModificationEvent event) {
    // 1. Fetch Form from PostgreSQL via FormRepository
    // 2. Map userIntent string (e.g. "ADD_CONTACT_SECTION") to concrete AbstractBlock
    // 3. Set sortOrder and append to form.getBlocks()
    // 4. Save Form back to PostgreSQL
}
```

#### B. Architectural Requirements & Specifications
* **Role:** Manages the physical UI layout and structural block arrangement of the form.
* **Supported Operations:**
  * Block Creation: `ADD_SHORT_TEXT`, `ADD_EMAIL`, `ADD_PHONE`, `ADD_RATING`, `ADD_CONVERSATIONAL_BLOCK`, `ADD_FILE_UPLOAD`.
  * Block Reordering: Updating `sortOrder` across `List<AbstractBlock>`.
  * Block Deletion: Removing specified `blockId` from layout.
  * Block Modification: Updating labels, descriptions, and required flags.
* **Inputs:** `FormLayoutModificationEvent` (containing `formId`, `userIntent`, `targetBlockId`, `customParameters`).
* **Outputs:** Updated `Form` entity persisted to PostgreSQL, WebSocket broadcast event (`CANVAS_UPDATED`) to connected clients.
* **Triggers:** `ModifyFormLayoutToolHandler` during Mode 4 voice co-building, REST co-builder endpoints during Mode 2 chat.
* **State Persistence:** PostgreSQL `forms.blocks` (JSONB column).
* **Dependencies:** `FormRepository`, `ApplicationEventPublisher`, Jackson ObjectMapper.

#### C. Gaps to Close for R2 Compliance
1. Expand intent mapping to support all selection (`ChoiceStaticBlock`), quantitative (`StarRatingStaticBlock`, `OpinionScaleStaticBlock`), and complex blocks (`DateTimeStaticBlock`, `FileUploadStaticBlock`).
2. Add explicit support for `DELETE_BLOCK` and `REORDER_BLOCKS` actions.
3. Emit a real-time WebSocket notification event (`FormCanvasUpdatedEvent`) so the frontend preview pane refreshes instantly upon block mutation.

---

### 3.2 SchemaGeneratorAgent

#### A. Current Implementation State
* **File Location:** Not yet implemented as a standalone `@Component` in `com.reForm.backend.ai.agent`.
* **Status:** Logic is currently split between `BlockFactory`, DTO mappers, and tool handler mocks.

#### B. Architectural Requirements & Specifications
* **Role:** Translates natural language instructions, target goals, or extracted document contents into valid JSON Schema and `AbstractBlock` object graphs.
* **Supported Operations:**
  * `generateSchemaFromPrompt(String prompt, String formType)`: Parses builder intent and generates a complete initial list of blocks with validation rules.
  * `generateSchemaFromDocument(String documentId, ExtractionType type)`: Converts document syllabus/job description into structured questionnaire items.
  * `refineBlockSchema(AbstractBlock existingBlock, String builderComment)`: Modifies a single block's schema based on builder inline comments.
* **Inputs:** Text prompts, raw document text, existing block schema, target domain guidelines.
* **Outputs:** `List<AbstractBlock>` DTOs, JSON Schema string, `BlockValidationRule` objects.
* **Triggers:** Mode 2 Co-Builder chat REST requests, Mode 4 document generation tool calls (`GenerateContentFromDocToolHandler`).
* **State Persistence:** PostgreSQL `forms.blocks` JSONB column.
* **Dependencies:** `IAiModelProviderStrategy` (Gemini Flash), `BlockFactory`, Jackson ObjectMapper.

#### C. Implementation Specification for Implementers
Create `@Component` `SchemaGeneratorAgent` in package `com.reForm.backend.ai.agent`:
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaGeneratorAgent {
    private final IAiModelProviderStrategy modelStrategy; // Gemini 3.6 Flash
    private final ObjectMapper objectMapper;

    public List<AbstractBlock> generateFormSchema(String prompt, String contextDocumentText) {
        // Formulates structured output JSON prompt to Gemini
        // Deserializes response into concrete AbstractBlock instances
    }
}
```

---

### 3.3 PersonaConfigAgent

#### A. Current Implementation State
* **File Location:** Partially present in `ConfigureFillerPersonaToolHandler.java` (directly accessing `FormAiAgentProfileRepository`).
* **Status:** Requires extraction into a dedicated Spring `@Component` service agent in `com.reForm.backend.ai.agent`.

#### B. Architectural Requirements & Specifications
* **Role:** Configures and compiles persona definitions, system prompt templates, voice models, temperature, and target interview goals for voice/text fill agents.
* **Supported Operations:**
  * `configurePersona(UUID formId, String tone, String voiceName, Double temperature, String customInstructions)`: Compiles system prompt template and saves `FormAiAgentProfile`.
  * `updateConversationalBlockGoals(UUID formId, String blockId, List<String> goals)`: Updates target goal checklist inside `ConversationalBlock`.
  * `compileSystemPrompt(FormAiAgentProfile profile, Form form)`: Replaces template variables (`{{formTitle}}`, `{{persona}}`) with runtime values.
* **Inputs:** `formId`, `tone`, `voiceName` (e.g. "Puck", "Kore", "Fenrir"), `temperature` (0.0 - 1.0), `customInstructions`, target goal list.
* **Outputs:** Persisted `FormAiAgentProfile` entity, compiled system prompt string.
* **Triggers:** `ConfigureFillerPersonaToolHandler` in Mode 4, Builder persona configuration REST API in Mode 1/2.
* **State Persistence:** PostgreSQL `form_ai_agent_profiles` table, `ConversationalBlock` within `forms.blocks`.
* **Dependencies:** `FormAiAgentProfileRepository`, `FormRepository`.

#### C. Implementation Specification for Implementers
Create `@Component` `PersonaConfigAgent` in package `com.reForm.backend.ai.agent`:
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonaConfigAgent {
    private final FormAiAgentProfileRepository profileRepository;
    private final FormRepository formRepository;

    @Transactional
    public FormAiAgentProfile configurePersona(UUID formId, PersonaConfigDto dto) {
        // Compiles system prompt template with variable substitution
        // Updates FormAiAgentProfile entity in PostgreSQL
    }
}
```

---

### 3.4 DocumentIngestionAgent

#### A. Current Implementation State
* **File Location:** Stubs present in `GenerateContentFromDocToolHandler.java`, `AnalyzeUploadedFileToolHandler.java`, and `ExtractStructuredDataToolHandler.java`.
* **Status:** Requires consolidation into a standalone Spring `@Component` service agent in `com.reForm.backend.ai.agent`.

#### B. Architectural Requirements & Specifications
* **Role:** Ingests, parses, and extracts structured knowledge from reference documents (PDFs, Word documents, Excel spreadsheets, images/diagrams) uploaded by form builders or form fillers.
* **Supported Operations:**
  * `ingestDocument(byte[] fileBytes, String filename, String contentType)`: Parses file content using Apache Tika / Gemini Vision and extracts raw text/metadata.
  * `extractStructuredSchema(String fileId, List<String> targetFields)`: Extracts key-value fields (e.g. candidate name, skills, dates) for form auto-population.
  * `generateQuizFromDocument(String fileId, int questionCount)`: Generates quiz/interview questions from document text.
* **Inputs:** File byte arrays, InputStream, file metadata, MIME type (`application/pdf`, `image/png`, `text/csv`).
* **Outputs:** `DocumentAnalysisResult` (raw text, semantic summary, extracted key-value pairs, generated question DTOs).
* **Triggers:** Co-builder document upload REST endpoint, tool calls (`analyzeUploadedFile`, `extractStructuredData`, `generateContentFromDocument`).
* **State Persistence:** Storage service (local disk / S3 / GCP Storage), metadata in PostgreSQL.
* **Dependencies:** Apache Tika / PDFBox, Gemini Vision API, `IAiModelProviderStrategy`.

#### C. Implementation Specification for Implementers
Create `@Component` `DocumentIngestionAgent` in package `com.reForm.backend.ai.agent`:
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionAgent {
    private final IAiModelProviderStrategy visionModelStrategy;

    public DocumentAnalysisResult processDocument(UUID fileId, ExtractionMode mode) {
        // Parses file bytes, extracts structured text/vision analysis
        // Returns clean semantic DTO for SchemaGeneratorAgent or LayoutAgent
    }
}
```

---

## 4. Decoupling, Threading & Spring Integration

To satisfy Requirement **R1** (Architecture & Decoupling) and ensure zero blocking calls on the main WebSockets audio thread:

1. **Single Responsibility Principle (SRP):**
   * Each agent is defined as an isolated `@Component` spring bean.
   * Tool handlers (`ModifyFormLayoutToolHandler`, `ConfigureFillerPersonaToolHandler`) do **not** perform heavy database or LLM work directly. They publish Spring events or delegate to `@Async` agents.

2. **Non-Blocking Real-Time Threading:**
   * WebSocket handler (`VoiceSyncWSHandler`) processes ~50 binary PCM frames/sec on Netty/Tomcat I/O threads.
   * Long-running operations (layout persistence, document ingestion, LLM schema generation) run asynchronously on Spring's `@Async` thread pool (`TaskExecutor`).

```
[WebSocket Audio Thread] ---> Emits Tool Call ---> [Spring Event Bus]
                                                          |
                                                          v  (@Async)
                                                  [LayoutAgent Pool]
                                                          |
                                                          v
                                                  [PostgreSQL Save]
```

---

## 5. Actionable Gap Analysis & Roadmap for Implementers

| Agent | Current Code Status | Missing Requirements | Recommended Action |
| :--- | :--- | :--- | :--- |
| **LayoutAgent** | Existing `@Component` in `ai/agent` | Missing deletion, reordering, full block type coverage, and WSS canvas update event. | Expand `createBlockFromIntent` mapping, add delete/reorder handlers, publish `FormCanvasUpdatedEvent`. |
| **SchemaGeneratorAgent** | Missing as dedicated agent class | Currently scattered across `BlockFactory` and tool handlers. | Create `SchemaGeneratorAgent.java` in `ai/agent`, inject Gemini Flash strategy for prompt-to-schema translation. |
| **PersonaConfigAgent** | Logic in tool handler | Tool handler directly touches repository instead of delegating to agent service. | Create `PersonaConfigAgent.java` in `ai/agent`, refactor `ConfigureFillerPersonaToolHandler` to call agent. |
| **DocumentIngestionAgent**| Stubs in file tool handlers | Mocked responses in file tool handlers without actual document parsing engine. | Create `DocumentIngestionAgent.java` in `ai/agent`, integrate document parsing (Tika/Gemini Vision). |

---

## 6. Conclusion

The Form Builder Agent Suite (R2 Requirements) forms the foundational intelligence layer for reForm's Form Builder Modes 1, 2, and 4. By formalizing `LayoutAgent`, `SchemaGeneratorAgent`, `PersonaConfigAgent`, and `DocumentIngestionAgent` as decoupled Spring `@Component` service agents communicating via async events, the platform maintains ultra-low latency on real-time voice WebSockets while delivering flexible AI-assisted form co-building.
