# SearchForge — Phase 8: Index Lifecycle, Data Consistency & Recovery

## 1. Executive Summary

Phase 8 elevates SearchForge from a static hybrid search engine into a production-grade, lifecycle-managed document retrieval system. In enterprise search platforms, search quality degrades catastrophically when documents undergo content changes, re-crawls, deletions, or partial transaction failures. 

The fundamental architectural principle implemented in Phase 8 is:
> **A document must never silently exist in an inconsistent state across SearchForge's storage, lexical indexes, positional indexes, document statistics, and semantic embeddings.**

Phase 8 introduces formal document lifecycle state machines, cryptographic content change detection, atomic incremental single-document index updates, safe residual-free deletions, a staged two-phase transactional indexing coordinator (keeping ONNX model inference outside database locks), a non-destructive index reconciliation auditor, automated self-healing repair engines, and exponential backoff retry recovery.

---

## 2. Document Lifecycle State Machine

Every document in SearchForge is modeled by `CrawledDocument` and governed by the `DocumentLifecycleStateMachine`. Documents transition through discrete, strictly validated states:

```
                  +---------------+
                  |  DISCOVERED   |
                  +-------+-------+
                          |
                          v
                  +---------------+
                  |    FETCHED    |
                  +-------+-------+
                          |
                          v
                  +---------------+
                  |  PROCESSING   |
                  +-------+-------+
                          |
                          v
                  +---------------+
                  |    INDEXING   |
                  +-------+-------+
                          |
                          v
                  +---------------+
                  |   EMBEDDING   |
                  +-------+-------+
                          |
                          v
+-------------+   +---------------+
|   RETRY     |<--+    ACTIVE     +<--+ (Idempotent / Unchanged)
+------+------+   +-------+-------+   |
       |                  |           |
       | (Max Retries)    v           |
       v          +---------------+   |
+-------------+   |   DELETING    |   |
|   FAILED    |   +-------+-------+   |
+-------------+           |           |
                          v           |
                  +---------------+   |
                  |    DELETED    |---+
                  +---------------+
```

### Lifecycle States

| Status | Searchable | Terminal | Description |
| :--- | :---: | :---: | :--- |
| `DISCOVERED` | No | No | URL identified by the crawler or seed queue, pending HTTP fetch. |
| `FETCHED` | No | No | Raw HTML downloaded and stored in the database. |
| `PROCESSING` | No | No | Text extracted, cleaned, and language processing initiated. |
| `INDEXING` | No | No | Lexical tokenization, stemming, stopword removal, and posting generation. |
| `EMBEDDING` | No | No | Dense vector embedding generated via ONNX Runtime neural transformer. |
| `ACTIVE` | **Yes** | No | Fully synchronized, indexed in lexical and vector stores, and visible to search. |
| `RETRY` | No | No | Transient indexing or embedding failure encountered; scheduled for backoff retry. |
| `FAILED` | No | No | Permanent error encountered or maximum retry limit exceeded (dead-letter). |
| `DELETING` | No | No | Deletion in progress; excluded from search while cleanup executes. |
| `DELETED` | No | **Yes** | Document marked deleted; completely purged from all indexes. |

---

## 3. Change Detection & Versioning Architecture

To avoid expensive full-corpus re-indexing and wasted CPU embedding inference, the `DocumentChangeDetector` evaluates inbound crawled documents against existing entities:

1. **Canonical URL Normalization:** Strips trailing slashes, fragments, and standardizes casing.
2. **Cryptographic Content Hashing:** SHA-256 digest of clean parsed text.
3. **Change Classification:**
   - `NEW_DOCUMENT`: URL not present in database. Requires full indexing.
   - `UNCHANGED`: URL exists, content hash matches active entity. **Indexing skipped completely** (Zero CPU/DB cost).
   - `MODIFIED`: URL exists, content hash differs. Increments `version`, replaces postings and embeddings.
   - `DUPLICATE_CONTENT_DIFFERENT_URL`: Same hash discovered on different URL (mirror). Tracked to prevent corpus dilution.

---

## 4. Incremental Index Update Engine

The `IncrementalIndexUpdateEngine` provides atomic, single-document index replacement without requiring corpus-wide rebuilds:

- **Lexical Postings Purge:** Removes prior postings for `documentId` across all vocabulary terms.
- **Term Statistics Adjustment:** Decrements Document Frequency ($df$) and Corpus Term Frequency ($ctf$) for removed terms. If $df$ reaches 0, dictionary metadata is cleanly pruned.
- **New Postings Insertion:** Generates positional postings for new tokens and updates $df$/$ctf$.
- **BM25 Corpus Metrics Maintenance:** Updates document length ($dl$) in metadata store, recalibrates total tokens and average document length ($avgdl$) dynamically.
- **Dense Embedding Refresh:** Overwrites stored dense vector in `DocumentEmbeddingEntity` and updates `VectorStorage`.

---

## 5. Safe Document Deletion & Residual Cleanup

Document removal via `DocumentDeletionService` guarantees that no orphaned data lingers:

1. **Soft Lock:** Transitions document to `DELETING` status, instantly removing it from search visibility.
2. **Lexical Purge:** Removes all entries from `InvertedIndex`, `PositionalIndex`, and `term_postings` table.
3. **Metadata Purge:** Deletes document length entry from `indexed_document_metadata` and recalculates $avgdl$.
4. **Vector Purge:** Removes dense vector from in-memory `VectorStorage` and deletes record from `document_embeddings` table.
5. **Final Transition:** Sets status to `DELETED` (soft delete) or deletes row from `crawled_documents` (hard delete).

---

## 6. Staged Two-Phase Transactional Indexing Coordinator

Holding relational database transaction locks during transformer embedding inference risks HikariCP connection pool exhaustion and database lock timeouts. 

SearchForge implements a **Staged Two-Phase Transactional Model** via `TransactionalIndexingCoordinator`:

```
================================================================================
STAGE 1: Pre-Transaction Computation (CPU Bound, 0 Database Connection Locks)
--------------------------------------------------------------------------------
1. Linguistic Tokenization & Normalization (Linguistic Pipeline)
2. Lead Text Representation Construction (Title + Lead Content)
3. ONNX Transformer Embedding Inference (all-MiniLM-L6-v2)
4. Binary Vector Serialization (IEEE 754 Float-to-Byte conversion)
================================================================================
                                       |
                                       v
================================================================================
STAGE 2: Atomic Relational Commit (Isolated READ_COMMITTED Database Transaction)
--------------------------------------------------------------------------------
1. Increment Document Version (v -> v + 1)
2. Update Content Hash and Timestamps
3. Replace Lexical Term Postings in Relational Store
4. Persist DocumentEmbeddingEntity (Vector, Hash, Version, Model Name)
5. Transition Lifecycle Status to ACTIVE
================================================================================
                                       |
                                       v
================================================================================
STAGE 3: In-Memory Synchronization (Post-Commit)
--------------------------------------------------------------------------------
1. Synchronize In-Memory InvertedIndex Postings
2. Store Dense Vector in In-Memory VectorStorage
================================================================================
```

### Rollback Safety & Version Preservation
If Stage 1 fails (e.g. ONNX out of memory), no database transaction is opened. If Stage 2 fails (e.g. SQL connection drop), the database transaction rolls back cleanly, and the document's previous active version is preserved without leaving residual half-updated records.

---

## 7. Document Version & Dense Embedding Consistency

Dense vectors must always accurately represent the current document text. The `DocumentEmbeddingConsistencyValidator` enforces strict consistency criteria:

| Status | Definition | Corrective Action |
| :--- | :--- | :--- |
| `CURRENT` | Vector exists, matches active document version, content hash, model name, and dimensions. | None (healthy). |
| `MISSING` | Active document has no embedding representation in database or vector storage. | Generate and store embedding. |
| `OUTDATED` | Stored embedding's content hash or version does not match active document entity. | Re-embed document with new content. |
| `INCOMPATIBLE` | Model name or dimensions differ from currently active configured embedding model. | Re-embed document using current model. |
| `ORPHANED` | Vector exists for a non-existent or deleted document. | Purge residual embedding. |
| `DESYNCHRONIZED` | Vector exists in database but missing from in-memory cache, or vice-versa. | Synchronize cache from database. |

---

## 8. Index Reconciliation & Non-Destructive Integrity Auditor

The `IndexIntegrityChecker` conducts holistic cross-layer consistency audits across:
1. `crawled_documents` relational table.
2. In-memory `InvertedIndex` and `PositionalIndex`.
3. `indexed_document_metadata` and corpus metrics ($N$, $avgdl$).
4. `document_embeddings` relational table and in-memory `VectorStorage`.

The auditor runs in `readOnly = true` mode and outputs an immutable `IndexIntegrityReport` detailing missing lexical postings, orphaned postings, corpus statistics drift, and embedding discrepancies.

---

## 9. Automated Self-Healing Repair Mechanism

The `IndexRepairService` repairs any defects detected by the integrity audit:

- `RepairMode.DRY_RUN`: Identifies and logs planned repair actions without mutating any state.
- `RepairMode.REPAIR`: Executes corrective repair procedures:
  - Re-indexes missing lexical documents from raw clean text.
  - Purges orphaned lexical postings and metadata.
  - Regenerates missing or outdated embeddings using active model.
  - Purges residual orphaned embeddings from deleted documents.
  - Re-warms in-memory index to synchronize BM25 corpus statistics.
  - Re-audits index to verify 100% health post-repair.

---

## 10. Retry & Recovery System

The `DocumentRecoveryService` manages transient failure recovery:
- **Failure Classification:**
  - `TRANSIENT`: Network timeouts, connection resets, deadlocks, temporary OOM. Eligible for retry.
  - `PERMANENT`: Malformed encoding, constraint violations, unsupported formats. Dead-lettered immediately.
- **Exponential Backoff:** $\text{delay} = \min(\text{maxBackoff}, \text{baseBackoff} \times 2^{\text{retryCount}})$.
- **Max Retries:** 3 attempts. Upon exceeding, document transitions to `FAILED`.
- **Version Preservation:** Failed update attempts on currently `ACTIVE` documents do not take down search availability; the previous version remains active and searchable.

---

## 11. REST Administration API Specification

All administrative lifecycle capabilities are exposed under `/api/v1/lifecycle`:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/lifecycle/status/{docId}` | Returns lifecycle state, version, and embedding consistency. |
| `POST` | `/api/v1/lifecycle/reindex/{docId}` | Manually triggers staged reindexing and embedding update. |
| `DELETE` | `/api/v1/lifecycle/{docId}?hard=false` | Deletes document and purges lexical and vector representations. |
| `GET` | `/api/v1/lifecycle/integrity/check` | Runs non-destructive cross-layer reconciliation audit. |
| `POST` | `/api/v1/lifecycle/integrity/repair?mode=REPAIR` | Runs automated repair (`DRY_RUN` or `REPAIR`). |
| `POST` | `/api/v1/lifecycle/retry?documentId=...` | Retries failed or retry-eligible documents. |
| `GET` | `/api/v1/lifecycle/metrics` | Retrieves operational telemetry metrics snapshot. |

---

## 12. Verification & Regression Metrics

- **Backend Test Suite:** **484 passing tests** (0 failures, 0 errors, 0 skipped).
- **Frontend Test Suite:** **13 passing tests** (0 failures).
- **Frontend Production Build:** Vite build successful in 772ms.
- **Total Test Suite:** **497 tests passing across the system**.
