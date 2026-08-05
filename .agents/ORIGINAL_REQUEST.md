# Original User Request

## 2026-08-05T15:23:47+07:00

Design and document a **complete production-grade Agent Architecture** for the reForm platform — covering all agents needed across the Form Builder, Form Filler, background pipeline, file processing, and session lifecycle. The output is **design documentation only** (no code), placed in `backend/knowledge/pth/week4/`.

Working directory: /Users/apple/Coding-projects/reForm-Web-App
Integrity mode: development

---

## Requirements

### R1. Understand & Audit the App
Research the full reForm platform context by reading:
- All agent docs in `backend/knowledge/pth/week3/` (especially `02`, `03`, `07`)
- Existing agent code: `LayoutAgent.java`, `IToolCallHandler.java`, `ToolCallRegistry.java`, all 18 tool handler files
- All entity/repository classes to understand the data model
- Week4 docs created so far: `03`, `06`, `07`, `08` in `backend/knowledge/pth/week4/`

Map every user journey that could benefit from an agent. Explain what "agentic" means in 2026 software engineering and how reForm's `@Component` beans match that definition.

### R2. Discover & Define All Agents — Full Deep-Dive Per Agent
For every agent across ALL 5 pipelines (Form Builder co-building, Form Filler interview, file/media processing, background async pipeline, session lifecycle), provide a **full deep-dive section**:

**Per-agent section must include:**
1. **Name & Role**: what it does in one sentence
2. **Trigger Mechanism**: how it is activated (event, tool call, schedule, socket frame)
3. **Input / Output**: exact data types in and out
4. **Design Pattern**: which pattern(s) — Strategy, Observer, Factory, Chain of Responsibility, etc. — and WHY that pattern
5. **Technology Choice**: which technology (pgvector, Redis, Gemini 3.6 Flash, Spring Events, WebSocket push, VirtualThread, etc.) and WHY this one over alternatives
6. **SOLID + KISS Justification**: one bullet per applicable SOLID principle explaining how the design satisfies it
7. **Open Questions**: any design decisions not yet settled

**Agents to cover (at minimum):**
- **Existing / Built**: `LayoutAgent`, all 18 `IToolCallHandler` strategy beans, `FormAiAgentProfile` entity
- **Designed but not built**: `GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `EvaluationAgent`, `RagSearchAgent`, sub-agents (`CodeAnalysisSubAgent`, `DocumentOcrSubAgent`, `ScoringSubAgent`)
- **Net-new**: discover any missing agents not yet documented by reasoning from the user journeys

### R3. Produce One Master Architecture Document
Write a single comprehensive markdown document at:
`backend/knowledge/pth/week4/09_agent_architecture_master_design.md`

Document structure:
1. **"What is Agentic in 2026?"** — conceptual framing
2. **Agent Catalog Table** — name, pipeline, pattern, tech, status (built/designed/new)
3. **Platform Mermaid Architecture Diagram** — all agents, their triggers, and data flows
4. **Per-Agent Deep-Dive Sections** (R2 format above), grouped by pipeline:
   - Form Builder Pipeline Agents
   - Form Filler Pipeline Agents
   - Session Lifecycle Agents
   - File & Media Processing Agents
   - Background / Async Pipeline Agents
5. **Technology Decision Matrix** — side-by-side comparison of key tech choices
6. **Design Principles Summary** — how the full agent system follows SOLID, OOP, KISS, and YAGNI

## Acceptance Criteria

### Documentation Quality
- [ ] Every agent has: name, trigger, input, output, pattern, technology choice, SOLID justification
- [ ] No code — design only
- [ ] Mermaid diagram showing how all agents relate to each other and the core platform
- [ ] Existing agents (`LayoutAgent`, all 18 tool handlers) are included and analyzed
- [ ] Missing agents (`GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `EvaluationAgent`, `RagSearchAgent`) are newly designed
- [ ] "Agentic in 2026" concept is explained clearly

### Coverage
- [ ] Form Builder journey fully covered (co-building, layout, publishing, persona config)
- [ ] Form Filler journey fully covered (interview, skipping, scoring, flagging, progress)
- [ ] Background pipeline covered (evaluation, billing, audit, notification)
- [ ] File/media processing covered (OCR, vision, upload)
