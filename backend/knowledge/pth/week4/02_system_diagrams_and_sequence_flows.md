# Week 4 System Diagrams & Sequence Flows

**Document Version:** 1.0  
**Target System:** reForm Core Monolith (`com.reForm.backend.ai` & Next.js Frontend)  

---

## Diagram 1: End-to-End User Mode & Model Selection Sequence Flow

This diagram visualizes what happens when a user selects a mode (e.g. Mode 4 vs Mode 3) and a form in the UI, and how the system resolves the strategy and database prompt underneath.

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Candidate (Browser)
    participant UI as Next.js Frontend (page.tsx)
    participant Interceptor as JwtHandshakeInterceptor
    participant WSHandler as VoiceSyncWSHandler
    participant Factory as AiVoiceAdapterFactory
    participant Adapter as GeminiLiveVoiceAdapter (Mode 4)
    participant SessionCtx as SessionContextService
    participant DB as PostgreSQL (form_ai_agent_profiles)
    participant Gemini as Google Gemini Live API

    User->>UI: Selects Mode 4 & Form ID (11111111-...)
    UI->>Interceptor: WSS Handshake (GET /ws/v1/voice?mode=MODE_4&formId=11111111-...)
    Note over Interceptor: Validates JWT token.<br/>Extracts 'mode', 'formId', 'modelKey'.<br/>Saves into attributes map.
    Interceptor-->>WSHandler: Handshake Approved (HTTP 101 Switching Protocols)
    
    WSHandler->>Factory: getAdapter(VoiceMode.MODE_4)
    Factory-->>WSHandler: Returns geminiLiveVoiceAdapter Bean
    Note over WSHandler: Stores adapter in session attributes:<br/>session.getAttributes().put("voiceAdapter", adapter)

    WSHandler->>Adapter: startSession(userId, safeClientSession)
    Adapter->>Gemini: Opens Outbound WebSocket (wss://generativelanguage.googleapis.com)
    Gemini-->>Adapter: Outbound Socket 2 Established (afterConnectionEstablished)

    Adapter->>SessionCtx: buildSetupContext(userId, role, formId, modelKey)
    SessionCtx->>DB: findByFormId(UUID.fromString(formId))
    DB-->>SessionCtx: Returns FormAiAgentProfile (voice: "Kore", prompt: "Cheerful Recruiter...")
    Note over SessionCtx: Compiles systemInstruction "parts"<br/>& injects prebuiltVoiceConfig ("Kore").

    SessionCtx-->>Adapter: Returns BidiGenerateContentSetup Payload Map
    Adapter->>Gemini: sendMessage(TextMessage(setupJson))
    Gemini-->>Adapter: BidiGenerateContentSetupComplete ACK
    Adapter-->>UI: WebSocket Connected & Ready!
```

---

## Diagram 2: Inbound & Outbound Dual WebSocket Proxy Routing (Direction A & B)

This diagram shows exact Java function calls for mic audio streaming and AI voice playback.

```mermaid
sequenceDiagram
    autonumber
    participant Mic as Client Mic (Browser)
    participant WSHandler as VoiceSyncWSHandler (Socket 1)
    participant Adapter as GeminiLiveVoiceAdapter
    participant GeminiHandler as GoogleBidiWebSocketHandler (Socket 2)
    participant Gemini as Google Gemini Live API
    participant Speaker as Client Speaker (Browser)

    rect rgba(59, 130, 246, 0.12)
        Note over Mic, Gemini: DIRECTION A: Candidate Speaks (Client ---> Proxy ---> Gemini)
        Mic->>WSHandler: Binary PCM Audio Frames (~50 fps)
        WSHandler->>WSHandler: handleBinaryMessage(session, BinaryMessage)
        WSHandler->>Adapter: (IAiVoiceAdapter) session.getAttributes().get("voiceAdapter")
        Adapter->>Gemini: geminiSession.sendMessage(TextMessage(Base64AudioJSON))
    end

    rect rgba(34, 197, 94, 0.12)
        Note over Gemini, Speaker: DIRECTION B: AI Responds (Gemini ---> Proxy ---> Client)
        Gemini->>GeminiHandler: TextMessage (ServerContent JSON with Base64 AI Audio)
        GeminiHandler->>GeminiHandler: handleTextMessage(session, TextMessage)
        GeminiHandler->>Adapter: processGooglePayload(userId, clientSession, payload)
        Adapter->>Adapter: decodeAndForwardPcmAudio(...)
        Adapter->>Speaker: safeClientSession.sendMessage(BinaryMessage(pcmAudioBytes))
        Speaker->>Speaker: Web Audio API plays 24kHz PCM voice output!
    end
```

---

## Diagram 3: Database Prompt & Voice Persona Hydration Flow

How `SessionContextService` retrieves database entities and builds Google's `setup` frame:

```mermaid
graph TD
    Start[WebSocket Session Connects] --> Extract[Extract formId from Session Attributes]
    Extract --> QueryDB{formId present?}
    
    QueryDB -- Yes --> FetchProfile[FormAiAgentProfileRepository.findByFormId]
    QueryDB -- No --> RoleFallback[Use Production Role Baseline Prompt]
    
    FetchProfile --> ProfileFound{Profile Exists in DB?}
    ProfileFound -- Yes --> ReadDB[Read: systemPromptTemplate,<br/>voiceName e.g. Kore/Puck,<br/>temperature, modelKey]
    ProfileFound -- No --> RoleFallback
    
    ReadDB --> BuildSetup[SessionContextService.buildSetupContext]
    RoleFallback --> BuildSetup
    
    BuildSetup --> AssembleJSON[Assemble BidiGenerateContentSetup JSON Payload]
    AssembleJSON --> SendGoogle[Send Setup JSON over Socket 2 to Gemini Live]
```

---

## Diagram 4: Mode 3 vs Mode 4 Execution Comparison

| Step / Layer | **Mode 4: Voice Native Live** | **Mode 3: Voice Cascaded Pipeline** |
| :--- | :--- | :--- |
| **Strategy Bean** | `GeminiLiveVoiceAdapter` | `CascadedVoiceAdapter` |
| **Factory Key** | `VoiceMode.MODE_4` | `VoiceMode.MODE_3` |
| **STT Engine** | Built-in native to Gemini Live | Deepgram Nova-3 over WebSocket (`wss://api.deepgram.com`) |
| **LLM Engine** | Gemini 3.1 Live (`gemini-3.1-flash-live-preview`) | Gemini 3.6 Flash via Unary HTTP REST |
| **TTS Engine** | Built-in native to Gemini Live | Cartesia Sonic over WebSocket (`wss://api.cartesia.ai`) |
| **Latency** | 🚀 **~300ms Native Audio** | ~700ms - 900ms Cascaded Pipeline |
| **Barge-in** | Native model turn detection (`interrupted: true`) | STT `speech_started` event triggers Cartesia flush |
