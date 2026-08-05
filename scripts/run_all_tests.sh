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
  echo -e "\n${BLUE}[Test Runner] Cleaning up background processes...${NC}"
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
