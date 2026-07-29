package com.reForm.backend.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reForm.backend.ai.event.FormLayoutModificationEvent;
import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GEMINI LIVE VOICE ADAPTER (Step 4 - Outbound WSS Proxy Adapter)
 * 
 * WHAT IS THIS CLASS?
 * Implements IAiVoiceAdapter to proxy real-time audio streams, live transcriptions, 
 * barge-in signals, and function calling tool calls between client browsers and 
 * Google's Gemini Multimodal Live API (wss://generativelanguage.googleapis.com).
 * 
 * UNIFIED FRAME PROCESSOR:
 * Handles both TextMessage and BinaryMessage payloads seamlessly. Auto-detects Google's 
 * JSON frames (setupComplete, serverContent, inputTranscription, outputTranscription, toolCall) 
 * and extracts Base64 audio PCM to forward to the candidate browser.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLiveVoiceAdapter implements IAiVoiceAdapter {

    private final SessionContextService sessionContextService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:DEFAULT_PLATFORM_KEY}")
    private String platformApiKey;

    @Override
    public void startSession(String userId, WebSocketSession clientSession) {
        // Wrap clientSession in ConcurrentWebSocketSessionDecorator with 10MB buffer limit
        WebSocketSession safeClientSession = new ConcurrentWebSocketSessionDecorator(clientSession, 10000, 10485760);
        clientSession.getAttributes().put("safeClientSession", safeClientSession);

        Role role = (Role) clientSession.getAttributes().get("role");
        String formId = (String) clientSession.getAttributes().get("formId");
        String requestedModelKey = (String) clientSession.getAttributes().get("modelKey");

        // 1. Resolve API key
        String apiKey = platformApiKey;

        // 2. Build Google Live API WSS Endpoint URI (v1beta for BidiGenerateContent)
        String googleWssUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;

        log.info("Opening Gemini Live WSS tunnel for user: {} (Role: {}, FormId: {})", userId, role, formId);

        try {
            jakarta.websocket.WebSocketContainer container = jakarta.websocket.ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(10485760); // 10MB buffer
            container.setDefaultMaxBinaryMessageBufferSize(10485760); // 10MB buffer

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient(container);
            webSocketClient.execute(new AbstractWebSocketHandler() {

                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("Outbound WebSocket connection to Google Gemini Live established for user: {}", userId);
                    
                    // Wrap outbound geminiSession in ConcurrentWebSocketSessionDecorator with 10MB buffer limit
                    WebSocketSession safeGeminiSession = new ConcurrentWebSocketSessionDecorator(session, 10000, 10485760);
                    clientSession.getAttributes().put("geminiSession", safeGeminiSession);

                    // Build initial setup context payload Map
                    Map<String, Object> setupPayload = sessionContextService.buildSetupContext(userId, role, formId, requestedModelKey);
                    
                    // Transmit BidiGenerateContentSetup JSON frame
                    String setupJson = objectMapper.writeValueAsString(Map.of("setup", setupPayload));
                    log.info("[SENDING SETUP TO GEMINI LIVE]: {}", setupJson);
                    safeGeminiSession.sendMessage(new TextMessage(setupJson));
                }

                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                    processGooglePayload(userId, clientSession, session, message.getPayload().getBytes(StandardCharsets.UTF_8));
                }

                @Override
                protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                    java.nio.ByteBuffer buffer = message.getPayload();
                    byte[] rawBytes = new byte[buffer.remaining()];
                    buffer.get(rawBytes);
                    processGooglePayload(userId, clientSession, session, rawBytes);
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.error("Outbound Gemini WSS transport error for user: {}", userId, exception);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                    log.warn("[OUTBOUND GEMINI LIVE WSS CLOSED]: user={}, code={}, reason={}", userId, status.getCode(), status.getReason());
                }

            }, null, URI.create(googleWssUrl));

        } catch (Exception e) {
            log.error("Failed to establish outbound Gemini Live WSS session for user: {}", userId, e);
        }
    }

    private void processGooglePayload(String userId, WebSocketSession clientSession, WebSocketSession geminiSession, byte[] payload) {
        try {
            String payloadStr = new String(payload, StandardCharsets.UTF_8).trim();

            // Check if frame is a JSON text message from Google
            if (payloadStr.startsWith("{") && payloadStr.endsWith("}")) {
                log.info("[RAW GEMINI JSON RESPONSE]: {}", payloadStr);

                WebSocketSession activeClient = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
                JsonNode root = objectMapper.readTree(payloadStr);

                // SECTION 1: SETUP COMPLETION
                if (root.has("setupComplete")) {
                    log.info("✅ Google Gemini Live Setup Complete for user: {}", userId);
                }

                // SECTION 2: REAL-TIME TRANSCRIPTIONS & AUDIO OUTPUT
                if (root.has("serverContent")) {
                    JsonNode serverContent = root.get("serverContent");

                    // 1. Candidate/User Live Speech Transcription
                    if (serverContent.has("inputTranscription")) {
                        String userTranscript = serverContent.path("inputTranscription").path("text").asText();
                        log.info("[Candidate Transcribed Text]: {}", userTranscript);
                        
                        if (activeClient != null && activeClient.isOpen()) {
                            activeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                                "type", "TRANSCRIPT_USER",
                                "text", userTranscript
                            ))));
                        }
                    }

                    // 2. AI Speaker Live Output Transcription
                    if (serverContent.has("outputTranscription")) {
                        String aiTranscript = serverContent.path("outputTranscription").path("text").asText();
                        log.info("[AI Speaker Transcribed Text]: {}", aiTranscript);
                        
                        if (activeClient != null && activeClient.isOpen()) {
                            activeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                                "type", "TRANSCRIPT_AI",
                                "text", aiTranscript
                            ))));
                        }
                    }

                    // 3. SERVER AUDIO OUTPUT (16-bit PCM Base64 -> Raw Binary Audio Bytes)
                    JsonNode parts = serverContent.path("modelTurn").path("parts");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            if (part.has("inlineData")) {
                                String base64Audio = part.path("inlineData").path("data").asText();
                                byte[] rawPcm = Base64.getDecoder().decode(base64Audio);

                                if (activeClient != null && activeClient.isOpen()) {
                                    log.info("[FORWARDING DECODED PCM AUDIO TO CLIENT]: {} bytes", rawPcm.length);
                                    activeClient.sendMessage(new BinaryMessage(rawPcm));
                                }
                            }
                        }
                    }

                    // 4. NATIVE BARGE-IN INTERRUPTION HANDLING
                    if (serverContent.path("interrupted").asBoolean(false)) {
                        log.info("Native barge-in detected by Gemini. Sending FLUSH signal to client.");
                        if (activeClient != null && activeClient.isOpen()) {
                            activeClient.sendMessage(new TextMessage("{\"type\":\"INTERRUPTED\"}"));
                        }
                    }
                }

                // SECTION 3: FUNCTION CALLING (toolCall Execution)
                if (root.has("toolCall")) {
                    JsonNode toolCallNode = root.path("toolCall");
                    JsonNode functionCalls = toolCallNode.path("functionCalls");
                    List<Map<String, Object>> functionResponses = new java.util.ArrayList<>();

                    if (functionCalls.isArray()) {
                        for (JsonNode functionCall : functionCalls) {
                            String callId = functionCall.path("id").asText();
                            String functionName = functionCall.path("name").asText();

                            log.info("Gemini Live issued toolCall '{}' (id: {})", functionName, callId);

                            if ("modifyFormLayout".equals(functionName)) {
                                String formId = (String) clientSession.getAttributes().get("formId");
                                String userIntent = functionCall.path("args").path("userIntent").asText();
                                String targetBlockId = functionCall.path("args").path("targetBlockId").asText(null);

                                eventPublisher.publishEvent(new FormLayoutModificationEvent(formId, userIntent, targetBlockId));

                                functionResponses.add(Map.of(
                                    "id", callId,
                                    "name", functionName,
                                    "response", Map.of("result", Map.of("status", "SUCCESS", "message", "Form layout modification executed"))
                                ));
                            } else if ("searchUserDocument".equals(functionName)) {
                                String query = functionCall.path("args").path("query").asText();
                                log.info("Executing searchUserDocument toolCall for query: {}", query);
                                functionResponses.add(Map.of(
                                    "id", callId,
                                    "name", functionName,
                                    "response", Map.of("result", Map.of("status", "SUCCESS", "content", "Document context retrieved for: " + query))
                                ));
                            } else {
                                functionResponses.add(Map.of(
                                    "id", callId,
                                    "name", functionName,
                                    "response", Map.of("result", Map.of("status", "SUCCESS"))
                                ));
                            }
                        }
                    }

                    if (!functionResponses.isEmpty()) {
                        Map<String, Object> toolResponseFrame = Map.of(
                            "toolResponse", Map.of(
                                "functionResponses", functionResponses
                            )
                        );
                        WebSocketSession activeGemini = (WebSocketSession) clientSession.getAttributes().get("geminiSession");
                        if (activeGemini != null && activeGemini.isOpen()) {
                            String json = objectMapper.writeValueAsString(toolResponseFrame);
                            activeGemini.sendMessage(new TextMessage(json));
                            log.info("[SENT TOOL RESPONSE TO GEMINI LIVE]: {}", json);
                        }
                    }
                }
            } else {
                // Raw Binary PCM Audio bytes from Google
                WebSocketSession activeClient = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
                if (activeClient != null && activeClient.isOpen()) {
                    log.info("[FORWARDING RAW BINARY PCM AUDIO TO CLIENT]: {} bytes", payload.length);
                    activeClient.sendMessage(new BinaryMessage(payload));
                }
            }
        } catch (Exception e) {
            log.error("Error processing Google WebSocket payload for user: {}", userId, e);
        }
    }

    @Override
    public void sendClientAudio(WebSocketSession clientSession, byte[] audioData) {
        WebSocketSession geminiSession = (WebSocketSession) clientSession.getAttributes().get("geminiSession");
        if (geminiSession != null && geminiSession.isOpen()) {
            try {
                String base64Audio = Base64.getEncoder().encodeToString(audioData);
                Map<String, Object> realtimeInputFrame = Map.of(
                    "realtimeInput", Map.of(
                        "audio", Map.of(
                            "mimeType", "audio/pcm;rate=16000",
                            "data", base64Audio
                        )
                    )
                );
                geminiSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(realtimeInputFrame)));
            } catch (IOException e) {
                log.error("Failed to send PCM audio frame to Gemini Live WSS", e);
            }
        }
    }

    @Override
    public void sendClientText(WebSocketSession clientSession, String text) {
        WebSocketSession geminiSession = (WebSocketSession) clientSession.getAttributes().get("geminiSession");
        if (geminiSession != null && geminiSession.isOpen()) {
            try {
                Map<String, Object> realtimeInputFrame = Map.of(
                    "realtimeInput", Map.of(
                        "text", text
                    )
                );
                String json = objectMapper.writeValueAsString(realtimeInputFrame);
                geminiSession.sendMessage(new TextMessage(json));
                log.info("[SENT TEXT TO GEMINI LIVE]: {}", text);
            } catch (IOException e) {
                log.error("Failed to send text frame to Gemini Live WSS", e);
            }
        }
    }

    @Override
    public void closeSession(WebSocketSession clientSession) {
        WebSocketSession geminiSession = (WebSocketSession) clientSession.getAttributes().remove("geminiSession");
        clientSession.getAttributes().remove("safeClientSession");

        if (geminiSession != null && geminiSession.isOpen()) {
            try {
                geminiSession.close();
                log.info("Gemini Live outbound WSS connection closed cleanly.");
            } catch (IOException e) {
                log.error("Error closing Gemini Live outbound WSS session", e);
            }
        }
    }
}
