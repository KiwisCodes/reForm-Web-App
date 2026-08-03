package com.reForm.backend.ai.testharness;

import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import com.reForm.backend.ai.schema.BlockSchemaGenerator;
import com.reForm.backend.ai.schema.SchemaDialect;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// THROWAWAY MANUAL TEST HARNESS — not part of Mode 2's real architecture. Exists purely to
// empirically verify, before wiring Phase 1 (AiResponseParser/BlockFactory) to Phase 2, how a real
// Gemini responseSchema-constrained call actually behaves: exact request shape, exact response
// envelope, whether BlockSchemaGenerator's output is accepted as-is. Returns the RAW Gemini
// response untouched — deliberately not run through AiResponseParser yet, so it can be inspected
// by eye first. See ai-test.html for the driving page.
@RestController
@RequestMapping("/api/v1/ai/test")
@RequiredArgsConstructor
public class AiSchemaTestController {

    private static final String GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";

    private final BlockSchemaGenerator schemaGenerator;
    private final List<IAiModelProviderStrategy> modelStrategies;
    private final WebClient webClient = WebClient.create();

    @Value("${gemini.api.key}")
    private String apiKey;

    public record TestChatRequest(String staticType, String message) {
    }

    @PostMapping("/chat")
    public Map<String, Object> testChat(@RequestBody TestChatRequest request) {
        Map<String, Object> schema = schemaGenerator.generateStaticSchemaFor(request.staticType(), SchemaDialect.GEMINI);

        String modelId = modelStrategies.stream()
                .filter(s -> s.supports("GEMINI_3_5_FLASH"))
                .findFirst()
                .map(IAiModelProviderStrategy::getModelId)
                .orElseThrow(() -> new IllegalStateException("No strategy registered for GEMINI_3_5_FLASH"));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", request.message()))
                )),
                "systemInstruction", Map.of(
                        "parts", Map.of("text",
                                "You are a form-building assistant. Generate a single form block "
                                        + "matching the requested schema based on the user's message.")
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", schema
                )
        );

        String url = String.format(GEMINI_ENDPOINT_TEMPLATE, modelId);

        Object rawGeminiResponse = webClient
                .post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Object.class)
                .block();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaSent", schema);
        result.put("requestBodySent", requestBody);
        result.put("rawGeminiResponse", rawGeminiResponse);
        return result;
    }
}
