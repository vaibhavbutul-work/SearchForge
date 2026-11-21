# SearchForge — Phase 8: Index Lifecycle, Data Consistency & Recovery

Please refer to the comprehensive Phase 8 design and specification in [`docs/PHASE_8_INDEX_LIFECYCLE.md`](../PHASE_8_INDEX_LIFECYCLE.md) and [`docs/PHASE_8_ARCHITECTURE.md`](../PHASE_8_ARCHITECTURE.md).

## Highlights
- **10-State Document Lifecycle State Machine:** `DISCOVERED`, `FETCHED`, `PROCESSING`, `INDEXING`, `EMBEDDING`, `ACTIVE`, `RETRY`, `FAILED`, `DELETING`, `DELETED`.
- **Change Detection & Idempotency:** SHA-256 fingerprinting prevents wasteful re-indexing of unchanged web content.
- **Staged Two-Phase Transactional Model:** Neural ONNX embedding inference pre-computed outside database transaction locks.
- **Single-Document Incremental Updates:** Replaces postings and recalibrates $df$/$ctf$ and $avgdl$ without full corpus rebuilds.
- **Safe Document Deletion:** Immediate exclusion from search, residual-free purging of lexical postings and vectors.
- **Cross-Layer Reconciliation Auditor:** Non-destructive integrity checks detecting orphaned or missing postings and drifted metrics.
- **Automated Self-Healing Repair:** `DRY_RUN` and `REPAIR` engines restoring 100% index health.
- **Retry & Recovery:** Exponential backoff, version preservation, and dead-letter queue.
- **Full Test Suite:** 497 automated tests (484 Backend + 13 Frontend) passing at 100%.
