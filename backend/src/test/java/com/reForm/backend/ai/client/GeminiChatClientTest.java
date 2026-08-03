package com.reForm.backend.ai.client;

import com.reForm.backend.ai.schema.BlockSchemaGenerator;
import com.reForm.backend.ai.schema.SchemaDialect;
import com.reForm.backend.ai.strategy.Gemini35FlashModelStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real Gemini call, no mocking — proves GeminiChatClient's own request/response handling works,
// not just the throwaway AiSchemaTestController harness it was ported from. Skips (rather than
// fails) when GEMINI_API_KEY isn't set in the environment, since this hits a real, billed API.
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiChatClientTest {

    @Test
    void generateFormResponseReturnsSchemaConformantJsonText() {
        BlockSchemaGenerator schemaGenerator = new BlockSchemaGenerator();
        Map<String, Object> schema = schemaGenerator.generateStaticSchemaFor("SHORT_TEXT", SchemaDialect.GEMINI);

        GeminiChatClient client = new GeminiChatClient(
                List.of(new Gemini35FlashModelStrategy()),
                new ObjectMapper(),
                System.getenv("GEMINI_API_KEY")
        );

        String responseText = client.generateFormResponse(
                "You are a form-building assistant. Generate a single form block matching the "
                        + "requested schema based on the user's message.",
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Add a name field")))),
                schema
        );

        assertTrue(responseText != null && !responseText.isBlank());
        assertFalse(responseText.trim().startsWith("```"), "responseSchema output should be raw JSON, no markdown fences");
    }
}
