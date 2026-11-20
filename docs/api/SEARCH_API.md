# SearchForge Search API Reference

Base URL: `http://localhost:8080/api/v1/search`

---

## 1. Free-Text BM25 Ranked Retrieval (Mode A)

Executes pure disjunctive (OR) BM25 ranked retrieval across the entire indexed corpus, enriched with automated query intelligence:
1. **Spelling Correction:** Surfaces `didYouMean` suggestions if typos are detected against the index vocabulary.
2. **Synonym Expansion:** Expands unquoted terms using configurable synonym dictionaries with discounted BM25 scoring.
3. **Dynamic Snippets & Highlighting:** Extracts the densest relevant passage and injects `<mark>` tags around query/synonym terms with full HTML sanitization.

* **Method:** `GET`
* **Path:** `/api/v1/search`
* **Query Parameters:**
  * `q` (string, required): Free-text query (e.g. `postgreql btre` or `spring boot`).
  * `limit` (int, optional, default: 10): Maximum number of top ranked results to return (1-100).
  * `explain` (boolean, optional, default: false): Returns score breakdown per matched term.

### Example Request
```http
GET /api/v1/search?q=postgreql+btre&limit=5 HTTP/1.1
Host: localhost:8080
```

### Example Response (`200 OK`)
```json
{
  "query": "postgreql btre",
  "didYouMean": "postgresql btree",
  "totalHits": 1,
  "returnedHits": 1,
  "executionTimeMs": 2,
  "results": [
    {
      "docId": 1,
      "url": "https://searchforge.dev/postgres-btree",
      "title": "PostgreSQL B-Tree Indexes",
      "domain": "searchforge.dev",
      "snippet": "PostgreSQL uses a B-tree index for high-performance equality and range queries...",
      "highlightedSnippet": "...<mark>PostgreSQL</mark> uses a <mark>B-tree</mark> index for high-performance equality and range queries...",
      "score": 4.85,
      "rank": 1,
      "matchedTerms": ["postgresql", "btree"]
    }
  ]
}
```

---

## 2. Weighted Trie Autocomplete

Suggests relevant indexed vocabulary terms in real-time as the user types prefix characters. Uses a deterministic ranking formula:
$$\text{Score}(t) = \ln(1 + \text{ctf}) \times \sqrt{\text{df}}$$
and returns the top-$K$ candidates via a bounded min-heap in $\mathcal{O}(L + M \log K)$ time.

* **Method:** `GET`
* **Path:** `/api/v1/search/autocomplete`
* **Query Parameters:**
  * `prefix` (string, required): Query prefix typed by the user (e.g. `post`).
  * `limit` (int, optional, default: 5): Maximum suggestions to return (1-100).

### Example Request
```http
GET /api/v1/search/autocomplete?prefix=post&limit=3 HTTP/1.1
Host: localhost:8080
```

### Example Response (`200 OK`)
```json
{
  "prefix": "post",
  "suggestions": [
    {
      "term": "postgresql",
      "ctf": 120,
      "df": 35,
      "score": 28.38
    },
    {
      "term": "postings",
      "ctf": 80,
      "df": 25,
      "score": 21.97
    },
    {
      "term": "positional",
      "ctf": 45,
      "df": 18,
      "score": 16.24
    }
  ],
  "count": 3
}
```

---

## 3. Spelling Correction & Query Suggestions

Detects spelling errors in multi-word queries using the Damerau-Levenshtein distance (insertions, deletions, substitutions, and adjacent transpositions) with length-bucket candidate pruning and Noisy Channel scoring:
$$P(C \mid T) \propto \text{ErrorWeight}(d) \times \left(\ln(1 + \text{ctf}) \times \sqrt{\text{df}} + 1.0\right)$$
Guarantees that words already present in the index vocabulary are strictly preserved.

* **Method:** `GET`
* **Path:** `/api/v1/search/suggest`
* **Query Parameters:**
  * `q` (string, required): Input query containing potential typos (e.g. `postgreql btre`).

### Example Request
```http
GET /api/v1/search/suggest?q=postgreql+btre HTTP/1.1
Host: localhost:8080
```

### Example Response (`200 OK`)
```json
{
  "originalQuery": "postgreql btre",
  "suggestedQuery": "postgresql btree",
  "hasCorrection": true,
  "corrections": [
    {
      "original": "postgreql",
      "suggestion": "postgresql",
      "editDistance": 1
    },
    {
      "original": "btre",
      "suggestion": "btree",
      "editDistance": 1
    }
  ]
}
```

---

## 4. Ranked Boolean Retrieval (Mode C)

Evaluates an AST Boolean expression (`AND`, `OR`, `NOT`, parentheses) and ranks matching candidates with BM25 and exact phrase boosting, while dynamically generating highlighted snippets for matching documents.

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
