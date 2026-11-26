# SearchForge Phase 14 — Production Deployment & Operational Validation Report

**Execution Date:** 2026-11-26  
**Stack Tested:** `docker-compose.prod.yml` (`postgres:16-alpine`, `searchforge-backend:latest`, `searchforge-frontend:latest`)

---

## 1. Observed Operational Scenarios (`A–G`)

| Scenario | Test Action | Observed Outcome | Status |
| :--- | :--- | :--- | :--- |
| **A. Clean Deployment Readiness** | `docker compose -f docker-compose.prod.yml ps` | All 3 containers `Up (healthy)`; `backend` (`uid=1001`) and `frontend` (`uid=101`) non-root; `postgres` & `backend` internal-only. | **PASS** |
| **B. Container Restart** | `docker compose restart backend frontend` | Both restarted cleanly; `/actuator/health/readiness` returned `200 UP`. | **PASS** |
| **C. Database Restart** | `docker compose restart postgres` | PostgreSQL restarted cleanly; backend reconnected and `/api/v1/health` returned `200 UP`. | **PASS** |
| **D. Persistent Storage** | Inserted `https://example.com/persist-14-7` into `crawled_documents`, restarted `postgres`, `backend`, and `frontend`, queried row count, then deleted test row. | `SELECT COUNT(*)` returned **`1`** after restart; `DELETE 1` cleaned up test data. | **PASS** |
| **E. Configuration Failure** | `OperationalResilienceTest` + `ProductionConfigurationProfileTest` (`prod` profile with missing `DB_PASSWORD` / weak `SEARCHFORGE_ADMIN_API_KEY`). | Spring Boot aborted startup cleanly with `IllegalStateException` / placeholder failure. *(Note: `docker compose config` auto-reads `.env` in CWD when present.)* | **PASS** |
| **F. Health Checks** | Queried `/actuator/health/liveness` (inside `backend`) and `/actuator/health/readiness` (via Nginx). | Both returned **`200 {"status":"UP"}`**. | **PASS** |
| **G. Frontend Connectivity** | Ran `.\scripts\smoke-test.ps1 -BaseUrl "http://localhost" -AdminKey $env:SEARCHFORGE_ADMIN_API_KEY`. | **7 / 7 passed** (`GET /`, health, readiness, rate limits, lexical/semantic/hybrid search, `401`/`200` admin auth). | **PASS** |
