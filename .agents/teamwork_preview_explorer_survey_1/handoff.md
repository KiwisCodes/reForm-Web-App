# Handoff Report: Step 0 Survey — Part 1

**Agent**: `teamwork_preview_explorer_survey_1`  
**Working Directory**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_1`  
**Target Report**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_survey_1/survey_report_1.md`  

---

## 1. Observation

- **Build Systems & Dependencies**:
  - `backend/pom.xml`: Spring Boot 4.1.0-parent (Java 21), `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-websocket`, `spring-boot-starter-webflux`, `bucket4j_jdk17-core` (8.19.0), PostgreSQL driver.
  - `frontend/package.json`: Next.js 16.2.6, React 19.2.4, TailwindCSS 4, TypeScript 5.
- **Spring Boot Async & Event Bus**:
  - `BackendApplication.java` line 10: `@EnableAsync` and `@EnableCaching` enabled.
  - `LayoutAgent.java` lines 37, 49, 50: `@Component`, `@Async`, `@EventListener` listening to `FormLayoutModificationEvent`.
  - `ModifyFormLayoutToolHandler.java` line 43: `eventPublisher.publishEvent(new FormLayoutModificationEvent(...))`.
- **WebSocket & Thread Execution**:
  - `VoiceSyncWSHandler.java` line 41: `@Component` extending `BinaryWebSocketHandler`, manages binary audio frames and socket lifecycle.
  - `GeminiLiveVoiceAdapter.java` line 49: `@Component("geminiLiveVoiceAdapter")`, opens outbound WebSocket to Google Gemini Live API. Line 282-300: `handleToolCall` executes `toolCallRegistry.executeTool(...)` synchronously on the WebSocket payload thread.
- **Existing Agents & Tool Handlers**:
  - `LayoutAgent` is the **ONLY** implemented agent bean in `com.reForm.backend.ai.agent`.
  - 18 tool handlers exist under `com.reForm.backend.ai.tool.handler` (`builder`, `filler`, `file`, `audio`, `ui`, `universal`), but most return direct synchronous/stub responses instead of dispatching events to `@Async` agents.
  - Missing required agents: `SchemaGeneratorAgent`, `PersonaConfigAgent`, `DocumentIngestionAgent`, `GuardrailAgent`, `MemoryGoalAgent`, `BillingAgent`, `RagSearchAgent`, `EvaluationAgent`.
- **Persistence Layer**:
  - PostgreSQL entities: `Form.java` (JSONB blocks column via `@JdbcTypeCode(SqlTypes.JSON)`), `FormAiAgentProfile.java` (1-to-1 dynamic persona profile), `Submission.java` (JSONB answers), `User.java`, `Workspace.java`.
  - Redis: `SessionTracker.java` manages `session:{userId}` hashes with 2-hour TTL lease refreshed on PING frames.

---

## 2. Logic Chain

1. **Premise**: Requirements R1, R2, R3 specify a decoupled multi-agent architecture with dedicated Spring `@Component` service agents for Form Builder, Form Filler, Security/Guardrails, Memory/State, Evaluation, and Billing, with zero blocking calls on the main WebSockets thread.
2. **Observation**: Codebase inspection reveals that while `@EnableAsync` and Spring `ApplicationEventPublisher` are configured, only `LayoutAgent` is implemented as an `@Async @EventListener` agent.
3. **Observation**: `GeminiLiveVoiceAdapter` executes tool calls (`toolCallRegistry.executeTool(...)`) synchronously inside the WebSocket event handling loop. Handlers like `EvaluateResponseToolHandler` and `SaveFieldResponseToolHandler` execute inline mock logic on this thread.
4. **Deduction**: The main WebSockets audio thread currently incurs potential latency from synchronous tool execution. To satisfy the acceptance criteria, the remaining 8 agents must be created as Spring `@Component` service beans, and tool handlers must delegate heavy tasks to these agents via event-driven `@Async` listeners.

---

## 3. Caveats

- CLI execution via `run_command` timed out due to sandbox approval prompt, so test suites were inspected via static code analysis rather than live terminal execution.
- Frontend component architecture was surveyed via `package.json` and directory layout; deeper UI component analysis will be conducted by survey part 2 / design team.

---

## 4. Conclusion

The reForm system infrastructure possesses a solid foundation (Spring Boot 3.4, Java 21, Next.js 16, PostgreSQL JSONB, Redis TTL session tracker, Gemini Live WebSocket proxy, Spring Event Bus). However, 8 of the 9 required Spring `@Component` agents are missing, and tool handlers currently execute synchronously on the WebSocket thread. Implementing the complete multi-agent suite and converting tool handlers to `@Async` event dispatchers is the primary technical objective.

---

## 5. Verification Method

To independently verify these findings:
1. View `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/BackendApplication.java` to confirm `@EnableAsync`.
2. Inspect `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/agent/` to confirm only `LayoutAgent.java` exists.
3. Inspect `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/service/GeminiLiveVoiceAdapter.java` line 282-300 to observe synchronous `toolCallRegistry.executeTool(...)` handling.
4. Execute backend build & test commands:
   - `./mvnw test` in `/Users/apple/Coding-projects/reForm-Web-App/backend`
   - `npm run lint` in `/Users/apple/Coding-projects/reForm-Web-App/frontend`
