# SearchForge Phase 14 — Crawling-to-Search End-to-End Report

**Execution Date:** 2026-11-26  
**Test Suite:** [CrawlingToSearchE2ETest.java](file:///d:/projects/backend/src/test/java/com/searchforge/integration/CrawlingToSearchE2ETest.java)  
**Test Infrastructure:** Spring Boot `4.1.2`, H2 (`MODE=PostgreSQL`), `WireMockServer` (controlled HTML fixtures)

---

## 1. Observed E2E Scenario Results (`8 / 8 Passed`)

| Scenario | Workflow Verified | Result |
| :--- | :--- | :--- |
| **A. Successful Document Indexing** | `WireMock` HTML -> `CrawlEngine` -> `POST /api/v1/index/build` -> `DocumentEmbeddingService` -> `GET /api/v1/search` (`lexical`, `semantic`, `hybrid`). | **PASS** |
| **B. Duplicate Document** | Crawling `/dup-1`, `/dup-2` (identical HTML), and `/dup-1` again yielded `1` persisted document and `totalHits=1`. | **PASS** |
| **C. Updated Document** | `IncrementalIndexUpdateEngine.updateDocument()` replaced content; old unique keyword returned `0` hits, new keyword returned `1` hit. | **PASS** |
| **D. Deleted Document** | `DocumentDeletionService.deleteDocument()` purged document; `lexical`, `semantic`, and `hybrid` all returned `totalHits=0`. | **PASS** |
| **E. Failed Crawling** | HTTP `500` and `404` responses were marked `FAILED` by `UrlTracker` without creating corrupt rows or index entries (`0` docs indexed). | **PASS** |
| **F. Missing Embedding** | Document indexed without vector embedding remained searchable via `mode=lexical` (`totalHits=1`). | **PASS** |
| **G. Re-Crawling** | Re-crawling an already visited URL preserved `1` row and `1` searchable hit without duplicate postings. | **PASS** |
| **H. Application Restart** | Cleared RAM (`InvertedIndex`, `VectorStorage`, `ActiveDocumentFilter`), ran `StartupIndexReconciliationRunner.reconcile()`, and verified `lexical`, `semantic`, and `hybrid` all returned the document again. | **PASS** |
