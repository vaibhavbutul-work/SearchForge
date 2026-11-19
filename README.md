# SearchForge 🔍
> **Build Your Own Search Engine from First Principles**

SearchForge is a custom, high-performance search engine designed and built from first principles using **Java 21 LTS**, **Spring Boot 4.1.2**, and **PostgreSQL 16**. Rather than relying on black-box search libraries, SearchForge implements every layer of modern search technology from scratch: multi-threaded web crawling, linguistic text processing, morphological stemming, inverted indexing, positional exact phrase search, and Boolean retrieval.

---

## 🛠️ Technology Stack

- **Language:** Java 21 (LTS) — Records, Sealed Interfaces, Pattern Matching, Project Loom Virtual Threads
- **Framework:** Spring Boot 4.1.2 (Spring Framework 7) — Modular WebMvc Starters, Actuator
- **Web Crawling & Extraction:** Jsoup 1.18.3 — DOM parsing, RFC 3986 URL normalization, RFC 9309 `robots.txt` compliance, 2MB size guards, DOM noise stripping, SHA-256 deduplication
- **Text Analysis & Tokenization:**
  - `TextNormalizer`: Canonical Unicode NFD decomposition, diacritic stripping, case-folding, possessive suffix removal
  - `StopWordFilter`: Thread-safe $O(1)$ lookup across 130+ English functional stop words
  - `Tokenizer`: Word boundary splitting with sequential token position tracking (preserving stop-word gaps)
  - `PorterStemmer`: Martin Porter's 1980 5-step morphological cascade (stateless, thread-safe, consonant/vowel conditions, syllable measure $m$)
- **Inverted Index Engine:**
  - `PostingsList`: Binary search insertion maintaining strictly ascending `documentId` ordering, tracking $df_t$ and $ctf_t$
  - `InMemoryInvertedIndex`: `ConcurrentHashMap` dictionary, tracking corpus metrics ($N$, $\text{avgdl}$, $|d|$)
  - `PositionalIndexSearcher`: Exact multi-word phrase intersection ($O(P_1 + P_2)$) and sliding-window proximity evaluation ($|pos_1 - pos_2| \le k$)
- **Data Persistence:** Spring Data JPA & Hibernate 7:
  - `CrawledDocument`: Crawled web pages with unique canonical URLs and SHA-256 content hashes
  - `IndexedTerm`: Persistent dictionary terms with $df$ and $ctf$
  - `TermPosting`: Relational postings with compound unique constraint on `(term_id, document_id)` and portable comma-separated position encoding
  - `IndexedDocumentMetadata`: Document token count ($|d|$) and indexed audit timestamps
- **Boolean Retrieval & Query Parsing:**
  - `BooleanQueryLexer`: Lexical tokenization (`TERM`, `PHRASE`, `AND`, `OR`, `NOT`, `LPAREN`, `RPAREN`)
  - `BooleanQueryParser`: Recursive-descent AST parser with operator precedence (`NOT` > `AND` > `OR`), parentheses overrides, and implicit `AND` for adjacent terms
  - `PostingsMerger`: Two-pointer linear merge algorithms ($O(L_1 + L_2)$) for intersection, union, and difference
  - `DefaultBooleanRetrievalEngine`: Evaluates query ASTs against RAM and positional searcher, evaluating pure negations against universe $U$
- **Database:** PostgreSQL 16 Alpine (via Docker Compose) & In-memory H2 for hermetic integration tests
- **Connection Pool:** HikariCP (`connection-timeout: 3000ms`, `initialization-fail-timeout: 0`)
- **Build & Dependency Tool:** Apache Maven 3.9.9 via Maven Wrapper (`.\mvnw.cmd` / `./mvnw`)
- **Automated Testing:** JUnit 5, Mockito 5, WireMock 3.10.0, Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`) — **118 automated tests passing**

---

## 📁 Repository Structure

```
SearchForge/
├── .gitattributes                           # Enforces LF for mvnw/sh and CRLF for cmd/bat
├── .gitignore                               # Git ignore rules (build artifacts, IDEs, .env)
├── .env.example                             # Environment variable template
├── docker-compose.yml                       # PostgreSQL 16 Alpine container definition
├── README.md                                # Project summary and engineering reference
├── backend/                                 # Java backend application root
│   ├── mvnw                                 # Maven wrapper shell script (LF, +x)
│   ├── mvnw.cmd                             # Maven wrapper Windows batch script (CRLF)
│   ├── pom.xml                              # Build configuration & dependencies
│   ├── .mvn/wrapper/
│   │   └── maven-wrapper.properties         # Wrapper config pinned to Maven 3.9.9
│   └── src/
│       ├── main/
│       │   ├── java/com/searchforge/
│       │   │   ├── SearchForgeApplication.java  # Main application entry point
│       │   │   ├── controller/                  # System health controller
│       │   │   ├── crawler/                     # Phase 2: Web Crawler Subsystem
│       │   │   │   ├── config/                  # CrawlerProperties (@ConfigurationProperties)
│       │   │   │   ├── controller/              # CrawlerController (/api/v1/crawler)
│       │   │   │   ├── dto/                     # CrawlStatusResponse, StartCrawlRequest
│       │   │   │   ├── engine/                  # CrawlEngine, Loom Virtual Worker threads
│       │   │   │   ├── fetcher/                 # DocumentFetcher (Jsoup + 2MB guard)
│       │   │   │   ├── model/                   # CrawledDocument JPA Entity
│       │   │   │   ├── parser/                  # HtmlParser (Noise stripper + SHA-256)
│       │   │   │   ├── politeness/              # RobotsTxtService (RFC 9309), RateLimiter
│       │   │   │   ├── repository/              # CrawledDocumentRepository
│       │   │   │   └── url/                     # UrlFrontier (BFS FIFO), UrlNormalizer
│       │   │   └── indexer/                     # Phase 3: Inverted Index & Retrieval Subsystem
│       │   │       ├── controller/              # IndexController (/api/v1/index, /api/v1/search/boolean)
│       │   │       ├── dto/                     # BooleanSearchResponse, IndexStatsResponse, SearchHitResponse
│       │   │       ├── entity/                  # IndexedTerm, TermPosting, IndexedDocumentMetadata
│       │   │       ├── index/                   # InvertedIndex, InMemoryInvertedIndex
│       │   │       ├── model/                   # Posting, PostingsList, TermMetadata
│       │   │       ├── repository/              # IndexedTermRepository, TermPostingRepository, MetadataRepo
│       │   │       ├── retrieval/               # BooleanRetrievalEngine, BooleanQueryParser, PostingsMerger
│       │   │       │   └── ast/                 # QueryNode (TermNode, PhraseNode, AndNode, OrNode, NotNode)
│       │   │       ├── search/                  # PositionalIndexSearcher (Phrase & Proximity)
│       │   │       ├── service/                 # IndexingEngine, DefaultIndexingEngine
│       │   │       ├── stem/                    # PorterStemmer (1980 5-step cascade), Stemmer
│       │   │       └── token/                   # Tokenizer, TextNormalizer, StopWordFilter, ProcessedToken
│       │   └── resources/
│       │       └── application.yml              # Database & Spring configuration
│       └── test/
│           ├── java/com/searchforge/            # 118 automated unit and integration tests
│           └── resources/
│               └── application.yml              # Hermetic test configuration (H2 in-memory)
├── docs/                                    # Detailed technical specifications
│   ├── PHASE_1_COMPLETE_GUIDE.md            # Phase 1: Foundation & Health Service Guide
│   ├── PHASE_2_COMPLETE_GUIDE.md            # Phase 2: Web Crawler Masterclass Guide
│   ├── PHASE_3_COMPLETE_GUIDE.md            # Phase 3: Inverted Index & Boolean Retrieval Guide
│   ├── ARCHITECTURE.md                      # Layered architecture & component interactions
│   └── PROJECT_OVERVIEW.md                  # Project motivation, pipeline, and rationale
└── scripts/
    └── load-env.ps1                         # PowerShell script to load .env into session
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Java:** JDK 21 (Eclipse Temurin 21 LTS recommended; verify with `java -version`)
- **Docker & Docker Compose:** For running the PostgreSQL container (`docker compose version`)
- **Maven:** Not required on host! The repository includes Apache Maven Wrapper 3.9.9 (`mvnw` / `mvnw.cmd`).

### 2. Configure Environment Variables
Copy `.env.example` to `.env`:
```powershell
Copy-Item .env.example .env
```
Ensure your `.env` contains:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=searchforge_db
DB_USERNAME=searchforge
DB_PASSWORD=your_secure_local_password
```

### 3. Start PostgreSQL Database
```powershell
docker compose up -d
docker compose ps
```

### 4. Load Environment Variables into Terminal Session
```powershell
# Dot-source the script in PowerShell:
. .\scripts\load-env.ps1
```

### 5. Build and Run All Automated Tests
Navigate to the `backend/` directory and use the Maven Wrapper:
```powershell
cd backend

# Execute all 118 automated tests
.\mvnw.cmd test

# Run the Spring Boot application
.\mvnw.cmd spring-boot:run
```
*(On Linux/macOS, use `./mvnw test` and `./mvnw spring-boot:run`)*.

The application starts on port `8080`.

---

## 🧪 Automated Testing Suite

SearchForge includes **118 automated tests** passing hermetically in ~18 seconds without external dependencies:

| Test Suite | Scope | Tests |
| :--- | :--- | :---: |
| `HealthControllerTest` & `HealthServiceTest` | Web slice and JDBC health check tests | 6 |
| `SearchForgeApplicationTests` | Hermetic context boot test | 1 |
| `CrawlerPropertiesTest` | Configuration properties binding & defaults | 2 |
| `UrlNormalizerTest` | RFC 3986 normalization, relative link resolution, cycle prevention | 8 |
| `VisitedFilterTest` | In-memory cache + persistent database visited filter | 5 |
| `UrlFrontierTest` | BFS FIFO queue, thread-safety, domain whitelisting | 6 |
| `RobotsTxtServiceTest` | RFC 9309 longest-match evaluation, 24h caching, WireMock HTTP tests | 9 |
| `PolitenessRateLimiterTest` | Per-domain concurrency locks and crawl delay enforcement | 5 |
| `HtmlParserTest` | Noise stripping (`script`, `style`), text extraction, SHA-256 hashing | 6 |
| `DocumentFetcherTest` | HTTP timeouts, 2MB size guards, non-crashing binary rejection | 6 |
| `CrawledDocumentRepositoryTest` | PostgreSQL repository CRUD, unique constraints, and domain filtering | 4 |
| `CrawlEngineTest` | Multi-worker virtual thread orchestration, cancellation, and deduplication | 3 |
| `CrawlerControllerTest` | Web slice tests for `/api/v1/crawler/*` endpoints | 5 |
| `TokenizerTest` | Linguistic pipeline: Unicode NFD, diacritics, stop-word gaps | 9 |
| `PorterStemmerTest` | 1980 5-step cascade, measure $m$, vowel/consonant condition checks | 5 |
| `InMemoryInvertedIndexTest` | Concurrent dictionary lookups, document length tracking, 8-thread safety | 6 |
| `PositionalIndexSearcherTest` | Positional intersection, exact phrase search, sliding-window proximity | 5 |
| `IndexPersistenceTest` | JPA entities, compound unique constraints, comma-separated positions | 6 |
| `IndexingEngineTest` | Document ingestion, idempotent re-indexing, batch corpus sync, cold warmup | 6 |
| `BooleanQueryParserTest` | Lexer & AST parser, operator precedence, implicit AND, syntax validation | 11 |
| `BooleanRetrievalEngineTest` | Boolean retrieval: AND, OR, NOT, exact phrases, nested expressions | 9 |
| `IndexControllerTest` | Web slice tests for `/api/v1/index/*` and `/api/v1/search/boolean` | 6 |

---

## 📡 REST API Reference

### 1. System Health & Probes
- `GET /api/v1/health`: Checks direct database connectivity (HTTP 200 on UP, HTTP 503 on DOWN).
- `GET /actuator/health`: Standard Spring Boot Actuator readiness and liveness probe.

### 2. Web Crawler Control (`/api/v1/crawler`)
- `POST /api/v1/crawler/start`: Asynchronously starts a crawl job (`202 Accepted`).
  ```json
  { "seedUrls": ["https://en.wikipedia.org/wiki/Information_retrieval"] }
  ```
- `POST /api/v1/crawler/stop`: Signals active workers to gracefully drain and stop (`200 OK`).
- `POST /api/v1/crawler/reset`: Resets engine state, cleared frontier, and visited cache (`200 OK`).
- `GET /api/v1/crawler/status`: Returns live telemetry (pages crawled, failed, queue size, elapsed time).
- `GET /api/v1/crawler/documents?page=0&size=10`: Returns paginated summaries of crawled web documents.

### 3. Inverted Index Management (`/api/v1/index`)
- `POST /api/v1/index/build?batchSize=100`: Synchronizes unindexed crawled pages into persistent and in-memory index tables.
- `POST /api/v1/index/rebuild`: Clears persistent index tables, in-memory structures, and rebuilds the index from scratch.
- `POST /api/v1/index/warmup`: Warms up the RAM inverted index directly from persistent PostgreSQL storage without re-tokenizing.
- `GET /api/v1/index/stats`: Returns corpus metrics ($N$, $V$, total postings, $\text{avgdl}$, and in-memory footprint).

### 4. Boolean Search Retrieval (`/api/v1/search`)
- `GET /api/v1/search/boolean?q={query}&limit={limit}`: Evaluates complex Boolean queries in sub-millisecond time.
  - **Example Query:** `GET /api/v1/search/boolean?q=(java OR python) AND "web server" NOT deprecated`
  - **Response Payload:**
    ```json
    {
      "query": "(java OR python) AND \"web server\" NOT deprecated",
      "totalHits": 2,
      "durationMs": 4,
      "hits": [
        {
          "documentId": 14,
          "url": "https://example.com/java-servers",
          "domain": "example.com",
          "title": "Modern Java Web Servers",
          "snippet": "High-performance HTTP server architectures in Java using virtual threads and non-blocking I/O..."
        }
      ]
    }
    ```

---

## 🗺️ Project Roadmap

- [x] **Phase 1: Project Setup and Backend Foundation** *(Hardened & Fully Tested — 7 Tests)*
- [x] **Phase 2: Web Crawler (Jsoup, BFS Queue, RFC 9309 Politeness, Virtual Threads, REST API)** *(Completed — 55 Tests)*
- [x] **Phase 3: Inverted Index & Tokenization Engine (Linguistic Pipeline, Porter Stemmer, Positional Index, Boolean Retrieval, REST API)** *(Completed — 118 Tests)*
- [ ] **Phase 4: Scoring, Ranking & Relevance Engine (Okapi BM25, TF-IDF, Vector Space Model, Min-Heap Top-K)**
- [ ] **Phase 5: Query Engine, Autocomplete & Search Experience**
- [ ] **Phase 6: Web Dashboard, Analytics & Deployment**
