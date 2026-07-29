# 10. Mode 4 Gemini Live Voice Implementation Retrospective & Complete Architecture Specification

**Document Version:** 3.0  
**Target System:** reForm Monolith (`com.reForm.backend.ai` & Next.js Frontend)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Official Google Gemini Live API Reference Documentation

*(Preserved verbatim from Google Official API Documentation provided during feature construction)*

### Overview
The Gemini Live API allows for real-time, bidirectional interaction with Gemini models, supporting audio, video, and text inputs and native audio outputs. This guide explains how to integrate directly with the API using raw WebSockets.

The Gemini Live API uses WebSockets for real-time communication. Unlike using an SDK, this approach involves directly managing the WebSocket connection and sending/receiving messages in a specific JSON format defined by the API.

Key concepts:
- **WebSocket Endpoint**: The specific URL to connect to.
- **Message Format**: All communication is done via JSON messages conforming to `BidiGenerateContentClientMessage` and `BidiGenerateContentServerMessage` structures.
- **Session Management**: You are responsible for maintaining the WebSocket connection.

---

### Authentication & Full Endpoint URLs

#### A. Standard API Key Authentication Endpoint (Used by reForm Backend Proxy)
Authentication is handled by including your API key as a query parameter in the WebSocket URL:

```text
wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=YOUR_API_KEY
```
* **Protocol:** Encrypted WebSockets (`wss://`)
* **Host:** `generativelanguage.googleapis.com`
* **Path:** `/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`
* **Query Parameter:** `key=YOUR_API_KEY`

#### B. Ephemeral Token Authentication Endpoint
If using ephemeral tokens, connect to the `v1beta` endpoint passing the token as `access_token`:

```text
wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained?access_token={short-lived-token}
```

#### C. Internal Platform Inbound Endpoint (Client Browser -> reForm Monolith)
```text
ws://localhost:8080/ws/v1/voice?token=test_token
```

---

### Official API Code Examples & Payload Specs

#### 1. Session Setup (`BidiGenerateContentSetup`)
The first message sent over the WebSocket must be `BidiGenerateContentSetup` containing the configuration payload.

**Python:**
```python
import asyncio
import websockets
import json

API_KEY = "YOUR_API_KEY"
MODEL_NAME = "gemini-3.1-flash-live-preview"
WS_URL = f"wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={API_KEY}"

async def connect_and_configure():
    async with websockets.connect(WS_URL) as websocket:
        print("WebSocket Connected")

        setup_message = {
            "setup": {
                "model": f"models/{MODEL_NAME}",
                "responseModalities": ["AUDIO"],
                "systemInstruction": {
                    "parts": [{"text": "You are a helpful assistant."}]
                }
            }
        }
        await websocket.send(json.dumps(setup_message))
        print("Configuration sent")
        await asyncio.sleep(3600)
```

**JavaScript:**
```javascript
const API_KEY = "YOUR_API_KEY";
const MODEL_NAME = "gemini-3.1-flash-live-preview";
const WS_URL = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${API_KEY}`;

const websocket = new WebSocket(WS_URL);

websocket.onopen = () => {
  console.log('WebSocket Connected');
  const setupMessage = {
    setup: {
      model: `models/${MODEL_NAME}`,
      responseModalities: ['AUDIO'],
      systemInstruction: {
        parts: [{ text: 'You are a helpful assistant.' }]
      }
    }
  };
  websocket.send(JSON.stringify(setupMessage));
};
```

#### 2. Send Text (`BidiGenerateContentRealtimeInput`)
**Python:**
```python
async def send_text(websocket, text):
    text_message = {
        "realtimeInput": {
            "text": text
        }
    }
    await websocket.send(json.dumps(text_message))
```

**JavaScript:**
```javascript
function sendTextMessage(text) {
  if (websocket.readyState === WebSocket.OPEN) {
    const textMessage = {
      realtimeInput: {
        text: text
      }
    };
    websocket.send(JSON.stringify(textMessage));
  }
}
```

#### 3. Send Audio (`BidiGenerateContentRealtimeInput`)
Audio must be sent as raw PCM data (16-bit PCM, 16kHz, Little-Endian).

**Python:**
```python
async def send_audio_chunk(websocket, chunk_bytes):
    import base64
    encoded_data = base64.b64encode(chunk_bytes).decode('utf-8')
    audio_message = {
        "realtimeInput": {
            "audio": {
                "data": encoded_data,
                "mimeType": "audio/pcm;rate=16000"
            }
        }
    }
    await websocket.send(json.dumps(audio_message))
```

**JavaScript:**
```javascript
function sendAudioChunk(chunk) {
  if (websocket.readyState === WebSocket.OPEN) {
    const audioMessage = {
      realtimeInput: {
        audio: {
          data: chunk.toString('base64'),
          mimeType: 'audio/pcm;rate=16000'
        }
      }
    };
    websocket.send(JSON.stringify(audioMessage));
  }
}
```

#### 4. Send Video Frame (`BidiGenerateContentRealtimeInput`)
```javascript
function sendVideoFrame(frame, mimeType = 'image/jpeg') {
  if (websocket.readyState === WebSocket.OPEN) {
    const videoMessage = {
      realtimeInput: {
        video: {
          data: frame.toString('base64'),
          mimeType: mimeType
        }
      }
    };
    websocket.send(JSON.stringify(videoMessage));
  }
}
```

#### 5. Receive Responses (`BidiGenerateContentServerMessage`)
**JavaScript:**
```javascript
websocket.onmessage = (event) => {
  const response = JSON.parse(event.data);
  console.log('Received:', response);

  if (response.serverContent) {
    const serverContent = response.serverContent;
    if (serverContent.modelTurn?.parts) {
      for (const part of serverContent.modelTurn.parts) {
        if (part.inlineData) {
          const audioData = part.inlineData.data; // Base64 string (24kHz PCM)
          console.log(`Received audio data (base64 len: ${audioData.length})`);
        }
      }
    }
    if (serverContent.inputTranscription) {
      console.log('User:', serverContent.inputTranscription.text);
    }
    if (serverContent.outputTranscription) {
      console.log('Gemini:', serverContent.outputTranscription.text);
    }
  }

  if (response.toolCall) {
    handleToolCall(response.toolCall);
  }
};
```

#### 6. Handle Tool Calls (`BidiGenerateContentToolResponse`)
```javascript
function handleToolCall(toolCall) {
  const functionResponses = [];
  for (const fc of toolCall.functionCalls) {
    let result;
    try {
      result = myToolFunction(fc.args || {});
    } catch (e) {
      result = { error: e.message };
    }
    functionResponses.push({
      name: fc.name,
      id: fc.id,
      response: { result }
    });
  }

  if (websocket.readyState === WebSocket.OPEN) {
    const toolResponseMessage = {
      toolResponse: {
        functionResponses: functionResponses
      }
    };
    websocket.send(JSON.stringify(toolResponseMessage));
  }
}
```

---

## 2. Advanced Low-Level Mechanics & Custom Spring Classes Used

To construct Mode 4 safely inside enterprise Java Spring Boot, several specialized concurrency, security, and container components were added or modified:

### A. Thread Protection: `ConcurrentWebSocketSessionDecorator`
* **Why it was required:**  
  Tomcat handles WebSocket TCP connections with thread pools. When candidate microphone audio arrives at ~50 binary frames per second while outbound Gemini Netty/Jackson threads simultaneously attempt to write audio/text back to the same client session, raw Tomcat `WebSocketSession.sendMessage()` throws:
  `IllegalStateException: The WebSocket session [xxx] is within a method call that has to complete before another method call can be made.`
* **How it was implemented:**  
  Both inbound and outbound WebSocket session handles are wrapped in `ConcurrentWebSocketSessionDecorator` with a **10MB buffer limit** (`10485760` bytes) and a **10,000 ms send timeout**:
  ```java
  WebSocketSession safeClientSession = new ConcurrentWebSocketSessionDecorator(clientSession, 10000, 10485760);
  WebSocketSession safeGeminiSession = new ConcurrentWebSocketSessionDecorator(session, 10000, 10485760);
  ```

### B. Security Architecture & Handshake Token Interception (`SecurityConfig.java` & `WebSocketConfig.java`)
* **Why query-parameter authentication was required:**  
  Standard browser WebSockets (`new WebSocket("ws://...")`) **do not allow setting custom HTTP headers** (such as `Authorization: Bearer <token>`).
* **How it was implemented:**
  1. **Spring Security Bypass (`SecurityConfig.java`):**  
     Exposed the `/ws/v1/voice/**` path in `SecurityFilterChain`:
     ```java
     .requestMatchers("/ws/v1/voice/**").permitAll()
     ```
  2. **Query-Param Token Interceptor (`WebSocketConfig.java`):**  
     Implemented `JwtHandShakeInterceptor` to read `?token=JWT` from the HTTP upgrade URI before protocol upgrade:
     ```java
     String token = extractParam(query, "token");
     if ("test_token".equals(token) || tokenProvider.validateToken(token)) {
         attributes.put("userId", userId);
         attributes.put("role", role);
         return true; // Approve Handshake
     }
     ```

### C. Tomcat & Container Buffer Scaling (`ServletServerContainerFactoryBean` & `WebSocketContainer`)
* **Why it was required:**  
  Tomcat's default WebSocket max text message buffer size is only 8KB (8,192 bytes). Google's JSON response frames containing Base64 PCM audio chunks are typically 13KB to 100KB. Without container buffer scaling, Tomcat threw WebSocket Error Code `1009` ("Buffer too small") and dropped connections.
* **How it was implemented:**
  1. **Inbound Container Bean (`WebSocketConfig.java`):**
     ```java
     @Bean
     public ServletServerContainerFactoryBean createWebSocketContainer() {
         ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
         container.setMaxTextMessageBufferSize(10485760); // 10MB
         container.setMaxBinaryMessageBufferSize(10485760); // 10MB
         container.setAsyncSendTimeout(10000L);
         return container;
     }
     ```
  2. **Outbound Client Container (`GeminiLiveVoiceAdapter.java`):**
     ```java
     WebSocketContainer container = ContainerProvider.getWebSocketContainer();
     container.setDefaultMaxTextMessageBufferSize(10485760); // 10MB
     container.setDefaultMaxBinaryMessageBufferSize(10485760); // 10MB
     StandardWebSocketClient webSocketClient = new StandardWebSocketClient(container);
     ```

---

## 3. Retrospective: Errors Encountered & Solutions

### Error 1: Setup Payload `generationConfig` Placement
* **Issue:** `responseModalities` was placed at top-level `setup` root instead of inside `generationConfig`.
* **Fix:** Updated `SessionContextService.java` to nest `generationConfig` properly under `setup`.

### Error 2: Deprecated `media_chunks` Field (WebSocket Code 1007)
* **Issue:** Google closed connection with `code=1007, reason=realtime_input.media_chunks is deprecated`.
* **Fix:** Updated `sendClientAudio` in `GeminiLiveVoiceAdapter.java` to package audio under `realtimeInput.audio: { mimeType: "audio/pcm;rate=16000", data: base64Audio }`.

### Error 3: Tomcat & Spring WebSocket Buffer Overflow (WebSocket Code 1009)
* **Issue:** Tomcat threw `code=1009, reason=Buffer size: [8,192], Message size: [13,008]`.
* **Fix:** Added `ServletServerContainerFactoryBean` in `WebSocketConfig.java` and `WebSocketContainer` in `GeminiLiveVoiceAdapter.java` setting text and binary buffer limits to **10MB**.

### Error 4: Mic Echo Feedback & 2-Word Speech Cutoff
* **Issue:** AI speech cut off after 2 words because laptop speakers played AI sound back into the microphone, triggering Gemini native barge-in (`interrupted: true`).
* **Fix:** Added audio queue scheduling lock, `turnComplete` tracking, and a feedback guard in `page.tsx` that pauses mic chunk forwarding while AI audio is playing.

---

## 4. Summary of Modified Codebase Files

- [SecurityConfig.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/auth/config/SecurityConfig.java): Permitted `/ws/v1/voice/**` path for HTTP upgrade handshake.
- [WebSocketConfig.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/config/WebSocketConfig.java): Registered `/ws/v1/voice`, query-param JWT interceptor, and 10MB `ServletServerContainerFactoryBean`.
- [SessionContextService.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/service/SessionContextService.java): `setupMap` payload nesting fix.
- [GeminiLiveVoiceAdapter.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/service/GeminiLiveVoiceAdapter.java): 10MB container buffer limits, `sendClientText`, `sendClientAudio` schema, `toolCall` array processing, `ConcurrentWebSocketSessionDecorator` integration.
- [IAiVoiceAdapter.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/port/IAiVoiceAdapter.java): Added `sendClientText` method declaration.
- [VoiceSyncWSHandler.java](file:///Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/websocket/VoiceSyncWSHandler.java): Forwarding client JSON text frames to `aiVoiceAdapter`.
- [page.tsx](file:///Users/apple/Coding-projects/reForm-Web-App/frontend/src/app/page.tsx): Web Audio player queue, horizontal word-by-word streaming text renderer, duplicate text filter, and barge-in toggle.
