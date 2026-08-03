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
  isolation, no Gemini call involved. **Mode 2 now splits into two flows**, not one unified chat: a
  workspace-level stats/status chat (genuinely form-independent — deferred, not yet designed) and a
  create/update chat entered only by clicking "create form" or navigating to an existing one, bound
  to exactly one form for its whole lifetime. `ChatTurn`/`ChatSessionStore` still carry no `formId`
  field even for the second flow — not because the session is form-independent, but because
  `formId` is a per-request, security-relevant value (supplied on each call, validated against real
  ownership via `FormSecurity`/`@PreAuthorize`, mirroring `BuilderController`'s existing pattern),
  never something to trust just because it's part of a stored turn.
- `[PLANNED]` **`FormChatPromptBuilder`** (plain class, `ai.prompt` — not a formal port; one real
  implementation doesn't earn that ceremony) — produces the system instruction + conversation
  content from a `ChatTurn` history and the new message. `SessionContextService
  .compileSystemInstruction` delegates to it for the text-chat case. Testable with hand-built
  `ChatTurn` lists, no network call needed.
- `[PLANNED]` **`IAiChatClient`** (port, `ai.port`) + **`GeminiChatClient`** (implementation,
  `ai.client`) — formalized as a port from the start, unlike `FormChatPromptBuilder`, since
  multi-vendor scalability is an explicit priority for this piece. The implementation combines
  `Gemini35FlashModelStrategy`'s model config, `BlockSchemaGenerator`'s schema, and
  `FormChatPromptBuilder`'s output into one `WebClient` request; returns the raw response text.
  Requires verifying Gemini's actual REST request/response shape against its docs before writing
  this — not to be guessed.
- `[PLANNED]` **`AiChatService`** — orchestrates the above with the already-`[COMPLETED]`
  `AiResponseParser` → `AiBlockApplicationService` chain; decides create-vs-update based on
  whether a `formId` was supplied. Deliberately controller-independent (see
  `03_mode2_package_and_pattern_architecture.md` §3, and the "two entry points" note in the mental
  model doc).
- `[PLANNED]` **`ChatRequestDto` / `ChatResponseDto`** — the HTTP-facing request/response shapes.
- `[PLANNED]` **`AiChatController`** — thin HTTP layer (`POST /api/v1/ai/chat`), reusing
  `BuilderController`'s existing auth pattern (`X-Workspace-Id` header,
  `@AuthenticationPrincipal` for creator, a `@PreAuthorize` workspace-membership check).

## Session Identity for the Update Flow — Two Known Problems

Surfaced while designing the create/update flow's session handling; deliberately split into a
now-vs-later pair rather than solved together.

- `[PRIORITY — fix before/while building `AiChatController`]` **Problem 1: `sessionId` must not be
  randomly regenerated per visit.** If whatever calls `ChatSessionStore` mints a fresh random UUID
  every time a user opens Form A's builder, each visit gets its own disconnected Redis key — the
  conversation "about Form A" is unambiguous from navigation, but the *history* silently doesn't
  come back, since nothing ties the new key to the old one. Not a storage problem — a database
  wouldn't fix this either, for the same reason (a fresh random ID has nothing to look itself up
  by regardless of where it's stored). The fix is a generation-strategy decision, not a storage
  change: for the update flow, derive `sessionId` deterministically from `formId` (e.g.
  `sessionId = formId.toString()`) instead of generating an unrelated random one, so returning to
  Form A's builder always resolves to the same key. No `ChatSessionStore` changes needed — this is
  entirely `AiChatController`/`AiChatService`'s responsibility.
  **Checked for the obvious follow-on security question — is `formId` safe to reuse as
  `sessionId`, given `formId` could be known/guessed?** Yes, because it's never used alone:
  `ChatSessionStore`'s Redis key is `chat:session:{userId}:{sessionId}` — `userId` (from the
  authenticated JWT, never client-supplied) is the other half, so knowing a `formId` only ever
  resolves to *your own* conversation about that form, never another user's. And the route
  accepting `formId` in the first place must be gated by `FormSecurity.isMember`/`@PreAuthorize`,
  same as every other form-scoped endpoint (`BuilderController`) — merely knowing a `formId` isn't
  enough to reach the endpoint at all without workspace membership. Side effect worth noting: two
  different members editing the same form (Alice, Bob) each derive their own key
  (`...{alice}:{formA}` vs `...{bob}:{formA}`) — independent per-person conversations about the
  same form, never a shared thread.
- `[DEFERRED — after Phase 2's complete demo]` **Problem 2: Redis's 2-hour TTL means history is
  lost if the user returns after longer than that**, even with Problem 1 correctly fixed (correct
  key, but the key has already expired). This is the actual case for persisting session
  existence/turn content in a real database — but explicitly not being solved now. Ship a complete
  Mode 2 demo first; revisit this (and any other problems surfaced by that demo) afterward, not
  preemptively.

## Explicitly Deferred (Phase 3+, not scoped here)

- `FormAgentConfig` entity (per-form AI customization) — see `02_mode2_problem_landscape.md` §E.
  **Update:** this now exists in code as `FormAiAgentProfile` (`form.entity`, real repository) —
  still deferred for Mode 2's own use, but no longer purely hypothetical; integrating with it
  later is integrating with a real entity, not building one from scratch.
- BYOK API-key resolution — see §F. **Update:** `SessionContextService.resolveApiKey` was fully
  *removed* (not merely left as a stub) in pth's latest merge — confirms BYOK is genuinely
  unimplemented anywhere in the codebase right now, not just deferred in this plan.
- SSE streaming of the conversational reply.
- Guardrail (content moderation), Billing (credit metering), Cache agents from the teammate's
  production agent catalog (doc 20).
- The event-driven entry point (`FormLayoutModificationEvent` → Mode 2 pipeline) implied by doc
  20's "Layout Agent." **Update:** `FormLayoutModificationEvent` now exists (`ai.event` package)
  and is actively published by `GeminiLiveVoiceAdapter` on a `modifyFormLayout` tool call —
  confirmed via `grep` that **no listener consumes it anywhere yet**. The `@EventListener`
  (`LayoutAgent`) itself is still not being built as part of this plan, but `AiChatService`'s
  method signature should be designed with this real, currently-orphaned event's payload shape in
  mind (`formId` + free-text `userIntent` in), so a future thin `@EventListener` wrapper is a
  natural fit rather than a rework.

## Suggested Build Order

Matches the dependency chain above, bottom-up: `ChatTurn` → `ChatSessionStore` →
`FormChatPromptBuilder` → `IAiChatClient` + `GeminiChatClient` → `AiChatService` → DTOs +
`AiChatController`. Each
step should be verified in relative isolation (real objects over mocks where feasible, a
throwaway experiment for anything uncertain about Gemini's actual behavior) before the next step
wires it in — the same discipline used throughout Phase 1.
