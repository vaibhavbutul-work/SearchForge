# SearchForge Phase 14 — Backend Integration Test Report

**Execution Date:** 2026-11-26  
**Test Suite:** [BackendIntegrationTest.java](file:///d:/projects/backend/src/test/java/com/searchforge/integration/BackendIntegrationTest.java)  
**Environment:** Spring Boot `4.1.2`, Java `21.0.5`, H2 (`MODE=PostgreSQL`), `WireMock` (external HTTP only)

---

## 1. Test Workflows & Observed Results (`6 / 6 Passed`)

| Workflow | Scope Tested (Zero Internal Mocks) | Result |
| :--- | :--- | :--- |
| **A. API-to-Service** | `SearchController` query/mode validation (`400 Bad Request`), `RankedSearchResponse` (`totalHits`, `explain=true`), `CrawlerController` `Pageable` pagination (`page=0&size=1`). | **PASS** |
| **B. Service-to-Repository** | `CrawledDocumentRepository` unique URL constraint (`DataIntegrityViolationException`), `IncrementalIndexUpdateEngine.updateDocument` (DB + RAM), `DocumentDeletionService.deleteDocument` (purging `ActiveDocumentFilter` + `VectorStorage`). | **PASS** |
| **C. Search Pipeline** | BM25 term-frequency ranking order, `<mark>` highlighting in `highlightedSnippet`, and `limit=1` vs `limit=10` rank-order preservation. | **PASS** |
| **D. Crawler-to-Index** | Local `WireMock` HTTP server serving HTML `/seed` (`200`) and `/broken` (`500`). Verified valid page persisted & indexed, HTTP `500` link did not create invalid document, and `GET /api/v1/search` retrieved `/seed`. | **PASS** |
| **E. Semantic Search** | `DocumentEmbeddingService` 384-D vector storage (`DocumentEmbeddingRepository` + `VectorStorage`), `mode=semantic` retrieval, and lexical search continuing to work when a document lacks an embedding. | **PASS** |
| **F. Hybrid Search** | `mode=hybrid` RRF fusion across lexical + semantic branches without duplicate results, and graceful handling when only one branch has hits. | **PASS** |

---

## 2. Integration Defect Discovered & Fixed

* **Defect (`DocumentEmbeddingService.java`):**
  * During Workflow B (`IncrementalIndexUpdateEngine.updateDocument` -> `DocumentEmbeddingService.generateAndStoreEmbedding`), updating an existing document's embedding inside a single `@Transactional` block threw `DataIntegrityViolationException: Unique index or primary key violation on PUBLIC.DOCUMENT_EMBEDDINGS(DOCUMENT_ID)` followed by `UnexpectedRollbackException`.
  * **Root Cause:** Hibernate's `ActionQueue` executes `INSERT` statements **before** `DELETE` statements on flush. Calling `embeddingRepository.delete(existing)` followed by `embeddingRepository.save(newEntity)` without flushing caused the `INSERT` to collide with the still-pending `DELETE`.
  * **Fix:** Added `embeddingRepository.flush()` immediately after `embeddingRepository.delete(existing)` in [DocumentEmbeddingService.java](file:///d:/projects/backend/src/main/java/com/searchforge/semantic/retrieval/DocumentEmbeddingService.java#L100-L105).
