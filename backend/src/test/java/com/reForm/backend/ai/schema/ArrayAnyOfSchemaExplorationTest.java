package com.reForm.backend.ai.schema;

import com.reForm.backend.ai.client.GeminiChatClient;
import com.reForm.backend.ai.strategy.Gemini35FlashModelStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real Gemini call, no mocking. Google's own docs confirm anyOf support for a top-level
// polymorphic field, but never explicitly confirm array items using anyOf across different object
// shapes — confirmed empirically here first (originally against a hand-built 2-type schema; now
// against generateBlocksArraySchema's real 12-variant output, since the docs also warn "very
// large or deeply nested schemas may be rejected" and 12 variants is meaningfully bigger than 2).
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ArrayAnyOfSchemaExplorationTest {

    @Test
    void arrayOfAnyOfAcrossAllTwelveBlockTypesReturnsMixedTypeArray() {
        BlockSchemaGenerator schemaGenerator = new BlockSchemaGenerator();
        Map<String, Object> combinedSchema = schemaGenerator.generateBlocksArraySchema(SchemaDialect.GEMINI);

        GeminiChatClient client = new GeminiChatClient(
                List.of(new Gemini35FlashModelStrategy()),
                new ObjectMapper(),
                System.getenv("GEMINI_API_KEY")
        );

        String responseText = client.generateFormResponse(
                "You are a form-building assistant. Generate the requested form blocks matching the "
                        + "provided schema.",
                List.of(Map.of("role", "user", "parts",
                        List.of(Map.of("text", "Add a name field and an email field")))),
                combinedSchema
        );

        System.out.println("Raw Gemini response: " + responseText);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode array = objectMapper.readTree(responseText);

        assertTrue(array.isArray(), "response should be a JSON array");
        assertEquals(2, array.size(), "expected one block per requested field");

        List<String> staticTypes = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            staticTypes.add(node.path("staticType").asText());
        }

        assertTrue(staticTypes.contains("SHORT_TEXT"), "expected a SHORT_TEXT block, got: " + staticTypes);
        assertTrue(staticTypes.contains("EMAIL"), "expected an EMAIL block, got: " + staticTypes);
    }
}
