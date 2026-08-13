# reForm Agent Architecture Master Specification & TOC

**Author**: Lead Technical Writer & Architect  
**Platform**: reForm Enterprise Form Builder & Conversational AI Platform (`com.reForm.backend.ai`)  
**Target Directory**: `backend/knowledge/pth/week4/`  
**Date**: 2026-08-13  
**Version**: 2.0.0-RELEASE  

---

## Executive Overview

The **reForm Agent Architecture Master Specification** defines the complete enterprise blueprint for autonomous, low-latency, conversational AI agents within the reForm platform monolith. Utilizing Java 21, Spring Boot 3.3, Virtual Threads, PostgreSQL `pgvector`, Redis RAM state management, Spring AI, and Google Gemini 3.1 Live / 3.6 Flash models, reForm bridges static web forms and real-time multimodal voice/text micro-interviews.

Due to the depth and scope of the design (covering 43 agents/tools, 5 pipelines, decision matrices, and codebase audits), the master design has been modularized into 8 dedicated files (indices `09` through `16`), followed by execution guides (indices `17` and `18`).

---

## Master Document Index

| Index | Document Name | File Path | Scope & Focus |
| :---: | :--- | :--- | :--- |
| **09** | **Master TOC & Index** | [`09_agent_architecture_master_toc.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/09_agent_architecture_master_toc.md) | Entry point & navigation directory across all 43 agent specs. |
| **10** | **Agentic Definition & Catalog** | [`10_agentic_definition_and_agent_catalog.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/10_agent_architecture_master_catalog.md) | Enterprise Java definition of "Agentic", 6 core pillars, and ranked master catalog table for 43 components across 8 priority groups (P0–P3). |
| **11** | **Platform Architecture Diagram** | [`11_platform_mermaid_architecture_diagram.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/11_platform_mermaid_architecture_diagram.md) | Full-system end-to-end Mermaid architecture diagram mapping ingress, twin-sockets, tool routing, 5 pipelines, and storage. |
| **12** | **Foundation & Builder Deep Dives** | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | Deep dives for Agents #1–10 (Foundation Infrastructure + Form Builder Pipeline, including Spring AI `LayoutAgent` & `SchemaAgent`). |
| **13** | **Filler & Voice Deep Dives** | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | Deep dives for Agents #11–22 (Form Filler Pipeline, `ThemeAgent`, `TranslationAgent`, `ValidationAgent`, `AdaptiveBranching`, `VoiceSpeechAgent`). |
| **14** | **File, Media & RAG Deep Dives** | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | Deep dives for Agents #23–33 (File upload tools, `DocumentChunkingEmbeddingAgent`, `DocumentOcrSubAgent`, `RagSearchAgent` + `PgVectorStore`). |
| **15** | **UI, Security & Async Deep Dives** | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | Deep dives for Agents #34–43 (UI tools, `GuardrailAgent`, `SecurityAuditAgent`, `EvaluationAgent`, `TokenMeteringAgent`, `BillingAgent`, `ArchivalAgent`). |
| **16** | **Tech Matrix, SOLID & Code Audit** | [`16_technology_decisions_and_implementation_audit.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/16_technology_decisions_and_implementation_audit.md) | Technology decision matrix (with Spring AI), SOLID/KISS/DRY compliance rules, and 100% honest codebase implementation audit scorecard. |
| **17** | **Mode 3 Cascaded Master Guide** | [`17_mode3_cascaded_voice_complete_master_guide.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/17_mode3_cascaded_voice_complete_master_guide.md) | Complete end-to-end operational guide for Mode 3 Cascaded Voice (Deepgram STT → Gemini 3.6 Flash → Cartesia TTS). |
| **18** | **Spring AI Strategy & Mode Analysis** | [`18_spring_ai_integration_strategy_and_mode_analysis.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/18_spring_ai_integration_strategy_and_mode_analysis.md) | Comprehensive Spring AI integration strategy, Mode 2 full fit, Mode 3 middle fit, Mode 4 Bidi WebSocket exclusion analysis, and pom.xml dependencies. |

---

## Agent Quick Lookup (Agent # → Document File)

| Agent / Tool Name | # | Target File | Priority |
| :--- | :---: | :--- | :---: |
| `FormAiAgentProfile` | 1 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `EndSessionToolHandler` | 2 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `SessionStateAgent` | 3 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `MemoryGoalAgent` | 4 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `ModifyFormLayoutToolHandler` | 5 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `LayoutAgent` | 6 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `ConfigureFillerPersonaToolHandler` | 7 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `PublishFormToolHandler` | 8 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P0 |
| `SchemaAgent` | 9 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P1 |
| `GenerateContentFromDocToolHandler` | 10 | [`12_foundation_and_builder_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/12_foundation_and_builder_pipeline_deep_dives.md) | P1 |
| `FormVersioningAgent` | 11 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P2 |
| `ThemeAgent` | 12 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P3 |
| `TranslationAgent` | 13 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P3 |
| `SaveFieldResponseToolHandler` | 14 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P0 |
| `LookupFormProgressToolHandler` | 15 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P0 |
| `SkipQuestionToolHandler` | 16 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P1 |
| `EvaluateResponseToolHandler` | 17 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P1 |
| `FlagForHumanReviewToolHandler` | 18 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P1 |
| `ValidationAgent` | 19 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P1 |
| `AdaptiveBranchingAgent` | 20 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P2 |
| `VoiceSpeechAgent` | 21 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P2 |
| `ScoringSubAgent` | 22 | [`13_filler_and_voice_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/13_filler_and_voice_pipeline_deep_dives.md) | P2 |
| `RequestFileUploadToolHandler` | 23 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `AnalyzeUploadedFileToolHandler` | 24 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `ExtractStructuredDataToolHandler` | 25 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `SaveSessionTranscriptToolHandler` | 26 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `SaveAudioRecordingToolHandler` | 27 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `DocumentChunkingEmbeddingAgent` | 28 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `MalwareScanAgent` | 29 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P2 |
| `DocumentOcrSubAgent` | 30 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P2 |
| `AudioTranscriptionSubAgent` | 31 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P3 |
| `SearchUserDocumentToolHandler` | 32 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `RagSearchAgent` | 33 | [`14_file_media_and_rag_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/14_file_media_and_rag_pipeline_deep_dives.md) | P1 |
| `RenderDynamicUIToolHandler` | 34 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P1 |
| `SendNotificationToolHandler` | 35 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `GuardrailAgent` | 36 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `SecurityAuditAgent` | 37 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `EvaluationAgent` | 38 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P1 |
| `TokenMeteringAgent` | 39 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `BillingAgent` | 40 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `AnalyticsAggregationAgent` | 41 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P2 |
| `CodeAnalysisSubAgent` | 42 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P3 |
| `ArchivalAgent` | 43 | [`15_ui_security_and_background_pipeline_deep_dives.md`](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week4/15_ui_security_and_background_pipeline_deep_dives.md) | P3 |
