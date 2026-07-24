# 21. Mermaid System Architecture & Sequence Diagrams
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## Diagram 1: Unified Package & Component Layout (`com.reForm.backend.ai`)

```mermaid
graph TD
    subgraph ClientLayer ["Client Layer"]
        Browser["Next.js Browser Client<br/>(WebAudio + WASM VAD)"]
    end

    subgraph GatewayLayer ["AI Infrastructure Module (com.reForm.backend.ai)"]
        WSConfig["config.WebSocketConfig<br/>(Path Router & Interceptor)"]
        Interceptor["config.JwtHandShakeInterceptor<br/>(JWT & ModelKey Extractor)"]
        WSHandler["websocket.VoiceSyncWSHandler<br/>(TCP Lifecycle Manager)"]
        Tracker["state.SessionTracker<br/>(Redis 2h TTL Presence)"]
    end

    subgraph ServiceLayer ["Service & Strategy Layer"]
        ContextSvc["service.SessionContextService<br/>(Dynamic Prompt/Tool Assembler)"]
        LiveAdapter["service.GeminiLiveVoiceAdapter<br/>(WSS Proxy to Google)"]
        CascadedAdapter["service.CascadedVoiceAdapter<br/>(Mode 3 Pipeline Manager)"]
        PortVoice["port.IAiVoiceAdapter<br/>(Adapter Contract)"]
        PortModel["port.IAiModelProviderStrategy<br/>(Strategy Contract)"]
        Strat31["strategy.Gemini31LiveModelStrategy"]
        Strat35["strategy.Gemini35FlashModelStrategy"]
    end

    subgraph ExternalServices ["External Services & Databases"]
        RedisDB[("Redis Server 6379<br/>(Session & Token Buckets)")]
        PostgresDB[("PostgreSQL Database<br/>(Forms, AgentConfigs, BYOK)")]
        GoogleLive["Google Gemini Live API<br/>(wss://generativelanguage.googleapis.com)"]
        DeepgramWSS["Deepgram Nova-3 STT<br/>(wss://api.deepgram.com)"]
        CartesiaWSS["Cartesia Sonic TTS<br/>(wss://api.cartesia.ai)"]
    end

    Browser -->|HTTP GET /ws/v1/voice?token=JWT&modelKey=...| WSConfig
    WSConfig --> Interceptor
    Interceptor -->|Validates Token & Writes Attributes| WSHandler
    WSHandler -->|Register Metadata| Tracker
    Tracker -->|opsForHash + expire 2h| RedisDB
    WSHandler -->|Delegates Session| PortVoice
    PortVoice --> LiveAdapter
    PortVoice --> CascadedAdapter
    LiveAdapter -->|Build Setup Payload| ContextSvc
    CascadedAdapter -->|1. Stream STT| DeepgramWSS
    CascadedAdapter -->|2. Stateless REST LLM| ContextSvc
    CascadedAdapter -->|3. Stream TTS| CartesiaWSS
    ContextSvc -->|Resolve Strategy| PortModel
    PortModel --> Strat31
    PortModel --> Strat35
    ContextSvc -->|Query Form & BYOK| PostgresDB
    LiveAdapter -->|Outbound WSS Tunnel| GoogleLive
```

---

## Diagram 2: Handshake, Authentication, & Session Initialization Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client as Next.js Candidate Browser
    participant Tomcat as Tomcat Server Container
    participant Interceptor as JwtHandShakeInterceptor
    participant WSHandler as VoiceSyncWSHandler
    participant Tracker as SessionTracker
    participant Redis as Redis (Port 6379)
    participant Adapter as GeminiLiveVoiceAdapter
    participant ContextSvc as SessionContextService
    participant Postgres as PostgreSQL DB
    participant GeminiWSS as Google Gemini Live API

    Client->>Tomcat: HTTP GET /ws/v1/voice?token=JWT_TOKEN&modelKey=GEMINI_3_1_LIVE<br/>Headers: Upgrade: websocket, Connection: Upgrade
    Tomcat->>Interceptor: beforeHandshake(request, response, wsHandler, attributes)
    Interceptor->>Interceptor: Validate JWT & extract userId, role, modelKey
    
    alt Token Invalid
        Interceptor-->>Client: Return HTTP 401 Unauthorized (Aborts Handshake)
    else Token Valid
        Interceptor->>Interceptor: Put userId, role, modelKey into attributes Map
        Interceptor-->>Tomcat: Return true (Approve Handshake)
        Tomcat-->>Client: HTTP 101 Switching Protocols (TCP Socket Upgraded)
        
        Tomcat->>WSHandler: afterConnectionEstablished(session)
        WSHandler->>WSHandler: activeSessions.put(userId, session) [Local RAM Map]
        WSHandler->>Tracker: registerSession(userId, sessionId)
        Tracker->>Redis: opsForHash().putAll("session:" + userId, metadata)<br/>+ expire("session:" + userId, 2 Hours)
        
        WSHandler->>Adapter: startSession(userId, session)
        Adapter->>ContextSvc: buildSetupContext(userId, role, modelKey)
        ContextSvc->>Postgres: Query FormAgentConfig, Goals, BYOK Key
        Postgres-->>ContextSvc: Returns FormAgentConfig entity
        ContextSvc-->>Adapter: Returns Setup Map (Model, System Prompt, Tools, SpeechConfig)
        
        Adapter->>GeminiWSS: Open WSS Connection & Transmit BidiGenerateContentSetup JSON
        GeminiWSS-->>Adapter: Returns setupComplete Ack
    end
```

---

## Diagram 3: Mode 3 Voice Cascaded Pipeline & Barge-in Interruption Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client as Candidate Browser
    participant WSHandler as VoiceSyncWSHandler
    participant CascadedAdapter as CascadedVoiceAdapter
    participant Deepgram as Deepgram STT (WSS)
    participant Gemini36 as Gemini 3.6 Flash (Text REST)
    participant Cartesia as Cartesia Sonic TTS (WSS)

    Note over Client, Cartesia: Normal Pipeline Execution
    Client->>WSHandler: Binary PCM Audio Stream
    WSHandler->>CascadedAdapter: sendClientAudio(pcmBytes)
    CascadedAdapter->>Deepgram: Forward PCM Audio Chunk
    Deepgram-->>CascadedAdapter: Emits Final Text Transcript ("I built microservices...")
    CascadedAdapter->>Gemini36: Send HTTP REST Request (Stateless LLM Call)
    Gemini36-->>CascadedAdapter: Returns AI Text Response ("Great! Which database did you use?")
    CascadedAdapter->>Cartesia: Stream Text Chunks for Synthesis
    Cartesia-->>CascadedAdapter: Stream PCM Audio Bytes
    CascadedAdapter-->>Client: Transmit PCM Audio → Speaker Plays Speech

    Note over Client, Cartesia: Real-Time Barge-in / Interruption Sequence
    Client->>WSHandler: Candidate Starts Interrupting ("I used PostgreSQL!")
    WSHandler->>CascadedAdapter: sendClientAudio(pcmBytes)
    CascadedAdapter->>Deepgram: Forward PCM Audio Chunk
    Deepgram-->>CascadedAdapter: Emits Event: { "speech_started": true }
    CascadedAdapter->>Cartesia: Send Cancel Frame: { "context_id": "c123", "cancel": true }
    CascadedAdapter-->>Client: Transmit Signal: { "type": "FLUSH_AUDIO_BUFFER" }
    Note over Client: Browser flushes audio buffer → AI speech stops in mid-sentence!
```

---

## Diagram 4: Mode 4 Real-Time Voice + Mode 2 Multi-Agent Event Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Creator as Form Builder (John)
    participant WSHandler as VoiceSyncWSHandler
    participant Adapter as GeminiLiveVoiceAdapter
    participant GeminiLive as Gemini 3.1 Live (WSS)
    participant SpringBus as Spring Event Bus (ApplicationEventPublisher)
    participant LayoutAgent as LayoutAgent (@Async Listener)
    participant Postgres as PostgreSQL DB

    Note over Creator, GeminiLive: Phase 1: Real-Time Audio Interaction (~300ms)
    Creator->>WSHandler: Streams PCM Binary Audio Frame ("Add contact section...")
    WSHandler->>Adapter: sendClientAudio(pcmBytes)
    Adapter->>GeminiLive: Send realtimeInput JSON (Base64 PCM)
    
    par Dual Response Stream
        GeminiLive-->>Adapter: Streams serverContent (Base64 Audio Response)
        Adapter-->>WSHandler: Forward Binary Message (PCM Audio Bytes)
        WSHandler-->>Creator: Transmit Audio Frame → Speaker Plays "Sure John! Creating contact section..."
    and Function Call Trigger
        GeminiLive-->>Adapter: Streams toolCall: modifyFormLayout({ intent: "ADD_CONTACT_SECTION" })
    end

    Note over Adapter, LayoutAgent: Phase 2: Asynchronous Event Dispatching
    Adapter->>SpringBus: publishEvent(new FormLayoutModificationEvent(formId, intent))
    SpringBus->>LayoutAgent: handleLayoutModification(event) [@Async Worker Thread]
    LayoutAgent->>LayoutAgent: Prompt Gemini 3.6 Flash (Mode 2) for valid DTO blocks
    LayoutAgent->>Postgres: Save updated form layout blocks
    LayoutAgent-->>Creator: Broadcasts WebSocket Canvas Event → Renders Live Preview Pane!
```

---

## Diagram 5: Distributed State & Heartbeat Ping-Pong Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client as Candidate Browser
    participant WSHandler as VoiceSyncWSHandler
    participant Tracker as SessionTracker
    participant Redis as Redis (Port 6379)

    loop Every 30 Seconds (Heartbeat)
        Client->>WSHandler: Send TextMessage ("PING")
        WSHandler->>WSHandler: handleTextMessage(session, message)
        WSHandler->>Tracker: refreshTTL(userId)
        Tracker->>Redis: expire("session:" + userId, 2 Hours)
        WSHandler-->>Client: Send TextMessage ("PONG")
    end

    alt Unexpected Server Crash / Connection Drop
        Note over Client, Redis: Client disconnects without clean closure
        Redis->>Redis: No PING frames received → 2-Hour TTL Countdown Expires
        Redis->>Redis: Redis automatically deletes ghost session key!
    end
```
