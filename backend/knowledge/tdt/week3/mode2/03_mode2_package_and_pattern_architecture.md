# Mode 2 Package & Pattern Architecture

Written in the style of `pth/week2/12_ai_design_patterns_and_package_architecture.md` — package
tree, then the design patterns in play, then an Open/Closed extensibility test.

## 1. Module Structure

```
com.reForm.backend.ai
├── dto/            AiBlockDto, AiStaticBlockDto, AiConversationalBlockDto, AiFormDto   [existing]
├── factory/         BlockFactory, FormFactory                                          [existing]
├── parser/           AiResponseParser                                                   [existing]
├── schema/           BlockSchemaGenerator, SchemaDialect                                [existing]
├── service/
│   ├── AiBlockApplicationService                                                        [existing]
│   ├── SessionContextService, GeminiLiveVoiceAdapter                                    [pth, voice modes]
│   └── AiChatService                                                                     [NEW — Mode 2 orchestrator]
├── port/
│   ├── IAiModelProviderStrategy, IAiVoiceAdapter                                        [pth, existing]
│   └── IPromptBuilder                                                                    [NEW]
├── client/
│   └── GeminiChatClient                                                                  [NEW]
├── session/
│   └── ChatSessionStore, ChatTurn                                                        [NEW]
├── strategy/         Gemini31LiveModelStrategy, Gemini35FlashModelStrategy               [pth, existing]
├── config/           WebSocketConfig                                                      [pth, existing]
├── state/            SessionTracker                                                       [pth, existing]
└── websocket/        VoiceSyncWSHandler                                                    [pth, existing]
```

### Why This Structure

`ai.*` stays the shared home for anything AI-related that both `form.*` and (eventually)
`submission.*` need, without either of those importing from the other — the same rationale
`12_ai_design_patterns_and_package_architecture.md` gives for the package existing at all. Mode 2's
new pieces slot into the existing subpackage-per-responsibility convention rather than inventing a
parallel structure: `port/` for new interfaces (matching where `IAiModelProviderStrategy` already
lives), `service/` for the new orchestrator (alongside the existing session/voice services, even
though — worth noting explicitly — `AiChatService` is a *use-case/orchestration* layer calling into
`form.*`, while `SessionContextService`/`GeminiLiveVoiceAdapter` are lower-level
session/protocol-plumbing; same package, different responsibility tier, which is fine but worth
remembering if this package grows further). `client/` and `session/` are new subpackages
specifically because nothing existing already covers "make an outbound HTTP call to Gemini for
text" or "store/retrieve chat history" — reusing `service/` for those would blur what's
orchestration versus what's low-level plumbing.

## 2. The Design Patterns in Play

1. **Factory** (`BlockFactory`, `FormFactory`) — already established this session; converts typed
   AI DTOs into real domain objects, reusing `AbstractBlock`'s own dispatch instead of a
   hand-written switch per attribute.
2. **Strategy** (`IAiModelProviderStrategy` → `Gemini35FlashModelStrategy`) — reused, not
   reinvented, for Mode 2's model selection. Matches pth's existing pattern exactly: `AiChatService`
   asks for "the text-mode strategy" and never branches on model name itself.
3. **Port/Adapter** (new `IPromptBuilder` → `FormChatPromptBuilder`) — same shape as
   `IAiVoiceAdapter`'s bridge between `VoiceSyncWSHandler` and concrete voice vendors: an interface
   decouples "something needs a prompt built" from "here's exactly how one gets built," so a
   second implementation (e.g. a different prompting strategy per form category) could exist later
   without touching callers.
4. **Single Responsibility** — each new class does exactly one job: `ChatSessionStore` only
   persists/retrieves history, `GeminiChatClient` only makes the HTTP call, `AiChatService` only
   orchestrates, `AiChatController` only translates HTTP ↔ the orchestrator. None of these absorb
   a neighboring concern, mirroring the discipline already used for `BlockFactory` vs.
   `AbstractBlockDeserializer` vs. `AiResponseParser` in Phase 1.

## 3. Scalability & Modifiability Evaluation (Open/Closed Principle)

**A. Scalability.** Redis-backed sessions mean any number of server instances can serve any
request for any session — no per-instance state to worry about. `GeminiChatClient`'s `WebClient`
calls are non-blocking by nature (reactive stack, already present via `spring-boot-starter-webflux`),
so a slow Gemini response doesn't tie up a request-handling thread the way a blocking HTTP client
would.

**B. Modifiability.**

*Adding a new AI vendor (e.g. OpenAI) for Mode 2:* Create one new class implementing
`IAiModelProviderStrategy` (mirroring `Gemini35FlashModelStrategy`), and — since
`BlockSchemaGenerator` already supports a `JSON_SCHEMA` dialect alongside `GEMINI` — no changes
needed to schema generation. `GeminiChatClient` would need generalizing into an
`IAiChatClient` port with a vendor-specific implementation, the same Bridge/Adapter shape
`IAiVoiceAdapter` already uses for voice vendors. Zero changes required to `AiResponseParser`,
`BlockFactory`, `FormFactory`, or `AiBlockApplicationService` — none of them know or care which
vendor produced the JSON they're processing.

*Adding a new block type (e.g. a new static leaf, or a second conversational leaf):* Per
`custom-abstractblock-deserializer-static-vs-conversational.md` and
`block-schema-generator-lifecycle-and-schema-dialect.md`, this only ever requires one new class
plus one registry entry — `BlockFactory`, `AbstractBlockDeserializer`, and `BlockSchemaGenerator`
all pick it up automatically via reflection, with zero changes to Mode 2's new chatbot layer at
all.

*Adding SSE later:* Since `AiChatService` is controller-independent, a future
`AiChatController` variant returning `Flux<ServerSentEvent<...>>` instead of a plain response body
could reuse the exact same orchestration logic — only the controller's return type and the
`GeminiChatClient`'s call style would need to change.
