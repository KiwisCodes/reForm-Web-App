# The Blocks-Array Schema Gap, and Verifying anyOf Against Real Gemini

Written in the style of `06_progress_...md` — what was found, the questions raised along the way,
how each was resolved, and what it unblocks.

## The Gap

While scoping `AiChatService` (next in the build order after `ChatTurn` / `ChatSessionStore` /
`FormChatPromptBuilder` / `IAiChatClient` + `GeminiChatClient`), a real blocker turned up before
any of that service's code could be written.

`AiBlockApplicationService.updateFormFromAiBlocks` takes `List<AiBlockDto>` — a form turn can
touch *several* blocks of *different* types at once (e.g. add a name field and an email field in
one message). `FormChatPromptBuilder`'s system instruction already promises Gemini exactly this:
"decide the complete revised set of blocks."

But `BlockSchemaGenerator` had no method that could constrain a response to that shape. Every
existing method constrained exactly **one** block type per call:

- `generateStaticSchemaFor(staticType, dialect)` — one static leaf.
- `generateConversationalSchema(dialect)` — the one conversational type.
- `generateAllStaticSchemas(dialect)` — a `Map<String, Map<String,Object>>` of individual schemas,
  keyed by type name; not itself a valid `responseSchema` (no top-level `type`, nothing telling
  Gemini "pick one of these").

Nothing existed to say "the response is an array, and each element may be *any one* of these known
block shapes." Without that, no amount of model intelligence could produce a mixed-type response —
`responseSchema` isn't advisory.

## Question 1 — "Isn't Gemini supposed to know how to combine blocks on its own? Isn't building this schema hardcoding?"

`responseSchema` constrains Gemini via constrained decoding — a hard restriction on which tokens
the decoder is *allowed* to emit at each step, not a hint the model interprets and can reason its
way around. If the only schema ever given to Gemini describes one block shape, the model is
*structurally incapable* of emitting a different shape in that response, no matter how well it
understands the user's intent — there's no token path to it.

This isn't hardcoding in the problematic sense (enumerating specific *combinations*, e.g. "if
name+email, use schema X"). It's one generic structural rule — "array of any-one-of-these-shapes"
— built once and reused for every possible combination and count of blocks, the same way `oneOf`
in ordinary JSON Schema doesn't need to know in advance which branch will be chosen.

## Question 2 — "Doesn't `generateAllStaticSchemas` already handle this?"

No — it solves a narrower, different problem. It returns a `Map<String, Map<String,Object>>`, a
lookup table of individual per-type schemas (built via reflection over `StaticBlock`'s
`@JsonSubTypes`, so a new leaf type needs zero changes there — that's the problem it actually
solves: not duplicating field definitions per type). Handed to Gemini as-is, it isn't a valid
schema at all — no top-level `type`, no combinator. It's the raw ingredients; nothing wraps them
into the "pick one of these" structure `responseSchema` needs.

## Question 3 — "Does Gemini's `responseSchema` even support `oneOf`/`anyOf`?"

Not something to guess — checked against Google's own current docs
([Structured outputs | Gemini API](https://ai.google.dev/gemini-api/docs/structured-output)):

- `anyOf` **is** supported (with a real example: a field that can be either `SpamDetails` or
  `NotSpamDetails`). `oneOf` is notably **absent** from the supported keyword list.
- The docs never explicitly confirm whether an **array's `items`** can itself be an `anyOf` across
  different object shapes — the documented example is a top-level field, not an array element.
  Docs separately warn "very large or deeply nested schemas may be rejected."

That gap between "the general mechanism exists" and "this specific combination is proven" was
exactly what needed empirical verification before writing any real code around it.

## Empirical Verification

Two real, unmocked calls to Gemini (via `GeminiChatClient`, no `.block()`-and-hope):

1. **Hand-built 2-type schema** (`{"type": "ARRAY", "items": {"anyOf": [shortTextSchema,
   emailSchema]}}`), message "Add a name field and an email field" — returned exactly:
   ```json
   [
     {"label":"Full Name","staticType":"SHORT_TEXT","placeholder":"John Doe","required":true},
     {"label":"Email Address","staticType":"EMAIL","placeholder":"john.doe@example.com","required":true}
   ]
   ```
   Confirms array-items-as-`anyOf`-across-different-object-shapes genuinely works — not just
   standard JSON Schema composition theory carrying over untested.

2. **The real production method** (`generateBlocksArraySchema`, all 12 variants — 11 static leaves
   + conversational — not just the 2-type proxy), same message — returned the same correct 2-block
   result. Confirms the "very large schema may be rejected" caveat doesn't bite at this size, and
   validates the actual artifact, not a stand-in for it.

Both preserved as real tests: `ArrayAnyOfSchemaExplorationTest` (gated on `GEMINI_API_KEY` being
set, so it skips cleanly rather than fails when the key isn't in the environment).

## Resolution

`BlockSchemaGenerator.generateBlocksArraySchema(dialect)`:

```java
public Map<String, Object> generateBlocksArraySchema(SchemaDialect dialect) {
    List<Object> variants = new ArrayList<>(generateAllStaticSchemas(dialect).values());
    variants.add(generateConversationalSchema(dialect));

    return Map.of(
            "type", dialect.arrayType(),
            "items", Map.of("anyOf", variants)
    );
}
```

Composed entirely from the existing per-type methods — no re-walking `@JsonSubTypes`, no second
hardcoded type list. A new block type is picked up automatically the same way
`generateAllStaticSchemas`/`BlockFactory`/`AbstractBlockDeserializer` already are. Covered by a
structural unit test (`BlockSchemaGeneratorTest`, no network call — asserts the shape and the
12-variant count) plus the live verification above.

## What This Unblocks

`AiChatService` can now request a real, schema-constrained "complete revised set of blocks"
response from Gemini via `IAiChatClient.generateFormResponse(systemInstruction, contents,
schemaGenerator.generateBlocksArraySchema(SchemaDialect.GEMINI))`, feeding the result straight into
`AiResponseParser.parseBlocks` → `AiBlockApplicationService.updateFormFromAiBlocks`. The
create-a-whole-form case (`AiFormDto` — title + blocks) still needs its own decision, deferred to
when `AiChatService` itself is built.
