# SearchForge Rollback, Recovery & Operational Resilience Guide

---

## 1. Data Durability & Retry Boundaries

| Data / State | Storage | Survives Container Restart? | Recovery / Retry Behavior |
| :--- | :--- | :--- | :--- |
| **Crawled Documents & Lifecycle** | PostgreSQL (`crawled_documents`) | Yes (`postgres_prod_data` volume) | Authoritative source of truth. |
| **Lexical Inverted Index** | PostgreSQL (`indexed_terms`, `term_postings`) + RAM cache | Yes | Re-warmed into RAM on boot by `StartupIndexReconciliationRunner`. |
| **Dense Vector Embeddings** | PostgreSQL (`document_embeddings`) + RAM `VectorStorage` | Yes | Re-warmed into RAM on boot; missing vectors auto-regenerated. |
| **In-Flight HTTP Requests** | Tomcat worker threads | No (ephemeral) | `server.shutdown: graceful` drains up to `30s`; clients can safely retry `GET /api/v1/search`. |
| **Rate-Limit Buckets** | In-memory `RateLimiterRegistry` | No (ephemeral) | Resets cleanly on backend restart. |

**Safe-to-Retry Operations:** All `GET` search/health queries, `POST /api/v1/lifecycle/integrity/repair`, `POST /api/v1/lifecycle/retry`, and `POST /api/v1/index/rebuild` (idempotent).

---

## 2. Failure & Recovery Scenarios

### Scenario 1 & 2: Backend or Frontend Container Restart
* **Behavior:** `restart: unless-stopped` restarts the container automatically. For `backend`, `StartupIndexReconciliationRunner` reloads postings/vectors from PostgreSQL and marks `/actuator/health/readiness` `UP` only after reconciliation completes.
* **Note:** Single-node Docker Compose experiences brief downtime (a few seconds) during container recreation; this is **not** a zero-downtime blue/green cluster.

### Scenario 3 & 4: PostgreSQL Restart or Unavailable at Backend Startup
* **Behavior:** HikariCP `initialization-fail-timeout: 5000` causes `backend` to fail fast rather than hanging indefinitely. Once `postgres` passes `pg_isready`, Docker `depends_on: condition: service_healthy` / `restart: unless-stopped` brings `backend` back cleanly.

### Scenario 5 & 6: Invalid Production Configuration / Failed Startup
* **Behavior:** Missing `DB_PASSWORD` or weak/default `SEARCHFORGE_ADMIN_API_KEY` aborts startup (`SecurityStartupValidator` throws `IllegalStateException`). Fix `.env` and run `docker compose -f docker-compose.prod.yml --env-file .env up -d`.

### Scenario 7 & 8: Database Backup Restoration & Volume Recovery
1. Restore PostgreSQL from `.dump`:
   ```bash
   ./scripts/restore-db.sh ./backups/searchforge_searchforge_db_<timestamp>.dump
   ```
2. Restart backend to re-warm RAM indexes and vectors:
   ```bash
   docker compose -f docker-compose.prod.yml restart backend
   ```

### Scenario 9: Failed Database Migration Handling
* Flyway executes each migration in a transaction (where supported by PostgreSQL DDL) and halts startup on `FlywayException`.
* **Rollback Limitation:** Flyway Community does not auto-undo failed or destructive migrations. Always take a `pg_dump` backup (`./scripts/backup-db.sh`) before applying new migrations, and use backward-compatible expand-and-contract SQL changes.

### Scenario 10: Application Image Rollback
Tag releases immutably (`searchforge-backend:v1.2.0`) before upgrading:
```bash
docker tag searchforge-backend:latest searchforge-backend:rollback
# To roll back:
docker tag searchforge-backend:rollback searchforge-backend:latest
docker compose -f docker-compose.prod.yml --env-file .env up -d --no-build backend
```
**Critical Warning:** Rolling back the application image does **not** roll back PostgreSQL schema or data changes. If the newer release ran destructive SQL migrations, restore the pre-upgrade database backup first.

---

## 3. Verified vs. Blocked Scenarios (2026-11-26)

* **Verified via Automated Tests (`OperationalResilienceTest`):**
  * Backend fast-fail when PostgreSQL is unreachable (`localhost:59999`).
  * Startup abort on weak/invalid production `SEARCHFORGE_ADMIN_API_KEY`.
  * Flyway fail-fast on invalid SQL migration.
  * `StartupIndexReconciliationRunner` auto-healing missing lexical postings and vector embeddings for an `ACTIVE` document.
* **Blocked Pending Host Docker Desktop Startup:** Live multi-container `docker compose restart` and `pg_dump`/`pg_restore` against `postgres:16-alpine` were not executed because Docker Desktop daemon is stopped on the host.
