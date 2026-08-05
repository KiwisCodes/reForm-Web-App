package com.reForm.backend.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GEMINI 3.6 FLASH REST SERVICE (Stateless LLM Reasoning Layer)
 * 
 * ARCHITECTURE ROLE:
 * Handles non-blocking HTTP REST calls to Google Gemini Flash generateContent API.
 * Shared directly across:
 * - Mode 2 (Text Chat Copilot)
 * - Mode 3 (Voice Cascaded Pipeline: STT -> LLM REST -> TTS)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiFlashRestService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:DEFAULT_PLATFORM_KEY}")
    private String geminiApiKey;

    /**
     * Executes non-blocking HTTP REST prompt request to Gemini Flash.
     * 
     * @param systemPrompt System instruction / persona compiled by SessionContextService
     * @param userTranscript User speech transcript or text input
     * @param tools Tool declaration schemas for function calling
     * @return Reactive Mono containing response JsonNode
     */
    public Mono<JsonNode> callGemini(String systemPrompt, String userTranscript, List<Map<String, Object>> tools) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }

        requestBody.put("contents", List.of(
            Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userTranscript))
            )
        ));

        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }

        log.info("[MODE 3 GEMINI FLASH REST REQUEST]: User Transcript: '{}'", userTranscript);

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(rawJson -> {
                    try {
                        return objectMapper.readTree(rawJson);
                    } catch (Exception e) {
                        log.error("Failed to parse Gemini REST response JSON", e);
                        throw new RuntimeException("Gemini REST JSON parsing error", e);
                    }
                })
                .doOnSuccess(json -> log.info("[MODE 3 GEMINI FLASH REST SUCCESS]"))
                .doOnError(err -> log.error("[MODE 3 GEMINI FLASH REST ERROR]: {}", err.getMessage()));
    }
}
