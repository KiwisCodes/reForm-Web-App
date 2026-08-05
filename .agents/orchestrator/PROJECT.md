# Project: reForm Agent Architecture Master Design

## Architecture
- Target file: `backend/knowledge/pth/week4/09_agent_architecture_master_design.md`
- Context Sources:
  - `backend/knowledge/pth/week3/` (especially 02, 03, 07)
  - `backend/knowledge/pth/week4/` (especially 03, 06, 07, 08)
  - Codebase: `LayoutAgent.java`, `IToolCallHandler.java`, `ToolCallRegistry.java`, 18 strategy implementations, entities (`FormAiAgentProfile`, etc.), repositories, controllers, services.
  - User original request: `/Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md`

## Feature Inventory
| # | Feature / Requirements | Scope | Milestone | Source |
|---|------------------------|-------|-----------|--------|
| 1 | Research & Audit reForm context (Week 3/4 docs + full codebase) | Code & Doc Survey | M1 | Request |
| 2 | Catalog & Net-New Agent Discovery across 5 pipelines | Discovery | M1 | Request |
| 3 | Section 1: "What is Agentic in 2026?" | Section 1 | M2 | Request |
| 4 | Section 2: Agent Catalog Table | Section 2 | M2 | Request |
| 5 | Section 3: Platform Mermaid Architecture Diagram | Section 3 | M2 | Request |
| 6 | Section 4: Per-Agent Deep Dives (all 43 agents, 7 sub-fields each) | Section 4 | M2 | Request |
| 7 | Section 5: Technology Decision Matrix | Section 5 | M2 | Request |
| 8 | Section 6: Design Principles Summary | Section 6 | M2 | Request |
| 9 | Multi-agent Review & Empirical Verification | Review | M3 | Request |
| 10 | Forensic Integrity Audit & Handoff | Audit | M4 | Request |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Research & Discovery Survey | Survey docs, codebase, 5 pipelines | None | DONE |
| 2 | M2: Master Design Document Drafting | Draft master design doc | M1 | DONE |
| 3 | M3: Review & Empirical Verification | Dual Reviewer + Dual Challenger | M2 | DONE |
| 4 | M4: Forensic Integrity Audit | Forensic Auditor check | M3 | DONE |

## Code Layout
- Target Output: `backend/knowledge/pth/week4/09_agent_architecture_master_design.md`
- Metadata: `.agents/orchestrator/`
