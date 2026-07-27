# Custom Deserializer: Dispatching STATIC vs CONVERSATIONAL Blocks

Follow-up to [reflection-based-ai-block-attribute-pipeline.md](./reflection-based-ai-block-attribute-pipeline.md) —
once `ConversationalBlock` needed to join the `AbstractBlock` hierarchy, the existing flat
`@JsonSubTypes` dispatch on `AbstractBlock` wasn't enough on its own, and the obvious-looking fix
(a second nested discriminator) turned out to be a dead end already documented once before.

## 1. The Question

`ConversationalBlock` needed to belong in the same `AbstractBlock` hierarchy as the 11 existing
static leaf types (`ChoiceStaticBlock`, `ShortTextStaticBlock`, ...). Given that, two ideas were on
the table: **"Can we add a new `@JsonTypeInfo` as `conversationalType`? Or can we design a class to
differentiate the 2 types?"** — specifically: `AbstractBlock` uses one property, `blockType`, with
2 entries (`STATIC`, `CONVERSATIONAL`); `StaticBlock` uses its own property, `staticType`, with the
11 leaf entries. Does that work?

## 2. The Problem

This is a nested, two-level polymorphic structure: an outer `@JsonTypeInfo` on `AbstractBlock`
resolving to an intermediate abstract class (`StaticBlock`), which has its *own* `@JsonTypeInfo`
for its leaves. `AbstractBlock`'s own code comment already documented this exact shape failing
once before:

> Jackson can't cascade a nested `@JsonTypeInfo` (`StaticBlock`'s own "staticType" discriminator
> only applies once you're already deserializing as `StaticBlock`, which never happens since
> polymorphic dispatch stops at the first concrete match).

Rather than trust that comment (written against Jackson 2, before this project's Jackson 3
migration) or trust memory of how Jackson's internals work, this was verified empirically with a
throwaway JUnit test (`NestedPolymorphismExperimentTest`, deleted after use) mirroring the proposed
`blockType`(2 entries) → `staticType`(11 entries) structure. Result — confirmed failing, Jackson 3
included:

```
InvalidDefinitionException: Cannot construct instance of `ExpStatic` (no Creators...):
abstract types either need to be mapped to concrete types, have custom deserializer,
or contain additional type information
```

Jackson resolved the outer discriminator to the intermediate abstract class, then tried plain bean
deserialization directly against it — it never re-checked that class's own `@JsonTypeInfo`. Same
failure mode as documented, now confirmed on the current Jackson version rather than assumed.

## 3. The Solution

A **custom deserializer** sidesteps the limitation because it doesn't rely on Jackson's automatic
type-resolution chaining at all for the first hop. It reads the JSON into a tree, branches on the
discriminator itself in plain Java, and then makes a **fresh, independent, top-level call** to
deserialize into the resolved abstract type. That second call isn't "nested" from Jackson's
perspective — it's the same kind of call as `objectMapper.readValue(json, StaticBlock.class)` from
outside code, which is standard, fully-supported polymorphism. This was also verified empirically
first (`CustomDeserializerExperimentTest`, deleted after use) before touching the real classes —
both the static-leaf case and the conversational case resolved correctly.

## 4. Design

**`AbstractBlock`** — the flat `@JsonTypeInfo`/`@JsonSubTypes` (11 entries) is gone. In its place:
```java
@JsonDeserialize(using = AbstractBlockDeserializer.class)
public abstract class AbstractBlock implements IFormBlock { ... }
```

**`AbstractBlockDeserializer`** — the new dispatch logic:
```java
public class AbstractBlockDeserializer extends StdDeserializer<AbstractBlock> {
    public AbstractBlockDeserializer() {
        super(AbstractBlock.class);
    }

    @Override
    public AbstractBlock deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);
        String type = node.path("type").asString(null);

        if ("CONVERSATIONAL".equals(type)) {
            return ctxt.readTreeAsValue(node, ConversationalBlock.class);
        }
        return ctxt.readTreeAsValue(node, StaticBlock.class);
    }
}
```
- `ctxt.readTree(p)` buffers the whole JSON object once, so it can be inspected without consuming
  the parser twice.
- `node.path("type").asString(null)` reads the **previously inert** `"type"` field
  (`AbstractBlock`'s old comment explicitly said this field was "sent by clients... but no longer
  drives dispatch, treat as inert" — it's revived here as the real first-level discriminator).
  Existing static-block payloads already send `"type": "STATIC"`, so no wire-format change was
  needed on that side.
- `ctxt.readTreeAsValue(node, StaticBlock.class)` is the fresh top-level call — `StaticBlock` still
  carries its own `"staticType"` discriminator, which now resolves normally because this call isn't
  chained from another discriminator's resolution.

**`StaticBlock`** — now carries the `@JsonTypeInfo("staticType")`/`@JsonSubTypes` (11 entries)
moved from `AbstractBlock`, plus `@JsonIgnoreProperties(ignoreUnknown = true)` (needed because
`"type"` and `"staticType"` are present in the JSON object being bound but aren't real fields on any
leaf class).

**`ConversationalBlock`** (new) — a leaf class alongside `StaticBlock`, not a family of its own:
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationalBlock extends AbstractBlock {
    private String prompt;
    private String persona;
    private Integer maxQuestions;
    // getType() -> BlockType.CONVERSATIONAL
}
```

### Jackson 3 API notes hit along the way (verified via `javap` against the resolved jar, not guessed)
- `@JsonDeserialize` lives at `tools.jackson.databind.annotation.JsonDeserialize` in Jackson 3, not
  the old `com.fasterxml.jackson.databind.annotation` location (that package isn't even on the
  compile classpath anymore per [06-jackson-2-vs-3-coexistence-and-error-masking.md](../week2/06-jackson-2-vs-3-coexistence-and-error-masking.md)).
- Building a custom `ObjectMapper` with an extra module is builder-based:
  `JsonMapper.builder().addModule(module).build()` — Jackson 3's `ObjectMapper` has no public
  mutable constructor/`registerModule()` the way Jackson 2 did.

## 5. What Problem This Solved

Without this, adding conversational block support would have forced a choice between two worse
options: (a) flatten all leaf types — 11 static plus however many conversational — into one shared
`@JsonSubTypes` list under one renamed discriminator property, blurring the STATIC/CONVERSATIONAL
category distinction into a single flat namespace; or (b) the nested-annotation approach, which
doesn't work at all (§2). The custom deserializer keeps the two categories cleanly separated at
their own dispatch levels — `"type"` picks the category, `"staticType"` (meaningful only within the
static category) picks the leaf — while reusing the existing, already-correct 11-entry static
dispatch table completely unchanged.
