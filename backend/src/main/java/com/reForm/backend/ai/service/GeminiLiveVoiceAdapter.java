package com.reForm.backend.ai.service;

import com.reForm.backend.ai.event.FormLayoutModificationEvent;
import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import com.reForm.backend.user.entity.Role;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * GEMINI LIVE VOICE ADAPTER (Outbound WSS Proxy Adapter)
 * 
 * WHAT IS THIS CLASS?
 * Implements IAiVoiceAdapter to proxy real-time audio streams, live transcriptions, 
 * barge-in signals, and function calling tool calls between client browsers and 
 * Google's Gemini Multimodal Live API (wss://generativelanguage.googleapis.com).
 * 
 * ARCHITECTURE & DESIGN:
 * 1. Centralized Buffer Factory: Shared 10MB constants and thread-safe session decorator helper.
 * 2. Named Outbound Handler: Dedicated GoogleBidiWebSocketHandler inner class managing Socket 2 lifecycle.
 * 3. Decomposed Payload Processor: Single-responsibility helper methods for JSON tagged union variants.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLiveVoiceAdapter implements IAiVoiceAdapter {

    // Centralized 10MB buffer limit and 10s write timeout for all WebSocket sessions
    public static final int BUFFER_10MB = WebSocketSessionUtils.BUFFER_10MB;
    public static final int SEND_TIMEOUT_MS = WebSocketSessionUtils.SEND_TIMEOUT_MS;

    private final SessionContextService sessionContextService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:DEFAULT_PLATFORM_KEY}")
    private String platformApiKey;

    @Override
    public void startSession(String userId, WebSocketSession clientSession) {
        WebSocketSession safeClientSession = WebSocketSessionUtils.wrapSafeSession(clientSession);
        clientSession.getAttributes().put("safeClientSession", safeClientSession);

        Role role = (Role) clientSession.getAttributes().get("role");
        String formId = (String) clientSession.getAttributes().get("formId");
        String requestedModelKey = (String) clientSession.getAttributes().get("modelKey");

        String apiKey = platformApiKey;
        String googleWssUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;

        log.info("Opening Gemini Live WSS tunnel for user: {} (Role: {}, FormId: {})", userId, role, formId);

        try {
            // Configure Tomcat Client WebSocket Container buffers
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(BUFFER_10MB);
            container.setDefaultMaxBinaryMessageBufferSize(BUFFER_10MB);

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient(container);
            webSocketClient.execute(
                new GoogleBidiWebSocketHandler(userId, clientSession, role, formId, requestedModelKey),
                null,
                URI.create(googleWssUrl)
            );

        } catch (Exception e) {
            log.error("Failed to establish outbound Gemini Live WSS session for user: {}", userId, e);
        }
    }

    /**
     * NAMED OUTBOUND WEBSOCKET HANDLER FOR GOOGLE GEMINI LIVE API (Socket 2)
     * Handles connection lifecycle and receives incoming responses over Socket 2.
     */
    @RequiredArgsConstructor
    private class GoogleBidiWebSocketHandler extends AbstractWebSocketHandler {
        private final String userId;
        private final WebSocketSession clientSession;
        private final Role role;
        private final String formId;
        private final String requestedModelKey;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("Outbound WebSocket connection to Google Gemini Live established for user: {}", userId);
            
            // Wrap outbound geminiSession in ConcurrentWebSocketSessionDecorator
            WebSocketSession safeGeminiSession = WebSocketSessionUtils.wrapSafeSession(session);
            clientSession.getAttributes().put("geminiSession", safeGeminiSession);

            // Build setup context payload and send setup JSON frame to Google
            Map<String, Object> setupPayload = sessionContextService.buildSetupContext(userId, role, formId, requestedModelKey);
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
            ByteBuffer buffer = message.getPayload();
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
    }

    /**
     * MAIN PAYLOAD DISPATCHER
     * Routes incoming frames to binary audio handler or JSON tagged union dispatcher.
     */
    private void processGooglePayload(String userId, WebSocketSession clientSession, WebSocketSession geminiSession, byte[] payload) {
        MDC.put("sessionId", userId);
        try {
            String payloadStr = new String(payload, StandardCharsets.UTF_8).trim();

            if (payloadStr.startsWith("{") && payloadStr.endsWith("}")) {
                log.info("[RAW GEMINI JSON RESPONSE]: {}", payloadStr);
                JsonNode root = objectMapper.readTree(payloadStr);
                handleJsonPayload(userId, clientSession, geminiSession, root);
            } else {
                handleRawBinaryAudio(clientSession, payload);
            }
        } catch (Exception e) {
            log.error("Error processing Google WebSocket payload for user: {}", userId, e);
        } finally {
            MDC.remove("sessionId");
        }
    }

    /**
     * HANDLER 1: RAW BINARY PCM AUDIO FORWARDING
     */
    private void handleRawBinaryAudio(WebSocketSession clientSession, byte[] rawBytes) throws IOException {
        WebSocketSession activeClient = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
        if (activeClient != null && activeClient.isOpen()) {
            log.info("[FORWARDING RAW BINARY PCM AUDIO TO CLIENT]: {} bytes", rawBytes.length);
            activeClient.sendMessage(new BinaryMessage(rawBytes));
        }
    }

    /**
     * HANDLER 2: POLYMORPHIC JSON TAGGED UNION DISPATCHER
     */
    private void handleJsonPayload(String userId, WebSocketSession clientSession, WebSocketSession geminiSession, JsonNode root) throws IOException {
        // VARIANT 1: SETUP COMPLETION ACK (`BidiGenerateContentSetupComplete`)
        if (root.has("setupComplete")) {
            handleSetupComplete(userId);
        }

        // VARIANT 2: REAL-TIME CONTENT GENERATION (`BidiGenerateContentServerContent`)
        if (root.has("serverContent")) {
            handleServerContent(clientSession, root.get("serverContent"));
        }

        // VARIANT 3: FUNCTION CALLING REQUEST (`BidiGenerateContentToolCall`)
        if (root.has("toolCall")) {
            handleToolCall(clientSession, geminiSession, root.path("toolCall"));
        }
    }

    /**
     * HELPER 2A: SETUP COMPLETE ACK
     */
    private void handleSetupComplete(String userId) {
        log.info("✅ Google Gemini Live Setup Complete for user: {}", userId);
    }

    /**
     * HELPER 2B: SERVER CONTENT PROCESSOR
     * Handles user transcriptions, AI transcriptions, 24kHz PCM audio decoding, and native barge-in.
     */
    private void handleServerContent(WebSocketSession clientSession, JsonNode serverContent) throws IOException {
        WebSocketSession activeClient = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
        if (activeClient == null || !activeClient.isOpen()) {
            return;
        }

        extractUserTranscript(activeClient, serverContent);
        extractAiTranscript(activeClient, serverContent);
        decodeAndForwardPcmAudio(activeClient, serverContent);
        handleBargeInInterruption(activeClient, serverContent);
    }

    /**
     * SUB-HELPER: USER TRANSCRIPTION
     */
    private void extractUserTranscript(WebSocketSession activeClient, JsonNode serverContent) throws IOException {
        if (serverContent.has("inputTranscription")) {
            String userTranscript = serverContent.path("inputTranscription").path("text").asText();
            log.info("[Candidate Transcribed Text]: {}", userTranscript);
            activeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "TRANSCRIPT_USER",
                "text", userTranscript
            ))));
        }
    }

    /**
     * SUB-HELPER: AI TRANSCRIPTION
     */
    private void extractAiTranscript(WebSocketSession activeClient, JsonNode serverContent) throws IOException {
        if (serverContent.has("outputTranscription")) {
            String aiTranscript = serverContent.path("outputTranscription").path("text").asText();
            log.info("[AI Speaker Transcribed Text]: {}", aiTranscript);
            activeClient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "TRANSCRIPT_AI",
                "text", aiTranscript
            ))));
        }
    }

    /**
     * SUB-HELPER: DECODE BASE64 PCM AUDIO TO RAW BINARY BYTES
     */
    private void decodeAndForwardPcmAudio(WebSocketSession activeClient, JsonNode serverContent) throws IOException {
        JsonNode parts = serverContent.path("modelTurn").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("inlineData")) {
                    String base64Audio = part.path("inlineData").path("data").asText();
                    byte[] rawPcm = Base64.getDecoder().decode(base64Audio);
                    log.info("[FORWARDING DECODED PCM AUDIO TO CLIENT]: {} bytes", rawPcm.length);
                    activeClient.sendMessage(new BinaryMessage(rawPcm));
                }
            }
        }
    }

    /**
     * SUB-HELPER: NATIVE BARGE-IN INTERRUPTION FLUSH SIGNAL
     */
    private void handleBargeInInterruption(WebSocketSession activeClient, JsonNode serverContent) throws IOException {
        if (serverContent.path("interrupted").asBoolean(false)) {
            log.info("Native barge-in detected by Gemini. Sending FLUSH signal to client.");
            activeClient.sendMessage(new TextMessage("{\"type\":\"INTERRUPTED\"}"));
        }
    }

    /**
     * HELPER 2C: FUNCTION CALLING TOOL CALL PROCESSOR
     */
    private void handleToolCall(WebSocketSession clientSession, WebSocketSession geminiSession, JsonNode toolCallNode) throws IOException {
        JsonNode functionCalls = toolCallNode.path("functionCalls");
        List<Map<String, Object>> functionResponses = new ArrayList<>();

        if (functionCalls.isArray()) {
            for (JsonNode functionCall : functionCalls) {
                String callId = functionCall.path("id").asText();
                String functionName = functionCall.path("name").asText();
                log.info("Gemini Live issued toolCall '{}' (id: {})", functionName, callId);

                Map<String, Object> responseMap = processFunctionCall(clientSession, functionCall, callId, functionName);
                functionResponses.add(responseMap);
            }
        }

        if (!functionResponses.isEmpty()) {
            sendToolResponseFrame(geminiSession, functionResponses);
        }
    }

    /**
     * SUB-HELPER: EXECUTE SPECIFIC TOOL CALL
     */
    private Map<String, Object> processFunctionCall(WebSocketSession clientSession, JsonNode functionCall, String callId, String functionName) {
        if ("modifyFormLayout".equals(functionName)) {
            String formId = (String) clientSession.getAttributes().get("formId");
            String userIntent = functionCall.path("args").path("userIntent").asText();
            String targetBlockId = functionCall.path("args").path("targetBlockId").asText(null);

            eventPublisher.publishEvent(new FormLayoutModificationEvent(formId, userIntent, targetBlockId));

            return Map.of(
                "id", callId,
                "name", functionName,
                "response", Map.of("result", Map.of("status", "SUCCESS", "message", "Form layout modification executed"))
            );
        } else if ("searchUserDocument".equals(functionName)) {
            String query = functionCall.path("args").path("query").asText();
            log.info("Executing searchUserDocument toolCall for query: {}", query);
            return Map.of(
                "id", callId,
                "name", functionName,
                "response", Map.of("result", Map.of("status", "SUCCESS", "content", "Document context retrieved for: " + query))
            );
        } else {
            return Map.of(
                "id", callId,
                "name", functionName,
                "response", Map.of("result", Map.of("status", "SUCCESS"))
            );
        }
    }

    /**
     * SUB-HELPER: SEND TOOL RESPONSE FRAME BACK TO GOOGLE WSS
     */
    private void sendToolResponseFrame(WebSocketSession geminiSession, List<Map<String, Object>> functionResponses) throws IOException {
        WebSocketSession activeGemini = WebSocketSessionUtils.wrapSafeSession(geminiSession);
        if (activeGemini.isOpen()) {
            Map<String, Object> toolResponseFrame = Map.of(
                "toolResponse", Map.of("functionResponses", functionResponses)
            );
            String json = objectMapper.writeValueAsString(toolResponseFrame);
            activeGemini.sendMessage(new TextMessage(json));
            log.info("[SENT TOOL RESPONSE TO GEMINI LIVE]: {}", json);
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
