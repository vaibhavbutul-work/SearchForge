/**
 * SearchForge Architecture Explorer
 * Clickable interactive architecture component inspector.
 */

const ARCH_SUBSYSTEMS = {
  "crawler": {
    name: "Web Crawler Subsystem",
    package: "com.searchforge.crawler",
    responsibility: "Autonomous, polite web ingestion using Java 21 Project Loom Virtual Threads, RFC 9309 compliance, and SHA-256 deduplication.",
    classes: [
      "CrawlEngine: Supervisor orchestrating virtual worker threads",
      "UrlFrontier: Lock-free ConcurrentLinkedQueue enforcing BFS depth & allowlists",
      "RobotsTxtService: RFC 9309 parser with longest-match evaluation & 24h cache",
      "PolitenessRateLimiter: Per-domain synchronized lock maps for 1000ms throttles",
      "DocumentFetcher: Jsoup HTTP client with 2MB ceiling & binary guards",
      "HtmlParser: DOM noise tag stripper & SHA-256 text fingerprint generator"
    ],
    inputs: "Seed URLs, allowed domains, max pages, max depth",
    outputs: "CrawledDocument entity saved to PostgreSQL table crawled_documents",
    patterns: "Supervisor Pattern, Producer-Consumer Queue, Lock-Free Concurrency"
  },
  "indexer": {
    name: "Linguistic Processing & Indexing",
    package: "com.searchforge.indexer",
    responsibility: "Transforms raw text into normalized, position-aware inverted postings with linguistic stemming.",
    classes: [
      "DefaultIndexingEngine: Batch chunked corpus synchronizer",
      "StandardTokenizer: Whitespace/punctuation token stream generator",
      "TextNormalizer: Unicode NFD decomposition & diacritical mark stripper",
      "StopWordFilter: 130+ stop-word filter preserving positional offsets",
      "PorterStemmer: Martin Porter's 1980 5-step morphological cascade",
      "InvertedIndex: Dual-tier dictionary (ConcurrentHashMap in RAM + PostgreSQL)"
    ],
    inputs: "Unindexed CrawledDocument records",
    outputs: "Term, TermPosting (with positions array), IndexedDocumentMetadata",
    patterns: "Pipe-and-Filter Pattern, Two-Tier Storage, Stateless Pipeline"
  },
  "retrieval": {
    name: "Boolean & Positional Query Engine",
    package: "com.searchforge.indexer.retrieval",
    responsibility: "Evaluates Boolean query ASTs and executes positional phrase proximity matching.",
    classes: [
      "BooleanQueryLexer: Tokenizes query strings into operator tokens",
      "BooleanQueryParser: Recursive-descent AST parser (AND > OR > NOT)",
      "BooleanRetrievalEngine: Postings evaluator applying linear two-pointer merges",
      "PositionalIndexSearcher: Checks phrase token adjacency (pos2 = pos1 + 1)",
      "PositiveTermExtractor: AST visitor isolating affirmative terms for BM25"
    ],
    inputs: "Query expressions: e.g. '(java OR python) AND microservices', '\"virtual threads\"'",
    outputs: "Candidate document IDs with matched phrase occurrence frequencies",
    patterns: "Abstract Syntax Tree (AST), Visitor Pattern, Recursive Descent"
  },
  "ranking": {
    name: "BM25 Scoring & Relevance Engine",
    package: "com.searchforge.ranking",
    responsibility: "Calculates Robertson-Spärck Jones IDF, applies BM25 saturation, and ranks candidates via bounded Min-Heap.",
    classes: [
      "DefaultRankedRetrievalEngine: TAAT union generator with PriorityQueue Top-K accumulator",
      "Bm25Scorer: Document-level multi-term score aggregator",
      "Bm25TermScorer: Asymptotic saturation (k1=1.2) & length penalty (b=0.75)",
      "Bm25IdfCalculator: Non-negative Robertson-Spärck Jones IDF calculator",
      "DefaultRankedBooleanQueryEngine: Hybrid Boolean AST + BM25 ranking engine",
      "RelevanceMetricsCalculator: IR benchmark suite (NDCG@K, MRR, P@K, R@K)"
    ],
    inputs: "Candidate document IDs, term frequencies, collection stats (N, L_avg)",
    outputs: "RankedSearchResult list sorted descending by score in sub-millisecond latency",
    patterns: "Strategy Pattern, Min-Heap Bounded Queue, Mathematical Modelling"
  },
  "semantic": {
    name: "Dense Semantic & Hybrid Search Subsystem",
    package: "com.searchforge.semantic, com.searchforge.ranking.fusion",
    responsibility: "Executes local ONNX transformer inference (all-MiniLM-L6-v2), subword WordPiece tokenization, L2 unit normalization, and scale-invariant Reciprocal Rank Fusion.",
    classes: [
      "OnnxEmbeddingModel: Microsoft ONNX Runtime Java bindings with dynamic sequence tensor allocation",
      "WordPieceTokenizer: Pure Java BERT subword segmenter loading official 30,522-token vocab.txt",
      "QueryEmbeddingCache: Thread-safe bounded LRU cache (sub-0.1ms repeat query latency)",
      "DocumentEmbeddingService: High-throughput batch vector generator & compatibility validator",
      "ReciprocalRankFusionEngine: Rank-based score blending: RRF(d) = Σ 1 / (60 + r_m(d))",
      "InMemoryVectorStorage: Thread-safe cosine similarity vector index with IEEE 754 serialization",
      "HybridSearchEngine: Orchestrates parallel lexical TAAT and dense semantic vector scans"
    ],
    inputs: "Natural language queries, candidate BM25 hits, stored 384-D document vectors",
    outputs: "384-dimensional dense float vectors, cosine similarity scores, unified hybrid ranked SERP",
    patterns: "Dual-Pathway Architecture, Rank-Based Fusion, Dynamic Tensor Sizing, LRU Caching"
  },
  "api": {
    name: "REST API & Actuator Telemetry",
    package: "com.searchforge.ranking.controller, com.searchforge.indexer.controller, com.searchforge.crawler.controller",
    responsibility: "Exposes administrative control, corpus synchronization, health probes, and ranked search endpoints with score explainability.",
    classes: [
      "SearchController: /api/v1/search, /api/v1/search/ranked-boolean, /api/v1/search/filtered",
      "IndexController: /api/v1/index/build, /rebuild, /warmup, /stats",
      "CrawlerController: /api/v1/crawler/start, /stop, /reset, /status, /documents",
      "HealthController: Actuator liveness and readiness probe aggregator"
    ],
    inputs: "HTTP GET/POST requests with JSON payloads and query parameters",
    outputs: "JSON responses with term explainability breakdowns",
    patterns: "Controller-Service-Repository, RESTful Resource Modeling"
  },
  "frontend": {
    name: "React 18 / Vite 6 Web Application",
    package: "frontend/src (React 18, React Router v6, Vite 6)",
    responsibility: "Delivers a fast search experience with live query persistence, BM25 score transparency, search mode dispatching, and WCAG AA accessibility.",
    classes: [
      "HomePage: Clean search portal with curiosity chips & auto-focus",
      "SearchPage: Full SERP coordinator managing query lifecycle & Top-K",
      "SearchResultItem: Domain breadcrumbs, highlighted snippets & copy link",
      "RankingExplanation: Inline BM25 score breakdown drawer (TF, RSJ IDF, S(tf))",
      "SearchModeSelector: Mode A (BM25), Mode B (Boolean AST), Mode C (Filtered)",
      "useSearchHistory & useGlobalShortcuts: Keyboard '/' hotkey & localStorage history"
    ],
    inputs: "User keystrokes, URL search params (?q=...&mode=...&limit=...)",
    outputs: "Real-time interactive SERP, math score breakdowns, accessible DOM",
    patterns: "Component-Driven Architecture, Custom Hooks, Reactive URL State Synchronization"
  },
  "lifecycle": {
    name: "Index Lifecycle & Recovery Engine",
    package: "com.searchforge.lifecycle",
    responsibility: "Guarantees cross-layer consistency across storage, lexical indexes, statistics, and ONNX embeddings using a 10-state machine and self-healing repair.",
    classes: [
      "DocumentLifecycleStateMachine: Validated 10-state governance with search isolation",
      "DocumentChangeDetector: RFC 3986 URL normalizer & SHA-256 content change classifier",
      "TransactionalIndexingCoordinator: Staged Two-Phase executor (Stage 1 outside DB locks)",
      "IncrementalIndexUpdateEngine: Single-document postings replacement & df/ctf recalibration",
      "DocumentDeletionService: Soft-locking & residual-free lexical and vector purging",
      "DocumentEmbeddingConsistencyValidator: Evaluates CURRENT, OUTDATED, MISSING, and INCOMPATIBLE embeddings",
      "IndexIntegrityChecker: Non-destructive cross-layer reconciliation auditor",
      "IndexRepairService: Self-healing repair engine (DRY_RUN & REPAIR modes)",
      "DocumentRecoveryService: Exponential backoff retry with active version preservation"
    ],
    inputs: "CrawledDocument state transitions, content changes, failure signals, admin audit requests",
    outputs: "Guaranteed consistent relational, lexical, and vector indices; IndexIntegrityReport",
    patterns: "Finite State Machine (FSM), Two-Phase Transactional Staging, Self-Healing / Auto-Repair"
  },
  "concurrency": {
    name: "Concurrency & Reliability Engineering (Phase 9)",
    package: "com.searchforge.concurrency",
    responsibility: "Guarantees thread safety, crash resilience, and read-isolated query snapshots under high concurrent read/write loads.",
    classes: [
      "DocumentLockManager: Striped document mutex fencing preventing concurrent update races",
      "IndexQuerySnapshot: Lock-free point-in-time postings reader isolating queries from mutations",
      "ActiveDocumentFilter: Sub-10ns O(1) concurrent set isolating non-ACTIVE documents from search",
      "StartupIndexReconciliationRunner: Automatic crash recovery on Spring ApplicationReadyEvent",
      "ChaosFailureInjector: Deterministic fault simulation (ONNX crashes, memory sync dropouts)",
      "ConcurrencyStressTest: 50-thread multi-operation stress test suite"
    ],
    inputs: "Concurrent HTTP search queries, crawler virtual worker mutations, lifecycle state changes",
    outputs: "Race-free, deadlock-free execution, zero lost updates, 100% crash recovery",
    patterns: "Optimistic Locking (@Version), Lock Striping, Point-in-Time Snapshot, Chaos Engineering"
  },
  "performance": {
    name: "Performance & Scalability Subsystem (Phase 10)",
    package: "com.searchforge.performance, com.searchforge.codec",
    responsibility: "Delivers sub-millisecond search latencies, high-throughput batch ingestion, compressed postings, and zero-sqrt vector dot-product scoring.",
    classes: [
      "Posting: Packed primitive int[] position representation (79.1% memory reduction)",
      "IntArrayListView: Zero-allocation List<Integer> unboxed view adapter",
      "DeltaCodec: Gap sequence encoder/decoder (di = pi - pi-1) for ascending postings",
      "VByteCodec: 7-bit continuation variable-byte codec (87.5% compression, 101.3M ints/s)",
      "ExactVectorSimilarityEngine: Unit L2 normalized dot-product vector search with zero square roots",
      "VectorScalabilityEvaluator: Empirical N=100..10,000 vector benchmark and ANN threshold model",
      "PerformanceRegressionTest: Automated 5-gate continuous performance regression test suite"
    ],
    inputs: "Lexical queries, raw posting arrays, 384-D dense vectors, batch document collections",
    outputs: "0.036ms lexical search, 185 docs/s ingestion, compressed byte buffers, 8.54ms 10k vector P95",
    patterns: "Zero-Allocation Primitives, Gap & Variable-Byte Compression, SIMD Math, Automated Quality Gates"
  },
  "security": {
    name: "Security Engineering & API Hardening (Phase 11)",
    package: "com.searchforge.security, com.searchforge.crawler.security",
    responsibility: "Guards against unauthorized administrative access, SSRF, DoS query floods, timing attacks, and information disclosure across 7 defense-in-depth layers.",
    classes: [
      "RateLimitingFilter: Lock-free monotonic TokenBucket filter (SEARCH: 60/m, ADMIN: 10/m) at HIGHEST_PRECEDENCE + 5",
      "ApiKeyAuthenticationFilter: Constant-time byte equality auth (MessageDigest.isEqual) at HIGHEST_PRECEDENCE + 10",
      "SafeDnsResolver: Pre-flight multi-homed DNS inspector enforcing web schemes and standard ports",
      "IpRangeValidator: Network sandbox blocking loopback, RFC 1918 private subnets, and cloud metadata (169.254.169.254)",
      "GlobalExceptionHandler: RFC 7807 problem details handler masking SQL and internal schema details",
      "SecurityStartupValidator: ApplicationReady pre-flight validator halting boots on default secrets",
      "SecurityRegressionSuiteTest: Automated 6-gate continuous regression test suite"
    ],
    inputs: "Inbound HTTP headers (X-API-KEY, X-Forwarded-For), crawl seed URLs, query parameters",
    outputs: "Sanitized responses, HTTP 401/429 status codes, RFC 7807 ApiErrorResponse, safe outbound HTTP connections",
    patterns: "Filter Chain Pattern, Token Bucket Algorithm, Defense-in-Depth, Constant-Time Comparison"
  },
  "evaluation": {
    name: "Search Quality & Relevance Engineering (Phase 12)",
    package: "com.searchforge.ranking.evaluation",
    responsibility: "Measures, optimizes, and guards search relevance using formal IR metrics, holdout splits, and automated regression quality gates.",
    classes: [
      "SearchForgeEvaluationDataset: 120-document corpus and 48-query benchmark across 8 archetypes",
      "RelevanceMetricsCalculator: Precision@K, Recall@K, MAP, MRR, and NDCG@K implementations",
      "EvaluationMetricsAggregator: Macro-averaged summaries, category breakdowns, and split analytics",
      "HybridRelevanceEvaluator: 3-way evaluation harness comparing BM25, Dense Semantic, and Hybrid RRF",
      "RankingTuningExperimentRunner: 18-trial parameter grid search with blind holdout EVAL validation",
      "RelevanceLatencyProfiler: High-precision sub-component latency, throughput, and Pareto profiler",
      "SearchQualityRegressionSuite: Automated 5-gate relevance regression harness with mutation testing",
      "SearchForgeEvaluationReportRunner: Master publication-grade evaluation summary generator"
    ],
    inputs: "48 benchmark queries across 8 archetypes, 120 corpus documents, graded relevance judgments [0, 3]",
    outputs: "0.9303 NDCG@5, 0.9448 NDCG@10, 0.9792 MRR, 2.55ms P95 latency, 5 passing regression gates",
    patterns: "Triangulated Evaluation, Reciprocal Rank Fusion, Holdout Generalization, Mutation Fault Injection"
  }
};

class ArchitectureExplorer {
  constructor() {
    this.selectedSubsystem = "ranking";
    this.initDOM();
    this.attachEvents();
    this.render();
  }

  initDOM() {
    this.cards = document.querySelectorAll(".arch-card");
    this.detailModal = document.getElementById("archDetailModal");
    this.modalTitle = document.getElementById("archModalTitle");
    this.modalPackage = document.getElementById("archModalPackage");
    this.modalResponsibility = document.getElementById("archModalResponsibility");
    this.modalClassesList = document.getElementById("archModalClassesList");
    this.modalInputs = document.getElementById("archModalInputs");
    this.modalOutputs = document.getElementById("archModalOutputs");
    this.modalPatterns = document.getElementById("archModalPatterns");
  }

  attachEvents() {
    if (this.cards) {
      this.cards.forEach(card => {
        card.addEventListener("click", () => {
          const sysKey = card.dataset.subsystem;
          if (sysKey && ARCH_SUBSYSTEMS[sysKey]) {
            this.selectedSubsystem = sysKey;
            this.cards.forEach(c => c.classList.remove("active"));
            card.classList.add("active");
            this.render();
          }
        });
      });
    }
  }

  render() {
    const data = ARCH_SUBSYSTEMS[this.selectedSubsystem];
    if (!data) return;

    if (this.detailModal) this.detailModal.classList.add("visible");
    if (this.modalTitle) this.modalTitle.textContent = data.name;
    if (this.modalPackage) this.modalPackage.textContent = `Package: ${data.package}`;
    if (this.modalResponsibility) this.modalResponsibility.textContent = data.responsibility;

    if (this.modalClassesList) {
      this.modalClassesList.innerHTML = "";
      data.classes.forEach(cls => {
        const li = document.createElement("li");
        li.style.marginBottom = "6px";
        li.style.fontFamily = "monospace";
        li.style.fontSize = "12px";
        li.style.color = "#38bdf8";
        li.textContent = cls;
        this.modalClassesList.appendChild(li);
      });
    }

    if (this.modalInputs) this.modalInputs.textContent = data.inputs;
    if (this.modalOutputs) this.modalOutputs.textContent = data.outputs;
    if (this.modalPatterns) this.modalPatterns.textContent = data.patterns;
  }
}

window.ArchitectureExplorer = ArchitectureExplorer;
