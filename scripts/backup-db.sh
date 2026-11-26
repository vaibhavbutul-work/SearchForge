#!/usr/bin/env bash
# ==============================================================================
# SearchForge PostgreSQL Backup Script
# Creates compressed custom-format dumps (.dump) and enforces retention policy.
# Usage: ./scripts/backup-db.sh [backup_dir] [retention_days]
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${1:-./backups}"
RETENTION_DAYS="${2:-7}"
CONTAINER_NAME="${POSTGRES_CONTAINER:-searchforge-postgres-prod}"
DB_NAME="${DB_NAME:-searchforge_db}"
DB_USER="${DB_USERNAME:-searchforge}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${BACKUP_DIR}/searchforge_${DB_NAME}_${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"

echo "[SearchForge Backup] Starting pg_dump of '${DB_NAME}' from container '${CONTAINER_NAME}'..."
docker exec -t "${CONTAINER_NAME}" pg_dump \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -Fc \
  --no-owner \
  --no-acl > "${BACKUP_FILE}"

if [ ! -s "${BACKUP_FILE}" ]; then
  echo "[SearchForge Backup] ERROR: Backup file is empty: ${BACKUP_FILE}" >&2
  rm -f "${BACKUP_FILE}"
  exit 1
fi

SIZE_BYTES=$(wc -c < "${BACKUP_FILE}")
echo "[SearchForge Backup] Backup created successfully: ${BACKUP_FILE} (${SIZE_BYTES} bytes)"

echo "[SearchForge Backup] Pruning backups older than ${RETENTION_DAYS} days in ${BACKUP_DIR}..."
find "${BACKUP_DIR}" -type f -name "searchforge_*.dump" -mtime "+${RETENTION_DAYS}" -delete
echo "[SearchForge Backup] Done."
