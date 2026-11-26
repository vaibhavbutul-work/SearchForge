# SearchForge Phase 13 — Production Readiness Audit Report

**Audit Date:** 2026-11-26  
**Scope:** Full-Stack Repository Inspection (Backend, Frontend, Database, Docker, CI/CD, Security, Observability, Documentation)  
**Audit Objective:** Identify all configuration, security, containerization, persistence, observability, and operational gaps prior to production deployment.

---

## 1. Executive Summary

SearchForge has completed Phases 1 through 12, delivering a custom Java 21 / Spring Boot 4.1.2 search engine with BM25 lexical indexing, ONNX `all-MiniLM-L6-v2` semantic retrieval, Reciprocal Rank Fusion (RRF), crash-resilient lifecycle reconciliation, token-bucket rate limiting, API key RBAC, SSRF protection, and a React 18 / Vite 6 frontend.

However, **SearchForge is not yet packaged or hardened for production deployment**. While individual application components (security filters, startup reconciliation, global exception masking) are strong, the deployment layer has critical gaps: no production Dockerfiles, no `application-prod.yml` profile, Hibernate `ddl-auto: update` without versioned migrations, a hardcoded fallback admin key in `application.yml`, a frontend `apiClient.js` fallback bug for reverse-proxied deployments, and no backup/restore or structured logging pipeline.

---

## 2. Existing Architecture & Current Deployment Capabilities

### 2.1 Backend (`backend/`)
* **Runtime & Build:** Java 21 LTS, Spring Boot `4.1.2`, Maven Wrapper (`mvnw` / `mvnw.cmd`), executable fat-JAR via `spring-boot-maven-plugin`.
* **Persistence:** PostgreSQL 16 (production driver `org.postgresql:postgresql`) and H2 (`com.h2database:h2` for `dev` and `test` profiles). HikariCP connection pool (`maximum-pool-size: 10`, `minimum-idle: 2`, `leak-detection-threshold: 30000`).
* **Search & Lifecycle:** In-memory inverted index and dense vector store pre-warmed and auto-healed on `ApplicationReadyEvent` by `StartupIndexReconciliationRunner`.
* **ONNX Embeddings:** `EmbeddingProperties` resolves `onnxModelPath` -> system property `searchforge.model.path` -> env var `SEARCHFORGE_MODEL_PATH`, with `allowFallbackToDeterministic = true` by default and WordPiece vocab bundled at `classpath:models/vocab.txt`.
* **Security (Phase 11):**
  * `ApiKeyAuthenticationFilter`: Protects `/api/v1/crawler/**`, `/api/v1/lifecycle/**`, and `/api/v1/index/{build,rebuild,warmup}` using constant-time `MessageDigest.isEqual`.
  * `RateLimitingFilter`: Token-bucket rate limiting on `/api/v1/*` (`search`: 60 rpm / burst 20; `admin`: 20 rpm / burst 5).
  * `SecurityStartupValidator`: Detects `KNOWN_DEFAULT_KEYS` and keys `< 16` chars; aborts startup **only** when `prod` profile or `strictSecrets=true` is active.
  * `GlobalExceptionHandler`: Masks SQL errors, internal file paths, and stack traces in `ApiErrorResponse`.
  * SSRF Protection: `SafeDnsResolver` and `IpRangeValidator` enabled via `searchforge.security.crawler.ssrf-protection-enabled: true` and `block-private-ips: true`.
* **Health & Actuator:**
  * Custom endpoint `/api/v1/health` (`HealthController` -> `HealthService`) checks `DataSource.getConnection().isValid(2)`.
  * Spring Boot Actuator exposes `health,info,metrics` with `probes.enabled: true`, `show-details: when_authorized`, and `env`/`configprops` `show-values: never`.

### 2.2 Frontend (`frontend/`)
* **Stack:** React `18.3.1`, React Router `6.28.0`, Vite `6.0.1`, Node.js built-in test runner (`node --test test/*.test.js`).
* **API Transport:** `frontend/src/services/api/apiClient.js` resolves `VITE_API_BASE_URL` at build time.

### 2.3 Infrastructure, CI/CD & Environment
* **Docker:** `docker-compose.yml` provisions **only** `postgres:16-alpine` with named volume `postgres_data` and `pg_isready` healthcheck. No `Dockerfile`s exist for backend or frontend.
* **CI/CD:** `.github/workflows/ci.yml` runs Java 21 backend tests (caching/downloading `all-MiniLM-L6-v2.onnx`) and packages the JAR. `.github/workflows/pages.yml` deploys `docs-site/` to GitHub Pages.
* **Environment Management:** `.gitignore` ignores `.env`, `.env.*` (except `!.env.example`), `*.onnx`, `*.log`. `scripts/load-env.sh` and `scripts/load-env.ps1` assist local env loading.

---

## 3. Identified Issues & Production Gaps

| ID | Severity | Category | Issue & Evidence | Production Impact | Target Task |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUD-01** | **HIGH** | Secrets / Config | `backend/src/main/resources/application.yml` (line 72) and `SecurityProperties.java` (line 20) define a fallback default key `sf-admin-secret-key-prod`. | If deployed without `--spring.profiles.active=prod`, the app logs a warning and runs with a publicly known admin key. | **Task 13.2** |
| **AUD-02** | **HIGH** | Profiles | No `application-prod.yml` exists (`backend/src/main/resources/` only has `application.yml` and `application-dev.yml`). | No isolated production profile to enforce `strict-secrets: true`, `ddl-auto: validate`, and graceful shutdown. | **Task 13.2** |
| **AUD-03** | **HIGH** | Database | `application.yml` (line 27) uses `spring.jpa.hibernate.ddl-auto: update` with no Flyway/Liquibase migrations. | Uncontrolled schema mutations, inability to safely evolve or roll back schemas, and risk of data corruption. | **Task 13.4** |
| **AUD-04** | **HIGH** | Containerization | No `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.dockerignore`, or `docker-compose.prod.yml`. | Application cannot be built or deployed reproducibly as isolated, non-root containers. | **Task 13.3** |
| **AUD-05** | **HIGH** | Frontend / Proxy | `frontend/src/services/api/apiClient.js` (line 26) uses `(envUrl \|\| 'http://localhost:8080')`. | Setting `VITE_API_BASE_URL=""` for same-origin Nginx `/api` proxying evaluates as falsy and breaks production browser calls by pointing to `localhost:8080`. | **Task 13.3** |
| **AUD-06** | **HIGH** | CORS | `WebConfig.java` (lines 17–23) hardcodes `localhost` origins (`5173`, `4173`, `3000`). | Deployed frontend domains cannot call the backend directly unless served via same-origin proxy or CORS is externalized. | **Task 13.2** |
| **AUD-07** | **MEDIUM** | Logging | No `logback-spring.xml` or HTTP request correlation ID (`X-Request-Id` / MDC) filter exists. | Hard to trace individual requests or aggregate container logs in production. | **Task 13.6** |
| **AUD-08** | **MEDIUM** | Health & Shutdown | No custom Actuator `HealthIndicator` for ONNX model availability or index readiness; `server.shutdown: graceful` is not set. | Orchestrators cannot distinguish liveness vs. index/model readiness; `SIGTERM` may drop in-flight requests. | **Task 13.6** |
| **AUD-09** | **MEDIUM** | Backup / DR | No PostgreSQL backup/restore scripts (`pg_dump` / `pg_restore`) or index/vector rebuild runbooks. | Data loss risk during host failure, bad migrations, or accidental deletion. | **Task 13.4 / 13.8** |
| **AUD-10** | **MEDIUM** | CI/CD | `.github/workflows/ci.yml` only tests/packages `backend/`; ignores `frontend/` tests/build and Docker image/Compose validation. | Broken frontend builds or invalid Dockerfiles can merge to `main` undetected. | **Task 13.5** |
| **AUD-11** | **MEDIUM** | Startup Validation | `application.yml` only requires `DB_PASSWORD` at datasource level; `SEARCHFORGE_ADMIN_API_KEY` is not mandatory unless `prod` profile is active, and `allow-fallback-to-deterministic` defaults to `true`. | Silent degradation to deterministic hash embeddings if ONNX model volume mount is misconfigured. | **Task 13.2 / 13.3** |
| **AUD-12** | **LOW** | Env Templates | Root `.env.example` omits `SPRING_PROFILES_ACTIVE`, `SEARCHFORGE_MODEL_PATH`, `CORS_ALLOWED_ORIGINS`, `LOG_LEVEL`, and `BACKEND_PORT`/`FRONTEND_PORT`. | Operators lack a complete template for production deployment variables. | **Task 13.2** |

---

## 4. Security & Resilience Verification Notes (Phase 11 Cross-Check)

* **Positive Controls Already Verified:**
  * `ApiKeyAuthenticationFilter` uses `MessageDigest.isEqual` (timing-attack resistant) and protects all crawler/lifecycle/rebuild routes.
  * `RateLimitingFilter` executes before `ApiKeyAuthenticationFilter` (`Ordered.HIGHEST_PRECEDENCE + 5`), mitigating brute-force and DoS floods.
  * `GlobalExceptionHandler` masks database constraint details and stack traces.
  * `application.yml` restricts Actuator exposure (`health,info,metrics`) and disables `env`/`configprops` value display.
  * `docker-compose.yml` enforces `${DB_PASSWORD:?DB_PASSWORD is required}` for PostgreSQL.
* **Residual Production Risks:**
  * Running without `SPRING_PROFILES_ACTIVE=prod` bypasses `SecurityStartupValidator` fail-fast enforcement.
  * `postgres` port `5432` is published to the host in `docker-compose.yml`; in `docker-compose.prod.yml`, PostgreSQL should remain internal to the Compose network without host port exposure.
  * `RateLimiterRegistry.resolveClientIp` behavior behind Nginx reverse proxy (`X-Forwarded-For`) must be verified during Task 13.3 / 13.7 so all users do not share Nginx's container IP bucket.

---

## 5. Implementation Priorities & Roadmap

1. **Priority 1 — Configuration & Secrets Isolation (Task 13.2):**
   * Remove hardcoded fallback `sf-admin-secret-key-prod` from `application.yml`; create `application-prod.yml` with `strict-secrets: true` and `graceful` shutdown; externalize CORS origins in `WebConfig.java`; complete `.env.example`.
2. **Priority 2 — Multi-Stage Dockerization & Reverse Proxy (Task 13.3):**
   * Author non-root `backend/Dockerfile` (`eclipse-temurin:21-jre-alpine`), `frontend/Dockerfile` (`node:20-alpine` -> `nginx:alpine`), `frontend/nginx.conf` (SPA fallback + `/api/` proxy + forwarded headers), `.dockerignore`, and `docker-compose.prod.yml` (internal DB network, ONNX model volume). Fix `apiClient.js` empty-string base URL handling.
3. **Priority 3 — Database Migrations, Persistence & Backups (Task 13.4):**
   * Evaluate and integrate Flyway baseline + versioned SQL migrations (`ddl-auto: validate` in `prod`), verify persistence, and implement + test `pg_dump` backup/restore scripts.
4. **Priority 4 — Full-Stack CI/CD Expansion (Task 13.5):**
   * Expand `.github/workflows/ci.yml` to run frontend tests/build, Docker image builds, and Compose config validation.
5. **Priority 5 — Observability, Probes & Operational Resilience (Tasks 13.6, 13.7, 13.8, 13.9):**
   * Add structured logging + request correlation IDs, liveness/readiness indicators (DB, ONNX, index), end-to-end Docker Compose smoke tests, failure/rollback testing, and final independent readiness sign-off.
