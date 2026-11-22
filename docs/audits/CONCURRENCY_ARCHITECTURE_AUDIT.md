# SearchForge: Concurrency & Reliability Architecture Audit
## Phase 9 — Task 1 Deliverable

**Date:** 2026-11-21  
**Author:** SearchForge Architecture & Reliability Engineering  
**Baseline Test Suite:** 497 Tests Passing (484 Backend + 13 Frontend, 100% Hermetic)  
**Status:** Complete Audit Specification  

---

## 1. Concurrency Architecture Overview

SearchForge operates across two asynchronous execution contexts:
1. **HTTP Request Threads (Spring Boot / Tomcat):** Handle inbound REST requests (`/api/v1/search`, `/api/v1/lifecycle/**`, `/api/v1/crawler/**`).
2. **Java 21 Project Loom Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`):** In `CrawlEngine`, lightweight virtual workers fetch web pages, follow outbound links, parse DOM trees, and submit documents for indexing.

SearchForge's storage model is a **hybrid dual-state architecture**:
- **Disk Tier (Relational PostgreSQL 16 / H2):** Authoritative persistent store (`crawled_documents`, `indexed_terms`, `term_postings`, `indexed_document_metadata`, `document_embeddings`).
- **Memory Tier (RAM):** High-speed sub-millisecond query structures (`InMemoryInvertedIndex` with dictionary and positional postings, and `InMemoryVectorStorage` with 384-dimensional dense vectors).

The fundamental architectural challenge of Phase 9 is to guarantee that **these two tiers never disagree (zero split-brain states)** and that **concurrent reads and writes never observe partial, corrupt, or inconsistent states**.

```
                        [Client HTTP Search Queries]       [Web Crawler Virtual Workers]
                                    │                                    │
                                    ▼                                    ▼
                         (Tomcat Request Threads)             (Project Loom Workers)
                                    │                                    │
                ┌───────────────────┴────────────────────────────────────┤
                ▼                                                        ▼
      [HybridSearchService]                                     [CrawlEngine & Frontier]
                │                                                        │
                ├────────────────────────┐                               ▼
                ▼                        ▼                     [TransactionalCoordinator]
       {In-Memory Inverted}     {In-Memory Vector}                       │
        (Dictionary + PL)       (384-D VectorMap)              Stage 1: NLP & ONNX (No Locks)
                │                        │                               │
                └───────────┬────────────┘                     Stage 2: Relational TX (@Transactional)
                            │                                            │
                            ▼                                  Stage 3: In-Memory Swap (RAM)
                  [Ranked Search Result]                                 │
                                                                         ▼
                                                           [Consistent Dual-Tier State]
```

---

## 2. Shared Mutable State Inventory

Every shared mutable object in SearchForge was inspected. The table below catalogs their class location, storage structure, lifetime, and access profile:

| Subsystem | Class Name | Shared Mutable Fields | Data Structure | Lifetime | Accessing Threads |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Crawler** | `CrawlEngine` | `isRunning`, `pagesCrawled`, `pagesFailed`, `visitedContentHashes`, `state` | `AtomicBoolean`, `AtomicInteger`, `ConcurrentHashMap.newKeySet()`, `AtomicReference` | Singleton | Crawler virtual threads, Admin controller |
| **Crawler** | `UrlFrontier` | `queue` | `ConcurrentLinkedQueue<CrawlTask>` | Singleton | Multiple concurrent crawler workers |
| **Crawler** | `VisitedFilter` | `memoryVisited` | `ConcurrentHashMap.newKeySet()` | Singleton | All crawler workers, frontier enqueue |
| **Crawler** | `PolitenessRateLimiter` | `domainLocks`, `lastAccessTimes` | `ConcurrentHashMap<String, Object>`, `ConcurrentHashMap<String, Long>` | Singleton | All crawler workers |
| **Indexer** | `InMemoryInvertedIndex` | `dictionary`, `docLengths`, `totalTokens` | `ConcurrentHashMap<String, PostingsList>`, `ConcurrentHashMap<Long, Integer>`, `AtomicLong` | Singleton | All search threads (readers), Indexing threads (writers) |
| **Indexer** | `PostingsList` | `postings`, `totalTermFrequency` | `ArrayList<Posting>`, `long` | Instance (per term) | Multiple search threads reading, Indexer adding postings |
| **Semantic** | `InMemoryVectorStorage` | `vectorMap` | `ConcurrentHashMap<Long, DocumentVector>` | Singleton | Semantic search threads (readers), Indexing coordinator (writers) |
| **Semantic** | `QueryEmbeddingCache` | `lruMap`, `hitCount`, `missCount`, `evictionCount` | `LinkedHashMap<String, float[]>`, `AtomicLong` | Singleton | All search threads executing semantic queries |
| **Lifecycle**| `LifecycleMetricsCollector`| `documents*` counters, `total*Ms` timings | `AtomicLong`, `LongAdder` | Singleton | All indexing workers, background repair, admin telemetry |
| **Relational**| `CrawledDocument` | `version`, `status`, `title`, `cleanText`, `contentHash` | JPA Entity Fields | Per Transaction | Worker threads via Spring Data JPA repositories |

---

## 3. Thread Access Inventory

| Thread Pool / Context | Concurrency Level | Operations Executed | Primary Synchronization Mechanism |
| :--- | :--- | :--- | :--- |
| **Tomcat HTTP Threads** | 10–200 concurrent threads | Execute `/api/v1/search`, query autocomplete, spelling corrections, admin probes | `ConcurrentHashMap` reads, synchronized blocks in cache |
| **Project Loom Virtual Threads** | Configurable (default: 4–16 concurrent workers) | Pop URLs from `UrlFrontier`, fetch HTTP, parse HTML, check politeness, persist document | Lock-free queue polling, fine-grained domain locks |
| **Lifecycle Transaction Worker** | Single or parallel workers | Tokenization, ONNX inference, JPA transaction commit, in-memory sync | Staged two-phase pipeline (`@Transactional`) |
| **Admin REST Controllers** | Unbounded HTTP threads | `/api/v1/lifecycle/reindex/{id}`, `/delete/{id}`, `/repair` | Direct service invocation, delegated transactions |

---

## 4. Analysis of Existing Synchronization Mechanisms

1. **`ConcurrentHashMap` Usage:**
   - Applied broadly in `InMemoryInvertedIndex` (dictionary and docLengths), `InMemoryVectorStorage` (vectorMap), `VisitedFilter` (memoryVisited), and `PolitenessRateLimiter` (domainLocks).
   - *Strengths:* Lock-free reads, striped bucket locking for writes. High throughput.
   - *Weakness:* `ConcurrentHashMap` guarantees atomicity **only for single-key operations** (`put`, `computeIfAbsent`). Compound operations across multiple keys (e.g., updating 50 postings in the inverted index for a single document) are **not atomic**.
2. **`PostingsList` Synchronization:**
   - Methods `addPosting()`, `removePosting()`, `size()`, `getPostings()` are annotated with Java `synchronized`.
   - *Strengths:* Guards the internal `ArrayList<Posting>` from concurrent corruption.
   - *Weakness:* Thread contention on single high-frequency terms (e.g., `"java"`, `"the"`). `iterator()` creates a complete defensive copy via `List.copyOf(postings)`, increasing allocation rate under read load.
3. **`QueryEmbeddingCache` Synchronization:**
   - Uses a private monitor lock `synchronized (lock)` around a `LinkedHashMap`.
   - *Strengths:* Strictly guarantees LRU entry eviction correctness.
   - *Weakness:* Coarse-grained locking forces read operations to wait for other read operations.
4. **`AtomicLong` & `LongAdder` Usage:**
   - `LifecycleMetricsCollector` utilizes `LongAdder` for latency accumulation and `AtomicLong` for event counters.
   - *Verdict:* Best-practice, low-contention design. No changes needed.

---

## 5. Potential Race Conditions & Concurrency Vulnerabilities

### Finding 1: Non-Atomic In-Memory Document Postings Replacement
- **Severity:** 🔴 **Critical**
- **Affected Class:** `com.searchforge.indexer.index.InMemoryInvertedIndex`
- **Mechanism:** In `addDocument(documentId, tokens)`, the method updates `docLengths` and `totalTokens` first, and then iterates through terms one by one:
  ```java
  for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
      PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
      postingsList.addPosting(new Posting(documentId, positions.size(), positions));
  }
  ```
- **Failure Scenario:** A search query executing in a Tomcat thread runs while a document with 100 terms is at term 40. The search query observes an inconsistent intermediate state: the document length is already registered as 100, average document length has drifted, but 60 terms are missing from the index.
- **Phase 9 Target:** **Task 2 & Task 6 (Atomic In-Memory Index Updates)**.

### Finding 2: Lack of Optimistic Locking on `CrawledDocument`
- **Severity:** 🔴 **Critical**
- **Affected Class:** `com.searchforge.crawler.model.CrawledDocument` & `TransactionalIndexingCoordinator`
- **Mechanism:** `CrawledDocument` maintains an `int version` column, but does **not** have the JPA `@Version` annotation.
- **Failure Scenario:**
  1. Worker A dequeues a URL to re-index and reads `doc.version = 1`.
  2. Worker B (or an admin reindex API call) reads `doc.version = 1` for the same document.
  3. Worker A completes Stage 1 ONNX inference (3ms) and commits `doc.version = 2` with Content A.
  4. Worker B completes Stage 1 and commits `doc.version = 2` with Content B.
  5. Worker B silently overwrites Worker A's update. Zero error or conflict was detected.
- **Phase 9 Target:** **Task 4 & Task 5 (Database Concurrency Control & Optimistic Locking)**.

### Finding 3: Transaction Boundary Discrepancy & Connection Starvation
- **Severity:** 🟠 **High**
- **Affected Class:** `IncrementalIndexUpdateEngine` vs `TransactionalIndexingCoordinator`
- **Mechanism:** `TransactionalIndexingCoordinator` correctly keeps ONNX inference in Stage 1 outside the `@Transactional` boundary. In contrast, `IncrementalIndexUpdateEngine.updateDocument()` is annotated with `@Transactional`, and inside the active transaction executes `embeddingService.generateAndStoreEmbedding(...)`.
- **Failure Scenario:** During heavy concurrent incremental updates, database connections are held open while the CPU computes tensor dot products (2.5ms–10ms). This can saturate the HikariCP connection pool, starving unrelated search or health check requests.
- **Phase 9 Target:** **Task 5 (Database Concurrency Control & Transaction Safety)**.

### Finding 4: In-Memory Search Isolation Failure During Document Deletion
- **Severity:** 🟠 **High**
- **Affected Class:** `DefaultRankedRetrievalEngine`, `ExactVectorSimilarityEngine`, `DocumentDeletionService`
- **Mechanism:** `DocumentDeletionService` marks a document as `DELETING` in the database, then purges postings from `InMemoryInvertedIndex` and vectors from `InMemoryVectorStorage`. However, `DefaultRankedRetrievalEngine` and `ExactVectorSimilarityEngine` scan in-memory postings and vectors without validating whether the document ID is in `ACTIVE` state.
- **Failure Scenario:** If a query runs after the document is marked `DELETING` in PostgreSQL but before in-memory postings have finished purging, the document appears in search results.
- **Phase 9 Target:** **Task 6 & Task 7 (Search During Updates & Deletions)**.

### Finding 5: Premature Visited Marking in Crawler Frontier
- **Severity:** 🟡 **Medium**
- **Affected Class:** `UrlFrontier`, `VisitedFilter`
- **Mechanism:** In `UrlFrontier.enqueue()`, `visitedFilter.markVisited(normalizedUrl)` is called before the URL is added to the queue. If the crawl fails due to a network timeout or binary rejection, the URL remains in `memoryVisited` permanently, preventing any retry mechanism from processing it.
- **Phase 9 Target:** **Task 3 (Concurrent Crawl Protection & URL Deduplication)**.

### Finding 6: Memory/Database Split-Brain on Post-Commit Failure
- **Severity:** 🟡 **Medium**
- **Affected Class:** `TransactionalIndexingCoordinator`
- **Mechanism:** In Stage 3 (post-commit memory sync), if an unexpected exception occurs while adding the vector to `vectorStorage.put()`, it logs a warning but leaves the database with the new vector and RAM with the old vector.
- **Phase 9 Target:** **Task 8 (Crash Recovery, Failure Injection & Reliability)**.

### Finding 7: Coarse-Grained Monitor Lock on Query Embedding Cache
- **Severity:** 🟢 **Low / Informational**
- **Affected Class:** `QueryEmbeddingCache`
- **Mechanism:** A single `synchronized(lock)` guards all `get` and `put` operations on the LRU map. Under hundreds of concurrent queries, read threads contend for this monitor lock.
- **Phase 9 Target:** **Task 2 (Thread Safety & Shared State Protection)**.

---

## 6. Deadlock Risks & Lock-Ordering Matrix

Deadlocks occur when two threads acquire multiple locks in opposite order (Thread 1: Lock A $\to$ Lock B; Thread 2: Lock B $\to$ Lock A).

### SearchForge Lock Hierarchy Guidelines
To guarantee zero deadlocks across Phase 9, all components must respect the following strict acquisition order:

$$\text{Domain Politeness Lock} \longrightarrow \text{Relational Database Lock} \longrightarrow \text{In-Memory Component Lock}$$

1. **Rule 1:** A thread holding an in-memory lock (e.g., `PostingsList` monitor or `QueryEmbeddingCache` monitor) **must never** initiate a database query or acquire a database transaction.
2. **Rule 2:** Expensive CPU operations (WordPiece tokenization, ONNX neural inference) **must never** hold database connection locks or dictionary locks.
3. **Rule 3:** Document updates affecting multiple terms must never acquire individual `PostingsList` locks in arbitrary order without deterministic ordering (e.g., sorting term strings lexicographically).

---

## 7. Prioritized Vulnerability & Task Mapping

| Rank | Severity | Risk Code | Vulnerability Description | Planned Remediation Task |
| :---: | :---: | :---: | :--- | :---: |
| **1** | **Critical** | `RISK-INDEX-ATOMIC` | Non-atomic term-by-term in-memory posting swap | **Task 2 & Task 6** |
| **2** | **Critical** | `RISK-ENTITY-VERSION` | Lost updates due to missing JPA `@Version` on `CrawledDocument` | **Task 4 & Task 5** |
| **3** | **High** | `RISK-TX-CONNECTION` | DB connection held during ONNX model inference | **Task 5** |
| **4** | **High** | `RISK-SEARCH-ISOLATION`| Search engines observing `DELETING` or `FAILED` documents | **Task 6 & Task 7** |
| **5** | **Medium** | `RISK-URL-INFLIGHT` | Premature visited marking & duplicate in-flight URL crawls | **Task 3** |
| **6** | **Medium** | `RISK-SPLIT-BRAIN` | Ungraceful crash desynchronizing PostgreSQL and in-memory index | **Task 8** |
| **7** | **Low** | `RISK-CACHE-CONTENTION`| Coarse monitor lock on Query Embedding Cache | **Task 2** |

---

## 8. Verification Strategy for Task 1

Task 1 is an audit deliverable and does not modify production code. Its verification requires:
1. **Factual Codebase Grounding:** Every vulnerability cited corresponds directly to verified source lines in `com.searchforge.*`.
2. **Actionable Roadmap Alignment:** Every identified finding maps cleanly to an upcoming task in Phase 9 (Tasks 2 through 9).
3. **Hermetic Test Suite Stability:** Verified that all 497 existing automated tests remain 100% passing.

---

*Task 1 Concurrency Architecture Audit is complete and documented.*
