package com.reForm.backend.ai.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Reflects the REAL block classes (not the AI DTOs) to build a JSON-Schema-shaped Map per block
// type, meant to constrain an LLM's structured output to only field names/enum values that
// actually exist. Static leaves are enumerated automatically from StaticBlock's existing
// @JsonSubTypes registry, so a new static leaf type needs zero changes here. ConversationalBlock
// stays a single concrete class for now (see week3 knowledge docs), so it's reflected directly
// instead of enumerated from a registry.
//
// Every public method takes a SchemaDialect because this app targets more than one model vendor
// (see IAiModelProviderStrategy) — the structural shape is vendor-agnostic, only the literal type
// tokens differ (SchemaDialect isolates that).
@Component
public class BlockSchemaGenerator {

    // System-managed fields — not something an LLM should ever be asked to set.
    private static final Set<String> INTERNAL_FIELDS = Set.of("id", "sortOrder");

    public Map<String, Object> generateStaticSchemaFor(String staticType, SchemaDialect dialect) {
        Class<? extends AbstractBlock> target = resolveStaticSubtype(staticType);
        return buildObjectSchema(target, BlockType.STATIC, staticType, dialect);
    }

    // Enumerates every known static leaf without a second, separately maintained list — reads it
    // straight off StaticBlock's existing @JsonSubTypes.
    public Map<String, Map<String, Object>> generateAllStaticSchemas(SchemaDialect dialect) {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        for (JsonSubTypes.Type t : StaticBlock.class.getAnnotation(JsonSubTypes.class).value()) {
            all.put(t.name(), buildObjectSchema(t.value().asSubclass(AbstractBlock.class), BlockType.STATIC, t.name(), dialect));
        }
        return all;
    }

    public Map<String, Object> generateConversationalSchema(SchemaDialect dialect) {
        return buildObjectSchema(ConversationalBlock.class, BlockType.CONVERSATIONAL, null, dialect);
    }

    // Constrains a whole-form/whole-turn response to "an array where each element matches any one
    // of the known block schemas" — what AiBlockApplicationService.updateFormFromAiBlocks actually
    // needs (a List<AiBlockDto> that can mix types), and what FormChatPromptBuilder's system
    // instruction already promises Gemini ("decide the complete revised set of blocks").
    // Uses anyOf, not oneOf: Gemini's responseSchema doesn't support oneOf at all, and whether an
    // array's items can even use anyOf across different object shapes wasn't documented anywhere —
    // confirmed empirically against the real API first (see
    // ArrayAnyOfSchemaExplorationTest / week3/mode2 docs) before writing this method around it.
    // Composed from the existing per-type methods rather than re-walking StaticBlock's
    // @JsonSubTypes registry itself, so a new block type needs zero changes here either.
    public Map<String, Object> generateBlocksArraySchema(SchemaDialect dialect) {
        List<Object> variants = new ArrayList<>(generateAllStaticSchemas(dialect).values());
        variants.add(generateConversationalSchema(dialect));

        return Map.of(
                "type", dialect.arrayType(),
                "items", Map.of("anyOf", variants)
        );
    }

    // Constrains a whole-form-creation response to AiFormDto's exact shape (title + blocks) — what
    // AiBlockApplicationService.createFormFromAiBlocks needs. Built on top of
    // generateBlocksArraySchema rather than re-deriving the block-variant list a second time, so a
    // new block type is still picked up here with zero changes, same as every other method above.
    public Map<String, Object> generateFormSchema(SchemaDialect dialect) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", dialect.stringType()));
        properties.put("blocks", generateBlocksArraySchema(dialect));

        return Map.of(
                "type", dialect.objectType(),
                "properties", properties,
                "required", List.of("title", "blocks")
        );
    }

    private Class<? extends AbstractBlock> resolveStaticSubtype(String staticType) {
        for (JsonSubTypes.Type t : StaticBlock.class.getAnnotation(JsonSubTypes.class).value()) {
            if (t.name().equals(staticType)) {
                return t.value().asSubclass(AbstractBlock.class);
            }
        }
        throw new IllegalArgumentException("Unknown staticType: " + staticType);
    }

    // "category" (STATIC/CONVERSATIONAL) is the discriminator AiBlockDto's @JsonTypeInfo dispatches
    // on when parsing Gemini's response — every block schema must require it, or Gemini's
    // constrained decoding has no token path to ever emit it (see AiResponseParser /
    // AiBlockDto). "staticType" is a second, narrower discriminator only static leaves carry.
    private Map<String, Object> buildObjectSchema(Class<? extends AbstractBlock> clazz,
                                                    BlockType category,
                                                    String staticType,
                                                    SchemaDialect dialect) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        required.add("label");

        properties.put("category", Map.of("type", dialect.stringType(), "enum", List.of(category.name())));
        required.add("category");

        if (staticType != null) {
            properties.put("staticType", Map.of("type", dialect.stringType(), "enum", List.of(staticType)));
            required.add("staticType");
        }

        for (Field field : allFieldsIncludingInherited(clazz)) {
            if (INTERNAL_FIELDS.contains(field.getName())) continue;
            properties.put(schemaPropertyName(field), schemaForField(field, dialect));
        }

        return Map.of("type", dialect.objectType(), "properties", properties, "required", required);
    }

    private List<Field> allFieldsIncludingInherited(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    // Mirrors Lombok/Jackson's own bean-property-naming rule: a boolean field already prefixed
    // "isXxx" gets a plain "isXxx()" getter but a "setXxx(boolean)" setter (prefix stripped), so
    // Jackson's real property name is "xxx", not "isXxx". AbstractBlock.isRequired is exactly this
    // case — field.getName() alone would silently produce the wrong schema key ("isRequired"
    // instead of "required"), the mismatch already flagged as a known risk before this was written.
    private String schemaPropertyName(Field field) {
        String name = field.getName();
        boolean isBoolean = field.getType() == boolean.class || field.getType() == Boolean.class;
        if (isBoolean && name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return name;
    }

    private Map<String, Object> schemaForField(Field field, SchemaDialect dialect) {
        Class<?> type = field.getType();

        if (type.isEnum()) {
            return Map.of("type", dialect.stringType(), "enum",
                    Arrays.stream(type.getEnumConstants()).map(Object::toString).toList());
        }
        if (type == String.class) return Map.of("type", dialect.stringType());
        if (type == boolean.class || type == Boolean.class) return Map.of("type", dialect.booleanType());
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return Map.of("type", dialect.integerType());
        }
        if (List.class.isAssignableFrom(type)) {
            return Map.of("type", dialect.arrayType(), "items", Map.of("type", dialect.stringType()));
        }
        throw new IllegalStateException("No schema mapping for field " + field.getName() + " (" + type + ")");
    }
}
