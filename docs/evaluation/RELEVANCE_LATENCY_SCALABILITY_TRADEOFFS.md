# SearchForge Relevance, Latency & Scalability Trade-offs Report

## 1. Executive Summary

A search engine is not successful merely because it retrieves documents quickly. It must retrieve the right documents near the top of the search results page (SERP). Conversely, an algorithm with high relevance that takes seconds to respond will fail user expectations and breach production SLAs.

This report evaluates the **empirical trade-offs between Relevance Quality (NDCG, MAP, MRR), Latency Percentiles (P50, P90, P95, P99), Sub-Component Overhead, and System Scalability** across SearchForge's three search paradigms:
1. **Lexical BM25:** Inverted Index with positional postings and Robertson–Spärck Jones scoring
2. **Dense Semantic Retrieval:** ONNX Runtime local transformer (`all-MiniLM-L6-v2`, 384-D) with exact dot-product vector search
3. **Multi-Modal Hybrid Search:** Concurrent Lexical + Semantic retrieval with Reciprocal Rank Fusion ($k=60$)

The benchmark was executed using the standardized 120-document corpus and 48-query dataset over multiple warm repetitions ($N = 144$ profiling requests per mode) on a modern multi-core workstation.

---

## 2. Relevance vs. Latency Summary (Pareto Analysis)

| Retrieval Paradigm | NDCG@5 | NDCG@10 | MAP | MRR | Mean Latency | P50 (Median) | P90 | P95 | P99 (Tail) | Single-Thread QPS |
|---|---|---|---|---|---|---|---|---|---|---|
| **Lexical BM25** | 0.8992 | 0.9094 | 0.8854 | 0.9688 | **0.0416 ms** | **0.0381 ms** | **0.0624 ms** | **0.0856 ms** | **0.2328 ms** | **24,041.7 QPS** |
| **Dense Semantic** | 0.9272 | **0.9468** | **0.9230** | **0.9844** | 2.1304 ms | 2.1021 ms | 2.5112 ms | 2.7025 ms | 3.1220 ms | 469.4 QPS |
| **Hybrid (RRF)** | **0.9303** | 0.9448 | 0.9210 | 0.9792 | 2.2682 ms | 2.2140 ms | 2.5980 ms | 2.7748 ms | 3.5002 ms | 440.9 QPS |

### Key Takeaways
1. **The Production Latency Budget is Safely Respected:**
   - Standard web search SLAs allocate 50ms–100ms for total backend query processing.
   - SearchForge's Hybrid Search finishes in **2.27 ms on average** and **2.77 ms at P95**, consuming less than **5% of typical SLA budgets**.
2. **The Pareto Frontier:**
   - **BM25** defines the ultra-low-latency boundary: **$0.04\text{ ms}$ (41 microseconds)** at 24,000 QPS.
   - **Hybrid RRF** defines the optimal relevance boundary: **$\text{NDCG}@5 = 0.9303$** at $2.27\text{ ms}$.
   - Spending $2.23\text{ ms}$ of computation to jump from $\text{NDCG}@5 = 0.8992 \to 0.9303$ (eliminating the catastrophic $0.7315$ collapse on ambiguous queries) is an exceptional, production-worthy trade-off.

---

## 3. Sub-Component Timing Breakdown (Hybrid Search)

Where is time spent during an end-to-end hybrid query?

```
+-----------------------------------------------------------------------------------------+
| HYBRID SEARCH SUB-COMPONENT BREAKDOWN (Mean: 2.2682 ms)                                 |
+---------------------------------------------+--------------+----------------------------+
| Sub-Component Subsystem                     | Time (ms)    | % of Total Execution Time  |
+---------------------------------------------+--------------+----------------------------+
| 1. Neural Inference (WordPiece + ONNX)      | 2.0071 ms    | 88.49%                     |
| 2. Vector Similarity Search (Dot Product)   | 0.1219 ms    |  5.37%                     |
| 3. Reciprocal Rank Fusion (Aggregation/Sort)| 0.0882 ms    |  3.89%                     |
| 4. Lexical BM25 (Inverted Index Postings)   | 0.0416 ms    |  1.83%                     |
| 5. Candidate Provenance Mapping & Return    | 0.0094 ms    |  0.42%                     |
+---------------------------------------------+--------------+----------------------------+
| Total End-to-End Hybrid Search Latency      | 2.2682 ms    | 100.00%                    |
+---------------------------------------------+--------------+----------------------------+
```

### Critical Architectural Observations
1. **Transformer Inference is the 88.5% Dominant Bottleneck:**
   - Executing the 6-layer transformer forward pass (`all-MiniLM-L6-v2`) accounts for **$2.01\text{ ms}$** of the $2.27\text{ ms}$ total query budget.
   - WordPiece tokenization itself requires only $\approx 0.012\text{ ms}$; the remaining time is tensor matrix multiplications and attention operations inside ONNX Runtime.
2. **Lexical BM25 is Essentially Free:**
   - Traversal of posting lists and Robertson–Spärck Jones scoring completes in **41.6 microseconds** ($1.8\%$ of total time). Running BM25 alongside semantic search introduces virtually zero overhead.
3. **Reciprocal Rank Fusion is Extremely Lightweight:**
   - Merging two candidate lists of size 50, computing reciprocal sums, and sorting takes only **88 microseconds** ($3.9\%$). RRF requires zero expensive matrix algebra or vector projections.

---

## 4. Cold-Start vs. Steady-State Warm Execution

| Lifecycle Stage | Operation | Latency | Architectural Cause |
|---|---|---|---|
| **Cold Startup** | ONNX Runtime Session Init | $\approx 970\text{ ms}$ | Loading weights from disk, building execution graph, initializing CPU thread pools. |
| **First Query** | Cold-Start Query Inference | $\approx 2.5\text{ ms}$ | Populating CPU instruction cache, tensor buffer allocation. |
| **Steady State** | Warm Query Inference | $\approx 2.0\text{ ms}$ | Resident L1/L2/L3 caches, HotSpot JIT C2 compiled loops. |
| **LRU Cache Hit** | Cached Query Vector | $< 0.001\text{ ms}$ | Instant hashmap lookup via `QueryEmbeddingCache` (skipping ONNX forward pass entirely!). |

---

## 5. Candidate Depth & Memory Scaling Analysis

### 5.1 Candidate Pool Size vs. Latency and Relevance

SearchForge retrieves a candidate pool of size $P = \max(\text{limit} \times 2, 50)$ per engine before fusion:

| Candidate Pool Size $P$ | Hybrid NDCG@5 | End-to-End Latency | Fusion Overhead | Scalability Assessment |
|---|---|---|---|---|
| **$P = 25$** | 0.9078 | 2.18 ms | 0.045 ms | Slightly faster, but risks dropping deep relevant candidates. |
| **$P = 50$ (Default)** | **0.9303** | **2.27 ms** | **0.088 ms** | **Optimal sweet spot:** Maximizes consensus without noise. |
| **$P = 100$** | 0.9303 | 2.45 ms | 0.175 ms | Identical relevance, but doubles RRF accumulator allocation. |

### 5.2 In-Memory Footprint Analysis

For the 120-document corpus:
- **Inverted Index Footprint:** $\approx 126\text{ KB}$ (sparse postings, term dictionary, document length maps).
- **Dense Vector Storage Footprint:** $\approx 187\text{ KB}$ ($120 \times 384 \times 4\text{ bytes} + \text{object overhead}$).
- **Projection to 1 Million Documents:**
  - Inverted Index: $\approx 1.2\text{ GB}$ (highly compressable using Varint/SIMD-PFor).
  - Dense Vector Storage (384-D): $\approx 1.54\text{ GB}$ (raw float32).

---

## 6. Final Production Architecture Recommendation

```
+-----------------------------------------------------------------------------------------+
| PARETO-OPTIMAL PRODUCTION STANDARD: HYBRID SEARCH (RRF k=60)                            |
+-----------------------------------------------------------------------------------------+
| Recommendation: Keep Hybrid Search as the default production engine.                    |
|                                                                                         |
| Quality:   NDCG@5 = 0.9303 (highest of all paradigms)                                   |
| Latency:   Mean = 2.27 ms, P95 = 2.77 ms, P99 = 3.50 ms                                 |
| Budget:    Consumes < 5% of standard 100ms web SLA                                      |
| Fallback:  If ONNX fails, degrades in < 0.1ms to pure BM25 with zero user downtime.     |
+-----------------------------------------------------------------------------------------+
```
