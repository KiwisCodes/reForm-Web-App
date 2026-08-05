## 2026-08-05T08:26:24Z
You are the Lead Technical Writer & Architect Worker for the reForm platform Agent Architecture Master Design.

Read ORIGINAL_REQUEST: /Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md

Your task is to author the definitive Master Design Document at:
`/Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_design.md`

You have full access to all research findings synthesized by the 3 research subagents:
1. Documentation Survey: `/Users/apple/Coding-projects/reForm-Web-App/.agents/explorer_docs_survey/analysis.md`
2. Codebase Survey: `/Users/apple/Coding-projects/reForm-Web-App/.agents/explorer_codebase_survey/analysis.md`
3. Net-New Agent Discovery: `/Users/apple/Coding-projects/reForm-Web-App/.agents/explorer_net_new_discovery/analysis.md`

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Document Requirements & Structure:
The generated document MUST be written directly to `backend/knowledge/pth/week4/09_agent_architecture_master_design.md`.

Structure:
# reForm Agent Architecture Master Design

## 1. What is Agentic in 2026?
- Definition of an "Agent" in enterprise Java Spring Boot 2026 context (@Component encapsulation, LLM/SLM integration, vector retrieval, state tracking, tool execution, safety guardrails).
- Paradigm Mapping: ReAct, Plan-and-Execute, Multi-Agent Orchestration, Tool-Calling, Reflection/Self-Correction, Dynamic Sub-Agent Spawning.

## 2. Agent Catalog Table
- Structured markdown table of all 26+ agents/sub-agents across 5 pipelines.
- Columns: Agent Name | Pipeline / Category | Status (Built / Designed / Net-New) | Primary Role | Primary Tech Stack.

## 3. Platform Mermaid Architecture Diagram
- Complete, high-level Mermaid diagram illustrating all 5 pipelines, event flows, WebSocket twin-sockets, ToolCallRegistry routing, vector/Redis storage, background Virtual Threads, and agent interactions.

## 4. Per-Agent Deep-Dive Sections (Grouped by Pipeline)
Group into 5 Pipeline subsections:
- 4.1 Form Builder Pipeline (`LayoutAgent`, `SchemaAgent`, `ThemeAgent`, `TranslationAgent`, `FormVersioningAgent`, `ConfigureFillerPersonaToolHandler`, `ModifyFormLayoutToolHandler`, `PublishFormToolHandler`, `GenerateContentFromDocToolHandler`)
- 4.2 Form Filler Pipeline (`AdaptiveBranchingAgent`, `VoiceSpeechAgent`, `ValidationAgent`, `ScoringSubAgent`, `EvaluateResponseToolHandler`, `FlagForHumanReviewToolHandler`, `LookupFormProgressToolHandler`, `SaveFieldResponseToolHandler`, `SkipQuestionToolHandler`)
- 4.3 File & Media Processing Pipeline (`MalwareScanAgent`, `DocumentOcrSubAgent`, `AudioTranscriptionSubAgent`, `DocumentChunkingEmbeddingAgent`, `AnalyzeUploadedFileToolHandler`, `ExtractStructuredDataToolHandler`, `RequestFileUploadToolHandler`, `SaveAudioRecordingToolHandler`, `SaveSessionTranscriptToolHandler`)
- 4.4 Background Pipeline (`AnalyticsAggregationAgent`, `TokenMeteringAgent`, `ArchivalAgent`, `CodeAnalysisSubAgent`, `EvaluationAgent`, `BillingAgent`)
- 4.5 Session Lifecycle Pipeline (`SessionStateAgent`, `SecurityAuditAgent`, `GuardrailAgent`, `MemoryGoalAgent`, `RagSearchAgent`, `EndSessionToolHandler`, `SearchUserDocumentToolHandler`, `RenderDynamicUIToolHandler`, `SendNotificationToolHandler`, `FormAiAgentProfile`)

For EVERY single agent / agent category in Sections 4.1 to 4.5, provide all 7 required sub-fields:
1. Name & Role (one sentence)
2. Trigger Mechanism (event, tool call, schedule, socket frame, etc.)
3. Input / Output (exact Java 21 record / Jackson DTO / JsonNode data types)
4. Design Pattern (Pattern name(s) + WHY)
5. Technology Choice (Tech + WHY over alternatives)
6. SOLID + KISS Justification (one bullet per applicable SOLID principle: SRP, OCP, LSP, ISP, DIP + KISS bullet)
7. Open Questions

## 5. Technology Decision Matrix
- Comprehensive comparison table & rationale comparing:
  - pgvector vs Pinecone/Weaviate
  - Redis vs Hazelcast
  - Gemini 3.1 Live / 3.6 Flash vs OpenAI GPT-4o / Realtime
  - Java Virtual Threads vs Reactive WebFlux
  - Spring Events vs Kafka/RabbitMQ

## 6. Design Principles Summary
- Architectural governance summary covering SOLID, KISS, DRY, YAGNI, Event-Driven Decoupling, Security/RBAC tool gating, and Resiliency/Fault Tolerance.

Working directory for worker artifacts: `/Users/apple/Coding-projects/reForm-Web-App/.agents/worker_draft_master_design/`
Create `/Users/apple/Coding-projects/reForm-Web-App/.agents/worker_draft_master_design/handoff.md` when finished and send a message back to parent.
