# SearchForge Data Flow & Invariants

This document details the lifecycle of data moving through SearchForge across its three primary modes: **Crawling**, **Indexing**, and **Ranked Query Execution**.

---

## 1. End-to-End Data Flow Lifecycles

### A. The Crawling Lifecycle
```mermaid
sequenceDiagram
    autonumber
    participant Client as REST Client
    participant Ctrl as CrawlerController
    participant Engine as CrawlEngine
    participant Frontier as UrlFrontier
    participant Robots as RobotsTxtService
    participant Limiter as PolitenessRateLimiter
    participant Fetcher as DocumentFetcher
    participant Parser as HtmlParser
    participant DB as CrawledDocumentRepository

    Client->>Ctrl: POST /api/v1/crawler/start {seedUrls}
    Ctrl->>Engine: startAsync(seedUrls)
    loop BFS Worker Execution
        Engine->>Frontier: poll()
        Frontier-->>Engine: CrawlTask(url, depth)
        Engine->>Robots: isAllowed(url)
        Robots-->>Engine: true / false
        opt If allowed
            Engine->>Limiter: delayIfNeeded(domain, 1000ms)
            Engine->>Fetcher: fetch(url)
            Fetcher-->>Engine: FetchedDocument(html, 200 OK)
            Engine->>Parser: parse(fetchedDoc)
            Parser-->>Engine: ParsedDocument(text, hash, links)
            Engine->>DB: save(CrawledDocument)
            opt If depth < maxDepth
                Engine->>Frontier: enqueue(link, depth + 1)
            end
        end
    end
```

### B. The Indexing Lifecycle
```mermaid
sequenceDiagram
    autonumber
    participant Admin as Admin / API
    participant IndexCtrl as IndexController
    participant IndexEngine as DefaultIndexingEngine
    participant DB as CrawledDocumentRepository
    participant Tokenizer as StandardTokenizer
    participant Stemmer as PorterStemmer
    participant RAM as InvertedIndex (RAM)
    participant Tables as PostgreSQL Postings Tables

    Admin->>IndexCtrl: POST /api/v1/index/build?batchSize=100
    IndexCtrl->>IndexEngine: synchronizeCorpus(100)
    IndexEngine->>DB: findUnindexedDocuments(pageable)
    DB-->>IndexEngine: List<CrawledDocument>
    loop For each CrawledDocument
        IndexEngine->>Tokenizer: tokenize(title + " " + cleanText)
        Tokenizer->>Stemmer: stem(token)
        IndexEngine->>RAM: addPosting(term, docId, positions)
        IndexEngine->>Tables: save(Term, TermPosting, DocumentMetadata)
    end
    IndexEngine-->>IndexCtrl: CorpusSyncResult(indexedCount, tokens, durationMs)
```

### C. The Search Query Lifecycle
```mermaid
sequenceDiagram
    autonumber
    actor User as User / Application
    participant Ctrl as SearchController
    participant Engine as DefaultRankedRetrievalEngine
    participant RAM as InvertedIndex
    participant Scorer as Bm25Scorer
    participant Queue as MinHeap (PriorityQueue)
    participant Repo as CrawledDocumentRepository

    User->>Ctrl: GET /api/v1/search?q="virtual threads"&limit=10&explain=true
    Ctrl->>Engine: search(query, limit)
    Engine->>RAM: getPostings(term) for each query term
    RAM-->>Engine: PostingsList
    Engine->>Engine: Generate Candidate Union (TAAT)
    loop For each candidate docId
        Engine->>Scorer: score(docId, queryTerms)
        Scorer-->>Engine: ScoredDocument(docId, score, explanations)
        Engine->>Queue: offer(scoredDoc)
    end
    Queue-->>Engine: Top-K Document IDs sorted desc
    Engine->>Repo: findAllById(topKIds)
    Repo-->>Engine: List<CrawledDocument>
    Engine-->>Ctrl: List<RankedSearchResult>
    Ctrl-->>User: RankedSearchResponse JSON (< 1ms)
```

---

## 2. Core Architectural Invariants

1. **Zero-I/O Search Invariant:** No SQL database queries are executed during BM25 candidate scoring. Postings lists, document frequencies, and document lengths reside in RAM (`InvertedIndex`), guaranteeing sub-millisecond scoring execution.
2. **Positional Invariant:** Stop-words increment the token position counter even though they are omitted from the index dictionary. This preserves positional distance so that phrase searches like `"spring boot"` correctly verify adjacency ($\Delta pos = 1$).
3. **Monotonic IDF Invariant:** Robertson-Spärck Jones IDF is guaranteed strictly non-negative:
   $$\text{IDF}(t) = \ln\left(1 + \frac{N - n(t) + 0.5}{n(t) + 0.5}\right) \ge 0$$
   This eliminates the classic BM25 flaw where common words receive negative scores.
4. **Decoupled Crawl & Index Invariant:** Crawling writes exclusively to `crawled_documents`. Indexing executes asynchronously in chunked batches, isolating network I/O from indexing CPU spikes.
