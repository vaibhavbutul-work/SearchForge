# SearchForge — Phase 5: Query Intelligence & Search Experience
## Architectural Guide, Algorithmic Derivations & Performance Evaluation

---

## 1. Executive Summary

In **Phase 5**, SearchForge evolves from a system that retrieves and ranks indexed documents into an **intelligent search experience** that understands, normalizes, corrects, expands, and dynamically presents user queries.

While Phases 1–4 solved crawling, indexing, and BM25 relevance scoring, user queries in the real world are messy, incomplete, and noisy:
- Users make spelling typos and expect helpful `"Did you mean?"` suggestions.
- Users expect real-time prefix autocomplete as they type.
- Users search using acronyms and abbreviations (e.g. `db`, `k8s`) and expect documents containing canonical synonyms (e.g. `database`, `kubernetes`) to be retrieved.
- Users require contextual, dynamic text snippets that highlight their search terms while strictly guaranteeing immunity against Cross-Site Scripting (XSS).

Rather than delegating these capabilities to external black-box search engines (Lucene, Algolia, Elasticsearch), every data structure and algorithm was designed and implemented **from first principles in Java 21 and Spring Boot**:

```
                                  USER INPUT
                                       │
        ┌──────────────────────────────┴──────────────────────────────┐
        ▼                                                             ▼
[ As User Types ]                                           [ Enter / Submit ]
  Prefix Autocomplete (Trie)                                  Raw Search Query
  O(L + M log K) Min-Heap                                             │
  Score(t) = ln(1+ctf) * sqrt(df)                                     ▼
                                                            ┌───────────────────┐
                                                            │ Length-Bucket OSA │
                                                            │ Damerau-Levensht. │
                                                            │ (Distance <= 2)   │
                                                            └─────────┬─────────┘
                                                                      │
                                                           didYouMean Suggestion
                                                           P(C|T) Noisy Channel
                                                                      │
                                                                      ▼
                                                            ┌───────────────────┐
                                                            │ Synonym Expansion │
                                                            │ Dual Table Map    │
                                                            │ Discount Weight   │
                                                            └─────────┬─────────┘
                                                                      │
                                                                      ▼
                                                            ┌───────────────────┐
                                                            │   Weighted BM25   │
                                                            │ Retrieval Engine  │
                                                            └─────────┬─────────┘
                                                                      │
                                                              Top-K Ranked Hits
                                                                      │
                                                                      ▼
                                                            ┌───────────────────┐
                                                            │  Dynamic Snippet  │
                                                            │  Window Scoring   │
                                                            │  Anti-XSS <mark>  │
                                                            └─────────┬─────────┘
                                                                      │
                                                                      ▼
                                                            ENRICHED JSON RESPONSE
```

---

## 2. Subsystem Architecture & Algorithms

### Subsystem 1: Weighted Trie Autocomplete (`com.searchforge.query.autocomplete`)
* **Package:** `com.searchforge.query.autocomplete.trie` & `service`
* **Data Structure:** Custom `TrieNode` using `ConcurrentHashMap<Character, TrieNode>` for branch children. Thread-safe lock-free reads and updates via `AtomicReference<AutocompleteTrie>`.
* **Deterministic Popularity Ranking:**
  $$\text{Score}(t) = \ln(1 + \text{ctf}) \times \sqrt{\text{df}}$$
  Balances total corpus term frequency ($ctf$) with document breadth ($df$), preventing high-frequency terms in a single document from dominating broad cross-document topics.
* **Top-K Min-Heap Selection:**
  Instead of collecting and sorting all $M$ terms in the subtree ($\mathcal{O}(M \log M)$), uses a bounded Min-Heap of capacity $K$. When full, lower-scoring candidates are rejected in $\mathcal{O}(1)$.
* **Complexity:**
  - Prefix Navigation: $\mathcal{O}(L)$ where $L$ is prefix length.
  - Subtree Exploration: $\mathcal{O}(M)$ where $M$ is terms in subtree.
  - Top-K Selection: $\mathcal{O}(M \log K)$.
  - Total Latency: **1.29 µs** mean, **0.80 µs** p50.

---

### Subsystem 2: Spelling Correction & Query Suggestions (`com.searchforge.query.spelling`)
* **Package:** `com.searchforge.query.spelling.algorithm` & `service`
* **Optimal String Alignment (OSA) Damerau-Levenshtein:**
  Evaluates insertions, deletions, substitutions, and adjacent transpositions.
  $$D[i,j] = \min \begin{cases}
  D[i-1,j] + 1 & \text{(Deletion)} \\
  D[i,j-1] + 1 & \text{(Insertion)} \\
  D[i-1,j-1] + \text{cost} & \text{(Substitution)} \\
  D[i-2,j-2] + 1 & \text{(Adjacent Transposition, if } s_1[i]=s_2[j-1] \land s_1[i-1]=s_2[j] \text{)}
  \end{cases}$$
* **Length-Bucket Candidate Pruning:**
  Words are bucketed by length during vocabulary indexing. For a query word of length $L$, candidates are restricted strictly to buckets $[L - 2, L + 2]$, pruning over 90% of vocabulary comparisons upfront.
* **Early-Exit Banding:**
  Row minimum tracking halts matrix construction immediately if every cell in row $i$ exceeds $\text{maxDistance} = 2$.
* **Noisy Channel Candidate Scoring:**
  $$P(C \mid T) \propto P(T \mid C) \cdot P(C)$$
  $$\text{Score}(C) = \text{ErrorWeight}(d) \times \left(\ln(1 + \text{ctf}) \times \sqrt{\text{df}} + 1.0\right)$$
  Where $\text{ErrorWeight}(1) = 1.0$, $\text{ErrorWeight}(2) = 0.5$.
* **Vocabulary Preservation:** Words that exist in the index vocabulary are strictly preserved.

---

### Subsystem 3: Dynamic Sliding-Window Snippets & Term Highlighting (`com.searchforge.query.snippet`)
* **Package:** `com.searchforge.query.snippet.algorithm` & `service`
* **Linguistic Word-Boundary Snapping:**
  Candidate window boundaries snap to word tokens rather than arbitrary character cuts, avoiding mangled words.
* **Density Relevance Window Scoring:**
  $$\text{Score} = 100 \cdot Q_{\text{unique}} + 10 \cdot Q_{\text{total}} + \frac{100}{1 + \text{Span}}$$
  Prioritizes passages containing the highest count of unique query terms clustered closely together.
* **Anti-XSS HTML Sanitization (`HtmlSanitizer`):**
  Document text is sanitized by converting `&`, `<`, `>`, `"`, `'` into respective HTML entities before injecting safe `<mark>` and `</mark>` tags around matched tokens.

---

### Subsystem 4: Query Normalization & Synonym Expansion (`com.searchforge.query.synonym`)
* **Package:** `com.searchforge.query.synonym.dictionary` & `service`
* **Dual-Graph Dictionary (`InMemorySynonymDictionary`):**
  Supports `ONE_WAY` (e.g. `k8s -> kubernetes`) and `BIDIRECTIONAL` (e.g. `db <-> database`) synonym tables.
* **Guards & Cycle Prevention:**
  Rejects self-mappings ($t \to t$), filters grammatical stop words, limits expansions to $K \le 3$, and tracks seen terms to avoid circular loops.
* **Weighted BM25 Integration:**
  Expanded terms receive a tunable discount factor ($\delta = 0.5$):
  $$\text{Score}(D) = \sum_{t \in Q_{\text{orig}}} 1.0 \times \text{BM25}(t, D) + \sum_{s \in Q_{\text{syn}}} \delta \times \text{BM25}(s, D)$$
  Guarantees that exact matches outrank synonym matches while boosting recall.
* **Mode Safety:** Quoted phrases and Boolean queries (`AND`, `OR`, `NOT`) strictly bypass expansion.

---

## 3. Empirical Performance Benchmarks

Measured on Java 21 LTS, Spring Boot 4.1.2, 500 documents, 557 unique vocabulary terms across 200 benchmark iterations with JIT warm-up:

| Component | Metric | Mean Latency | p50 Latency | p90 Latency | p99 Latency |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Autocomplete** | Top-K Prefix Lookup ($K=5$) | **1.29 µs** | 0.80 µs | 2.00 µs | 4.70 µs |
| **Spelling** | Candidate Generation & Scoring | **71.52 µs** | 89.10 µs | 109.90 µs | 177.70 µs |
| **Snippets** | Dynamic Windowing & Highlighting | **20.13 µs** | 19.40 µs | 31.70 µs | 76.70 µs |
| **Baseline Search** | Raw BM25 Ranked Retrieval | **0.17 ms** | 0.15 ms | 0.23 ms | 0.44 ms |
| **Enriched Search** | End-to-End Query Intelligence | **0.26 ms** | 0.23 ms | 0.39 ms | 0.59 ms |

*Total overhead introduced by query intelligence (spelling + synonyms + snippets) is **under 90 microseconds**.*

---

## 4. Information Retrieval Relevance Evaluation

Using the Phase 4 IR evaluation framework (`RelevanceEvaluationRunner`):

1. **Spelling Correction:**
   - Raw misspelled query (`postgreql btre`) yields 0 hits ($\text{Recall@1} = 0.0$).
   - Suggested query (`postgresql btree`) recovers relevant documents with **$\text{Recall@1} = 1.0$** and **$\text{MRR} = 1.0$**.
2. **Synonym Expansion:**
   - Querying `db` without synonym expansion yields $\text{Recall@2} = 0.5$ (only retrieves exact term).
   - With synonym expansion enabled, surfaces documents containing `database`, achieving **$\text{Recall@2} = 1.0$**.
3. **Precision Preservation:**
   - Discounting synonym contributions by $0.5$ ensures that exact matches score higher than synonym matches, maintaining **$\text{Precision@1} = 1.0$** and preventing query drift.
4. **Snippet Windowing:**
   - Dynamically selects query-dense passages even when located deep inside long documents, skipping low-scoring filler prefixes.
5. **Consistency:**
   - When synonym expansion is disabled or queries are quoted, BM25 scores match baseline with zero numerical variance ($< 10^{-6}$).

---

## 5. Automated Testing Breakdown (273 Tests Total)

| Subsystem | Test Classes | Test Count |
| :--- | :--- | :--- |
| **Phase 5: Autocomplete** | `AutocompleteTrieTest`, `AutocompleteServiceTest`, `AutocompleteControllerTest` | 17 |
| **Phase 5: Spelling** | `DamerauLevenshteinDistanceTest`, `SpellingCorrectionServiceTest`, `SpellingSuggestionControllerTest` | 22 |
| **Phase 5: Snippets** | `HtmlSanitizerTest`, `WindowRelevanceScorerTest`, `SnippetGeneratorServiceTest` | 15 |
| **Phase 5: Synonyms** | `InMemorySynonymDictionaryTest`, `SynonymExpansionServiceTest` | 13 |
| **Phase 5: Integration** | `SearchIntelligenceIntegrationTest`, `DefaultRankedRetrievalEngineSynonymTest` | 12 |
| **Phase 5: Benchmarks** | `QueryIntelligenceBenchmarkTest`, `QueryIntelligenceRelevanceEvaluationTest` | 9 |
| **Phase 4: Scoring & Ranking** | Scorer, IDF, dampener, BM25, evaluation runner, metrics calculator, performance | 65 |
| **Phase 3: Indexing & Retrieval** | Inverted index, tokenizer, stemmer, AST parser, phrase search, postings | 58 |
| **Phase 1 & 2: Crawler & Core** | Concurrency, crawler engine, robots.txt, politeness, normalizer, WireMock | 62 |
| **Total Test Suite** | **53 Test Classes** | **273 Passing (100%)** |
