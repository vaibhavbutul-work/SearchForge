# SearchForge Indexing & Corpus Management API Reference

Base URL: `http://localhost:8080/api/v1/index`

---

## 1. Synchronize Corpus

Scans `crawled_documents` for unindexed rows, processes tokens, updates inverted postings, and updates in-memory RAM structures.

* **Method:** `POST`
* **Path:** `/api/v1/index/build`
* **Query Parameters:**
  * `batchSize` (int, optional, default: 100): Batch chunk size.

### Example Response (`200 OK`)
```json
{
  "documentsProcessed": 16,
  "documentsIndexed": 16,
  "documentsSkipped": 0,
  "totalTokensIndexed": 21406,
  "durationMs": 2913
}
```

---

## 2. Rebuild Inverted Index

Drops all persistent index tables, clears in-memory structures, and indexes all crawled documents from scratch.

* **Method:** `POST`
* **Path:** `/api/v1/index/rebuild`

### Example Response (`200 OK`)
```json
{
  "documentsProcessed": 22,
  "documentsIndexed": 22,
  "documentsSkipped": 0,
  "totalTokensIndexed": 21674,
  "durationMs": 3120
}
```

---

## 3. Warm Up In-Memory RAM Index

Loads existing postings and document lengths from PostgreSQL into RAM `InvertedIndex` for sub-millisecond retrieval.

* **Method:** `POST`
* **Path:** `/api/v1/index/warmup`

### Example Response (`200 OK`)
```json
{
  "status": "SUCCESS",
  "message": "In-memory inverted index successfully warmed up from persistent storage"
}
```

---

## 4. Get Index Statistics

Returns corpus-wide metrics, vocabulary sizes, postings count, and average document length.

* **Method:** `GET`
* **Path:** `/api/v1/index/stats`

### Example Response (`200 OK`)
```json
{
  "totalIndexedDocuments": 22,
  "vocabularySize": 1999,
  "totalPostings": 4526,
  "averageDocumentLength": 985.18,
  "inMemoryDocuments": 22,
  "inMemoryVocabularySize": 1999
}
```

---

## 5. Boolean Search (Unranked)

Evaluates raw Boolean query expressions and returns matching document summaries without BM25 scoring.

* **Method:** `GET`
* **Path:** `/api/v1/search/boolean`
* **Query Parameters:**
  * `q` (string, required): Boolean query expression (e.g. `java AND microservices`).
  * `limit` (int, optional, default: 20): Results limit.
