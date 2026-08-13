# 10: Agentic Definition & Master Agent Catalog

**Author**: Lead Technical Writer & Architect  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.ai`)  
**Target Document**: `backend/knowledge/pth/week4/10_agentic_definition_and_agent_catalog.md`  
**Date**: 2026-08-13  
**Version**: 2.0.0-RELEASE  

---

## Executive Summary

The **reForm Agent Architecture Master Design** defines the enterprise specification for autonomous, low-latency, conversational AI agents within the reForm platform monolith. Utilizing Java 21, Spring Boot 3.3, Virtual Threads, PostgreSQL `pgvector`, Redis RAM state management, Spring AI, and Google Gemini 3.1 Live / 3.6 Flash models, reForm bridges static web forms and real-time multimodal voice/text micro-interviews.

This document establishes the conceptual framing of "Agentic in 2026" and presents the master catalog of all 43 platform agents, sub-agents, tool handlers, and profile entities across 5 distinct processing pipelines, categorized into priority-ranked build groups (P0–P3).

---

## 1. What is Agentic in 2026?

### 1.1 Enterprise Java Spring Boot Definition of "Agent" (2026)

In 2026 enterprise software engineering, an **Agent** is no longer defined as an unconstrained LLM prompt loop or an opaque, third-party Python framework (such as LangChain or CrewAI) running outside the primary application boundary. Within the reForm Java 21 / Spring Boot 3.3 monolith, an **Agent is defined as**:

> **A Spring `@Component` service bean that encapsulates LLM/SLM client invocation, vector database retrieval, state tracking, tool execution strategies, and deterministic safety guardrails within a compiled, type-safe domain boundary.**

Instead of granting LLMs direct, unmediated access to database tables or network sockets, reForm encapsulates intelligence inside Spring IoC components. The LLM acts as an **adaptive reasoning engine**, while the Spring `@Component` agent enforces domain constraints, executes transactional side-effects, manages state transitions in Redis, and streams structured frames over WebSockets.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   2026 AGENTIC ENCAPSULATION                           │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                      Spring Boot @Component Agent Boundary                      │   │
│   │                                                                                │   │
│   │   ┌───────────────┐     ┌───────────────────┐     ┌────────────────────────┐   │   │
│   │   │  Perception   │ ──► │  Reasoning (LLM)  │ ──► │  Action (Tool Strategy) │   │   │
│   │   │ (WS / Events) │     │ (Gemini Live/Flash│     │ (IToolCallHandler)     │   │   │
│   │   └───────────────┘     └───────────────────┘     └────────────────────────┘   │   │
│   │           ▲                       │                            │               │   │
│   │           │                       ▼                            ▼               │   │
│   │   ┌───────────────┐     ┌───────────────────┐     ┌────────────────────────┐   │   │
│   │   │ State (Redis) │ ◄── │ Guardrails & RAG  │ ◄── │ Persistence (Postgres) │   │   │
│   │   └───────────────┘     └───────────────────┘     └────────────────────────┘   │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

An Agentic System in reForm is characterized by six fundamental pillars:
1. **Perception**: Receiving continuous, real-time input streams (16kHz PCM audio frames, JSON WebSocket text messages, Spring ApplicationEvents, scheduled cron triggers).
2. **Reasoning & Planning**: Decomposing high-level goals into multi-step execution plans using ReAct loops or structured JSON generation (Gemini 3.1 Live / 3.6 Flash).
3. **Action Execution (Tool Calling)**: Invoking strongly typed domain operations via strategy beans (`IToolCallHandler`) registered in a central registry (`ToolCallRegistry`).
4. **Reflection & Self-Correction**: Validating schema outputs, scoring candidate answers against rubrics post-session, and auto-correcting malformed block definitions.
5. **Stateful Memory**: Retaining short-term working memory in Redis (`opsForHash()`) and long-term semantic memory in PostgreSQL `pgvector` HNSW indexes.
6. **Safety & Guardrails**: Executing sub-2ms embedding similarity checks against injection vectors before LLM context ingestion.

---

### 1.2 Paradigm Mapping Matrix

reForm maps modern agentic AI paradigms directly to Java Spring Boot architectural patterns:

| Agentic Paradigm | Theoretical Definition | reForm Enterprise Implementation Mapping |
| :--- | :--- | :--- |
| **ReAct (Reasoning + Acting)** | Interleaved reasoning ("thought") and action ("tool call") loops where LLM observes tool outputs before responding. | Gemini 3.1 Live emitting WebSocket `toolCall` frames $\rightarrow$ `ToolCallRegistry` executing `@Component` `IToolCallHandler` beans $\rightarrow$ returning `toolResponse` JSON frames back to Gemini over Socket 2. |
| **Plan-and-Execute** | Decomposing high-level user intent into a multi-step sequence of discrete sub-actions. | `LayoutAgent` receiving intent (`ADD_CONTACT_SECTION`), prompting Gemini 3.6 Flash Mode 2 for block JSON schemas, validating constraints via `SchemaAgent`, and appending blocks to PostgreSQL JSONB columns. |
| **Multi-Agent Orchestration** | Deploying specialized concurrent agents handling isolated tasks rather than 1 monolithic prompt. | Form Filler Pipeline where `GuardrailAgent` (safety), `MemoryGoalAgent` (state), `BillingAgent` (metering), `RagSearchAgent` (RAG), and `EvaluationAgent` (scoring) execute in parallel during active voice sessions. |
| **Tool Calling & Registry** | Binding LLM function declarations to executable backend native functions. | `ToolCallRegistry` auto-wiring all 18 `IToolCallHandler` strategy beans via Spring IoC container and dispatching function calls in $O(1)$ constant time. |
| **Reflection & Self-Correction** | In-flight or post-hoc evaluation of output quality, safety, and validity. | `SchemaAgent` validating generated block definitions and auto-healing missing fields; `EvaluationAgent` performing multi-criteria post-session scoring. |
| **Dynamic Sub-Agent Spawning** | Main agent instantiating background worker micro-agents on demand for heavy processing. | `SubAgentFactory` spawning `CodeAnalysisSubAgent`, `DocumentOcrSubAgent`, `AudioTranscriptionSubAgent`, or `ScoringSubAgent` on Spring `AsyncTaskExecutor` Virtual Thread pools. |

---

## 2. Agent Catalog Table (Ranked by MVP Build Priority)

Below is the master catalog of all **43 components** (Agents, Sub-Agents, Tool Handlers, and Profile Entities) across reForm's 5 processing pipelines, **reorganized by build priority and functional group**.

### Priority Legend
- **P0 — MVP Critical**: The app cannot function at all without these. Build first.
- **P1 — Core Experience**: Features that complete the core user journey (build → fill → save). Build immediately after P0.
- **P2 — Production Readiness**: Required before going live with real users (security, billing, analytics). Build before launch.
- **P3 — Growth & Scale**: Enhances the platform but not required for initial launch. Build post-launch.

---

### Group A: Foundation Infrastructure (Build First — Nothing Works Without These)

*These components form the absolute skeleton of the app. Without them, no WebSocket session can start, no AI persona can load, and no tool can execute.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `FormAiAgentProfile` | **P0** | **Built** | Domain Entity holding prompt & voice settings | JPA `@Entity` (`form_ai_agent_profiles`) |
| 2 | `EndSessionToolHandler` | **P0** | **Built** | 3-stage graceful socket teardown & cleanup | Spring `@Component` + Java Virtual Threads |
| 3 | `SessionStateAgent` | **P0** | **Net-New** | Snapshot & WebSocket cluster failover manager | Redis Hash + Redisson + Spring Session |
| 4 | `MemoryGoalAgent` | **P0** | **Designed** | In-flight goal state & checklist tracker | Redis `opsForHash()` (`session:{id}:goals`) |

> **Why P0?** `FormAiAgentProfile` is the database row that tells every session *who the AI persona is*. `EndSessionToolHandler` is the only way to cleanly terminate a session without resource leaks. `SessionStateAgent` prevents session data loss on reconnect. `MemoryGoalAgent` prevents the AI from repeating questions or losing track of progress.

---

### Group B: Form Builder Pipeline (Build the Form Creation Journey)

*The form builder is the first thing a user interacts with — they must be able to create, modify, configure, and publish a form before anyone can fill it.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 5 | `ModifyFormLayoutToolHandler` | **P0** | **Built** | Dispatches layout modification events | Spring `@Component` + `ApplicationEventPublisher` |
| 6 | `LayoutAgent` | **P0** | **Built** | Form layout modification event processor | Gemini 3.6 Flash + Spring `@Async` + Postgres JSONB |
| 7 | `ConfigureFillerPersonaToolHandler` | **P0** | **Built** | Persists interviewer prompt & voice settings | Spring `@Component` + `FormAiAgentProfileRepository` |
| 8 | `PublishFormToolHandler` | **P0** | **Built** | Locks form layout & generates public slug URL | Spring `@Component` + `FormRepository` |
| 9 | `SchemaAgent` | **P1** | **Net-New** | Form block schema compiler & constraint validator | Jackson JsonSchema + Jakarta Validation 3.0 |
| 10 | `GenerateContentFromDocToolHandler` | **P1** | **Built** | Generates quiz questions from uploaded docs | Spring `@Component` + Gemini 3.6 Flash |
| 11 | `FormVersioningAgent` | **P2** | **Net-New** | Form schema diffing & migration manager | java-diff-utils + Jackson JsonPatch (RFC 6902) |
| 12 | `ThemeAgent` | **P3** | **Net-New** | AI theme & WCAG accessibility synthesizer | Tailwind CSS v4 + Color4j + Gemini 3.6 Flash |
| 13 | `TranslationAgent` | **P3** | **Net-New** | Multilingual form localization engine | Gemini 3.6 Flash + Redis String Cache |

> **Build Order Rationale:** You can't fill a form that doesn't exist. `ModifyFormLayout` → `LayoutAgent` → `ConfigureFillerPersona` → `PublishForm` is the minimum builder journey. `SchemaAgent` validates block quality (P1). `FormVersioningAgent` matters once forms are live and being edited (P2). `ThemeAgent` and `TranslationAgent` are premium features (P3).

---

### Group C: Form Filler Pipeline (The Core Interview Experience)

*Once a form is published, respondents fill it via AI-guided voice/text interviews. These components power the actual conversation.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 14 | `SaveFieldResponseToolHandler` | **P0** | **Built** | Persists field responses to PostgreSQL | Spring `@Component` + `FieldResponseRepository` |
| 15 | `LookupFormProgressToolHandler` | **P0** | **Built** | Queries form completion percentage & counts | Spring `@Component` + Redis Session State |
| 16 | `SkipQuestionToolHandler` | **P1** | **Built** | Marks field skipped with reason metadata | Spring `@Component` + Postgres Field State |
| 17 | `EvaluateResponseToolHandler` | **P1** | **Built** | Scores candidate answer against rubric (0-100) | Spring `@Component` + Jackson JsonNode |
| 18 | `FlagForHumanReviewToolHandler` | **P1** | **Built** | Flags suspicious/ambiguous responses for review | Spring `@Component` + Postgres Audit Log |
| 19 | `ValidationAgent` | **P1** | **Net-New** | Cross-field business & external API validator | Hibernate Validator + Resilience4j CircuitBreaker |
| 20 | `AdaptiveBranchingAgent` | **P2** | **Net-New** | Dynamic conditional question DAG traverser | SpEL / MVEL + JGraphT DAG Engine |
| 21 | `VoiceSpeechAgent` | **P2** | **Net-New** | Real-time PCM VAD & audio stream processor | Netty ByteBuf + Silero VAD + WebRTC Native |
| 22 | `ScoringSubAgent` | **P2** | **Designed** | Multi-criteria competency worker sub-agent | Spring `AsyncTaskExecutor` + Gemini 3.6 Flash |

> **Build Order Rationale:** `SaveFieldResponse` + `LookupFormProgress` are the absolute minimum — the app must save answers and know progress. `SkipQuestion`, `EvaluateResponse`, `FlagForHumanReview`, and `ValidationAgent` complete the quality layer (P1). `AdaptiveBranching` and `VoiceSpeechAgent` add sophistication (P2). `ScoringSubAgent` is a background worker enhancement (P2).

---

### Group D: File & Media Processing Pipeline (Document Handling)

*Users upload resumes, documents, and reference files. These components process, analyze, and index them.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 23 | `RequestFileUploadToolHandler` | **P1** | **Built** | Pushes file upload UI dropzone to browser | Spring `@Component` + WebSocket Text Frame |
| 24 | `AnalyzeUploadedFileToolHandler` | **P1** | **Built** | Vision & document analysis execution | Spring `@Component` + Gemini Vision API |
| 25 | `ExtractStructuredDataToolHandler` | **P1** | **Built** | Extracts key-value fields from files/resumes | Spring `@Component` + Gemini 3.6 Flash |
| 26 | `SaveSessionTranscriptToolHandler` | **P1** | **Built** | Persists timestamped dialogue transcript array | Spring `@Component` + Postgres JSONB |
| 27 | `SaveAudioRecordingToolHandler` | **P1** | **Built** | Compresses & uploads session audio PCM to S3 | Spring `@Component` + AWS S3 SDK v2 |
| 28 | `DocumentChunkingEmbeddingAgent` | **P1** | **Net-New** | Semantic chunker & HNSW vector indexer | `text-embedding-004` (768d) + `pgvector` HNSW |
| 29 | `MalwareScanAgent` | **P2** | **Net-New** | Security virus & sandbox media inspector | ClamAV REST + Apache Tika Magic Inspection |
| 30 | `DocumentOcrSubAgent` | **P2** | **Designed** | Scanned document OCR & text extractor | Apache Tika + Tesseract OCR 5.0 |
| 31 | `AudioTranscriptionSubAgent` | **P3** | **Net-New** | Speaker diarization & STT worker agent | Deepgram STT / Faster-Whisper + Spring AMQP |

> **Build Order Rationale:** File upload + analysis + transcript saving are core to the builder and filler experience (P1). `DocumentChunkingEmbeddingAgent` powers RAG search (P1). `MalwareScan` and `DocumentOcr` are security/quality hardening (P2). `AudioTranscriptionSubAgent` is a premium post-processing feature (P3).

---

### Group E: Knowledge Retrieval & RAG Pipeline (AI Context Enrichment)

*These components give the AI persona access to uploaded documents and knowledge during live sessions.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 32 | `SearchUserDocumentToolHandler` | **P1** | **Built** | In-session vector document retrieval tool | Spring `@Component` + `pgvector` HNSW |
| 33 | `RagSearchAgent` | **P1** | **Designed** | Hybrid vector RAG search engine | `text-embedding-004` + `pgvector` Cosine Search |

> **Build Order Rationale:** `SearchUserDocumentToolHandler` is the tool Gemini calls to retrieve knowledge. `RagSearchAgent` is the engine behind it. Both are P1 because the AI interviewer needs document context to ask informed questions.

---

### Group F: Real-Time UI & Notification Tools (Client-Facing Interactivity)

*These tools push interactive widgets and alerts to the user's browser during live sessions.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 34 | `RenderDynamicUIToolHandler` | **P1** | **Built** | Pushes interactive widgets (stars, pickers) | Spring `@Component` + WebSocket Text Frame |
| 35 | `SendNotificationToolHandler` | **P2** | **Built** | Triggers alerts (Dashboard, Email, Slack) | Spring `@Component` + Spring Event Bus |

> **Build Order Rationale:** `RenderDynamicUI` enhances the filling experience by rendering rich UI components inline (P1). `SendNotification` is needed for production alerting but not for the core MVP loop (P2).

---

### Group G: Security, Safety & Compliance Pipeline (Production Hardening)

*These agents protect the platform from attacks, enforce safety, and maintain audit trails. Not needed for local dev, but mandatory before real users.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 36 | `GuardrailAgent` | **P2** | **Designed** | Real-time embedding similarity safety guard | `text-embedding-004` + `pgvector` HNSW (<2ms) |
| 37 | `SecurityAuditAgent` | **P2** | **Net-New** | Real-time PII & prompt injection auditor | Spring Security + OpenSearch Append-Only Log |

> **Build Order Rationale:** Both are critical before launching to real users (P2). `GuardrailAgent` prevents prompt injection attacks in real-time. `SecurityAuditAgent` writes immutable compliance audit trails.

---

### Group H: Background Processing & Post-Session Pipeline (Async Workers)

*These agents run after sessions end or on scheduled intervals. They handle scoring, analytics, billing, and archival.*

| # | Agent Name | Priority | Status | Primary Role | Primary Tech Stack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 38 | `EvaluationAgent` | **P1** | **Designed** | Post-session summary & scoring report engine | Gemini 3.6 Flash + Spring `@Async` |
| 39 | `TokenMeteringAgent` | **P2** | **Net-New** | Real-time quota & LLM token billing meter | Redis Lua Scripts + Redisson Distributed Lock |
| 40 | `BillingAgent` | **P2** | **Designed** | VAD silence metering & credit balance check | Spring `@Scheduled` + Redis TTL + VAD Frames |
| 41 | `AnalyticsAggregationAgent` | **P2** | **Net-New** | Drop-off & conversion analytics rollups | Postgres Window Functions + Redis Caches |
| 42 | `CodeAnalysisSubAgent` | **P3** | **Designed** | Sandboxed code syntax & execution checker | Docker Java SDK + GraalVM Sandbox |
| 43 | `ArchivalAgent` | **P3** | **Net-New** | Compliance retention & cold storage lifecycle | AWS S3 Glacier + Spring Batch 5.0 |

> **Build Order Rationale:** `EvaluationAgent` generates the candidate report that form owners see — core to the product value (P1). `TokenMetering`, `Billing`, and `Analytics` are required for monetization and production monitoring (P2). `CodeAnalysis` and `Archival` are specialized premium features (P3).

---

### Priority Summary Dashboard

| Priority | Count | Description | When to Build |
| :--- | :--- | :--- | :--- |
| **P0 — MVP Critical** | **8** | Foundation infrastructure + minimum builder/filler journey | **Now (Sprint 1-2)** |
| **P1 — Core Experience** | **14** | Complete user journeys, file handling, RAG, evaluation | **Immediately after P0 (Sprint 3-5)** |
| **P2 — Production Readiness** | **13** | Security, billing, analytics, advanced filler features | **Before public launch (Sprint 6-8)** |
| **P3 — Growth & Scale** | **8** | Theming, translation, OCR, code sandbox, archival | **Post-launch iteration** |

| Status | Count |
| :--- | :--- |
| **Built** (code exists and compiles) | **18** |
| **Designed** (architecture specified, not yet coded) | **8** |
| **Net-New** (architecture specified, significant new work) | **17** |
