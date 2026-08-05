# Survey Report 1: Existing Codebase Architecture & System Infrastructure

**Agent**: `teamwork_preview_explorer_survey_1`  
**Date**: 2026-08-05  
**Target Repository**: `/Users/apple/Coding-projects/reForm-Web-App`  

---

## Executive Summary

The **reForm** platform is an omni-modal voice and visual form platform built on a dual-stack architecture:
1. **Backend**: Spring Boot 3.4 / Java 21 application supporting real-time WebSocket audio streaming to Google Gemini Multimodal Live API, PostgreSQL JSONB state persistence, Redis session tracking, and Spring Event Bus decoupling.
2. **Frontend**: Next.js 16 (React 19, TypeScript, TailwindCSS 4) providing interactive form creation, real-time voice/visual co-building canvas, and respondent interface.

This survey provides a comprehensive audit of the backend system infrastructure, Spring components, WebSocket thread execution flow, async event pipeline, and database persistence layer to establish the foundation for implementing the complete multi-agent architecture.

---

## 1. System Infrastructure & Build Architecture

### 1.1 Backend Technology Stack (`backend/pom.xml`)
- **Framework**: Spring Boot (`spring-boot-starter-parent` 4.1.0 / Spring Boot 3.4.x), JDK 21.
- **Web & Real-Time**:
  - `spring-boot-starter-web` (Spring MVC REST API controllers)
  - `spring-boot-starter-websocket` (Low-level Tomcat binary WebSocket handlers)
  - `spring-boot-starter-webflux` (WebClient reactive HTTP client)
- **Data & Persistence**:
  - `spring-boot-starter-data-jpa` (Hibernate 6 JPA ORM with PostgreSQL JSONB mapping via `@JdbcTypeCode(SqlTypes.JSON)`)
  - `org.postgresql:postgresql` (Database driver)
  - `spring-boot-starter-data-redis` (Lettuce Redis client for distributed state)
  - `spring-boot-starter-cache` (Spring Caching abstraction)
- **Security & Rate Limiting**:
  - `spring-boot-starter-security` & JWT (`io.jsonwebtoken:jjwt-api` 0.13.0)
  - `com.bucket4j:bucket4j_jdk17-core` & `bucket4j_jdk17-lettuce` (8.19.0 distributed rate limiting)
- **Utilities & Generators**:
  - Lombok, MapStruct (1.6.3), Jackson ObjectMapper.

### 1.2 Frontend Technology Stack (`frontend/package.json`)
- **Framework**: Next.js 16.2.6 (App Router), React 19.2.4.
- **Styling & Language**: TailwindCSS 4 (`@tailwindcss/postcss`), TypeScript 5.
- **Tooling**: ESLint 9 (`eslint-config-next`), Babel React Compiler.

---

## 2. Directory Layout & Module Structure

```
reForm-Web-App/
├── backend/
│   ├── src/main/java/com/reForm/backend/
│   │   ├── BackendApplication.java           # Entrypoint (@EnableAsync, @EnableCaching)
│   │   ├── ai/                                # AI & Voice Streaming Engine
│   │   │   ├── agent/                        # AI Agents (LayoutAgent)
│   │   │   ├── config/                       # WS & Data Initializer configs
│   │   │   ├── domain/                       # VoiceMode enum (MODE_1 to MODE_4)
│   │   │   ├── dto/                          # Block DTOs
│   │   │   ├── event/                        # Spring Events (FormLayoutModificationEvent)
│   │   │   ├── factory/                      # AiVoiceAdapterFactory, BlockFactory
│   │   │   ├── port/                         # Strategy & Adapter Interfaces
│   │   │   ├── service/                      # GeminiLiveVoiceAdapter, CascadedVoiceAdapter, SessionContextService
│   │   │   ├── state/                        # SessionTracker (Redis TTL management)
│   │   │   ├── strategy/                     # Gemini31LiveModelStrategy, Gemini35FlashModelStrategy
│   │   │   ├── tool/                         # Function Calling Tool Framework (18 tools)
│   │   │   │   ├── handler/                  # Tool execution handlers (builder, filler, audio, etc.)
│   │   │   │   ├── port/                     # IToolCallHandler interface
│   │   │   │   └── registry/                 # ToolCallRegistry strategy lookup
│   │   │   └── websocket/                    # VoiceSyncWSHandler, WebSocketSessionUtils
│   │   ├── auth/                             # Auth Controllers, JWT Filter, Security Config
│   │   ├── core/                             # Global Exceptions, Interceptors, BaseEntity
│   │   ├── form/                             # Form & FormAiAgentProfile Entities, Controllers, Repos
│   │   ├── submission/                       # Submission Entity, Processor, Controllers
│   │   └── user/                             # User & Workspace Entities, Controllers, Repos
├── frontend/                                 # Next.js 16 Frontend App
└── .agents/                                  # Agent coordination metadata
```

---

## 3. Spring Boot Configuration & Async Infrastructure

### 3.1 Async Enablement
- `BackendApplication.java` is explicitly annotated with `@EnableAsync` and `@EnableCaching`:
  ```java
  @SpringBootApplication
  @EnableCaching
  @EnableAsync
  public class BackendApplication { ... }
  ```
- **Execution Model**: Spring initializes a default `SimpleAsyncTaskExecutor` or `ThreadPoolTaskExecutor` for methods annotated with `@Async`.

### 3.2 Event Bus & Decoupled Architecture
- **Publisher**: Spring `ApplicationEventPublisher` injected into tool handlers (e.g. `ModifyFormLayoutToolHandler`).
- **Event**: Immutable record `FormLayoutModificationEvent(String formId, String userIntent, String targetBlockId)`.
- **Listener / Consumer**: `LayoutAgent.handleLayoutModification(FormLayoutModificationEvent event)` annotated with `@Async`, `@EventListener`, `@Transactional`.

---

## 4. WebSocket & Real-Time Audio Stream Thread Analysis

### 4.1 Connection Lifecycle & Proxy Architecture
1. **Endpoint**: Registered at `/ws/voice-sync` in `WebSocketConfig.java`.
2. **Handshake**: `JwtHandshakeInterceptor.beforeHandshake` validates JWT token, extracts `userId`, `role`, and `mode` (e.g., `MODE_3` vs `MODE_4`), putting them into WebSocket session attributes.
3. **Session Establishment**: `VoiceSyncWSHandler.afterConnectionEstablished`:
   - Resolves adapter via `AiVoiceAdapterFactory.getAdapter(mode)`.
   - Wraps raw WebSocket session in `ConcurrentWebSocketSessionDecorator` (10MB buffer limit, 10s timeout) for thread-safe concurrent writes.
   - Registers Redis presence in `SessionTracker.registerSession(userId, sessionId)`.
   - Invokes `IAiVoiceAdapter.startSession(userId, safeSession)`.
4. **Outbound WSS Proxy**: `GeminiLiveVoiceAdapter` opens an outbound WebSocket connection (`GoogleBidiWebSocketHandler`) to Google Gemini Live API (`wss://generativelanguage.googleapis.com`).

### 4.2 Thread Execution Flow during Audio Streaming
```
[ Browser Client ]
       │  (Binary PCM Audio Frames ~50/sec)
       ▼
[ Tomcat WS I/O Thread ] ──► VoiceSyncWSHandler.handleBinaryMessage
       │
       ▼ (Direct forwarding)
[ GeminiLiveVoiceAdapter ] ──► sendClientAudio ──► Base64 JSON ──► [ Outbound Google WSS Socket ]
       │
       ◄────────────────────── Outbound WSS Event Loop (GoogleBidiWebSocketHandler)
[ Response Frame ]
       ├─ Binary PCM Audio ────► Forwarded directly to Client WS Session
       ├─ User/AI Text Transcript ──► JSON frame to Client WS Session
       └─ Tool Call Request ────► handleToolCall(...) ──► ToolCallRegistry.executeTool(...)
```

---

## 5. Audit of Spring Components & Blocking Thread Analysis

### 5.1 Existing Spring `@Component` / `@Service` Inventory
- **Agents**:
  - `com.reForm.backend.ai.agent.LayoutAgent` (`@Component`) — **Currently the ONLY implemented agent bean**.
- **Services & Trackers**:
  - `SessionContextService` (`@Service`) — Dynamic prompt compiler & Gemini setup payload generator.
  - `SessionTracker` (`@Service`) — Redis distributed session presence & TTL management.
  - `ToolCallRegistry` (`@Service`) — Auto-wires all `IToolCallHandler` implementations into lookup map.
  - `RateLimitServiceImpl` (`@Service`) — Bucket4j rate limiting service.
- **Voice Adapters & Factories**:
  - `GeminiLiveVoiceAdapter` (`@Component("geminiLiveVoiceAdapter")`) — Mode 4 Gemini Live proxy.
  - `CascadedVoiceAdapter` (`@Component("cascadedVoiceAdapter")`) — Mode 3 STT -> LLM -> TTS proxy.
  - `AiVoiceAdapterFactory` (`@Component`) — Strategy factory.
  - `BlockFactory` (`@Component`) — Form block instantiator.
- **Model Strategies**:
  - `Gemini31LiveModelStrategy` (`@Component`)
  - `Gemini35FlashModelStrategy` (`@Component`)
- **Tool Handlers (18 `@Component` Beans)**:
  - Audio: `SaveAudioRecordingToolHandler`, `SaveSessionTranscriptToolHandler`
  - Builder: `ModifyFormLayoutToolHandler`, `ConfigureFillerPersonaToolHandler`, `PublishFormToolHandler`, `GenerateContentFromDocToolHandler`
  - File: `AnalyzeUploadedFileToolHandler`, `ExtractStructuredDataToolHandler`, `RequestFileUploadToolHandler`
  - Filler: `EvaluateResponseToolHandler`, `FlagForHumanReviewToolHandler`, `LookupFormProgressToolHandler`, `SaveFieldResponseToolHandler`, `SkipQuestionToolHandler`
  - UI: `RenderDynamicUIToolHandler`, `SendNotificationToolHandler`
  - Universal: `EndSessionToolHandler`, `SearchUserDocumentToolHandler`

### 5.2 Identification of Missing Agents & Non-Decoupled / Blocking Calls

#### Key Finding 1: Missing Agent Suites (R1, R2, R3 Requirements Violation)
Out of the 9 required multi-agent suite components, **8 are currently missing**:
- **Form Builder Agent Suite**:
  - `LayoutAgent`: **PRESENT** (`@Component`, `@Async`, `@EventListener`).
  - `SchemaGeneratorAgent`: **MISSING**. Currently schema generation logic is embedded inline inside `LayoutAgent.createBlockFromIntent` or missing.
  - `PersonaConfigAgent`: **MISSING**. `ConfigureFillerPersonaToolHandler` logs persona but does not dispatch to a dedicated agent.
  - `DocumentIngestionAgent`: **MISSING**. `GenerateContentFromDocToolHandler` returns a stub JSON response without document parsing / chunking.
- **Form Filler & Governance Suite**:
  - `GuardrailAgent`: **MISSING**. No security/toxicity/PII agent checking incoming frames or prompts.
  - `MemoryGoalAgent`: **MISSING**. `SaveFieldResponseToolHandler` logs mock saving without tracking conversation state, intent goals, or JPA persistence.
  - `BillingAgent`: **MISSING**. No agent tracking real-time token/audio frame consumption, credit deduction, or session rate limits.
  - `RagSearchAgent`: **MISSING**. `SearchUserDocumentToolHandler` returns hardcoded dummy results without pgvector semantic search.
  - `EvaluationAgent`: **MISSING**. `EvaluateResponseToolHandler` returns mock numeric scores synchronously without LLM grading or feedback loop.

#### Key Finding 2: Synchronous Blocking Tool Calls on WebSocket Response Thread
In `GeminiLiveVoiceAdapter.java`:
- When Gemini Live requests a tool call, `handleToolCall(...)` executes `toolCallRegistry.executeTool(...)` **synchronously on the WebSocket netty/tomcat thread (`GoogleBidiWebSocketHandler`)**.
- Tool handlers like `EvaluateResponseToolHandler`, `SaveFieldResponseToolHandler`, `AnalyzeUploadedFileToolHandler`, `SearchUserDocumentToolHandler`, and `GenerateContentFromDocToolHandler` execute their logic inline on this thread.
- **Impact**: Any heavy LLM call, DB query, vector search, or file processing in these handlers blocks the incoming/outgoing audio stream, causing audio jitter and latency spikes on the main WebSockets thread.
- **Required Architecture**: All non-trivial tool handlers must publish Spring events and return immediate ack frames, offloading heavy processing to `@Async` Spring `@Component` service agents.

---

## 6. Database & State Persistence Mechanisms

### 6.1 PostgreSQL Database Schema & JPA Mappings
1. **Form Entity (`forms` table)**:
   - Primary key: UUID.
   - Blocks field: `List<AbstractBlock>` serialized into PostgreSQL `jsonb` column via `@JdbcTypeCode(SqlTypes.JSON)` and `AbstractBlockConverter`.
   - Relationships: Belongs to `Workspace` (`workspaceId`) and `User` (`creatorId`). Has unique indexed `slug`.
2. **Form AI Agent Profile Entity (`form_ai_agent_profiles` table)**:
   - One-to-One with `Form` (`form_id`).
   - Fields: `modelKey` (e.g. `GEMINI_3_1_LIVE`), `systemPromptTemplate` (TEXT with `{{placeholders}}`), `voiceName` (e.g. `Puck`, `Kore`), `temperature` (Float), `byokApiKeyEncrypted` (AES-256-GCM encrypted string).
3. **Submission Entity (`submission` table)**:
   - Fields: `formId` (UUID), `answers` (`JsonNode` serialized to `jsonb`), `userAgent`.
4. **User & Workspace Entities (`users`, `workspaces` tables)**:
   - Authentication, roles (`FORM_BUILDER`, `FORM_FILLER`, `ADMIN`), workspace membership.

### 6.2 Redis State & Session Tracking
- `SessionTracker.java` manages active WebSocket connection state across nodes.
- **Redis Key Structure**: `session:{userId}` (Redis Hash).
- **Attributes**: `sessionId`, `connectedAt`, `nodeId`.
- **TTL Lifecycle**: Enforces an explicit 2-hour TTL (`Duration.ofHours(2)`). Refreshed automatically on WebSocket `PING` frames (`refreshTTL`), deleted on socket disconnect (`deregisterSession`).

---

## 7. Project Build & Test Commands

### 7.1 Backend Commands (Maven & Java 21)
All commands executed from directory `/Users/apple/Coding-projects/reForm-Web-App/backend`:

- **Build / Compile**:
  ```bash
  ./mvnw clean compile
  ```
- **Run Unit & Integration Tests**:
  ```bash
  ./mvnw test
  ```
- **Package Application Jar**:
  ```bash
  ./mvnw clean package -DskipTests
  ```
- **Run Spring Boot Application**:
  ```bash
  ./mvnw spring-boot:run
  ```

### 7.2 Frontend Commands (Next.js 16 / npm)
All commands executed from directory `/Users/apple/Coding-projects/reForm-Web-App/frontend`:

- **Install Dependencies**:
  ```bash
  npm install
  ```
- **Run Development Server**:
  ```bash
  npm run dev
  ```
- **Run Production Build**:
  ```bash
  npm run build
  ```
- **Run ESLint Checks**:
  ```bash
  npm run lint
  ```

---

## 8. Summary of Findings & Next Steps

1. **Infrastructure Ready**: Spring Boot `@EnableAsync`, Spring Event Bus (`ApplicationEventPublisher`), WebSocket handlers (`VoiceSyncWSHandler`), PostgreSQL JSONB hibernate mapping, and Redis TTL session tracking are fully established and operational.
2. **Current Agent Baseline**: Only `LayoutAgent` exists as an event-driven `@Async` `@Component`.
3. **Primary Decoupling Goal**: Implement the missing 8 Spring `@Component` service agents (`SchemaGeneratorAgent`, `PersonaConfigAgent`, `DocumentIngestionAgent`, `GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `RagSearchAgent`, `EvaluationAgent`), transition all tool handlers to event-driven `@Async` dispatch, and ensure zero blocking calls remain on the main WebSockets audio thread.
