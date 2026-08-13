package com.reForm.backend.ai.service;

import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.ai.strategy.stt.ISttProviderStrategy;
import com.reForm.backend.ai.strategy.tts.ITtsProviderStrategy;
import com.reForm.backend.ai.tool.registry.ToolCallRegistry;
import com.reForm.backend.ai.websocket.WebSocketSessionUtils;
import com.reForm.backend.form.entity.FormAiAgentProfile;
import com.reForm.backend.form.repository.FormAiAgentProfileRepository;
import com.reForm.backend.user.entity.Role;
import jakarta.annotation.PreDestroy;
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
 * ARCHITECTURE ROLE:
 * Orchestrates Mode 3 (Cascaded 3-Stage Pipeline):
 * 1. Speech-to-Text (STT Strategy) -> Transcribes client speech to text.
 * 2. LLM Reasoning (Gemini Flash REST) -> Processes text response & tool calls.
 * 3. Text-to-Speech (TTS Strategy) -> Synthesizes AI voice audio back to client.
 * 
 * REFACTORED FEATURES:
 * - Strategy Pattern for STT (ISttProviderStrategy) and TTS (ITtsProviderStrategy).
 * - Zero hardcoded URLs, zero hardcoded vendor schemas.
 * - Single Responsibility Principle (SRP) method decomposition.
 * - Spring-managed @PreDestroy graceful thread pool shutdown.
 */
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.repository.FormRepository;

@Slf4j
@Component("cascadedVoiceAdapter")
@RequiredArgsConstructor
public class CascadedVoiceAdapter implements IAiVoiceAdapter {

    public static final int BUFFER_10MB = WebSocketSessionUtils.BUFFER_10MB;

    private final SessionContextService sessionContextService;
    private final GeminiFlashRestService geminiFlashRestService;
    private final ToolCallRegistry toolCallRegistry;
    private final FormAiAgentProfileRepository profileRepository;
    private final FormRepository formRepository;
    private final ObjectMapper objectMapper;

    // Injected STT & TTS Strategy Beans (Strategy Pattern)
    private final List<ISttProviderStrategy> sttStrategies;
    private final List<ITtsProviderStrategy> ttsStrategies;

    @Value("${deepgram.api.key:DEFAULT_DEEPGRAM_KEY}")
    private String deepgramApiKey;

    @Value("${cartesia.api.key:DEFAULT_CARTESIA_KEY}")
    private String cartesiaApiKey;

    @Value("${voice.stt.default-strategy:DEEPGRAM_NOVA_3}")
    private String defaultSttStrategyKey;

    @Value("${voice.stt.sample-rate:16000}")
    private int sttSampleRate;

    @Value("${voice.stt.channels:1}")
    private int sttChannels;

    @Value("${voice.stt.utterance-end-ms:1000}")
    private int sttUtteranceEndMs;

    @Value("${voice.tts.default-strategy:CARTESIA_SONIC_3_5}")
    private String defaultTtsStrategyKey;

    @Value("${voice.tts.default-voice-id:a0e99841-438c-4a64-b679-ae501e7d6091}")
    private String defaultVoiceId;

    // Silent PCM keep-alive frame (100ms of silence at 16kHz 16-bit mono)
    private static final byte[] SILENT_PCM_FRAME = new byte[3200];

    // Thread pool for STT keep-alive pings
    private final ScheduledExecutorService keepAliveScheduler = Executors.newScheduledThreadPool(2);

    @PreDestroy
    public void destroy() {
        log.info("[MODE 3 LIFECYCLE]: Shutting down keep-alive executor service...");
        keepAliveScheduler.shutdown();
        try {
            if (!keepAliveScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                keepAliveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            keepAliveScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void startSession(String userId, WebSocketSession clientSession) {
        MDC.put("sessionId", userId);
        try {
            Role role = (Role) clientSession.getAttributes().get("role");
            String formId = (String) clientSession.getAttributes().get("formId");

            initSessionState(clientSession);
            FormAiAgentProfile profile = loadAgentProfile(formId);
            compilePromptAndTools(clientSession, role, profile);

            log.info("[MODE 3 CASCADED]: Initialized session for user: {}", userId);

            connectDeepgramStt(userId, clientSession);
            connectCartesiaTts(userId, clientSession);

        } finally {
            MDC.remove("sessionId");
        }
    }

    private void initSessionState(WebSocketSession clientSession) {
        WebSocketSession safeClientSession = WebSocketSessionUtils.wrapSafeSession(clientSession);
        clientSession.getAttributes().put("safeClientSession", safeClientSession);
        clientSession.getAttributes().put("mode3Active", true);
    }

    private FormAiAgentProfile loadAgentProfile(String formId) {
        if (formId != null && !formId.isBlank()) {
            try {
                return profileRepository.findByFormId(UUID.fromString(formId)).orElse(null);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private ConversationalBlock resolveActiveBlock(WebSocketSession clientSession) {
        String formId = (String) clientSession.getAttributes().get("formId");
        if (formId != null && !formId.isBlank()) {
            try {
                Form form = formRepository.findById(UUID.fromString(formId)).orElse(null);
                if (form != null && form.getBlocks() != null) {
                    for (AbstractBlock block : form.getBlocks()) {
                        if (block instanceof ConversationalBlock convBlock) {
                            return convBlock;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve active ConversationalBlock for formId: {}", formId, e);
            }
        }
        return null;
    }

    private void compilePromptAndTools(WebSocketSession clientSession, Role role, FormAiAgentProfile profile) {
        ConversationalBlock activeBlock = resolveActiveBlock(clientSession);
        if (activeBlock != null) {
            clientSession.getAttributes().put("activeBlock", activeBlock);
            if (activeBlock.getRagDocumentIds() != null && !activeBlock.getRagDocumentIds().isEmpty()) {
                clientSession.getAttributes().put("ragDocumentIds", activeBlock.getRagDocumentIds());
            }
        }

        String systemPrompt = sessionContextService.compileSystemInstruction(role, profile, activeBlock);
        List<Map<String, Object>> tools = sessionContextService.buildToolDeclarations(role, true, true, activeBlock);

        clientSession.getAttributes().put("systemPrompt", systemPrompt);
        clientSession.getAttributes().put("tools", tools);
    }

    private ISttProviderStrategy resolveSttStrategy(String requestedKey) {
        String targetKey = requestedKey != null ? requestedKey : defaultSttStrategyKey;
        return sttStrategies.stream()
                .filter(s -> s.supports(targetKey))
                .findFirst()
                .orElseGet(() -> sttStrategies.get(0));
    }

    private ITtsProviderStrategy resolveTtsStrategy(String requestedKey) {
        String targetKey = requestedKey != null ? requestedKey : defaultTtsStrategyKey;
        return ttsStrategies.stream()
                .filter(s -> s.supports(targetKey))
                .findFirst()
                .orElseGet(() -> ttsStrategies.get(0));
    }

    private void connectDeepgramStt(String userId, WebSocketSession clientSession) {
        ISttProviderStrategy strategy = resolveSttStrategy(defaultSttStrategyKey);
        Map<String, Object> options = Map.of(
            "sampleRate", sttSampleRate,
            "channels", sttChannels,
            "utteranceEndMs", sttUtteranceEndMs
        );

        String wssUrl = strategy.buildWebSocketUrl(deepgramApiKey, options);
        WebSocketHttpHeaders headers = strategy.buildHeaders(deepgramApiKey);

        try {
            cancelExistingKeepAliveTimer(clientSession);
            StandardWebSocketClient client = createConfiguredClient();

            client.execute(
                new DeepgramSttHandler(userId, clientSession),
                headers,
                URI.create(wssUrl)
            );
            log.info("[MODE 3 STT]: Outbound STT tunnel initialized ({}) for user: {}", strategy.getSttKey(), userId);
        } catch (Exception e) {
            log.error("[MODE 3 STT ERROR]: Connection failed for user: {}", userId, e);
        }
    }

    private void connectCartesiaTts(String userId, WebSocketSession clientSession) {
        ITtsProviderStrategy strategy = resolveTtsStrategy(defaultTtsStrategyKey);
        String wssUrl = strategy.buildWebSocketUrl(cartesiaApiKey);

        try {
            StandardWebSocketClient client = createConfiguredClient();
            client.execute(
                new CartesiaTtsHandler(userId, clientSession),
                null,
                URI.create(wssUrl)
            );
            log.info("[MODE 3 TTS]: Outbound TTS tunnel initialized ({}) for user: {}", strategy.getTtsKey(), userId);
        } catch (Exception e) {
            log.error("[MODE 3 TTS ERROR]: Connection failed for user: {}", userId, e);
        }
    }

    private StandardWebSocketClient createConfiguredClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(BUFFER_10MB);
        container.setDefaultMaxBinaryMessageBufferSize(BUFFER_10MB);
        return new StandardWebSocketClient(container);
    }

    private void cancelExistingKeepAliveTimer(WebSocketSession clientSession) {
        ScheduledFuture<?> existingTimer = (ScheduledFuture<?>) clientSession.getAttributes().remove("deepgramKeepAliveTimer");
        if (existingTimer != null) existingTimer.cancel(true);
    }

    @Override
    public void sendClientAudio(WebSocketSession clientSession, byte[] audioData) {
        WebSocketSession deepgramSession = (WebSocketSession) clientSession.getAttributes().get("deepgramSession");
        if (deepgramSession != null && deepgramSession.isOpen()) {
            try {
                deepgramSession.sendMessage(new BinaryMessage(audioData));
            } catch (IOException e) {
                log.error("[MODE 3 STT ERROR]: Failed to forward audio frame", e);
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
        closeOutboundSocket(clientSession, "deepgramSession", "[MODE 3 STT]");
        closeOutboundSocket(clientSession, "cartesiaSession", "[MODE 3 TTS]");
        clientSession.getAttributes().remove("safeClientSession");
    }

    private void closeOutboundSocket(WebSocketSession clientSession, String attributeKey, String logPrefix) {
        WebSocketSession outboundSession = (WebSocketSession) clientSession.getAttributes().remove(attributeKey);
        if (outboundSession != null && outboundSession.isOpen()) {
            try {
                outboundSession.close();
                log.info("{} Outbound WSS closed cleanly.", logPrefix);
            } catch (IOException e) {
                log.error("{} Error closing socket", logPrefix, e);
            }
        }
    }

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

    private void processGeminiRestResponse(String userId, WebSocketSession clientSession, JsonNode responseJson) {
        MDC.put("sessionId", userId);
        try {
            JsonNode candidates = responseJson.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) return;

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray()) return;

            for (JsonNode part : parts) {
                if (part.has("text")) {
                    handleAiTextPart(clientSession, part.get("text").asText());
                }

                if (part.has("functionCall")) {
                    handleAiToolCallPart(clientSession, part.get("functionCall"));
                }
            }
        } catch (Exception e) {
            log.error("Error processing Gemini REST response in Mode 3", e);
        } finally {
            MDC.remove("sessionId");
        }
    }

    private void handleAiTextPart(WebSocketSession clientSession, String aiText) {
        log.info("[MODE 3 AI TEXT RESPONSE]: {}", aiText);
        sendTranscriptToClient(clientSession, "TRANSCRIPT_AI", aiText);
        sendTextToCartesia(clientSession, aiText);
    }

    private void handleAiToolCallPart(WebSocketSession clientSession, JsonNode functionCall) {
        String functionName = functionCall.path("name").asText();
        String callId = UUID.randomUUID().toString();
        log.info("[MODE 3 TOOL CALL DETECTED]: {}", functionName);

        Map<String, Object> result = toolCallRegistry.executeTool(clientSession, functionCall, callId, functionName);
        log.info("[MODE 3 TOOL EXECUTED]: {}", result);
    }

    private void sendTextToCartesia(WebSocketSession clientSession, String text) {
        WebSocketSession cartesiaSession = (WebSocketSession) clientSession.getAttributes().get("cartesiaSession");
        if (cartesiaSession == null || !cartesiaSession.isOpen()) {
            log.warn("[MODE 3 TTS WARN]: Cartesia session unavailable.");
            return;
        }

        try {
            ITtsProviderStrategy strategy = resolveTtsStrategy(defaultTtsStrategyKey);
            String contextId = UUID.randomUUID().toString();

            clientSession.getAttributes().put("activeContextId", contextId);
            clientSession.getAttributes().put("isAiSpeaking", true);

            String jsonPayload = strategy.buildSynthesisPayload(objectMapper, text, defaultVoiceId, contextId);
            cartesiaSession.sendMessage(new TextMessage(jsonPayload));
            log.info("[MODE 3 TTS SENT]: Sent text chunk for voice synthesis");
        } catch (Exception e) {
            log.error("[MODE 3 TTS ERROR]: Failed to send frame to Cartesia WSS", e);
        }
    }

    private void triggerBargeIn(WebSocketSession clientSession) {
        Boolean isSpeaking = (Boolean) clientSession.getAttributes().getOrDefault("isAiSpeaking", false);
        if (Boolean.TRUE.equals(isSpeaking)) {
            log.info("[MODE 3 BARGE-IN TRIGGERED]: Interrupting active AI speech.");
            sendCartesiaCancelFrame(clientSession);
            sendClientFlushSignal(clientSession);
            clientSession.getAttributes().put("isAiSpeaking", false);
        }
    }

    private void sendCartesiaCancelFrame(WebSocketSession clientSession) {
        WebSocketSession cartesiaSession = (WebSocketSession) clientSession.getAttributes().get("cartesiaSession");
        String contextId = (String) clientSession.getAttributes().get("activeContextId");

        if (cartesiaSession != null && cartesiaSession.isOpen() && contextId != null) {
            try {
                ITtsProviderStrategy strategy = resolveTtsStrategy(defaultTtsStrategyKey);
                String cancelFrame = strategy.buildCancelPayload(objectMapper, contextId);
                cartesiaSession.sendMessage(new TextMessage(cancelFrame));
            } catch (Exception e) {
                log.error("Failed to send cancel frame to Cartesia TTS", e);
            }
        }
    }

    private void sendClientFlushSignal(WebSocketSession clientSession) {
        WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
        if (safeClientSession != null && safeClientSession.isOpen()) {
            try {
                safeClientSession.sendMessage(new TextMessage("{\"type\":\"INTERRUPTED\"}"));
                safeClientSession.sendMessage(new TextMessage("{\"type\":\"FLUSH_AUDIO_BUFFER\"}"));
            } catch (Exception e) {
                log.error("Failed to send FLUSH signal to client browser", e);
            }
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

    @RequiredArgsConstructor
    private class DeepgramSttHandler extends AbstractWebSocketHandler {
        private final String userId;
        private final WebSocketSession clientSession;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("[DEEPGRAM STT ESTABLISHED]: Socket 2 connected for user: {}", userId);
            WebSocketSession safeDeepgramSession = WebSocketSessionUtils.wrapSafeSession(session);
            clientSession.getAttributes().put("deepgramSession", safeDeepgramSession);

            ScheduledFuture<?> keepAliveTimer = keepAliveScheduler.scheduleAtFixedRate(
                () -> sendKeepAlivePing(userId, clientSession),
                5, 5, TimeUnit.SECONDS
            );
            clientSession.getAttributes().put("deepgramKeepAliveTimer", keepAliveTimer);
        }

        private void sendKeepAlivePing(String userId, WebSocketSession clientSession) {
            WebSocketSession dgSession = (WebSocketSession) clientSession.getAttributes().get("deepgramSession");
            if (dgSession != null && dgSession.isOpen()) {
                try {
                    dgSession.sendMessage(new BinaryMessage(SILENT_PCM_FRAME));
                } catch (Exception e) {
                    log.warn("[DEEPGRAM KEEP-ALIVE ERROR]: Failed to send silent frame for user: {}", userId);
                }
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload().trim();
            if (payload.startsWith("{") && payload.endsWith("}")) {
                JsonNode root = objectMapper.readTree(payload);
                evaluateBargeIn(root);
                extractFinalTranscript(root);
            }
        }

        private void evaluateBargeIn(JsonNode root) {
            if (root.path("speech_started").asBoolean(false) || "SpeechStarted".equalsIgnoreCase(root.path("type").asText())) {
                triggerBargeIn(clientSession);
            }
        }

        private void extractFinalTranscript(JsonNode root) {
            if (root.path("is_final").asBoolean(false)) {
                String transcript = root.path("channel").path("alternatives").path(0).path("transcript").asText();
                if (transcript != null && !transcript.isBlank()) {
                    log.info("[DEEPGRAM FINAL TRANSCRIPT]: {}", transcript);
                    onFinalTranscript(userId, clientSession, transcript);
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
            cancelExistingKeepAliveTimer(clientSession);
            clientSession.getAttributes().remove("deepgramSession");

            boolean clientStillActive = clientSession.isOpen() &&
                    Boolean.TRUE.equals(clientSession.getAttributes().get("mode3Active"));

            if (clientStillActive && status.getCode() != 1000) {
                log.info("[DEEPGRAM AUTO-RECONNECT]: Reconnecting Socket 2 after unexpected close ({})", status.getCode());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
                connectDeepgramStt(userId, clientSession);
            }
        }
    }

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

            forwardPcmToClient(clientSession, rawPcm);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload().trim();
            if (payload.startsWith("{") && payload.endsWith("}")) {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.path("type").asText("");

                if ("done".equalsIgnoreCase(type)) {
                    handleDoneFrame();
                } else if ("error".equalsIgnoreCase(type)) {
                    handleErrorFrame(root);
                } else if ("chunk".equalsIgnoreCase(type) || root.has("data")) {
                    handleAudioChunkFrame(root);
                }
            }
        }

        private void handleDoneFrame() {
            log.info("[CARTESIA TTS DONE]: Synthesis completed for user: {}", userId);
            clientSession.getAttributes().put("isAiSpeaking", false);
        }

        private void handleErrorFrame(JsonNode root) {
            log.error("[CARTESIA TTS ERROR]: user={}, error={}", userId, root.path("error").asText());
            clientSession.getAttributes().put("isAiSpeaking", false);
        }

        private void handleAudioChunkFrame(JsonNode root) {
            String base64Audio = root.path("data").asText("");
            if (!base64Audio.isBlank()) {
                try {
                    byte[] rawPcm = Base64.getDecoder().decode(base64Audio);
                    forwardPcmToClient(clientSession, rawPcm);
                } catch (Exception e) {
                    log.error("Failed to decode Cartesia Base64 audio chunk", e);
                }
            }
        }

        private void forwardPcmToClient(WebSocketSession clientSession, byte[] rawPcm) throws IOException {
            WebSocketSession safeClientSession = (WebSocketSession) clientSession.getAttributes().get("safeClientSession");
            if (safeClientSession != null && safeClientSession.isOpen()) {
                safeClientSession.sendMessage(new BinaryMessage(rawPcm));
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
