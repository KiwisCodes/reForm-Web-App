# Mode 2 Implementation Plan

Written in the style of `pth/week2/08_websocket_session_implementation_plan.md` — a component
checklist tagged `[COMPLETED]` / `[IN IMPLEMENTATION]` / `[PLANNED]` / `[NEXT FOCUS]`.

## Phase 1 — LLM Output Processing (the pipeline Gemini's response feeds into)

- `[COMPLETED]` `AiBlockDto` / `AiStaticBlockDto` / `AiConversationalBlockDto` / `AiFormDto` —
  polymorphic DTO layer for what Gemini returns.
- `[COMPLETED]` `BlockFactory` / `FormFactory` — convert typed DTOs into real domain objects.
- `[COMPLETED]` `AbstractBlockDeserializer` (+ `AbstractBlockJacksonConfig` module registration) —
  STATIC/CONVERSATIONAL dispatch, bug-fixed this session (see
  `custom-abstractblock-deserializer-static-vs-conversational.md`).
- `[COMPLETED]` `AiResponseParser` — raw JSON string → typed DTOs.
- `[COMPLETED]` `BlockSchemaGenerator` / `SchemaDialect` — reflects real domain classes into a
  vendor-parameterized JSON Schema, ready to constrain Gemini's `responseSchema`. *(Sits on the
  request-shaping side, not the output-processing side — see note in
  `llm-output-processing-lifecycle.md`.)*
- `[COMPLETED]` `AiBlockApplicationService` — `createFormFromAiBlocks` / `updateFormFromAiBlocks`,
  delegating to the existing `IFormBuilderService`.

Known gaps carried forward from Phase 1 (not blocking Mode 2, but not forgotten):
error/validation boundary around `BlockFactory` failures; dedicated tests for `BlockFactory` and
`AbstractBlockDeserializer` specifically; an end-to-end test reaching a real database.

## Phase 2 — Mode 2 Chatbot (this plan's scope)

- `[NEXT FOCUS]` **`ChatTurn`** — the smallest unit: one message (`role`, `content`) in a
  conversation. No dependencies; needed before session storage can be built.
- `[PLANNED]` **`ChatSessionStore`** — Redis-backed `getHistory(sessionId)` /
  `appendTurn(sessionId, ChatTurn)`. Depends only on `ChatTurn`; testable against Redis in
  isolation, no Gemini call involved.
- `[PLANNED]` **`IPromptBuilder`** (port, `ai.port`) + **`FormChatPromptBuilder`** (implementation) —
  produces the system instruction + conversation content from a `ChatTurn` history and the new
  message. Testable with hand-built `ChatTurn` lists, no network call needed.
- `[PLANNED]` **`GeminiChatClient`** — combines `Gemini35FlashModelStrategy`'s model config,
  `BlockSchemaGenerator`'s schema, and `IPromptBuilder`'s output into one `WebClient` request;
  returns the raw response text. Requires verifying Gemini's actual REST request/response shape
  against its docs before writing this — not to be guessed.
- `[PLANNED]` **`AiChatService`** — orchestrates the above with the already-`[COMPLETED]`
  `AiResponseParser` → `AiBlockApplicationService` chain; decides create-vs-update based on
  whether a `formId` was supplied. Deliberately controller-independent (see
  `03_mode2_package_and_pattern_architecture.md` §3, and the "two entry points" note in the mental
  model doc).
- `[PLANNED]` **`ChatRequestDto` / `ChatResponseDto`** — the HTTP-facing request/response shapes.
- `[PLANNED]` **`AiChatController`** — thin HTTP layer (`POST /api/v1/ai/chat`), reusing
  `BuilderController`'s existing auth pattern (`X-Workspace-Id` header,
  `@AuthenticationPrincipal` for creator, a `@PreAuthorize` workspace-membership check).

## Explicitly Deferred (Phase 3+, not scoped here)

- `FormAgentConfig` entity (per-form AI customization) — see `02_mode2_problem_landscape.md` §E.
- BYOK API-key resolution (`SessionContextService.resolveApiKey`) — see §F.
- SSE streaming of the conversational reply.
- Guardrail (content moderation), Billing (credit metering), Cache agents from the teammate's
  production agent catalog (doc 20).
- The event-driven entry point (`FormLayoutModificationEvent` → Mode 2 pipeline) implied by doc
  20's "Layout Agent" — the orchestration layer is *structured* to allow this later (see pattern
  doc §3), but the actual `@EventListener` is not being built now.

## Suggested Build Order

Matches the dependency chain above, bottom-up: `ChatTurn` → `ChatSessionStore` → `IPromptBuilder` +
`FormChatPromptBuilder` → `GeminiChatClient` → `AiChatService` → DTOs + `AiChatController`. Each
step should be verified in relative isolation (real objects over mocks where feasible, a
throwaway experiment for anything uncertain about Gemini's actual behavior) before the next step
wires it in — the same discipline used throughout Phase 1.
