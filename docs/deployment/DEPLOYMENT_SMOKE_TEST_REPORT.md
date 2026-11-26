# SearchForge Deployment & Smoke Test Report

**Test Date:** 2026-11-26  
**Target Artifact:** `backend/target/searchforge-0.0.1-SNAPSHOT.jar` (`Spring Boot 4.1.2`, Java 21) + Automated Smoke Scripts (`scripts/smoke-test.ps1`, `scripts/smoke-test.sh`)

---

## 1. Environment Scope & Limitations

* **Live Packaged-JAR Execution (Verified):** Booted `searchforge-0.0.1-SNAPSHOT.jar` directly on `localhost:18080` (`--spring.profiles.active=dev` with H2 in-memory storage and `DevDataLoader`) and executed live HTTP smoke checks.
* **Docker Compose Multi-Container Stack (Blocked / Not Executed):** `docker info` failed with `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified` because Docker Desktop is not currently running on the Windows host. Therefore, live multi-container checks (`postgres:16-alpine` persistence across container restart, `nginx` reverse proxy `/api/` routing, and `prod` Flyway migration against live PostgreSQL) could not be executed in this session and must be run via `.\scripts\smoke-test.ps1` once Docker Desktop is started.

---

## 2. Observed Live HTTP Smoke Test Results (Packaged JAR)

| # | Smoke Check | Endpoint / Method | Observed Result |
| :--- | :--- | :--- | :--- |
| 1 | **Startup & Readiness Gate** | `GET /actuator/health/readiness` | Returned `503 OUT_OF_SERVICE` while `ApplicationReadyEvent` was running, then transitioned to **`200 UP`** once `StartupIndexReconciliationRunner` finished (`211 ms`). |
| 2 | **Public Health & Correlation ID** | `GET /api/v1/health` | **`200 OK`** with `X-Request-Id: d41c1be7-9bbb-4a62-b317-015994c8f9b8`. |
| 3 | **Liveness Probe** | `GET /actuator/health/liveness` | **`200 OK`** (`UP`). |
| 4 | **Prometheus Metrics** | `GET /actuator/prometheus` | **`200 OK`**; `searchforge_index_documents` present. |
| 5 | **Lexical Search + Rate Limit** | `GET /api/v1/search?q=searchforge&mode=lexical&page=0&size=5` | **`200 OK`**; `X-RateLimit-Remaining: 38/40`. |
| 6 | **Semantic Search + Rate Limit** | `GET /api/v1/search?q=searchforge&mode=semantic&page=0&size=5` | **`200 OK`**; `X-RateLimit-Remaining: 37/40`. |
| 7 | **Hybrid Search + Rate Limit** | `GET /api/v1/search?q=searchforge&mode=hybrid&page=0&size=5` | **`200 OK`**; `X-RateLimit-Remaining: 36/40`. |
| 8 | **Admin Auth Guard (No Key)** | `GET /api/v1/lifecycle/metrics` | **`401 Unauthorized`**; logged `WARN` with correlation ID `req=6abdb45f-7b6a-4fd7-aa97-dc58960cdd07`. |
| 9 | **Admin Auth Guard (Valid Key)** | `GET /api/v1/lifecycle/metrics` (`X-API-KEY`) | **`200 OK`**. |

---

## 3. Reproducible Smoke Test Scripts

Created two standalone smoke-test scripts for verifying full Docker Compose deployments once Docker Desktop is running:
* PowerShell: [smoke-test.ps1](file:///d:/projects/scripts/smoke-test.ps1)
* Bash: [smoke-test.sh](file:///d:/projects/scripts/smoke-test.sh)

```bash
# Run against live Docker Compose stack:
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
./scripts/smoke-test.sh http://localhost "$SEARCHFORGE_ADMIN_API_KEY"
```
