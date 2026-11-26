# SearchForge Database Migration, Persistence, Backup & Restore Guide

---

## 1. Schema Management Strategy (Flyway + Hibernate `validate`)

SearchForge replaces Hibernate `ddl-auto: update` in production with **Flyway** (`org.flywaydb:flyway-core`, `flyway-database-postgresql`) paired with `spring.jpa.hibernate.ddl-auto: validate`.

* **Migration Location:** `backend/src/main/resources/db/migration/`
* **Baseline Script:** `V1__init_schema.sql` creates all 5 JPA-backed tables (`crawled_documents`, `indexed_document_metadata`, `indexed_terms`, `term_postings`, `document_embeddings`), foreign keys, unique constraints, and indexes.
* **Production Profile (`application-prod.yml`):**
  * `spring.flyway.enabled: true`
  * `spring.flyway.baseline-on-migrate: true` (`baseline-version: 0`) so pre-existing databases can adopt Flyway without data loss.
  * `spring.flyway.validate-on-migrate: true` verifies migration checksums before startup.
* **Dev & Test Profiles:** `spring.flyway.enabled: false` so H2 `create-drop` / `update` tests remain fast and isolated.

---

## 2. Persistence Topology (Relational + Index + Vector Data)

SearchForge stores all durable state in PostgreSQL (persisted on named Docker volume `postgres_prod_data`):
1. **Relational Documents:** `crawled_documents` (URL, title, clean text, content hash, lifecycle state, optimistic lock).
2. **Lexical Inverted Index:** `indexed_terms` + `term_postings` + `indexed_document_metadata`.
3. **Dense Semantic Vectors:** `document_embeddings` (`BYTEA` 4-bytes/dim float vectors).

**Important:** In-memory inverted indexes and vector stores are **derived caches** rebuilt on startup by `StartupIndexReconciliationRunner` from PostgreSQL. Therefore, backing up PostgreSQL protects 100% of SearchForge's durable corpus, lexical index, and vector embeddings.

---

## 3. Backup & Restore Procedures

### Automated Backup (`scripts/backup-db.sh`)
Uses `pg_dump -Fc` (compressed custom format) inside the PostgreSQL container and prunes dumps older than `retention_days` (default `7`):
```bash
./scripts/backup-db.sh ./backups 7
```
Schedule via cron (e.g., daily at 02:00 UTC):
```cron
0 2 * * * cd /opt/searchforge && ./scripts/backup-db.sh /opt/searchforge/backups 14 >> /var/log/searchforge-backup.log 2>&1
```

### Restore & Integrity Verification (`scripts/restore-db.sh`)
Restores a `.dump` archive with `--clean --if-exists` and queries row counts across all 5 tables:
```bash
./scripts/restore-db.sh ./backups/searchforge_searchforge_db_20261126T020000Z.dump
docker compose -f docker-compose.prod.yml restart backend
```
Restarting `backend` triggers `StartupIndexReconciliationRunner` to reload the restored postings and embeddings into memory.

---

## 4. Migration Rollback Limitations

* **Flyway Community Edition** does not support automatic `undo` (`U*.sql`) migrations.
* Forward-only migrations (`V2__...`, `V3__...`) must be **backward-compatible** during rolling deployments (expand-and-contract pattern: add nullable columns first, backfill, then drop old columns in a later release).
* If a destructive migration fails or corrupts data, **restore from the pre-deployment `pg_dump` backup** before rolling back the application container image.
