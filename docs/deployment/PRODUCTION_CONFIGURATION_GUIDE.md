# SearchForge Production Configuration Guide

This guide documents Spring Boot profile separation, secret externalization, fail-fast validation, and the complete environment variable matrix for SearchForge.

---

## 1. Spring Boot Profiles

| Profile | Config File | Purpose | Database | Security Enforcement |
| :--- | :--- | :--- | :--- | :--- |
| **Base** | `backend/src/main/resources/application.yml` | Shared defaults across all environments | PostgreSQL (via `DB_*` env vars) | Enabled; no hardcoded fallback admin key |
| **Development** | `backend/src/main/resources/application-dev.yml` | Local developer inner loop | H2 in-memory (`MODE=PostgreSQL`) | Enabled with dev key (`sf-dev-admin-key-2026`); warnings only |
| **Test** | `backend/src/test/resources/application.yml` | Automated JUnit 5 & WireMock tests | H2 `create-drop` | Disabled by default for unit/slice isolation |
| **Production** | `backend/src/main/resources/application-prod.yml` | Hardened production runtime | PostgreSQL 16 (`ddl-auto: validate`) | `strict-secrets: true`; aborts startup on missing/weak secrets |

Activate production mode via:
```bash
export SPRING_PROFILES_ACTIVE=prod
```

---

## 2. Fail-Fast Startup Guarantees

1. **Missing Database Credentials:** `application-prod.yml` defines `spring.datasource.username: ${DB_USERNAME}` and `spring.datasource.password: ${DB_PASSWORD}` with **no default values**. Spring Boot aborts startup immediately if either is unset.
2. **Weak or Default API Keys:** `SecurityStartupValidator` runs on `ApplicationReadyEvent`. Under the `prod` profile (`strict-secrets: true`), it rejects:
   * Empty or blank `SEARCHFORGE_ADMIN_API_KEY`
   * Keys shorter than `16` characters
   * Any known sample/default key (`sf-admin-secret-key-prod`, `admin`, `password`, etc.)
3. **Secret Masking:** `SecurityProperties.toString()` masks API keys using `SecurityProperties.maskSecret(...)` (e.g., `pr****21`), and Actuator `env` and `configprops` set `show-values: never`.
4. **Error Masking:** `application-prod.yml` disables `server.error.include-message`, `include-binding-errors`, `include-stacktrace`, and `include-exception`, complementing `GlobalExceptionHandler`.

---

## 3. Environment Variable Matrix

### Mandatory in Production (`SPRING_PROFILES_ACTIVE=prod`)

| Variable | Description | Example / Notes |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `DB_USERNAME` | PostgreSQL username | `searchforge` |
| `DB_PASSWORD` | PostgreSQL password (no default in prod) | Strong random password |
| `SEARCHFORGE_ADMIN_API_KEY` | Admin API key (`X-API-KEY`, $\ge 16$ chars) | Generated 32+ char random secret |

### Optional / Configurable Variables

| Variable | Default (`prod`) | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | HTTP port for Spring Boot backend |
| `DB_HOST` | `postgres` | PostgreSQL hostname (use `localhost` outside Docker) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `searchforge_db` | PostgreSQL database name |
| `DB_URL` | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` | Full JDBC URL override |
| `DB_POOL_MAX_SIZE` | `15` | HikariCP maximum pool size |
| `DB_POOL_MIN_IDLE` | `3` | HikariCP minimum idle connections |
| `JPA_DDL_AUTO` | `validate` | Hibernate DDL mode (`validate` recommended with Flyway; `update` only for initial pre-Flyway bootstrap) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost` | Comma-separated allowed browser origins (`WebConfig`) |
| `SEMANTIC_EMBEDDING_MODEL_TYPE` | `onnx` | `onnx` for transformer embeddings or `deterministic` |
| `SEARCHFORGE_MODEL_PATH` | `/app/models/all-MiniLM-L6-v2.onnx` | Filesystem path to ONNX model |
| `SEMANTIC_EMBEDDING_ALLOW_FALLBACK` | `false` | Whether to fall back to deterministic embeddings if ONNX is missing |
| `RATE_LIMIT_SEARCH_RPM` | `60` | Search tier requests/minute per client IP |
| `RATE_LIMIT_SEARCH_BURST` | `20` | Search tier burst capacity |
| `RATE_LIMIT_ADMIN_RPM` | `20` | Admin tier requests/minute per client IP |
| `RATE_LIMIT_ADMIN_BURST` | `5` | Admin tier burst capacity |
| `LOG_LEVEL_ROOT` | `WARN` | Root logger level |
| `LOG_LEVEL_APP` | `INFO` | `com.searchforge` package logger level |
| `FRONTEND_PORT` | `80` | Host port mapped to Nginx frontend container |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend API base URL (empty string `""` for same-origin Nginx proxy) |

---

## 4. Local Setup Workflow

```bash
cp .env.example .env
# Edit .env with strong DB_PASSWORD and SEARCHFORGE_ADMIN_API_KEY
source ./scripts/load-env.sh   # or .\scripts\load-env.ps1 on Windows
```

`.env` is explicitly excluded from Git in `.gitignore` and must never be committed.
