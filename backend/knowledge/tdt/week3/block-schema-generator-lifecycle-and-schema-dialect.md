# `BlockSchemaGenerator` and `SchemaDialect`: Purpose, Lifecycle, and Cross-Vendor Support

## 1. Purpose

`BlockSchemaGenerator` reflects the *real* block domain classes (`ChoiceStaticBlock`,
`ConversationalBlock`, etc. — not the AI DTOs) to build a JSON-Schema-shaped `Map` per block type.
That schema is meant to constrain an LLM's structured output so it can only emit field names and
enum values that genuinely exist on the corresponding Java class — closing the "unknown attribute
silently dropped" risk flagged back when `AiStaticBlockDto`'s `additionalProperties` catch-all was
designed (see [reflection-based-ai-block-attribute-pipeline.md](./reflection-based-ai-block-attribute-pipeline.md)).

It currently has no production caller — the only thing exercising it is `BlockSchemaGeneratorTest`.
It's designed to eventually feed `IPromptBuilder`'s `responseSchema` configuration for the Gemini
(or other vendor) call.

## 2. Method-by-Method Lifecycle: `generateStaticSchemaFor("CHOICE", SchemaDialect.GEMINI)`

**Step 1 — `resolveStaticSubtype("CHOICE")`:**
- Reads `StaticBlock.class.getAnnotation(JsonSubTypes.class)` — the *same* 11-entry registry that
  already drives real deserialization via `AbstractBlockDeserializer`.
- Scans `.value()` for the entry whose `name()` equals `"CHOICE"`.
- Returns `t.value().asSubclass(AbstractBlock.class)` → `ChoiceStaticBlock.class`.

**Step 2 — `buildObjectSchema(ChoiceStaticBlock.class, "staticType", "CHOICE", GEMINI)`:**
- `properties = {}`, `required = ["label"]`.
- Discriminator given (`"staticType"`) → `properties.put("staticType", {"type":"STRING","enum":["CHOICE"]})`, `required` becomes `["label", "staticType"]`.

**Step 3 — `allFieldsIncludingInherited(ChoiceStaticBlock.class)` walks the hierarchy:**
- `ChoiceStaticBlock` → `selectionType, options, allowMultiSelect`
- `StaticBlock` → `placeholder`
- `AbstractBlock` → `id, label, description, isRequired, sortOrder`

**Step 4 — loop over every field (this is where `schemaPropertyName` and `schemaForField` fire):**

| field | `INTERNAL_FIELDS`? | schema key (`schemaPropertyName`) | schema value (`schemaForField`, GEMINI dialect) |
|---|---|---|---|
| `selectionType` | no | `selectionType` | `{"type":"STRING","enum":["RADIO_BUTTON","CHECKBOX","DROPDOWN"]}` |
| `options` | no | `options` | `{"type":"ARRAY","items":{"type":"STRING"}}` |
| `allowMultiSelect` | no | `allowMultiSelect` (boolean but doesn't start with `is`) | `{"type":"BOOLEAN"}` |
| `placeholder` | no | `placeholder` | `{"type":"STRING"}` |
| `id` | **yes** | — skipped — | — |
| `label` | no | `label` | `{"type":"STRING"}` |
| `description` | no | `description` | `{"type":"STRING"}` |
| `isRequired` | no | **`required`** (boolean, starts with `is`, next char uppercase → prefix stripped) | `{"type":"BOOLEAN"}` |
| `sortOrder` | **yes** | — skipped — | — |

**Step 5 — final result:**
```json
{
  "type": "OBJECT",
  "required": ["label", "staticType"],
  "properties": {
    "staticType": {"type": "STRING", "enum": ["CHOICE"]},
    "selectionType": {"type": "STRING", "enum": ["RADIO_BUTTON", "CHECKBOX", "DROPDOWN"]},
    "options": {"type": "ARRAY", "items": {"type": "STRING"}},
    "allowMultiSelect": {"type": "BOOLEAN"},
    "placeholder": {"type": "STRING"},
    "label": {"type": "STRING"},
    "description": {"type": "STRING"},
    "required": {"type": "BOOLEAN"}
  }
}
```

**The `isRequired` → `required` fix, precisely:** `field.getName()` on `AbstractBlock.isRequired`
literally returns `"isRequired"`. But Jackson never binds JSON against that string — Lombok gives a
boolean field already prefixed `is` a getter that keeps the prefix (`isRequired()`) but a *setter
that strips it* (`setRequired(boolean)`), and Jackson derives its property name from the setter:
`"required"`. Without `schemaPropertyName` correcting for this, the generated schema would tell an
LLM to emit `"isRequired"`, which `StaticBlock`'s `@JsonIgnoreProperties(ignoreUnknown = true)` would
then silently swallow — the block's required flag would always come back `false`, with no error
anywhere to reveal why.

**Other two public methods, same mechanism:**
- `generateAllStaticSchemas(dialect)` — loops the *same* registry from Step 1, calling
  `buildObjectSchema` once per entry (11 times total). No hardcoded list of type names anywhere —
  add a 12th static leaf (with its required `@JsonSubTypes.Type` entry, same one-time cost as
  today), and this method includes it automatically.
- `generateConversationalSchema(dialect)` — calls `buildObjectSchema(ConversationalBlock.class, null, null, dialect)` directly, skipping `resolveStaticSubtype` (no registry to look up — `ConversationalBlock` is still a single concrete class by deliberate choice, see [custom-abstractblock-deserializer-static-vs-conversational.md](./custom-abstractblock-deserializer-static-vs-conversational.md)). Because `discriminatorProperty` is `null`, no `staticType`/`conversationalType` key is ever added.

## 3. Cross-Vendor Problem: `SchemaDialect`

The first version hardcoded literal type strings — `"STRING"`, `"BOOLEAN"`, `"INTEGER"`, `"ARRAY"`,
`"OBJECT"` (uppercase) — which is specifically **Gemini's** `Type` enum convention, not standard
JSON Schema. OpenAI's structured outputs and Claude's tool-use schemas both expect standard,
lowercase JSON Schema types (`"string"`, `"boolean"`, ...). The *structural* shape (`type`,
`properties`, `required`, `enum`, `items` as key names) is the same across vendors — Gemini's own
docs describe its Schema object as a subset of the OpenAPI 3.0 Schema object — so the only real
difference is the literal token used per primitive type.

Since this app targets more than one model vendor (the teammate's `IAiModelProviderStrategy` /
`Gemini31LiveModelStrategy` / `Gemini35FlashModelStrategy`, merged from `main`, already handles
switching between *Gemini model variants* — but that's still all-Gemini, sharing Gemini's schema
dialect; a genuinely different vendor like OpenAI or Claude would reject the uppercase tokens
outright), the fix was to isolate that one difference into `SchemaDialect`:

```java
public enum SchemaDialect {
    GEMINI("STRING", "BOOLEAN", "INTEGER", "ARRAY", "OBJECT"),
    JSON_SCHEMA("string", "boolean", "integer", "array", "object");

    private final String stringType, booleanType, integerType, arrayType, objectType;
    // constructor assigns each; five accessor methods (stringType(), booleanType(), ...)
}
```

Every public `BlockSchemaGenerator` method now takes a `SchemaDialect` parameter, and every place
that used to write a literal type string now calls the matching `dialect.xxxType()` instead. The
reflection/naming logic (`resolveStaticSubtype`, `allFieldsIncludingInherited`,
`schemaPropertyName`) is completely untouched — none of it ever referenced a type token, so none of
it needed to change. That's the intended split: field *discovery* is 100% vendor-agnostic; only
type *token spelling* is vendor-specific, and now lives in exactly one place.

### 3.1 Enum construction timing — why passing `GEMINI` doesn't "set" anything

A common point of confusion: passing `SchemaDialect.GEMINI` as a parameter does **not** trigger any
field assignment at that moment. The two constants are built exactly once, automatically, the first
time anything touches the `SchemaDialect` class — effectively:
```java
SchemaDialect GEMINI = new SchemaDialect("STRING", "BOOLEAN", "INTEGER", "ARRAY", "OBJECT");
SchemaDialect JSON_SCHEMA = new SchemaDialect("string", "boolean", "integer", "array", "object");
```
This runs once, ever, for the life of the running application — enum constants are singletons. By
the time `generateStaticSchemaFor(..., SchemaDialect.GEMINI)` is ever called, both fully-built
objects already exist in memory with their fields permanently fixed. `SchemaDialect.GEMINI` in a
call site is just a reference to that pre-built object — passing it hands over a pointer, not a
trigger to compute anything. `dialect.stringType()` later just reads whichever object's field is
being pointed at. No case-conversion logic exists anywhere in this code — the "difference" between
`"STRING"` and `"string"` is just two different literal strings typed out by hand in the enum
declaration, not a computed transformation.

## 4. Side-by-Side Output: Same Call, Two Dialects

```java
generator.generateStaticSchemaFor("CHOICE", SchemaDialect.GEMINI)
```
```json
{"type": "OBJECT", "properties": {"allowMultiSelect": {"type": "BOOLEAN"}, "options": {"type": "ARRAY", "items": {"type": "STRING"}}, ...}}
```
```java
generator.generateStaticSchemaFor("CHOICE", SchemaDialect.JSON_SCHEMA)
```
```json
{"type": "object", "properties": {"allowMultiSelect": {"type": "boolean"}, "options": {"type": "array", "items": {"type": "string"}}, ...}}
```
Property **names and count** are identical in both — only the literal type tokens differ.

## 5. Test Coverage (`BlockSchemaGeneratorTest`)

- `choiceSchemaExposesItsRealFieldsUnderTheirRealJacksonNames` — asserts `"required"` is present and
  `"isRequired"` is not (the naming-fix regression guard), plus inherited/own fields, enum values,
  and `id`/`sortOrder` exclusion.
- `generateAllStaticSchemasCoversAllElevenLeavesWithoutHardcodingAList` — asserts exactly 11 entries.
- `conversationalSchemaHasNoLeafDiscriminatorSinceThereIsOnlyOneConcreteClass` — confirms the two
  code paths (static vs conversational) don't leak into each other.
- `sameSchemaInBothDialectsDiffersOnlyByTypeTokenCasing` — generates the same schema under both
  dialects, asserts identical property key sets, and spot-checks that nested type tokens (including
  inside `items`) actually swap correctly.
