#!/usr/bin/env bash
# ==============================================================================
# SearchForge End-to-End Smoke Test Script (Bash)
# Usage: ./scripts/smoke-test.sh [base_url] [admin_api_key]
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost}"
ADMIN_KEY="${2:-${SEARCHFORGE_ADMIN_API_KEY:-}}"

echo "[1/6] Checking Frontend SPA (GET ${BASE_URL}/)..."
curl -fsS "${BASE_URL}/" | grep -qi "SearchForge"

echo "[2/6] Checking Health & X-Request-Id (GET ${BASE_URL}/api/v1/health)..."
curl -fsSI "${BASE_URL}/api/v1/health" | grep -qi "X-Request-Id"

echo "[3/6] Checking Actuator Readiness (GET ${BASE_URL}/actuator/health/readiness)..."
curl -fsS "${BASE_URL}/actuator/health/readiness" | grep -q '"UP"'

echo "[4/6] Checking Search Modes & Rate Limit Headers..."
curl -fsSI "${BASE_URL}/api/v1/search?q=searchforge&mode=lexical&page=0&size=5" | grep -qi "X-RateLimit-Limit"
curl -fsS "${BASE_URL}/api/v1/search?q=inverted+index&mode=semantic&page=0&size=5" > /dev/null
curl -fsS "${BASE_URL}/api/v1/search?q=inverted+index&mode=hybrid&page=0&size=5" > /dev/null

echo "[5/6] Checking Admin Auth Enforcement (expect 401 without X-API-KEY)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/lifecycle/metrics")
if [ "$STATUS" != "401" ]; then
  echo "ERROR: Expected 401 without API key, got $STATUS" >&2
  exit 1
fi

if [ -n "$ADMIN_KEY" ]; then
  echo "[6/6] Checking Authenticated Admin Endpoint..."
  curl -fsS -H "X-API-KEY: ${ADMIN_KEY}" "${BASE_URL}/api/v1/lifecycle/metrics" > /dev/null
else
  echo "[6/6] Skipping Authenticated Admin Check (ADMIN_KEY not provided)."
fi

echo "All smoke tests passed!"
