# SearchForge Phase 8: Index Lifecycle, Data Consistency & Recovery Architecture

## 1. Executive Summary & Objective

SearchForge has constructed an industrial search engine across Phases 1 through 7.1, spanning virtual-threaded web crawling, inverted and positional indexing, BM25 scoring, query intelligence, a React 18 / Vite SERP frontend, and local ONNX dense semantic retrieval with Reciprocal Rank Fusion (RRF).

However, an in-depth architectural audit of the crawling, indexing, and embedding subsystems reveals critical operational risks:
- Document crawling, lexical indexing, and vector embedding currently operate as **disconnected stages**.
- Changes to crawled documents are not automatically detected or propagated to lexical postings or embeddings.
- Deletions in one subsystem leave orphaned records in others, producing "ghost search results".
- If embedding generation fails after lexical indexing succeeds, the document remains in a permanent **split-brain state** (searchable in BM25, invisible in semantic search, skewing RRF ranking).

**The Core Phase 8 Mandate:**
> *A document must never silently exist in an inconsistent state across SearchForge's storage, lexical indexes, positional indexes, document statistics, and semantic embeddings.*

This document provides the foundational architectural audit and blueprints the Phase 8 unified document lifecycle, version-locked change detection, transactional indexing pipeline, non-destructive integrity checker, and automated repair mechanisms.

---

## 2. Existing Architecture & Subsystems

SearchForge currently structures document ingress and retrieval across four decoupled modules:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 1. Crawler Subsystem (com.searchforge.crawler)                                         │
│    - CrawlEngine: BFS crawler on Project Loom virtual threads                          │
│    - DocumentFetcher: Jsoup HTTP client with timeout & 2MB binary guards               │
│    - HtmlParser: DOM stripping, link extraction, SHA-256 content hashing               │
│    - CrawledDocumentRepository: Persists to 'crawled_documents' table                  │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ (Manual or Batch Trigger)
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 2. Lexical Indexing Subsystem (com.searchforge.indexer)                                │
│    - DefaultIndexingEngine: Coordinates text tokenization, stemming & persistence      │
│    - Tokenizer / TextNormalizer / StopWordFilter / PorterStemmer: Linguistic pipeline   │
│    - InMemoryInvertedIndex: ConcurrentHashMap dictionary, positions, and doc lengths   │
│    - Relational Repositories: 'indexed_terms', 'term_postings', 'indexed_document_meta'│
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ (Independent Trigger)
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 3. Semantic Embedding Subsystem (com.searchforge.semantic)                             │
│    - DocumentEmbeddingService: Generates 384-d dense vectors                           │
│    - OnnxEmbeddingModel: Local ONNX Runtime inference (all-MiniLM-L6-v2)               │
│    - InMemoryVectorStorage: Concurrent memory vector store for dot-product similarity  │
│    - DocumentEmbeddingRepository: Persists raw binary float vectors to database        │
└────────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ (User Query Time)
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ 4. Search & Ranking Subsystem (com.searchforge.search / ranking)                       │
│    - DefaultHybridSearchService: Executes BM25 and Semantic retrieval in parallel      │
│    - ReciprocalRankFusionEngine: RRF score fusion: RRF(d) = Σ 1 / (60 + rank)          │
│    - SearchController: Exposes REST API (/api/v1/search) to React SERP                 │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Current Document Processing & Ingestion Flows

### 3.1 Document Ingestion Flow (Crawler)
1. **Frontier Dequeue:** `CrawlEngine` virtual threads poll `UrlFrontier`.
2. **Politeness & Robots:** Checks `RobotsTxtService` and throttles host via `PolitenessRateLimiter`.
3. **HTML Fetching:** `DocumentFetcher` performs HTTP GET and enforces 2MB payload guard.
4. **DOM Parsing & Hash Generation:** `HtmlParser` strips structural HTML, extracts title and clean text, and computes `contentHash` via SHA-256 (`MessageDigest.getInstance("SHA-256")`).
5. **Deduplication Check:** Checks in-memory `visitedContentHashes` set and calls `repository.existsByContentHash(hash)`.
6. **Persistence:** Saves new `CrawledDocument` into PostgreSQL `crawled_documents` table.
7. **Outbound Links:** Discovered hyperlinks are enqueued into `UrlFrontier` for depth $+ 1$.

### 3.2 Lexical Indexing Flow
1. **Batch Trigger:** Invoked manually via `POST /api/v1/index/build` or `synchronizeCorpus()`.
2. **Unindexed Discovery:** Queries `crawledDocumentRepository.findUnindexedDocuments(pageable)` where query is:
   ```sql
   SELECT d FROM CrawledDocument d 
   WHERE d.id NOT IN (SELECT m.documentId FROM IndexedDocumentMetadata m)
   ```
3. **Linguistic Processing:** Text is normalized, stop words removed, and stemmed into `ProcessedToken`s.
4. **Postings & Statistics:**
   - Group token positions by term.
   - For each term, increment `documentFrequency` ($df$) and `totalTermFrequency` ($ctf$) in `indexed_terms`.
   - Save `TermPosting` containing `(termId, documentId, positions)` in `term_postings`.
   - Save `IndexedDocumentMetadata` containing `(documentId, tokenCount, indexedAt)` in `indexed_document_metadata`.
5. **Memory Synchronization:** Inserts into `InMemoryInvertedIndex` (updating `dictionary`, `docLengths`, and `totalTokens`).

### 3.3 Semantic Embedding Flow
1. **Trigger:** Invoked separately via `DocumentEmbeddingService.generateAndStoreEmbedding(doc)` or `embedAndStoreBatch()`.
2. **Lead-Weighted Text Representation:** Truncates title and first 250 words to avoid semantic dilution.
3. **ONNX Model Inference:** `OnnxEmbeddingModel` runs WordPiece tokenization, dynamic sequence tensor inference on CPU, mean pooling, and L2 normalization to produce a 384-dimensional `float[]` vector.
4. **Dual Storage:**
   - Puts `DocumentVector(documentId, vector)` into `InMemoryVectorStorage`.
   - Serializes `vector` into binary byte array and saves `DocumentEmbeddingEntity` into `document_embeddings` table.

---

## 4. Architectural Audit: Specific Inquiries

| Question | Current Code Reality | Phase 8 Evaluation |
| :--- | :--- | :--- |
| **Where are documents created?** | `CrawlEngine.processTask()` creates `CrawledDocument` entities. Also `DevDataLoader` seeds 24 encyclopedia documents directly into the repository. | Centralized repository persistence, but lacks lifecycle tracking (`status`, `version`, `retryCount`). |
| **Where are documents updated?** | **Nowhere.** `CrawledDocument` has setters, but neither the crawler nor indexer ever executes an `UPDATE` on existing document rows. Re-crawling an existing URL with new content fails due to unique URL constraints. | Critical gap. Phase 8 must introduce explicit document versioning and content update pipelines. |
| **How are duplicate URLs handled?** | `VisitedFilter` skips seen URLs during a crawl run. `crawled_documents.url` has a unique database constraint. Re-crawling an existing URL triggers a database constraint error or skips saving. | If an existing URL's content changes on the web, SearchForge currently cannot update it. |
| **How are content hashes calculated?** | `HtmlParser.computeSha256(cleanText)` produces a 64-character hexadecimal SHA-256 digest over the parsed clean text. | Clean text SHA-256 is reliable, but is only checked against new documents, not used for version comparison. |
| **How is the inverted index updated?** | `DefaultIndexingEngine.removeDocument(id)` removes old postings, decrements $df$/$ctf$, and deletes metadata. However, this is only called if a document is re-indexed by explicit ID. | The primitive exists, but is not connected to change detection. |
| **How are positional indexes updated?** | `TermPosting` stores `List<Integer> positions`. `InMemoryInvertedIndex` stores positions in `PostingsList`. Removing a document purges all position offsets. | Cleanly decoupled, but requires atomic synchronization during updates. |
| **How are document statistics maintained?** | `InMemoryInvertedIndex` maintains `docLengths` map and `AtomicLong totalTokens`. $\text{avgdl}$ is computed dynamically as $\text{totalTokens} / N$. `IndexedTerm` stores persistent $df$ and $ctf$. | Removing a document correctly decrements `totalTokens` and $df$/$ctf$, but statistical consistency between RAM and DB is not formally checked. |
| **How are embeddings generated & persisted?** | `OnnxEmbeddingModel` runs local ONNX CPU inference. Persisted as binary IEEE-754 bytes in `document_embeddings.vector_data` and RAM `InMemoryVectorStorage`. | Fast and efficient, but completely decoupled from lexical indexing. |
| **Is indexing synchronous or asynchronous?** | Crawling is asynchronous (`CompletableFuture` on Virtual Threads). Indexing and embedding are synchronous on demand. | No asynchronous event bridge links crawl completion to indexing and embedding. |
| **Existing transaction boundaries?** | `DefaultIndexingEngine.indexDocument()` is `@Transactional`. `DocumentEmbeddingService.generateAndStoreEmbedding()` is `@Transactional`. They run in separate transactions. | Holding DB transactions during ONNX model inference is risky. They must be staged. |
| **Existing failure handling?** | `DefaultIndexingEngine` catches exceptions and returns `IndexingResult.failure()`. `CrawlEngine` increments `pagesFailed`. | No retry queue, no error status persistence, and no compensation for partial failures. |
| **Existing deletion mechanisms?** | `DefaultIndexingEngine.removeDocument(id)` and `DocumentEmbeddingService.deleteEmbedding(id)` exist, but are not invoked together from any deletion controller. | Deleting a crawled document from PostgreSQL leaves orphan entries in index tables and RAM vectors. |
| **Index persistence vs. rebuild?** | Dual-tier: Persistent tables (`indexed_terms`, `term_postings`, `indexed_document_metadata`, `document_embeddings`) store index data. On boot, `InMemoryInvertedIndex` and `InMemoryVectorStorage` warm up from DB. | Strong dual-tier foundation, but no integrity audit verifies that RAM matches DB after drift. |
| **Potential sources of stale data?** | 1. URL content updates leave old postings and vectors intact.<br>2. Document deletion leaves orphan postings.<br>3. Model changes leave incompatible vectors.<br>4. Failure during embedding leaves document half-indexed. | High risk of subtle, silent search relevance corruption. |

---

## 5. Identified Consistency & Operational Risks

```
                                  [Document Changed on Web]
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
             [Lexical Index Stale]                       [Vector Embedding Stale]
        - Search continues matching old terms       - Dense retrieval matches obsolete concept
        - Snippets highlight deleted words          - Dot product reflects old latent space
                       │                                           │
                       └─────────────────────┬─────────────────────┘
                                             ▼
                                [Corrupted Hybrid Ranking]
                         - BM25 and Semantic rank different texts!
                         - RRF fuses contradictory rankings
```

### Risk 1: Silent Content Drift
If `https://example.com/docs` updates its text from "Java 17 features" to "Java 21 Virtual Threads":
- Current behavior: Crawler detects existing URL and skips or errors.
- If forced: Indexing updates BM25 terms, but embedding is never regenerated.
- Consequence: Lexical search finds "Java 21", but Semantic search still matches "Java 17".

### Risk 2: Split-Brain Ingestion on Failure
If an unindexed batch processes 100 documents:
- Lexical indexing succeeds for Document #42 and commits postings to DB.
- Document #42's embedding inference fails (e.g., token length exception or OOM).
- Consequence: Document #42 is permanently indexed in BM25, but missing from Semantic search. RRF penalizes or skews the document inconsistently.

### Risk 3: Stale Ghost Results from Partial Deletion
If an administrator deletes a document:
- If only `crawled_documents` row is deleted, postings in `term_postings` and vectors in `InMemoryVectorStorage` remain alive.
- Consequence: User searches match the term, but clicking the hit yields a 404 / missing entity.

### Risk 4: Long-Running DB Transaction Locking
If ONNX inference is placed inside a `@Transactional` Spring service method:
- Database connection from HikariCP pool is held open for 50–500 ms while CPU computes transformer tensors.
- Under high crawl load, HikariCP pool exhausts, blocking all user search queries.

---

## 6. Proposed Phase 8 Lifecycle Architecture

To resolve every identified risk, Phase 8 introduces the **Unified Document Lifecycle Engine**:

```
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                         Document Lifecycle Engine                           │
 │                                                                             │
 │                   [1. Ingress & Change Detection]                           │
 │                                   │                                         │
 │                      ┌────────────┴────────────┐                            │
 │                      ▼                         ▼                            │
 │              (Hash Matches)            (Hash Differs / New)                 │
 │             [Mark UNCHANGED]                   │                            │
 │            (Skip Processing)                   ▼                            │
 │                                     [State: PROCESSING]                     │
 │                                                │                            │
 │                   [2. Stage A: Linguistic & Embedding Prep]                 │
 │                     - Tokenize & compute term frequencies                   │
 │                     - Generate ONNX vector (OUTSIDE DB TX)                  │
 │                                                │                            │
 │                   [3. Stage B: Atomic Transactional Commit]                 │
 │                     - @Transactional Boundary Opens:                        │
 │                         • Update CrawledDocument (version++, hash, status)  │
 │                         • Atomic Lexical Swap (delete old, insert new)      │
 │                         • Update Positional Postings                        │
 │                         • Update Document Statistics                        │
 │                         • Upsert DocumentEmbeddingEntity (version-locked)   │
 │                         • Transition State: ACTIVE                          │
 │                     - @Transactional Boundary Commits                       │
 │                                                │                            │
 │                   [4. Stage C: Memory Cache Sync]                           │
 │                     - Swap InMemoryInvertedIndex entries                    │
 │                     - Swap InMemoryVectorStorage vector                     │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Proposed Document Lifecycle State Machine

```
              ┌──────────────┐
              │  DISCOVERED  │ (URL added to frontier)
              └──────┬───────┘
                     │
                     ▼
              ┌──────────────┐
              │   FETCHED    │ (Raw HTML downloaded)
              └──────┬───────┘
                     │
                     ▼
              ┌──────────────┐
              │  PROCESSING  │ (Parsing, linguistic tokenization, ONNX embedding)
              └──┬─────────┬─┘
                 │         │
      (Success)  │         │ (Failure)
                 ▼         ▼
              ┌──────┐  ┌────────┐
              │ACTIVE│  │ FAILED │ ◄──┐ (Max retries exceeded)
              └──┬───┘  └───┬────┘    │
                 │          │         │
                 │          ▼         │
                 │     ┌─────────┐    │
                 │     │ RETRY   ├────┘
                 │     └─────────┘
                 │
                 ▼
          ┌──────────────┐
          │   DELETING   │ (Excluded from search immediately)
          └──────┬───────┘
                 │
                 ▼
          ┌──────────────┐
          │   DELETED    │ (Postings, vectors, metadata fully purged)
          └──────────────┘
```

### State Definitions & Invariants:
1. `DISCOVERED`: URL is scheduled in the frontier queue; no content or entity exists yet.
2. `FETCHED`: HTML payload downloaded by fetcher and verified with HTTP 200.
3. `PROCESSING`: DOM parsed, text extracted, tokens generated, and ONNX embedding calculated. (Document is **invisible** to search).
4. `ACTIVE`: Both lexical index and dense embedding are committed to persistent storage and synchronized in RAM. Fully searchable across all search modes.
5. `FAILED`: An unrecoverable or transient error occurred during processing. Recorded with error message and failure timestamp.
6. `RETRY`: Scheduled for bounded exponential backoff retry.
7. `DELETING`: Soft-lock state: document is immediately excluded from all active search queries while background deletion sweeps postings and vectors.
8. `DELETED`: Terminal state. All inverted postings, positions, corpus statistics, embeddings, and metadata have been permanently eradicated.

---

## 8. Staged Transaction & Recovery Strategy

To guarantee strict ACID data consistency without starving database connection pools during computationally expensive ONNX inference, Phase 8 enforces **Staged Two-Phase Indexing**:

### Step 1: Pre-Transaction Computation (CPU Bound, No DB Locks)
```java
// 1. Tokenize & stem text into ProcessedTokens
List<ProcessedToken> tokens = tokenizer.tokenize(rawText);

// 2. Compute 384-d dense vector on ONNX Runtime (CPU)
// NO database connection is held open during this model execution!
float[] vector = embeddingModel.embed(leadRepresentation);
byte[] binaryVector = VectorSerialization.toByteArray(vector);
```

### Step 2: Atomic Transactional Commit (I/O Bound, Database Transaction)
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void commitDocumentUpdate(Long docId, String contentHash, int version, 
                                 List<ProcessedToken> tokens, byte[] binaryVector) {
    // 1. Update CrawledDocument entity
    CrawledDocument doc = crawledDocumentRepo.findById(docId).orElseThrow();
    doc.setContentHash(contentHash);
    doc.setVersion(version);
    doc.setStatus(DocumentLifecycleStatus.ACTIVE);
    crawledDocumentRepo.save(doc);

    // 2. Atomic Lexical Purge & Insert
    indexingEngine.updateDocumentPostings(docId, tokens);

    // 3. Version-Locked Vector Upsert
    DocumentEmbeddingEntity embeddingEntity = embeddingRepo.findByDocumentId(docId)
        .orElseGet(() -> new DocumentEmbeddingEntity(docId, modelName, dimensions, binaryVector));
    embeddingEntity.setVectorData(binaryVector);
    embeddingEntity.setContentHash(contentHash);
    embeddingEntity.setDocumentVersion(version);
    embeddingRepo.save(embeddingEntity);
}
```

### Step 3: Post-Commit In-Memory Synchronization
```java
// Executed only after the database transaction successfully commits
inMemoryIndex.addDocument(docId, tokens);
vectorStorage.put(new DocumentVector(docId, vector));
```

### Recovery Invariants:
- If Step 1 fails $\rightarrow$ Zero database changes. Previous `ACTIVE` document version remains searchable. Document marked `FAILED` with error log.
- If Step 2 fails $\rightarrow$ Spring `@Transactional` triggers automatic SQL rollback. Zero orphaned postings or vectors committed.
- If Step 3 encounters an issue $\rightarrow$ Database contains the definitive state; `warmup()` from DB reconciles RAM automatically.

---

## 9. Implementation Roadmap & Task Mapping

We will implement Phase 8 strictly following the master prompt sequence:

```
Task 0  ──► Architecture Audit & Design (docs/PHASE_8_ARCHITECTURE.md) [COMPLETED]
Task 1  ──► Document Lifecycle Model (DocumentLifecycleStatus enum, CrawledDocument extension)
Task 2  ──► Document Versioning & Change Detection (DocumentChangeDetector, SHA-256 diffing)
Task 3  ──► Incremental Index Update Engine (Atomic single-document lexical + positional update)
Task 4  ──► Document Deletion & Cleanup (DocumentLifecycleService.deleteDocument)
Task 5  ──► Transactional Indexing & Failure Handling (Staged 2-phase pipeline, idempotency)
Task 6  ──► Document Version & Embedding Consistency (Content hash locked vectors, state validation)
Task 7  ──► Index Reconciliation & Integrity Checker (Non-destructive multi-tier audit service)
Task 8  ──► Automated Repair Mechanism (Dry-run & active repair for missing/stale entries)
Task 9  ──► Retry & Recovery System (Exponential backoff, dead-letter queue, fallback state)
Task 10 ──► Lifecycle Observability & Operational Metrics (Micrometer/SLF4J structured metrics)
Task 11 ──► API & Administration Support (Lifecycle endpoints in IndexLifecycleAdminController)
Task 12 ──► Comprehensive Testing & Regression (Maintain all 421 tests, add lifecycle suites)
Task 13 ──► Documentation (docs/PHASE_8_INDEX_LIFECYCLE.md & README update)
Task 14 ──► Final Verification & 16-Step Scenario Demonstration
```

---

## 10. Audit Sign-off

- **Audit Completed By:** Antigravity / Google DeepMind
- **Repository:** `SearchForge` (Java 21 LTS / Spring Boot 4.1.2)
- **Target Deliverable:** `docs/PHASE_8_ARCHITECTURE.md`
- **Current Test Suite Status:** **421 Tests Passing (100% Hermetic)**
- **Next Immediate Action:** Proceed directly to **Task 1: Document Lifecycle Model**.
