package com.reForm.backend.ai.service;

import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.ai.tool.registry.ToolCallRegistry;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.user.entity.Role;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * CASCADED VOICE ADAPTER (Mode 3 Voice Architecture Strategy)
 * 
 * WHAT IS THIS CLASS?
 * Implements IAiVoiceAdapter for Mode 3 (Cascaded 3-Stage Pipeline):
 * 1. Deepgram Nova-3 STT (WebSocket) -> Transcribes client speech to text in ~100ms.
 * 2. Gemini 3.6 Flash LLM (HTTP REST) -> Processes text response & tool calls in ~400ms.
 * 3. Cartesia Sonic TTS (WebSocket) -> Synthesizes AI voice audio back to client in ~200ms.
 * 
 * ARCHITECTURE ROLE:
 * Registered with bean name "cascadedVoiceAdapter" so AiVoiceAdapterFactory can
 * dynamically inject it whenever a connection specifies ?mode=MODE_3.
 */
@Slf4j
@Component("cascadedVoiceAdapter")
@RequiredArgsConstructor
public class CascadedVoiceAdapter implements IAiVoiceAdapter {

    public static final int BUFFER_10MB = WebSocketSessionUtils.BUFFER_10MB;

    private final SessionContextService sessionContextService;
    private final GeminiFlashRestService geminiFlashRestService;
    private final ToolCallRegistry toolCallRegistry;
    private final FormAiAgentProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    @Value("${deepgram.api.key:DEFAULT_DEEPGRAM_KEY}")
    private String deepgramApiKey;

    @Value("${cartesia.api.key:DEFAULT_CARTESIA_KEY}")
    private String cartesiaApiKey;

    // Cartesia default English male voice ID fallback
    private static final String DEFAULT_CARTESIA_VOICE_ID = "a0e99841-438c-4a64-b679-ae501e7d6091";

    // Silent PCM keep-alive frame (160 bytes = 10ms of silence at 16kHz 16-bit mono)
    // Sent to Deepgram every 5 seconds when no mic audio is flowing, prevents server-side idle timeout (code=1011)
    private static final byte[] SILENT_PCM_FRAME = new byte[3200]; // 100ms of silence

    // Shared scheduler for Deepgram keep-alive across all sessions
    private final ScheduledExecutorService keepAliveScheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void startSession(String userId, WebSocketSession clientSession) {
        MDC.put("sessionId", userId);
        try {
            WebSocketSession safeClientSession = WebSocketSessionUtils.wrapSafeSession(clientSession);
            clientSession.getAttributes().put("safeClientSession", safeClientSession);
            clientSession.getAttributes().put("mode3Active", true);

            Role role = (Role) clientSession.getAttributes().get("role");
            String formId = (String) clientSession.getAttributes().get("formId");

            // Step 1: Query form agent profile from DB if formId is present
            FormAiAgentProfile profile = null;
            if (formId != null && !formId.isBlank()) {
                try {
                    profile = profileRepository.findByFormId(UUID.fromString(formId)).orElse(null);
                } catch (Exception ignored) {}
            }

            // Step 2: Compile persona system prompt and tool declarations
            String systemPrompt = sessionContextService.compileSystemInstruction(role, profile, null);
            List<Map<String, Object>> tools = sessionContextService.buildToolDeclarations(role, true, true);

            clientSession.getAttributes().put("systemPrompt", systemPrompt);
            clientSession.getAttributes().put("tools", tools);

            log.info("[MODE 3 CASCADED]: Initializing Cascaded Voice session (Deepgram STT -> Gemini 3.6 -> Cartesia TTS) for user: {}", userId);

            // Step 3: Establish Deepgram Nova-3 WSS Tunnel (Socket 2)
            connectDeepgramStt(userId, clientSession);

            // Step 4: Establish Cartesia Sonic TTS WSS Tunnel (Socket 3)
            connectCartesiaTts(userId, clientSession);

        } finally {
            MDC.remove("sessionId");
        }
    }

    private void connectDeepgramStt(String userId, WebSocketSession clientSession) {
        String deepgramWssUrl = "wss://api.deepgram.com/v1/listen?encoding=linear16&sample_rate=16000&channels=1&model=nova-3&punctuate=true&interim_results=true&utterance_end_ms=1000&no_delay=true";
        try {
            // Cancel any existing keep-alive timer for this session before reconnecting
            ScheduledFuture<?> existingTimer = (ScheduledFuture<?>) clientSession.getAttributes().remove("deepgramKeepAliveTimer");
            if (existingTimer != null) existingTimer.cancel(true);

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(BUFFER_10MB);
            container.setDefaultMaxBinaryMessageBufferSize(BUFFER_10MB);

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient(container);
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("Authorization", "Token " + deepgramApiKey);

            webSocketClient.execute(
                new DeepgramSttHandler(userId, clientSession),
                headers,
                URI.create(deepgramWssUrl)
            );
            log.info("[MODE 3 STT]: Outbound Deepgram Nova-3 WSS tunnel initiated for user: {}", userId);
        } catch (Exception e) {
            log.error("[MODE 3 STT ERROR]: Failed to connect to Deepgram WSS for user: {}", userId, e);
        }
    }

    private void connectCartesiaTts(String userId, WebSocketSession clientSession) {
        String cartesiaWssUrl = "wss://api.cartesia.ai/tts/websocket?api_key=" + cartesiaApiKey + "&cartesia_version=2024-06-10";
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(BUFFER_10MB);
            container.setDefaultMaxBinaryMessageBufferSize(BUFFER_10MB);

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient(container);

            webSocketClient.execute(
                new CartesiaTtsHandler(userId, clientSession),
                null,
                URI.create(cartesiaWssUrl)
            );
            log.info("[MODE 3 TTS]: Outbound Cartesia Sonic WSS tunnel initiated for user: {}", userId);
        } catch (Exception e) {
            log.error("[MODE 3 TTS ERROR]: Failed to connect to Cartesia WSS for user: {}", userId, e);
        }
    }

    @Override
    public void sendClientAudio(WebSocketSession clientSession, byte[] audioData) {
        WebSocketSession deepgramSession = (WebSocketSession) clientSession.getAttributes().get("deepgramSession");
        if (deepgramSession != null && deepgramSession.isOpen()) {
            try {
                deepgramSession.sendMessage(new BinaryMessage(audioData));
            } catch (IOException e) {
                log.error("[MODE 3 STT ERROR]: Failed to forward audio frame to Deepgram", e);
            }
        }
    }

    @Override
    public void sendClientText(WebSocketSession clientSession, String text) {
        String userId = (String) clientSession.getAttributes().get("userId");
        MDC.put("sessionId", userId);
        try {
            log.info("[MODE 3 TEXT INPUT]: Processing text turn: '{}'", text);
            onFinalTranscript(userId, clientSession, text);
        } finally {
            MDC.remove("sessionId");
        }
    }

    @Override
    public void closeSession(WebSocketSession clientSession) {
        WebSocketSession deepgramSession = (WebSocketSession) clientSession.getAttributes().remove("deepgramSession");
        WebSocketSession cartesiaSession = (WebSocketSession) clientSession.getAttributes().remove("cartesiaSession");
        clientSession.getAttributes().remove("safeClientSession");

        if (deepgramSession != null && deepgramSession.isOpen()) {
            try {
                deepgramSession.close();
                log.info("[MODE 3 STT]: Deepgram WSS closed cleanly.");
            } catch (IOException e) {
                log.error("Error closing Deepgram session", e);
            }
        }

        if (cartesiaSession != null && cartesiaSession.isOpen()) {
            try {
                cartesiaSession.close();
                log.info("[MODE 3 TTS]: Cartesia WSS closed cleanly.");
            } catch (IOException e) {
                log.error("Error closing Cartesia session", e);
            }
        }
    }

    /**
     * PIPELINE INTERMEDIARY: FINAL USER TRANSCRIPT RECEIVED
     * Forwards transcript to Gemini 3.6 Flash REST service.
     */
    private void onFinalTranscript(String userId, WebSocketSession clientSession, String finalTranscript) {
        if (finalTranscript == null || finalTranscript.isBlank()) return;

        MDC.put("sessionId", userId);
        try {
            sendTranscriptToClient(clientSession, "TRANSCRIPT_USER", finalTranscript);

            String systemPrompt = (String) clientSession.getAttributes().get("systemPrompt");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) clientSession.getAttributes().get("tools");

            geminiFlashRestService.callGemini(systemPrompt, finalTranscript, tools)
                    .subscribe(
                        responseJson -> processGeminiRestResponse(userId, clientSession, responseJson),
                        error -> log.error("[MODE 3 GEMINI ERROR]: REST invocation failed for user: {}", userId, error)
                    );
        } finally {
            MDC.remove("sessionId");
        }
    }

    /**
     * PIPELINE INTERMEDIARY: GEMINI REST RESPONSE PROCESSOR
     */
    private void processGeminiRestResponse(String userId, WebSocketSession clientSession, JsonNode responseJson) {
        MDC.put("sessionId", userId);
        try {
            JsonNode candidates = responseJson.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) return;

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray()) return;

            for (JsonNode part : parts) {
                // VARIANT 1: TEXT RESPONSE
                if (part.has("text")) {
                    String aiText = part.get("text").asText();
                    log.info("[MODE 3 AI TEXT RESPONSE]: {}", aiText);
                    sendTranscriptToClient(clientSession, "TRANSCRIPT_AI", aiText);
                    sendTextToCartesia(clientSession, aiText);
                }

                // VARIANT 2: FUNCTION CALL / TOOL CALL
                if (part.has("functionCall")) {
                    JsonNode functionCall = part.get("functionCall");
                    String functionName = functionCall.path("name").asText();
                    String callId = UUID.randomUUID().toString();
                    log.info("[MODE 3 TOOL CALL DETECTED]: {}", functionName);

                    Map<String, Object> result = toolCallRegistry.executeTool(clientSession, functionCall, callId, functionName);
                    log.info("[MODE 3 TOOL EXECUTED]: {}", result);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Gemini REST response in Mode 3", e);
        } finally {
            MDC.remove("sessionId");
        }
    }

    /**
     * PIPELINE INTERMEDIARY: SEND AI TEXT TO CARTESIA TTS WSS
     */
    private void sendTextToCartesia(WebSocketSession clientSession, String text) {
        WebSocketSession cartesiaSession = (WebSocketSession) clientSession.getAttributes().get("cartesiaSession");
        if (cartesiaSession == null || !cartesiaSession.isOpen()) {
            log.warn("[MODE 3 TTS WARN]: Cartesia session unavailable for text synthesis.");
            return;
        }

        try {
            String contextId = UUID.randomUUID().toString();
            clientSession.getAttributes().put("activeContextId", contextId);
            clientSession.getAttributes().put("isAiSpeaking", true);

            // Cartesia 2026 model_id: "sonic-3.5" (current stable) or "sonic-latest"
            // See: https://docs.cartesia.ai
            Map<String, Object> cartesiaFrame = Map.of(
                "model_id", "sonic-3.5",
                "transcript", text,
                "voice", Map.of(
                    "mode", "id",
                    "id", DEFAULT_CARTESIA_VOICE_ID
                ),
                "output_format", Map.of(
                    "container", "raw",
                    "encoding", "pcm_s16le",
                    "sample_rate", 24000
                ),
                "context_id", contextId
            );

            String jsonPayload = objectMapper.writeValueAsString(cartesiaFrame);
            cartesiaSession.sendMessage(new TextMessage(jsonPayload));
            log.info("[MODE 3 TTS SENT TO CARTESIA]: Sent text chunk for voice synthesis");
        } catch (Exception e) {
            log.error("[MODE 3 TTS ERROR]: Failed to send frame to Cartesia WSS", e);
        }
    }

    /**
     * BARGE-IN INTERRUPTION MECHANISM
     * Flushes active Cartesia audio synthesis & emits FLUSH signal to client browser.
     */
    private void triggerBargeIn(WebSocketSession clientSession) {
        Boolean isSpeaking = (Boolean) clientSession.getAttributes().getOrDefault("isAiSpeaking", false);
        if (Boolean.TRUE.equals(isSpeaking)) {
            log.info("[MODE 3 BARGE-IN TRIGGERED]: Interrupting active AI speech.");
            
            WebSocketSession cartesiaSession = (WebSocketSession) clientSession.getAttributes().get("cartesiaSession");
            String contextId = (String) clientSession.getAttributes().get("activeContextId");

            if (cartesiaSession != null && cartesiaSession.isOpen() && contextId != null) {
                try {
                    String cancelFrame = objectMapper.writeValueAsString(Map.of("context_id", contextId, "cancel", true));
                    cartesiaSession.sendMessage(new TextMessage(cancelFrame));
                } catch (Exception e) {
                    log.error("Failed to send cancel frame to Cartesia TTS", e);
                }
            }

            // Flush client browser audio playback
            WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
            if (safeClientSession != null && safeClientSession.isOpen()) {
                try {
                    safeClientSession.sendMessage(new TextMessage("{\"type\":\"INTERRUPTED\"}"));
                    safeClientSession.sendMessage(new TextMessage("{\"type\":\"FLUSH_AUDIO_BUFFER\"}"));
                } catch (Exception e) {
                    log.error("Failed to send FLUSH signal to client browser", e);
                }
            }

            clientSession.getAttributes().put("isAiSpeaking", false);
        }
    }

    private void sendTranscriptToClient(WebSocketSession clientSession, String type, String text) {
        WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
        if (safeClientSession != null && safeClientSession.isOpen()) {
            try {
                safeClientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "text", text
                ))));
            } catch (IOException e) {
                log.error("Failed to send transcript frame to client browser", e);
            }
        }
    }

    /**
     * NAMED OUTBOUND HANDLER FOR DEEPGRAM STT WSS (Socket 2)
     */
    @RequiredArgsConstructor
    private class DeepgramSttHandler extends AbstractWebSocketHandler {
        private final String userId;
        private final WebSocketSession clientSession;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("[DEEPGRAM STT ESTABLISHED]: Socket 2 connected for user: {}", userId);
            WebSocketSession safeDeepgramSession = WebSocketSessionUtils.wrapSafeSession(session);
            clientSession.getAttributes().put("deepgramSession", safeDeepgramSession);

            // Start keep-alive timer: send 100ms silent PCM frame every 5 seconds
            // This prevents Deepgram server-side idle timeout (code=1011) when user is not speaking
            ScheduledFuture<?> keepAliveTimer = keepAliveScheduler.scheduleAtFixedRate(() -> {
                WebSocketSession dgSession = (WebSocketSession) clientSession.getAttributes().get("deepgramSession");
                if (dgSession != null && dgSession.isOpen()) {
                    try {
                        dgSession.sendMessage(new BinaryMessage(SILENT_PCM_FRAME));
                    } catch (Exception e) {
                        log.warn("[DEEPGRAM KEEP-ALIVE ERROR]: Failed to send silent frame for user: {}", userId);
                    }
                }
            }, 5, 5, TimeUnit.SECONDS);

            clientSession.getAttributes().put("deepgramKeepAliveTimer", keepAliveTimer);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload().trim();
            if (payload.startsWith("{") && payload.endsWith("}")) {
                JsonNode root = objectMapper.readTree(payload);

                // Barge-in check: speech_started
                if (root.path("speech_started").asBoolean(false) || "SpeechStarted".equalsIgnoreCase(root.path("type").asText())) {
                    triggerBargeIn(clientSession);
                }

                // Final transcript check
                if (root.path("is_final").asBoolean(false)) {
                    String transcript = root.path("channel").path("alternatives").path(0).path("transcript").asText();
                    if (transcript != null && !transcript.isBlank()) {
                        log.info("[DEEPGRAM FINAL TRANSCRIPT]: {}", transcript);
                        onFinalTranscript(userId, clientSession, transcript);
                    }
                }
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("[DEEPGRAM STT TRANSPORT ERROR]: user={}", userId, exception);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            log.warn("[DEEPGRAM STT CLOSED]: user={}, status={}", userId, status);

            // Cancel the keep-alive timer
            ScheduledFuture<?> timer = (ScheduledFuture<?>) clientSession.getAttributes().remove("deepgramKeepAliveTimer");
            if (timer != null) timer.cancel(true);
            clientSession.getAttributes().remove("deepgramSession");

            // Auto-reconnect if the client WebSocket is still active (Deepgram timeout should not kill the interview!)
            // code=1011 = Deepgram server-side idle timeout, code=1000 = clean intentional close
            boolean clientStillActive = clientSession.isOpen() &&
                    Boolean.TRUE.equals(clientSession.getAttributes().get("mode3Active"));

            if (clientStillActive && status.getCode() != 1000) {
                log.info("[DEEPGRAM AUTO-RECONNECT]: Client still active after unexpected Deepgram close ({}). Reconnecting Socket 2...", status.getCode());
                try {
                    Thread.sleep(500); // Brief 500ms backoff before reconnect
                } catch (InterruptedException ignored) {}
                connectDeepgramStt(userId, clientSession);
            }
        }
    }

    /**
     * NAMED OUTBOUND HANDLER FOR CARTESIA TTS WSS (Socket 3)
     */
    @RequiredArgsConstructor
    private class CartesiaTtsHandler extends AbstractWebSocketHandler {
        private final String userId;
        private final WebSocketSession clientSession;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("[CARTESIA TTS ESTABLISHED]: Socket 3 connected for user: {}", userId);
            WebSocketSession safeCartesiaSession = WebSocketSessionUtils.wrapSafeSession(session);
            clientSession.getAttributes().put("cartesiaSession", safeCartesiaSession);
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
            ByteBuffer buffer = message.getPayload();
            byte[] rawPcm = new byte[buffer.remaining()];
            buffer.get(rawPcm);

            // Forward raw 24kHz PCM audio bytes directly to client browser
            WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
            if (safeClientSession != null && safeClientSession.isOpen()) {
                safeClientSession.sendMessage(new BinaryMessage(rawPcm));
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload().trim();

            if (payload.startsWith("{") && payload.endsWith("}")) {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.path("type").asText("");

                if ("done".equalsIgnoreCase(type)) {
                    log.info("[CARTESIA TTS DONE]: Synthesis context completed for user: {}", userId);
                    clientSession.getAttributes().put("isAiSpeaking", false);

                } else if ("error".equalsIgnoreCase(type)) {
                    log.error("[CARTESIA TTS API ERROR]: user={}, error={}", userId, root.path("error").asText());
                    clientSession.getAttributes().put("isAiSpeaking", false);

                } else if ("chunk".equalsIgnoreCase(type) || root.has("data")) {
                    String base64Audio = root.path("data").asText("");
                    if (!base64Audio.isBlank()) {
                        try {
                            byte[] rawPcm = Base64.getDecoder().decode(base64Audio);
                            WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
                            if (safeClientSession != null && safeClientSession.isOpen()) {
                                log.debug("[FORWARDING PCM AUDIO TO CLIENT]: {} bytes", rawPcm.length);
                                safeClientSession.sendMessage(new BinaryMessage(rawPcm));
                            }
                        } catch (Exception e) {
                            log.error("Failed to decode Cartesia Base64 audio chunk", e);
                        }
                    }
                } else {
                    log.info("[CARTESIA TTS TEXT FRAME]: {}", payload);
                }
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("[CARTESIA TTS TRANSPORT ERROR]: user={}", userId, exception);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            log.warn("[CARTESIA TTS CLOSED]: user={}, status={}", userId, status);
        }
    }
}
