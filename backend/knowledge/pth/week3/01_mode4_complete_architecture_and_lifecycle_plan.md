# Mode 4 Native Live Voice: Master Architecture & Lifecycle Plan
**Document Version:** 2.1 (Production Specification & Technical Glossary)  
**Location:** `backend/knowledge/pth/week3/01_mode4_complete_architecture_and_lifecycle_plan.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Technical Glossary & Terminology

| Term | Category / Type | Definition & Purpose | Data Protocol / Format |
| :--- | :--- | :--- | :--- |
| **PCM** | Raw Binary Audio | 16-bit 16kHz Little-Endian uncompressed pulse-code modulation audio bytes. Used for real-time sub-300ms microphone/speaker streaming. | `byte[]` (32,000 bytes/sec) |
| **Base64** | Encoding Standard | Binary-to-text string encoding scheme. Used to embed binary PCM audio frames into JSON text payloads when communicating over WebSockets with Google Gemini Live API. | ASCII `String` |
| **WSS (RFC 6455)** | Network Protocol | Full-duplex bidirectional persistent TCP socket protocol between browser and Spring Boot server. | TCP Socket Frames |
| **`WebSocketSession`** | Spring Framework Interface | Represents an active physical WebSocket TCP connection in Spring Boot (`org.springframework.web.socket.WebSocketSession`). | Java Object Handle |
| **`ObjectMapper`** | Jackson JSON Library | High-performance JSON serializer/deserializer converting Java Maps/DTOs to JSON strings and vice-versa. | `com.fasterxml.jackson.databind.ObjectMapper` |
| **`FormAiAgentProfile`** | JPA Domain Entity | Database entity (bound 1-to-1 with `Form`) storing custom prompt templates, voice choice (`Puck`), temperature, and BYOK API keys. | PostgreSQL Entity |

---

## 2. Codebase Audit & Required Refactoring

| Class / Component | Current Implementation | Issues Identified | Required Refactoring |
| :--- | :--- | :--- | :--- |
| **`VoiceSyncWSHandler.java`** | Standard `BinaryWebSocketHandler` managing TCP socket maps and Redis presence. | Handles raw binary messages directly without delegating protocol parsing to adapter. | Keep connection lifecycle, delegate all frame handling to `IAiVoiceAdapter`. |
| **`SessionTracker.java`** | Redis template wrapper with 2-hour TTL. | Works as intended. | Retain for distributed presence state. |
| **`GeminiLiveVoiceAdapter.java`** | Basic stub with TODO comments. | Missing outbound WSS connection, Base64 audio tunneling, native barge-in handling, and tool call handling. | Implement full `StandardWebSocketClient` WSS proxy targeting Google's `BidiGenerateContent` endpoint. |
| **`SessionContextService.java`** | Scaffolded assembler. | Contained hardcoded prompt strings as fallbacks. | Refactor to query `FormAiAgentProfile` entity from PostgreSQL and hydrate dynamic prompt templates. |
| **`FormAgentConfig.java`** | Non-existent in `src`. | Mentioned in docs but not implemented in code. | Create as `FormAiAgentProfile.java` entity bound 1-to-1 with `Form`. |
| **`FormLayoutModificationEvent`** | Created in Step 1. | Missing fields for block category parameters. | Expand record fields to carry full layout mutation metadata. |

---

## 3. Complete Mode 4 Connection Lifecycle

```text
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ 1. HANDSHAKE & INITIALIZATION                                                         │
│ Client connects: GET /ws/v1/voice?token=JWT&formId=UUID&role=FORM_BUILDER             │
│   ├── JwtHandshakeInterceptor validates JWT token & extracts (userId, role, formId)   │
│   ├── VoiceSyncWSHandler registers connection in RAM ConcurrentHashMap                │
│   ├── SessionTracker registers session in Redis ("session:{userId}") with 2h TTL       │
│   ├── SessionContextService queries FormAiAgentProfile & Form from PostgreSQL         │
│   └── GeminiLiveVoiceAdapter opens WSS to wss://generativelanguage.googleapis.com     │
└───────────────────────────────────────────────────────────────────────────────────────┘
                                           │
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ 2. ACTIVE STREAMING & MULTI-AGENT LOOP                                                │
│ Mic Audio Stream (Client) ──► Base64 PCM ──► GeminiLiveVoiceAdapter ──► Google Gemini │
│                                                                                       │
│ Dual Outbound Processing from Gemini:                                                 │
│   ├── Audio Output: Base64 PCM ──► Base64 Decode ──► BinaryMessage ──► Client Speaker  │
│   ├── Interruption: Google emits interrupted: true ──► Send FLUSH frame to Client     │
│   └── Tool Call: Google emits toolCall JSON ──► Publish FormLayoutModificationEvent   │
│                                                                                       │
│ Background Microservices Execution:                                                   │
│   ├── Guardrail Agent: Runs pgvector cosine similarity checks on input transcript     │
│   ├── Memory/Goal Agent: Updates Redis opsForHash() goal checklist                   │
│   ├── Billing Agent: Monitors VAD silence (pauses stream if silent 45s)               │
│   └── RAG Search Agent: Executes vector distance queries if candidate asks question   │
└───────────────────────────────────────────────────────────────────────────────────────┘
                                           │
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ 3. SESSION TERMINATION & CLEANUP                                                      │
│ Client closes tab or disconnects:                                                     │
│   ├── VoiceSyncWSHandler.afterConnectionClosed() removes handle from RAM map          │
│   ├── GeminiLiveVoiceAdapter closes outbound WSS socket to Google cleanly             │
│   ├── SessionTracker deregisters Redis session key                                    │
│   └── Evaluation Agent runs @Async: computes candidate match score (0-100) & summary │
└───────────────────────────────────────────────────────────────────────────────────────┘
```
