# Mode 2 Mental Model: Building a Text-Chat Form Co-Builder

Written in the same style as `pth/week2/01_rate_limiting_mental_model.md` and
`09_websocket_mental_model.md` — a chain of questions a naive learner would actually ask, each
escalating from the last, ending in a full recap tree.

## 1. Sub-Problem: The Root Problem

### ❓ Question:
*"I want a user to describe a form in plain English, chat back and forth with an AI, and have a
real form show up in my database. Where do I even start?"*

### ⚠️ The Conflict:
The obvious naive approach: have the frontend call Gemini's API directly from the browser. This
fails immediately for a boring but critical reason — it would require shipping a Gemini API key
inside client-side JavaScript, where anyone can extract it from the network tab and use it on our
dime. It also gives us zero control: nothing stops the browser from asking Gemini for anything at
all, unconstrained by our schema, our security rules, or our data model.

### 💡 The Solution:
The backend must be the only thing that ever talks to Gemini. The browser talks to *our* server;
our server talks to Gemini; our server applies whatever comes back. Every question below is really
just "what does that middle box need to do?"

## 2. Sub-Problem: Memory Across Messages

### ❓ Question:
*"A chat is more than one message — the user says 'add a name field,' then later 'now add an
email field too.' Does my backend need to remember the earlier messages?"*

### ⚠️ The Conflict:
HTTP is stateless by design. Each request is handled independently — the server has no built-in
memory connecting one request to the next, even from the same browser tab.

### 💡 The Solution:
Introduce an explicit "session": some server-side memory, keyed by a session ID the client sends
back on every request, holding the conversation so far.

## 3. Sub-Problem: Where Does That Memory Actually Live?

### ❓ Question:
*"Fine, a session. But where do I store it? A plain Java `Map` in memory?"*

### ⚠️ The Conflict:
An in-memory map dies the moment the server restarts, and if the app ever runs on more than one
server instance (horizontal scaling), a map living on Instance A is invisible to Instance B — a
user's next message could land on a different instance with no memory of them at all.

### 💡 The Solution:
Redis — already running in this project for rate-limiting and form caching. A shared,
external store every server instance can read from equally.

## 4. Sub-Problem: Does This Need to Feel Like a Live Phone Call?

### ❓ Question:
*"Modes 3 and 4 use raw WebSockets for voice, so it feels real-time. Shouldn't text chat use the
same thing, so it doesn't feel like slow, half-duplex, one-at-a-time messaging?"*

### ⚠️ The Conflict:
This conflates two different things: the *transport mechanism* (WebSocket vs. HTTP) and the
*nature of the medium* (voice vs. text). Voice is genuinely continuous and interruptible — a
person can talk over the AI mid-sentence, and the system has to detect that ("barge-in") and react
instantly, which needs a permanently open, bidirectional connection. Text isn't like that: even
between two humans, you type a complete message, send it, and wait for a reply. There's no
equivalent of "typing over" someone in a text medium the way there is in speech.

### 💡 The Solution:
A REST request/response *already* matches how text chat naturally behaves. Mode 2's own
documented transport is "HTTP REST / Server-Sent Events (SSE)" — never raw WebSocket — precisely
because text doesn't need full-duplex the way voice does.

## 5. Sub-Problem: But Shouldn't the Reply at Least Stream In Gradually?

### ❓ Question:
*"Ok, not a WebSocket. But could the AI's reply still appear gradually, like it's typing, instead
of a blank screen then a wall of text?"*

### ⚠️ The Conflict:
That's exactly what SSE is for — and it doesn't need full-duplex WebSockets, since the server only
ever pushes data one direction. But our actual output isn't free-form prose to watch appear
word-by-word — it's structured JSON representing form blocks, constrained by a schema. A
half-formed fragment like `{"staticType": "CH` isn't usable by anything downstream until the whole
object is complete and valid.

### 💡 The Solution:
Skip streaming for v1. Wait for Gemini's complete response, then process it in one shot. SSE
stays a legitimate future upgrade for the conversational reply text specifically — just not for the
structured block data, which can't be meaningfully shown half-finished anyway.

## 6. Sub-Problem: Stopping Gemini From Inventing Fields

### ❓ Question:
*"If I just ask Gemini to 'generate JSON for a form block,' what stops it from making up field
names or values that don't exist on my real Java classes?"*

### ⚠️ The Conflict:
Nothing, by default — an LLM will happily invent a plausible-looking field name from its training
data, and our domain classes (`ChoiceStaticBlock`, `ShortTextStaticBlock`, ...) have no built-in
way to reject that except quietly ignoring unknown properties.

### 💡 The Solution:
`BlockSchemaGenerator` reflects the *real* domain classes to build a JSON Schema — the exact field
names, types, and enum values that genuinely exist — and that schema is passed to Gemini as a
`responseSchema` constraint, so it's physically incapable of emitting anything else at the
token-generation level, not just politely asked to.

## 7. Sub-Problem: From Gemini's JSON to a Real Saved Form

### ❓ Question:
*"Say Gemini's response comes back clean and schema-valid. How does that actually become a row in
my database?"*

### 💡 The Solution:
This is the entire "processing engine" already built and verified this session:
`AiResponseParser` (raw JSON → `AiFormDto`/`List<AiBlockDto>`) → `FormFactory`/`BlockFactory`
(→ real `AbstractBlock` instances, via `AbstractBlockDeserializer`'s "STATIC"/"CONVERSATIONAL"
dispatch) → `AiBlockApplicationService` → the existing `IFormBuilderService` → Postgres. Nothing
about the chatbot changes this — it only has to *produce* well-formed input for it.

## 8. Sub-Problem: Whose Workspace Does This Form Belong To?

### ❓ Question:
*"The AI decides the form's title and blocks. Does it also decide which workspace the form goes
in, or who owns it?"*

### ⚠️ The Conflict:
If `workspaceId`/`creatorId` came from AI-generated content (or anything the client sent
unchecked), a malicious or malfunctioning chat turn could attach a form to an arbitrary workspace
or attribute it to an arbitrary user.

### 💡 The Solution:
`AiFormDto` was deliberately designed *without* a `workspaceId`/`creatorId` field.
`workspaceId`/`creatorId` must always come from the authenticated request context (header +
`@AuthenticationPrincipal`), the same boundary `BuilderController` already enforces for the manual
path — never from anything AI-generated.

## 9. Sub-Problem: Who Decides What to Actually Tell Gemini?

### ❓ Question:
*"What instructions do we even give Gemini — its role, its behavior, what it's allowed to do?"*

### ⚠️ The Conflict:
The teammate's existing pattern for this, `SessionContextService.compileSystemInstruction(userId,
role, formId)`, is a single concrete method (currently a `TODO` stub) reading from a
not-yet-built `FormAgentConfig` entity — designed around voice-mode's persona/goals concept, not
around schema-constrained structured output.

### 💡 The Solution:
Introduce `IPromptBuilder` as a new port, matching the existing port/strategy convention
(`IAiModelProviderStrategy`, `IAiVoiceAdapter`). `SessionContextService.compileSystemInstruction`
should eventually delegate to it for the text-chat case, rather than the codebase ending up with
two separate, competing "how do prompts get built" stories.

## 10. Sub-Problem: Which Model, and How Do We Actually Call It?

### ❓ Question:
*"Gemini isn't one thing — there are multiple model variants. Which one do we call, and with what
HTTP mechanism?"*

### 💡 The Solution:
`IAiModelProviderStrategy`/`Gemini35FlashModelStrategy` already exists and is already the
text-mode strategy (`getGenerationConfig()` currently returns
`{"responseModalities": ["TEXT"]}`) — reuse it rather than re-deriving model selection. For the
actual HTTP call, `spring-boot-starter-webflux` is already a dependency, so Spring's `WebClient`
can hit Gemini's REST endpoint directly with no new dependency needed.

## 11. Sub-Problem: Who Pays for the Call?

### ❓ Question:
*"Every Gemini call costs real money. Does every workspace need to bring their own API key from
day one?"*

### ⚠️ The Conflict:
BYOK (bring-your-own-key) is a real, planned feature — `SessionContextService.resolveApiKey`
exists as a stub already — but it's a whole security/encryption/billing feature of its own
(AES-256-GCM key decryption, platform-key fallback, credit tracking).

### 💡 The Solution:
Defer it. Use one shared platform Gemini key from `application.yml` for v1. Bolt on BYOK once the
core chat loop works.

## 12. Sub-Problem: Does Every Form Need Custom AI Behavior From Day One?

### ❓ Question:
*"Different forms might want the AI to behave differently — friendlier for a feedback form, more
formal for a job interview. Do we need that now?"*

### ⚠️ The Conflict:
Building this properly means creating `FormAgentConfig` (a new entity, migration, repository) —
and it doesn't exist in code yet at all.

### 💡 The Solution:
Defer it. One fixed default system prompt for every form in v1; add per-form customization once
it's actually needed.

## 13. Sub-Problem: Could Someone Abuse This?

### ❓ Question:
*"A public-facing chat endpoint calling a paid AI model — what could go wrong?"*

### 💡 The Solution:
Covered in depth in `05_mode2_security_and_edge_cases.md` — rate limiting the endpoint (reusing
existing infra), never trusting AI-generated content for security-relevant fields (§8 above),
and treating Gemini's output as untrusted input even though it's schema-constrained (defense in
depth, not defense-only).

## 14. Sub-Problem: Is There Only One Door Into This Logic?

### ❓ Question:
*"Is a direct chat endpoint the only way this ever gets triggered?"*

### ⚠️ The Conflict:
The teammate's documented "Layout Agent" concept describes this same block-generation logic being
triggered internally, via a Spring event (`FormLayoutModificationEvent`) fired when a *voice*
conversation's tool-calling decides a form layout change is needed — not just a browser hitting a
REST endpoint.

### 💡 The Solution:
Keep the core orchestration logic (parsing → building → applying) in a plain,
controller-independent Spring service, so it can be called from either a REST controller *or* a
future `@EventListener`, without rework.

---

## Complete Backtracking Mental Model Tree

```
Root: How do I let a user build a form by chatting with AI?
│
├─ 1. Who talks to Gemini? → Only the backend, never the browser directly.
│
├─ 2. Does the backend need memory across messages? → Yes, a "session."
│  └─ 3. Where does that memory live? → Redis (shared, survives restarts, works across instances).
│
├─ 4. Should this use a WebSocket like voice modes? → No — text is naturally turn-based (half-duplex
│  in spirit), voice needs full-duplex for interruption; REST/SSE matches text's real shape.
│  └─ 5. Should the reply at least stream in? → Not for v1 — structured JSON isn't useful half-formed.
│
├─ 6. What stops Gemini from inventing fields? → BlockSchemaGenerator's responseSchema, reflecting
│  the real domain classes.
│
├─ 7. How does valid JSON become a saved Form? → The existing processing pipeline (AiResponseParser
│  → FormFactory/BlockFactory → AbstractBlockDeserializer → IFormBuilderService → Postgres).
│
├─ 8. Who decides the workspace/owner? → Always the authenticated caller, never AI content
│  (AiFormDto has no workspaceId/creatorId field, by design).
│
├─ 9. Who decides what to tell Gemini? → New IPromptBuilder port, matching the existing
│  port/strategy convention; SessionContextService should delegate to it.
│
├─ 10. Which model, which HTTP mechanism? → Reuse IAiModelProviderStrategy/Gemini35FlashModelStrategy;
│  call via Spring WebClient (already a dependency).
│
├─ 11. Who pays? → One shared platform key for v1; BYOK deferred.
│
├─ 12. Does every form need custom AI behavior now? → No; FormAgentConfig deferred, one default
│  prompt for v1.
│
├─ 13. Could this be abused? → See the security/edge-case doc — rate limiting, untrusted-output
│  handling, the workspaceId/creatorId boundary.
│
└─ 14. Is there only one entry point? → Not necessarily — keep orchestration logic
   controller-independent so a future event-driven entry point (voice mode's Layout Agent) can
   reuse it.
```
