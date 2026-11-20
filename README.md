<p align="center">
  <img src="docs/assets/searchforge-hero-banner.svg" alt="SearchForge Hero Banner" width="100%">
</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot"></a>
  <a href="https://www.postgresql.org/"><img src="https://img.shields.io/badge/PostgreSQL-16_Alpine-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
  <a href="https://openjdk.org/projects/loom/"><img src="https://img.shields.io/badge/Concurrency-Project_Loom_Virtual_Threads-4B0082?style=for-the-badge" alt="Project Loom"></a>
  <a href="https://github.com/vaibhavbutul-work/SearchForge/actions"><img src="https://img.shields.io/badge/Tests-273%20Passing%20(100%25)-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white" alt="273 Tests Passing"></a>
  <a href="#-information-retrieval-evaluation-suite"><img src="https://img.shields.io/badge/Evaluation-MRR%201.000%20%7C%20NDCG@3%201.000-00E5FF?style=for-the-badge" alt="Evaluation Metrics"></a>
</p>

<p align="center">
  <a href="https://vaibhavbutul-work.github.io/SearchForge/"><strong>🌐 Launch Live Interactive Documentation Website</strong></a> •
  <a href="#-end-to-end-search-pipeline"><strong>Search Pipeline</strong></a> •
  <a href="#-subsystem-architecture"><strong>System Architecture</strong></a> •
  <a href="#-okapi-bm25-relevance-engine"><strong>BM25 Math</strong></a> •
  <a href="#-query-intelligence--search-experience-phase-5"><strong>Query Intelligence</strong></a> •
  <a href="#-verified-real-world-experiment"><strong>Real-World Benchmark</strong></a> •
  <a href="#-rest-api-reference"><strong>API Reference</strong></a> •
  <a href="#-quickstart-guide"><strong>Quickstart</strong></a>
</p>

---

> **"What I cannot create, I do not understand." — Richard Feynman**  
> 
> Rather than treating search as a black-box library dependency (like Lucene, Solr, or Elasticsearch), **SearchForge** implements the entire algorithmic and systems stack of an industrial search engine **from first principles in modern Java 21 and Spring Boot**.
> 
> From non-blocking polite web crawling on Project Loom virtual threads to morphological Porter stemming, lock-free dual-tier inverted indexing, recursive-descent Boolean AST resolution, Robertson-Spärck Jones Okapi BM25 probabilistic scoring, positional phrase boosting, real-time Weighted Trie prefix autocomplete, Damerau-Levenshtein spelling error correction with noisy channel scoring, dynamic sliding-window snippet extraction with anti-XSS highlighting, directional synonym query expansion, and automated Information Retrieval (IR) evaluation suites (NDCG, MRR, P@K, R@K).

---

<p align="center">
  <img src="docs/assets/metrics-badge-summary.svg" alt="SearchForge Production Metrics Dashboard" width="100%">
</p>

---

## 🌐 Interactive Documentation & Architecture Website

SearchForge includes a dedicated, zero-dependency interactive documentation web portal live at [vaibhavbutul-work.github.io/SearchForge](https://vaibhavbutul-work.github.io/SearchForge/).

<p align="center">
  <a href="https://vaibhavbutul-work.github.io/SearchForge/">
    <img src="https://img.shields.io/badge/Live_Interactive_Website-Visit_Portal-00E5FF?style=for-the-badge&logo=html5&logoColor=black" alt="Open Live Website">
  </a>
</p>

### Built-in Interactive Simulators:
1. **Animated Search Pipeline Simulator:** Visualizes document ingestion through crawling, HTML parsing, tokenization, stemming, postings construction, TAAT scoring, priority queue ranking, and results presentation with Play/Pause, step-by-step stepping, and speed controls.
2. **Interactive BM25 Saturation Curve Graph:** Live canvas renderer comparing Okapi BM25 asymptotic saturation against linear TF-IDF. Adjust $k_1$, $b$, term frequency ($tf$), and document length ($|d|$) in real time.
3. **Inverted Index Vocabulary Explorer:** Clickable dictionary cloud displaying real vocabulary terms, document frequency ($df$), corpus term frequency ($ctf$), document IDs, and exact word position offset arrays.
4. **BFS Web Crawler Simulator:** Demonstrates frontier FIFO dequeuing, host normalization, RFC 9309 `robots.txt` path evaluation, per-domain concurrency locks, and politeness delay timing.
5. **Top-K Bounded Priority Queue Visualizer:** Shows the bounded min-heap maintaining the top-$K$ candidates in $\mathcal{O}(C \log K)$ runtime without full collection sorting.
6. **Component Architecture Inspector:** Interactive subsystem diagram revealing responsibilities, input/output data contracts, and production Java classes upon click.
7. **Query Intelligence & Suggestion Inspector:** Demonstrates real-time Trie prefix completion, Damerau-Levenshtein OSA edit-distance alignment, sliding-window snippet extraction with anti-XSS protection, and directional synonym expansion graphs.

```bash
# Run local documentation site with Python
cd docs-site
python -m http.server 3000
# Open http://localhost:3000 in your browser
```
*(Or simply double-click [`docs-site/index.html`](docs-site/index.html) in any modern browser—no Node.js or build steps required).*

---

## 🚀 End-to-End Search Pipeline

<p align="center">
  <img src="docs/assets/searchforge-pipeline.svg" alt="SearchForge 7-Stage End-to-End Pipeline" width="100%">
</p>

SearchForge operates across two interconnected lifecycles:

### Ingestion & Indexing Lifecycle
1. **Ingress & Frontier:** The `UrlFrontier` provides FIFO scheduling for discovered seed URLs. The `UrlNormalizer` canonicalizes targets per RFC 3986 (lowercase schemes/domains, remove fragment `#hashes`, strip trailing default ports, order query parameters).
2. **Polite Crawling:** The `RobotsTxtService` caches rules per RFC 9309 and enforces longest-match disallow prefixes. `PerDomainRateLimiter` enforces atomic per-host delays. `DocumentFetcher` retrieves raw HTML via virtual threads with a 2MB payload guard.
3. **DOM Cleaning & Deduplication:** `HtmlParser` strips structural tags (`<script>`, `<style>`, `<nav>`, `<aside>`), extracts title/body text, and computes a SHA-256 hash to eliminate mirror content duplicates.
4. **Linguistic Processing:** Text passes through Unicode NFD decomposition (`TextNormalizer`) to strip diacritics, followed by the `StopWordFilter` (130+ non-discriminative terms), while strictly preserving word position offsets.
5. **Morphological Reduction:** The `PorterStemmer` executes a 5-step morphological cascade, reducing inflected variants to canonical word stems.
6. **Index Construction:** Postings lists record `(docId, termFrequency, [positions])`. The in-memory tier (`InMemoryInvertedIndex`) updates concurrent lookup maps, and the persistence tier writes relational postings to PostgreSQL.

### Retrieval & Ranking Lifecycle
1. **Query Pre-Processing & Suggestions:** Free-text queries pass through `SpellingCorrectionService` (Damerau-Levenshtein OSA distance + Noisy Channel model) to generate `didYouMean` suggestions. Keystroke prefixes trigger `TrieAutocompleteEngine` for real-time suggestions.
2. **Directional Synonym Expansion:** Queries are normalized and expanded by `SynonymExpansionService` using directional synonym graphs (`ONE_WAY` and `BIDIRECTIONAL`), while quoted phrases and Boolean operators (`AND`, `OR`, `NOT`) bypass expansion to protect AST semantics.
3. **Query Lexing & Parsing:** Free-text query terms and expanded synonyms are tokenized and stemmed identically to indexed documents. Boolean queries are tokenized by `BooleanQueryLexer` and parsed into an AST by `BooleanQueryParser`.
4. **Candidate Generation (TAAT):** `DefaultRankedRetrievalEngine` queries the index using Term-At-A-Time postings accumulation, gathering documents matching at least one non-stopword query or synonym term.
5. **Weighted BM25 Scoring:** Documents are scored using the Okapi BM25 probabilistic model incorporating Robertson-Spärck Jones non-negative IDF, sublinear term frequency saturation ($k_1=1.2$), document length normalization ($b=0.75$), and a tunable discount factor ($\delta = 0.5$) for expanded synonym terms.
6. **Positional Phrase Boosting:** Documents containing consecutive query terms at adjacent offsets ($pos_{i+1} - pos_i = 1$) receive additive score boosts.
7. **Top-K Priority Queue:** Candidate documents are streamed through a bounded min-heap of size $K$, yielding the final top-$K$ ranked result set in $\mathcal{O}(C \log K)$ runtime.
8. **Dynamic Snippet Generation & Highlighting:** Matched documents are passed to `SnippetGeneratorService` to extract the highest-density excerpt window snapped to word boundaries, escaping HTML entities via `HtmlSanitizer` and wrapping query and synonym terms in `<mark>` tags.

---

## 🏗️ Subsystem Architecture

```mermaid
flowchart TD
    subgraph WebIngress["1. Web Ingress & Politeness Subsystem"]
        Seed["Seed URLs"] --> Frontier["UrlFrontier (FIFO Queue)"]
        Frontier --> Normalizer["UrlNormalizer (RFC 3986)"]
        Normalizer --> Robots["RobotsTxtService (RFC 9309 Longest-Match)"]
        Robots --> Limiter["PerDomainRateLimiter (Atomic Locks)"]
        Limiter --> Fetcher["DocumentFetcher (Jsoup + 2MB Guard)"]
        Fetcher --> Loom["Project Loom Virtual Threads"]
        Loom --> Parser["HtmlParser (DOM Stripper + SHA-256)"]
        Parser --> CrawledDocs[("crawled_documents Table")]
    end

    subgraph LinguisticPipeline["2. Linguistic Processing & Stemming"]
        CrawledDocs --> DocReader["Unindexed Documents Batch"]
        DocReader --> Tokenizer["Tokenizer (Sequential Position Tracking)"]
        Tokenizer --> NormalizerText["TextNormalizer (Unicode NFD & Diacritics)"]
        NormalizerText --> StopFilter["StopWordFilter (130+ English Stop Words)"]
        StopFilter --> Stemmer["PorterStemmer (5-Step Morphological Cascade)"]
    end

    subgraph IndexTier["3. Dual-Tier Inverted Index"]
        Stemmer --> InMem["InMemoryInvertedIndex (ConcurrentHashMap)"]
        InMem --> PostingsRAM["PostingsList (Binary-Search Ascending Docs)"]
        Stemmer --> RelationalStore[("PostgreSQL 16 Relational Postings")]
        RelationalStore --> TermTable["indexed_terms (df, ctf)"]
        RelationalStore --> PostingTable["term_postings (term_id, doc_id, positions)"]
        RelationalStore --> MetaTable["indexed_document_metadata (|d|, timestamp)"]
    end

    subgraph QueryIntelligence["4. Query Intelligence Subsystem"]
        Keystroke["Client Keystroke / Prefix"] --> AutoTrie["AutocompleteTrie (Weighted Prefix Tree)"]
        AutoTrie --> AutoResp["Top-K Autocomplete Response (1.3 µs)"]
        RawQuery["Client Search Query"] --> SpellCheck["SpellingCorrectionService (Damerau-Levenshtein OSA)"]
        SpellCheck --> NoisyChannel["Noisy Channel Scorer + Length Buckets"]
        NoisyChannel --> DidYouMean["'didYouMean' Suggestion (71.5 µs)"]
        RawQuery --> SynExp["SynonymExpansionService (Directional Graph)"]
        SynExp --> NormQuery["Normalized & Expanded Query (δ = 0.5)"]
    end

    subgraph QueryRanking["5. Relevance & Ranking Engine"]
        NormQuery --> QueryRoute{"Query Mode"}
        QueryRoute -->|Free-Text BM25| TAAT["DefaultRankedRetrievalEngine (TAAT Union)"]
        QueryRoute -->|Ranked Boolean| BoolEngine["DefaultRankedBooleanQueryEngine"]
        QueryRoute -->|Filtered Search| FilterEngine["Boolean Filter + Ranked Union"]

        BoolEngine --> AST["BooleanQueryParser (Recursive Descent AST)"]
        BoolEngine --> Merger["PostingsMerger (Two-Pointer O(L1+L2))"]

        TAAT --> Scorer["Bm25Scorer (Document Scoring)"]
        Scorer --> TermScorer["Bm25TermScorer (k1 Saturation & b Length Penalty)"]
        Scorer --> IDF["Bm25IdfCalculator (Non-Negative RSJ IDF)"]
        Scorer --> PhraseBoost["PositionalIndexSearcher (Exact Phrase Boost)"]
        
        Scorer --> TopK["Bounded Min-Heap Priority Queue (Top-K)"]
        TopK --> SnippetGen["SnippetGeneratorService (Word-Boundary Window)"]
        SnippetGen --> Sanitizer["HtmlSanitizer (Anti-XSS + Mark Tags)"]
        Sanitizer --> SearchResult["Enriched Ranked Response (JSON)"]
        DidYouMean -.-> SearchResult
    end
```

---

## 🔬 Subsystem Deep-Dive

### 1. Web Crawler Subsystem (`com.searchforge.crawler`)
* **Project Loom Concurrency:** Spawns lightweight Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) to fetch and parse pages without consuming OS platform threads.
* **RFC 3986 URL Canonicalization:** Lowercases schemes and domains, resolves relative links, strips fragment identifiers (`#section`), sorts query parameters, and removes trailing default ports.
* **RFC 9309 Robots Protocol:** Discovers and parses `robots.txt`, applying the **longest-matching directive** rule, user-agent bot matching, crawl-delay politeness, and 24-hour expiration caching.
* **Politeness Rate Limiting:** Enforces per-domain synchronization locks with atomic timestamps to prevent hammering target hosts.
* **Content Deduplication:** Generates SHA-256 cryptographic hashes of stripped DOM text to eliminate mirror URL duplicates.

### 2. Linguistic Text Processing Pipeline (`com.searchforge.indexer.token`)
* **Unicode Normalization:** Decomposes accented characters into base characters and diacritical marks (`Normalizer.Form.NFD`), then strips combining marks via regex `\\p{M}+` (e.g., `"café"` $\to$ `"cafe"`, `"naïve"` $\to$ `"naive"`).
* **Positional Invariant Preservation:** When stop words (e.g. `"the"`, `"in"`, `"on"`) are filtered, the sequential word offset counter continues to increment. This ensures that exact phrase search (`"cat mat"`) does not produce false positives over `"cat on the mat"`.

### 3. Porter Stemming Algorithm (`com.searchforge.indexer.stem`)
A complete, thread-safe implementation of Martin Porter's 1980 morphological reduction cascade:
- **Consonant / Vowel Logic:** Handles contextual `y` preceded by consonants/vowels.
- **Syllable Measure ($m$):** Counts alternating consonant-vowel sequences $[C](VC)^m[V]$.
- **Condition Functions:** Validates vowel containment ($*v*$), double consonants ($*d$), and consonant-vowel-consonant patterns ($*o$).
- **5-Step Cascades:**
  - *Step 1a/1b/1c:* Plural suffixes (`-sses` $\to$ `-ss`, `-ies` $\to$ `-i`), past participles (`-eed` $\to$ `-ee`, `-ed`/`-ing` stripping), and terminal `-y` normalization.
  - *Step 2/3:* Derivational and complex suffix reductions (`-ational` $\to$ `-ate`, `-ization` $\to$ `-ize`).
  - *Step 4/5:* Residual suffix stripping for $m > 1$, terminal `-e` pruning, and double consonant deduplication.

### 4. Dual-Tier Inverted Index & Positional Search (`com.searchforge.indexer`)
* **In-Memory Tier (`InMemoryInvertedIndex`):**
  - Dictionary backed by `ConcurrentHashMap<String, PostingsList>`.
  - Maintains document lengths and instantaneous corpus metrics: $N$ (total documents), $V$ (vocabulary size), and $\text{avgdl}$ (average document length).
* **Positional Indexing (`PositionalIndexSearcher`):**
  - Records every token offset: `Posting(documentId, tf, [pos1, pos2, ...])`.
  - **Exact Phrase Search:** Intersects postings lists in $\mathcal{O}(P_1 + P_2)$ time, verifying $pos_{i+1} - pos_i = 1$.
* **Relational Persistence Tier:**
  - `IndexedTerm`: Dictionary table with unique index on `term`, tracking $df$ and $ctf$.
  - `TermPosting`: Relational postings with compound unique constraint `(term_id, document_id)` and portable text positions.
  - `IndexedDocumentMetadata`: Tracks document lengths and indexing timestamps.

### 5. Query Intelligence & User Experience Subsystem (`com.searchforge.query`)
* **Weighted Trie Autocomplete (`com.searchforge.query.autocomplete`):**
  - Lock-free atomic reference prefix tree (`AtomicReference<AutocompleteTrie>`) dynamically synchronized with index updates.
  - Scores terms by combining corpus term frequency and document frequency: $\text{Score}(t) = \ln(1 + \text{ctf}) \times \sqrt{\text{df}}$.
  - Extracts the top-$K$ candidates via bounded min-heap in $\mathcal{O}(L + M \log K)$ runtime without sorting full subtrees.
* **Damerau-Levenshtein Spelling Correction (`com.searchforge.query.spelling`):**
  - Optimal String Alignment (OSA) dynamic programming evaluating insertions, deletions, substitutions, and adjacent transpositions.
  - Length-bucket index partitions the vocabulary to evaluate only terms within $\pm 2$ characters, pruning over 90% of candidate comparisons.
  - Noisy Channel scoring weights edit distance likelihood against indexed popularity: $P(C \mid T) \propto \text{ErrorWeight}(d) \times (\ln(1 + \text{ctf}) \times \sqrt{\text{df}} + 1.0)$.
* **Dynamic Sliding-Window Snippets (`com.searchforge.query.snippet`):**
  - Scans document text using word-boundary snapping to preserve linguistic integrity (160 character target window).
  - Density relevance scoring optimizes term clusters: $\text{Score} = 100 \cdot Q_{\text{unique}} + 10 \cdot Q_{\text{total}} + \frac{100}{1 + \text{Span}}$.
  - Built-in `HtmlSanitizer` escapes raw HTML entities (`&`, `<`, `>`, `"`, `'`) before wrapping matched terms in `<mark>` tags.
* **Directional Synonym Expansion (`com.searchforge.query.synonym`):**
  - Configurable directional mappings (`ONE_WAY` vs. `BIDIRECTIONAL`) with loop prevention and self-mapping elimination.
  - Applies a discounted multiplier ($\delta = 0.5$) during BM25 score accumulation.
  - Strictly preserves Boolean operators (`AND`, `OR`, `NOT`) and quoted phrases without expansion to maintain AST semantics.

---

## 📐 Okapi BM25 Relevance Engine

SearchForge implements the complete **Okapi BM25** probabilistic relevance framework with document length normalization, asymptotic term-frequency saturation, and non-negative Robertson-Spärck Jones Inverse Document Frequency (RSJ-IDF).

<p align="center">
  <img src="docs/assets/bm25-saturation-curve.svg" alt="BM25 Saturation vs Linear TF-IDF" width="100%">
</p>

### Mathematical Formulation

$$\text{Score}(D, Q) = \sum_{q \in Q} \text{IDF}_{\text{RSJ}}(q) \times \frac{tf(q, D) \cdot (k_1 + 1)}{tf(q, D) + k_1 \cdot \left(1 - b + b \cdot \frac{|D|}{\text{avgdl}}\right)} + \text{PhraseBoost}(D, Q)$$

#### 1. Non-Negative Robertson–Spärck Jones IDF
Traditional RSJ-IDF can evaluate to negative values when a query term appears in more than half the collection ($df > N/2$). SearchForge implements the smoothed floor formulation to guarantee strictly non-negative weights:

$$\text{IDF}_{\text{RSJ}}(q) = \ln\left(1 + \frac{N - df(q) + 0.5}{df(q) + 0.5}\right)$$

#### 2. Sublinear Term Frequency Saturation ($k_1 = 1.2$)
Unlike standard Vector Space Models where relevance scales linearly with frequency ($W \propto tf$), BM25 saturates asymptotically at $k_1 + 1 = 2.2$. This prevents repetitive keyword stuffing from dominating search rankings:

$$\lim_{tf \to \infty} \frac{tf \cdot (k_1 + 1)}{tf + K} = k_1 + 1 = 2.2$$

#### 3. Document Length Normalization ($b = 0.75$)
The length penalty divisor $B = 1 - b + b \cdot \frac{|D|}{\text{avgdl}}$ dynamically adjusts expectations based on document verbosity:
- When $|D| = \text{avgdl}$, $B = 1.0$ (no adjustment).
- When $|D| > \text{avgdl}$, $B > 1.0$ (penalizes verbose documents where high term counts are incidental).
- When $|D| < \text{avgdl}$, $B < 1.0$ (rewards concise, dense answers).

#### 4. Positional Phrase Boosting
Documents containing consecutive query terms at adjacent token positions ($pos_{i+1} - pos_i = 1$) receive an exact-phrase additive score multiplier, elevating precise semantic matches above loose bag-of-words hits.

---

## 📊 Verified Real-World Experiment

SearchForge was validated on an end-to-end live public web crawl across three enterprise technical documentation domains: **PostgreSQL Documentation** (`www.postgresql.org`), **Oracle Java SE Documentation** (`docs.oracle.com`), and **Spring Framework Reference** (`spring.io`).

<p align="center">
  <img src="https://img.shields.io/badge/Experiment-16%20Live%20Pages-blue?style=flat-square" alt="16 Live Pages">
  <img src="https://img.shields.io/badge/Tokens-21%2C406%20Indexed-purple?style=flat-square" alt="21406 Tokens">
  <img src="https://img.shields.io/badge/Vocabulary-1%2C999%20Terms-teal?style=flat-square" alt="1999 Terms">
  <img src="https://img.shields.io/badge/Postings-4%2C526-orange?style=flat-square" alt="4526 Postings">
  <img src="https://img.shields.io/badge/Evaluation%20MRR-1.000-brightgreen?style=flat-square" alt="MRR 1.000">
  <img src="https://img.shields.io/badge/Search%20Latency-%3C1ms-success?style=flat-square" alt="Sub-1ms Latency">
</p>

### Corpus & Vocabulary Metrics
- **Pages Crawled:** 16 live HTML documents retrieved over HTTPS.
- **Raw Tokens Extracted:** 21,406 tokens processed through Unicode normalization.
- **Unique Lexicon / Vocabulary ($V$):** 1,999 stemmed unique terms.
- **Total Postings Created:** 4,526 positional posting records.
- **Average Document Length ($\text{avgdl}$):** 572.75 tokens per document.
- **Document Length Range:** Minimum 29 tokens (Spring overview page) to Maximum 3,467 tokens (PostgreSQL indexes guide).

### Information Retrieval Evaluation Results
Evaluated against multi-intent benchmark queries with curated binary and graded relevance judgments:

| Benchmark Query | Intent Category | Gold Standard Target | Rank | BM25 Score | Reciprocal Rank | P@1 | P@3 |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `"b-tree indexes"` | High-specificity technical concept | PostgreSQL B-Tree Indexes Guide | **#1** | **5.4187** | **1.000** | **1.00** | **1.00** |
| `"virtual threads loom"` | Concurrency architecture | Oracle Java 21 Virtual Threads Guide | **#1** | **3.8941** | **1.000** | **1.00** | **1.00** |
| `"spring dependency injection"` | Framework fundamentals | Spring Core Framework Reference | **#1** | **4.1205** | **1.000** | **1.00** | **1.00** |
| **Aggregate IR Metrics** | — | **All Domains Evaluated** | — | — | **MRR: 1.000** | **1.00** | **1.00** |

*For complete experiment audit logs, see [`docs/experiments/REAL_WORLD_CRAWL_EXPERIMENT.md`](docs/experiments/REAL_WORLD_CRAWL_EXPERIMENT.md).*

---

## ⚡ Algorithmic Complexity Reference

| Operation | Implementation Algorithm | Time Complexity | Auxiliary Space |
| :--- | :--- | :---: | :---: |
| **URL Canonicalization** | RFC 3986 Normalizer | $\mathcal{O}(L_{\text{url}})$ | $\mathcal{O}(L_{\text{url}})$ |
| **Robots Path Matching** | RFC 9309 Longest-Match Prefix Scan | $\mathcal{O}(N_{\text{rules}} \cdot L_{\text{path}})$ | $\mathcal{O}(1)$ |
| **Text Tokenization** | Unicode NFD Decomposition | $\mathcal{O}(N_{\text{chars}})$ | $\mathcal{O}(N_{\text{tokens}})$ |
| **Porter Stemming** | 5-Step Morphological Cascade | $\mathcal{O}(L_{\text{word}})$ | $\mathcal{O}(1)$ |
| **Posting Insertion** | Ascending Binary Search | $\mathcal{O}(\log K + K)$ | $\mathcal{O}(1)$ |
| **In-Memory Dictionary Lookup** | Lock-Free Concurrent Hash Index | $\mathcal{O}(1)$ | $\mathcal{O}(1)$ |
| **Exact Phrase Matching** | Positional Intersection | $\mathcal{O}(P_1 + P_2)$ | $\mathcal{O}(P_{\text{match}})$ |
| **BM25 Single Term Scoring** | Asymptotic Normalization | $\mathcal{O}(1)$ | $\mathcal{O}(1)$ |
| **Candidate Generation (TAAT)** | In-Memory Postings Accumulation | $\mathcal{O}(\sum_{q} |P_q|)$ | $\mathcal{O}(M_{\text{candidates}})$ |
| **Top-K Selection** | Bounded Min-Heap Priority Queue | $\mathcal{O}(M \log K)$ | $\mathcal{O}(K)$ |
| **NDCG@K Evaluation** | Log-Discounted Gain | $\mathcal{O}(K \log K)$ | $\mathcal{O}(K)$ |
| **AND Intersection** | Two-Pointer Linear Merge | $\mathcal{O}(L_1 + L_2)$ | $\mathcal{O}(\min(L_1, L_2))$ |
| **OR Union** | Two-Pointer Linear Merge | $\mathcal{O}(L_1 + L_2)$ | $\mathcal{O}(L_1 + L_2)$ |
| **NOT Difference** | Two-Pointer Linear Difference | $\mathcal{O}(L_1 + L_2)$ | $\mathcal{O}(L_1)$ |
| **Trie Prefix Autocomplete** | Bounded Min-Heap Traversal | $\mathcal{O}(L + M \log K)$ | $\mathcal{O}(V \cdot \bar{L})$ |
| **Spelling OSA Distance** | Damerau-Levenshtein DP | $\mathcal{O}(L_1 \cdot L_2)$ | $\mathcal{O}(L_1 \cdot L_2)$ |
| **Length-Bucket Candidate Pruning** | Hash-Indexed Bucket Lookup | $\mathcal{O}(1)$ | $\mathcal{O}(V)$ |
| **Sliding-Window Snippets** | Word-Boundary Density Scan | $\mathcal{O}(W \cdot |Q|)$ | $\mathcal{O}(W)$ |
| **Anti-XSS Sanitization** | Single-Pass Entity Escaping | $\mathcal{O}(N_{\text{chars}})$ | $\mathcal{O}(N_{\text{chars}})$ |
| **Synonym Expansion** | Directional Graph Traversal | $\mathcal{O}(|Q| \cdot S_{\text{max}})$ | $\mathcal{O}(|Q| \cdot S_{\text{max}})$ |

---

## 🧠 Query Intelligence & Search Experience (Phase 5)

SearchForge elevates information retrieval beyond basic keyword matching through custom-built query intelligence algorithms:

### 1. Weighted Trie Autocomplete
* **Custom Trie Data Structure:** Thread-safe prefix tree backed by `ConcurrentHashMap` and lock-free atomic references (`AtomicReference<AutocompleteTrie>`).
* **Deterministic Vocabulary Ranking:** Combines Corpus Term Frequency ($ctf$) and Document Frequency ($df$):
  $$\text{Score}(t) = \ln(1 + \text{ctf}) \times \sqrt{\text{df}}$$
* **Bounded Min-Heap Top-K Retrieval:** Traverses prefix subtrees and extracts top-$K$ candidates in $\mathcal{O}(L + M \log K)$ runtime without full subtree sorting.

### 2. Spelling Correction & Query Suggestions
* **Optimal String Alignment (OSA):** Evaluates insertions, deletions, substitutions, and adjacent transpositions via Damerau-Levenshtein distance with early-exit pruning at $\text{maxDistance} = 2$.
* **Length-Bucket Candidate Pruning:** Indexes vocabulary into word-length buckets ($[L - 2, L + 2]$) to prune 90%+ of irrelevant comparisons.
* **Noisy Channel Model:** Scores candidate corrections by trading off edit likelihood against corpus popularity:
  $$P(C \mid T) \propto \text{ErrorWeight}(d) \times \left(\ln(1 + \text{ctf}) \times \sqrt{\text{df}} + 1.0\right)$$
* **Vocabulary Invariance:** Known valid indexed terms are strictly protected from modification.

### 3. Dynamic Sliding-Window Snippets & Term Highlighting
* **Word-Boundary Windowing:** Snaps dynamically to complete linguistic words rather than raw character offsets.
* **Density Relevance Scoring:** Evaluates candidate excerpts by reward density:
  $$\text{Score} = 100 \cdot Q_{\text{unique}} + 10 \cdot Q_{\text{total}} + \frac{100}{1 + \text{Span}}$$
* **Anti-XSS Sanitization:** Automatically escapes HTML entities (`&`, `<`, `>`, `"`, `'`) before injecting safe `<mark>` tags around matched query and synonym terms.

### 4. Query Normalization & Synonym Expansion
* **Directional Synonym Graphs:** Supports `ONE_WAY` (e.g. `k8s -> kubernetes`) and `BIDIRECTIONAL` (e.g. `db <-> database`) mappings with cycle and self-mapping guards.
* **Weighted BM25 Retrieval:** Applies a tunable discount factor ($\delta = 0.5$) to expanded synonym terms:
  $$\text{Score}(D) = \sum_{t \in Q_{\text{orig}}} 1.0 \times \text{BM25}(t, D) + \sum_{s \in Q_{\text{syn}}} \delta \times \text{BM25}(s, D)$$
* **Operator & Phrase Safety:** Quoted phrases and Boolean queries (`AND`, `OR`, `NOT`) strictly bypass expansion to guarantee AST semantic integrity.

### 5. Empirical Performance Benchmarks (500 Documents, 557 Unique Terms)
Measured across 200 iterations with JIT warm-up on Java 21 LTS:

| Component | Metric | Mean Latency | p50 Latency | p90 Latency | p99 Latency |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Autocomplete** | Top-K Prefix Lookup ($K=5$) | **1.29 µs** | 0.80 µs | 2.00 µs | 4.70 µs |
| **Spelling** | Candidate Generation & Scoring | **71.52 µs** | 89.10 µs | 109.90 µs | 177.70 µs |
| **Snippets** | Dynamic Windowing & Highlighting | **20.13 µs** | 19.40 µs | 31.70 µs | 76.70 µs |
| **Baseline Search** | Raw BM25 Ranked Retrieval | **0.17 ms** | 0.15 ms | 0.23 ms | 0.44 ms |
| **Enriched Search** | End-to-End Query Intelligence | **0.26 ms** | 0.23 ms | 0.39 ms | 0.59 ms |

*Total query intelligence overhead is **under 90 microseconds**.*

---

## 📡 REST API Reference

### 1. Relevance Search Engine (`/api/v1/search`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/search?q={query}&limit=10&explain={bool}` | Free-text BM25 search with spelling suggestions, synonyms, and dynamic snippets |
| `GET` | `/api/v1/search/autocomplete?prefix={prefix}&limit=5` | Real-time prefix autocomplete suggestions ranked by popularity |
| `GET` | `/api/v1/search/suggest?q={query}` | Damerau-Levenshtein spelling error detection and `didYouMean` suggestions |
| `GET` | `/api/v1/search/ranked-boolean?q={boolQuery}&limit=10` | Ranked Boolean search evaluating AST logic with highlighted snippets |
| `GET` | `/api/v1/search/filtered?q={query}&filter={filter}` | Filtered ranked search restricted to Boolean filter |

#### Example Enriched Search Response (`200 OK`):
```json
{
  "query": "postgreql btre",
  "didYouMean": "postgresql btree",
  "totalHits": 1,
  "returnedHits": 1,
  "executionTimeMs": 2,
  "results": [
    {
      "docId": 1,
      "url": "https://searchforge.dev/postgres-btree",
      "title": "PostgreSQL B-Tree Indexes",
      "domain": "searchforge.dev",
      "snippet": "PostgreSQL uses a B-tree index for high-performance equality and range queries...",
      "highlightedSnippet": "...<mark>PostgreSQL</mark> uses a <mark>B-tree</mark> index for high-performance equality and range queries...",
      "score": 4.85,
      "rank": 1,
      "matchedTerms": ["postgresql", "btree"]
    }
  ]
}
```

### 2. Inverted Index Management (`/api/v1/index`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/index/build?batchSize=100` | Synchronizes unindexed crawled pages into inverted index tables |
| `POST` | `/api/v1/index/rebuild` | Purges existing index and rebuilds all postings from scratch |
| `POST` | `/api/v1/index/warmup` | Warms up RAM inverted index directly from PostgreSQL storage |
| `GET` | `/api/v1/index/stats` | Returns corpus size ($N$), vocabulary ($V$), total postings, and $\text{avgdl}$ |

### 3. Web Crawler Control (`/api/v1/crawler`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/crawler/start` | Starts an asynchronous crawl job with seed URLs |
| `POST` | `/api/v1/crawler/stop` | Signals active virtual threads to stop crawling |
| `POST` | `/api/v1/crawler/reset` | Resets frontier queue, visited cache, and engine state |
| `GET` | `/api/v1/crawler/status` | Returns live crawl telemetry (pages, errors, queue, runtime) |

*For complete request/response schemas, see [`docs/api/SEARCH_API.md`](docs/api/SEARCH_API.md), [`docs/api/INDEXER_API.md`](docs/api/INDEXER_API.md), and [`docs/api/CRAWLER_API.md`](docs/api/CRAWLER_API.md).*

---

## 🧪 Automated Testing Audit (273 Tests)

```
[INFO] Results:
[INFO] Tests run: 273, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: ~18.7s)
```

SearchForge includes **273 hermetic automated tests** across all subsystems:
- **Phase 5 Query Intelligence & Experience (88 tests):**
  - `AutocompleteTrieTest`, `AutocompleteServiceTest`, `AutocompleteControllerTest`: Trie prefix traversal, min-heap Top-K, thread-safe atomic sync, REST validation.
  - `DamerauLevenshteinDistanceTest`, `SpellingCorrectionServiceTest`, `SpellingSuggestionControllerTest`: Edit distance matrices, adjacent transpositions, length-bucket candidate selection, multi-word spelling scan.
  - `HtmlSanitizerTest`, `WindowRelevanceScorerTest`, `SnippetGeneratorServiceTest`: Word-boundary snapping, query density scoring, XSS entity escaping, `<mark>` tag injection.
  - `InMemorySynonymDictionaryTest`, `SynonymExpansionServiceTest`: Bidirectional and one-way synonym expansion, cycle suppression, stop-word pruning.
  - `SearchIntelligenceIntegrationTest`: Enriched search responses, `didYouMean` suggestions, highlighted snippets, Boolean AST preservation, zero-hit handling.
  - `DefaultRankedRetrievalEngineSynonymTest`: Weighted BM25 scoring with discounted synonyms ($w_t = 0.5$).
  - `QueryIntelligenceBenchmarkTest` & `QueryIntelligenceRelevanceEvaluationTest`: Latency percentile benchmarks and IR relevance verification.
- **Phase 4 Scoring & Ranking (65 tests):** BM25 term scorer, RSJ IDF, length normalization ($b$), term saturation ($k_1$), TAAT retrieval, NDCG@K, MRR, P@K, R@K.
- **Phase 3 Indexing & Boolean Retrieval (58 tests):** Tokenizer, Porter stemmer, inverted index, positional phrase search, JPA persistence, recursive-descent parser, two-pointer mergers.
- **Phase 1 & 2 Foundation & Crawler (62 tests):** Virtual thread concurrency, BFS frontier, RFC 3986 normalizer, RFC 9309 robots parser, WireMock integration tests.

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Java 21 LTS** (Eclipse Temurin 21 recommended)
- **Docker & Docker Compose** (optional for PostgreSQL 16 container, or run zero-Docker via H2 `dev` profile)
- **Maven Wrapper:** Included (`mvnw` / `mvnw.cmd`)

### Option A: Zero-Docker In-Memory Quickstart (Recommended for fast exploration)
SearchForge includes an in-memory H2 development profile with pre-configured domain allowlists (`www.postgresql.org`, `docs.oracle.com`, `spring.io`).

```bash
cd backend

# Run all 273 tests in-memory
./mvnw test

# Launch the server with the dev profile (H2 in-memory DB)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
*(On Windows PowerShell: `.\mvnw.cmd test` and `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`)*.

### Option B: Production Docker Compose Setup (PostgreSQL 16)
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Start PostgreSQL 16 Alpine container
docker compose up -d

# 3. Build and launch backend
cd backend
./mvnw spring-boot:run
```

The search engine server will start on port `8080`.

---

## 📚 Technical Documentation Hub

The complete engineering documentation is organized under [`docs/`](docs/):

| Directory / Document | Purpose & Contents |
| :--- | :--- |
| **[`docs-site/`](docs-site/index.html)** | **Interactive HTML documentation website with animated search pipeline and BM25 graph** |
| **[`docs/architecture/SYSTEM_ARCHITECTURE.md`](docs/architecture/SYSTEM_ARCHITECTURE.md)** | Subsystem architecture, data contracts, and class-level interactions |
| **[`docs/architecture/DATA_FLOW.md`](docs/architecture/DATA_FLOW.md)** | End-to-end data lifecycle sequences for crawling, indexing, and retrieval |
| **[`docs/api/SEARCH_API.md`](docs/api/SEARCH_API.md)** | Complete REST API specification for BM25, autocomplete, spelling suggestions, Boolean, and filtered search |
| **[`docs/api/INDEXER_API.md`](docs/api/INDEXER_API.md)** | Index management, synchronization, warmup, and corpus statistics endpoints |
| **[`docs/api/CRAWLER_API.md`](docs/api/CRAWLER_API.md)** | Crawler control, status telemetry, and frontier reset endpoints |
| **[`docs/experiments/REAL_WORLD_CRAWL_EXPERIMENT.md`](docs/experiments/REAL_WORLD_CRAWL_EXPERIMENT.md)** | Audit report of the 16-page live crawl across PostgreSQL, Oracle, and Spring docs |
| **[`docs/phases/`](docs/phases/)** | Detailed milestone guides for Phase 1, Phase 2, Phase 3, Phase 4, and Phase 5 |
| **[`docs/phases/PHASE_5_QUERY_INTELLIGENCE.md`](docs/phases/PHASE_5_QUERY_INTELLIGENCE.md)** | Complete guide to Trie autocomplete, spelling correction, sliding snippets, and synonyms |
| **[`docs/PHASE_5_COMPLETE_GUIDE.md`](docs/PHASE_5_COMPLETE_GUIDE.md)** | Comprehensive index and technical reference for Phase 5 Query Intelligence |

---

## 🛠️ Repository Governance

- **[CONTRIBUTING.md](CONTRIBUTING.md):** Guidelines for code style, branching, pull requests, and test expectations.
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md):** Contributor standards and inclusive community pledges.
- **[SECURITY.md](SECURITY.md):** Vulnerability reporting and disclosure protocols.
- **[CHANGELOG.md](CHANGELOG.md):** Semantic version history across Phase 1, Phase 2, Phase 3, Phase 4, and Phase 5.
- **[.github/workflows/ci.yml](.github/workflows/ci.yml):** Automated GitHub Actions CI workflow running Java 21 test suites on every pull request.
- **[.github/workflows/pages.yml](.github/workflows/pages.yml):** Automated GitHub Pages deployment workflow for the interactive `docs-site/`.

---

<p align="center">
  Built with ❤️ from first principles by <a href="https://github.com/vaibhavbutul-work"><strong>Vaibhav Butul</strong></a>
</p>
