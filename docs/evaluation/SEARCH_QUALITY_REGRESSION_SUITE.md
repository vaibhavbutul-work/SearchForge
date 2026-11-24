# SEARCHFORGE — SEARCH QUALITY REGRESSION SUITE

## 1. Executive Summary

In traditional software systems, automated tests ensure functional correctness: an endpoint responds with HTTP 200, handles null pointers gracefully, and parses JSON input accurately. In an Information Retrieval (IR) system, however, a search engine can pass 100% of functional tests and return results in under 5 milliseconds while suffering from **catastrophic relevance failure** (e.g. ranking completely irrelevant documents above primary results due to tokenization or scoring regressions).

The **SearchForge Search Quality Regression Suite** introduces an automated relevance verification gate designed to halt builds or CI/CD pipelines when ranking metrics degrade below established engineering floors.

---

## 2. Architectural Design & Quality Gates

The regression harness evaluates SearchForge against the standardized 48-query benchmark across 8 query archetypes and 120 corpus documents, enforcing a **5-Gate Quality Guardrail System**:

```
+---------------------------------------------------------------------------------------------------------+
|                                    SEARCH QUALITY REGRESSION PIPELINE                                   |
+---------------------------------------------------------------------------------------------------------+
                                                     |
                                                     v
   +---------------------------------------------------------------------------------------------------+
   | Ingestion & Setup: Index 120 Documents (Inverted Index + 384-D Vector Storage via ONNX Runtime)  |
   +---------------------------------------------------------------------------------------------------+
                                                     |
                                                     v
   +---------------------------------------------------------------------------------------------------+
   | 48 Benchmark Queries Executed Across 3 Modes: BM25 Lexical, Dense Semantic, Multi-Modal Hybrid   |
   +---------------------------------------------------------------------------------------------------+
                                                     |
                         +---------------------------+---------------------------+
                         |                           |                           |
                         v                           v                           v
              +--------------------+      +--------------------+      +--------------------+
              |   GATE 1: HYBRID   |      |   GATE 2: BM25     |      |  GATE 3: SEMANTIC  |
              |   QUALITY FLOORS   |      |   QUALITY FLOORS   |      |   QUALITY FLOORS   |
              +--------------------+      +--------------------+      +--------------------+
              | NDCG@10 >= 0.9300  |      | NDCG@10 >= 0.8900  |      | NDCG@10 >= 0.9200  |
              | NDCG@5  >= 0.9100  |      | MAP     >= 0.8600  |      | MRR     >= 0.9500  |
              | MAP     >= 0.9000  |      | MRR     >= 0.9400  |      +--------------------+
              | MRR     >= 0.9500  |      +--------------------+
              | P@5     >= 0.2800  |
              +--------------------+
                         |                           |                           |
                         +---------------------------+---------------------------+
                                                     |
                                                     v
   +---------------------------------------------------------------------------------------------------+
   | GATE 4: CATEGORY INVARIANTS                                                                       |
   | - Exact Keyword NDCG@5 >= 0.9500 (Preserves literal keyword precision)                            |
   | - Ambiguous NDCG@5     >= 0.8800 (Ensures semantic fusion prevents polysemy failure)              |
   +---------------------------------------------------------------------------------------------------+
                                                     |
                                                     v
   +---------------------------------------------------------------------------------------------------+
   | GATE 5: MUTATION TESTING (INTENTIONAL CORRUPTION VALIDATION)                                      |
   | - Inverts result rankings -> Asserts test FAILS                                                   |
   | - Shuffles result rankings -> Asserts test FAILS                                                  |
   | - Injects irrelevant docs  -> Asserts test FAILS                                                   |
   +---------------------------------------------------------------------------------------------------+
                                                     |
                                                     v
                                     [ BUILD PASS / FAIL DECISION ]
```

---

## 3. Metric Thresholds & Empirical Grounding

The regression floor thresholds were derived from empirical evaluations conducted across Tasks 12.3 through 12.7:

| Gate | Target Modality | IR Metric | Observed Baseline | Regression Floor | Safety Margin ($\epsilon$) | Rationale |
|---|---|---|---|---|---|---|
| **1** | Hybrid (Production) | $\text{NDCG}@10$ | **0.9448** | $\ge 0.9300$ | $-0.0148$ | Top-10 ranking fidelity must stay within $1.5\%$ of baseline |
| **1** | Hybrid (Production) | $\text{NDCG}@5$ | **0.9303** | $\ge 0.9100$ | $-0.0203$ | Immediate first-page user visibility floor |
| **1** | Hybrid (Production) | $\text{MAP}$ | **0.9210** | $\ge 0.9000$ | $-0.0210$ | Global recall-precision area floor |
| **1** | Hybrid (Production) | $\text{MRR}$ | **0.9792** | $\ge 0.9500$ | $-0.0292$ | First relevant result must appear at rank 1 or 2 |
| **1** | Hybrid (Production) | $P@5$ | **0.3125** | $\ge 0.2800$ | $-0.0325$ | Precision floor at top-5 results |
| **2** | BM25 Lexical | $\text{NDCG}@10$ | **0.9094** | $\ge 0.8900$ | $-0.0194$ | Protects inverted index and term weighting pipeline |
| **2** | BM25 Lexical | $\text{MAP}$ | **0.8854** | $\ge 0.8600$ | $-0.0254$ | Guarantees BM25 baseline retrieval competence |
| **2** | BM25 Lexical | $\text{MRR}$ | **0.9688** | $\ge 0.9400$ | $-0.0288$ | Guards against term saturation and IDF degradation |
| **3** | Dense Semantic | $\text{NDCG}@10$ | **0.9468** | $\ge 0.9200$ | $-0.0268$ | Ensures ONNX transformer embedding quality |
| **3** | Dense Semantic | $\text{MRR}$ | **0.9844** | $\ge 0.9500$ | $-0.0344$ | Ensures vector dot product preserves rank 1 retrieval |
| **4** | Hybrid Category | `EXACT_KEYWORD` $\text{NDCG}@5$ | **0.9833** | $\ge 0.9500$ | $-0.0333$ | Prevents semantic drift on exact queries |
| **4** | Hybrid Category | `AMBIGUOUS` $\text{NDCG}@5$ | **0.9160** | $\ge 0.8800$ | $-0.0360$ | Ensures dense embedding disambiguates polysemous terms |

---

## 4. Intentional Fault Injection (Mutation Testing)

A regression test that never fails provides a false sense of security. Gate 5 implements **intentional corruption testing** by injecting three distinct failure patterns into the ranking results:

1. **Reversed Rankings:** Inverts the result list order ($R_1 \to R_{10}, R_2 \to R_9$).
   - Expected Result: Severe collapse in $\text{MRR}$ ($< 0.30$) and $\text{NDCG}@5$ ($< 0.50$).
   - Assertion: Regression gate fails immediately.
2. **Shuffled Rankings:** Randomly permutes candidate documents.
   - Expected Result: Inconsistent, degraded NDCG and MRR.
   - Assertion: Regression gate fails immediately.
3. **Arbitrary Irrelevant Documents:** Injects synthetic document IDs outside the corpus range (`99901L`–`99905L`).
   - Expected Result: Complete zeroing of relevant hits ($\text{NDCG}=0, \text{MAP}=0$).
   - Assertion: Regression gate fails immediately.

---

## 5. CI/CD Integration & Maven Command

To execute the search quality regression suite independently or as part of continuous integration:

```bash
# Run only the Search Quality Regression Suite
mvn test -Dtest=SearchQualityRegressionSuiteTest

# Run all regression tests across performance, security, and search quality
mvn test -Dtest=*Regression*
```

---

## 6. Regression Failure Triage Workflow

When a build fails on the Search Quality Regression Gate, follow this diagnosis protocol:

1. **Check which gate failed:**
   - **Gate 1 (Hybrid) failed, but Gates 2 & 3 passed:** Indicates an issue in Reciprocal Rank Fusion (RRF constant $k$, weight balance, or candidate deduplication).
   - **Gate 2 (BM25) failed, but Gate 3 passed:** Indicates an issue in text processing (Tokenizer, PorterStemmer, StopWordFilter) or BM25 parameter alterations ($k_1, b$).
   - **Gate 3 (Semantic) failed, but Gate 2 passed:** Indicates an issue in the ONNX embedding pipeline (tokenizer vocab change, missing normalization, or vector store indexing error).
   - **Gate 4 (`EXACT_KEYWORD`) failed:** Semantic retrieval is drowning out exact lexical matches; check BM25 fusion weighting.
   - **Gate 4 (`AMBIGUOUS`) failed:** Semantic embedding is not properly disambiguating context; inspect vector similarity and embeddings.
