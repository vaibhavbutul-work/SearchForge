# SearchForge: Phase 8 Masterclass Guide
## Index Lifecycle, Data Consistency & Recovery

This masterclass guide documents the architectural decisions, transaction models, consistency invariants, self-healing algorithms, and interview-ready concepts for Phase 8 of SearchForge.

---

## 1. Architectural Blueprint & Foundational Invariant

In industrial search engines, document indexing is not a one-time static event. In live web environments, documents are continuously discovered, updated, moved, or deleted. 

Without rigorous lifecycle governance, search engines experience silent data corruption:
- Residual postings matching terms that were removed from the document.
- Dense embedding vectors representing old versions of text (semantic drift).
- Drifted BM25 corpus statistics ($N$, $avgdl$) degrading ranking scores across the entire collection.
- Orphaned vectors for deleted documents appearing as ghost search results.

Phase 8 guarantees the fundamental invariant:
> **A document must never silently exist in an inconsistent state across SearchForge's storage, lexical indexes, positional indexes, document statistics, and semantic embeddings.**

```
                                [Inbound Crawled Document]
                                            │
                                            ▼
                              [ DocumentChangeDetector ]
                              - RFC 3986 URL Normalizer
                              - SHA-256 Text Fingerprint
                                            │
                     ┌──────────────────────┴──────────────────────┐
                     │                                             │
             [Content Unchanged]                         [New / Modified Text]
                     │                                             │
                     ▼                                             ▼
             [Idempotent Skip]                        [STAGE 1: Pre-Transaction Compute]
             - Return UNCHANGED                       - WordPiece Tokenization
             - Zero CPU / DB Cost                     - ONNX Dense Embedding Inference
                                                        (Zero Database Transaction Locks Held)
                                                                   │
                                                                   ▼
                                                      [STAGE 2: Atomic Relational Commit]
                                                      - Isolated @Transactional Boundary
                                                      - Increment Version (v -> v + 1)
                                                      - Replace Postings & Update df/ctf
                                                      - Persist DocumentEmbeddingEntity
                                                      - Transition Status to ACTIVE
                                                                   │
                                                                   ▼
                                                      [STAGE 3: Memory Synchronization]
                                                      - Synchronize InvertedIndex in RAM
                                                      - Store Vector in VectorStorage
```

---

## 2. The 10-State Document Lifecycle State Machine

Every document in SearchForge is modeled by `CrawledDocument` and governed by `DocumentLifecycleStateMachine`.

### Discrete State Matrix

| State | Searchable | Terminal | Valid Outbound Transitions | Description |
| :--- | :---: | :---: | :--- | :--- |
| `DISCOVERED` | No | No | `FETCHED`, `PROCESSING`, `FAILED`, `DELETED` | Seed URL or discovered hyperlink queued for crawling. |
| `FETCHED` | No | No | `PROCESSING`, `FAILED`, `DELETED` | Raw HTML downloaded and persisted in database. |
| `PROCESSING` | No | No | `INDEXING`, `EMBEDDING`, `ACTIVE`, `FAILED`, `DELETING`, `DELETED` | Text extracted, cleaned, and normalized. |
| `INDEXING` | No | No | `EMBEDDING`, `ACTIVE`, `FAILED`, `DELETING`, `DELETED` | Lexical tokenization, stemming, and postings generation. |
| `EMBEDDING` | No | No | `ACTIVE`, `FAILED`, `DELETING`, `DELETED` | Dense semantic vector generated via ONNX transformer. |
| `ACTIVE` | **Yes** | No | `PROCESSING`, `INDEXING`, `EMBEDDING`, `FAILED`, `RETRY`, `DELETING`, `DELETED` | Document is live, fully synchronized, and visible to search. |
| `RETRY` | No | No | `PROCESSING`, `INDEXING`, `EMBEDDING`, `ACTIVE`, `FAILED`, `DELETED` | Transient failure encountered; backoff retry scheduled. |
| `FAILED` | No | No | `PROCESSING`, `INDEXING`, `EMBEDDING`, `ACTIVE`, `RETRY`, `DELETED` | Permanent failure or max retries exceeded (dead-letter). |
| `DELETING` | No | No | `DELETED`, `FAILED` | Soft-lock in progress; excluded from search while cleanup executes. |
| `DELETED` | No | **Yes** | `PROCESSING`, `INDEXING`, `EMBEDDING` (if re-discovered) | Marked deleted; completely purged from all indexes. |

---

## 3. Cryptographic Change Detection & Idempotency

To prevent wasted CPU and neural transformer inference during recurring crawls, `DocumentChangeDetector` evaluates inbound content:

1. **Canonical URL Normalization:** RFC 3986 compliant normalization strips trailing slashes, fragments, and standardizes casing.
2. **SHA-256 Digest:** Evaluates `cleanText` SHA-256 digest against stored `contentHash`.
3. **Change Classification:**
   - `NEW_DOCUMENT`: New URL. Enters staged indexing pipeline.
   - `UNCHANGED`: Content hash matches existing active document. **Skips tokenization and ONNX inference entirely.**
   - `MODIFIED`: Content hash differs. Increments `version` and executes atomic incremental replacement.
   - `DUPLICATE_CONTENT_DIFFERENT_URL`: Same content found on alternative URL (mirror detection).

---

## 4. Staged Two-Phase Transactional Model

Holding database transaction locks while executing ONNX neural embeddings risks HikariCP connection pool exhaustion and database lock timeouts.

SearchForge implements a **Staged Two-Phase Execution Model**:

- **Stage 1 (Pre-Transaction Compute — CPU Bound):**
  - Linguistic tokenization, stop-word removal, and Porter stemming.
  - Lead representation construction (Title + Lead Content).
  - Neural transformer inference via ONNX Runtime (`all-MiniLM-L6-v2`).
  - Binary IEEE 754 vector serialization.
  - *No database connection or transaction lock is held during this stage.*
- **Stage 2 (Atomic Relational Commit — Database Transaction):**
  - Enters `@Transactional(isolation = Isolation.READ_COMMITTED)`.
  - Increments document version ($v \rightarrow v + 1$) and updates `content_hash`.
  - Atomically replaces term postings in `term_postings` table.
  - Persists `DocumentEmbeddingEntity` with model metadata, dimensions, and version.
  - Transitions lifecycle state to `ACTIVE`.
- **Stage 3 (Post-Commit Memory Sync):**
  - Synchronizes in-memory `InvertedIndex` postings.
  - Stores dense vector in `VectorStorage`.

---

## 5. Safe Document Deletion & Residual Cleanup

When a document is deleted via `DocumentDeletionService`:
1. **Immediate Search Exclusion:** Transitions document status to `DELETING`.
2. **Lexical Postings Purge:** Removes all entries for `documentId` from `InvertedIndex`, `PositionalIndex`, and relational `term_postings`.
3. **Corpus Statistics Maintenance:** Decrements Document Frequency ($df$) and Corpus Term Frequency ($ctf$). Prunes empty vocabulary terms. Recalculates average document length ($avgdl$) and total corpus tokens.
4. **Vector Storage Purge:** Deletes vector from memory `VectorStorage` and removes record from `document_embeddings` table.
5. **State Transition:** Marks document `DELETED` (soft delete) or purges row from `crawled_documents` (hard delete).

---

## 6. Cross-Layer Reconciliation & Self-Healing Repair

SearchForge includes automated auditing and repair engines:

### IndexIntegrityChecker (Auditor)
- Non-destructive (`readOnly = true`) scanner checking consistency across:
  - `crawled_documents` table vs. in-memory `InvertedIndex`.
  - Relational metadata (`indexed_document_metadata`) vs. calculated postings length.
  - `document_embeddings` table vs. in-memory `VectorStorage`.
  - Current configured embedding model name and dimensions vs. stored vectors.
- Produces `IndexIntegrityReport` identifying missing lexical documents, orphaned postings, corpus statistics drift, and embedding defects.

### IndexRepairService (Automated Self-Healing)
- `RepairMode.DRY_RUN`: Identifies and logs repair plan without mutating state.
- `RepairMode.REPAIR`:
  - Re-indexes missing lexical documents directly from clean text.
  - Purges orphaned postings and residual metadata.
  - Regenerates missing, outdated, or incompatible embeddings.
  - Purges orphaned vectors from deleted documents.
  - Re-warms in-memory inverted index to synchronize BM25 corpus statistics.
  - Re-audits post-repair state to verify 100% healthy recovery.

---

## 7. Exponential Backoff Retry & Recovery System

Managed by `DocumentRecoveryService`:
- **Classification:** Categorizes failures into `TRANSIENT` (timeouts, deadlocks, IO blips) and `PERMANENT` (unsupported encoding, constraint violations).
- **Exponential Backoff:** $\text{delay} = \min(30000\text{ ms}, 1000\text{ ms} \times 2^{\text{retryCount}})$.
- **Dead-Letter Policy:** Allows up to 3 retry attempts before transitioning to `FAILED`.
- **Version Preservation:** If an update to an already `ACTIVE` document fails during Stage 1 or Stage 2, the document's previous version remains active and searchable.

---

## 8. REST Administration Endpoints

All administrative lifecycle management is exposed under `/api/v1/lifecycle`:

```http
GET    /api/v1/lifecycle/status/{docId}         # Document status, version, embedding consistency
POST   /api/v1/lifecycle/reindex/{docId}        # Trigger staged single-document reindexing
DELETE /api/v1/lifecycle/{docId}?hard=false     # Safe deletion and index/vector purge
GET    /api/v1/lifecycle/integrity/check        # Non-destructive cross-layer reconciliation audit
POST   /api/v1/lifecycle/integrity/repair       # Automated repair (mode=REPAIR|DRY_RUN)
POST   /api/v1/lifecycle/retry?documentId=...   # Retry failed or retry-eligible documents
GET    /api/v1/lifecycle/metrics                # Real-time operational telemetry snapshot
```

---

## 9. Staff-Engineer Interview Talking Points

1. **Why keep ONNX inference outside database transactions?**
   - Transformer inference on CPU takes 2–15 ms per document. Under crawl spikes, holding database connection locks across inference exhausts the HikariCP pool and causes transaction timeouts. Staging inference pre-transaction keeps database commits sub-millisecond.
2. **How does SearchForge avoid index drift on content modification?**
   - Single-document atomic replacement deletes all prior postings, adjusts $df$ and $ctf$, and recalibrates $avgdl$ dynamically, avoiding expensive whole-corpus rebuilds.
3. **What is the difference between soft delete and hard delete?**
   - Soft delete keeps the `crawled_documents` row with status `DELETED` for audit trails and re-crawl tracking, but immediately purges all search representations. Hard delete cascades removal from all relational tables.
4. **How does idempotency prevent wasted computation?**
   - Canonical URL normalization and SHA-256 fingerprinting detect unchanged documents immediately upon fetch, skipping tokenization, index replacement, and ONNX inference completely.
