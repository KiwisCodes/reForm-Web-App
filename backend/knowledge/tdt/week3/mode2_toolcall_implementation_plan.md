# Mode 2 Tool-Call Integration — Implementation Plan

Concrete file inventory for Mode 2 text chat tool calling. Mode 2 uses its own isolated `Mode2ToolRegistry`
strategy pattern. Six tools are reused from Mode 3/4 not via an adapter, but by extracting each one's business
logic into a new shared `@Service` — see [[mode2_toolcall_architecture]]'s "Shared Service Extraction" section for
why a synthetic-`WebSocketSession` adapter was tried first and dropped. `endSession`, `requestFileUpload`, and
`modifyFormLayout` are deliberately excluded — confirmed by reading each handler, none has a business-logic core
separable from live socket I/O or Mode 4's async event model.

---

## Phase 1 — Mode 2 Tool Registry Foundation

The strategy pattern foundation for Mode 2 tool dispatching. Fully independent of `IToolCallHandler` /
`ToolCallRegistry` — no shared interface, no adapter, no synthetic transport objects.

| File | New / Modify | Purpose | Status |
| :--- | :--- | :--- | :--- |
| `ai/tool/port/ChatToolExecutionContext.java` | 🆕 New | Plain Java record (`formId`, `workspaceId`, `userId`). No `getAttribute(String key)` method — nothing calls it now that `IMode2ToolHandler` takes the concrete record directly, so it was dropped from the original sketch as dead weight. | ✅ Done |
| `ai/tool/port/IMode2ToolHandler.java` | 🆕 New | Strategy interface for Mode 2 tool handlers (`getFunctionName()`, `execute(ChatToolExecutionContext context, JsonNode functionCall, String callId)`). | ✅ Done |
| `ai/tool/registry/Mode2ToolRegistry.java` | 🆕 New | Strategy Pattern Registry mirroring `ToolCallRegistry.java`. Stores and dispatches Mode 2 tool calls via plain constructor injection of `List<IMode2ToolHandler>` — same as `ToolCallRegistry` does for `IToolCallHandler`. | ✅ Done |

**`Mode2ToolRegistryConfig.java` was dropped from this phase** — `ToolCallRegistry` itself proves Spring can
auto-collect every `IMode2ToolHandler` bean via plain constructor injection with no separate `@Configuration`
class, so adding one for Mode 2 would be an unnecessary extra file.

---

## Phase 1a — Extract shared services out of reused Mode 3/4 handlers

Each row: a new plain `@Service` holding the extracted business logic, and the existing Mode 3/4 handler edited to
delegate to it. The edit is additive and behavior-preserving — same `execute(WebSocketSession, ...)` signature,
same bean, same `IToolCallHandler`, no call-site changes anywhere else (`ToolCallRegistry`, `GeminiLiveVoiceAdapter`,
`CascadedVoiceAdapter` are all untouched by this phase).

| New service file | Modified Mode 3/4 handler | What moved | Status |
| :--- | :--- | :--- | :--- |
| `ai/service/FormPublishService.java` | `ai/tool/handler/builder/PublishFormToolHandler.java` | Status → `PUBLISHED`, slug/URL generation, `formRepository.save(...)`. Signature is `publishForm(UUID formId)` only — the original handler never actually used `workspaceId`, so the service doesn't carry an unused parameter. | ✅ Done |
| `ai/service/DocumentContentGenerationService.java` | `ai/tool/handler/builder/GenerateContentFromDocToolHandler.java` | The (currently mock) content-generation body — trivial move. | ✅ Done |
| `ai/service/FileAnalysisService.java` | `ai/tool/handler/file/AnalyzeUploadedFileToolHandler.java` | The (currently mock) analysis body — trivial move. | ✅ Done |
| `ai/service/StructuredDataExtractionService.java` | `ai/tool/handler/file/ExtractStructuredDataToolHandler.java` | The (currently mock) extraction body — trivial move. | ✅ Done |
| `ai/service/FillerPersonaService.java` | `ai/tool/handler/builder/ConfigureFillerPersonaToolHandler.java` | `FormAiAgentProfile` upsert (tone/voice/temperature/compiled prompt). | ⏸ **On hold** |
| `ai/service/DocumentSearchService.java` | `ai/tool/handler/universal/SearchUserDocumentToolHandler.java` | The (currently mock) RAG search body. | ⏸ **On hold** |

**Why the last two are on hold:** `origin/pth/week4/mode4_continue` — unmerged, past the `pth/week4/mode3` tip
this work is based on — already modifies these exact two files: it adds `targetBlockId`-scoped block-level
`ConversationalBlock` persona overrides to `ConfigureFillerPersonaToolHandler` (a second write path beyond the
form-level `FormAiAgentProfile` write this plan originally scoped), and `ragDocumentIds`-scoped filtering to
`SearchUserDocumentToolHandler`. Extracting a service now would miss both params and create a real merge conflict
— not just textual, semantic: whoever merges it would need to fold pth's new logic into the extracted service too.
The two handlers were reverted with `git restore` back to their exact original committed content (verified, not
hand-edited back), and the two now-orphaned service files were deleted. Redo this row once that branch lands.

---

## Phase 2 — Generalize the REST client to carry `tools` + multi-turn `contents`

| File | New / Modify | Purpose |
| :--- | :--- | :--- |
| `ai/service/GeminiFlashRestService.java` | ✏️ Modify | Add `generateContent(String systemPrompt, List<Map<String,Object>> contents, List<Map<String,Object>> tools)`. Refactor existing `callGemini(systemPrompt, userTranscript, tools)` into a one-line wrapper that builds a single-turn `contents` and delegates — Mode 3 keeps working unchanged. |

---

## Phase 3 — Mode 2's own tool catalog & handlers

`Mode2ToolDeclarationBuilder` declares exactly the 8 tools from the catalog in [[mode2_toolcall_architecture]] —
`endSession`, `requestFileUpload`, and `modifyFormLayout` are never declared to Mode 2's Gemini requests at all.

| File | New / Modify | Purpose |
| :--- | :--- | :--- |
| `ai/prompt/FunctionDeclarationSupport.java` | 🆕 New | Shared static helper producing `{name, description, parameters:{type:OBJECT, properties, required}}` — lifted out of `SessionContextService.buildFunctionDeclaration` so it's shared cleanly. |
| `ai/prompt/Mode2ToolDeclarationBuilder.java` | 🆕 New | Builds the 8-tool declaration list for Mode 2's REST request. `proposeFormBlocks`'s `blocks` parameter reuses `BlockSchemaGenerator.generateBlocksArraySchema(SchemaDialect.GEMINI)` directly. |
| `ai/tool/handler/chat/ProposeFormBlocksToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `proposeFormBlocks`. Converts `args.blocks` (`JsonNode`) → `List<AiBlockDto>` via `ObjectMapper.convertValue` and calls `AiBlockApplicationService.updateFormFromAiBlocks(...)`. Native to Mode 2 — no backing service. |
| `ai/tool/handler/chat/FinalizeFormToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `finalizeForm`. Pure "conversation concluded" signal, returns `{status:"SUCCESS"}`. Native to Mode 2 — no backing service. |
| `ai/tool/handler/chat/Mode2PublishFormToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `publishForm`. Calls `FormPublishService.publishForm(context.formId())`. |
| `ai/tool/handler/chat/Mode2GenerateContentFromDocumentToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `generateContentFromDocument`. Calls `DocumentContentGenerationService.generateContent(...)`. |
| `ai/tool/handler/chat/Mode2AnalyzeUploadedFileToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `analyzeUploadedFile`. Calls `FileAnalysisService.analyze(fileId, analysisType)`. |
| `ai/tool/handler/chat/Mode2ExtractStructuredDataToolHandler.java` | 🆕 New | `IMode2ToolHandler` for `extractStructuredData`. Calls `StructuredDataExtractionService.extract(fileId, fieldsToExtract)`. |
| `ai/tool/handler/chat/Mode2ConfigureFillerPersonaToolHandler.java` | ⏸ Blocked | `IMode2ToolHandler` for `configureFillerPersona`. Blocked on Phase 1a's `FillerPersonaService`, which is on hold. |
| `ai/tool/handler/chat/Mode2SearchUserDocumentToolHandler.java` | ⏸ Blocked | `IMode2ToolHandler` for `searchUserDocument`. Blocked on Phase 1a's `DocumentSearchService`, which is on hold. |

---

## Phase 4 — Restore cross-turn state memory

| File | New / Modify | Purpose |
| :--- | :--- | :--- |
| `ai/prompt/FormChatPromptBuilder.java` | ✏️ Modify | Add `buildCurrentStateContext(FormResponseDto form)` (or fold into `buildConversationContents`) — injects the form's current blocks as a synthetic context turn each request, replacing the implicit memory the old raw-JSON `MODEL` history turn used to provide. |

---

## Phase 5 — Orchestration loop (the actual feature)

| File | New / Modify | Purpose |
| :--- | :--- | :--- |
| `ai/service/AiChatService.java` | ✏️ Modify | Rewrite `continueFormEditSession`: re-fetch current form state → build `contents` (history + state context + new message) → bounded loop (≤4 iterations) calling `GeminiFlashRestService.generateContent(...)` with `Mode2ToolDeclarationBuilder`'s tools → on `functionCall` parts, dispatch via `Mode2ToolRegistry.executeTool(chatContext, ...)` and append `functionCall`/`functionResponse` turns → on a plain `text` part, stop and treat it as the reply. Persists only the original user message + final text reply via `ChatSessionStore`. |

---

## Phase 6 — Response contract

| File | New / Modify | Purpose |
| :--- | :--- | :--- |
| `ai/dto/ChatResponseDto.java` | ✏️ Modify | `record ChatResponseDto(FormResponseDto form)` → `record ChatResponseDto(String message, FormResponseDto form, boolean formUpdated)`. `AiChatController` needs no changes — it already just relays whatever `AiChatService` returns. |

---

## Out of scope (explicitly untouched by this plan)

- `CascadedVoiceAdapter.java`, `GeminiLiveVoiceAdapter.java`, `IToolCallHandler.java`, `ToolCallRegistry.java`, and
  the 12 Mode 3/4 handlers with no Mode 2 equivalent (`ModifyFormLayoutToolHandler`, `EndSessionToolHandler`,
  `RequestFileUploadToolHandler`, the audio/filler/ui handlers) — genuinely untouched, no edits at all.
- `ConfigureFillerPersonaToolHandler.java` and `SearchUserDocumentToolHandler.java` were touched and then reverted
  — see Phase 1a's "on hold" rows. Currently untouched, matching their state on `pth/week4/mode3`.
- The 4 Mode 3/4 handlers actually extracted (Phase 1a) get a small additive edit each — **not** "100% untouched"
  the way an earlier version of this doc claimed. Every one of those edits preserves the existing
  `execute(WebSocketSession, ...)` signature and changes nothing any other caller depends on.
- `AiChatService.startFormCreationSession` and `ai/client/GeminiChatClient.java` / `ai/port/IAiChatClient.java` — the create-a-new-form flow stays on the schema-forced single-shot path for now. Revisit separately once the update flow above is proven out.
- Frontend chat UI — consuming the new `message`/`formUpdated` fields on `ChatResponseDto` isn't covered here.

---

## File count summary

| Bucket | Done | On hold / blocked | Not yet started | Planned total |
| :--- | :---: | :---: | :---: | :---: |
| 🆕 New | 7 | 2 (`FillerPersonaService`, `DocumentSearchService`) | 10 (Phase 3, 2 of which are blocked on the on-hold services) | 17 |
| ✏️ Modified | 4 | 2 (`ConfigureFillerPersonaToolHandler`, `SearchUserDocumentToolHandler` — reverted to original) | 4 (Phase 2/4/5/6) | 10 |

**27 files planned total** (down from an earlier 30 — `Mode2ToolRegistryConfig.java` was dropped as unnecessary,
see Phase 1). **11 files touched so far, 7 of them landed and compiling clean, 4 reverted back to original.** Full
compile (`./mvnw compile`) verified clean both right after the Phase 1a extraction and again right after the
revert. Nothing in this plan is committed to git yet.
