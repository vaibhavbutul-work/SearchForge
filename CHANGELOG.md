# SearchForge Changelog

All notable changes to the SearchForge search engine project are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

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
