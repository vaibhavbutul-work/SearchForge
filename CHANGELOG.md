# SearchForge Changelog

All notable changes to the SearchForge search engine project are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

---

## [1.7.0] - Phase 11: Security Engineering, API Hardening & Production Security (2026-11-24)

### Added
- **Constant-Time Administrative Authentication & RBAC:** Implemented `ApiKeyAuthenticationFilter` registered at `Ordered.HIGHEST_PRECEDENCE + 10` intercepting `/api/v1/lifecycle/**`, `/api/v1/index/**`, and `/api/v1/crawler/**` mutating endpoints. Employs `MessageDigest.isEqual` to guarantee constant-time verification against timing side-channel attacks, returning RFC 7807 `401 Unauthorized` responses.
- **Lock-Free Monotonic Token Bucket Rate Limiting:** Built `RateLimitingFilter` at `Ordered.HIGHEST_PRECEDENCE + 5` supporting tiered token bucket quotas: `SEARCH` tier (60 req/min continuous, burst capacity 60) and `ADMIN` tier (10 req/min continuous, burst capacity 10) using `System.nanoTime()` for monotonic clock stability. Emits RFC 6585 `429 Too Many Requests` with `Retry-After: <sec>` and `X-RateLimit-*` headers.
- **Web Crawler SSRF Sandbox & Private IP Defense:** Engineered `IpRangeValidator` and `SafeDnsResolver` performing pre-flight DNS inspections across all resolved IPs, blocking IPv4 loopback (`127.0.0.0/8`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local / cloud metadata (`169.254.169.254`), multicast, and IPv6 unique local. Enforced web schemes (`http`, `https`) and standard ports (80, 443, 8080, 8443).
- **Controlled Redirect Loop & Cycle Trap Defense:** Refactored `DocumentFetcher` with `.followRedirects(false)` and a manual redirect loop capped at 3 hops, re-evaluating `SafeDnsResolver` on every hop and tracking visited URLs in a `Set<String>` to neutralize cyclic redirect tarpits.
- **Strict Input Length Validation & Algorithmic DoS Prevention:** Defensively bounded all public and admin controller parameters ($q \le 500$ chars, autocomplete prefix $\le 100$, spelling suggest $\le 200$, seed URLs $\le 50$, URLs $\le 2048$, page size $\le 100$, page $\ge 0$), completely neutralizing algorithmic DoS on Damerau-Levenshtein spelling correction and ONNX tensor allocation churn.
- **RFC 7807 Error Response Sanitization:** Implemented `GlobalExceptionHandler` and `ApiErrorResponse` converting unexpected runtime exceptions and database constraint violations into sanitized problem details payloads (HTTP 409 Conflict / HTTP 500 Internal Error) without exposing database schema internals or stack traces.
- **Secrets Management, Pre-Flight Startup Validation & Actuator Masking:** Created `SecurityStartupValidator` to prevent production startup with default credentials, implemented secret redacting in `SecurityProperties.toString()` (`sf****od`), and sanitized Spring Boot Actuator endpoints (`env.show-values: never`, `configprops.show-values: never`).
- **Automated Security Regression Suite:** Implemented `SecurityRegressionSuiteTest` with 6 automated end-to-end regression gates validating authorization, constant-time comparison, SSRF sandbox, input length bounds, rate limiting, and SQL exception masking.
- **Automated Testing Suite Milestone:** Added 105 new automated security tests across 8 test suites, bringing the total hermetic test count to **657 tests (644 backend + 13 frontend)** with a 100% pass rate.
- **Production Hardening & Audit Documentation:** Authored comprehensive post-implementation STRIDE audit matrix in `docs/security/PHASE_11_SECURITY_AUDIT_REPORT.md` and operator runbook in `docs/security/PRODUCTION_HARDENING_GUIDE.md`.

---

## [1.6.0] - Phase 10: Performance & Scalability Engineering (2026-11-23)

### Added
- **Packed Primitive Postings Memory Optimization:** Re-architected `Posting.java` internal storage from boxed `List<Integer>` to packed primitive `int[]` with an unmodifiable, lightweight `IntArrayListView`, eliminating 5 heap objects and 272 bytes per posting (**79.1% memory reduction** per posting).
- **Postings Compression Codecs (Delta + VByte):** Built first-principles `DeltaCodec` ($\Delta_i = d_i - d_{i-1}$) and `VByteCodec` (7-bit continuation framing), achieving **87.5% memory savings** on dense postings with decode throughput of **101.3 Million integers/second**.
- **Zero-Allocation Postings Traversal:** Added `forEachPosting(Consumer<Posting>)`, `getPostingsView()`, primitive array projections (`getDocumentIds()`, `getTermFrequencies()`), and zero-allocation binary search without allocating dummy probe objects.
- **Vector Math Optimization ($L_2$ Normalization Equivalence):** Implemented `VectorMath.fastNormalizedDotProduct`, mathematically proving and leveraging $\text{Cosine}(A, B) \equiv A \cdot B$ for unit vectors. Eliminated **66.7% of floating-point multiplications** and **100% of transcendental square roots** per candidate vector with numerical drift $< 1.5 \times 10^{-8}$.
- **ONNX Session Graph Optimization & Warmup:** Configured ONNX Runtime with `OptLevel.ALL_OPT` (constant folding, operator fusion), `ExecutionMode.SEQUENTIAL` (zero inter-op thread pool jitter), and startup JIT/native memory `warmup()`.
- **Concurrent Hybrid Search via Virtual Threads:** Parallelized lexical BM25 and dense semantic retrieval concurrently using Java 21 Virtual Threads (`searchExecutor`), reducing hybrid search latency to $\max(T_{\text{lex}}, T_{\text{sem}}) + T_{\text{RRF}}$ with resilient fallback to lexical BM25 upon semantic subsystem failure.
- **Exact Vector Search Scalability Evaluation:** Conducted empirical scalability profiling across $N \in \{100, 500, 1000, 5000, 10000\}$ 384-D vectors. Proved exact flat scan achieves **$P_{95} = 8.54\text{ ms}$ at $10,000$ scale with 100% recall**, establishing the architectural boundary for future ANN (HNSW) transition at $N^* \approx 25,000 - 50,000$ documents.
- **High-Throughput Batch Indexing:** Implemented JDBC $N+1$ bulk batching via `IndexedTermRepository.batchInsertOrUpdate`, fine-grained term mutex locking (`ConcurrentHashMap<String, Object>`), and token instance caching, achieving burst batch indexing throughput of **$185\text{ docs/second}$**.
- **Automated Performance Regression Suite:** Built `PerformanceRegressionTest.java` enforcing 5 hard performance and invariant gates (Lexical latency $< 2\text{ ms}$, Positional memory $\le 80\text{ B}$, Codec throughput $\ge 30\text{M}$ ints/s, Vector scalability $P_{95} < 10\text{ ms}$, and Hybrid degradation resiliency).
- **Automated Testing Suite Milestone:** Added 31 new performance, memory, compression, scalability, and regression tests, bringing total test count to **552 tests (539 backend + 13 frontend)** with a 100% pass rate.

---

## [1.5.0] - Phase 9: Concurrency, Crash-Safety & High-Throughput Reliability (2026-11-22)

### Added
- **Optimistic Locking & Version Race Protection:** Added JPA `@Version` column (`version_lock`) to `CrawledDocument` and built optimistic locking retry loops with exponential backoff and jitter in `TransactionalIndexingCoordinator`, completely preventing lost updates under concurrent modifications.
- **Fine-Grained Striped Document Lock Fencing:** Created `DocumentLockManager` providing fair, thread-safe mutex fencing per document ID (`ConcurrentHashMap` with atomic ref-counted pruning), ensuring concurrent operations on the same document execute in mutual exclusion while operations on distinct documents proceed in parallel.
- **Atomic Point-in-Time Index Query Snapshots:** Designed and implemented `IndexQuerySnapshot` and atomic postings replacement in `InMemoryInvertedIndex` with `ReentrantReadWriteLock`, guaranteeing lock-free, zero-skew query reads during continuous high-throughput document ingestion.
- **Sub-10ns Search Read-Isolation (`ActiveDocumentFilter`):** Implemented lock-free `ActiveDocumentFilter` backed by `ConcurrentHashMap.newKeySet()` and integrated it into `DefaultRankedRetrievalEngine`, `ExactVectorSimilarityEngine`, and `DefaultRankedBooleanQueryEngine` to instantaneously exclude mutating, deleting, or rolling-back documents prior to candidate scoring.
- **Concurrent Crawler Lease Tracking (`UrlTracker`):** Built atomic `UrlTracker` managing transitions (`DISCOVERED`, `QUEUED`, `IN_FLIGHT`, `PROCESSED`, `RETRY_ELIGIBLE`, `FAILED`) in `UrlFrontier` to guarantee exclusive in-flight leases and prevent duplicate concurrent HTTP requests across Project Loom virtual threads.
- **Startup Crash Reconciliation & Auto-Healing:** Developed `StartupIndexReconciliationRunner` listening to Spring Boot's `ApplicationReadyEvent` to audit persistent database state against RAM structures, pre-warm index caches, and automatically repair crash-induced inconsistencies on restart.
- **Deterministic Chaos Engineering Harness:** Built `ChaosFailureInjector` to simulate real-world failure points (Stage 1 ONNX neural inference crashes, Stage 2 relational transaction drops, and Stage 3 in-memory vector storage exhaustion), verifying zero data corruption and clean rollback quarantine.
- **50-Thread High-Concurrency Stress Suite:** Created `ConcurrencyStressTest` utilizing `CyclicBarrier` and 50 concurrent worker threads (15 indexing, 10 updating, 5 deleting, 20 searching), proving zero deadlocks, zero unhandled thread deaths, and 100% post-stress data integrity.
- **Comprehensive Automated Testing Audit:** Added 24 new concurrency, chaos, and stress tests, bringing total test count to **521 tests (508 backend + 13 frontend)** with 100% pass rate.

---

## [1.4.0] - Phase 8: Index Lifecycle, Data Consistency & Recovery (2026-11-21)

### Added
- **10-State Document Lifecycle State Machine:** Formalized document state governance (`DISCOVERED`, `FETCHED`, `PROCESSING`, `INDEXING`, `EMBEDDING`, `ACTIVE`, `RETRY`, `FAILED`, `DELETING`, `DELETED`) with checked transition validation, terminal state enforcement, and search isolation (`ACTIVE` only).
- **Cryptographic Change Detection & Idempotent Crawling:** Built `DocumentChangeDetector` utilizing canonical URL normalization (RFC 3986) and SHA-256 clean content hashing. Identifies `NEW_DOCUMENT`, `UNCHANGED`, `MODIFIED`, and `DUPLICATE_CONTENT_DIFFERENT_URL`, completely skipping redundant tokenization and neural inference for identical content.
- **Staged Two-Phase Transactional Coordinator:** Implemented `TransactionalIndexingCoordinator` executing CPU-intensive NLP tokenization and ONNX model inference outside database transaction locks (Stage 1), followed by an atomic isolated `@Transactional(isolation = READ_COMMITTED)` relational commit (Stage 2) and post-commit memory synchronization (Stage 3).
- **Atomic Incremental Single-Document Updates:** Built `IncrementalIndexUpdateEngine` supporting single-document updates without full corpus rebuilds, purging old postings, recalibrating $df$/$ctf$, pruning empty vocabulary terms, maintaining $avgdl$, and updating dense vectors.
- **Residual-Free Safe Document Deletion:** Implemented `DocumentDeletionService` with soft-lock (`DELETING`), immediate search exclusion, atomic removal from in-memory inverted and positional indexes, metadata pruning, and removal of vectors from `VectorStorage` and database entities.
- **Cross-Layer Index Reconciliation & Integrity Checker:** Created non-destructive `IndexIntegrityChecker` auditing relational tables, in-memory inverted indexes, positional postings, BM25 corpus metrics, and dense embeddings to detect orphaned or missing postings, model incompatibilities, and statistics drift.
- **Automated Self-Healing Repair Service:** Created `IndexRepairService` supporting `DRY_RUN` and `REPAIR` execution modes to re-index missing active documents, purge orphaned postings and embeddings, regenerate outdated vectors, and re-warm memory state.
- **Exponential Backoff Retry & Recovery System:** Built `DocumentRecoveryService` classifying transient vs permanent failures, computing exponential backoff with jitter, enforcing a 3-retry dead-letter limit, and preserving active document versions during failed update attempts.
- **Lifecycle Administration REST Endpoints:** Added `/api/v1/lifecycle` endpoints for document status (`/status/{id}`), manual reindexing (`/reindex/{id}`), deletion (`/{id}`), integrity audit (`/integrity/check`), automated repair (`/integrity/repair`), retry execution (`/retry`), and operational metrics (`/metrics`).
- **Comprehensive Automated Testing Audit:** Added 76 new tests across unit, MVC slice, and integration suites, bringing total test count to **497 tests (484 backend + 13 frontend)** with 100% pass rate.

---

## [1.3.0] - Phase 7: Hybrid Search & Dense Semantic Retrieval (2026-11-20)

### Added
- **Local Dense Semantic Retrieval via ONNX Runtime:** Integrated `all-MiniLM-L6-v2` dense embedding model running locally on CPU in pure Java with Microsoft ONNX Runtime Java bindings. Zero external vector databases, zero paid APIs.
- **Authentic WordPiece Tokenizer (`vocab.txt`):** Built a zero-dependency BERT-compatible WordPiece tokenizer in Java with 30,522-token vocabulary support, lowercase normalization, punctuation separation, greedy longest-match subword segmentation, and special tokens (`[PAD]=0`, `[UNK]=100`, `[CLS]=101`, `[SEP]=102`, `[MASK]=103`).
- **Dynamic Sequence Length Tensor Allocation:** Replaced static 256-token allocation with dynamic sequence length tensor shapes `[batchSize, actualSequenceLength]`, reducing warmed-up ONNX CPU inference from 625.44 ms to **2.36 ms (265x–336x speedup)**.
- **Attention-Weighted Mean Pooling & L2 Normalization:** Implements mathematical mean pooling masked by active token attention flags ($v = \sum h_i m_i / \sum m_i$) followed by L2 unit normalization, guaranteeing dot-product equality with cosine similarity.
- **Reciprocal Rank Fusion (RRF) Hybrid Search:** Blends lexical BM25 ranking and dense semantic ranking into a unified hybrid score using $RRF(d) = \sum \frac{1}{60 + r_i(d)}$. Achieves superior relevance (+4.8% NDCG@3 vs BM25).
- **Thread-Safe Bounded Query Embedding Cache:** Implemented `QueryEmbeddingCache` with LRU eviction policy, query text normalization, defensive array cloning, and real-time hit/miss metrics, enabling $< 0.1\text{ ms}$ retrieval for repeat queries.
- **Multi-Modal SERP UI Integration:** Added Semantic and Hybrid modes to the React 18 / Vite frontend, featuring hybrid score badges, individual lexical and semantic rank breakdowns, and fallback telemetry.
- **Expanded Benchmark Dataset & Multi-Cutoff Evaluation:** 120 synthetic documents across 10 domains with 35 distractor documents and 40 categorized queries, evaluated on NDCG@3, NDCG@5, MRR, Precision@K, and Recall@K.
- **Comprehensive Test Suite Expansion:** Added 135 new automated tests across backend and frontend, bringing total passing tests to **421 (408 backend + 13 frontend)** with 100% pass rate.

---

## [1.2.0] - Phase 6: Complete Frontend Web Application (2026-11-20)

### Added
- **Modern React + Vite Frontend Application:** Standalone, decoupled web application in `frontend/` powered by React 18, React Router v6, Vite, and modern CSS modules.
- **Hybrid Search Engine UI:** Minimalist, focused homepage with brand wordmark, tagline ("Search. Understand. Discover."), responsive search bar, and example query chips (`Java virtual threads`, `Spring Boot dependency injection`, `PostgreSQL indexing`, `b-tree indexes`).
- **Comprehensive Search Results Interface:** Search results page (SERP) featuring domain breadcrumbs, accessible titles, highlighted snippets with `<mark>` tags, rank badges, BM25 scores, and sticky navigation header.
- **Direct Backend Integration & CORS Configuration:** Centralized `apiClient.js` and `searchService.js` connecting directly to Spring Boot backend (`http://localhost:8080`) with zero mock data. Added secure, restricted CORS configuration (`WebConfig.java`) for local origins.
- **Multi-Modal Retrieval Selector:** Compact mode selector exposing native backend retrieval engines: Mode A (Free-Text BM25), Mode B (Boolean AST `AND`/`OR`/`NOT`), and Mode C (Filtered Search with inline secondary filter input).
- **Ranking Transparency ("Why this result?"):** Interactive expandable explainability drawer deconstructing Robertson-Spärck Jones BM25 scores into raw TF, RSJ IDF, sublinear saturation factor $S(tf)$, and exact positional phrase boosts directly from backend's `TermExplanation` DTO.
- **Local Search History & Keyboard Shortcuts:** Browser `localStorage` search history (deduplicated, capped at 8, clearable), global `/` shortcut to focus search input with input typing protection, and `Escape` dismissal.
- **Top-K Limit Selector & Full URL Query Persistence:** Results-per-page selector (5, 10, 20, 50) directly controlling backend's bounded min-heap limit, with full URL parameter serialization (`/search?q=...&mode=...&limit=...`) ensuring 100% shareable and reloadable state.
- **Multi-Domain Encyclopedia Dev Corpus:** Expanded dev seed dataset (`DevDataLoader.java`) to 24 diverse encyclopedia documents spanning everyday objects (bottles, chairs, tables, books, clocks, cars, computers), animals (dogs, cats), nature, astronomy (galaxies, oceans, trees, sun), and software engineering.
- **Cross-Platform Wrapper Patch:** Enhanced `backend/mvnw.cmd` with automatic `%SystemRoot%\System32\WindowsPowerShell\v1.0` path resolution for PowerShell 7 environments and added root `mvnw.cmd` forwarder.
- **Application Polish & WCAG AA Accessibility:** Skip to main content link (`.skipLink`), enhanced text contrast (5.1:1), visible focus states, one-click document URL clipboard copying, long URL word-break overflow protection, and reduced-motion support.

---

## [1.1.0] - Phase 5: Query Intelligence & Search Experience (2026-11-20)

### Added
- **Weighted Trie Autocomplete Engine:** Custom thread-safe prefix tree backed by `ConcurrentHashMap` with lock-free atomic references (`AtomicReference<AutocompleteTrie>`), corpus popularity scoring ($\text{Score}(t) = \ln(1 + \text{ctf}) \times \sqrt{\text{df}}$), and bounded Min-Heap top-$K$ prefix traversal ($\mathcal{O}(L + M \log K)$). REST endpoint: `/api/v1/search/autocomplete`.
- **Damerau-Levenshtein Spelling Correction:** Optimal String Alignment (OSA) dynamic programming evaluating insertions, deletions, substitutions, and adjacent transpositions. Length-bucket candidate pruning ($[L-2, L+2]$) and Noisy Channel model scoring ($P(C \mid T) \propto \text{ErrorWeight}(d) \times (\ln(1 + \text{ctf}) \times \sqrt{\text{df}} + 1.0)$). REST endpoint: `/api/v1/search/suggest`.
- **Dynamic Sliding-Window Snippets:** Word-boundary window snapping (160 char target), multi-term cluster density scoring ($100 \cdot Q_{\text{unique}} + 10 \cdot Q_{\text{total}} + \frac{100}{1 + \text{Span}}$), and anti-XSS HTML escaping (`HtmlSanitizer`) with safe `<mark>` tags.
- **Directional Synonym Expansion:** Directional synonym graphs (`ONE_WAY` and `BIDIRECTIONAL`) with cycle suppression, stop-word elimination, and query-intent safety bypass for quoted phrases and Boolean operators.
- **Enriched Search Integration:** Enriched `SearchController` returning `didYouMean` suggestions and `highlightedSnippet`s alongside BM25 rankings, with discounted synonym term weighting ($\delta = 0.5$) in `DefaultRankedRetrievalEngine`.
- **Latency Benchmarks & IR Evaluation Suite:** Microsecond latency percentile benchmark suite (`QueryIntelligenceBenchmarkTest`) and IR evaluation suite (`QueryIntelligenceRelevanceEvaluationTest`) confirming sub-90µs query intelligence overhead and 100% precision/recall.
- **Automated Test Suite Expansion:** 273 hermetic tests passing with 0 failures (88 new tests across Phase 5).

---

## [1.0.0] - Phase 4: Scoring, Ranking & Relevance Engine (2026-11-19)

### Added
- **Okapi BM25 Ranking:** Robertson-Spärck Jones non-negative IDF, term saturation ($k_1=1.2$), and document length normalization ($b=0.75$).
- **Term-At-A-Time (TAAT) Retrieval:** In-memory postings union with bounded Min-Heap priority queue Top-K selection.
- **Positional Phrase Boosting:** Precision ranking crediting consecutive phrase matches via positional index intersection.
- **Hybrid Ranked Boolean Search:** Abstract Syntax Tree (AST) Boolean query evaluation coupled with BM25 candidate scoring.
- **Explainability API:** Detailed term scoring breakdowns ($\text{rawTf}$, $\text{IDF}$, $\text{saturation}$, and $\text{termScore}$) via `/api/v1/search?explain=true`.
- **Information Retrieval (IR) Evaluation:** Automated calculation of Precision@K, Recall@K, Mean Reciprocal Rank (MRR), and NDCG@K.
- **Real-World Crawling Experiment:** Crawled 16 live documentation pages across PostgreSQL, Oracle, and Spring; indexed 21,406 tokens; achieved 1.000 MRR and sub-1ms search latency.
- **Automated Test Suite Expansion:** 185 automated tests passing with 0 failures.

---

## [0.3.0] - Phase 3: Inverted Index & Boolean Retrieval (2026-11-19)

### Added
- **Text Analysis Pipeline:** Unicode NFD decomposition, diacritical mark stripping, 130+ stop-word filter with positional preservation.
- **Porter Stemming Algorithm:** Complete 5-step morphological cascade based on Martin Porter's 1980 reduction rules.
- **Dual-Tier Inverted Index:** ConcurrentHashMap dictionary in RAM synchronized with PostgreSQL tables (`indexed_terms`, `term_postings`, `indexed_document_metadata`).
- **Positional Phrase Search:** Linear positional intersection algorithm verifying adjacent word offsets ($O(P_1 + P_2)$).
- **Boolean Query Engine:** Recursive-descent AST parser supporting `AND`, `OR`, `NOT`, and parenthetical grouping with two-pointer linear list merging.
- **120 Automated Tests.**

---

## [0.2.0] - Phase 2: Web Crawler Subsystem (2026-11-18)

### Added
- **Concurrency Architecture:** Java 21 Project Loom Virtual Threads for high-throughput non-blocking crawl workers.
- **URL Frontier:** Lock-free FIFO Breadth-First Search (BFS) queue with depth limiting and domain allowlists.
- **RFC 9309 Compliance:** Robots exclusion protocol parser with longest-match path matching and 24-hour cache invalidation.
- **Politeness Rate Limiting:** Per-domain synchronized lock maps enforcing 1,000ms crawl delay between successive requests.
- **DOM Parsing & Deduplication:** Jsoup boilerplate stripping, outbound link discovery, and SHA-256 cryptographic text fingerprinting.
- **Crawler REST APIs:** `/api/v1/crawler/start`, `/stop`, `/reset`, `/status`, and `/documents`.
- **60 Automated Tests.**

---

## [0.1.0] - Phase 1: Foundation & Backend Architecture (2026-11-17)

### Added
- **Backend Core:** Java 21 LTS and Spring Boot layered architecture.
- **Persistence Layer:** PostgreSQL 16 Alpine configuration via Docker Compose and HikariCP connection pool.
- **Testing Infrastructure:** Hermetic in-memory H2 database configuration and Spring Boot context verification tests.
- **Health Probes:** Spring Boot Actuator liveness and readiness endpoints.
