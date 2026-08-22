# Mode 2 Tool-Calling Architecture (Design)

Mode 2 today has no branch point: every turn is forced through `BlockSchemaGenerator.generateBlocksArraySchema()`
as a top-level `responseSchema`, so Gemini has no way to just talk — it must always emit a complete block
array, every turn, whether the user asked a question or not. This doc designs the tool-calling redesign
that gives it a real branch: **talk when it needs more info, call a tool when it's ready to act.**

This is a design doc, grounded directly in the current code (`AiChatService`, `GeminiFlashRestService`, `BlockSchemaGenerator`). Two hard constraints shape everything below:

1. **`responseSchema` and `tools` are mutually exclusive** in a single Gemini `generateContent` call.
   `GeminiChatClient.generateFormResponse()` sends `responseMimeType: application/json` + `responseSchema` unconditionally — that whole mechanism is replaced for tool-calling turns, not layered on top of it.
2. **Mode 2 has no push channel.** Unlike Mode 4 (persistent Gemini Live socket) or Mode 3 (persistent browser socket), Mode 2 is one HTTP POST → one HTTP response. Any "round trip back into Gemini" happens *inside* the same request, before the controller returns.

---

## Mode 2 Tool Catalog

Mode 2 text chat utilizes a dedicated, strategy-pattern-backed tool set (`IMode2ToolHandler` / `Mode2ToolRegistry`).
Every tool falls into one of three buckets — native to Mode 2, reused from Mode 3/4 via a shared service, or
excluded because it has no separable business-logic core. See "Shared Service Extraction" below for why the
middle bucket replaced the earlier synthetic-`WebSocketSession` adapter idea.

### Native to Mode 2
| Tool | Called when | Args | Effect |
| :--- | :--- | :--- | :--- |
| **`proposeFormBlocks`** | Gemini has enough information to draft or revise the form's structure | `blocks`: schema produced by `BlockSchemaGenerator.generateBlocksArraySchema()`. Also `summary: STRING` — a human-readable one-line description of what changed. | Converts `args.blocks` → `List<AiBlockDto>` → `AiBlockApplicationService.updateFormFromAiBlocks(...)`. Writes to Postgres immediately during the HTTP turn. |
| **`finalizeForm`** | User confirms they are satisfied with the form structure | none | Deliberate "we're done drafting" signal. Returns `{status:"SUCCESS"}`. |

### Reused from Mode 3/4 via a shared service
Each row's business logic is extracted out of the existing Mode 3/4 handler into a new plain `@Service`; both the
original Mode 3/4 handler and a new thin Mode 2 handler call the same service. See "Shared Service Extraction."

| Tool | Args | Backing service | Original handler (now a thin delegator) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **`publishForm`** | none | `FormPublishService.publishForm(formId)` | `PublishFormToolHandler.java` | ✅ Extracted |
| **`generateContentFromDocument`** | `fileId: STRING`, `contentType: STRING`, `count: INTEGER` | `DocumentContentGenerationService.generateContent(fileId, contentType, count)` | `GenerateContentFromDocToolHandler.java` | ✅ Extracted |
| **`analyzeUploadedFile`** | `fileId: STRING`, `analysisType: STRING` | `FileAnalysisService.analyze(fileId, analysisType)` | `AnalyzeUploadedFileToolHandler.java` | ✅ Extracted |
| **`extractStructuredData`** | `fileId: STRING`, `fieldsToExtract: STRING` | `StructuredDataExtractionService.extract(fileId, fieldsToExtract)` | `ExtractStructuredDataToolHandler.java` | ✅ Extracted |
| **`configureFillerPersona`** | `tone: STRING`, `voiceName: STRING`, `customInstructions: STRING`, `temperature: NUMBER` | `FillerPersonaService` — **on hold** | `ConfigureFillerPersonaToolHandler.java` | ⏸ On hold |
| **`searchUserDocument`** | `query: STRING` | `DocumentSearchService` — **on hold** | `SearchUserDocumentToolHandler.java` | ⏸ On hold |

`generateContentFromDocument`, `analyzeUploadedFile`, and `extractStructuredData` are all still mock/stub
implementations today (no Vision/OCR or doc-parsing wiring yet) — extraction for these three was close to free,
relocating a stub body, not untangling real logic.

**`configureFillerPersona` and `searchUserDocument` are on hold, not yet extracted.** `origin/pth/week4/mode4_continue`
— an unmerged branch past the `pth/week4/mode3` tip this work is based on — already modifies both of these exact
files: it adds `targetBlockId`-scoped block-level `ConversationalBlock` persona overrides to
`ConfigureFillerPersonaToolHandler` (a second write path beyond the form-level `FormAiAgentProfile` this doc
originally scoped), and `ragDocumentIds`-scoped search filtering to `SearchUserDocumentToolHandler`. Extracting a
service now would miss both of those params entirely and create a real merge conflict, not just a textual one —
whoever merges it would need to fold pth's new logic into the extracted service too. Deferred until that branch
lands and the extraction can be redone against the current logic.

### Excluded — no separable business-logic core

Confirmed by reading each handler directly, not assumed:

| Tool | Why it can't be reused this way |
| :--- | :--- |
| **`endSession`** | `EndSessionToolHandler` sends a live WS message, then spawns a virtual thread that sleeps 2s and closes two real sockets (`geminiSession`, `clientSession`). The entire function *is* transport teardown — there's no business-logic core to extract, and Mode 2 has nothing to tear down. |
| **`requestFileUpload`** | `RequestFileUploadToolHandler` pushes a `FILE_UPLOAD_REQUESTED` event live through the socket (`safeClient.sendMessage(...)`) so the browser can render an upload zone mid-conversation. That's the same "no push channel" constraint from the top of this doc — Mode 2 has no live channel to push that event through mid-turn. |
| **`modifyFormLayout`** | (Already excluded in the original design, restated here for completeness.) `ModifyFormLayoutToolHandler` fires an async `FormLayoutModificationEvent` for `LayoutAgent` to interpret later, with `targetBlocks` left as an empty stub — it's fire-and-forget by design, incompatible with Mode 2's synchronous request/response turn. `proposeFormBlocks` is Mode 2's synchronous equivalent instead. |

---

## Strategy Pattern Architecture: `Mode2ToolRegistry` + Shared Service Extraction

Mode 2 gets its own Strategy Pattern (`IMode2ToolHandler` / `Mode2ToolRegistry`), independent of `IToolCallHandler` /
`ToolCallRegistry`. Reuse of existing Mode 3/4 handlers happens by extracting their business logic into a plain
`@Service`, not by faking a `WebSocketSession` — see "Why not a synthetic-session adapter" below for why that
idea was dropped.

### 1. `ChatToolExecutionContext`
A transport-neutral Java record wrapping stateless HTTP request metadata:
```java
public record ChatToolExecutionContext(UUID formId, UUID workspaceId, UUID userId) {
    public Object getAttribute(String key) { ... }
}
```

### 2. `IMode2ToolHandler`
Strategy interface for Mode 2 tool handlers:
```java
public interface IMode2ToolHandler {
    String getFunctionName();
    Map<String, Object> execute(ChatToolExecutionContext context, JsonNode functionCall, String callId);
}
```

### 3. Shared Service Extraction (replaces the earlier `Mode2ToolAdapter` idea)

For each reused tool, the existing Mode 3/4 handler's business logic moves into a new plain `@Service`. The
original handler becomes a thin delegator — same `execute(WebSocketSession, ...)` signature, same bean, same
`IToolCallHandler` — just calling the service instead of doing the work inline. A new Mode 2 handler calls the
*same* service through `ChatToolExecutionContext` instead.

```java
// New — shared business logic, no transport type anywhere in its signature
@Service
@RequiredArgsConstructor
public class FormPublishService {
    private final FormRepository formRepository;

    public String publishForm(UUID formId, UUID workspaceId) {
        // pure business logic: transition status to PUBLISHED, generate slug, return public URL
        return publicUrl;
    }
}

// Modified — PublishFormToolHandler.java (Mode 3/4), same signature, body now delegates
@Component
@RequiredArgsConstructor
public class PublishFormToolHandler implements IToolCallHandler {
    private final FormPublishService publishService;

    @Override
    public String getFunctionName() { return "publishForm"; }

    @Override
    public Map<String, Object> execute(WebSocketSession session, JsonNode functionCall, String callId) {
        UUID formId = UUID.fromString((String) session.getAttributes().get("formId"));
        UUID workspaceId = UUID.fromString((String) session.getAttributes().get("workspaceId"));
        String url = publishService.publishForm(formId, workspaceId);
        return Map.of("id", callId, "name", getFunctionName(),
                       "response", Map.of("result", Map.of("status", "SUCCESS", "publicUrl", url)));
    }
}

// New — Mode2PublishFormToolHandler.java, same service, Mode 2's own context type
@Component
@RequiredArgsConstructor
public class Mode2PublishFormToolHandler implements IMode2ToolHandler {
    private final FormPublishService publishService;

    @Override
    public String getFunctionName() { return "publishForm"; }

    @Override
    public Map<String, Object> execute(ChatToolExecutionContext context, JsonNode functionCall, String callId) {
        String url = publishService.publishForm(context.formId(), context.workspaceId());
        return Map.of("id", callId, "name", getFunctionName(),
                       "response", Map.of("result", Map.of("status", "SUCCESS", "publicUrl", url)));
    }
}
```

The same three-way split (new service, thin-delegator existing handler, new `Mode2*ToolHandler`) applies to
`configureFillerPersona`, `searchUserDocument`, `generateContentFromDocument`, `analyzeUploadedFile`, and
`extractStructuredData` — six services, six modified Mode 3/4 files, six new Mode 2 handler files, listed in full
in [[mode2_toolcall_implementation_plan]].

**Why not a synthetic-session adapter:** the earlier draft of this doc wrapped `ChatToolExecutionContext` into a
fake `WebSocketSession` and called the existing handler unchanged, to avoid touching Mode 3/4 files at all. Reading
the actual handlers surfaced the problem directly: `WebSocketSession` is a broad interface (`sendMessage`, `close`,
`getId`, `isOpen`, `getAttributes`, ...), and nothing would stop a future edit to a reused handler from calling one
of those on the synthetic object — silently NPE'ing or no-op'ing instead of failing to compile. Service extraction
trades "zero Mode 3/4 files touched" for "every touch is additive, behavior-preserving, and compiler-checked" — a
better trade, and it's also what surfaced `endSession`/`requestFileUpload` as genuinely non-reusable rather than
silently wrapping them into something that would misbehave at runtime.

### 4. `Mode2ToolRegistry` & `Mode2ToolRegistryConfig`
Spring auto-wires all `IMode2ToolHandler` beans — both Mode 2-native (`proposeFormBlocks`, `finalizeForm`) and
service-backed (`Mode2PublishFormToolHandler` and its five siblings) — into `Mode2ToolRegistry` at startup, same
Strategy Registry shape as `ToolCallRegistry`:

```java
@Service
public class Mode2ToolRegistry {
    private final Map<String, IMode2ToolHandler> handlerMap;

    public Map<String, Object> executeTool(ChatToolExecutionContext context, JsonNode functionCall, String callId, String functionName) {
        IMode2ToolHandler handler = handlerMap.get(functionName);
        if (handler != null) {
            return handler.execute(context, functionCall, callId);
        }
        return Map.of("id", callId, "name", functionName, "response", Map.of("result", Map.of("status", "SUCCESS")));
    }
}
```

---

## Bounded Orchestration Loop in `AiChatService`

`AiChatService.continueFormEditSession` becomes a bounded loop (capped at ≤4 iterations):

```
contents = promptBuilder.buildConversationContents(history, currentBlocksSnapshot, message)
loop (max 4):
    response = geminiFlashRestService.generateContent(systemInstruction, contents, mode2Tools)
    parts = response.candidates[0].content.parts
    if any part has functionCall:
        result = mode2ToolRegistry.executeTool(chatContext, functionCall, callId, name)
        contents += { role: "model", parts: [{functionCall}] }
        contents += { role: "function", parts: [{functionResponse: {name, response: result}}] }
        continue loop
    else:
        replyText = part.text
        break loop
```

Only the **original user message** and the **final text reply** get persisted via `ChatSessionStore.appendTurn(...)`. Intra-request tool turns stay ephemeral. Cross-request state memory comes from re-fetching the current form blocks snapshot each turn.

---

## Response Contract

`ChatResponseDto` is expanded to carry conversational text + updated form state + updated flag:

```java
public record ChatResponseDto(String message, FormResponseDto form, boolean formUpdated) {}
```

`formUpdated` lets the frontend distinguish "just talked, don't touch canvas" from "blocks changed, refresh UI canvas".
