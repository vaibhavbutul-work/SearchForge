# SearchForge Logging, Health Checks & Observability Guide

---

## 1. Structured Logging & Request Correlation

* **Logback Configuration:** [logback-spring.xml](file:///d:/projects/backend/src/main/resources/logback-spring.xml) formats all stdout logs for container collection (`docker logs`) with ISO-8601 timestamps, severity, application name, `[req=%X{requestId:-none}]`, thread, logger, and exception traces.
* **Production Log Levels:** `prod` profile sets `root: WARN` and `com.searchforge: INFO` (configurable via `LOG_LEVEL_ROOT` and `LOG_LEVEL_APP`). Secrets are masked via `SecurityProperties.maskSecret()` and `GlobalExceptionHandler`.
* **Correlation ID (`RequestCorrelationFilter`):**
  * Registered at `Ordered.HIGHEST_PRECEDENCE + 1` (executes before `RateLimitingFilter` and `ApiKeyAuthenticationFilter`).
  * Validates incoming `X-Request-Id` against `^[a-zA-Z0-9_-]{1,64}$` to block CRLF log-forging attacks; generates a UUID if missing or invalid, binds it to SLF4J `MDC`, and echoes `X-Request-Id` on the HTTP response.

---

## 2. Liveness vs. Readiness Health Probes

| Endpoint | Components Included | Purpose |
| :--- | :--- | :--- |
| **`/actuator/health/liveness`** | `livenessState` | Verifies the JVM/Tomcat process is alive without failing on optional/external dependencies. |
| **`/actuator/health/readiness`** | `db`, `readinessState`, `searchIndex` | Verifies PostgreSQL connectivity and in-memory `SearchIndexHealthIndicator` readiness before receiving traffic. |
| **`/api/v1/health`** | `HealthService` (`DataSource.isValid(2)`) | Public lightweight REST health summary. |

`EmbeddingModelHealthIndicator` (`embeddingModel`) reports active model mode (`onnx` vs `deterministic-fallback`), dimensions, and enabled state. When deterministic fallback is permitted, it reports `UP` so fallback mode does not kill container liveness.

---

## 3. Metrics & Prometheus Integration

* **Actuator & Micrometer:** Exposes `health`, `info`, `metrics`, and `prometheus` (`/actuator/prometheus`).
* **Custom Metrics (`SearchForgeMetricsBinder`):** Reuses `IndexingEngine.getStats()`, `VectorStorage.size()`, and `LifecycleMetricsCollector` to expose:
  * `searchforge_index_documents`
  * `searchforge_index_terms`
  * `searchforge_vector_embeddings`
  * `searchforge_lifecycle_documents_failed`
  * `searchforge_lifecycle_documents_repaired`
  * Alongside standard `http_server_requests_seconds_*`, `hikaricp_connections_*`, and `jvm_memory_*`.
* **Endpoint Security:** `frontend/nginx.conf` proxies **only** `/api/` and `/actuator/health` to `backend:8080`. `/actuator/prometheus` and `/actuator/metrics` remain internal to `searchforge-net` for Prometheus scraping ([monitoring/prometheus.yml](file:///d:/projects/monitoring/prometheus.yml)).
