# System Diagrams & Comprehensive Sequence Flows
**Document Version:** 3.0 (Unified 4-Mode & Mode 3 Cascaded Voice Flows)  
**Location:** `backend/knowledge/pth/week3/06_system_diagrams_and_sequence_flows.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## Diagram 1: Unified 4-Mode Component Architecture

```mermaid
graph TD
    subgraph Client ["Frontend Client (Next.js)"]
        WebAudio["WebAudio API + WASM VAD Client"]
        ChatUI["Mode 2 Text Chat UI"]
    end

    subgraph Gateway ["AI Infrastructure Gateway (com.reForm.backend.ai)"]
        WSConfig["WebSocketConfig Router"]
        WSHandler["VoiceSyncWSHandler"]
        Tracker["SessionTracker (Redis Presence)"]
    end

    subgraph Adapters ["Voice & Text Adapters Layer"]
        LiveAdapter["GeminiLiveVoiceAdapter (Mode 4 WSS)"]
        CascadedAdapter["CascadedVoiceAdapter (Mode 3 Pipeline)"]
        TextService["Gemini36FlashService (Mode 2 REST)"]
    end

    subgraph CoreServices ["Shared Core Services Layer"]
        ContextSvc["SessionContextService"]
        ProfileRepo["FormAiAgentProfileRepository"]
        EventBus["Spring ApplicationEventPublisher"]
    end

    subgraph MultiAgent ["Shared Multi-Agent Pipeline"]
        LayoutAgent["LayoutAgent (@Async Canvas Generator)"]
        Guardrail["Guardrail Agent (pgvector Moderation)"]
        Memory["Memory & Goal Agent (Redis Hash Tracker)"]
        Billing["Billing Agent (VAD Metering)"]
        RAG["RAG Search Agent (Document Query)"]
        Evaluation["Evaluation Agent (Post-Session Summary)"]
    end

    subgraph External ["Databases & External Vendors"]
        RedisDB[("Redis Server 6379")]
        PostgresDB[("PostgreSQL DB (Forms, Profiles, pgvector)")]
        GeminiLiveWSS["Google Gemini Live API (WSS)"]
        DeepgramWSS["Deepgram Nova-3 STT (WSS)"]
        CartesiaWSS["Cartesia Sonic TTS (WSS)"]
        GeminiFlashREST["Google Gemini 3.6 Flash (REST)"]
    end

    WebAudio -->|WSS /ws/v1/voice| WSConfig
    ChatUI -->|HTTP REST| TextService
    WSConfig --> WSHandler
    WSHandler --> Tracker
    Tracker --> RedisDB
    WSHandler --> LiveAdapter
    WSHandler --> CascadedAdapter
    
    LiveAdapter --> ContextSvc
    CascadedAdapter --> ContextSvc
    TextService --> ContextSvc
    ContextSvc --> ProfileRepo
    ProfileRepo --> PostgresDB

    LiveAdapter --> GeminiLiveWSS
    CascadedAdapter --> DeepgramWSS
    CascadedAdapter --> GeminiFlashREST
    CascadedAdapter --> CartesiaWSS
    TextService --> GeminiFlashREST

    LiveAdapter --> EventBus
    CascadedAdapter --> EventBus
    TextService --> EventBus

    EventBus --> LayoutAgent
    EventBus --> MultiAgent
    RAG --> PostgresDB
```

---

## Diagram 2: Mode 3 Complete Cascaded Voice Pipeline & Barge-in Sequence Flow

```mermaid
sequenceDiagram
    autonumber
    actor Candidate as Candidate (Sarah)
    participant WSHandler as VoiceSyncWSHandler
    participant Adapter as CascadedVoiceAdapter
    participant Deepgram as Deepgram STT (WSS)
    participant FlashLLM as Gemini 3.6 Flash (REST)
    participant Cartesia as Cartesia TTS (WSS)

    Note over Candidate, Cartesia: Stage 1: Audio Input -> Transcription
    Candidate->>WSHandler: Binary PCM Audio Stream
    WSHandler->>Adapter: sendClientAudio(pcmBytes)
    Adapter->>Deepgram: Forward PCM Audio Chunk
    Deepgram-->>Adapter: Emits Text JSON: { "transcript": "I built microservices in Java", "is_final": true }

    Note over Candidate, Cartesia: Stage 2: LLM Reasoning (Shares Mode 2 REST Service)
    Adapter->>FlashLLM: POST /v1beta/models/gemini-3.6-flash:generateContent
    FlashLLM-->>Adapter: Returns AI Text Response: "Great! Which database did you use?"

    Note over Candidate, Cartesia: Stage 3: TTS Audio Synthesis & Playback
    Adapter->>Cartesia: Stream Text Chunk to TTS WSS
    Cartesia-->>Adapter: Stream Binary PCM Audio Bytes
    Adapter-->>WSHandler: Forward Binary Message
    WSHandler-->>Candidate: Speaker Plays Response Speech

    Note over Candidate, Cartesia: Manual Barge-in Interruption Sequence
    Candidate->>WSHandler: Candidate Starts Speaking Mid-Sentence ("I used PostgreSQL!")
    WSHandler->>Adapter: sendClientAudio(pcmBytes)
    Adapter->>Deepgram: Forward PCM Audio Chunk
    Deepgram-->>Adapter: Emits Event: { "speech_started": true }
    Adapter->>Cartesia: Send Cancel Frame: { "context_id": "c123", "cancel": true }
    Adapter-->>WSHandler: Send Text Message: { "type": "FLUSH_AUDIO_BUFFER" }
    WSHandler-->>Candidate: Browser Flushes Audio Buffer -> Speech Stops Instantly!
```

---

## Diagram 3: In-Session Document RAG Tool Calling Sequence Flow (Mode 4)

```mermaid
sequenceDiagram
    autonumber
    actor Candidate as Candidate (Sarah)
    participant WSHandler as VoiceSyncWSHandler
    participant Adapter as GeminiLiveVoiceAdapter
    participant Gemini as Gemini 3.1 Live API (WSS)
    participant RAGAgent as RagSearchAgent
    participant PgVector as PostgreSQL pgvector

    Candidate->>WSHandler: Audio Chunk ("What is the remote work policy in section 3?")
    WSHandler->>Adapter: sendClientAudio(pcmBytes)
    Adapter->>Gemini: Send realtimeInput (Base64 PCM)

    Gemini-->>Adapter: Stream toolCall: searchUserDocument({ query: "remote work policy section 3" })
    Adapter->>RAGAgent: searchRelevantPassages(formId, query)
    RAGAgent->>PgVector: Vector Cosine Query (<=>) on document_embeddings
    PgVector-->>RAGAgent: Return Top-3 Matching Passages
    RAGAgent-->>Adapter: Return Passages Text

    Adapter->>Gemini: Send toolResponse JSON Frame (Function Output)
    Gemini-->>Adapter: Streams serverContent Audio (Speaks answer from document)
    Adapter-->>WSHandler: Forward Binary PCM Audio
    WSHandler-->>Candidate: Speaker Plays Speech ("According to Section 3...")
```
