## 2026-08-05T07:58:04Z

You are the Implementation Worker for Milestone 1: System Architecture & Event Foundation.
Working directory: /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_worker_m1

Scope and Explorer Handoff documents to read:
- /Users/apple/Coding-projects/reForm-Web-App/.agents/ORIGINAL_REQUEST.md
- /Users/apple/Coding-projects/reForm-Web-App/PROJECT.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/sub_orch_m1/SCOPE.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_1/handoff.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_1/analysis.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_2/handoff.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_2/analysis.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_3/handoff.md
- /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_explorer_m1_3/analysis.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Tasks:
1. Backend Event Infrastructure (`backend/src/main/java/com/reForm/backend/ai/event/`):
   - Implement/update Java 21 record classes for:
     - FormLayoutModificationEvent(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)
     - GuardrailValidationEvent(UUID sessionId, String inputContent, String direction)
     - SessionEndedEvent(UUID sessionId, UUID formId, UUID submissionId, String closeReason)
     - BillingUsageEvent(UUID workspaceId, UUID sessionId, String meterType, long unitsUsed)
     - DocumentIngestionEvent(UUID documentId, byte[] content, String mimeType)
     - RagQueryEvent(UUID formId, String queryText, int topK)
   - Update caller references in ModifyFormLayoutToolHandler.java and LayoutAgent.java to match new FormLayoutModificationEvent signature.

2. Spring Async Configuration & Tests (`backend/src/main/java/com/reForm/backend/config/`):
   - Create AsyncConfig.java with @Configuration, @EnableAsync, and @Bean(name = "taskExecutor") returning ThreadPoolTaskExecutor (core=10, max=50, queue=500, thread name prefix="reForm-async-").
   - Create AsyncConfigTest.java under backend/src/test/java/com/reForm/backend/config/ verifying bean existence and executor property settings.

3. Frontend Test Setup & Dependencies (`frontend/`):
   - Update frontend/package.json with devDependencies (vitest, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, jsdom, @vitejs/plugin-react, @playwright/test, msw, ws, @types/ws, tsx) and NPM scripts (test, test:coverage, test:e2e, mock:gemini-ws).
   - Run npm install in frontend/ if needed.
   - Create frontend/vitest.config.ts, frontend/tests/setup.ts, and frontend/playwright.config.ts.

4. Local Gemini WebSocket Mock Server (`frontend/e2e/mocks/gemini-ws-mock.ts`):
   - Implement standalone Node ws WebSocket server on port 8081 supporting handshake (setupComplete), 16kHz PCM audio frame simulation, base64 payload intervals, and JSON tool call emission (updateFormLayout).

5. Unified Test Execution Script (`./scripts/run_all_tests.sh`):
   - Create executable bash script at scripts/run_all_tests.sh (make executable with chmod +x) that runs backend Maven unit tests and frontend Vitest/Playwright tests sequentially with flags (--unit-only, --e2e-only, --backend-only, --frontend-only) and exit code aggregation.

6. Build & Test Verification:
   - Run backend compilation and tests (`cd backend && ./mvnw clean test`).
   - Run frontend tests (`cd frontend && npm test` or vitest).
   - Execute `./scripts/run_all_tests.sh --unit-only`.
   - Write full implementation report and test results to /Users/apple/Coding-projects/reForm-Web-App/.agents/teamwork_preview_worker_m1/handoff.md and report back via send_message.
