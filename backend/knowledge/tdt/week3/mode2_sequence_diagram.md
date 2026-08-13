# Mode 2 Sequence Diagram & Lifecycle Execution Flow

This document details the end-to-end data flow and lifecycle of **Mode 2 (AI Form Builder / Chat-to-Build)**.

---

## 📊 Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as Form Creator (Browser)
    participant Ctrl as AiChatController
    participant Sec as FormSecurity
    participant Svc as AiChatService
    participant Store as ChatSessionStore (Redis)
    participant Prompt as FormChatPromptBuilder
    participant Schema as BlockSchemaGenerator
    participant Gemini as GeminiChatClient
    participant Api as Google Gemini API
    participant Parser as AiResponseParser
    participant AppSvc as AiBlockApplicationService
    participant Factory as BlockFactory / Deserializer
    participant FormSvc as FormBuilderServiceImpl
    participant DB as PostgreSQL (forms table)

    User->>Ctrl: POST /api/v1/workspaces/{wId}/forms/{fId}/chat { "message": "Add a star rating for graphics" }
    Ctrl->>Sec: @PreAuthorize("@formSecurity.isMember(authentication, #fId)")
    Sec-->>Ctrl: Authorized
    Ctrl->>Svc: continueFormEditSession(auth, workspaceId, formId, message)

    Svc->>Sec: getCurrentUserId(authentication)
    Sec-->>Svc: userId

    Svc->>Store: getHistory(userId, sessionId=formId)
    Store-->>Svc: List<ChatTurn> (History from Redis)

    Svc->>Prompt: buildSystemInstruction()
    Prompt-->>Svc: System prompt string (loaded from mode2_system_instruction.txt)

    Svc->>Prompt: buildConversationContents(history, message)
    Prompt-->>Svc: List<Map> Gemini contents payload

    Svc->>Schema: generateBlocksArraySchema(GEMINI)
    Schema-->>Svc: Map (JSON Schema constraining block output)

    Svc->>Gemini: generateFormResponse(systemInstruction, contents, schema)
    Gemini->>Api: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent
    Api-->>Gemini: HTTP 200 OK (Raw JSON envelope)
    Gemini->>Gemini: extractResponseText(rawResponse)
    Gemini-->>Svc: Raw JSON string of block array

    Svc->>Parser: parseBlocks(rawResponseText)
    Parser-->>Svc: List<AiBlockDto> (AiStaticBlockDto / AiConversationalBlockDto)

    Svc->>AppSvc: updateFormFromAiBlocks(formId, workspaceId, aiBlocks)
    AppSvc->>Factory: build(aiBlockDto) for each DTO
    Factory->>Factory: AbstractBlockDeserializer dispatches STATIC/CONVERSATIONAL
    Factory-->>AppSvc: List<AbstractBlock> (ChoiceStaticBlock, StarRatingStaticBlock, etc.)

    AppSvc->>FormSvc: updateBlocks(FormUpdateDto)
    FormSvc->>DB: findByIdAndWorkspaceId(formId, workspaceId)
    DB-->>FormSvc: Form entity
    FormSvc->>FormSvc: form.setBlocks(newBlocks)
    FormSvc->>DB: save(form) -> AbstractBlockConverter converts blocks to JSONB
    FormSvc->>FormSvc: @CacheEvict("forms", key=slug) drops stale Redis form cache
    FormSvc-->>AppSvc: FormResponseDto

    Svc->>Store: appendTurn(userId, sessionId, USER turn)
    Svc->>Store: appendTurn(userId, sessionId, MODEL turn)

    Svc-->>Ctrl: FormResponseDto
    Ctrl-->>User: HTTP 200 OK (FormResponseDto with updated blocks)
```

---

## 🛠️ Class & Function Execution Roadmap

| Step | Class Name | Function / Method Name | Purpose |
| :--- | :--- | :--- | :--- |
| **1** | `AiChatController` | `continueFormEditSession(...)` | Receives HTTP POST request from frontend with `ChatRequestDto`. |
| **2** | `FormSecurity` | `isMember(...)` & `getCurrentUserId(...)` | Validates JWT & workspace membership; extracts authenticated `userId`. |
| **3** | `AiChatService` | `continueFormEditSession(...)` | Main orchestrator managing session, prompt assembly, API call, parsing, and persistence. |
| **4** | `ChatSessionStore` | `getHistory(userId, sessionId)` | Loads prior conversation turns from Redis (`chat:session:{userId}:{formId}`). |
| **5** | `FormChatPromptBuilder` | `buildSystemInstruction()` & `buildConversationContents(...)` | Reads `mode2_system_instruction.txt` and formats history + new prompt for Gemini. |
| **6** | `BlockSchemaGenerator` | `generateBlocksArraySchema(SchemaDialect.GEMINI)` | Reflects domain block classes into a JSON Schema to constrain Gemini's output tokens. |
| **7** | `GeminiChatClient` | `generateFormResponse(...)` | Calls Google Gemini REST API (`generateContent`) via `WebClient` and extracts response text. |
| **8** | `AiResponseParser` | `parseBlocks(rawResponse)` | Deserializes raw JSON block array string into `List<AiBlockDto>` polymorphic records. |
| **9** | `AiBlockApplicationService` | `updateFormFromAiBlocks(...)` | Converts DTOs to domain blocks and hands off to form builder service. |
| **10** | `BlockFactory` | `build(dto)` & `AbstractBlockDeserializer` | Instantiates leaf block entities (e.g. `StarRatingStaticBlock`, `ChoiceStaticBlock`). |
| **11** | `FormBuilderServiceImpl` | `updateBlocks(FormUpdateDto)` | Updates the `Form` entity in PostgreSQL and evicts stale Redis form cache via `@CacheEvict`. |
| **12** | `AbstractBlockConverter` | `convertToDatabaseColumn(...)` | JPA converter serializing `List<AbstractBlock>` into Postgres `jsonb` column. |
| **13** | `ChatSessionStore` | `appendTurn(...)` | Saves the new `USER` message and `MODEL` response into Redis for future turns. |
