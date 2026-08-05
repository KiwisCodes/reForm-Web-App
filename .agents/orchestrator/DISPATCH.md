## 2026-08-05T08:23:57Z
Execute the user request recorded in `/Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md`.

Output path: `backend/knowledge/pth/week4/09_agent_architecture_master_design.md`

Tasks:
1. Research the full reForm platform context:
   - Week 3 docs (`backend/knowledge/pth/week3/`, especially 02, 03, 07)
   - Week 4 docs (`backend/knowledge/pth/week4/`, 03, 06, 07, 08)
   - Codebase: `LayoutAgent.java`, `IToolCallHandler.java`, `ToolCallRegistry.java`, all 18 tool handler strategy implementation files, entity/repository classes.
2. Discover & Define ALL agents across all 5 pipelines (Form Builder, Form Filler, File/Media Processing, Background Pipeline, Session Lifecycle):
   - Include Existing/Built agents (`LayoutAgent`, 18 `IToolCallHandler` strategy beans, `FormAiAgentProfile` entity).
   - Include Designed agents (`GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `EvaluationAgent`, `RagSearchAgent`, sub-agents: `CodeAnalysisSubAgent`, `DocumentOcrSubAgent`, `ScoringSubAgent`).
   - Discover Net-New agents by auditing user journeys.
3. Provide a full deep-dive section for EVERY agent:
   - Name & Role (one sentence)
   - Trigger Mechanism (event, tool call, schedule, socket frame, etc.)
   - Input / Output (exact data types in and out)
   - Design Pattern (Pattern name(s) + WHY)
   - Technology Choice (Tech + WHY over alternatives)
   - SOLID + KISS Justification (one bullet per applicable SOLID principle)
   - Open Questions
4. Format into master design document at `backend/knowledge/pth/week4/09_agent_architecture_master_design.md`:
   1. "What is Agentic in 2026?"
   2. Agent Catalog Table
   3. Platform Mermaid Architecture Diagram
   4. Per-Agent Deep-Dive Sections grouped by pipeline
   5. Technology Decision Matrix
   6. Design Principles Summary
