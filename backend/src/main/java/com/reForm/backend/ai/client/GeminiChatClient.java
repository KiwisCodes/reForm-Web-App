package com.reForm.backend.ai.client;

import com.reForm.backend.ai.port.IAiChatClient;
import com.reForm.backend.ai.port.IAiModelProviderStrategy;
import com.reForm.backend.ai.strategy.Gemini35FlashModelStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

// Gemini implementation of IAiChatClient for Mode 2 text chat. Request/response shape (contents /
// systemInstruction / generationConfig.responseSchema, and extracting
// candidates[0].content.parts[0].text from the reply) was verified empirically against the real
// Gemini API via AiSchemaTestController before this was written, not guessed from docs alone.
// WebClient.create() as a field initializer, not injected — WebClient.Builder isn't
// auto-configured in this project (same fix AiSchemaTestController needed).
// apiKey is a constructor parameter (@Value on the parameter, not the field) rather than
// @RequiredArgsConstructor + field injection: field injection only fires when Spring builds the
// bean, which makes the class unconstructable-with-a-real-key in a plain unit test.
@Component
public class GeminiChatClient implements IAiChatClient {

    private static final String GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";

    private final List<IAiModelProviderStrategy> modelStrategies;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final WebClient webClient = WebClient.create();

    public GeminiChatClient(List<IAiModelProviderStrategy> modelStrategies, ObjectMapper objectMapper,
                             @Value("${gemini.api.key}") String apiKey) {
        this.modelStrategies = modelStrategies;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String generateFormResponse(String systemInstruction, List<Map<String, Object>> contents,
                                        Map<String, Object> responseSchema) {
        String modelId = resolveModelId();

        Map<String, Object> requestBody = Map.of(
                "contents", contents,
                "systemInstruction", Map.of("parts", Map.of("text", systemInstruction)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema
                )
        );

        String url = String.format(GEMINI_ENDPOINT_TEMPLATE, modelId);

        String rawResponse = webClient
                .post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractResponseText(rawResponse);
    }

    private String resolveModelId() {
        return modelStrategies.stream()
                .filter(strategy -> strategy.supports(Gemini35FlashModelStrategy.MODEL_KEY))
                .findFirst()
                .map(IAiModelProviderStrategy::getModelId)
                .orElseThrow(() -> new IllegalStateException(
                        "No strategy registered for " + Gemini35FlashModelStrategy.MODEL_KEY));
    }

    private String extractResponseText(String rawResponse) {
        JsonNode root = objectMapper.readTree(rawResponse);
        return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
    }
}
