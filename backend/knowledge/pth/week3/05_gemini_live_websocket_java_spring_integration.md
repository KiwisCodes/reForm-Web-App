# Gemini Multimodal Live API: Java Spring Boot Integration Guide
**Document Version:** 2.0  
**Location:** `backend/knowledge/pth/week3/05_gemini_live_websocket_java_spring_integration.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Why Official Docs Only Show Python & JavaScript

Google's Gemini Live API documentation demonstrates examples in Python and Browser JavaScript for quickstart convenience. However, the API uses standard **WebSocket (RFC 6455)** over TCP.

Because WebSockets exchange JSON text frames and raw binary audio packets over standard TCP sockets, **Java & Spring Boot can connect natively using standard Java WebSocket clients**.

---

## 2. Complete Java Rosetta Stone Mapping

### A. Connection Setup Frame (`BidiGenerateContentSetup`)

```java
// Java Spring Boot (StandardWebSocketClient)
String googleWssUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=" + apiKey;

StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
this.geminiSession = webSocketClient.execute(new TextWebSocketHandler() {
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> setupPayload = sessionContextService.buildSetupContext(userId, role, formId);
        String setupJson = objectMapper.writeValueAsString(Map.of("setup", setupPayload));
        session.sendMessage(new TextMessage(setupJson));
    }
}, null, URI.create(googleWssUrl)).get();
```

### B. Sending Client PCM Audio (`realtimeInput`)

```java
// Encodes raw 16-bit PCM mic audio chunk to Base64 and sends realtimeInput frame
public void sendClientAudio(byte[] pcmData) throws IOException {
    String base64Audio = Base64.getEncoder().encodeToString(pcmData);
    Map<String, Object> frame = Map.of(
        "realtimeInput", Map.of(
            "mediaChunks", List.of(Map.of(
                "mimeType", "audio/pcm;rate=16000",
                "data", base64Audio
            ))
        )
    );
    geminiSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
}
```

### C. Processing Inbound Gemini Audio & Native Interruption

```java
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode root = objectMapper.readTree(message.getPayload());

    // 1. Audio Part Processing
    JsonNode parts = root.path("serverContent").path("modelTurn").path("parts");
    if (parts.isArray()) {
        for (JsonNode part : parts) {
            if (part.has("inlineData")) {
                String base64Audio = part.path("inlineData").path("data").asText();
                byte[] rawPcm = Base64.getDecoder().decode(base64Audio);
                clientSession.sendMessage(new BinaryMessage(rawPcm));
            }
        }
    }

    // 2. Native Interruption (barge-in) Handling
    if (root.path("serverContent").path("interrupted").asBoolean(false)) {
        clientSession.sendMessage(new TextMessage("{\"type\":\"INTERRUPTED\"}"));
    }
}
```
