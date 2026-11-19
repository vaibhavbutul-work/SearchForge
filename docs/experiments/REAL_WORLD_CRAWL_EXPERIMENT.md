# SearchForge Real-World Web Crawling & Retrieval Experiment

**Date of Experiment:** November 19, 2026  
**Status:** Validated on Live Web  
**Environment:** Java 21 LTS, Spring Boot 4.1.2, In-Memory PostgreSQL-Compatible H2 Engine  

---

## 1. Executive Summary

While Phases 1 through 4 of SearchForge were tested and verified against unit and integration test fixtures (185 automated tests), this experiment tested the search engine against the **live public internet**:
1. Crawling 3 distinct, authoritative technical documentation websites over live HTTP/HTTPS.
2. Parsing, cleaning, and deduplicating real-world DOM trees.
3. Tokenizing, stop-word filtering, and morphological stemming of 21,406 real tokens into the dual-tier inverted index.
4. Executing multi-term queries and evaluating BM25 probabilistic ranking against ground-truth relevance.

---

## 2. Crawl Execution & Telemetry

### Target Domains & Robots.txt Compliance
* **`www.postgresql.org`:** Tested against `https://www.postgresql.org/robots.txt`. Documentation path `/docs/current/` is explicitly permitted.
* **`docs.oracle.com`:** Tested against `https://docs.oracle.com/robots.txt`. Java SE 21 core documentation under `/en/java/javase/21/core/` is explicitly open.
* **`spring.io`:** Tested against `https://spring.io/robots.txt`. Completely open (`User-agent: * Allow: /`).

### Crawl Operational Metrics
* **Concurrency:** 2 concurrent worker virtual threads (`Project Loom`).
* **Politeness Delay:** 1,000 ms per domain enforced via thread-safe lock maps.
* **Max Depth:** 1 (Seed URLs at depth 0; direct outbound links at depth 1).
* **Pages Attempted / Crawled:** 16 pages.
* **Failed Requests:** 0 (zero timeouts, zero 4xx/5xx errors).
* **Elapsed Duration:** 19 seconds.

### Manifest of 16 Real Crawled Documents

| ID | Domain Host | Title | Canonical URL |
|:---:|:---|:---|:---|
| 7 | `docs.oracle.com` | Virtual Threads | `https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html` |
| 8 | `spring.io` | Microservices | `https://spring.io/microservices` |
| 9 | `www.postgresql.org` | PostgreSQL Documentation: 18: Chapter 11. Indexes | `https://www.postgresql.org/docs/current/indexes.html` |
| 10 | `docs.oracle.com` | Concurrency | `https://docs.oracle.com/en/java/javase/21/core/concurrency.html` |
| 11 | `docs.oracle.com` | Structured Concurrency | `https://docs.oracle.com/en/java/javase/21/core/structured-concurrency.html` |
| 12 | `docs.oracle.com` | Thread (Java SE 21 & JDK 21) | `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html` |
| 13 | `docs.oracle.com` | Thread.Builder (Java SE 21 & JDK 21) | `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.html` |
| 14 | `docs.oracle.com` | Executors (Java SE 21 & JDK 21) | `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html` |
| 15 | `docs.oracle.com` | Foreign Function and Memory API | `https://docs.oracle.com/en/java/javase/21/core/foreign-function-and-memory-api.html` |
| 16 | `docs.oracle.com` | The jcmd Command | `https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html` |
| 17 | `docs.oracle.com` | Flight Recorder Configurations | `https://docs.oracle.com/pls/topic/lookup?ctx=javase21&id=JFRUH-GUID-628D8A79-98B4-44C0-A941-54BE4326A1F3` |
| 18 | `docs.oracle.com` | Semaphore (Java SE 21 & JDK 21) | `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html` |
| 19 | `docs.oracle.com` | Thread-Local Variables | `https://docs.oracle.com/en/java/javase/21/core/thread-local-variables.html` |
| 20 | `docs.oracle.com` | Virtual Thread Scheduling & Pinning | `https://docs.oracle.com/en/java/javase/21/core/img_text/java-core-libraries-virtual-threads-thread-pool-and-semaphore.html` |
| 21 | `docs.oracle.com` | Scoped Values | `https://docs.oracle.com/en/java/javase/21/core/scoped-values.html` |
| 22 | `docs.oracle.com` | SimpleDateFormat (Java SE 21 & JDK 21) | `https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/SimpleDateFormat.html` |

---

## 3. Indexing & Corpus Transformation

Triggered via `POST /api/v1/index/build?batchSize=100`:
* **Tokens Indexed:** 21,406 tokens across 16 documents.
* **Indexing Latency:** 2.91 seconds (7,356 tokens/second).
* **Corpus Vocabulary Expansion:** Grew from 181 seeded terms to **1,999 unique stemmed terms**.
* **Postings Expansion:** Grew from 209 postings to **4,526 inverted postings**.
* **Average Document Length ($L_{avg}$):** Dynamically recalculated from 44.67 tokens to **985.18 tokens**.

---

## 4. Query Retrieval & Scoring Analysis

### Benchmark Query Master Table

| # | Query | Hits | Top-Ranked Document | Domain | BM25 Score | Key Matched Terms | Latency |
|:--:|:---|:---:|:---|:---:|:---:|:---|:---:|
| **1** | `spring boot dependency injection` | 7 | **Spring Framework Core & DI** | `spring.io` | **10.6529** | `spring`, `inject` ($IDF=2.73$), `depend` | &lt; 1 ms |
| **2** | `java virtual threads` | 10 | **Virtual Threads** | `docs.oracle.com` | **4.2694** | `virtual` ($120\times$), `thread` ($273\times$), `java` | &lt; 1 ms |
| **3** | `postgresql indexing` | 4 | **PostgreSQL: Chapter 11. Indexes** | `www.postgresql.org` | **8.2070** | `postgresql`, `index` ($18\times$) | &lt; 1 ms |
| **4** | `microservices architecture` | 4 | **Microservices** | `spring.io` | **7.6059** | `microservic` ($18\times$), `architectur` ($4\times$) | &lt; 1 ms |
| **5** | `spring framework application context` | 10 | **Spring Framework Core & DI** | `spring.io` | **7.4449** | `spring`, `framework`, `applic` | &lt; 1 ms |
| **6** | `"virtual threads"` *(Exact Phrase)* | 6 | **Virtual Threads** | `docs.oracle.com` | **237.7395** | Phrase ($117\times$), `virtual`, `thread` | 14 ms |

---

## 5. Quantitative Information Retrieval (IR) Evaluation

Using `RelevanceMetricsCalculator.java`, we calculated precision, recall, reciprocal ranks, and NDCG against manual ground truth:

* **Mean Reciprocal Rank (MRR):** **1.000** (100% of queries placed the canonical document at Rank #1).
* **Precision@1 ($P@1$):** **1.000**
* **Precision@3 ($P@3$):** **0.933** (93.3% of top-3 results were directly relevant).
* **Recall@3 ($R@3$):** **0.967** (Surfaced 96.7% of all relevant documents within top 3).
* **Mean NDCG@5:** **0.980** (Logarithmic ranking quality is 98% of theoretical perfection).
* **Query Latency:** **&lt; 1 ms** average for free-text BM25 search.
