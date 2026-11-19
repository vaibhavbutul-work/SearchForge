# SearchForge 🔍
### *Industrial-Grade Custom Search Engine Built From First Principles*

[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1.2](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16_Alpine-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Project Loom](https://img.shields.io/badge/Concurrency-Project_Loom_Virtual_Threads-4B0082?style=for-the-badge)](https://openjdk.org/projects/loom/)
[![Automated Tests](https://img.shields.io/badge/Tests-120%20Passing%20(100%25)-brightgreen?style=for-the-badge&logo=checkmarx&logoColor=white)](https://github.com/vaibhavbutul-work/SearchForge)
[![Architecture](https://img.shields.io/badge/Architecture-First_Principles_IR-blueviolet?style=for-the-badge)](https://github.com/vaibhavbutul-work/SearchForge)

> **"What I cannot create, I do not understand." — Richard Feynman**  
> Rather than relying on black-box search frameworks like Apache Lucene or Elasticsearch, **SearchForge** implements every fundamental algorithm and data structure of an enterprise search engine from scratch: multi-worker polite web crawling, linguistic text processing, morphological stemming, thread-safe inverted indexing, positional exact phrase search, recursive-descent Boolean query parsing, and two-pointer linear merge retrieval.

---

## 🌟 Why SearchForge Stands Out (Interviewer Highlights)

| Engineering Challenge | How SearchForge Solves It From First Principles |
| :--- | :--- |
| **High-Throughput Crawling** | Powered by **Java 21 Project Loom Virtual Threads**; non-blocking worker pools fetch and parse web pages concurrently without OS thread starvation. |
| **RFC 9309 Politeness** | Custom `robots.txt` parser implementing **longest-match path evaluation**, per-domain thread-safe concurrency locks, crawl delays, and 24-hour cache invalidation. |
| **Linguistic Processing** | Full **Unicode NFD canonical decomposition**, diacritical mark stripping, English possessive stripping, 130+ stop-word filtering with **strict positional preservation**. |
| **Morphological Stemming** | Complete implementation of **Martin Porter’s 1980 5-step cascade** ($m$-measure, vowel conditions $*v*$, double consonants $*d$, $cvc$ checks $*o$) in a thread-safe, stateless architecture. |
| **Dual-Tier Inverted Index** | **RAM Tier:** `ConcurrentHashMap` dictionary with ascending binary-search `PostingsList` containers.<br>**Persistent Tier:** PostgreSQL 16 schema with compound unique constraints and portable positional encoding. |
| **Positional Phrase Search** | Exact phrase search evaluated via **linear positional intersection** ($O(P_1 + P_2)$) and sliding-window proximity searches ($|pos_1 - pos_2| \le k$). |
| **Boolean Query Engine** | **Recursive-descent AST parser** supporting operator precedence (`NOT` > `AND` > `OR`), nested parentheses, implicit `AND`, and **two-pointer linear merges** ($O(L_1 + L_2)$). |
| **Zero-Flake Test Suite** | **120 automated tests** passing in ~20 seconds; hermetic integration tests running against an in-memory H2 database without Docker or internet dependencies. |

---

## 🏗️ End-to-End System Architecture

```mermaid
flowchart TD
    subgraph WebLayer["1. Web Ingestion & Ingress"]
        Web[("World Wide Web")]
        Frontier["UrlFrontier (BFS FIFO Queue)"]
        Normalizer["UrlNormalizer (RFC 3986 Canonicalizer)"]
        Robots["RobotsTxtService (RFC 9309 Cache)"]
        Fetcher["DocumentFetcher (Jsoup + 2MB Guard)"]
        Parser["HtmlParser (Noise Stripper + SHA-256)"]
        Loom["Virtual Thread Worker Pool (Project Loom)"]
    end

    subgraph AnalysisLayer["2. Linguistic Analysis Subsystem"]
        TextNorm["TextNormalizer (Unicode NFD & Diacritics)"]
        StopWords["StopWordFilter (130+ Stop Words, O(1))"]
        Porter["PorterStemmer (5-Step Morphological Cascade)"]
        Tokenizer["Tokenizer (Sequential Position Preservation)"]
    end

    subgraph StorageLayer["3. Dual-Tier Index Storage"]
        InMemIndex["InMemoryInvertedIndex (ConcurrentHashMap)"]
        PostingsList["PostingsList (Binary-Search Sorted Docs)"]
        PostgresDB[("PostgreSQL 16 Database")]
        TermRepo["indexed_terms (df, ctf, vocabulary)"]
        PostingRepo["term_postings (uk_term_doc, positions)"]
        DocMetaRepo["indexed_document_metadata (|d|, timestamp)"]
    end

    subgraph RetrievalLayer["4. Query Parsing & Boolean Retrieval"]
        QueryLexer["BooleanQueryLexer (Token Stream)"]
        QueryParser["BooleanQueryParser (Recursive-Descent AST)"]
        RetrievalEng["BooleanRetrievalEngine (Evaluator)"]
        Merger["PostingsMerger (Two-Pointer O(L1+L2) Merges)"]
        PosSearcher["PositionalIndexSearcher (Exact Phrase Match)"]
    end

    subgraph ApiLayer["5. REST API & Telemetry"]
        Client["Client / cURL / Browser"]
        IndexCtrl["IndexController (/api/v1/index/*, /api/v1/search/*)"]
        CrawlerCtrl["CrawlerController (/api/v1/crawler/*)"]
    end

    %% Flow connections
    Web --> Fetcher
    Frontier --> Normalizer --> Robots --> Fetcher
    Fetcher --> Parser --> Loom
    Parser -->|crawled_documents| PostgresDB

    PostgresDB -->|Unindexed Docs| Tokenizer
    Tokenizer --> TextNorm --> StopWords --> Porter

    Tokenizer --> InMemIndex
    InMemIndex --> PostingsList
    Tokenizer --> TermRepo & PostingRepo & DocMetaRepo
    TermRepo & PostingRepo & DocMetaRepo --> PostgresDB

    Client -->|Search & Management| ApiLayer
    IndexCtrl --> RetrievalEng & InMemIndex
    CrawlerCtrl --> Loom

    RetrievalEng --> QueryLexer --> QueryParser
    QueryParser --> RetrievalEng
    RetrievalEng --> Merger & PosSearcher
    Merger & PosSearcher --> InMemIndex
```

---

## 🗺️ Project Roadmap & Phase Status

| Phase | Milestone Name | Status | Key Deliverables & Algorithms | Tests |
| :---: | :--- | :---: | :--- | :---: |
| **Phase 1** | **Foundation & Infrastructure** | **Completed** | Modular Spring Boot 4 setup, Docker Compose PostgreSQL 16 Alpine, HikariCP, Health REST API, hermetic context tests | **7** |
| **Phase 2** | **Web Crawler Subsystem** | **Completed** | Loom Virtual Threads, BFS FIFO frontier, RFC 3986 normalizer, RFC 9309 robots parser, Jsoup DOM stripper, SHA-256 deduplication, crawler REST API | **55** |
| **Phase 3** | **Inverted Index & Boolean Retrieval** | **Completed** | Unicode NFD normalizer, 130+ stop-word filter, 5-step Porter stemmer, positional inverted index, relational PostgreSQL schema, recursive-descent query parser, two-pointer linear merge, Boolean search REST API | **120** |
| **Phase 4** | **Scoring, Ranking & Relevance Engine** | **In Progress** | Okapi BM25 probabilistic model ($k_1, b$), Robertson–Spärck Jones IDF, TF-IDF Vector Space Model (VSM), cosine similarity, multi-field boosting (BM25F), Bounded Min-Heap Top-$K$ priority queue | *Pending* |
| **Phase 5** | **Query Engine & Search Experience** | **Upcoming** | Query suggestion, spelling correction (Levenshtein edit distance / SymSpell), prefix trie autocomplete, dynamic snippet highlighting | *Pending* |
| **Phase 6** | **Web Dashboard & Deployment** | **Upcoming** | Modern React search dashboard, live query telemetry, latency percentiles (p50/p95/p99), containerized production deployment | *Pending* |

---

## 🔬 Deep-Dive Subsystem Breakdown

### 1. Web Crawler Subsystem (`com.searchforge.crawler`)
* **Project Loom Concurrency:** Spawns lightweight Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) to fetch and parse pages without consuming OS threads.
* **RFC 3986 URL Canonicalization:** Lowercases schemes and domains, resolves relative links, strips fragment identifiers (`#section`), sorts query parameters, and removes trailing default ports.
* **RFC 9309 Robots Protocol:** Discovers and parses `robots.txt`, applying the **longest-matching directive** rule, user-agent bot matching, crawl-delay politeness, and 24-hour expiration caching.
* **Politeness Rate Limiting:** Enforces per-domain synchronization locks with atomic timestamps to prevent hammering target hosts.
* **Content Deduplication:** Generates SHA-256 cryptographic hashes of stripped DOM text to eliminate mirror URL duplicates.

### 2. Linguistic Text Processing Pipeline (`com.searchforge.indexer.token`)
* **Unicode Normalization:** Decomposes accented characters into base characters and diacritical marks (`Normalizer.Form.NFD`), then strips combining marks via regex `\\p{M}+` (e.g., `"café"` $\to$ `"cafe"`, `"naïve"` $\to$ `"naive"`).
* **Positional Invariant Preservation:** When stop words (e.g. `"the"`, `"in"`, `"on"`) are filtered, the sequential word offset counter continues to increment. This ensures that exact phrase search (`"cat mat"`) does not produce false positives over `"cat on the mat"`.

### 3. Porter Stemming Algorithm (`com.searchforge.indexer.stem`)
A complete, thread-safe implementation of Martin Porter's 1980 morphological reduction cascade:
- **Consonant / Vowel Logic:** Handles contextual `$y$` preceded by consonants/vowels.
- **Syllable Measure ($m$):** Counts alternating consonant-vowel sequences $[C](VC)^m[V]$.
- **Condition Functions:** Validates vowel containment ($*v*$), double consonants ($*d$), and consonant-vowel-consonant patterns ($*o$).
- **5-Step Cascades:**
  - *Step 1a/1b/1c:* Plural suffixes, past participles, and terminal `-y` normalization.
  - *Step 2/3:* Derivational and complex suffix reductions (`-ational` $\to$ `-ate`, `-ization` $\to$ `-ize`).
  - *Step 4/5:* Residual suffix stripping for $m > 1$, terminal `-e` pruning, and double consonant deduplication.

### 4. Dual-Tier Inverted Index & Positional Search (`com.searchforge.indexer`)
* **In-Memory Tier (`InMemoryInvertedIndex`):**
  - Dictionary backed by `ConcurrentHashMap<String, PostingsList>`.
  - Maintains document lengths and instantaneous corpus metrics: $N$ (total documents), $V$ (vocabulary size), and $\text{avgdl}$ (average document length).
* **Positional Indexing (`PositionalIndexSearcher`):**
  - Records every token offset: `Posting(documentId, tf, [pos1, pos2, ...])`.
  - **Exact Phrase Search:** Intersects postings lists in $O(P_1 + P_2)$ time, verifying $pos_{i+1} - pos_i = 1$.
  - **Proximity Window Search:** Two-pointer sliding window identifying co-occurring terms within distance $k$ ($|pos_1 - pos_2| \le k$).
* **Relational Persistence Tier:**
  - `IndexedTerm`: Dictionary table with unique index on `term`, tracking $df$ and $ctf$.
  - `TermPosting`: Relational postings with compound unique constraint `(term_id, document_id)` and portable comma-separated position strings (`"0,7,24"`).
  - `IndexedDocumentMetadata`: Tracks document lengths and indexing timestamps.

### 5. Boolean Retrieval Engine & Query Parser (`com.searchforge.indexer.retrieval`)
* **Grammar & Precedence:** Evaluates queries using recursive-descent parsing with standard IR operator precedence:
  $$\text{Parentheses } () \quad > \quad \text{Unary NOT } (\text{or } -) \quad > \quad \text{Binary AND / Implicit AND} \quad > \quad \text{Binary OR}$$
* **Implicit AND:** Automatically binds adjacent terms (e.g. `java spring boot` $\to$ `(java AND spring) AND boot`).
* **Two-Pointer Linear Merge Algorithms ($O(L_1 + L_2)$):**
  - `intersect(p1, p2)`: Linear traversal advancing the smaller pointer until matches are found.
  - `intersectAll(lists)`: Sorts postings lists by ascending document frequency ($\min(df_t)$) first, drastically pruning intermediate result sets.
  - `difference(p1, p2)`: Computes set difference ($p_1 \setminus p_2$).
  - `Pure Negation (`NOT term`):` Evaluated against the entire corpus document universe $U = \text{getAllDocumentIds()}$.

---

## ⚡ Algorithmic Complexity Reference

| Operation | Implementation Algorithm | Time Complexity | Auxiliary Space |
| :--- | :--- | :---: | :---: |
| **URL Canonicalization** | RFC 3986 Parser | $O(L_{\text{url}})$ | $O(L_{\text{url}})$ |
| **Robots Path Matching** | Longest Prefix Tree Scan | $O(N_{\text{rules}} \cdot L_{\text{path}})$ | $O(1)$ |
| **Text Tokenization** | Unicode NFD Decomposition | $O(N_{\text{chars}})$ | $O(N_{\text{tokens}})$ |
| **Porter Stemming** | 5-Step Morphological Cascade | $O(L_{\text{word}})$ | $O(1)$ |
| **Posting Insertion** | Ascending Binary Search | $O(\log K + K)$ | $O(1)$ |
| **In-Memory Dictionary Lookup** | Lock-Free Hash Index | $O(1)$ | $O(1)$ |
| **Exact Phrase Matching** | Positional Intersection | $O(P_1 + P_2)$ | $O(P_{\text{match}})$ |
| **Proximity Search** | Two-Pointer Sliding Window | $O(P_1 + P_2)$ | $O(P_{\text{match}})$ |
| **AND Intersection** | Two-Pointer Linear Merge | $O(L_1 + L_2)$ | $O(\min(L_1, L_2))$ |
| **Optimized Multi-Term AND** | Size-Ascending Greedy Merge | $O(\sum L_i)$ | $O(\min(L_i))$ |
| **OR Union** | Two-Pointer Linear Merge | $O(L_1 + L_2)$ | $O(L_1 + L_2)$ |
| **NOT Difference** | Two-Pointer Linear Difference | $O(L_1 + L_2)$ | $O(L_1)$ |
| **Pure Negation** | Difference with Universe $U$ | $O(\|U\| + L)$ | $O(\|U\|)$ |

---

## 📡 REST API Reference

### 1. System Health & Probes
| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :---: |
| `GET` | `/api/v1/health` | Direct database connection health probe | `200 OK` / `503 Unavailable` |
| `GET` | `/actuator/health` | Spring Boot Actuator readiness probe | `200 OK` |

### 2. Web Crawler Control (`/api/v1/crawler`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/crawler/start` | Starts an asynchronous crawl job with seed URLs |
| `POST` | `/api/v1/crawler/stop` | Signals active virtual threads to stop crawling |
| `POST` | `/api/v1/crawler/reset` | Resets frontier queue, visited cache, and engine state |
| `GET` | `/api/v1/crawler/status` | Returns live crawl telemetry (pages, errors, queue, runtime) |
| `GET` | `/api/v1/crawler/documents` | Returns paginated summaries of crawled web pages |

### 3. Inverted Index Management (`/api/v1/index`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/index/build?batchSize=100` | Synchronizes unindexed crawled pages into inverted index tables |
| `POST` | `/api/v1/index/rebuild` | Purges existing index and rebuilds all postings from scratch |
| `POST` | `/api/v1/index/warmup` | Warms up RAM inverted index directly from PostgreSQL storage |
| `GET` | `/api/v1/index/stats` | Returns corpus size ($N$), vocabulary ($V$), total postings, and $\text{avgdl}$ |

### 4. Boolean Search Retrieval (`/api/v1/search`)
```http
GET /api/v1/search/boolean?q=(java OR python) AND "web server" NOT deprecated&limit=10
```
**Example Response (`200 OK`):**
```json
{
  "query": "(java OR python) AND \"web server\" NOT deprecated",
  "totalHits": 1,
  "durationMs": 4,
  "hits": [
    {
      "documentId": 14,
      "url": "https://example.com/java-servers",
      "domain": "example.com",
      "title": "Modern Java Web Servers",
      "snippet": "High-performance HTTP server architectures in Java using virtual threads and non-blocking I/O with reactive streams..."
    }
  ]
}
```

---

## 🧪 Automated Testing Audit (120 Tests)

```
[INFO] Results:
[INFO] Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: ~20.2s)
```

SearchForge includes **120 automated tests** covering unit, integration, and web slice layers:
- **`TokenizerTest` (9 tests):** Unicode NFD normalization, diacritics removal, stop-word gaps, stemming integration.
- **`PorterStemmerTest` (5 tests):** 5-step cascades, plural/past reductions, vowel/measure conditions, idempotency.
- **`InMemoryInvertedIndexTest` (6 tests):** Term frequencies, document counts, $\text{avgdl}$, vocabulary sizes, 8-thread concurrency safety.
- **`PositionalIndexSearcherTest` (5 tests):** Exact multi-word phrase matching, non-consecutive position rejection, sliding-window proximity searches.
- **`IndexPersistenceTest` (6 tests):** JPA entity mapping, unique constraints on `term` and `(term_id, document_id)`, comma-separated positions encoding, and aggregate JPQL queries.
- **`IndexingEngineTest` (6 tests):** Single-document indexing, idempotent re-indexing, batch corpus synchronization, cold startup warmup from storage, and empty document handling.
- **`BooleanQueryParserTest` (11 tests):** AST construction, operator precedence (`NOT` > `AND` > `OR`), parentheses overrides, implicit `AND`, and syntax error validation.
- **`BooleanRetrievalEngineTest` (9 tests):** End-to-end `AND`, `OR`, `NOT`, exact phrases, nested expressions, and pure negations against the corpus universe.
- **`IndexControllerTest` (6 tests):** Web slice tests for `/api/v1/index/*` and `/api/v1/search/boolean` using Spring MockMvc.
- **Crawler & Health Test Suites (55 tests):** BFS frontier, normalizer, robots parser, politeness limiter, fetcher, HTML parser, and engine orchestration.

---

## 🚀 Quickstart & Setup Guide

### 1. Prerequisites
- **Java 21 LTS** (Eclipse Temurin 21 recommended)
- **Docker & Docker Compose** (for PostgreSQL 16 container)
- **Maven:** Bundled via Apache Maven Wrapper (`mvnw` / `mvnw.cmd`)

### 2. Configure Environment & Start Database
```powershell
# 1. Copy environment template
Copy-Item .env.example .env

# 2. Start PostgreSQL 16 Alpine container
docker compose up -d

# 3. Load environment variables into current PowerShell session (dot-source)
. .\scripts\load-env.ps1
```

### 3. Run Build & Verify All 120 Tests
```powershell
cd backend

# Execute all automated tests
.\mvnw.cmd test

# Launch the search engine server (runs on port 8080)
.\mvnw.cmd spring-boot:run
```
*(On Linux/macOS, use `./mvnw test` and `./mvnw spring-boot:run`)*.

---

## 📚 Technical Documentation Directory

- **[docs/PHASE_1_COMPLETE_GUIDE.md](file:///d:/projects/docs/PHASE_1_COMPLETE_GUIDE.md):** Foundation, Health Services, Docker & JDBC Architecture.
- **[docs/PHASE_2_COMPLETE_GUIDE.md](file:///d:/projects/docs/PHASE_2_COMPLETE_GUIDE.md):** Web Crawler, RFC 9309 Politeness & Loom Concurrency.
- **[docs/PHASE_3_COMPLETE_GUIDE.md](file:///d:/projects/docs/PHASE_3_COMPLETE_GUIDE.md):** Inverted Index, Porter Stemmer, Positional Search & Boolean Retrieval.
- **[docs/ARCHITECTURE.md](file:///d:/projects/docs/ARCHITECTURE.md):** Layered architecture and component interactions.
- **[docs/PROJECT_OVERVIEW.md](file:///d:/projects/docs/PROJECT_OVERVIEW.md):** Motivation, pipeline, and tech rationale.
