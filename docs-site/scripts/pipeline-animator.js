/**
 * SearchForge Pipeline Animator
 * Controls the 12-stage animated search engine data-flow pipeline.
 */

const PIPELINE_STAGES = [
  {
    id: 1,
    name: "Web Sources",
    badge: "Public Internet",
    classes: "Jsoup, HTTP/HTTPS Protocol",
    summary: "HTTP endpoints on public web servers containing HTML documents.",
    input: "https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html",
    output: "Raw HTTP 200 Response (text/html, 48 KB)"
  },
  {
    id: 2,
    name: "Web Crawler",
    badge: "Project Loom Virtual Threads",
    classes: "CrawlEngine, UrlFrontier, RobotsTxtService, PolitenessRateLimiter",
    summary: "Evaluates robots.txt compliance, enforces 1000ms domain politeness delays, and downloads page safely via DocumentFetcher.",
    input: "URL Task from FIFO UrlFrontier (Depth=0)",
    output: "FetchedDocument(url, statusCode=200, rawHtml, timestamp)"
  },
  {
    id: 3,
    name: "HTML Parsing",
    badge: "DOM Noise Stripping",
    classes: "HtmlParser (Jsoup Selector Cascade)",
    summary: "Removes boilerplate elements (script, style, nav, footer, aside, iframe, noscript) and extracts title and clean body text.",
    input: "Raw HTML DOM with scripts and styles",
    output: "Clean Text: 'Virtual threads are lightweight threads that reduce...'"
  },
  {
    id: 4,
    name: "Content Hash Deduplication",
    badge: "SHA-256 Fingerprint",
    classes: "HtmlParser.computeSha256(), CrawledDocumentRepository",
    summary: "Generates a 64-character SHA-256 hash of the cleaned text to detect and discard duplicate or redirected content.",
    input: "Clean text body",
    output: "ContentHash: '8f4c2e1b...9a0d' (Checked against repository.existsByContentHash)"
  },
  {
    id: 5,
    name: "Tokenization",
    badge: "Position Preservation",
    classes: "StandardTokenizer",
    summary: "Splits text on whitespace and punctuation into sequential token streams while recording exact token offset positions.",
    input: "'Virtual threads are lightweight threads...'",
    output: "Tokens: ['Virtual'@0, 'threads'@1, 'are'@2, 'lightweight'@3, 'threads'@4]"
  },
  {
    id: 6,
    name: "Unicode Normalization",
    badge: "NFD Decomposition",
    classes: "TextNormalizer",
    summary: "Decomposes accented characters into base letters and diacritical marks, stripping non-ASCII accents.",
    input: "Tokens with diacritics / casing",
    output: "Normalized tokens: ['virtual'@0, 'threads'@1, 'are'@2, 'lightweight'@3]"
  },
  {
    id: 7,
    name: "Stop-Word Filtering",
    badge: "Positional Offset Invariant",
    classes: "StopWordFilter (130+ Stop Words)",
    summary: "Filters out high-frequency grammatical noise words (e.g. 'are', 'the', 'in') while preserving positional counters for exact phrase matches.",
    input: "['virtual'@0, 'threads'@1, 'are'@2, 'lightweight'@3]",
    output: "Filtered: ['virtual'@0, 'threads'@1, 'lightweight'@3] (Notice gap at @2)"
  },
  {
    id: 8,
    name: "Porter Stemming",
    badge: "5-Step Morphological Cascade",
    classes: "PorterStemmer (Stateless Cascade)",
    summary: "Reduces words to their grammatical root stem using Martin Porter's 1980 5-step morphological cascade.",
    input: "'threads' -> 'thread', 'indexing' -> 'index', 'services' -> 'servic'",
    output: "Stemmed: ['virtual'@0, 'thread'@1, 'lightweight'@3]"
  },
  {
    id: 9,
    name: "Inverted Indexing",
    badge: "Dual-Tier Postings Store",
    classes: "InvertedIndex (ConcurrentHashMap), TermPostingRepository",
    summary: "Records postings in high-speed RAM dictionary: term -> List of (DocId, TF, Positions).",
    input: "Term: 'thread', DocId: 7, Position: 1",
    output: "RAM Postings: thread -> [Doc 7 (TF=273, pos=[1, 14, 28...]), Doc 12 (TF=680)]"
  },
  {
    id: 10,
    name: "Query AST Processing",
    badge: "Recursive-Descent Parser",
    classes: "BooleanQueryLexer, BooleanQueryParser, PositiveTermExtractor",
    summary: "Parses user query into an Abstract Syntax Tree (AST) handling operators (AND, OR, NOT) and phrase quotes.",
    input: "Query: 'java virtual threads'",
    output: "AST: OR(TermNode('java'), TermNode('virtual'), TermNode('thread'))"
  },
  {
    id: 11,
    name: "BM25 Scoring",
    badge: "Robertson-Spärck Jones IDF",
    classes: "Bm25Scorer, Bm25TermScorer, Bm25IdfCalculator",
    summary: "Evaluates Robertson-Spärck Jones IDF and calculates asymptotic term frequency saturation with length normalization.",
    input: "Candidates: Doc 7, Doc 12, Doc 6; Terms: 'virtual', 'thread'",
    output: "Doc 7 Score = 4.2694 (virtual: 2.4112 + thread: 1.3284 + java: 0.5299)"
  },
  {
    id: 12,
    name: "Top-K Ranking & Hits",
    badge: "Bounded Min-Heap Queue",
    classes: "DefaultRankedRetrievalEngine, SearchController",
    summary: "Accumulates top-K highest scoring documents using a Min-Heap priority queue in O(C log K) time and returns JSON response.",
    input: "Top 3 Scored Candidates",
    output: "Rank 1: Doc 7 (Score: 4.2694) | Rank 2: Doc 12 (Score: 4.0915) [< 1 ms]"
  },
  {
    id: 13,
    name: "Frontend SERP & Transparency",
    badge: "React 18 / Vite 6 Web App",
    classes: "SearchPage, SearchResultItem, RankingExplanation",
    summary: "Renders real-time search results with highlighted snippets, mode selector, and expandable mathematical BM25 score transparency drawer.",
    input: "SearchResponseDTO from /api/v1/search (Top-K documents with TermExplanation)",
    output: "Interactive web SERP with 'Why this result? ▾' breakdown and zero external UI bloat"
  }
];

class PipelineAnimator {
  constructor() {
    this.currentStageIndex = 0;
    this.isPlaying = false;
    this.timer = null;
    this.speed = 1800; // ms per step

    this.initDOM();
    this.renderStageNodes();
    this.updateDisplay();
    this.attachEvents();
  }

  initDOM() {
    this.container = document.getElementById("pipelineNodesContainer");
    this.stageBadge = document.getElementById("currentStageBadge");
    this.progressBar = document.getElementById("pipelineProgressFill");
    this.playBtn = document.getElementById("pipelinePlayBtn");
    this.prevBtn = document.getElementById("pipelinePrevBtn");
    this.nextBtn = document.getElementById("pipelineNextBtn");
    this.restartBtn = document.getElementById("pipelineRestartBtn");
    this.speedSelect = document.getElementById("pipelineSpeedSelect");

    this.inspectorStageName = document.getElementById("inspectorStageName");
    this.inspectorClasses = document.getElementById("inspectorClasses");
    this.inspectorSummary = document.getElementById("inspectorSummary");
    this.inspectorInput = document.getElementById("inspectorInput");
    this.inspectorOutput = document.getElementById("inspectorOutput");
  }

  renderStageNodes() {
    if (!this.container) return;
    this.container.innerHTML = "";

    PIPELINE_STAGES.forEach((stage, idx) => {
      const node = document.createElement("div");
      node.className = `stage-node ${idx === 0 ? "active" : ""}`;
      node.dataset.index = idx;
      node.innerHTML = `
        <span class="stage-num">${stage.id < 10 ? '0' + stage.id : stage.id}</span>
        <span class="stage-name">${stage.name}</span>
        <span class="stage-desc-short">${stage.badge}</span>
      `;
      node.addEventListener("click", () => {
        this.pause();
        this.currentStageIndex = idx;
        this.updateDisplay();
      });
      this.container.appendChild(node);
    });
  }

  updateDisplay() {
    const stage = PIPELINE_STAGES[this.currentStageIndex];

    // Update nodes active class
    const nodes = this.container ? this.container.querySelectorAll(".stage-node") : [];
    nodes.forEach((node, idx) => {
      if (idx === this.currentStageIndex) {
        node.classList.add("active");
        node.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
      } else {
        node.classList.remove("active");
      }
    });

    // Update Progress Bar
    const percent = ((this.currentStageIndex + 1) / PIPELINE_STAGES.length) * 100;
    if (this.progressBar) this.progressBar.style.width = `${percent}%`;

    // Update Top Badge
    if (this.stageBadge) {
      this.stageBadge.textContent = `Stage ${stage.id} / ${PIPELINE_STAGES.length}: ${stage.name}`;
    }

    // Update Inspector Data
    if (this.inspectorStageName) this.inspectorStageName.textContent = stage.name;
    if (this.inspectorClasses) this.inspectorClasses.textContent = stage.classes;
    if (this.inspectorSummary) this.inspectorSummary.textContent = stage.summary;
    if (this.inspectorInput) this.inspectorInput.textContent = stage.input;
    if (this.inspectorOutput) this.inspectorOutput.textContent = stage.output;
  }

  next() {
    if (this.currentStageIndex < PIPELINE_STAGES.length - 1) {
      this.currentStageIndex++;
    } else {
      this.currentStageIndex = 0;
    }
    this.updateDisplay();
  }

  prev() {
    if (this.currentStageIndex > 0) {
      this.currentStageIndex--;
    } else {
      this.currentStageIndex = PIPELINE_STAGES.length - 1;
    }
    this.updateDisplay();
  }

  play() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    if (this.playBtn) {
      this.playBtn.innerHTML = `<span>⏸ Pause</span>`;
    }
    this.timer = setInterval(() => {
      this.next();
    }, this.speed);
  }

  pause() {
    this.isPlaying = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.playBtn) {
      this.playBtn.innerHTML = `<span>▶ Play</span>`;
    }
  }

  togglePlay() {
    if (this.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  restart() {
    this.pause();
    this.currentStageIndex = 0;
    this.updateDisplay();
  }

  attachEvents() {
    if (this.playBtn) this.playBtn.addEventListener("click", () => this.togglePlay());
    if (this.nextBtn) this.nextBtn.addEventListener("click", () => { this.pause(); this.next(); });
    if (this.prevBtn) this.prevBtn.addEventListener("click", () => { this.pause(); this.prev(); });
    if (this.restartBtn) this.restartBtn.addEventListener("click", () => this.restart());

    if (this.speedSelect) {
      this.speedSelect.addEventListener("change", (e) => {
        this.speed = parseInt(e.target.value, 10);
        if (this.isPlaying) {
          this.pause();
          this.play();
        }
      });
    }
  }
}

// Export for global portal use
window.PipelineAnimator = PipelineAnimator;
