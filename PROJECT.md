# Project: reForm Web App Multi-Agent Architecture

## Architecture
Decoupled Spring Boot `@Component` multi-agent architecture with event-driven `@Async` execution for zero-blocking WebSocket audio stream performance, paired with a Next.js 16 / React 19 frontend and PostgreSQL (JSONB + pgvector) + Redis TTL session state persistence.

```
[ Frontend: Next.js 16 / React 19 ]
         │
         ├── REST APIs (Modes 1 & 2) ──► Spring Controllers ──► EventPublisher
         │                                                            │
         └── WSS (Modes 3 & 4) ────────► VoiceSyncWSHandler (Zero-blocking)
                                              │                       │
                                              ▼                       ▼
                                       WebSocket Loop          @Async Event Bus
                                              │                       │
                                              ▼                       ▼
                                       PCM Audio Proxy        Form Builder Suite (M2)
                                                              Form Filler Suite (M3)
                                                              Governance & Security (M4)
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Event Infrastructure & Test Setup | Base Spring events, async thread executors, testing stack (Vitest, RTL, Playwright), Gemini WS Mock | Milestone 1 | R1, Criteria |
| 2 | LayoutAgent | Spring `@Component` `@Async` `@EventListener` for form layout mutations, block ordering, delete/insert intents, WebSocket canvas push | Milestone 2 | R2 |
| 3 | SchemaGeneratorAgent | Spring `@Component` `@Async` service for prompt-to-schema and document-to-schema transformation via Gemini Flash | Milestone 2 | R2 |
| 4 | PersonaConfigAgent | Spring `@Component` `@Async` service for filler persona compilation, voice settings, and system prompt dynamic customization | Milestone 2 | R2 |
| 5 | DocumentIngestionAgent | Spring `@Component` `@Async` service for multi-format document parsing, OCR, and form extraction | Milestone 2 | R2 |
| 6 | Form Builder Modes 1, 2, 4 | Drag-and-drop REST (Mode 1), Text AI co-builder SSE (Mode 2), Voice AI co-builder WSS (Mode 4) | Milestone 2 | R2 |
| 7 | GuardrailAgent | Spring `@Component` `@Async` real-time safety, prompt injection detection, and input/output moderation (<5ms pgvector) | Milestone 3 | R1, R3 |
| 8 | MemoryGoalAgent | Spring `@Component` `@Async` session memory state manager, conversation goal tracking via Redis opsForHash | Milestone 3 | R1, R3 |
| 9 | BillingAgent | Spring `@Component` `@Async` usage tracking, credit metering calculations, VAD silence timer (>45s warning / 60s auto-close) | Milestone 3 | R1, R3 |
| 10 | RagSearchAgent | Spring `@Component` `@Async` vector semantic search over form/user documents via pgvector embeddings | Milestone 3 | R1, R3 |
| 11 | EvaluationAgent | Spring `@Component` `@Async` `@EventListener` post-session candidate scoring, rubric evaluation, and report generation | Milestone 3 | R1, R3 |
| 12 | Form Filler Modes 1, 2, 3, 4 | Static REST (Mode 1), Text Chat + Dynamic UI (Mode 2), Cascaded Voice WSS (Mode 3), Omni-Modal Native WSS (Mode 4) | Milestone 3 | R3 |
| 13 | System Governance & Security | Multi-tenancy workspace isolation, RBAC (ADMIN, CREATOR, VIEWER), Credit Ledger pre-checks, Webhooks, API keys | Milestone 4 | R1 |
| 14 | E2E Testing Suite & Hardening | 4-Tier test suite (Unit, Integration, Workflow E2E, Governance E2E), Tier 5 adversarial hardening, Forensic Audit verification | Milestone 5 | Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: System Architecture & Event Foundation | Event declarations, thread pools, frontend/backend test config, Gemini WS Mock | none | IN_PROGRESS |
| 2 | M2: Form Builder Agent Suite (R2) | LayoutAgent, SchemaGeneratorAgent, PersonaConfigAgent, DocumentIngestionAgent, Builder Modes 1, 2, 4 | M1 | PLANNED |
| 3 | M3: Form Filler Suite & Audio Decoupling (R1, R3) | GuardrailAgent, MemoryGoalAgent, BillingAgent, RagSearchAgent, EvaluationAgent, Filler Modes 1-4 | M1 | PLANNED |
| 4 | M4: System Governance & Security (R1) | Multi-tenancy, RBAC, Credit Ledger, Webhook engine, secret API keys | M1, M3 | PLANNED |
| 5 | M5: Dual Track E2E Testing & Hardening | 4-Tier test suite build & execution (TEST_READY.md), Tier 5 adversarial hardening, Forensic Audit CLEAN | M1, M2, M3, M4 | PLANNED |

## Interface Contracts

### Event Bus Interface (`com.reForm.backend.ai.event`)
- `FormLayoutModificationEvent`: `(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)`
- `GuardrailValidationEvent`: `(UUID sessionId, String inputContent, String direction)`
- `SessionEndedEvent`: `(UUID sessionId, UUID formId, UUID submissionId, String closeReason)`
- `BillingUsageEvent`: `(UUID workspaceId, UUID sessionId, String meterType, long unitsUsed)`
- `DocumentIngestionEvent`: `(UUID documentId, byte[] content, String mimeType)`
- `RagQueryEvent`: `(UUID formId, String queryText, int topK)`

### Agent Component Interface (`com.reForm.backend.ai.agent`)
- `LayoutAgent`: `@Component @Async @EventListener void onLayoutModification(FormLayoutModificationEvent event)`
- `SchemaGeneratorAgent`: `@Component @Async CompletableFuture<FormSchemaDto> generateSchema(SchemaGenerationRequest request)`
- `PersonaConfigAgent`: `@Component @Async CompletableFuture<PersonaConfigDto> configurePersona(PersonaConfigRequest request)`
- `DocumentIngestionAgent`: `@Component @Async CompletableFuture<IngestedDocDto> processDocument(DocumentIngestionEvent event)`
- `GuardrailAgent`: `@Component @Async CompletableFuture<GuardrailResultDto> checkSafety(GuardrailValidationEvent event)`
- `MemoryGoalAgent`: `@Component @Async CompletableFuture<GoalChecklistDto> updateGoals(UUID sessionId, String fieldKey, Object value)`
- `BillingAgent`: `@Component @Async void processBilling(BillingUsageEvent event)`
- `RagSearchAgent`: `@Component @Async CompletableFuture<List<RagChunkDto>> searchVectorContext(RagQueryEvent event)`
- `EvaluationAgent`: `@Component @Async @EventListener void evaluateSession(SessionEndedEvent event)`

## Code Layout

```
backend/
├── src/main/java/com/reForm/backend/
│   ├── ai/
│   │   ├── agent/                 # Spring @Component agents (LayoutAgent, SchemaGeneratorAgent, PersonaConfigAgent, DocumentIngestionAgent, GuardrailAgent, MemoryGoalAgent, BillingAgent, RagSearchAgent, EvaluationAgent)
│   │   ├── event/                 # Spring ApplicationEvents (FormLayoutModificationEvent, GuardrailValidationEvent, SessionEndedEvent, etc.)
│   │   ├── service/               # IAiVoiceAdapter, GeminiLiveVoiceAdapter, CascadedVoiceAdapter, SessionContextService
│   │   ├── tool/handler/          # Tool handlers delegating heavy tasks to event bus
│   │   └── websocket/             # VoiceSyncWSHandler (zero-blocking audio frame loop)
│   ├── governance/                # Multi-tenancy, RBAC, Credit Ledger, Webhook Dispatcher, API Keys
│   ├── form/                      # Form, FormBlock, FormAiAgentProfile entities & controllers
│   └── submission/                # Submission entities & evaluation services
└── src/test/java/com/reForm/backend/   # JUnit 5 & SpringBootTest integration tests

frontend/
├── src/
│   ├── app/                       # Next.js 16 App Router pages
│   ├── components/                # React 19 block canvas, chat co-builder, voice filler components
│   └── lib/                       # WebSocket client, audio streamer, API client
├── tests/                         # Vitest unit tests & React Testing Library tests
└── e2e/                           # Playwright E2E browser automation & Gemini WS mock server
```
