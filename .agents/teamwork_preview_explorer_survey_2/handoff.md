# Handoff Report — Step 0 Survey (Part 2: Form Builder Agent Suite - R2 Requirements)

**Agent:** teamwork_preview_explorer_survey_2  
**Date:** 2026-08-05  
**Working Directory:** `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_2`  
**Report Path:** `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_2/survey_report_2.md`

---

## 1. Observation

1. **`ORIGINAL_REQUEST.md` (Lines 14-15)**:
   - "R2. Form Builder Agent Suite: Implement agents supporting Form Builders across Modes 1, 2, and 4 (LayoutAgent, SchemaGeneratorAgent, PersonaConfigAgent, DocumentIngestionAgent)."
2. **Existing Agent Code (`backend/src/main/java/com/reForm/backend/ai/agent/`)**:
   - `LayoutAgent.java` (Lines 37-39): Annotated with `@Component`, listens to `@EventListener` for `FormLayoutModificationEvent` running asynchronously (`@Async`). It receives `userIntent` (e.g., `"ADD_CONTACT_SECTION"`), creates concrete `AbstractBlock` subclasses (`EmailStaticBlock`, `PhoneStaticBlock`, `ConversationalBlock`), sets `sortOrder`, and persists to `Form.blocks` in PostgreSQL.
   - `SchemaGeneratorAgent`, `PersonaConfigAgent`, and `DocumentIngestionAgent` do **not** exist as dedicated classes in `com.reForm.backend.ai.agent`.
3. **Existing Tool Handlers (`backend/src/main/java/com/reForm/backend/ai/tool/handler/`)**:
   - `ModifyFormLayoutToolHandler.java` (Lines 43): Dispatches `FormLayoutModificationEvent` via `ApplicationEventPublisher`.
   - `ConfigureFillerPersonaToolHandler.java` (Lines 52-73): Handles persona configuration directly in tool handler by querying/updating `FormAiAgentProfileRepository`.
   - `GenerateContentFromDocToolHandler.java`, `AnalyzeUploadedFileToolHandler.java`, `ExtractStructuredDataToolHandler.java`: Contain tool call handlers with mocked file/doc analysis responses.
4. **Form & Agent Domain Entities (`backend/src/main/java/com/reForm/backend/form/entity/`)**:
   - `Form.java` (Lines 29-32): `@Convert(converter = AbstractBlockConverter.class)` `@JdbcTypeCode(SqlTypes.JSON)` `@Column(columnDefinition = "jsonb") private List<AbstractBlock> blocks = new ArrayList<>();`
   - `FormAiAgentProfile.java` (Lines 35-55): JPA entity storing `modelKey` ("GEMINI_3_1_LIVE"), `systemPromptTemplate`, `voiceName` ("Puck", "Kore"), `temperature`, `byokApiKeyEncrypted`.
5. **Mode Architecture (`backend/knowledge/pth/week2/10_ai_feature_spectrum_and_4mode_strategy.md`)**:
   - Mode 1: Manual Form Builder over HTTP REST ($0.00/min).
   - Mode 2: Text Chat Co-Builder over HTTP REST / SSE using Gemini 3.6 Flash (~0.2¢/min).
   - Mode 4: Voice Native Live Co-Builder over WSS using Gemini 3.1 Flash Live (~2.7¢/min).

---

## 2. Logic Chain

1. **Requirement R2 Analysis**: R2 requires decoupled Spring `@Component` service agents for Form Builder processes across Modes 1, 2, and 4.
2. **Current State Assessment**:
   - `LayoutAgent` is already instantiated as a Spring `@Component` and uses an event-driven `@Async` `@EventListener` pattern triggered by `ModifyFormLayoutToolHandler`.
   - `PersonaConfigAgent` logic is currently embedded inside `ConfigureFillerPersonaToolHandler.java` rather than being an independent `@Component` agent service.
   - `DocumentIngestionAgent` logic is partially stubbed across file tool handlers (`GenerateContentFromDocToolHandler`, `AnalyzeUploadedFileToolHandler`, `ExtractStructuredDataToolHandler`).
   - `SchemaGeneratorAgent` logic is distributed across DTO mappers and `BlockFactory` but lacks a dedicated agent service class.
3. **Form Builder Modes Strategy (Modes 1, 2, 4)**:
   - **Mode 1**: Uses client-side React drag-and-drop canvas over REST APIs (`BuilderController`). Zero LLM cost. Uses standard block DTOs (`FormCreateDto`, `FormUpdateDto`).
   - **Mode 2**: Text-only LLM chat co-builder over REST/SSE. Uses Gemini 3.6 Flash for structured JSON outputs. Invokes `SchemaGeneratorAgent` and `LayoutAgent` for layout creation/mutation.
   - **Mode 4**: Bi-directional voice co-builder over raw WebSockets (`VoiceSyncWSHandler`). Uses Gemini 3.1 Flash Live. Dispatches function calls (`modifyFormLayout`, `configureFillerPersona`) to event bus so `@Async` background workers update PostgreSQL without blocking the 50 frames/sec PCM audio stream.
4. **Architectural Isolation**: All 4 agents must be defined as decoupled `@Component` beans in `com.reForm.backend.ai.agent` with event-driven or interface-driven invocation to prevent audio stream blocking (R1 compliance).

---

## 3. Caveats

- **Mode 3 (Cascaded Voice)**: Focused primarily on candidate form filling rather than form building, though Form Builder modes interact via standard block-level mode assignments.
- **Frontend Real-Time Synchronization**: Backend dispatches `FormLayoutModificationEvent` and persists to PostgreSQL, but real-time frontend canvas re-rendering requires a WebSocket event broadcast (`CANVAS_UPDATED`) to be wired to client sessions.

---

## 4. Conclusion

The Form Builder Agent Suite (R2 Requirements) comprises `LayoutAgent` (existing, needs layout intent expansion), `SchemaGeneratorAgent` (needs implementation), `PersonaConfigAgent` (needs extraction from tool handler), and `DocumentIngestionAgent` (needs consolidation from file stubs). All four agents operate across Form Builder Modes 1, 2, and 4 using Spring `@Component` and `@Async` event-driven communication to ensure zero blocking on WebSocket audio threads.

Exhaustive survey findings have been documented in `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_2/survey_report_2.md`.

---

## 5. Verification Method

1. **Inspect Survey Report File**:
   - View `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_2/survey_report_2.md` to confirm all sections (Executive Summary, Modes 1/2/4 Deep Dive, R2 Agents Analysis, Decoupling & Threading, Gap Analysis) are fully populated.
2. **Inspect Existing Agent File**:
   - `view_file` on `backend/src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java` to verify `@Async`, `@EventListener`, `@Component` annotations and event handling logic.
3. **Inspect Tool Handlers & Entities**:
   - `view_file` on `ConfigureFillerPersonaToolHandler.java`, `ModifyFormLayoutToolHandler.java`, `FormAiAgentProfile.java`, `Form.java`.
4. **Invalidation Condition**:
   - If any agent in R2 is not specified with concrete input/output DTOs, trigger mechanics, state persistence models, and Spring `@Component` integration specifications, this handoff is invalid.
