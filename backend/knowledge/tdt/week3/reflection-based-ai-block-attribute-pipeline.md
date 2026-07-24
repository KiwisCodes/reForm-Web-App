# Reflection-Based Pipeline for AI-Generated Block Attributes

Follow-up to [current_block.md](./current_block.md) — resolves the "Stretching DTO" trap
described there by reusing `AbstractBlock`'s existing polymorphic Jackson registry instead of
hand-maintaining a second one for the AI layer.

## 1. The Question

`AiStaticBlockDto` is a single flat contract meant to carry AI-generated data for *every* static
block type (`ChoiceStaticBlock`, `FileUploadStaticBlock`, `ShortTextStaticBlock`, ...). Each type
has its own specific attributes (`selectionType`, `allowMultiSelect`, `multipleFile`, `maxFileSize`,
...). The original question: **is it inherently exhausting to cover every attribute of every block
type in this DTO — and is there a way to avoid hardcoding unmapped/expanding attributes into
`AiStaticBlockDto` at all?**

## 2. Obstacle Identification

### 2.1 The DTO-bloat trap (from `current_block.md`)
If every block-type-specific attribute becomes a named field on `AiStaticBlockDto`, the record
grows without bound as block types and their capabilities grow, and any change to one block type
risks touching a DTO shared by all of them.

### 2.2 A false lead: "these are new capabilities"
Initial framing assumed attributes like `selectionType: DROPDOWN` and `multipleFile: true`
represented capabilities not yet supported by the domain model. Checking the actual code
disproved this:
- [`ChoiceStaticBlock.java:16,20`](../../../src/main/java/com/reForm/backend/form/entity/block/staticblock/selection/ChoiceStaticBlock.java) already has `selectionType` (enum `SelectionType`: `RADIO_BUTTON`, `CHECKBOX`, `DROPDOWN`) and `allowMultiSelect`.
- [`FileUploadStaticBlock.java:15-19`](../../../src/main/java/com/reForm/backend/form/entity/block/staticblock/upload/FileUploadStaticBlock.java) already has `typeOfFiles`, `maxFileSize`, `multipleFile`.

So the real gap wasn't the domain model — it was that `AiStaticBlockDto` (a flat record with only
`staticType, label, isRequired, options, maxScale`) and `BlockFactory` (an empty stub) hadn't
caught up to attributes the domain already supports.

### 2.3 The residual "hardcoding" after a naive fix
A `Map<String, Object> additionalProperties` (`@JsonAnySetter`) removes the need to declare every
attribute as a DTO field. But `BlockFactory` still needs, somewhere, to know that map key
`"selectionType"` becomes `choiceBlock.setSelectionType(...)`. Writing that by hand (a
`switch (staticType)` with one hand-coded setter call per attribute) just *relocates* the hardcoded
knowledge from the DTO's field list into `BlockFactory`'s method bodies — same knowledge, different
location, still hand-maintained per attribute.

## 3. Solution: reuse `AbstractBlock`'s existing polymorphic registry, don't build a second one

`AbstractBlock` already carries everything needed for polymorphic dispatch, used today by the
ordinary (non-AI) create-form flow:

```java
// AbstractBlock.java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "staticType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ShortTextStaticBlock.class, name = "SHORT_TEXT"),
    @JsonSubTypes.Type(value = ChoiceStaticBlock.class, name = "CHOICE"),
    // ...
})
```

Instead of writing a parallel, hand-coded mapping for the AI path, `BlockFactory` merges the DTO's
known fields and its `additionalProperties` map into one plain `Map<String, Object>`, then hands
that map to the *same* Jackson mechanism the create-form flow already uses:

```java
objectMapper.convertValue(mergedMap, AbstractBlock.class);
```

Jackson's own reflection-based `BeanDeserializer` reads `mergedMap.get("staticType")`, looks it up
in the existing `@JsonSubTypes` table to pick the concrete class, and binds every other key to that
class's fields by name — automatically. Adding a new attribute to an *existing* block type (e.g. a
future `defaultValue` on `ChoiceStaticBlock`) requires **zero** changes to `AiStaticBlockDto` or
`BlockFactory` — it flows through `additionalProperties` → merged map → `convertValue` → bound by
reflection, same as any attribute already known today.

What does **not** go away: adding a wholly new block type still requires one `@JsonSubTypes.Type`
entry (an unavoidable, closed registry in any statically-typed polymorphic system — the alternative,
`Class.forName(llmControlledString)`, is a code-injection risk and was rejected). The distinction
that matters: **attribute-level hardcoding is eliminated; type-level registry hardcoding remains,
but it's the one registry the codebase already had, not a second one introduced for AI.**

### 3.1 Extending the same idea outward: constrain Gemini's output too
The same `@JsonSubTypes` registry can be read reflectively a third time — not just for inbound
binding, but to generate the **outbound** `responseSchema` sent to Gemini, so it's constrained at
the token-generation level to only emit fields that actually exist on the target class (see
`BlockSchemaGenerator` sketch, §5). One source of truth serves: real deserialization (existing),
AI-response binding (`BlockFactory`), and AI-request schema constraints (`BlockSchemaGenerator`).

### 3.2 Explicit non-goal: no runtime codegen
If a user asks for an attribute that doesn't exist on any block type, the schema constraint makes
it impossible for Gemini to emit it — the correct behavior is a conversational fallback or a
graceful degrade to the closest supported option, plus logging the unmet request for a human
developer to evaluate later. **The AI must never generate/compile/deploy new Java code at
runtime** — that's an arbitrary-code-execution surface, not a feature, and is out of scope
regardless of which DTO strategy is used.

## 4. Pipeline Flow (create/edit request → persisted block)

```
Gemini (Chat-to-Build)
   │  JSON: { "category": "STATIC", "staticType": "CHOICE", "label": "...",
   │          "required": true, "selectionType": "DROPDOWN", "options": [...] }
   ▼
AiBlockDto (@JsonTypeInfo "category") ──dispatch──► AiStaticBlockDto
   │
   │  Named fields (staticType, label, required) bind via Lombok-generated
   │  setters. Everything else ("selectionType", "options", ...) falls into
   │  additionalProperties via @JsonAnySetter.
   ▼
BlockFactory.build(AiStaticBlockDto dto)
   │  merged = new HashMap<>(dto.getAdditionalProperties())
   │  merged.put("staticType", dto.getStaticType())
   │  merged.put("label", dto.getLabel())
   │  merged.put("required", dto.isRequired())
   ▼
objectMapper.convertValue(merged, AbstractBlock.class)
   │  Reuses AbstractBlock's existing @JsonTypeInfo/@JsonSubTypes:
   │  "staticType": "CHOICE" → dispatch to ChoiceStaticBlock
   │  remaining keys bound by reflection (selectionType, options, ...)
   ▼
ChoiceStaticBlock instance (real domain object)
   ▼
Form.setBlocks(...) → repository.save(form) → AbstractBlockConverter → jsonb column
```

This is deliberately the same shape as the existing, already-verified create-form flow
(`FormCreateDto` → `List<AbstractBlock>` → `AbstractBlockConverter` → `jsonb`) — the AI path
converges onto it at the `AbstractBlock` boundary instead of duplicating it.

## 5. Pipeline Design

### 5.1 `AiStaticBlockDto` — the validated boundary
```java
@Getter
@Setter
public class AiStaticBlockDto implements AiBlockDto {
    private String staticType;
    private String label;
    private boolean required;             // named "required", not "isRequired" — see §5.4
    private final Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void addAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }
}
```
Keeps a minimal, always-required core (`staticType`, `label`, `required`) as real typed fields —
this is the safety boundary the original design wanted (reject/inspect malformed core data before
touching persistence-bound classes) — while everything type-specific flows through the generic map.

### 5.2 `BlockFactory` — binds via reuse, not via hand-written mapping
```java
@Component
@RequiredArgsConstructor
public class BlockFactory {
    private final ObjectMapper objectMapper; // tools.jackson.databind (Jackson 3)

    public AbstractBlock build(AiStaticBlockDto dto) {
        Map<String, Object> merged = new HashMap<>(dto.getAdditionalProperties());
        merged.put("staticType", dto.getStaticType());
        merged.put("label", dto.getLabel());
        merged.put("required", dto.isRequired());
        return objectMapper.convertValue(merged, AbstractBlock.class);
    }
}
```
No per-attribute code, no per-type switch — both dispatch and binding come from `AbstractBlock`'s
existing annotations.

### 5.3 `BlockSchemaGenerator` (sketch) — constrains Gemini using the same registry
```java
public class BlockSchemaGenerator {
    private static final Set<String> INTERNAL_FIELDS = Set.of("id", "sortOrder");

    public Map<String, Map<String, Object>> generateAllSchemas() {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        for (JsonSubTypes.Type t : AbstractBlock.class.getAnnotation(JsonSubTypes.class).value()) {
            all.put(t.name(), buildObjectSchema(t.value().asSubclass(AbstractBlock.class), t.name()));
        }
        return all;
    }
    // buildObjectSchema() reflects declared fields (incl. inherited), maps Java types to
    // schema types (String→STRING, boolean→BOOLEAN, enum→STRING+enum values,
    // List<String>→ARRAY of STRING), skipping INTERNAL_FIELDS.
}
```

### 5.4 Corrections made along the way (verified against actual code/payloads, not guessed)
- **Missing accessors bug:** `AiStaticBlockDto` initially had private fields with no getters/setters
  besides `@JsonAnySetter`. Jackson's default visibility requires a public field, an explicit
  setter, or `@JsonProperty` to bind a name — without one, `staticType`/`label`/`required` would
  have silently fallen through to the any-setter catch-all instead of binding to the named fields,
  defeating the point of having them. Fixed with Lombok `@Getter @Setter`.
- **Boolean field naming mismatch:** Lombok generates `isRequired()`/`setRequired(boolean)` for a
  field literally named `isRequired` (stripping the redundant `is`), so the real Jackson property
  name is `"required"`, not `"isRequired"`. Confirmed against actual working payloads in
  [`user-workspace.http:190,237,247,359`](../../../user-workspace.http) (`"required": true`), not
  assumed from the field name. The DTO's field was renamed from `isRequired` to `required` to match
  this convention directly rather than special-casing it elsewhere.
- This same naming rule would have been a latent bug in `BlockSchemaGenerator` too:
  `Field.getName()` on a Java field doesn't necessarily equal Jackson's actual property name for
  boolean `isXxx`-style fields — the generator needs to special-case this rather than assume
  `field.getName()` is always the correct schema key.

### 5.5 Open decisions, not yet settled
1. `List<String>` field-type assumption in the schema generator — reflection erases generics, so
   it can't verify element type; currently assumes every `List` field is `List<String>` (true today
   for every block, but a silent trap if that changes).
2. `INTERNAL_FIELDS` as a bare string set (`"id", "sortOrder"`) is itself a small hardcoded list —
   an alternative is a marker annotation (e.g. `@AiManaged`) directly on those fields in
   `AbstractBlock`, self-documenting at the declaration site instead of a separately maintained set.
3. No Gemini client dependency exists in `pom.xml` yet — the schema is currently a generic
   `Map`/JSON shape; wiring it into a real `responseSchema` call depends on which SDK gets added.
