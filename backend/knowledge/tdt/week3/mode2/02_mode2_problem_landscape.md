# Mode 2 Problem Landscape: Evaluating the Alternatives

Written in the style of `pth/week2/02_rate_limiting_problem_landscape.md` — a comparative analysis
matrix per decision, followed by "The reForm Decision" and a short justification.

## A. Conversation Memory: Where Does Session State Live?

### A. Comparative Analysis Matrix

| Approach | Survives server restart | Works across multiple instances | Frontend complexity | New infra needed |
|---|---|---|---|---|
| In-memory `Map` on the server | No | No | Low | None |
| Client resends full history every request | Yes (client owns it) | Yes | Higher — client must track and grow a list every turn | None |
| Redis, keyed by session ID | Yes (with TTL) | Yes | Low — client only sends a session ID | None new — already running |

### B. The reForm Decision: Redis

Redis is already running in this project (rate limiting, form caching), so this adds no new
operational surface. It keeps the client thin (send a session ID, not a growing message list) and
naturally supports horizontal scaling, which an in-memory map cannot. The one real cost is needing
a TTL/expiry policy for abandoned sessions — a Redis key expiring naturally handles that with no
custom cleanup job needed.

## B. Transport: REST, REST+SSE, or Raw WebSocket?

### A. Comparative Analysis Matrix

| Approach | Matches text's turn-based nature | Supports gradual/"typing" reply | Supports mid-response interruption | Implementation complexity |
|---|---|---|---|---|
| Plain REST (request → wait → full response) | Yes | No | No | Low |
| REST + SSE | Yes | Yes (server pushes incrementally) | No (client can't send mid-stream) | Medium |
| Raw WebSocket (as Modes 3/4 use for voice) | Not particularly — designed for continuous bidirectional streams | Yes | Yes | High — connection lifecycle, reconnection, barge-in-style handling |

### B. The reForm Decision: Plain REST for v1, SSE as a Later Upgrade

Mode 2's own documented transport is "HTTP REST / Server-Sent Events (SSE)" — never raw
WebSocket, which is reserved for the voice modes where continuous, interruptible audio genuinely
needs full-duplex. Since our structured block output can't be meaningfully shown half-formed
anyway (see mental model §5), plain REST is sufficient for v1; SSE remains a legitimate later
upgrade specifically for the conversational reply text, not the block data.

## C. Calling Gemini: `WebClient` or an Official SDK?

### A. Comparative Analysis Matrix

| Approach | New dependency | Type safety | Fits existing project style | Maintenance if Gemini's API shape changes |
|---|---|---|---|---|
| Spring `WebClient` + hand-built JSON | None (`spring-boot-starter-webflux` already present) | Low — plain `Map`/`String` request-building | Matches how `BlockSchemaGenerator` already builds plain `Map` shapes | We own and must update the shape ourselves |
| Official Google Gemini Java SDK | Yes | Higher — typed request/response objects | New pattern, not used elsewhere in this codebase yet | SDK maintainers absorb API-shape churn |

### B. The reForm Decision: `WebClient`

No new dependency, and it's consistent with this project's existing "plain `Map`-shaped payload"
style. The real tradeoff, named plainly: we're responsible for getting Gemini's exact request/response
JSON shape right ourselves — the same category of risk as the `AbstractBlockDeserializer`
registration bug found this session, just pointed at an external API's contract instead of
Jackson's internals. This needs verifying against Gemini's actual API docs before `GeminiChatClient`
is built, not guessed.

## D. Prompt Construction: Extend `SessionContextService` Directly, or a New Class?

### A. Comparative Analysis Matrix

| Approach | Matches existing convention | Schema-injection / form-context support | Risk of touching a teammate's actively-evolving file |
|---|---|---|---|
| Add methods directly onto `SessionContextService` | Reuses what's there as-is | Would need retrofitting into a voice-shaped class | Higher — that class's shape changed substantially in the last merge alone |
| New `FormChatPromptBuilder`, a plain concrete class (not a formal port) | `SessionContextService` delegates to it for the text-chat case | Purpose-built for it | None — new file, own package |

### B. The reForm Decision: New `FormChatPromptBuilder`, Plain Concrete Class

Not a formal `IAiModelProviderStrategy`/`IAiVoiceAdapter`-style port — with exactly one real
implementation, the interface wouldn't earn its keep yet, and simplicity wins here.
`SessionContextService.compileSystemInstruction` delegates to it for the text-chat case, so
there's a single source of truth for "how do prompts get built" rather than two competing
stories, without requiring Mode 2's logic to live inside voice mode's own class.

## E. `FormAgentConfig`: Build Now, or Defer?

### A. Comparative Analysis Matrix

| Approach | Speed to a working v1 | Per-form AI customization | New entity/migration/repository needed now |
|---|---|---|---|
| Build a minimal `FormAgentConfig` now | Slower | Available from day one | Yes |
| Defer — one fixed default prompt for all forms | Faster | Not available until built later | No |

### B. The reForm Decision: Defer

Every form gets identical AI behavior until this is built — an accepted, explicit limitation, not
an oversight. Building the entity now would be a detour into `form.entity` territory before the
core chat loop even functions.

## F. BYOK: Build Now, or Defer?

### A. Comparative Analysis Matrix

| Approach | Security/encryption work needed now | Billing implications resolved | Speed to v1 |
|---|---|---|---|
| Implement `resolveApiKey` (AES-256-GCM decrypt, platform fallback) now | Yes | Yes | Slower |
| One shared platform key from `application.yml` | No | Deferred | Faster |

### B. The reForm Decision: Defer

Same reasoning as `FormAgentConfig` — BYOK is a full feature (encryption, key storage, billing
branching) that doesn't block a first working chat loop.
