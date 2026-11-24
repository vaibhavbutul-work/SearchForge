# SearchForge Information Retrieval (IR) Evaluation Metrics

## 1. Overview and Architecture

In Information Retrieval (IR), measuring search system quality requires quantitative metrics that evaluate how well a ranking algorithm places relevant documents near the top of the search engine results page (SERP). 

A search engine can retrieve thousands of matching documents quickly, but if the most relevant results appear on page 5 or 10, user satisfaction drops to zero.

SearchForge implements standard, industry-grade evaluation metrics via:
- [`RelevanceMetricsCalculator.java`](file:///d:/projects/backend/src/main/java/com/searchforge/ranking/evaluation/RelevanceMetricsCalculator.java)
- [`EvaluationMetricsAggregator.java`](file:///d:/projects/backend/src/main/java/com/searchforge/ranking/evaluation/EvaluationMetricsAggregator.java)
- [`InformationRetrievalMetricsTest.java`](file:///d:/projects/backend/src/test/java/com/searchforge/ranking/evaluation/InformationRetrievalMetricsTest.java)

---

## 2. Core Metrics: Definitions and Mathematical Formulations

### 2.1 Precision@K ($P@K$)

**Definition:** Precision at rank cutoff $K$ measures the fraction of the top-$K$ retrieved unique documents that are relevant.

$$\text{Precision@}K = \frac{|\{d \in R_K : \text{rel}(d) = 1\}|}{K}$$

where $R_K$ is the set of unique documents retrieved at ranks $1, \dots, K$, and $\text{rel}(d) \in \{0, 1\}$ denotes binary relevance.

- **Range:** $[0.0, 1.0]$
- **Focus:** Purity of the top-$K$ results.
- **Limitation:** Does not distinguish whether a relevant document is at rank 1 vs rank $K$.

---

### 2.2 Recall@K ($R@K$)

**Definition:** Recall at rank cutoff $K$ measures the fraction of all known relevant documents that appear within the top-$K$ results.

$$\text{Recall@}K = \frac{|\{d \in R_K : \text{rel}(d) = 1\}|}{|\text{Total Relevant Documents for Query}|}$$

- **Range:** $[0.0, 1.0]$
- **Focus:** Completeness of retrieval within the top-$K$ window.
- **Edge-Case Policy:** If a query has zero relevant documents in ground truth, $R@K = 1.0$ (vacuously satisfied). If retrieved list is empty and relevant documents exist, $R@K = 0.0$.

---

### 2.3 F1@K ($F_1@K$)

**Definition:** The harmonic mean of Precision@K and Recall@K, balancing precision and recall.

$$F_1@K = 2 \cdot \frac{\text{Precision@}K \cdot \text{Recall@}K}{\text{Precision@}K + \text{Recall@}K}$$

- **Range:** $[0.0, 1.0]$
- **Edge-Case Policy:** If $\text{Precision@}K + \text{Recall@}K = 0$, $F_1@K = 0.0$ to prevent division by zero.

---

### 2.4 Reciprocal Rank (RR) and Mean Reciprocal Rank (MRR)

**Definition:** Reciprocal Rank measures the speed with which the search engine produces its *first* relevant document.

$$\text{RR}(q) = \begin{cases} 
\frac{1}{\text{rank}_1}, & \text{if a relevant document is retrieved at rank } \text{rank}_1 \\
0.0, & \text{if no relevant document is retrieved}
\end{cases}$$

Across a test collection of $Q$ queries, the **Mean Reciprocal Rank (MRR)** is the arithmetic mean:

$$\text{MRR} = \frac{1}{|Q|} \sum_{q \in Q} \text{RR}(q)$$

- **Range:** $[0.0, 1.0]$
- **Interpretation:** $\text{MRR} = 1.0$ means every query had a relevant document at rank 1. $\text{MRR} = 0.5$ means on average the first relevant result was at rank 2.
- **Use Case:** Critical for navigational queries, QA systems, and voice assistants.

---

### 2.5 Average Precision (AP) and Mean Average Precision (MAP)

**Definition:** Average Precision rewards systems that place relevant documents high in the ranking while penalizing false positives. It computes the average of Precision@$i$ evaluated at each rank $i$ where a relevant document is found:

$$\text{AP}(q) = \frac{1}{|\text{Total Relevant for } q|} \sum_{i=1}^{N} \Big( \text{Precision@}i \cdot \text{rel}(i) \Big)$$

where $\text{rel}(i) \in \{0, 1\}$ indicates whether the item at rank $i$ is relevant.

Across a query set $Q$, the **Mean Average Precision (MAP)** is:

$$\text{MAP} = \frac{1}{|Q|} \sum_{q \in Q} \text{AP}(q)$$

- **Range:** $[0.0, 1.0]$
- **Property:** Highly sensitive to rank order across the entire candidate list.

---

### 2.6 Graded Relevance: DCG@K, IDCG@K, and NDCG@K

Binary metrics treat all relevant documents equally. In reality, a document can be *Highly Relevant* (Grade 3), *Relevant* (Grade 2), *Slightly Relevant* (Grade 1), or *Irrelevant* (Grade 0).

#### Discounted Cumulative Gain (DCG@K)
Using the standard logarithmic position discount and exponential gain:

$$\text{DCG@}K = \sum_{i=1}^{K} \frac{2^{\text{grade}_i} - 1}{\log_2(i + 1)}$$

- The gain $2^{\text{grade}_i} - 1$ heavily prioritizes Grade 3 ($2^3 - 1 = 7$) over Grade 2 ($2^2 - 1 = 3$) and Grade 1 ($2^1 - 1 = 1$).
- The discount $\log_2(i + 1)$ decreases monotonically as rank $i$ increases:
  - Rank 1: $\log_2(2) = 1.000$ (no discount)
  - Rank 2: $\log_2(3) \approx 1.585$
  - Rank 3: $\log_2(4) = 2.000$
  - Rank 4: $\log_2(5) \approx 2.322$
  - Rank 5: $\log_2(6) \approx 2.585$

#### Ideal Discounted Cumulative Gain (IDCG@K)
IDCG@K is the maximum theoretically achievable DCG@K, calculated by sorting all known relevant grades for the query in descending order:

$$\text{IDCG@}K = \sum_{i=1}^{\min(K, |\text{known}|)} \frac{2^{\text{grade}^*_i} - 1}{\log_2(i + 1)}$$

where $\text{grade}^*$ is the sorted ground-truth grades.

#### Normalized Discounted Cumulative Gain (NDCG@K)
$$\text{NDCG@}K = \frac{\text{DCG@}K}{\text{IDCG@}K}$$

- **Range:** $[0.0, 1.0]$
- **Edge-Case Policy:** If $\text{IDCG@}K = 0$ (no positive grades exist anywhere for this query), $\text{NDCG@}K = 1.0$ (trivially optimal).

---

## 3. Strict SearchForge Policies and Edge Cases

| Condition | Metric Behavior / SearchForge Policy |
|---|---|
| **Binary Threshold** | For binary metrics ($P@K, R@K, F_1@K, AP, RR$), documents with grade $\ge 2$ (Relevant, Highly Relevant) are treated as relevant. Grade 1 (Slightly Relevant) and Grade 0 are non-relevant. |
| **Unjudged Documents** | Any retrieved document not present in ground truth is strictly treated as Grade 0 (Non-relevant). |
| **Duplicate Candidate IDs** | Pre-sanitized preserving rank order: $[101, 101, 102] \to [101, 102]$ before slicing to top-$K$. Prevents search engine bugs or malicious spam from artificially inflating metrics. |
| **Empty Candidate List** | If retrieved list is empty: $P@K=0, R@K=0, F_1@K=0, RR=0, AP=0, \text{NDCG@}K=0$. |
| **Zero Relevant Ground Truth** | If $|\text{Rel}| = 0$: $P@K=0, R@K=1.0, RR=0, AP=0, \text{NDCG@}K=1.0$. |
| **Cutoff Selection** | Standard web search cutoffs: $K=5$ (first visible fold on mobile/desktop) and $K=10$ (standard first page of SERP). |

---

## 4. Step-by-Step Worked Numerical Walkthrough

Consider an evaluation query where ground truth contains:
- Document `101`: Grade 3 (Highly Relevant)
- Document `102`: Grade 2 (Relevant)
- Document `103`: Grade 1 (Slightly Relevant)
- Document `104`: Grade 0 (Irrelevant)
- Document `105`: Grade 0 (Irrelevant)

Total relevant under binary threshold ($\ge 2$): **2 documents** (`101`, `102`).

### Scenario: Suboptimal Retrieved Ranking
Suppose SearchForge retrieves: `[103, 101, 104, 102, 105]`.

#### 1. Precision@K
- **Top 1:** `[103]` $\to$ 0 relevant $\implies P@1 = 0 / 1 = 0.0$
- **Top 2:** `[103, 101]` $\to$ 1 relevant (`101`) $\implies P@2 = 1 / 2 = 0.5$
- **Top 3:** `[103, 101, 104]` $\to$ 1 relevant $\implies P@3 = 1 / 3 \approx 0.3333$
- **Top 5:** `[103, 101, 104, 102, 105]` $\to$ 2 relevant (`101, 102`) $\implies P@5 = 2 / 5 = 0.4000$

#### 2. Recall@K
Total relevant = 2.
- **Recall@1:** $0 / 2 = 0.0$
- **Recall@2:** $1 / 2 = 0.5000$
- **Recall@3:** $1 / 2 = 0.5000$
- **Recall@5:** $2 / 2 = 1.0000$

#### 3. F1@K
- **F1@2:** $2 \cdot (0.5 \cdot 0.5) / (0.5 + 0.5) = 0.5000$
- **F1@5:** $2 \cdot (0.4 \cdot 1.0) / (0.4 + 1.0) = 0.8 / 1.4 \approx 0.5714$

#### 4. Reciprocal Rank (RR)
First relevant document (`101`) appears at Rank 2.
$$\text{RR} = \frac{1}{2} = 0.5000$$

#### 5. Average Precision (AP)
Relevant documents appear at rank 2 (`101`) and rank 4 (`102`).
- Precision at rank 2: $1 / 2 = 0.5000$
- Precision at rank 4: $2 / 4 = 0.5000$
$$\text{AP} = \frac{0.5000 + 0.5000}{2} = 0.5000$$

#### 6. DCG@3, IDCG@3, and NDCG@3
Retrieved grades at top 3:
- Rank 1: Doc `103` (Grade 1) $\implies (2^1 - 1) / \log_2(2) = 1.0000$
- Rank 2: Doc `101` (Grade 3) $\implies (2^3 - 1) / \log_2(3) = 7 / 1.5849625 \approx 4.4165$
- Rank 3: Doc `104` (Grade 0) $\implies (2^0 - 1) / \log_2(4) = 0.0000$
$$\text{DCG@}3 = 1.0000 + 4.4165 + 0 = 5.4165$$

Ideal ordering for top 3: Sorted grades `[3, 2, 1]`.
- Rank 1: Grade 3 $\implies 7 / 1 = 7.0000$
- Rank 2: Grade 2 $\implies (2^2 - 1) / \log_2(3) = 3 / 1.5849625 \approx 1.8928$
- Rank 3: Grade 1 $\implies 1 / \log_2(4) = 1 / 2 = 0.5000$
$$\text{IDCG@}3 = 7.0000 + 1.8928 + 0.5000 = 9.3928$$

$$\text{NDCG@}3 = \frac{5.4165}{9.3928} \approx 0.5767$$

---

## 5. Aggregation Strategy: Macro vs Micro Averaging

SearchForge utilizes **Macro-Averaging** for all benchmark reports.

$$\text{Metric}_{\text{macro}} = \frac{1}{|Q|} \sum_{q \in Q} \text{Metric}(q)$$

- **Why Macro-Averaging?** Every user query is treated as an equal information need. Micro-averaging pools all relevant documents across all queries, which biases aggregate scores heavily toward broad queries with many relevant documents while ignoring rare or specific queries.
- **Slicing Dimensions:**
  1. **Overall:** System-wide macro average across all 48 benchmark queries.
  2. **By Category:** Macro average across each of the 8 query archetypes (`EXACT_KEYWORD`, `BROAD_TOPICAL`, `CONCEPTUAL`, `MULTI_CONCEPT`, `AMBIGUOUS`, `NATURAL_LANGUAGE`, `RARE_TERM`, `SYNONYM`).
  3. **By Split:** Macro average across `DEV` (24 queries), `TUNING` (12 queries), and `EVAL` (12 queries).
