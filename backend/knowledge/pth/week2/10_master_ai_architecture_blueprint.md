# reForm AI Subsystem: Master Architectural Knowledge Base Index
**Document Version:** 10.0 (Master Index)  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## Executive Summary

The master architectural blueprint for the reForm AI subsystem has been decomposed into **13 dedicated, modular technical reference documents** (`10_` through `22_`) inside `backend/knowledge/pth/week2/`.

Each document provides in-depth technical analysis, concrete Java/JSON code implementations, mathematical formulas, and sequence diagrams.

---

## 📚 Complete Knowledge Base Directory

1. **[10. AI Feature Spectrum & 4-Mode Architectural Strategy](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/10_ai_feature_spectrum_and_4mode_strategy.md)**
   * Transports, target engines, cost comparisons, latency metrics, acoustic nuances, barge-in capabilities, and selection decision matrix across Modes 1, 2, 3, and 4.

2. **[11. AI Pricing Models & Market Benchmarks (2026 Update)](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/11_ai_pricing_models_and_market_benchmarks.md)**
   * March 2026 Gemini 3.6 Flash benchmarks (SWE-Bench Pro 58.7%, Terminal-bench 78%, Elo 1421, CharXiv) & token rates ($1.50 in / $7.50 out). Mode 4 Audio streaming formulas ($3.00 in / $12.00 out) and 10m/20m/30m session projections. 2026 STT/LLM/TTS comparative market analysis.

3. **[12. AI Design Patterns & Package Architecture](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/12_ai_design_patterns_and_package_architecture.md)**
   * Refactored package layout (`com.reForm.backend.ai`), Bridge/Adapter (`IAiVoiceAdapter`), Strategy (`IAiModelProviderStrategy`), Factory (`VoiceAdapterFactory`), Single Responsibility Principle, and Open/Closed Principle scalability/modifiability analysis.

4. **[13. BYOK Security & User Model Selection Architecture](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/13_byok_security_and_user_model_selection.md)**
   * Bring Your Own Key (BYOK) AES-256-GCM encryption in PostgreSQL `workspaces`, RAM decryption lifetime, `SessionContextService.resolveApiKey()` fallback, frontend dropdown selection, and zero `if/else` strategy matching.

5. **[14. FormAgentConfig Entity & Prompt Management](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/14_form_agent_config_entity_and_prompt_management.md)**
   * Database entity design (`FormAgentConfig.java`), One-to-One binding with `Form` / `ConversationalBlock`, fields (`modelKey`, `systemPrompt`, `voiceName`, `temperature`, `byokApiKeyEncrypted`), and runtime prompt compilation with `FormGoals`.

6. **[15. Mode 4 Native Live Voice Architecture](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/15_mode4_native_live_voice_architecture.md)**
   * `GeminiLiveVoiceAdapter.java`, Google Live API WSS endpoint (`wss://generativelanguage.googleapis.com/.../BidiGenerateContent`), `BidiGenerateContentSetup` JSON payload mapping, Base64 PCM audio tunneling, and native speech interruption handling.

7. **[16. Mode 3 Cascaded Voice Pipeline & Barge-in Handling](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/16_mode3_cascaded_voice_pipeline_and_bargein.md)**
   * `CascadedVoiceAdapter.java`, 2-WS + 1 Stateless HTTP REST optimization, Deepgram STT WSS → Gemini 3.6 Flash REST → Cartesia TTS WSS. Deepgram speech detection (`speech_started`), Cartesia cancellation frames (`{"cancel": true}`), client buffer flushing (`FLUSH_AUDIO_BUFFER`), and single-node capacity math (2,000 to 3,000 voice sessions per node).

8. **[17. Function Calling (`toolCall`) Mechanics](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/17_function_calling_toolcall_mechanics.md)**
   * OpenAI origins (June 2023), industry standardization across Google/Anthropic. 4-Step concrete code & JSON schema execution flow (Tool declaration JSON schema, inbound `toolCall` frame, Spring event dispatching, outbound `toolResponse` frame).

9. **[18. Spring Event-Driven Architecture & Dependency Injection](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/18_spring_event_driven_architecture_and_di.md)**
   * Lombok `@RequiredArgsConstructor` constructor injection over `@Autowired` field injection. POJOs & Java 21 `record`s. Event publisher & listener implementations. Monolith checkout problem BEFORE event-driven, Pros & Cons, 3 decision questions. Message Queues problem BEFORE MQ, RabbitMQ vs Apache Kafka comparison matrix.

10. **[19. Model Context Protocol (MCP) Server Integration Strategy](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/19_mcp_server_integration_strategy.md)**
    * Anthropic MCP open standard, JSON-RPC 2.0 over SSE transport. The 3 primitives (Resources `reform://`, Tools `tools/list` & `tools/call`, Prompts). REST vs Tool Calling vs MCP comparison. Commercial BYO-AI advantage of exposing `https://api.reform.com/mcp` to Claude Desktop / Cursor / ChatGPT.

11. **[20. Production Agent Catalog & End-to-End Scenarios](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/20_production_agent_catalog_and_scenarios.md)**
    * 6 Production Agents (with **pgvector** embeddings for Guardrail Agent), Scenario Phase A (Form Builder John) & Phase B (Form Filler Sarah).

12. **[21. Mermaid System Architecture & Sequence Diagrams](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/21_mermaid_system_architecture_diagrams.md)**
    * Complete collection of 5 Mermaid System Diagrams (1. Unified Package Layout, 2. Handshake Sequence, 3. Mode 3 Cascaded Interruption Sequence, 4. Mode 4 Event Sequence, 5. Redis Ping-Pong Sequence).

13. **[22. Technical Q&A Knowledge Base](file:///Users/apple/Coding-projects/reForm-Web-App/backend/knowledge/pth/week2/22_technical_qa_knowledge_base.md)**
    * Comprehensive 12-Question Technical Knowledge Base (RFC 6455 opcodes `0x1`, `0x2`, `0x8`, `0x9`, `0xA`, Lettuce connection encapsulation, raw Lettuce vs `RedisTemplate`, `ConcurrentHashMap` safety, stateful sockets vs scalable architecture, frontend WebAssembly VAD silence trimming, package refactoring, `SessionTracker` 2h TTL fix, Bucket4j `LettuceBasedProxyManager` fix, scalability/modifiability, constructor injection, and POJOs).
