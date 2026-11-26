#!/usr/bin/env bash
# ==============================================================================
# SearchForge Automated End-to-End Regression Suite Runner (Bash)
# Usage: ./scripts/run-regression.sh [--live-http]
# ==============================================================================
set -euo pipefail

echo "=== [1/3] Running Backend Integration & Regression Suites (@Tag('integration')) ==="
(cd backend && ./mvnw test -Dgroups=integration --batch-mode --no-transfer-progress)

echo "=== [2/3] Running Frontend Unit Suite ==="
(cd frontend && npm test)

if [ "${1:-}" = "--live-http" ]; then
  echo "=== [3/3] Running Frontend-Backend Live HTTP Integration Suite ==="
  (cd frontend && npm run test:integration)
else
  echo "=== [3/3] Skipping Live HTTP Integration (pass --live-http when stack is up) ==="
fi

echo "SearchForge Regression Suite PASSED."
