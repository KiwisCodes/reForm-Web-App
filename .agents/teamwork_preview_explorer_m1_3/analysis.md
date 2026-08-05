# Milestone 1: Frontend Test Setup, Gemini WS Mock & Unified Test Runner Analysis

**Author**: Explorer 3 (Milestone 1)  
**Date**: 2026-08-05  
**Target Scope**: Frontend testing dependencies, Vitest/Playwright configurations, Gemini WebSocket Mock Server (`frontend/e2e/mocks/gemini-ws-mock.ts`), and Unified Test Runner (`./scripts/run_all_tests.sh`).

---

## Executive Summary

This analysis defines the requirements, architecture, and detailed specifications for the frontend test framework setup, local Gemini WebSocket mock server, and unified test runner script for **reForm Web App Milestone 1**. 

Currently, `frontend/package.json` only contains standard Next.js dependencies (`next`, `react`, `react-dom`) and lacks testing tools. Furthermore, configuration files (`vitest.config.ts`, `playwright.config.ts`) and test directories (`tests/`, `e2e/`) do not exist. To establish a robust 4-tier testing foundation, the project must install `vitest`, `@testing-library/react`, `@playwright/test`, `msw`, `jsdom`, and `ws`, set up standalone Gemini WebSocket mocking, and provide `./scripts/run_all_tests.sh` to orchestrate backend Maven unit/integration tests and frontend Vitest/Playwright suites with unified exit code reporting.

---

## 1. Frontend Setup & Dependencies Audit

### 1.1 Current State Analysis
Observation of `/Users/apple/Coding-projects/reForm-Web-App/frontend/package.json`:
- **Existing Dependencies**: `next` (16.2.6), `react` (19.2.4), `react-dom` (19.2.4).
- **Existing DevDependencies**: `@tailwindcss/postcss` (^4), `@types/node` (^20), `@types/react` (^19), `@types/react-dom` (^19), `babel-plugin-react-compiler` (1.0.0), `eslint` (^9), `eslint-config-next` (16.2.6), `tailwindcss` (^4), `typescript` (^5).
- **Existing Scripts**: `"dev"`, `"build"`, `"start"`, `"lint"`.
- **Missing Directories & Configs**: `vitest.config.ts`, `playwright.config.ts`, `tests/`, `e2e/`.

### 1.2 Required Package & Script Additions

| Dependency Category | Package Name | Version Target | Purpose |
|---------------------|--------------|----------------|---------|
| **Unit & Integration Testing** | `vitest` | `^3.0.0` | Fast Unit/Component test runner with React 19 ESM support |
| | `@testing-library/react` | `^16.0.0` | React component DOM rendering and user event testing |
| | `@testing-library/jest-dom` | `^6.6.0` | Custom DOM element matchers (`toBeInTheDocument`, etc.) |
| | `@testing-library/user-event` | `^14.5.0` | User interaction simulation (click, type, drag) |
| | `jsdom` | `^26.0.0` | Browser DOM emulation environment for Vitest |
| | `@vitejs/plugin-react` | `^4.3.0` | JSX/TSX support in Vitest configuration |
| **API & WS Mocking** | `msw` | `^2.7.0` | Mock Service Worker for REST and HTTP interceptors |
| | `ws` | `^8.18.0` | Standalone WebSocket server implementation for Node.js |
| | `@types/ws` | `^8.5.14` | TypeScript definitions for `ws` package |
| | `tsx` | `^4.19.0` | TypeScript execution engine for running standalone scripts |
| **End-to-End Testing** | `@playwright/test` | `^1.50.0` | Multi-browser E2E automation (Chromium, Firefox, WebKit) |

### 1.3 Required `frontend/package.json` Scripts Update

```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui",
    "mock:gemini-ws": "tsx e2e/mocks/gemini-ws-mock.ts"
  }
}
```

---

## 2. Configuration Files Specification

### 2.1 Vitest Configuration (`frontend/vitest.config.ts`)
```typescript
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules/', 'e2e/'],
    },
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
```

### 2.2 Test Setup (`frontend/tests/setup.ts`)
```typescript
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
```

### 2.3 Playwright Configuration (`frontend/playwright.config.ts`)
```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    video: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'npm run dev',
      url: 'http://localhost:3000',
      reuseExistingServer: !process.env.CI,
      timeout: 120 * 1000,
    },
    {
      command: 'npm run mock:gemini-ws',
      port: 8081,
      reuseExistingServer: !process.env.CI,
      timeout: 30 * 1000,
    }
  ],
});
```

---

## 3. Gemini WebSocket Mock Server Specification (`frontend/e2e/mocks/gemini-ws-mock.ts`)

### 3.1 Overview & Architecture
The local Gemini WebSocket Mock Server is a standalone Node.js process using the `ws` library. It emulates Gemini's Multimodal Live API (`wss://generativelanguage.googleapis.com/ws/...`) or local proxy endpoints (`ws://localhost:8081/ws/gemini`).

It enables deterministic client testing for voice UI components, real-time PCM audio streaming, and bidirectional JSON tool calls without needing real Gemini API keys or network latency.

```
┌─────────────────────────────────┐           WebSocket (ws://localhost:8081)         ┌─────────────────────────────────┐
│     Next.js Frontend / Client    │ ───────────────────────────────────────────────► │   Gemini WebSocket Mock Server  │
│  (Audio Recorder & Canvas UI)   │ ◄─────────────────────────────────────────────── │  (gemini-ws-mock.ts on Node.js) │
└─────────────────────────────────┘                                                   └─────────────────────────────────┘
                                       1. setup / setupComplete
                                       2. Audio PCM Chunks (Base64 / Binary)
                                       3. Tool Calls (JSON functionCall)
                                       4. Audio Response Broadcast (20ms frames)
```

### 3.2 Key Functional Requirements

1. **Protocol Handshake & Session Setup**:
   - Accepts incoming WebSocket connection at `ws://localhost:8081`.
   - Listens for client `setup` message containing model configuration, system instructions, and tool definitions.
   - Emits `{ "setupComplete": {} }` frame upon receipt.

2. **Simulated PCM Audio Streaming**:
   - Receives inbound user audio chunks (16kHz / 24kHz PCM mono) in JSON format `{ realtimeInput: { mediaChunks: [...] } }` or raw binary buffers.
   - Generates simulated PCM audio responses back to client. Emits chunks at 20ms or 50ms intervals using base64 encoded audio payloads inside `{ serverContent: { modelTurn: { parts: [{ inlineData: { mimeType: "audio/pcm", data: "<base64>" } }] } } }`.

3. **Audio Interruption Handling**:
   - If client sends audio or a `userTurn` message while mock server is actively broadcasting audio frames, mock server immediately halts active audio interval and sends `{ serverContent: { interrupted: true } }`.

4. **JSON Tool Calls & Function Ingestion**:
   - Supports triggering tool call payloads to simulate AI form modifications (e.g. `FormLayoutModificationEvent` triggers).
   - Payload format:
     ```json
     {
       "serverContent": {
         "modelTurn": {
           "parts": [
             {
               "functionCall": {
                 "name": "modifyFormLayout",
                 "id": "call_12345",
                 "args": {
                   "action": "ADD_BLOCK",
                   "blockType": "TEXT_INPUT",
                   "label": "Full Name",
                   "required": true
                 }
               }
             }
           ]
         }
       }
     }
     ```
   - Validates client's `toolResponse` message:
     ```json
     {
       "toolResponse": {
         "functionResponses": [
           {
             "id": "call_12345",
             "response": { "status": "SUCCESS", "blockId": "block_abc" }
           }
         ]
       }
     }
     ```
   - Emits turn completion signal `{ serverContent: { turnComplete: true } }`.

5. **Scenario Control & Mode Switcher**:
   - Accepts control headers or initial setup configuration flags:
     - `SCENARIO_NORMAL`: standard conversational response.
     - `SCENARIO_TOOL_CALL`: triggers immediate `modifyFormLayout` tool call.
     - `SCENARIO_VAD_SILENCE`: emulates 45s / 60s silence without response (testing `BillingAgent` timeout).
     - `SCENARIO_ERROR`: emits 429 Rate Limit error or terminates connection abruptly.

### 3.3 Reference Implementation Outline (`frontend/e2e/mocks/gemini-ws-mock.ts`)

```typescript
import { WebSocketServer, WebSocket } from 'ws';
import http from 'http';

const PORT = parseInt(process.env.GEMINI_MOCK_PORT || '8081', 10);
const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'UP', mock: 'Gemini-WS-Mock' }));
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server });

console.log(`[Gemini-WS-Mock] Starting server on port ${PORT}...`);

wss.on('connection', (ws: WebSocket, req: http.IncomingMessage) => {
  console.log(`[Gemini-WS-Mock] Client connected from ${req.socket.remoteAddress}`);
  
  let audioStreamInterval: NodeJS.Timeout | null = null;
  let isInterrupted = false;

  const stopAudioBroadcast = () => {
    if (audioStreamInterval) {
      clearInterval(audioStreamInterval);
      audioStreamInterval = null;
    }
  };

  ws.on('message', (message: string | Buffer) => {
    try {
      const data = JSON.parse(message.toString());

      // 1. Setup Phase
      if (data.setup) {
        console.log('[Gemini-WS-Mock] Received setup configuration');
        ws.send(JSON.stringify({ setupComplete: {} }));
        return;
      }

      // 2. Client Realtime Input (Audio or Text)
      if (data.realtimeInput) {
        stopAudioBroadcast();
        isInterrupted = false;

        // Check for special test trigger commands in text
        const textChunk = data.realtimeInput.mediaChunks?.[0]?.data;
        if (textChunk === 'TRIGGER_TOOL_CALL') {
          // Send Function Call
          ws.send(JSON.stringify({
            serverContent: {
              modelTurn: {
                parts: [{
                  functionCall: {
                    name: 'updateFormLayout',
                    id: `call_${Date.now()}`,
                    args: { action: 'INSERT_BLOCK', blockType: 'TEXT_INPUT', label: 'Email Address' }
                  }
                }]
              }
            }
          }));
          return;
        }

        // Standard response audio streaming simulation
        let frameCount = 0;
        audioStreamInterval = setInterval(() => {
          if (ws.readyState !== WebSocket.OPEN || isInterrupted) {
            stopAudioBroadcast();
            return;
          }

          frameCount++;
          // Simulated 20ms PCM audio frame (synthetic silence/sine wave base64 payload)
          const dummyPcmBase64 = 'AAAAAP////8AAAAA//';
          ws.send(JSON.stringify({
            serverContent: {
              modelTurn: {
                parts: [{
                  inlineData: { mimeType: 'audio/pcm;rate=16000', data: dummyPcmBase64 }
                }]
              }
            }
          }));

          if (frameCount >= 25) { // ~500ms response
            stopAudioBroadcast();
            ws.send(JSON.stringify({ serverContent: { turnComplete: true } }));
          }
        }, 20);
      }

      // 3. Client Tool Response
      if (data.toolResponse) {
        console.log('[Gemini-WS-Mock] Received tool response:', data.toolResponse);
        ws.send(JSON.stringify({ serverContent: { turnComplete: true } }));
      }

    } catch (err) {
      console.error('[Gemini-WS-Mock] Error processing message:', err);
    }
  });

  ws.on('close', () => {
    stopAudioBroadcast();
    console.log('[Gemini-WS-Mock] Client disconnected');
  });
});

server.listen(PORT, () => {
  console.log(`[Gemini-WS-Mock] Listening on ws://localhost:${PORT}`);
});
```

---

## 4. Unified Test Runner Script Specification (`./scripts/run_all_tests.sh`)

### 4.1 Requirements & Execution Flow
The unified test runner script `./scripts/run_all_tests.sh` provides a single entry point for developers and CI/CD pipelines to run all test tiers across backend (Spring Boot / Maven) and frontend (Vitest / Playwright).

#### Key Requirements:
1. **Location & Permissions**: `./scripts/run_all_tests.sh`, executable (`chmod +x`).
2. **Environment Auto-Detection**:
   - Backend: Prefers `./mvnw` inside `backend/` directory; falls back to global `mvn`.
   - Frontend: Prefers `npm` inside `frontend/` directory.
3. **Execution Modes (CLI Flags)**:
   - Default (no flags): Runs Backend Unit/Integration Tests -> Frontend Vitest -> Frontend Playwright E2E.
   - `--unit-only`: Runs Backend tests and Frontend Vitest; skips Playwright E2E.
   - `--e2e-only`: Runs Frontend Playwright E2E tests only.
   - `--backend-only`: Runs Backend Maven tests only.
   - `--frontend-only`: Runs Frontend Vitest & Playwright tests only.
   - `--coverage`: Generates code coverage reports.
   - `--help` / `-h`: Displays usage instructions.
4. **Mock Server Lifecycle Management**:
   - Ensures `gemini-ws-mock.ts` is started (if running E2E) or verifies port 8081 availability.
   - Utilizes `trap` to cleanly terminate background processes (Mock Server, Next.js dev server) on script exit or interrupt (SIGINT/SIGTERM).
5. **Exit Code & Summary Reporting**:
   - Tracks exit status of each test phase independently.
   - Displays colorized status report (GREEN for PASS, RED for FAIL).
   - Returns exit code `0` iff ALL target test suites succeed. Returns exit code `1` if any test suite fails.

### 4.2 Complete Script Specification (`scripts/run_all_tests.sh`)

```bash
#!/usr/bin/env bash

# ==============================================================================
# reForm Web App - Unified Test Execution Script
# Milestone 1: System Architecture & Event Foundation
# ==============================================================================

set -eo pipefail

# Color Codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"

# Execution Flags
RUN_BACKEND=true
RUN_FRONTEND_UNIT=true
RUN_FRONTEND_E2E=true
GENERATE_COVERAGE=false

show_help() {
  cat << EOF
Usage: ./scripts/run_all_tests.sh [OPTIONS]

Options:
  --unit-only       Run only backend unit tests and frontend Vitest unit tests.
  --e2e-only        Run only frontend Playwright E2E tests.
  --backend-only    Run only backend Maven tests.
  --frontend-only   Run only frontend Vitest and Playwright tests.
  --coverage        Enable code coverage reporting.
  -h, --help        Show this help message.

Examples:
  ./scripts/run_all_tests.sh
  ./scripts/run_all_tests.sh --unit-only
  ./scripts/run_all_tests.sh --frontend-only
EOF
}

# Parse Arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --unit-only)
      RUN_BACKEND=true
      RUN_FRONTEND_UNIT=true
      RUN_FRONTEND_E2E=false
      shift
      ;;
    --e2e-only)
      RUN_BACKEND=false
      RUN_FRONTEND_UNIT=false
      RUN_FRONTEND_E2E=true
      shift
      ;;
    --backend-only)
      RUN_BACKEND=true
      RUN_FRONTEND_UNIT=false
      RUN_FRONTEND_E2E=false
      shift
      ;;
    --frontend-only)
      RUN_BACKEND=false
      RUN_FRONTEND_UNIT=true
      RUN_FRONTEND_E2E=true
      shift
      ;;
    --coverage)
      GENERATE_COVERAGE=true
      shift
      ;;
    -h|--help)
      show_help
      exit 0
      ;;
    *)
      echo -e "${RED}Unknown option: $1${NC}"
      show_help
      exit 1
      ;;
  esac
done

# Result Tracking
BACKEND_STATUS="SKIPPED"
FRONTEND_UNIT_STATUS="SKIPPED"
FRONTEND_E2E_STATUS="SKIPPED"
OVERALL_EXIT=0

cleanup() {
  echo -e "\n${BLUE}[Test Runner] Cleaning up background background processes...${NC}"
  # Kill any spawned mock servers or background processes if needed
  pkill -f "gemini-ws-mock.ts" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo -e "${BLUE}====================================================${NC}"
echo -e "${BLUE}    reForm Web App Unified Test Execution Suite     ${NC}"
echo -e "${BLUE}====================================================${NC}\n"

# 1. Backend Maven Tests
if [ "$RUN_BACKEND" = true ]; then
  echo -e "${YELLOW}► Running Backend Unit & Integration Tests (Maven)...${NC}"
  cd "$BACKEND_DIR"
  
  MAVEN_CMD="./mvnw"
  if [ ! -f "$MAVEN_CMD" ]; then
    MAVEN_CMD="mvn"
  fi

  MAVEN_ARGS="test"
  if [ "$GENERATE_COVERAGE" = true ]; then
    MAVEN_ARGS="test jacoco:report"
  fi

  if $MAVEN_CMD $MAVEN_ARGS; then
    BACKEND_STATUS="PASSED"
    echo -e "${GREEN}✓ Backend tests passed.${NC}\n"
  else
    BACKEND_STATUS="FAILED"
    OVERALL_EXIT=1
    echo -e "${RED}✗ Backend tests failed.${NC}\n"
  fi
fi

# 2. Frontend Vitest Unit Tests
if [ "$RUN_FRONTEND_UNIT" = true ]; then
  echo -e "${YELLOW}► Running Frontend Unit & Component Tests (Vitest)...${NC}"
  cd "$FRONTEND_DIR"

  VITEST_CMD="npm run test"
  if [ "$GENERATE_COVERAGE" = true ]; then
    VITEST_CMD="npm run test:coverage"
  fi

  if $VITEST_CMD; then
    FRONTEND_UNIT_STATUS="PASSED"
    echo -e "${GREEN}✓ Frontend unit tests passed.${NC}\n"
  else
    FRONTEND_UNIT_STATUS="FAILED"
    OVERALL_EXIT=1
    echo -e "${RED}✗ Frontend unit tests failed.${NC}\n"
  fi
fi

# 3. Frontend Playwright E2E Tests
if [ "$RUN_FRONTEND_E2E" = true ]; then
  echo -e "${YELLOW}► Running Frontend E2E Tests (Playwright)...${NC}"
  cd "$FRONTEND_DIR"

  if npm run test:e2e; then
    FRONTEND_E2E_STATUS="PASSED"
    echo -e "${GREEN}✓ Playwright E2E tests passed.${NC}\n"
  else
    FRONTEND_E2E_STATUS="FAILED"
    OVERALL_EXIT=1
    echo -e "${RED}✗ Playwright E2E tests failed.${NC}\n"
  fi
fi

# Summary Report
echo -e "${BLUE}====================================================${NC}"
echo -e "${BLUE}              TEST EXECUTION SUMMARY                ${NC}"
echo -e "${BLUE}====================================================${NC}"

format_status() {
  case $1 in
    PASSED) echo -e "${GREEN}PASSED${NC}" ;;
    FAILED) echo -e "${RED}FAILED${NC}" ;;
    SKIPPED) echo -e "${YELLOW}SKIPPED${NC}" ;;
  esac
}

echo -e " Backend Maven Tests:         $(format_status $BACKEND_STATUS)"
echo -e " Frontend Vitest Unit Tests:  $(format_status $FRONTEND_UNIT_STATUS)"
echo -e " Playwright E2E Tests:        $(format_status $FRONTEND_E2E_STATUS)"
echo -e "${BLUE}====================================================${NC}"

if [ $OVERALL_EXIT -eq 0 ]; then
  echo -e "${GREEN}OVERALL RESULT: ALL TESTS PASSED${NC}"
else
  echo -e "${RED}OVERALL RESULT: TEST SUITE FAILED${NC}"
fi

exit $OVERALL_EXIT
```

---

## 5. Summary & Actionable Recommendations for Implementation

1. **Update `frontend/package.json`**:
   - Add devDependencies: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`, `@vitejs/plugin-react`, `@playwright/test`, `msw`, `ws`, `@types/ws`, `tsx`.
   - Add scripts: `"test"`, `"test:watch"`, `"test:coverage"`, `"test:e2e"`, `"test:e2e:ui"`, `"mock:gemini-ws"`.
2. **Create Configuration Files**:
   - Create `frontend/vitest.config.ts` and `frontend/tests/setup.ts`.
   - Create `frontend/playwright.config.ts`.
3. **Implement Local Gemini WebSocket Mock Server**:
   - Create `frontend/e2e/mocks/gemini-ws-mock.ts` adhering to the full event lifecycle (handshake, PCM audio frame interval, function call emission/ingestion, interruption handling).
4. **Implement Unified Test Execution Script**:
   - Create `scripts/run_all_tests.sh` with `chmod +x`.
