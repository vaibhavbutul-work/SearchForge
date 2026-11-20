# SearchForge Changelog

All notable changes to the SearchForge search engine project are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

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
