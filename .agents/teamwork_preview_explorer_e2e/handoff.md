# Handoff Report — E2E Testing Track Survey & Infrastructure Planning

**Agent**: `teamwork_preview_explorer_e2e`  
**Working Directory**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e`  
**Target Path**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/handoff.md`  
**Survey Artifact**: `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/e2e_survey.md`  
**Date**: 2026-08-05  

---

## 1. Observation

- **Original Request**: Read `/Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md` (25 lines) defining requirements R1 (Agentic Architecture & Governance), R2 (Form Builder Agent Suite), and R3 (Form Filler Agent Suite).
- **Backend Architecture & Existing Tests**:
  - `backend/pom.xml`: Spring Boot 4.1.0 / Java 21 project with `spring-boot-starter-test` and `spring-security-test` dependencies (lines 99-108).
  - Existing test file: `backend/src/test/java/com/reForm/backend/BackendApplicationTests.java` containing `@SpringBootTest class BackendApplicationTests { @Test void contextLoads() {} }`.
  - Spring Service Agents: `LayoutAgent.java` (`@Component`, `@Async`, `@EventListener` lines 39-51 handling `FormLayoutModificationEvent`).
- **Frontend Architecture & Existing Tests**:
  - `frontend/package.json`: Next.js 16.2.6, React 19.2.4 app with scripts `"dev"`, `"build"`, `"start"`, `"lint"` (lines 5-10). Currently no test frameworks (`vitest`, `playwright`, `@testing-library/react`) declared in `devDependencies`.
- **Feature & Workflow Requirements**:
  - `backend/reForm-features.md` & `backend/architecture_design.md`:
    - Form Builder Modes: Mode 1 (Manual Drag-and-Drop), Mode 2 (Text AI Helper + Document Ingestion), Mode 4 (Voice-Conversational AI Helper).
    - Form Filler Modes: Mode 1 (Static Input Entry), Mode 2 (Conversational Text + Dynamic UI), Mode 3 (Conversational Voice PCM WebSocket + 45s warning / 60s auto-close VAD silence timer), Mode 4 (Omni-Modal Tri-modal Sync + Gemini Vision upload).
    - System Governance: Scoped workspace multi-tenancy, RBAC (`ADMIN`, `CREATOR`, `VIEWER`), Credit Ledger formula calculations and pre-session balance checks, AI Guardrail system prompt redirection + async classification, Webhook dispatching with exponential backoff, secret API keys.

---

## 2. Logic Chain

1. **Observation 1**: Backend uses Spring Boot and JUnit 5 with `@SpringBootTest`, while Frontend currently lacks test framework configuration in `package.json`.
2. **Observation 2**: Requirements R1, R2, R3 span complex multi-modal workflows (Voice PCM WebSockets, split-screen UI builders, dynamic UI button rendering, Gemini Vision file ingestion, credit ledger math, and RBAC governance).
3. **Logic Step 1**: A single testing layer is insufficient to validate all aspects. Therefore, a 4-Tier Test Architecture is required:
   - **Tier 1 (Unit)**: Fast component and agent isolation tests using JUnit 5/Mockito for backend agents (`LayoutAgent`, `BillingAgent`, etc.) and Vitest/React Testing Library for frontend block components.
   - **Tier 2 (Integration)**: Spring Boot REST controllers, `@Async` event handlers, WebSocket handlers (`VoiceSyncWSHandler`, `GeminiWebSocketHandler`), JPA JSONB converters, and Redis session state.
   - **Tier 3 (Workflow E2E)**: Playwright specs covering Form Builder (Modes 1, 2, 4) and Form Filler (Modes 1, 2, 3, 4).
   - **Tier 4 (System Governance E2E)**: Playwright specs covering RBAC, Multi-tenancy isolation, Credit Ledger pre-checks and burn rates, AI Guardrail safety redirections, and Webhook dispatching.
4. **Logic Step 2**: To ensure browser automation tests run fast and deterministically without incurring LLM cost or network flakiness, a local WebSocket mock server (`e2e/mocks/gemini-ws-mock.ts`) will simulate Gemini Live PCM audio chunks and JSON tool calls during E2E runs.
5. **Logic Step 3**: All specifications, configuration structures, execution commands, and verification matrices have been compiled into `e2e_survey.md`.

---

## 3. Caveats

- **External Live Gemini API Dependency**: E2E browser tests should run against local mock servers by default (`application-test.yml` pointing to `ws://localhost:8089`). Live API integration testing requires setting valid `GEMINI_API_KEY` environment variables.
- **Microphone Permissions in Headless Browsers**: Playwright tests for Voice Modes (Builder Mode 4, Filler Modes 3 & 4) must explicitly grant browser microphone permissions via `permissions: ['microphone']` in `playwright.config.ts`.

---

## 4. Conclusion

The E2E Testing Track Survey and Infrastructure Plan is complete and fully documented in `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/e2e_survey.md`.

Key Infrastructure Components Defined:
- **Frontend Test Dependencies**: Vitest, React Testing Library, Playwright (`@playwright/test`), MSW, JSDOM.
- **Backend Test Runner**: `./mvnw test` and `./mvnw verify`.
- **4-Tier Testing Structure**: Tier 1 (Unit), Tier 2 (Backend Integration & Async Events), Tier 3 (Builder Modes 1, 2, 4 & Filler Modes 1, 2, 3, 4 Workflow E2E), Tier 4 (System Governance E2E).
- **Gemini WebSocket Mocking Strategy**: Local Node.js WebSocket mock server for PCM audio streaming and dynamic UI tool calls.
- **Victory Criteria**: 100% test pass rate across Tiers 1-4 with zero failures and CLEAN Forensic Auditor verdict.

---

## 5. Verification Method

To verify this survey and handoff report:
1. Inspect survey document:
   ```bash
   view_file /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/e2e_survey.md
   ```
2. Verify section completeness: Confirm coverage of Form Builder (Modes 1, 2, 4), Form Filler (Modes 1, 2, 3, 4), Governance, Tier 1-4 definitions, `package.json` updates, `playwright.config.ts`, and test execution commands.
3. Verify file path existence:
   - `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/e2e_survey.md`
   - `/Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_e2e/handoff.md`
