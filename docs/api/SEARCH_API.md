# SearchForge Search API Reference

Base URL: `http://localhost:8080/api/v1/search`

---

## 1. Free-Text BM25 Ranked Retrieval (Mode A)

Executes pure disjunctive (OR) BM25 ranked retrieval across the entire indexed corpus.

* **Method:** `GET`
* **Path:** `/api/v1/search`
* **Query Parameters:**
  * `q` (string, required): Free-text query (e.g. `spring boot dependency injection`).
  * `limit` (int, optional, default: 10): Maximum number of top ranked results to return.
  * `explain` (boolean, optional, default: false): Returns score breakdown per matched term.

### Example Request
```http
GET /api/v1/search?q=java+virtual+threads&limit=5&explain=true HTTP/1.1
Host: localhost:8080
```

### Example Response (`200 OK`)
```json
{
  "query": "java virtual threads",
  "totalHits": 10,
  "returnedHits": 5,
  "executionTimeMs": 0,
  "results": [
    {
      "docId": 7,
      "url": "https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html",
      "title": "Virtual Threads",
      "domain": "docs.oracle.com",
      "snippet": "Virtual threads are lightweight threads that reduce the effort of writing, maintaining...",
      "score": 4.2694,
      "rank": 1,
      "matchedTerms": ["java", "thread", "virtual"],
      "explanation": {
        "virtual": { "rawTf": 120, "idf": 1.1206, "saturation": 2.1517, "termScore": 2.4112 },
        "thread":  { "rawTf": 273, "idf": 0.6098, "saturation": 2.1785, "termScore": 1.3284 },
        "java":    { "rawTf": 20,  "idf": 0.2733, "saturation": 1.9388, "termScore": 0.5299 }
      }
    }
  ]
}
```

---

## 2. Ranked Boolean Retrieval (Mode C)

Evaluates an AST Boolean expression (`AND`, `OR`, `NOT`, parentheses) and ranks matching candidates with BM25 and exact phrase boosting.

* **Method:** `GET`
* **Path:** `/api/v1/search/ranked-boolean`
* **Query Parameters:**
  * `q` (string, required): Boolean query string (e.g. `(java OR python) AND microservices` or exact phrase `"virtual threads"`).
  * `limit` (int, optional, default: 10): Maximum number of top ranked results to return.
  * `explain` (boolean, optional, default: false): Returns term and phrase score breakdowns.

### Example Request
```http
GET /api/v1/search/ranked-boolean?q="virtual+threads"&limit=5&explain=true HTTP/1.1
Host: localhost:8080
```

### Example Response (`200 OK`)
```json
{
  "query": "\"virtual threads\"",
  "totalHits": 6,
  "returnedHits": 5,
  "executionTimeMs": 14,
  "results": [
    {
      "docId": 7,
      "url": "https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html",
      "title": "Virtual Threads",
      "domain": "docs.oracle.com",
      "snippet": "Virtual threads are lightweight threads that reduce the effort of writing...",
      "score": 237.7395,
      "rank": 1,
      "matchedTerms": ["PHRASE: \"virtual threads\"", "thread", "virtual"],
      "explanation": {
        "virtual": { "rawTf": 120, "idf": 1.1206, "saturation": 2.1517, "termScore": 2.4112 },
        "thread":  { "rawTf": 273, "idf": 0.6098, "saturation": 2.1785, "termScore": 1.3284 },
        "PHRASE: \"virtual threads\"": { "rawTf": 1, "idf": 0.0, "saturation": 0.0, "termScore": 234.0 }
      }
    }
  ]
}
```

---

## 3. Filtered Relevance Search (Mode B)

Executes free-text BM25 search restricted strictly to candidate documents satisfying a separate Boolean filter.

* **Method:** `GET`
* **Path:** `/api/v1/search/filtered`
* **Query Parameters:**
  * `q` (string, required): Free-text query.
  * `filter` (string, required): Hard Boolean filter expression (e.g. `NOT legacy`).
  * `limit` (int, optional, default: 10): Maximum number of top ranked results to return.
  * `explain` (boolean, optional, default: false): Returns score breakdown.
