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
