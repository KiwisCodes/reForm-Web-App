# Scope: Milestone 1 — System Architecture & Event Foundation

## Architecture
Spring Boot async event bus infrastructure (`ApplicationEventPublisher`, `@EnableAsync`, `@EventListener`), thread pool executor configurations (`TaskExecutor`), frontend test framework setup (Vitest, RTL, Playwright dependencies in `package.json`), backend test base classes, and local Gemini WebSocket mock server (`e2e/mocks/gemini-ws-mock.ts`).

## Features Included
1. Base Spring event declarations in `com.reForm.backend.ai.event`:
   - `FormLayoutModificationEvent`
   - `GuardrailValidationEvent`
   - `SessionEndedEvent`
   - `BillingUsageEvent`
   - `DocumentIngestionEvent`
   - `RagQueryEvent`
2. Thread pool executor configuration in `com.reForm.backend.config.AsyncConfig` (core pool 10, max pool 50, queue capacity 500).
3. Frontend testing dependencies in `frontend/package.json` (`vitest`, `@testing-library/react`, `@playwright/test`, `msw`, `jsdom`) and `playwright.config.ts`.
4. Gemini WebSocket Mock Server (`frontend/e2e/mocks/gemini-ws-mock.ts`) supporting simulated PCM audio streaming and JSON tool calls.
5. Unified test execution script (`scripts/run_all_tests.sh`).

## Interface Contracts
- `com.reForm.backend.ai.event.FormLayoutModificationEvent`
- `com.reForm.backend.ai.event.GuardrailValidationEvent`
- `com.reForm.backend.ai.event.SessionEndedEvent`
- `com.reForm.backend.ai.event.BillingUsageEvent`
- `com.reForm.backend.ai.event.DocumentIngestionEvent`
- `com.reForm.backend.ai.event.RagQueryEvent`

## Status
IN_PROGRESS
