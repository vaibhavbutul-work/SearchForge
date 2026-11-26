# SearchForge Phase 14 — System Architecture Audit & Integration Test Plan

**Audit Date:** 2026-11-26  
**Scope:** Complete Repository Inspection (`backend/`, `frontend/`, `docker-compose.prod.yml`, `.github/workflows/`, `scripts/`, `monitoring/`)

---

## 1. Verified System Architecture

### 1.1 Backend (`backend/` — Java 21 LTS, Spring Boot 4.1.2)
* **HTTP Filter Chain:**
  1. `RequestCorrelationFilter` (`Ordered.HIGHEST_PRECEDENCE + 1`): Validates/generates `X-Request-Id` and binds SLF4J `MDC`.
  2. `RateLimitingFilter` (`Ordered.HIGHEST_PRECEDENCE + 5`): Token-bucket throttling via `RateLimiterRegistry` (`SEARCH` & `ADMIN` tiers) on `/api/v1/*`.
  3. `ApiKeyAuthenticationFilter` (`Ordered.HIGHEST_PRECEDENCE + 10`): Constant-time `MessageDigest.isEqual` RBAC enforcing `ROLE_ADMIN` on `/api/v1/crawler/**`, `/api/v1/lifecycle/**`, and `/api/v1/index/{build,rebuild,warmup}`.
* **REST Controllers (8):**
  * Public Search & Intelligence: `SearchController`, `SemanticSearchController`, `AutocompleteController`, `SpellingSuggestionController`, `HealthController`.
  * Administrative: `CrawlerController`, `IndexController`, `IndexLifecycleAdminController`.
  * Centralized Error Masking: `GlobalExceptionHandler` (`@RestControllerAdvice`).
* **Core Pipelines & Services:**
  * **Crawler:** `CrawlEngine` (Virtual Threads), `SafeDnsResolver`, `IpRangeValidator` (SSRF protection), `PolitenessRateLimiter`, `RobotsTxtService`, `DocumentFetcher`, `HtmlParser`, `UrlFrontier`, `UrlNormalizer`, `UrlTracker`.
  * **Text & Indexing:** `TextProcessor`, `DefaultIndexingEngine`, `InMemoryInvertedIndex`, `PositionalIndex`, `ActiveDocumentFilter`.
  * **Ranking & Search:** `Bm25Scorer`, `DefaultHybridSearchService`, `ReciprocalRankFusionEngine` ($k=60$), `WordPieceTokenizer`, `OnnxEmbeddingModel` / `DeterministicEmbeddingModel`, `DocumentEmbeddingService`, `VectorStorage`.
  * **Lifecycle & Recovery:** `DocumentLifecycleStateMachine`, `TransactionalIndexingCoordinator`, `IncrementalIndexUpdateEngine`, `DocumentDeletionService`, `IndexIntegrityChecker`, `IndexRepairService`, `StartupIndexReconciliationRunner`.
* **Persistence (5 JPA Entities & Repositories):**
  * `CrawledDocument` (`CrawledDocumentRepository`) -> `crawled_documents`
  * `IndexedDocumentMetadata` (`IndexedDocumentMetadataRepository`) -> `indexed_document_metadata`
  * `IndexedTerm` (`IndexedTermRepository`) -> `indexed_terms`
  * `TermPosting` (`TermPostingRepository`) -> `term_postings`
  * `DocumentEmbeddingEntity` (`DocumentEmbeddingRepository`) -> `document_embeddings`
  * Managed in production by Flyway (`V1__init_schema.sql`) + Hibernate `ddl-auto: validate`.

### 1.2 Frontend (`frontend/` — React 18.3, Vite 6.0, React Router 6.28)
* **Transport & Services:** `apiClient.js` (`VITE_API_BASE_URL` with nullish coalescing), `searchService.js` (`hybrid`, `lexical`, `semantic`, `boolean`, `filtered`).
* **Pages & Components:** `HomePage`, `SearchPage`, `NotFoundPage`, `SearchBar`, `SearchModeSelector`, `TopKSelector`, `SearchResultsList`, `SearchResultItem`, `RankingExplanation`, `SearchStats`, `SearchLoadingState`, `SearchEmptyState`, `SearchErrorState`, `ExampleQueries`.

### 1.3 Infrastructure & Observability
* **Production Compose (`docker-compose.prod.yml`):** `postgres:16-alpine` (internal only), `backend` (`eclipse-temurin:21-jre-jammy`, UID `1001`), `frontend` (`nginxinc/nginx-unprivileged:1.27-alpine`, UID `101`, reverse-proxying `/api/` and `/actuator/health`).
* **Observability:** `logback-spring.xml`, `SearchIndexHealthIndicator`, `EmbeddingModelHealthIndicator`, `SearchForgeMetricsBinder` (`/actuator/prometheus`), `monitoring/prometheus.yml`.

---

## 2. Component Dependency & Data-Flow Maps

### 2.1 Search Query Flow
```
User Browser
  |
  v
React Frontend (SearchPage -> searchService -> apiClient)
  |  HTTP GET /api/v1/search?q=...&mode=hybrid|lexical|semantic
  v
Nginx Reverse Proxy (:80 -> http://backend:8080, X-Real-IP, X-Forwarded-For)
  |
  v
RequestCorrelationFilter -> RateLimitingFilter -> ApiKeyAuthenticationFilter
  |
  v
SearchController
  |
  v
Search Service (DefaultHybridSearchService / BM25 / Semantic)
  |
  +---> ActiveDocumentFilter + InMemoryInvertedIndex (BM25)
  |
  +---> EmbeddingModel (ONNX / Deterministic) + VectorStorage (Cosine)
  |
  v
ReciprocalRankFusionEngine (k=60) + RankingExplanation + Snippets
  |
  v
RankedSearchResponse JSON -> React UI
```

### 2.2 Crawling-to-Search Flow
```
Seed URL (POST /api/v1/crawler/start)
  |
  v
CrawlerController -> CrawlEngine (SafeDnsResolver + IpRangeValidator + Politeness)
  |
  v
DocumentFetcher -> HtmlParser (Title & Clean Text Extraction)
  |
  v
CrawledDocumentRepository (PostgreSQL: crawled_documents)
  |
  v
TransactionalIndexingCoordinator / DefaultIndexingEngine
  |
  +---> TextProcessor -> indexed_terms + term_postings + InMemoryInvertedIndex
  |
  +---> DocumentEmbeddingService -> EmbeddingModel -> document_embeddings + VectorStorage
  |
  v
ActiveDocumentFilter -> Searchable Document
```

---

## 3. Existing Test Inventory & Coverage Gaps

### 3.1 Existing Tests Observed
* **Backend (121 test files):** `ranking` (29), `lifecycle` (15), `crawler` (14), `semantic` (14), `query` (13), `indexer` (10), `performance` (8), `security` (7), `config` (4), `search` (4), `concurrency` (2), `service` (1).
* **Frontend (2 test files):** `searchModes.test.js`, `searchService.test.js` (13 Node tests mocking `fetch`).

### 3.2 High-Risk Integration Boundaries & Missing Coverage
1. **API-to-Service-to-Repository Boundaries (Task 14.2):** Need dedicated non-mocked integration tests verifying pagination rank preservation, transaction integrity, missing-embedding resilience, and hybrid fallback when one retrieval path returns empty.
2. **Frontend-Backend Contract Integration (Task 14.3):** Frontend tests currently mock `fetch`. Need integration tests verifying real HTTP contract alignment, query intelligence endpoints, and UI/client handling of `400`, `401`, `403`, `429`, `500`, and network timeouts.
3. **Full Crawl-to-Search E2E Workflow (Task 14.4):** Need an automated E2E suite driving controlled local HTML fixtures (`WireMock`) from `CrawlEngine` -> `IndexingEngine` -> `DocumentEmbeddingService` -> `SearchController` across all 8 lifecycle scenarios (create, duplicate, update, delete, crawl failure, missing embedding, re-crawl, restart).
4. **Cross-Filter Security Integration (Task 14.5):** Need real HTTP filter-chain integration tests validating API key RBAC, rate-limit `429` + `Retry-After`, input validation, SSRF redirect/DNS blocking, and masked production errors.
5. **Deterministic Concurrency & Recovery (Task 14.6):** Need barrier/latch-synchronized tests for concurrent search + indexing + deletion, optimistic locking conflicts, and DB interruption recovery.
6. **Production Deployment & Regression Suite (Tasks 14.7–14.8):** Need systematic validation of all 7 Compose deployment scenarios and a categorized, reproducible regression suite execution guide.

---

## 4. Proposed Phase 14 Test Strategy

* **No Internal Mocking at Boundaries:** Use real Spring Boot context, real H2 (`MODE=PostgreSQL`) / Docker PostgreSQL, real `InvertedIndex`, and real `VectorStorage`; mock only external HTTP targets via `WireMock`.
* **Deterministic Synchronization:** Use `CountDownLatch` / `CyclicBarrier` with bounded timeouts instead of arbitrary `Thread.sleep`.
* **Strict Separation:** Keep fast unit/integration tests separate from slow Phase 10 benchmarks and Phase 12 relevance evaluations.
