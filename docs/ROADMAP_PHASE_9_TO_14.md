# SearchForge: Master Engineering Roadmap (Phase 9 Through Phase 14)
## Systems Reliability, Scale, Security, Quality, Deployment & Portfolio Release

**Author:** SearchForge Architecture Team  
**Status:** Approved Roadmap Specification (Strictly Planning-Only)  
**Target Completion:** Production & Portfolio Release  
**Current Baseline:** Phase 9 Complete (521 Hermetic Tests Passing: 508 Backend + 13 Frontend)

---

## 1. Executive Summary & Project Status Through Phase 9

SearchForge is a production-grade, custom search engine built from first principles in Java 21 LTS, Spring Boot 4.1.2, and React 18 / Vite 6. Designed explicitly without third-party search engines (no Lucene, Elasticsearch, OpenSearch, Solr), external vector databases (no Pinecone, Milvus, Weaviate), or paid neural APIs, SearchForge implements every algorithmic component from foundational data structures and mathematical models.

### Completed Milestones (Phases 1–9)

| Phase | Milestone Name | Core Implemented Architecture | Test Count |
| :--- | :--- | :--- | :---: |
| **Phase 1** | Inception & Concurrency Foundation | Java 21 Project Loom virtual threads, lock-free queues, modular architecture | **15 tests** |
| **Phase 2** | Polite Web Crawler & Ingestion | RFC 3986 URL normalizer, RFC 9309 robots parser, per-domain rate limiter, SHA-256 deduplication | **40 tests** |
| **Phase 3** | Linguistic Pipeline & Inverted Index | Unicode NFD decomposition, 130+ stopword filter, Martin Porter 1980 stemmer, dual-tier index, positional phrase search, recursive-descent Boolean AST | **65 tests** |
| **Phase 4** | Okapi BM25 Ranking Engine | Robertson-Spärck Jones IDF, sublinear term saturation ($k_1=1.2$), length penalty ($b=0.75$), TAAT union generator, Min-Heap Top-K, IR metrics | **70 tests** |
| **Phase 5** | Query Intelligence & Search Assistance | Weighted Trie prefix autocomplete ($1.3\ \mu\text{s}$), Damerau-Levenshtein spelling correction ($71\ \mu\text{s}$), dynamic highlighted snippets, directional synonyms | **88 tests** |
| **Phase 6** | Frontend Web Application | React 18, Vite 6, multi-modal search dispatching, inline BM25 math explainability drawer ("Why this result?"), search history, accessible keyboard hotkeys | **13 tests** |
| **Phase 7 & 7.1** | Dense Semantic & Hybrid Search | Local Microsoft ONNX Runtime inference (`all-MiniLM-L6-v2`, 384-D), 30,522-token WordPiece tokenizer, dynamic sequence tensors ($2.36\text{ ms}$ CPU), Reciprocal Rank Fusion (RRF) | **130 tests** |
| **Phase 8** | Index Lifecycle & Data Consistency | 10-state lifecycle state machine, cryptographic change detection, staged two-phase transactional coordinator, residual-free deletion, self-healing auto-repair | **76 tests** |
| **Phase 9** | Concurrency & Reliability Engineering | Striped `DocumentLockManager`, JPA `@Version` optimistic locking, lock-free `ActiveDocumentFilter`, atomic `IndexQuerySnapshot`, `UrlTracker` in-flight claims, `StartupIndexReconciliationRunner`, chaos testing, 50-thread stress harness | **24 tests** |
| **TOTAL** | **Phases 1 Through 9** | **Full-Stack, Hermetic, Verified Search Engine Core** | **521 tests** |

### Purpose of Phases 9 Through 14

Phases 1 through 8 established core functional capabilities. However, a production search engine cannot rely solely on happy-path execution:
1. It must remain resilient under severe concurrency, race conditions, and abrupt node crashes (**Phase 9**).
2. It must scale predictably to larger corpora and higher QPS without memory exhaustion or latency degradation (**Phase 10**).
3. It must defend against malicious web content, SSRF, DNS rebinding, and API abuse (**Phase 11**).
4. Its ranking superiority must be validated by independent, unbiased search quality benchmarks (**Phase 12**).
5. It must be reproducible, containerized, monitored, and deployable via automated CI/CD (**Phase 13**).
6. It must be polished into a showcase portfolio release with executive documentation, interactive demonstrations, and staff-engineer architectural dossiers (**Phase 14**).

---

## 2. Global Architectural Governance & Implementation Rules

All future work from Phase 9 through Phase 14 must adhere to the following non-negotiable engineering rules:

1. **Strictly Sequential Execution:** Only one phase will be implemented at a time. A phase must be implemented, tested, verified, and approved before the next phase begins.
2. **Zero External Search Engines:** Under no circumstance will Lucene, Elasticsearch, Solr, OpenSearch, Pinecone, or external search frameworks be introduced.
3. **No Arbitrary Dependencies:** Every dependency must solve a demonstrated engineering bottleneck. No Kafka, Redis, or Kubernetes will be added unless empirical profiling mandates it.
4. **Hermetic Testing:** Every phase must expand the automated test suite without breaking or weakening existing tests. Zero flaky tests allowed.
5. **No Data Fabrication:** Benchmark scores, latency measurements, and relevance judgments must be genuinely computed from executed code.
6. **Strict Planning Boundary:** This document serves as the master blueprint. No source code or database migrations will be applied until the phase is explicitly triggered.

---

## 3. High-Level Dependency Map & Execution Sequence

```
                                [Phase 9: Complete (521 Tests)]
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 9: Concurrency & Reliability          │
                       │   - Thread-safety audits & race protection    │
                       │   - Atomic index read-isolation & rollback    │
                       │   - Failure injection & crash recovery        │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 10: Performance & Scalability         │
                       │   - Latency percentiles & memory profiling    │
                       │   - Batch throughput & connection tuning      │
                       │   - Exact vector vs. ANN feasibility study    │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 11: Security & API Hardening          │
                       │   - SSRF, DNS rebinding & crawler network IP  │
                       │   - API authentication & RBAC endpoints       │
                       │   - Token-bucket rate limiting & headers      │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 12: Search Quality Evaluation         │
                       │   - Expanded multi-intent evaluation dataset  │
                       │   - Independent relevance ground truth        │
                       │   - RRF parameter tuning & ablation analysis  │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 13: Production Deployment & CI/CD     │
                       │   - Multi-container Docker & Compose topology │
                       │   - Automated GitHub Actions deployment       │
                       │   - Backup, restore & zero-downtime rollback  │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                       ┌───────────────────────────────────────────────┐
                       │   PHASE 14: Product Polish & Portfolio        │
                       │   - UI micro-interactions & WCAG AA layout    │
                       │   - Visual README & architecture dossiers     │
                       │   - Interactive demo walkthrough & release    │
                       └───────────────────────────────────────────────┘
```

---

## 4. Phase 9 — Concurrency & Reliability Engineering

### 4.1 Phase Overview
* **Phase Name:** Concurrency & Reliability Engineering
* **Objective:** Validate and harden SearchForge against concurrent read/write races, simultaneous crawler mutations, database transaction rollbacks, in-memory split-brain conditions, and abrupt application terminations.
* **Why Necessary:** Phase 8 introduced lifecycle state transitions and staged two-phase transactional indexing. However, when hundreds of virtual threads fetch URLs, update documents, and execute searches concurrently, race conditions can cause partial posting visibility, version drift, or orphaned memory state.
* **Problems Solved:**
  - Race conditions between parallel workers updating or deleting the same document ID.
  - Split-brain states where PostgreSQL commits a document update but in-memory index synchronization fails.
  - Search queries observing partially swapped lexical postings or mismatched dense vectors.
  - Crawler workers producing duplicate entries for canonicalized URLs during concurrent frontier dequeuing.

### 4.2 Existing Architecture Analysis
* **Existing Components:**
  - `TransactionalIndexingCoordinator`: Executes Stage 1 (tokenization & embedding) outside DB transactions, Stage 2 inside `@Transactional(isolation = READ_COMMITTED)`, Stage 3 memory sync.
  - `DocumentChangeDetector`: Computes SHA-256 hashes and evaluates change types.
  - `IncrementalIndexUpdateEngine`: Recalibrates postings and corpus stats.
  - `UrlFrontier`: Backed by `ConcurrentLinkedQueue`.
  - `InMemoryInvertedIndex` & `InMemoryVectorStorage`: Backed by `ConcurrentHashMap`.
* **Gaps & Needed Improvements:**
  - `CrawledDocument` lacks an explicit `@Version` optimistic locking field; concurrent updates can overwrite without conflict detection.
  - `InMemoryInvertedIndex` replaces postings term-by-term; a concurrent search query running mid-update can observe a document partially indexed.
  - `TransactionalIndexingCoordinator` lacks an explicit in-memory rollback handler if Stage 3 encounters an unexpected out-of-memory or thread interruption.
  - `UrlFrontier` does not atomically lock in-flight URLs across parallel virtual threads, allowing simultaneous crawls of identical targets.

### 4.3 Task-by-Task Breakdown

#### Task 9.1: Concurrency Architecture & Thread-Safety Audit
* **Objective:** Inspect all mutable singleton beans, static state, caches, and repository access patterns across the backend.
* **Technical Explanation:** Run static and dynamic concurrency analysis across `com.searchforge.*`. Detect unsynchronized access to collections, non-thread-safe formatters, and unprotected field mutations.
* **Components Affected:** All services in `crawler`, `indexer`, `ranking`, `semantic`, and `lifecycle`.
* **Dependencies:** None.
* **Implementation Scope:** Non-destructive audit report documenting critical synchronization paths.
* **Deliverables:** `docs/audits/CONCURRENCY_THREAD_SAFETY_AUDIT.md`.
* **Testing Requirements:** Inspection checklists and static analysis verification.
* **Acceptance Criteria:** Every shared mutable resource is cataloged with its synchronization primitive (e.g., `ConcurrentHashMap`, `AtomicReference`, `ReentrantReadWriteLock`).
* **Complexity:** Medium.

#### Task 9.2: Optimistic Locking & Version Race Protection on Document Lifecycle Entities
* **Objective:** Prevent silent overwrite anomalies when two workers attempt to modify the same document concurrently.
* **Technical Explanation:** Add JPA `@Version` column (`version_lock`) to `CrawledDocument`. When concurrent updates occur, Hibernate throws `OptimisticLockException`, triggering `DocumentRecoveryService` exponential retry rather than silent data corruption.
* **Components Affected:** `CrawledDocument`, `CrawledDocumentRepository`, `TransactionalIndexingCoordinator`, `DocumentRecoveryService`.
* **Dependencies:** Task 9.1.
* **Implementation Scope:** Schema update, entity version annotation, exception mapping in coordinator.
* **Deliverables:** Updated entity, migration script, optimistic lock conflict handler.
* **Testing Requirements:** Concurrent multi-threaded update tests attempting simultaneous writes to identical doc ID.
* **Acceptance Criteria:** Conflicting simultaneous writes throw `OptimisticLockException` and trigger clean retry without data loss.
* **Complexity:** Medium.

#### Task 9.3: Atomic In-Memory Index Snapshotting & Read-Isolated Postings Replacement
* **Objective:** Ensure concurrent search queries never observe half-updated or partially indexed document states.
* **Technical Explanation:** Implement copy-on-write or read-write locked atomic postings substitution in `InMemoryInvertedIndex`. During single-document incremental update, prepare the document's new postings in a local buffer, then swap them into the dictionary using fine-grained atomic operations.
* **Components Affected:** `InMemoryInvertedIndex`, `IncrementalIndexUpdateEngine`, `DefaultRankedRetrievalEngine`.
* **Dependencies:** None.
* **Implementation Scope:** Refactor `InMemoryInvertedIndex` mutation methods to guarantee atomic replacement.
* **Deliverables:** Thread-safe atomic update mechanisms in `InMemoryInvertedIndex`.
* **Testing Requirements:** High-frequency read loop during continuous heavy background document updates.
* **Acceptance Criteria:** Zero query iterations observe mismatched term counts, corrupted position arrays, or negative document lengths.
* **Complexity:** High.

#### Task 9.4: Atomic In-Memory Vector Storage Synchronization & Rollback Guard
* **Objective:** Maintain 100% synchronization between PostgreSQL `document_embeddings` and `InMemoryVectorStorage`.
* **Technical Explanation:** Wrap Stage 3 memory synchronization with defensive rollback mechanics. If vector memory insertion fails, the coordinator marks the document for immediate reconciliation and logs an explicit transactional alert.
* **Components Affected:** `TransactionalIndexingCoordinator`, `VectorStorage`, `InMemoryVectorStorage`.
* **Dependencies:** Task 9.2.
* **Implementation Scope:** Staged synchronization coordinator hardening.
* **Deliverables:** Verified memory-sync rollback and alert dispatching.
* **Testing Requirements:** Mocked memory failure injection during Stage 3 execution.
* **Acceptance Criteria:** Relational state and in-memory vector storage remain consistent; failures trigger automatic repair flag.
* **Complexity:** Medium.

#### Task 9.5: Concurrent Crawler Race & In-Flight URL Deduplication Guard
* **Objective:** Guarantee that two concurrent crawler virtual threads never process the same URL simultaneously.
* **Technical Explanation:** Introduce an in-flight atomic URL claim set (`ConcurrentHashMap.newKeySet()`) in `UrlFrontier`. When a virtual thread dequeues a URL, it atomically claims it. Other threads encountering the same canonical URL immediately skip execution.
* **Components Affected:** `UrlFrontier`, `ConcurrentCrawlEngine`, `CrawlEngine`.
* **Dependencies:** None.
* **Implementation Scope:** Thread-safe in-flight registry with release-on-completion lifecycle.
* **Deliverables:** In-flight claim registry in `UrlFrontier`.
* **Testing Requirements:** Multi-threaded crawl tests seeding identical URLs simultaneously across 10 virtual threads.
* **Acceptance Criteria:** Exactly one worker fetches the target URL; duplicate workers gracefully yield.
* **Complexity:** Low.

#### Task 9.6: Search Isolation & Active-State Filtering Under Concurrent Mutations
* **Objective:** Ensure queries strictly filter out documents undergoing deletion or rollback.
* **Technical Explanation:** Enhance `DefaultRankedRetrievalEngine`, `RankedBooleanQueryEngine`, and `DefaultHybridSearchService` with an in-memory active document bitmask or bloom filter, rejecting candidate IDs not in `ACTIVE` state before score accumulation.
* **Components Affected:** All retrieval engines in `com.searchforge.ranking` and `com.searchforge.search`.
* **Dependencies:** Task 9.3.
* **Implementation Scope:** Search engine candidate validation pipeline.
* **Deliverables:** Lock-free active document filter integrated into search dispatchers.
* **Testing Requirements:** Queries executed while target documents are transitioned to `DELETING` or `FAILED`.
* **Acceptance Criteria:** Mutating or deleting documents disappear from search results instantaneously.
* **Complexity:** Medium.

#### Task 9.7: Crash Simulation & Startup Reconciliation Auto-Healing
* **Objective:** Guarantee that if the application crashes mid-transaction, startup sequence reconciles memory.
* **Technical Explanation:** Connect `IndexIntegrityChecker` and `IndexRepairService` directly to the Spring Boot `ApplicationReadyEvent`. On startup, SearchForge audits database state against in-memory postings, automatically repairing any inconsistencies from previous ungraceful shutdowns.
* **Components Affected:** `IndexRepairService`, `IndexIntegrityChecker`, `SearchForgeApplication`.
* **Dependencies:** Task 9.4.
* **Implementation Scope:** Startup reconciliation runner with configurable fast-fail and repair profiles.
* **Deliverables:** Automated startup health verification hook.
* **Testing Requirements:** Crash simulation tests injecting uncommitted records into H2 and restarting context.
* **Acceptance Criteria:** Application restarts, detects incomplete document states, and heals them to 100% health automatically.
* **Complexity:** Medium.

#### Task 9.8: Chaos Engineering & Failure Injection Harness
* **Objective:** Provide a deterministic test harness to inject network timeouts, database connection drops, and memory pressure.
* **Technical Explanation:** Build a test utility (`ChaosFailureInjector`) capable of simulating database deadlocks, interrupted threads, and ONNX inference timeouts during live crawling and indexing.
* **Components Affected:** Test suites under `src/test/java/com/searchforge/concurrency`.
* **Dependencies:** Tasks 9.2–9.7.
* **Implementation Scope:** Dedicated test harness module.
* **Deliverables:** `ChaosFailureInjector` and failure simulation scenarios.
* **Testing Requirements:** Automated chaos scenarios executing against Spring context.
* **Acceptance Criteria:** System maintains data consistency and zero unhandled thread deaths under 20% simulated failure injection.
* **Complexity:** High.

#### Task 9.9: High-Concurrency Stress & Reliability Regression Test Suite
* **Objective:** Validate end-to-end reliability under sustained concurrent load.
* **Technical Explanation:** Execute a multi-threaded stress suite: 20 virtual threads crawling, 10 threads continuously updating documents, 5 threads deleting documents, and 20 threads executing hybrid searches simultaneously.
* **Components Affected:** Entire backend test suite.
* **Dependencies:** Tasks 9.1–9.8.
* **Implementation Scope:** Stress test suite in `src/test/java/com/searchforge/concurrency/ConcurrencyStressTest.java`.
* **Deliverables:** Automated stress test suite executing for 60 seconds with zero assertion failures.
* **Testing Requirements:** Multi-threaded stress test with `CountDownLatch` and `CyclicBarrier`.
* **Acceptance Criteria:** Zero race conditions, zero deadlocks, zero split-brain states, and 100% test pass rate.
* **Complexity:** Medium.

### 4.4 Phase Boundaries
* **In Scope:** Thread-safety, locking, race conditions, in-memory isolation, crawler deduplication, crash recovery, chaos testing.
* **Out of Scope:** Distributed sharding, multi-node clustering, external Redis locking, performance profiling (deferred to Phase 10).

### 4.5 Completion Criteria
1. Zero race conditions detected under high-concurrency stress test (50+ virtual threads).
2. Optimistic locking active on `CrawledDocument` with verified rollback and retry handling.
3. Startup reconciliation automatically heals crash-simulated database states.
4. Total test suite expanded with at least 25 new concurrency tests, maintaining 100% pass rate.

### 4.6 Risks
* *Deadlock Risk:* Complex locking between relational transactions and in-memory locks could cause deadlocks if acquisition order is not strictly deterministic.
* *Mitigation:* Stage 1 computations remain strictly lock-free; in-memory swaps occur strictly post-commit.

---

## 5. Phase 10 — Performance & Scalability Engineering

### 5.1 Phase Overview
* **Phase Name:** Performance & Scalability Engineering
* **Objective:** Profile SearchForge empirically, identify algorithmic bottlenecks, optimize memory consumption, measure query latency percentiles (P50/P90/P95/P99), and scale the corpus from 1,000 to 25,000+ documents.
* **Why Necessary:** SearchForge achieved sub-2.5ms latency on a 120-document benchmark in Phase 7.1. However, real search engines must index tens of thousands of documents without linear memory explosion or quadratic scoring penalties.
* **Problems Solved:**
  - Inverted index heap consumption scaling linearly without compression.
  - Linear scan bottleneck in exact vector similarity as corpus size exceeds 10,000 documents.
  - Connection pool exhaustion under sustained HTTP search traffic.
  - Unwarmed ONNX runtime thread allocation causing CPU core thrashing.

### 5.2 Existing Architecture Analysis
* **Existing Components:**
  - `InMemoryInvertedIndex`: Uses raw Java `List<Integer>` for positional posting arrays (heavy object overhead).
  - `ExactVectorSimilarityEngine`: Brute-force linear scan over `ConcurrentHashMap<Long, DocumentVector>`.
  - `OnnxEmbeddingModel`: Dynamic sequence tensor sizing with single-query inference.
  - `QueryEmbeddingCache`: Bounded LRU cache (1,000 capacity).
* **Gaps & Needed Improvements:**
  - Postings lists lack variable-byte or delta integer compression (`VByte` / `Elias-Fano`), consuming ~16 bytes per position instead of ~1–2 bytes.
  - Exact vector scan scales as $O(N \cdot D)$; at 25,000 documents, computing 25,000 dot products of 384 floats requires 9.6 million floating-point operations per query.
  - Database connection pool (`HikariCP`) uses default small pool sizes unoptimized for high-concurrency Virtual Thread throughput.

### 5.3 Task-by-Task Breakdown

#### Task 10.1: Empirical Performance & Latency Telemetry Harness
* **Objective:** Establish automated micro-benchmarks and latency telemetry capturing P50, P90, P95, and P99 metrics.
* **Technical Explanation:** Implement a dedicated benchmark harness using `System.nanoTime()` and histogram aggregators (`HdrHistogram` or custom reservoir sampling). Separates tokenization, BM25 scoring, ONNX inference, and RRF blending timings.
* **Components Affected:** `com.searchforge.ranking.evaluation`, new benchmark controller.
* **Dependencies:** None.
* **Implementation Scope:** Telemetry interceptors and benchmark runners.
* **Deliverables:** `LatencyBenchmarkRunner` with percentile JSON reporting.
* **Testing Requirements:** Benchmark suite executed across 500 query iterations with warm-up cycles.
* **Acceptance Criteria:** Produces verifiable CSV/JSON latency reports with zero mock data.
* **Complexity:** Medium.

#### Task 10.2: Lexical Inverted Index Memory Footprint & Compression Profiling
* **Objective:** Analyze and compress positional postings to reduce in-memory heap footprint by 60%+.
* **Technical Explanation:** Profile JVM heap using `Runtime.getRuntime()` and memory instrumentation. Replace raw boxed integers in `Posting` with compact primitive arrays (`int[]`) or delta-encoded byte buffers ($d_{i} - d_{i-1}$).
* **Components Affected:** `PostingsList`, `Posting`, `InMemoryInvertedIndex`.
* **Dependencies:** Task 10.1.
* **Implementation Scope:** Internal data structure optimization preserving existing search contracts.
* **Deliverables:** Memory-optimized postings representation.
* **Testing Requirements:** Verification that exact phrase search and BM25 scores remain bit-identical before and after compression.
* **Acceptance Criteria:** Heap usage per 10,000 postings decreases by at least 50% with zero regression in phrase matching.
* **Complexity:** High.

#### Task 10.3: High-Throughput Batch Crawl & Ingestion Pipeline Optimization
* **Objective:** Maximize crawler document ingestion rate without triggering host politeness violations.
* **Technical Explanation:** Tune Project Loom virtual thread worker pools, adjust Jsoup socket buffers, and optimize DOM parsing allocations. Eliminate redundant string allocations in `HtmlParser`.
* **Components Affected:** `CrawlEngine`, `HtmlParser`, `DocumentFetcher`.
* **Dependencies:** None.
* **Implementation Scope:** Low-allocation parsing refactor and socket reuse.
* **Deliverables:** Optimized crawler worker pipeline.
* **Testing Requirements:** Ingestion rate test against local WireMock web server simulating 1,000 pages.
* **Acceptance Criteria:** Ingestion throughput increases by at least 35% on local benchmarks.
* **Complexity:** Medium.

#### Task 10.4: Warmed ONNX Batch Inference & Dynamic Thread Pool Tuning
* **Objective:** Optimize ONNX Runtime CPU execution for parallel multi-query workloads.
* **Technical Explanation:** Configure `OrtSession.SessionOptions`: set execution mode (`ORT_SEQUENTIAL`), tune intra-op thread count (`intraOpNumThreads = Runtime.getRuntime().availableProcessors()`), and pre-warm model execution graphs on startup.
* **Components Affected:** `OnnxEmbeddingModel`, `EmbeddingConfig`.
* **Dependencies:** None.
* **Implementation Scope:** ONNX session tuning and lifecycle warmup.
* **Deliverables:** Enhanced ONNX configuration profile.
* **Testing Requirements:** Multi-threaded query benchmark measuring inference latency under 10 concurrent requests.
* **Acceptance Criteria:** Average warmed inference latency stays below 3.0ms even under concurrent load.
* **Complexity:** Medium.

#### Task 10.5: Exact Vector Similarity vs. Approximate Nearest Neighbor (ANN) Feasibility Evaluation
* **Objective:** Empirically evaluate the exact vector scan threshold and assess if an Approximate Nearest Neighbor (ANN) index is required.
* **Technical Explanation:** Benchmark exact cosine similarity across corpus sizes: $N = 1,000$, $N = 5,000$, $N = 10,000$, and $N = 25,000$. Measure scan latency, CPU utilization, and candidate recall.
* **Components Affected:** `ExactVectorSimilarityEngine`, `VectorStorage`.
* **Dependencies:** Task 10.1.
* **Implementation Scope:** Benchmarking harness and comparative architectural analysis report.
* **Deliverables:** `docs/benchmarks/VECTOR_SCALABILITY_ANN_STUDY.md`.
* **Testing Requirements:** Benchmark suite executed at progressive corpus scales.
* **Acceptance Criteria:** Document the exact corpus threshold where brute-force cosine exceeds 10ms SLA, providing a clear go/no-go decision on custom HNSW implementation.
* **Complexity:** High.

#### Task 10.6: Database Query & Connection Pool Under Load Optimization
* **Objective:** Eliminate database bottlenecks during simultaneous crawling and search traffic.
* **Technical Explanation:** Optimize `HikariConfig`: adjust pool size, connection timeout, and leak detection thresholds. Add composite B-tree indexes on `crawled_documents(status, content_hash)` and `term_postings(term_id, document_id)`.
* **Components Affected:** `application.yml`, PostgreSQL JPA entities, repository queries.
* **Dependencies:** None.
* **Implementation Scope:** Database index optimization and connection pool tuning.
* **Deliverables:** Tuned HikariCP configuration and relational index migration.
* **Testing Requirements:** Concurrent load test exercising repository queries under 50 simultaneous connections.
* **Acceptance Criteria:** Zero connection pool timeouts; 99th percentile query time remains sub-5ms.
* **Complexity:** Low.

#### Task 10.7: Corpus Scaling Benchmark (1k → 10k → 25k Documents)
* **Objective:** Demonstrate SearchForge's operational limits on progressively larger synthetic corpora.
* **Technical Explanation:** Seed and index multi-domain documents at 1,000, 10,000, and 25,000 scales. Measure index build time, memory consumption, average document length stability, and query latency across all three retrieval modes (BM25, Semantic, Hybrid).
* **Components Affected:** Full system.
* **Dependencies:** Tasks 10.1–10.6.
* **Implementation Scope:** Automated corpus scaling benchmark runner.
* **Deliverables:** `docs/benchmarks/CORPUS_SCALING_REPORT.md` with charts and tables.
* **Testing Requirements:** End-to-end execution of large-scale corpus indexing.
* **Acceptance Criteria:** Complete performance profile published with documented memory and latency ceilings.
* **Complexity:** Very High.

#### Task 10.8: Performance Regression Suite & Latency SLA Enforcement
* **Objective:** Ensure future code changes never silently degrade latency or throughput.
* **Technical Explanation:** Add an automated performance regression test suite executing in CI, asserting that BM25 query latency is $< 1.0\text{ ms}$, cached semantic query latency is $< 0.2\text{ ms}$, and end-to-end hybrid query latency is $< 5.0\text{ ms}$.
* **Components Affected:** `src/test/java/com/searchforge/performance`.
* **Dependencies:** Tasks 10.1–10.7.
* **Implementation Scope:** Automated JUnit 5 performance assertions with warm-up cycles.
* **Deliverables:** `PerformanceRegressionTest.java`.
* **Testing Requirements:** Hermetic performance test assertions.
* **Acceptance Criteria:** Automated build fails if key latency thresholds are violated.
* **Complexity:** Medium.

### 5.4 Phase Boundaries
* **In Scope:** Profiling, memory optimization, postings compression, ONNX tuning, ANN threshold study, corpus scaling tests.
* **Out of Scope:** Distributed sharding, multi-server clustering, distributed caching (deferred to post-release).

### 5.5 Completion Criteria
1. P50, P90, P95, and P99 latency benchmarks established and documented.
2. In-memory postings heap footprint reduced by at least 40% via primitive array structures.
3. Corpus scaled to 10,000+ documents with verified search latency under 5.0ms for hybrid queries.
4. Comprehensive scalability report published in `docs/benchmarks/`.

### 5.6 Risks
* *Memory Ceiling:* Large corpora with 384-dimensional float vectors (1.5 KB per vector) could pressure JVM heap on development machines at 50,000+ documents.
* *Mitigation:* Explicitly bound maximum benchmark scale to available system memory (e.g., 25,000 docs $\approx$ 38 MB vector RAM).

---

## 6. Phase 11 — Security & API Hardening

### 6.1 Phase Overview
* **Phase Name:** Security & API Hardening
* **Objective:** Secure SearchForge against web crawler attack vectors (SSRF, DNS rebinding, redirect traps), protect administrative lifecycle APIs with authentication, enforce rate limiting, and harden HTTP endpoints.
* **Why Necessary:** SearchForge actively fetches external URLs from the web. Without stringent security boundaries, an autonomous crawler can be weaponized to scan internal private subnets (`192.168.x.x`, `10.x.x.x`), attack cloud metadata endpoints (`169.254.169.254`), or crash the JVM via billion-laugh decompression bombs.
* **Problems Solved:**
  - Server-Side Request Forgery (SSRF) targeting internal network services.
  - DNS rebinding attacks evading initial URL string hostname checks.
  - Infinite redirect loops and malicious zip/gzip decompression bombs.
  - Unauthenticated access to sensitive administrative endpoints (`/api/v1/lifecycle/repair`, `/api/v1/crawler/reset`).
  - Denial of Service (DoS) from rapid, unthrottled API search queries.

### 6.2 Existing Architecture Analysis
* **Existing Components:**
  - `DocumentFetcher`: Validates content length (2MB guard) and verifies MIME type (`text/html`).
  - `UrlNormalizer`: Normalizes schemes and domains.
  - `HtmlSanitizer`: Escapes raw HTML entities (`&`, `<`, `>`, `"`, `'`) before wrapping terms in `<mark>` tags.
* **Gaps & Needed Improvements:**
  - `DocumentFetcher` relies on Jsoup default DNS resolution; it does not check resolved IP addresses against private or loopback ranges before initiating socket connections.
  - Administrative endpoints under `/api/v1/crawler`, `/api/v1/index`, and `/api/v1/lifecycle` have zero authentication; anyone on the network can trigger corpus wipes or reindexing.
  - REST endpoints lack token-bucket rate limiting, making them vulnerable to query floods.
  - Spring Boot application lacks standard security response headers (CSP, HSTS, X-Frame-Options).

### 6.3 Task-by-Task Breakdown

#### Task 11.1: Security Architecture Audit & Threat Modeling
* **Objective:** Perform STRIDE threat modeling across the ingestion, search, and admin surfaces.
* **Technical Explanation:** Analyze potential attack vectors: SSRF, DoS, XSS in snippets, SQL injection in JPA repositories, and resource exhaustion.
* **Components Affected:** Entire system.
* **Dependencies:** None.
* **Implementation Scope:** Security review and threat cataloging.
* **Deliverables:** `docs/security/THREAT_MODEL_AUDIT.md`.
* **Testing Requirements:** Vulnerability surface checklist.
* **Acceptance Criteria:** Documented threat matrix with assigned mitigations.
* **Complexity:** Low.

#### Task 11.2: Server-Side Request Forgery (SSRF) & Private Network IP Defense for Web Crawler
* **Objective:** Block crawler from connecting to internal, private, loopback, or cloud metadata IP addresses.
* **Technical Explanation:** Implement a custom `SafeDnsResolver` and `NetworkSecurityGuard`. Before connecting to any host, resolve all `InetAddress` records and reject:
  - Loopback (`127.0.0.0/8`, `::1`)
  - Private IPv4 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
  - Link-local and Cloud metadata (`169.254.169.254`, `fe80::/10`)
  - Broadcast and multicast addresses.
* **Components Affected:** `DocumentFetcher`, `CrawlEngine`, new `SafeDnsResolver`.
* **Dependencies:** Task 11.1.
* **Implementation Scope:** Socket connection pre-flight check in crawler fetcher.
* **Deliverables:** `SafeDnsResolver.java` and `IpRangeValidator.java`.
* **Testing Requirements:** Unit tests verifying immediate rejection of `localhost`, `127.0.0.1`, `192.168.1.1`, and AWS/GCP metadata IPs.
* **Acceptance Criteria:** Crawler strictly throws `SecurityException` and drops the URL before sending any HTTP packet.
* **Complexity:** High.

#### Task 11.3: Crawler Fetch Guardrails (DNS Rebinding, Redirect Loops & Content Bombs)
* **Objective:** Protect against malicious redirect traps, infinite redirect loops, and oversized payloads.
* **Technical Explanation:** Restrict redirect following to a maximum of 3 hops. Re-validate the destination IP address on every redirect step to eliminate DNS rebinding. Enforce an unyielding stream byte counter terminating downloads at 2MB immediately.
* **Components Affected:** `DocumentFetcher`.
* **Dependencies:** Task 11.2.
* **Implementation Scope:** Hardened HTTP execution pipeline in `DocumentFetcher`.
* **Deliverables:** Enhanced `DocumentFetcher` with redirect safety and streaming byte counters.
* **Testing Requirements:** WireMock tests simulating redirect loops, DNS rebinding to 127.0.0.1, and 50MB endless HTTP streams.
* **Acceptance Criteria:** All malicious scenarios are terminated safely within 100ms without memory spikes.
* **Complexity:** Medium.

#### Task 11.4: API Key Authentication & Role-Based Access Control (RBAC)
* **Objective:** Restrict administrative operations to authorized administrators while keeping search endpoints public.
* **Technical Explanation:** Implement a lightweight, zero-dependency API Key authentication filter (`ApiKeyAuthenticationFilter`). Validate `X-API-KEY` header against configured hashes. Differentiate between `ROLE_SEARCH` (public) and `ROLE_ADMIN` (required for `/api/v1/lifecycle/**`, `/api/v1/crawler/**`, and `/api/v1/index/**`).
* **Components Affected:** `com.searchforge.config.security`, all controllers.
* **Dependencies:** None.
* **Implementation Scope:** Spring Web security filter and configuration properties.
* **Deliverables:** `ApiKeySecurityConfig`, `ApiKeyAuthFilter`, updated admin endpoints.
* **Testing Requirements:** MVC slice tests asserting `401 Unauthorized` / `403 Forbidden` on admin endpoints without key.
* **Acceptance Criteria:** Admin endpoints require valid API key; search endpoints remain accessible.
* **Complexity:** Medium.

#### Task 11.5: REST API Input Sanitization, Schema Validation & Error Masking
* **Objective:** Prevent malformed inputs from causing unhandled exceptions and mask internal stack traces.
* **Technical Explanation:** Enforce strict validation: query length limits (max 500 chars), parameter type checking, and boundary guards on `limit` (1–100). Implement a global `@ControllerAdvice` sanitizing all error responses, returning standard RFC 7807 problem details without internal database error messages or stack traces.
* **Components Affected:** `com.searchforge.controller`, `GlobalExceptionHandler`.
* **Dependencies:** None.
* **Implementation Scope:** Global exception handler and request validators.
* **Deliverables:** `GlobalExceptionHandler.java`, `ApiErrorResponse.java`.
* **Testing Requirements:** Tests submitting 10,000-character queries, negative limits, and malformed JSON payloads.
* **Acceptance Criteria:** System returns clean `400 Bad Request` JSON with zero internal stack trace exposure.
* **Complexity:** Low.

#### Task 11.6: Token-Bucket Rate Limiting & DoS Protection
* **Objective:** Prevent abusive query floods and denial-of-service attacks.
* **Technical Explanation:** Implement an in-memory token-bucket rate limiter (`TokenBucketRateLimiter`) based on client IP address. Enforce configurable rate limits (e.g., 60 search requests per minute per IP, 10 admin requests per minute).
* **Components Affected:** New `RateLimitingFilter` in `com.searchforge.config.security`.
* **Dependencies:** None.
* **Implementation Scope:** Servlet filter with thread-safe atomic token buckets.
* **Deliverables:** `TokenBucketRateLimiter.java`, `RateLimitingFilter.java`.
* **Testing Requirements:** Concurrent load tests asserting `429 Too Many Requests` when limit is exceeded.
* **Acceptance Criteria:** Requests exceeding burst limits receive `429 Too Many Requests` with `Retry-After` header.
* **Complexity:** Medium.

#### Task 11.7: HTTP Security Headers, Strict CORS & Cookie Hygiene
* **Objective:** Harden HTTP responses against clickjacking, MIME sniffing, and cross-origin attacks.
* **Technical Explanation:** Configure security response headers:
  - `Content-Security-Policy: default-src 'self'`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - Restrict CORS origins explicitly via `application.yml` environment configuration.
* **Components Affected:** `WebConfig.java`, HTTP filter chain.
* **Dependencies:** None.
* **Implementation Scope:** Security headers filter and CORS configuration hardening.
* **Deliverables:** Security headers filter and verified CORS policies.
* **Testing Requirements:** Automated HTTP response header assertions across all endpoints.
* **Acceptance Criteria:** Every HTTP response includes all mandatory security headers.
* **Complexity:** Low.

#### Task 11.8: Dependency Vulnerability Scanning & Security Regression Suite
* **Objective:** Validate that no known Common Vulnerabilities and Exposures (CVEs) exist in dependencies.
* **Technical Explanation:** Integrate OWASP Dependency-Check or Maven dependency audit into the build lifecycle. Create automated security test suites validating SSRF blocks, rate limiting, and API key enforcement.
* **Components Affected:** `backend/pom.xml`, `src/test/java/com/searchforge/security`.
* **Dependencies:** Tasks 11.1–11.7.
* **Implementation Scope:** Automated security regression test suite.
* **Deliverables:** `SecurityRegressionTest.java` and dependency audit report.
* **Testing Requirements:** Complete execution of security test suite.
* **Acceptance Criteria:** Zero high or critical CVEs; 100% pass rate on security test suite.
* **Complexity:** Medium.

### 6.4 Phase Boundaries
* **In Scope:** SSRF defense, private IP filtering, DNS rebinding protection, API authentication, rate limiting, security headers, input sanitization.
* **Out of Scope:** OAuth2 / OpenID Connect servers, external identity providers (Auth0/Okta), hardware security modules.

### 6.5 Completion Criteria
1. SSRF defense blocks all private, loopback, and cloud metadata IP attempts with 100% reliability.
2. Administrative APIs strictly enforce API key authentication.
3. Token-bucket rate limiter rejects abusive traffic with HTTP 429.
4. Security test suite passing with at least 20 new dedicated security tests.

### 6.6 Risks
* *Public Crawling Legitimate Block Risk:* Strict IP validation might inadvertently block valid public URLs hosted on unusual CDN configurations.
* *Mitigation:* Allow explicit domain whitelisting and only block RFC 1918 / IANA reserved IP ranges.

---

## 7. Phase 12 — Search Quality Evaluation & Optimization

### 7.1 Phase Overview
* **Phase Name:** Search Quality Evaluation & Optimization
* **Objective:** Establish an independent, rigorous Information Retrieval (IR) evaluation harness to quantify ranking effectiveness across BM25, Dense Semantic, and Hybrid RRF modes.
* **Why Necessary:** The Phase 7.1 benchmark demonstrated +4.8% NDCG@3 on 40 queries across 120 synthetic encyclopedia documents. However, an enterprise search engine requires a diverse, realistic query set containing technical identifiers, complex multi-term concepts, and vocabulary mismatches to eliminate bias.
* **Problems Solved:**
  - Lack of multi-intent query diversity in existing benchmarks.
  - Risk of dataset leakage or self-fulfilling relevance labels.
  - Uncalibrated RRF smoothing parameters ($k$) for varying corpus sizes.
  - Inability to detect ranking regressions during continuous development.

### 7.2 Existing Architecture Analysis
* **Existing Components:**
  - `RelevanceMetricsCalculator`: Computes NDCG@K, MRR, Precision@K, Recall@K.
  - `SearchQualityBenchmarkRunner`: Evaluates queries against relevance judgments.
  - `ReciprocalRankFusionEngine`: Blends lexical and semantic ranks with fixed $k=60$.
* **Gaps & Needed Improvements:**
  - Benchmark query set is limited to 40 queries; needs expansion to 100+ queries across 6 distinct intent categories.
  - Relevance judgments are binary or 3-level; needs 5-level graded relevance ($0$ = Irrelevant, $1$ = Marginally Relevant, $2$ = Relevant, $3$ = Highly Relevant, $4$ = Exact Answer).
  - Lacks Mean Average Precision (MAP) and Mean Reciprocal Rank at cutoff $K$ (MRR@K).
  - RRF constant $k=60$ was adopted from academic literature without empirical grid-search on SearchForge's specific scoring distributions.

### 7.3 Task-by-Task Breakdown

#### Task 12.1: Multi-Intent Gold-Standard Evaluation Dataset Expansion
* **Objective:** Build a representative, high-diversity benchmark dataset of 100+ queries across 6 query categories.
* **Technical Explanation:** Curate queries spanning:
  1. *Exact Identifier / Keyword Queries:* (e.g. `"RFC 9309"`, `"ConcurrentHashMap"`, `"HTTP 429"`).
  2. *Conceptual / Vocabulary Mismatch Queries:* (e.g. `"fast lightweight web daemon"`, `"concurrency without OS threads"`).
  3. *Technical Informational Queries:* (e.g. `"how does B-Tree balance in database"`, `"Java virtual thread memory overhead"`).
  4. *Ambiguous / Polysemous Queries:* (e.g. `"spring"`, `"driver"`, `"loom"`).
  5. *Synonym & Paraphrase Queries:* (e.g. `"k8s cluster setup"`, `"database indexes guide"`).
  6. *Navigational / Exact URL Queries:* Target specific homepages and guides.
* **Components Affected:** `src/main/resources/benchmark/` or test dataset loaders.
* **Dependencies:** None.
* **Implementation Scope:** JSON dataset definition with metadata tags and intent labels.
* **Deliverables:** `evaluation_queries_v2.json` (100+ queries).
* **Testing Requirements:** Schema validation and intent distribution balance checks.
* **Acceptance Criteria:** Dataset contains at least 100 queries with balanced category distribution.
* **Complexity:** Medium.

#### Task 12.2: Independent Relevance Judgment Protocol & Graded Ground Truth
* **Objective:** Establish an unbiased 5-point graded relevance scale ($0$ to $4$) for all evaluation query-document pairs.
* **Technical Explanation:** Formulate ground truth judgments using strict independent rubrics rather than ranking engine outputs. Store judgments in standard TREC-style qrels format: `(query_id, document_id, relevance_grade)`.
* **Components Affected:** Benchmark dataset resources.
* **Dependencies:** Task 12.1.
* **Implementation Scope:** Ground-truth qrels file creation and validation.
* **Deliverables:** `relevance_judgments_v2.qrels`.
* **Testing Requirements:** Consistency verification ensuring no unmapped document IDs exist.
* **Acceptance Criteria:** Every query has verified graded judgments across relevant and distractor documents.
* **Complexity:** Medium.

#### Task 12.3: Quantitative Search Quality Benchmark Pipeline (NDCG@K, MRR, MAP, P@K, R@K)
* **Objective:** Expand the IR metrics calculator to support Mean Average Precision (MAP) and arbitrary cutoff metrics.
* **Technical Explanation:** Extend `RelevanceMetricsCalculator` with MAP:
  $$\text{MAP} = \frac{1}{|Q|} \sum_{q=1}^{|Q|} \left( \frac{1}{\text{Rel}_q} \sum_{k=1}^{N} P@k \cdot \text{rel}(k) \right)$$
  and support cutoffs $K \in \{1, 3, 5, 10\}$.
* **Components Affected:** `RelevanceMetricsCalculator`, `SearchQualityBenchmarkRunner`.
* **Dependencies:** Tasks 12.1, 12.2.
* **Implementation Scope:** Algorithm expansion in metrics calculator.
* **Deliverables:** Enhanced `RelevanceMetricsCalculator.java`.
* **Testing Requirements:** Mathematical unit tests against textbook worked IR examples.
* **Acceptance Criteria:** Calculations match standard TREC evaluation benchmarks within 0.0001 precision.
* **Complexity:** Low.

#### Task 12.4: Reciprocal Rank Fusion (RRF) Parameter Grid Search & Tuning
* **Objective:** Find the optimal RRF smoothing constant $k$ for SearchForge's specific candidate pool sizes.
* **Technical Explanation:** Execute a grid search across $k \in \{10, 20, 30, 40, 50, 60, 80, 100\}$ and candidate pool cutoffs $C \in \{10, 20, 50\}$. Plot NDCG@5 and MRR curves to determine empirical optimality.
* **Components Affected:** `ReciprocalRankFusionEngine`, benchmark runner.
* **Dependencies:** Tasks 12.2, 12.3.
* **Implementation Scope:** Grid search evaluation harness.
* **Deliverables:** `docs/benchmarks/RRF_PARAMETER_TUNING_STUDY.md`.
* **Testing Requirements:** Automated grid sweep script running against evaluation dataset.
* **Acceptance Criteria:** Optimal $k$ parameter identified and justified by empirical NDCG improvements.
* **Complexity:** Medium.

#### Task 12.5: Vocabulary Mismatch & Technical Identifier Failure Analysis (Ablation Study)
* **Objective:** Conduct an ablation study detailing where each retrieval mode fails and why hybrid succeeds.
* **Technical Explanation:** Compare BM25 vs. Semantic vs. Hybrid on:
  - Exact identifier queries (highlighting semantic softening failures).
  - Vocabulary mismatch queries (highlighting BM25 zero-hit failures).
  - Quantify the hybrid consensus boost rate.
* **Components Affected:** `SearchQualityBenchmarkRunner`.
* **Dependencies:** Task 12.3.
* **Implementation Scope:** Automated ablation report generator.
* **Deliverables:** `docs/benchmarks/SEARCH_QUALITY_ABLATION_STUDY.md`.
* **Testing Requirements:** Category-by-category comparative performance assertion.
* **Acceptance Criteria:** Documented trade-offs with explicit error analysis for top failure modes.
* **Complexity:** Medium.

#### Task 12.6: Search Quality Telemetry & Automated Drift Detection
* **Objective:** Enable continuous runtime quality monitoring to detect relevance drift when new documents are indexed.
* **Technical Explanation:** Build a lightweight quality check service running in the background after major indexing operations, verifying that benchmark NDCG@5 does not degrade below a configurable threshold (e.g., 0.85).
* **Components Affected:** `com.searchforge.lifecycle.metrics`, new quality probe.
* **Dependencies:** Tasks 12.3, 12.4.
* **Implementation Scope:** Periodic quality assertion probe.
* **Deliverables:** `SearchQualityMonitorService.java`.
* **Testing Requirements:** Automated test asserting quality alert generation upon simulated index degradation.
* **Acceptance Criteria:** System flags an alert if NDCG@5 drops by more than 5%.
* **Complexity:** Low.

#### Task 12.7: Quality Regression Test Suite & Evaluation Report Generator
* **Objective:** Add search quality gates to the automated test suite.
* **Technical Explanation:** Implement a JUnit 5 test asserting minimum quality thresholds on the gold-standard dataset:
  - Hybrid NDCG@5 $\ge 0.85$
  - Hybrid MRR $\ge 0.88$
  - Hybrid NDCG@5 $>$ BM25 NDCG@5 by at least 3.0%.
* **Components Affected:** `src/test/java/com/searchforge/evaluation/SearchQualityRegressionTest.java`.
* **Dependencies:** Tasks 12.1–12.6.
* **Implementation Scope:** Automated CI regression test.
* **Deliverables:** `SearchQualityRegressionTest.java`.
* **Testing Requirements:** Hermetic test execution against gold-standard dataset.
* **Acceptance Criteria:** Test suite enforces ranking quality on every build.
* **Complexity:** Medium.

### 7.4 Phase Boundaries
* **In Scope:** 100+ query dataset expansion, 5-point graded relevance, MAP/NDCG/MRR metrics, RRF grid search, ablation studies, CI quality gates.
* **Out of Scope:** Online click-through rate (CTR) modeling, user behavioral logging, A/B testing infrastructure.

### 7.5 Completion Criteria
1. 100+ query evaluation dataset established with independent graded ground truth.
2. Comparative benchmark completed proving Hybrid RRF superiority over single-mode baselines.
3. Optimal RRF smoothing parameter $k$ empirically justified and configured.
4. Quality regression suite integrated into automated tests.

### 7.6 Risks
* *Label Bias Risk:* Subjective judgment bias could artificially flatter semantic retrieval.
* *Mitigation:* Apply strict independent rubrics separating exact keyword match expectations from conceptual paraphrases.

---

## 8. Phase 13 — Production Deployment & CI/CD

### 8.1 Phase Overview
* **Phase Name:** Production Deployment & CI/CD
* **Objective:** Containerize SearchForge into an optimized production topology, automate build and verification pipelines, and establish a repeatable deployment and disaster recovery runbook.
* **Why Necessary:** SearchForge currently runs in development mode via local Maven and Vite commands. For production viability, it must run inside secure, multi-stage Docker containers with persistent PostgreSQL storage, volume mounts for ONNX model weights, and zero-downtime rollback capabilities.
* **Problems Solved:**
  - "Works on my machine" inconsistencies across host OS environments.
  - Fragile manual startup steps and environment variable misconfigurations.
  - Unmanaged database schema migrations.
  - Lack of automated container vulnerability scanning and deployment pipelines.

### 8.2 Existing Architecture Analysis
* **Existing Components:**
  - `docker-compose.yml`: Basic PostgreSQL 16 Alpine container configuration.
  - `.github/workflows/ci.yml`: Runs `mvn test` on pull requests.
  - `.github/workflows/pages.yml`: Deploys `docs-site/` to GitHub Pages.
* **Gaps & Needed Improvements:**
  - No production `Dockerfile` for the Spring Boot backend (uses heavyweight base images or lacks multi-stage build).
  - No production `Dockerfile` / Nginx setup for the React frontend.
  - No unified production `docker-compose.prod.yml` coordinating backend, frontend, database, and volume storage.
  - No Flyway or Liquibase database migration tool managing PostgreSQL schema evolution.
  - CI pipeline only tests backend; does not test frontend, build Docker images, or verify end-to-end container health.

### 8.3 Task-by-Task Breakdown

#### Task 13.1: Production Configuration Profile & Environment Secrets Separation
* **Objective:** Create a secure, hardened `application-prod.yml` reading all sensitive properties from environment variables.
* **Technical Explanation:** Configure externalized properties for database credentials, ONNX model path, API keys, CORS origins, and thread pool sizes. Ensure default passwords fail fast in production profile.
* **Components Affected:** `backend/src/main/resources/application-prod.yml`.
* **Dependencies:** None.
* **Implementation Scope:** Production Spring profile configuration.
* **Deliverables:** `application-prod.yml` and `.env.example`.
* **Testing Requirements:** Spring context startup tests verifying failure when required production environment variables are missing.
* **Acceptance Criteria:** Production profile starts cleanly with zero hardcoded credentials.
* **Complexity:** Low.

#### Task 13.2: Multi-Stage Production Docker Containers (Backend & Frontend)
* **Objective:** Build minimal, secure, non-root Docker images for backend and frontend.
* **Technical Explanation:**
  - *Backend Dockerfile:* Multi-stage build using `eclipse-temurin:21-jdk-alpine` for build and `eclipse-temurin:21-jre-alpine` for runtime. Run as unprivileged user `searchforge:searchforge`. Image size $< 250\text{ MB}$.
  - *Frontend Dockerfile:* Multi-stage build using `node:20-alpine` building Vite distribution, served via unprivileged `nginx:alpine` with optimized gzip and cache headers.
* **Components Affected:** `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`.
* **Dependencies:** Task 13.1.
* **Implementation Scope:** Dockerfile specifications and Nginx configurations.
* **Deliverables:** Production Dockerfiles for backend and frontend.
* **Testing Requirements:** Docker build execution and image vulnerability scan.
* **Acceptance Criteria:** Both images build cleanly and pass non-root security execution tests.
* **Complexity:** Medium.

#### Task 13.3: Production Docker Compose Orchestration with Health Checks & Volume Persistence
* **Objective:** Provide a turn-key production stack orchestration file.
* **Technical Explanation:** Author `docker-compose.prod.yml` linking:
  - `postgres`: PostgreSQL 16 Alpine with named volume `searchforge-db-data`.
  - `backend`: Spring Boot backend with volume `searchforge-models` (caching ONNX weights) and dependency healthcheck on `postgres`.
  - `frontend`: Nginx reverse proxy routing `/api/**` to backend and serving static SPA assets.
* **Components Affected:** `docker-compose.prod.yml`.
* **Dependencies:** Tasks 13.1, 13.2.
* **Implementation Scope:** Compose orchestration definition.
* **Deliverables:** `docker-compose.prod.yml`.
* **Testing Requirements:** Full stack startup via `docker compose -f docker-compose.prod.yml up -d` and health verification.
* **Acceptance Criteria:** Complete search engine stack boots in $< 30$ seconds and passes health probes.
* **Complexity:** Medium.

#### Task 13.4: Automated GitHub Actions CI/CD Pipeline Expansion
* **Objective:** Expand GitHub Actions workflow to validate frontend, backend, Docker builds, and deployment gates.
* **Technical Explanation:** Upgrade `.github/workflows/ci.yml` into a staged pipeline:
  1. *Stage 1 (Backend):* Java 21 LTS build, hermetic unit & integration test suites.
  2. *Stage 2 (Frontend):* Node.js test suite, ESLint, production Vite build.
  3. *Stage 3 (Containerization):* Docker build validation for backend and frontend images.
  4. *Stage 4 (Security):* Dependency vulnerability scan.
* **Components Affected:** `.github/workflows/ci.yml`.
* **Dependencies:** Tasks 13.2, 13.3.
* **Implementation Scope:** GitHub Actions workflow orchestration.
* **Deliverables:** Hardened `.github/workflows/ci.yml`.
* **Testing Requirements:** Workflow execution on feature branches and pull requests.
* **Acceptance Criteria:** CI validates entire full-stack build in $< 3$ minutes.
* **Complexity:** Medium.

#### Task 13.5: Production Database Migration & Schema Versioning Strategy
* **Objective:** Ensure automated, reliable database schema evolution across releases.
* **Technical Explanation:** Integrate Flyway migration framework (`flyway-core`, `flyway-database-postgresql`). Transition raw Hibernate `ddl-auto: update` to versioned migration scripts (`V1__init_schema.sql`, `V2__add_vector_tables.sql`, `V3__lifecycle_versioning.sql`).
* **Components Affected:** `backend/pom.xml`, `backend/src/main/resources/db/migration/`.
* **Dependencies:** None.
* **Implementation Scope:** Flyway integration and baseline schema scripts.
* **Deliverables:** Flyway configuration and versioned SQL migration scripts.
* **Testing Requirements:** Clean database initialization and migration tests.
* **Acceptance Criteria:** Flyway successfully applies all migrations on clean database instances.
* **Complexity:** Medium.

#### Task 13.6: Automated Backup, Disaster Recovery & Zero-Downtime Rollback Strategy
* **Objective:** Define automated backup scripts and rollback procedures.
* **Technical Explanation:** Create automated backup scripts (`scripts/backup-db.sh`, `scripts/restore-db.sh`) dumping PostgreSQL state and vector entities. Document zero-downtime container rollback using Docker image tagging.
* **Components Affected:** `scripts/`.
* **Dependencies:** Task 13.3.
* **Implementation Scope:** Shell scripts and operational procedures.
* **Deliverables:** Backup/restore shell scripts and `docs/operations/DISASTER_RECOVERY.md`.
* **Testing Requirements:** Simulated backup and restore test restoring 1,000 documents onto clean PostgreSQL instance.
* **Acceptance Criteria:** Database restored to 100% data integrity in $< 60$ seconds.
* **Complexity:** Low.

#### Task 13.7: Production Deployment Verification & Operational Runbook
* **Objective:** Author a complete operational guide for deploying, monitoring, and maintaining SearchForge.
* **Technical Explanation:** Document cloud hosting deployment options (e.g., AWS EC2 / Lightsail, Render, Hetzner, DigitalOcean), minimum hardware requirements (2 vCPU, 4GB RAM), startup verification checklists, and log inspection commands.
* **Components Affected:** `docs/operations/PRODUCTION_DEPLOYMENT_GUIDE.md`.
* **Dependencies:** Tasks 13.1–13.6.
* **Implementation Scope:** Operational engineering documentation.
* **Deliverables:** `PRODUCTION_DEPLOYMENT_GUIDE.md` and `TROUBLESHOOTING.md`.
* **Testing Requirements:** Dry-run deployment following the guide verbatim.
* **Acceptance Criteria:** A developer can launch the production stack in $< 5$ minutes following the runbook.
* **Complexity:** Low.

### 8.4 Phase Boundaries
* **In Scope:** Docker multi-stage images, production Compose stack, Flyway migrations, GitHub Actions CI pipeline, backup/restore scripts, operational runbook.
* **Out of Scope:** Multi-region Kubernetes clusters, Terraform infrastructure-as-code, multi-cloud failover.

### 8.5 Completion Criteria
1. Production Docker images built with unprivileged users and verified size $< 250\text{ MB}$.
2. `docker compose -f docker-compose.prod.yml up` starts the entire full-stack application successfully.
3. Flyway migrations control 100% of the relational schema.
4. GitHub Actions CI pipeline validates full-stack tests and Docker builds on every PR.

### 8.6 Risks
* *Resource Contention:* Running ONNX CPU inference alongside PostgreSQL inside a small container instance (e.g. 1GB RAM free tier) will trigger Linux OOM-killer.
* *Mitigation:* Explicitly document 4GB RAM minimum requirement; set JVM max heap `-Xmx2g` in Docker runtime flags.

---

## 9. Phase 14 — Product Polish & Portfolio Release

### 9.1 Phase Overview
* **Phase Name:** Product Polish & Portfolio Release
* **Objective:** Refine SearchForge into a visually stunning, highly usable, accessible, and interview-ready engineering showcase on GitHub.
* **Why Necessary:** Many technically impressive backend projects fail to gain traction because their visual presentation, user experience, documentation, and portfolio framing are unpolished. Phase 14 bridges technical rigor with executive presentation.
* **Problems Solved:**
  - Rough frontend edge-cases, missing loading skeletons, and uninformative error states.
  - Inaccessible UI contrast and lack of responsive mobile design.
  - Unstructured READMEs that overwhelm recruiters or fail to communicate architectural depth.
  - Absence of a fast, self-guided demo walkthrough for interviewers and visitors.

### 9.2 Existing Architecture Analysis
* **Existing Components:**
  - `frontend/`: React 18, Vite 6, clean custom-properties design system.
  - `README.md`: Comprehensive engineering guide with embedded SVGs.
  - `docs-site/`: Live interactive documentation portal on GitHub Pages.
  - `RankingExplanation`: Expandable "Why this result?" drawer.
* **Gaps & Needed Improvements:**
  - Search results page lacks smooth skeleton loading states during initial query evaluation.
  - Empty search results state is purely static; lacks query suggestion chips or spelling recovery prompts.
  - Mobile layout needs fine-tuning for mode selector and score drawer on small screens.
  - README lacks a concise 3-minute executive summary for recruiters and hiring managers.
  - Lacks a structured staff-engineer interview dossier synthesizing all 14 phases into actionable technical talking points.

### 9.3 Task-by-Task Breakdown

#### Task 14.1: Frontend UI/UX Polish, Micro-Interactions & Loading State Design
* **Objective:** Deliver a fluid, modern search experience with loading skeletons and transition animations.
* **Technical Explanation:** Replace abrupt loading spinners with pulsing skeleton cards matching the exact layout of search result items. Add subtle CSS transition animations for mode switching, autocomplete suggestions, and drawer expansion.
* **Components Affected:** `frontend/src/components/`, `frontend/src/styles/`.
* **Dependencies:** None.
* **Implementation Scope:** React component enhancement and CSS animation refinement.
* **Deliverables:** `ResultSkeleton.jsx` and smooth UI transitions.
* **Testing Requirements:** Visual inspection and user interaction testing.
* **Acceptance Criteria:** Zero layout shifts (CLS $< 0.1$) during query execution.
* **Complexity:** Low.

#### Task 14.2: Edge-Case Handling, Empty States & User Guidance
* **Objective:** Provide helpful, actionable feedback when queries yield zero results.
* **Technical Explanation:** When a query produces zero hits, display an informative empty state suggesting:
  - Spelling corrections (if applicable).
  - Switching from Boolean/Lexical mode to **Hybrid Mode** (leveraging dense semantic recall).
  - Removing strict negation operators or broadening search terms.
  - Interactive clickable fallback query chips.
* **Components Affected:** `frontend/src/pages/SearchPage.jsx`, `EmptyState.jsx`.
* **Dependencies:** None.
* **Implementation Scope:** Empty state component with intelligent guidance.
* **Deliverables:** `EmptyState.jsx` with recovery recommendations.
* **Testing Requirements:** Tests submitting nonsense queries and inspecting recovery suggestions.
* **Acceptance Criteria:** Empty results state actively guides user to successful query reformulations.
* **Complexity:** Low.

#### Task 14.3: Interactive Ranking Transparency Drawer Enhancement
* **Objective:** Make score explanations intuitive for both non-technical users and search engineers.
* **Technical Explanation:** Enhance the "Why this result?" drawer with visual bar charts comparing lexical BM25 vs. semantic cosine contributions, highlighting matched token positions, and visualizing the RRF reciprocal rank fusion math:
  $$\text{RRF}(d) = \frac{1}{60 + r_{\text{lex}}} + \frac{1}{60 + r_{\text{sem}}}$$
* **Components Affected:** `frontend/src/components/RankingExplanation.jsx`.
* **Dependencies:** None.
* **Implementation Scope:** Interactive visual drawer enhancements.
* **Deliverables:** Upgraded `RankingExplanation.jsx` with graphic contribution meters.
* **Testing Requirements:** Visual rendering tests across all three retrieval modes.
* **Acceptance Criteria:** Score breakdown is visually intuitive with clear mathematical transparency.
* **Complexity:** Medium.

#### Task 14.4: Web Accessibility (WCAG 2.1 AA) & Cross-Device Responsive Layout Audit
* **Objective:** Ensure SearchForge meets accessibility standards and looks flawless on mobile, tablet, and desktop.
* **Technical Explanation:** Audit color contrast ratios (guaranteeing $\ge 4.5:1$ on text), ensure full keyboard navigability (Tab, Enter, Escape, Arrow keys for autocomplete), add ARIA live regions for search updates, and tune mobile CSS breakpoints ($< 640\text{px}$).
* **Components Affected:** `frontend/src/`.
* **Dependencies:** None.
* **Implementation Scope:** Accessibility attributes, responsive CSS media queries.
* **Deliverables:** WCAG AA certified frontend and responsive design.
* **Testing Requirements:** Automated Lighthouse accessibility audit score $\ge 95$.
* **Acceptance Criteria:** Lighthouse Accessibility score $\ge 95$; complete keyboard-only operability.
* **Complexity:** Medium.

#### Task 14.5: Master README Redesign & System Visual Assets Creation
* **Objective:** Transform the GitHub README into a premier portfolio showcase.
* **Technical Explanation:** Structure the README into an executive 3-minute read followed by technical deep-dives:
  - Hero banner with live interactive site links.
  - Architecture-at-a-glance diagram.
  - 30-second Quickstart snippet.
  - Core algorithms breakdown (BM25, WordPiece, ONNX, RRF, Lifecycle).
  - Real-world crawl experiment metrics table.
  - Verified test matrix badge (497+ passing tests).
* **Components Affected:** `README.md`, `docs/assets/`.
* **Dependencies:** Tasks 14.1–14.4.
* **Implementation Scope:** Documentation and vector visual redesign.
* **Deliverables:** Redesigned `README.md` and updated vector illustrations.
* **Testing Requirements:** Markdown rendering check and link verification.
* **Acceptance Criteria:** README provides clear executive and technical communication within 3 minutes of reading.
* **Complexity:** Low.

#### Task 14.6: Interactive Demonstration Dataset & Video Walkthrough Guide
* **Objective:** Package a rich multi-domain demonstration corpus and author a self-guided demo script.
* **Technical Explanation:** Curate a multi-domain demo dataset (Wikipedia excerpts across Computing, Science, History, Architecture) auto-seeded in dev mode. Author a step-by-step walkthrough script (`docs/DEMO_WALKTHROUGH.md`) demonstrating lexical precision, vocabulary mismatch resolution via semantic search, and RRF hybrid consensus.
* **Components Affected:** `backend/src/main/resources/dev-data/`, `docs/DEMO_WALKTHROUGH.md`.
* **Dependencies:** None.
* **Implementation Scope:** Demonstration dataset curation and walkthrough documentation.
* **Deliverables:** `DEMO_WALKTHROUGH.md` and pre-indexed demonstration corpus.
* **Testing Requirements:** Step-by-step validation of demo script queries.
* **Acceptance Criteria:** A visitor can follow the walkthrough and observe every search capability in $< 3$ minutes.
* **Complexity:** Low.

#### Task 14.7: Staff-Engineer Technical Portfolio & Interview Architecture Dossier
* **Objective:** Create an authoritative technical interview dossier detailing all design trade-offs and engineering triumphs.
* **Technical Explanation:** Author `docs/portfolio/STAFF_ENGINEER_DOSSIER.md` answering critical architectural interview questions:
  1. *Why build an inverted index and BM25 from scratch instead of using Lucene?*
  2. *How did dynamic tensor allocation reduce ONNX CPU latency by 265x?*
  3. *Why choose Reciprocal Rank Fusion over linear score blending?*
  4. *How does the staged two-phase transactional coordinator guarantee index consistency?*
  5. *What are the hard scaling boundaries of the system and how would you shard it?*
* **Components Affected:** `docs/portfolio/STAFF_ENGINEER_DOSSIER.md`.
* **Dependencies:** All previous phases.
* **Implementation Scope:** Engineering dossier and portfolio presentation guide.
* **Deliverables:** `STAFF_ENGINEER_DOSSIER.md` and resume project summary snippets.
* **Testing Requirements:** Technical review and accuracy verification against implementation.
* **Acceptance Criteria:** Comprehensive, staff-engineer level architectural defense ready for technical interviews.
* **Complexity:** Medium.

### 9.4 Phase Boundaries
* **In Scope:** UI polish, loading skeletons, empty states, accessibility (WCAG AA), responsive CSS, master README redesign, demo dataset, interview architecture dossier.
* **Out of Scope:** New backend algorithms, new retrieval models, framework migrations.

### 9.5 Completion Criteria
1. Frontend achieves Lighthouse Accessibility score $\ge 95$ with verified keyboard navigation.
2. README communicates architecture, benchmarks, and quickstart clearly in $< 3$ minutes.
3. Interactive demo walkthrough script tested and published.
4. Staff-Engineer Interview Dossier published in `docs/portfolio/`.

### 9.6 Risks
* *Scope Creep:* Temptation to rewrite the frontend with heavy component libraries (e.g. Tailwind, Material-UI).
* *Mitigation:* Preserve the existing zero-dependency custom-properties design system; focus strictly on polish, accessibility, and micro-interactions.

---

## 10. Master Task Summary & Complexity Breakdown

| Phase | Phase Name | Focus Area | Proposed Tasks | Estimated Effort |
| :---: | :--- | :--- | :---: | :---: |
| **Phase 9** | Concurrency & Reliability | Thread-safety, locking, race conditions, crash recovery | **9 Tasks** | High |
| **Phase 10** | Performance & Scalability | Profiling, latency percentiles, postings compression, ANN study | **8 Tasks** | High |
| **Phase 11** | Security & API Hardening | SSRF, DNS rebinding, crawler network security, rate limiting, auth | **8 Tasks** | Medium |
| **Phase 12** | Search Quality Evaluation | 100+ query dataset, graded ground truth, RRF tuning, ablation study | **7 Tasks** | Medium |
| **Phase 13** | Production Deployment & CI/CD | Multi-stage Docker, Compose topology, Flyway migrations, runbooks | **7 Tasks** | Medium |
| **Phase 14** | Product Polish & Portfolio | UI skeletons, WCAG AA accessibility, README, interview dossier | **7 Tasks** | Medium |
| **TOTAL** | **Phases 9 Through 14** | **Production & Portfolio Readiness** | **46 Tasks** | **Enterprise Grade** |

---

## 11. Final SearchForge Release Definition (v2.0 Production & Portfolio Ready)

Upon completion of Phase 14, SearchForge will satisfy the definition of a **Tier-1 Engineering Portfolio Project**:

1. **First-Principles Authenticity:** Built 100% in pure Java 21, Spring Boot, and React without relying on Lucene, Elasticsearch, or external vector databases.
2. **Dual-Pathway Hybrid Retrieval:** High-precision Okapi BM25 coupled with local neural ONNX transformer embeddings (`all-MiniLM-L6-v2`) blended via Reciprocal Rank Fusion ($< 2.5\text{ ms}$ latency).
3. **Data Integrity & Self-Healing:** 10-state lifecycle machine, staged transactions, residual-free deletion, and automatic crash repair.
4. **Concurrency & Thread Safety:** Verified under 50+ virtual threads with zero race conditions, deadlocks, or split-brain states.
5. **Hardened Security:** Built-in SSRF defense, private network IP filtering, DNS rebinding guards, token-bucket rate limiting, and API key authentication.
6. **Empirically Evaluated Quality:** 100+ multi-intent queries with 5-point graded ground truth demonstrating statistically superior hybrid search results.
7. **Production Ready:** Multi-stage Docker containers, automated GitHub Actions CI/CD, Flyway database migrations, and disaster recovery runbooks.
8. **Showcase Presentation:** Live documentation website on GitHub Pages, accessible WCAG AA frontend, visual README, and Staff-Engineer Interview Dossier.

---

*End of Master Roadmap Specification. Awaiting user approval and directive to initiate Phase 9.*
