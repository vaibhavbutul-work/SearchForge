#!/usr/bin/env bash
# ==============================================================================
# SearchForge PostgreSQL Restore & Integrity Verification Script
# Usage: ./scripts/restore-db.sh <path_to_backup.dump>
# ==============================================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <path_to_backup.dump>" >&2
  exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[SearchForge Restore] ERROR: Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

CONTAINER_NAME="${POSTGRES_CONTAINER:-searchforge-postgres-prod}"
DB_NAME="${DB_NAME:-searchforge_db}"
DB_USER="${DB_USERNAME:-searchforge}"

echo "[SearchForge Restore] Restoring '${BACKUP_FILE}' into '${DB_NAME}' on '${CONTAINER_NAME}'..."
docker exec -i "${CONTAINER_NAME}" pg_restore \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl < "${BACKUP_FILE}"

echo "[SearchForge Restore] Verifying restored table row counts..."
docker exec -t "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -c "
SELECT 'crawled_documents' AS table_name, COUNT(*) AS row_count FROM crawled_documents
UNION ALL
SELECT 'indexed_document_metadata', COUNT(*) FROM indexed_document_metadata
UNION ALL
SELECT 'indexed_terms', COUNT(*) FROM indexed_terms
UNION ALL
SELECT 'term_postings', COUNT(*) FROM term_postings
UNION ALL
SELECT 'document_embeddings', COUNT(*) FROM document_embeddings;
"

echo "[SearchForge Restore] Restore complete. Restart backend container to trigger StartupIndexReconciliationRunner."
