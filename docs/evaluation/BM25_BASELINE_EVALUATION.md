# SearchForge BM25 Baseline Relevance Evaluation Report

## 1. Executive Summary

This report establishes the empirical **baseline relevance and ranking quality** of SearchForge's pure lexical **BM25** retrieval engine. 

Before introducing neural semantic embeddings (Task 12.4), hybrid reciprocal rank fusion (Task 12.5), or parameter optimization (Task 12.6), we must establish a rigorous, reproducible, and mathematically verified reference benchmark.

### Experimental Configuration
- **Retrieval Engine:** `DefaultRankedRetrievalEngine` (Pure BM25, no synonym expansion)
- **Scoring Parameters:** $k_1 = 1.20$ (Term frequency saturation), $b = 0.75$ (Document length normalization)
- **IDF Formula:** Strictly non-negative Robertson–Spärck Jones variant:
  $$\text{IDF}(t) = \ln\left(1 + \frac{N - \text{df}(t) + 0.5}{\text{df}(t) + 0.5}\right)$$
- **Test Corpus:** [`SearchForgeEvaluationCorpus.java`](file:///d:/projects/backend/src/main/java/com/searchforge/ranking/evaluation/SearchForgeEvaluationCorpus.java) (120 contiguous documents, IDs `101L`–`220L` across 10 technical and biological domains + 35 background distractors)
- **Evaluation Dataset:** [`SearchForgeEvaluationDataset.java`](file:///d:/projects/backend/src/main/java/com/searchforge/ranking/evaluation/SearchForgeEvaluationDataset.java) (48 queries across 8 archetypes, evaluated at cutoffs $K=5$ and $K=10$ with 4-level graded relevance $[0, 3]$)
- **Aggregation Strategy:** Unweighted Macro-Averaging across all queries

---

## 2. Overall Baseline Metrics

| Metric | Depth $K=5$ | Depth $K=10$ | Description / Interpretation |
|---|---|---|---|
| **Precision@K** | **0.2917** | **0.1521** | Fraction of retrieved top-$K$ results that are relevant ($\text{grade} \ge 2$) |
| **Recall@K** | **0.8889** | **0.9167** | Fraction of all known relevant documents successfully retrieved within top-$K$ |
| **F1@K** | **0.4228** | **0.2544** | Harmonic balance between Precision and Recall |
| **MRR** | **0.9688** | — | Mean Reciprocal Rank (position of the *first* relevant document) |
| **MAP** | **0.8854** | — | Mean Average Precision across all retrieved ranks |
| **NDCG@K** | **0.8992** | **0.9094** | Normalized Discounted Cumulative Gain against ideal graded ranking |

### Key Overall Insights
1. **Exceptional First-Hit Accuracy ($\text{MRR} = 0.9688$):** For almost all queries ($>96\%$), pure BM25 successfully surfaces a highly relevant or relevant document at Rank 1.
2. **High Top-5 Recall ($R@5 = 0.8889$):** On average, BM25 captures nearly $89\%$ of all relevant documents within the first 5 results.
3. **Precision Profile ($P@5 = 0.2917$):** Because our ground truth has between 1 and 3 relevant documents per query ($|\text{Rel}|_{\text{avg}} \approx 1.5$), retrieving 5 documents yields a theoretical maximum precision of $\approx 0.30$. A $P@5$ of $0.2917$ indicates that non-relevant distractors rarely push relevant results out of the top 3.

---

## 3. Query Category Breakdown

The 48 queries span 8 distinct information retrieval archetypes (6 queries per category):

| Query Category Archetype | $N$ | $P@5$ | $R@5$ | MRR | MAP | NDCG@5 | NDCG@10 | Performance Tier |
|---|---|---|---|---|---|---|---|---|
| **RARE_TERM** | 6 | 0.2000 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **Tier 1 (Flawless)** |
| **MULTI_CONCEPT** | 6 | 0.4333 | **1.0000** | **1.0000** | **1.0000** | **0.9720** | **0.9720** | **Tier 1 (Exceptional)** |
| **SYNONYM** | 6 | 0.2333 | **1.0000** | **1.0000** | **1.0000** | **0.9587** | **0.9652** | **Tier 1 (High)** |
| **EXACT_KEYWORD** | 6 | 0.2333 | **1.0000** | **1.0000** | **1.0000** | **0.9454** | **0.9513** | **Tier 1 (High)** |
| **BROAD_TOPICAL** | 6 | 0.4667 | 0.8611 | **1.0000** | 0.8611 | 0.8905 | 0.9141 | **Tier 2 (Good)** |
| **NATURAL_LANGUAGE** | 6 | 0.2333 | 0.9167 | 0.9167 | 0.8333 | 0.8736 | 0.8808 | **Tier 2 (Good)** |
| **CONCEPTUAL** | 6 | 0.2333 | **0.6667** | **1.0000** | 0.7222 | **0.8222** | **0.8601** | **Tier 3 (Degraded)** |
| **AMBIGUOUS** | 6 | 0.3000 | **0.6667** | 0.8333 | **0.6667** | **0.7315** | **0.7315** | **Tier 3 (Severely Degraded)** |

---

## 4. Breakdown by Dataset Split

To prevent experimental data leakage, SearchForge splits queries across three isolated splits:

| Split | Description | Query Count | $P@5$ | $R@5$ | MRR | MAP | NDCG@5 |
|---|---|---|---|---|---|---|---|
| **DEV** | Exploratory development and validation | 24 | 0.3083 | 0.9236 | 0.9583 | 0.9375 | **0.9116** |
| **TUNING** | Parameter grid search ($k_1, b$, fusion weights) | 12 | 0.2667 | 0.8750 | 1.0000 | 0.8681 | **0.9269** |
| **EVAL** | Blind holdout for final unbiased evaluation | 12 | 0.2833 | 0.8333 | 0.9583 | 0.7986 | **0.8467** |

> [!NOTE]
> The holdout `EVAL` set exhibits a lower baseline ($\text{NDCG}@5 = 0.8467$) compared to `DEV` ($0.9116$) and `TUNING` ($0.9269$). This confirms that the evaluation split presents genuinely challenging queries and will serve as an unpolluted benchmark for final testing.

---

## 5. Detailed Investigation of BM25 Strengths and Weaknesses

### 5.1 Where BM25 Dominates

#### 1. Rare-Term Queries ($\text{NDCG}@5 = 1.0000, \text{MAP} = 1.0000$)
- **Example Queries:** `"mitochondria cristae ATP synthase"`, `"sched_ext BPF extensible CPU scheduler"`, `"Gutenberg moveable metal type"`.
- **Reason:** Inverted index postings for rare specialized terminology (low document frequency $\text{df}(t)$) produce massive IDF multipliers. The Robertson–Spärck Jones formula assigns heavy weights to unique terms, completely isolating target documents from background distractors.

#### 2. Multi-Concept Queries ($\text{NDCG}@5 = 0.9720, \text{MAP} = 1.0000$)
- **Example Queries:** `"Linux Completely Fair Scheduler CPU virtual runtime"`, `"PostgreSQL ACID MVCC write ahead logging WAL"`.
- **Reason:** Documents that contain all 3 or 4 distinct query terms accumulate multiple additive BM25 term scores, creating a large score separation over documents matching only 1 or 2 terms.

---

### 5.2 Where BM25 Fails (Identified Weaknesses)

#### 1. Polysemy and Ambiguity ($\text{NDCG}@5 = 0.7315, \text{MAP} = 0.6667, R@5 = 0.6667$)
- **Example Queries:** `"Python"`, `"Bikes"`, `"Postgres"`, `"Acoustics"`.
- **Root Cause:** A short ambiguous query matches multiple documents across different domains or diverse aspects of a topic. BM25 has no latent contextual awareness to understand which document represents the primary canonical definition versus an incidental secondary reference. Shorter distractor documents matching the token receive less length penalty and can displace more authoritative, comprehensive documents.

#### 2. Conceptual Paraphrasing & Vocabulary Mismatch ($\text{NDCG}@5 = 0.8222, R@5 = 0.6667$)
- **Example Queries:** 
  - Query: `"how do computer systems schedule multiple programs simultaneously"` $\implies$ Targets document on *Linux Completely Fair Scheduler* and *Process Management*.
  - Query: `"how do plants convert sunlight into biochemical energy"` $\implies$ Targets document on *Chloroplast and Thylakoid Photosynthesis*.
- **Root Cause:** The user expresses an information need conceptually without using the exact technical jargon found in the corpus. BM25 relies strictly on exact lexical match (and morphological stems via Porter Stemmer). When the words "schedule", "multiple", and "simultaneously" appear sparsely in an article that primarily uses words like "CFS", "virtual runtime", "red-black tree", and "CPU cores", BM25's Recall@5 drops to **66.67%**.

#### 3. Conversational / Natural-Language Noise ($\text{NDCG}@5 = 0.8736, \text{MAP} = 0.8333$)
- **Example Queries:** `"Explain the fundamental mechanism by which vaccines trigger adaptive immune memory"`.
- **Root Cause:** Conversational filler tokens ("explain", "fundamental", "mechanism", "trigger") dilute the query. While stop words are removed, remaining non-discriminative nouns and verbs still contribute to posting list union, resulting in sub-optimal candidate scoring.

---

## 6. Baseline Reference Table for Subsequent Tasks

This table serves as the authoritative baseline against which subsequent phases will be compared:

```
=========================================================================================
SEARCHFORGE BM25 BASELINE BENCHMARK (k1=1.20, b=0.75, N=48)
=========================================================================================
Metric             Baseline Score       Target in Phase 12 (Hybrid / Tuned)
-----------------------------------------------------------------------------------------
Precision@5        0.2917               >= 0.3100
Precision@10       0.1521               >= 0.1600
Recall@5           0.8889               >= 0.9400
Recall@10          0.9167               >= 0.9600
F1@5               0.4228               >= 0.4500
MRR                0.9688               >= 0.9800
MAP                0.8854               >= 0.9200
NDCG@5             0.8992               >= 0.9300
NDCG@10            0.9094               >= 0.9400
-----------------------------------------------------------------------------------------
Weakest Category:  AMBIGUOUS (NDCG@5 = 0.7315) -> Target: Improve via Semantic Prior
2nd Weak Category: CONCEPTUAL (NDCG@5 = 0.8222) -> Target: Improve via Dense Embeddings
=========================================================================================
```
