package com.reForm.backend.ai.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.reForm.backend.ai.dto.AiBlockDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSchemaGeneratorTest {

    private final BlockSchemaGenerator generator = new BlockSchemaGenerator();

    @Test
    @SuppressWarnings("unchecked")
    void choiceSchemaExposesItsRealFieldsUnderTheirRealJacksonNames() {
        Map<String, Object> schema = generator.generateStaticSchemaFor("CHOICE", SchemaDialect.GEMINI);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        // AbstractBlock.isRequired must appear as "required" (Jackson's real property name),
        // not the raw Java field name "isRequired" — this is the exact bug fixed before this test.
        assertTrue(properties.containsKey("required"), "expected 'required', schema had: " + properties.keySet());
        assertFalse(properties.containsKey("isRequired"));

        // Inherited from StaticBlock.
        assertTrue(properties.containsKey("placeholder"));

        // ChoiceStaticBlock's own fields.
        assertTrue(properties.containsKey("options"));
        assertTrue(properties.containsKey("allowMultiSelect"));

        Map<String, Object> selectionType = (Map<String, Object>) properties.get("selectionType");
        assertEquals(List.of("RADIO_BUTTON", "CHECKBOX", "DROPDOWN"), selectionType.get("enum"));

        // Internal/system-managed fields must never be exposed to the LLM.
        assertFalse(properties.containsKey("id"));
        assertFalse(properties.containsKey("sortOrder"));

        Map<String, Object> staticType = (Map<String, Object>) properties.get("staticType");
        assertEquals(List.of("CHOICE"), staticType.get("enum"));

        // "category" is the discriminator AiBlockDto's @JsonTypeInfo dispatches on when parsing
        // Gemini's response — every block schema must require it (see AiResponseParser).
        Map<String, Object> category = (Map<String, Object>) properties.get("category");
        assertEquals(List.of("STATIC"), category.get("enum"));
        assertTrue(((List<String>) schema.get("required")).contains("category"));
    }

    @Test
    void generateAllStaticSchemasCoversAllElevenLeavesWithoutHardcodingAList() {
        Map<String, Map<String, Object>> all = generator.generateAllStaticSchemas(SchemaDialect.GEMINI);

        assertEquals(11, all.size());
        assertTrue(all.containsKey("SHORT_TEXT"));
        assertTrue(all.containsKey("FILE_UPLOAD"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conversationalSchemaHasNoLeafDiscriminatorSinceThereIsOnlyOneConcreteClass() {
        Map<String, Object> schema = generator.generateConversationalSchema(SchemaDialect.GEMINI);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertTrue(properties.containsKey("prompt"));
        assertTrue(properties.containsKey("persona"));
        assertTrue(properties.containsKey("maxQuestions"));
        assertTrue(properties.containsKey("required"));
        assertFalse(properties.containsKey("staticType"));

        // Still needs "category" — AiBlockDto's discriminator applies to every block type, not
        // just static leaves.
        Map<String, Object> category = (Map<String, Object>) properties.get("category");
        assertEquals(List.of("CONVERSATIONAL"), category.get("enum"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameSchemaInBothDialectsDiffersOnlyByTypeTokenCasing() {
        Map<String, Object> gemini = generator.generateStaticSchemaFor("CHOICE", SchemaDialect.GEMINI);
        Map<String, Object> jsonSchema = generator.generateStaticSchemaFor("CHOICE", SchemaDialect.JSON_SCHEMA);

        assertEquals("OBJECT", gemini.get("type"));
        assertEquals("object", jsonSchema.get("type"));

        Map<String, Object> geminiProps = (Map<String, Object>) gemini.get("properties");
        Map<String, Object> jsonSchemaProps = (Map<String, Object>) jsonSchema.get("properties");

        // Same field set in both dialects — only the literal type tokens differ.
        assertEquals(geminiProps.keySet(), jsonSchemaProps.keySet());

        assertEquals("BOOLEAN", ((Map<String, Object>) geminiProps.get("allowMultiSelect")).get("type"));
        assertEquals("boolean", ((Map<String, Object>) jsonSchemaProps.get("allowMultiSelect")).get("type"));

        assertEquals("ARRAY", ((Map<String, Object>) geminiProps.get("options")).get("type"));
        assertEquals("array", ((Map<String, Object>) jsonSchemaProps.get("options")).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blocksArraySchemaCombinesAllStaticLeavesAndConversationalViaAnyOf() {
        Map<String, Object> schema = generator.generateBlocksArraySchema(SchemaDialect.GEMINI);

        assertEquals("ARRAY", schema.get("type"));

        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        List<Object> variants = (List<Object>) items.get("anyOf");

        // 11 static leaves + 1 conversational block, composed from the existing per-type methods
        // rather than a second hardcoded count.
        assertEquals(12, variants.size());
    }

    // Regression test for the exact bug that made every real chat turn fail in production:
    // AiBlockDto's @JsonTypeInfo dispatches on "category", but no schema variant required it, so
    // Gemini's constrained decoding had no token path to ever emit it and AiResponseParser threw
    // "missing type id property 'category'" on every real (non-hand-written-JSON) response.
    // Derives the expected discriminator values straight from AiBlockDto's own @JsonSubTypes
    // registry, so this stays in sync automatically if a new block category is ever added there.
    @Test
    @SuppressWarnings("unchecked")
    void everyBlockVariantRequiresACategoryAiBlockDtoCanActuallyDispatchOn() {
        Set<String> dispatchableCategories = Arrays.stream(
                        AiBlockDto.class.getAnnotation(JsonSubTypes.class).value())
                .map(JsonSubTypes.Type::name)
                .collect(Collectors.toSet());

        Map<String, Object> schema = generator.generateBlocksArraySchema(SchemaDialect.GEMINI);
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        List<Object> variants = (List<Object>) items.get("anyOf");

        for (Object variantObj : variants) {
            Map<String, Object> variant = (Map<String, Object>) variantObj;
            Map<String, Object> properties = (Map<String, Object>) variant.get("properties");
            List<String> required = (List<String>) variant.get("required");

            assertTrue(properties.containsKey("category"), "variant missing 'category': " + variant);
            assertTrue(required.contains("category"), "'category' must be required, not just present: " + variant);

            List<String> categoryEnum = (List<String>) ((Map<String, Object>) properties.get("category")).get("enum");
            assertEquals(1, categoryEnum.size());
            assertTrue(dispatchableCategories.contains(categoryEnum.get(0)),
                    "schema declares category '" + categoryEnum.get(0)
                            + "' but AiBlockDto has no @JsonSubTypes entry for it");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void formSchemaWrapsTitleAndTheSameBlocksArraySchema() {
        Map<String, Object> schema = generator.generateFormSchema(SchemaDialect.GEMINI);

        assertEquals("OBJECT", schema.get("type"));
        assertEquals(List.of("title", "blocks"), schema.get("required"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals("STRING", ((Map<String, Object>) properties.get("title")).get("type"));

        Map<String, Object> blocksSchema = (Map<String, Object>) properties.get("blocks");
        assertEquals(generator.generateBlocksArraySchema(SchemaDialect.GEMINI), blocksSchema);
    }
}
