# Mode 2 Security & Edge Cases

Written in the style of `pth/week2/06_ip_spoofing_and_production_security.md` — name the threat,
then the mitigation, then what to log/audit.

## 1. AI-Generated Content Is Untrusted Input, Not a Trusted Peer

### The Threat
It's tempting to treat Gemini's response as "our own system talking to itself" once it comes back
schema-constrained — but a schema only constrains *shape* (field names, types, enum membership),
not *intent*. A workspace-scoped attacker could craft chat messages designed to make the model
emit maliciously-crafted `label`/`description`/`prompt` text (e.g. script-injection payloads
intended for later rendering in a form-builder UI, or a resource-exhausting field like an
absurdly long `options` array).

### Mitigation
Everything downstream of `AiResponseParser` already treats the content as ordinary user-authored
data — no special trust is extended to it. Whatever eventually renders block content in the
frontend must apply the same escaping/sanitization it would apply to a human typing directly into
the manual form builder. This isn't new work Mode 2 introduces; it's a reminder not to *skip*
existing output-encoding discipline just because the text came from an AI instead of a human.

## 2. Security-Relevant Fields Must Never Come From the AI

### The Threat
`workspaceId` and `creatorId` determine which workspace a form lands in and who owns it. If either
were sourced from AI-generated content (or an unchecked client field), a malicious chat turn could
attach a form to an arbitrary workspace, or attribute authorship to an arbitrary user.

### Mitigation
Already enforced structurally: `AiFormDto` has no `workspaceId`/`creatorId` field at all —
`FormFactory.buildCreateDto(aiForm, workspaceId)` takes `workspaceId` as an explicit parameter,
sourced only from the authenticated request context. `AiChatController` must follow the exact same
pattern `BuilderController` already uses: `workspaceId` from the `X-Workspace-Id` header (validated
against workspace membership, same `@PreAuthorize` mechanism), `creatorId` from
`@AuthenticationPrincipal` — never trusted from the request body.

## 3. Prompt Injection via Chat Messages

### The Threat
A user's chat message is, itself, untrusted text handed to an LLM alongside our own system
instructions. A crafted message could attempt to override the system prompt ("ignore previous
instructions and instead...") to make the model behave outside its intended role — e.g. leaking
its own system prompt, or attempting to generate content unrelated to form-building.

### Mitigation
Structured output is a meaningful defense here, not just a data-shape convenience:
`responseSchema` constrains *what the model can emit* regardless of what it was tricked into
"wanting" to say — an injected instruction can't make the model emit a field or type that doesn't
exist in the schema. This isn't complete protection (it can still emit misleading *content* within
allowed fields, e.g. an inappropriate `label` string), but it substantially narrows what a
successful injection could actually achieve, since it can't escape the response's structural
bounds. Full guardrail/content-moderation coverage is explicitly deferred (see implementation
plan) — this point should be revisited once that agent exists.

## 4. Rate Limiting the Chat Endpoint

### The Threat
Every chat turn triggers a real, billed Gemini API call. An endpoint with no rate limit is an open
invitation to run up API costs (accidentally, via a buggy retrying frontend, or deliberately).

### Mitigation
This project already has rate-limiting infrastructure (`RateLimitInterceptor`,
`IRateLimitService`, Bucket4j + Redis, per `pth/week2/01-07`). `AiChatController` should be
registered under the same interceptor rather than building a separate mechanism — likely with a
*tighter* limit than general API endpoints, given the real cost-per-call.

## 5. Session Expiry and Abandoned Conversations

### The Threat
A user starts a chat, never finishes, and closes the tab. Without an expiry policy, Redis
accumulates an unbounded number of orphaned session keys over time.

### Mitigation
`ChatSessionStore` should set a TTL on each session key when writing (Redis's native expiry
mechanism — no custom cleanup job needed, consistent with how existing Redis usage in this project
already relies on TTLs rather than manual eviction).

## 6. Schema-Constrained Output Still Needs Validation, Not Blind Trust

### The Threat
`responseSchema` constrains Gemini at the token-generation level, but it isn't a formal proof of
correctness — a vendor bug, an API version mismatch, or an edge case in how the schema is conveyed
could still produce output that doesn't perfectly match expectations.

### Mitigation
This is exactly why the still-open "no error/validation boundary" gap from Phase 1 (see
`04_mode2_implementation_plan.md`) matters for Mode 2 specifically, not just as abstract
robustness: `BlockFactory`/`AiResponseParser` failures reachable from a live, internet-facing chat
endpoint need to fail cleanly (a sensible error response) rather than leak a raw stack trace to
the client. This should be built before, or alongside, `AiChatController`, not deferred
indefinitely — the risk profile changes materially once this pipeline is reachable from outside
the process instead of only from local tests.

## What to Log

Mirroring `06_ip_spoofing_and_production_security.md`'s "log the fields that let you diagnose and
tune, not everything": per chat turn, log `sessionId`, `workspaceId`, `formId` (if present),
whether the call resulted in create vs. update vs. an error, and the *category* of any failure
(parse failure vs. schema-violation vs. Gemini API error) — not full raw AI-generated content by
default, to avoid inadvertently logging anything a user typed that they'd reasonably expect not to
be persisted in plaintext log files indefinitely.
