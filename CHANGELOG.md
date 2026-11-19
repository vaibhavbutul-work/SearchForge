# SearchForge Changelog

All notable changes to the SearchForge search engine project are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

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
