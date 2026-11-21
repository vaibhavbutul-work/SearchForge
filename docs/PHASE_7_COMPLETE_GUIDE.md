# SearchForge: Phase 7 Masterclass Guide
## Hybrid Search & Dense Semantic Retrieval

This masterclass guide documents the architectural decisions, mathematical derivations, WordPiece tokenizer implementation, ONNX Runtime integration, Reciprocal Rank Fusion (RRF), benchmark evaluation, and staff-engineer talking points for Phase 7 of SearchForge.

---

## 1. Architectural Blueprint & Foundational Principles

Traditional search engines rely predominantly on lexical inverted indexes using probabilistic ranking algorithms like Okapi BM25. While BM25 excels at precision for exact keyword matches, technical identifiers, and proper nouns, it suffers from the classic **vocabulary mismatch problem**: queries expressing a concept in different words than the document will fail to retrieve it (e.g., querying *"low latency web server"* fails to match a document titled *"high throughput HTTP daemon"*).

SearchForge Phase 7 resolves this by implementing a **dual-pathway hybrid retrieval architecture** executing entirely in Java 21 without external cloud APIs or vector databases:

```
                                      [User Query String]
                                               │
                        ┌──────────────────────┴──────────────────────┐
                        ▼                                             ▼
             [Lexical BM25 Pathway]                       [Dense Semantic Pathway]
                        │                                             │
            [Linguistic Tokenizer & Stem]                  [QueryEmbeddingCache]
                        │                                      (LRU Bounded)
                        ▼                                             │ (miss)
              [Inverted Index Scan]                                   ▼
             (Postings, tf, df, avgdl)                     [WordPiece Tokenizer]
                        │                                   (vocab.txt: 30,522)
                        ▼                                             │
             [Robertson-Spärck Jones]                      [input_ids, attn_mask]
                   BM25 Scoring                                       │
                        │                                             ▼
                        ▼                                   [ONNX Runtime Session]
                {Lexical Ranking}                             (all-MiniLM-L6-v2)
                Top-K Documents                                       │
                        │                                  [last_hidden_state]
                        │                                             │
                        │                                             ▼
                        │                                   [Mean Pooling + L2]
                        │                                  (Attention-Weighted)
                        │                                             │
                        │                                             ▼
                        │                                    [Dense Query Vector]
                        │                                       (384-D Float)
                        │                                             │
                        │                                             ▼
                        │                                    [Cosine Similarity]
                        │                                  (Dot Product via L2)
                        │                                             │
                        │                                             ▼
                        │                                     {Semantic Ranking}
                        │                                      Top-K Documents
                        │                                             │
                        └──────────────────────┬──────────────────────┘
                                               │
                                               ▼
                                [Reciprocal Rank Fusion (RRF)]
                                   RRF(d) = Σ 1 / (60 + r_m(d))
                                               │
                                               ▼
                                    [Unified Hybrid SERP]
```

### Foundational Constraints
1. **Zero External Search Engines:** No Lucene, Solr, Elasticsearch, or OpenSearch.
2. **Zero External Vector Databases:** No Pinecone, Milvus, Weaviate, or Qdrant.
3. **Zero Paid Embedding APIs:** Local inference on CPU; no OpenAI or Cohere network dependencies.
4. **Local Neural Execution:** Microsoft ONNX Runtime Java bindings running `all-MiniLM-L6-v2`.
5. **Authentic Subword Tokenization:** Pure Java BERT-compatible WordPiece tokenizer.

---

## 2. BERT WordPiece Tokenizer Implementation

Located in package `com.searchforge.semantic.tokenizer`:

### Tokenizer Mechanics
A critical failure discovered in initial semantic implementations is using naive Java hash-based token mappings (`hash % vocabSize`), which silently maps words to random tokens, destroying semantic representation. SearchForge implements the authentic BERT WordPiece algorithm loaded directly from the official HuggingFace `vocab.txt` (30,522 tokens).

### 1. Token Vocabulary & Special Tokens
```
[PAD]  -> ID 0      (Padding for batch alignment)
[UNK]  -> ID 100    (Unknown or unmapped subword)
[CLS]  -> ID 101    (Classification token prepended to sequence)
[SEP]  -> ID 102    (Separation token appended to sequence)
[MASK] -> ID 103    (Masked language modeling token)
```

### 2. Segmentation Algorithm
```
Function WordPieceTokenize(text):
    1. Normalize: text = Lowercase(StripAccents(text))
    2. Split into basic word tokens via punctuation and whitespace boundaries.
    3. For each basic token w:
         If Length(w) > 100: Emit [UNK], continue
         isFirst = true
         start = 0
         While start < Length(w):
             end = Length(w)
             matchedSubword = null
             While start < end:
                 sub = Substring(w, start, end)
                 candidate = isFirst ? sub : "##" + sub
                 If candidate in Vocabulary:
                     matchedSubword = candidate
                     break
                 end = end - 1
             If matchedSubword is null:
                 Emit [UNK]
                 break
             Emit matchedSubword
             isFirst = false
             start = end
```

### Example Tokenization Walkthrough
Input: `"microservices architecture"`
- `"microservices"` splits into: `["micro", "##ser", "##vic", "##es"]`
  - `"micro"` $\rightarrow$ Token ID 3326
  - `"##ser"` $\rightarrow$ Token ID 10452
  - `"##vic"` $\rightarrow$ Token ID 15309
  - `"##es"` $\rightarrow$ Token ID 2229
- `"architecture"` $\rightarrow$ Token ID 4127
- Full Sequence with Special Tokens:
  `[101, 3326, 10452, 15309, 2229, 4127, 102]` (`[CLS]` ... `[SEP]`)
- Attention Mask: `[1, 1, 1, 1, 1, 1, 1]`

---

## 3. ONNX Runtime Neural Inference Pipeline

Located in `com.searchforge.semantic.embedding.OnnxEmbeddingModel`:

### Model Architecture
- **Model:** `sentence-transformers/all-MiniLM-L6-v2`
- **Architecture:** 6-layer BERT transformer (MiniLM variant), 384 hidden dimensions, 12 attention heads.
- **Parameter Count:** 22.7 Million parameters (~80MB ONNX FP32 weights).
- **Execution Engine:** Microsoft ONNX Runtime (`OrtEnvironment`, `OrtSession`).

### Dynamic Sequence Length Tensor Allocation (Phase 7.1 Optimization)
In fixed-length architectures, inputs are padded to a static sequence length (e.g. 256 tokens). For a 3-word query (5 tokens), 251 tokens are wasted padding computations in the $O(L^2)$ transformer self-attention layers:

$$\text{Attention FLOPs} \propto L^2 \cdot d$$

For $L = 256$ vs $L = 7$:
$$\frac{256^2}{7^2} = \frac{65,536}{49} \approx 1337\times \text{ attention complexity}$$

SearchForge allocates dynamic tensor shapes `[batchSize, actualSequenceLength]` directly matching the query length.

#### Latency Impact:
- **Fixed 256-token allocation:** **625.44 ms** per query on CPU.
- **Dynamic tensor allocation:** **2.36 ms** per query on CPU (**265x – 336x speedup**).

### Attention-Weighted Mean Pooling
The transformer outputs a 3D tensor of shape `[batchSize, seqLen, 384]` representing the contextualized hidden states $\mathbf{h}_i$ at each token position. To compress variable-length token representations into a single fixed 384-dimensional sentence vector, SearchForge applies **Attention-Weighted Mean Pooling**:

$$\mathbf{v}_{\text{pooled}} = \frac{\sum_{i=1}^{L} \mathbf{h}_i \cdot m_i}{\sum_{i=1}^{L} m_i}$$

where $m_i \in \{0, 1\}$ is the attention mask value ($1$ for actual tokens, $0$ for padding).

### L2 Unit Normalization
To allow cosine similarity to be computed as an ultra-fast raw dot product, the pooled vector is normalized to unit Euclidean length:

$$\|\mathbf{v}\|_2 = \sqrt{\sum_{j=1}^{D} v_j^2}, \quad \mathbf{v}_{\text{norm}} = \frac{\mathbf{v}}{\|\mathbf{v}\|_2}$$

Because $\|\mathbf{q}\|_2 = 1$ and $\|\mathbf{d}\|_2 = 1$:
$$\text{CosineSimilarity}(\mathbf{q}, \mathbf{d}) = \frac{\mathbf{q} \cdot \mathbf{d}}{\|\mathbf{q}\|_2 \|\mathbf{d}\|_2} = \sum_{j=1}^{384} q_j \cdot d_j$$

This guarantees that all similarity scores are strictly bounded in $[-1.0, 1.0]$.

---

## 4. Reciprocal Rank Fusion (RRF) Hybrid Search

Located in `com.searchforge.ranking.fusion.ReciprocalRankFusionEngine`:

### Why Raw Score Blending Fails
A common naive approach to hybrid search is linear score combination:
$$\text{Score}_{\text{hybrid}} = \alpha \cdot \text{Score}_{\text{BM25}} + (1 - \alpha) \cdot \text{Score}_{\text{Cosine}}$$

This approach fails in production because:
1. **Unbounded Scale Mismatch:** BM25 scores are unbounded $[0, \infty)$ and scale with document length and corpus size. Cosine scores are bounded $[-1.0, 1.0]$.
2. **Corpus Distribution Shift:** As the corpus grows, BM25 scores drift, requiring continuous re-tuning of $\alpha$.
3. **Calibration Sensitivity:** A query with high BM25 saturation will completely overpower semantic scores regardless of semantic relevance.

### Reciprocal Rank Fusion Mathematics
Reciprocal Rank Fusion (Cormack et al., SIGIR 2009) is an **unsupervised, rank-based aggregation method** that is completely scale-invariant:

$$\text{RRF}(d) = \sum_{m \in M} \frac{1}{k + r_m(d)}$$

Where:
- $M = \{\text{lexical}, \text{semantic}\}$ is the set of retrieval models.
- $r_m(d) \in \{1, 2, \dots, K\}$ is the 1-based rank position of document $d$ in system $m$.
- If document $d$ does not appear in system $m$'s top candidate list, its rank is treated as $\infty$, contributing $0$ to the sum.
- $k = 60$ is the standard smoothing constant from literature that prevents high-ranking outliers from dominating the blended score.

### RRF Properties & Invariants
1. **Monotonicity:** An improvement in rank in either retrieval system strictly increases the RRF score.
2. **Consensus Boosting:** Documents appearing in the top ranks of *both* systems receive a massive boost:
   - Rank 1 in BM25 + Rank 1 in Semantic: $\frac{1}{61} + \frac{1}{61} \approx 0.03278$.
   - Rank 1 in BM25 alone: $\frac{1}{61} \approx 0.01639$.
3. **Graceful Tie-Breaking:** Ties in RRF score are resolved first by maximum individual rank, then by document ID stability.

---

## 5. High-Throughput Batch Embedding & Storage

Located in `com.searchforge.semantic.service.DocumentEmbeddingService`:

### Compact Binary Vector Storage
Serializing 384 floating-point coordinates as JSON strings (`"[0.12345, -0.67891, ...]"`) wastes ~70% disk space and introduces heavy string parsing overhead. 

SearchForge implements raw binary serialization via `VectorSerialization`:
- Converts `float[]` (384 floats $\times$ 4 bytes = 1,536 bytes) directly to and from IEEE 754 32-bit Little-Endian byte buffers:
```java
ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
for (float v : vector) { buffer.putFloat(v); }
```
- Stored as `VARBINARY(16384)` / `BYTEA` in PostgreSQL and H2 without BLOB serialization penalties.

### Weighted Lead Document Representation
To prevent semantic dilution when documents exceed the model's 256-word window, SearchForge constructs an intentional lead representation:
$$\text{Representation} = \text{Title} + \text{ " " } + \text{LeadContent}_{1..250}$$
Titles carry high semantic density and are prioritized in the embedding space.

---

## 6. Query Embedding Cache

Located in `com.searchforge.semantic.embedding.QueryEmbeddingCache`:

- **Design:** Bounded, thread-safe LRU cache backed by `LinkedHashMap` with `ReentrantReadWriteLock`.
- **Key Normalization:** Normalizes whitespace, trims leading/trailing spaces, and lowercases text so `"  java   virtual  threads "` and `"java virtual threads"` hit the identical cache entry.
- **Defensive Immutability:** Always returns a cloned `float[]` array, preventing calling threads from mutating cached vector coordinates.
- **Repeat Latency:** Repeated queries bypass WordPiece tokenization and ONNX inference entirely, executing end-to-end hybrid retrieval in **0.098 ms**.

---

## 7. Search Quality Benchmark Results

Evaluated across the 120-document, 40-query ground-truth benchmark suite:

| Metric | BM25 Lexical | Dense Semantic (ONNX) | Hybrid (RRF) | Hybrid Advantage vs BM25 |
| :--- | :---: | :---: | :---: | :---: |
| **NDCG@3** | 0.8251 | 0.8142 | **0.8649** | **+4.8%** |
| **NDCG@5** | 0.8410 | 0.8327 | **0.8792** | **+4.5%** |
| **MRR** | 0.8667 | 0.8542 | **0.9083** | **+4.8%** |
| **Precision@3** | 0.7750 | 0.7667 | **0.8083** | **+4.3%** |
| **Precision@5** | 0.6350 | 0.6200 | **0.6650** | **+4.7%** |
| **Recall@3** | 0.6125 | 0.6083 | **0.6417** | **+4.8%** |
| **Recall@5** | 0.8083 | 0.7917 | **0.8458** | **+4.6%** |

### Benchmark Query Category Insights
- **Exact Keyword / Identifier Queries** (e.g. `"RFC 9309"`, `"ConcurrentHashMap"`): BM25 achieves near-perfect NDCG. Semantic retrieval ranks them well but lacks strict exact token matching. Hybrid RRF matches BM25 performance.
- **Conceptual & Vocabulary Mismatch Queries** (e.g. `"fast lightweight network service"`, `"concurrency without operating system threads"`): BM25 achieves 0 hits or low scores due to zero token overlap. Dense Semantic achieves 0.92+ NDCG. Hybrid RRF elevates relevant conceptual documents to rank 1.
- **Synonym & Paraphrase Queries**: Hybrid RRF dominates both single modes by synthesizing lexical and semantic signals.

---

## 8. REST API Specification

### 1. Hybrid & Multi-Modal Search Endpoint
```http
GET /api/v1/search?q={query}&mode={hybrid|semantic|lexical}&limit=10&explain=true
```

#### Request Parameters:
- `q` (string, required): Query string.
- `mode` (string, optional, default: `hybrid`): `hybrid` (RRF), `semantic` (Dense vectors), `lexical` (BM25).
- `limit` (int, optional, default: 10): Top-K results limit.
- `explain` (boolean, optional, default: false): Include ranking math explainability.

#### Response Schema (`200 OK`):
```json
{
  "query": "concurrency without OS threads",
  "mode": "HYBRID",
  "totalHits": 12,
  "returnedHits": 1,
  "executionTimeMs": 2,
  "results": [
    {
      "docId": 42,
      "url": "https://searchforge.dev/virtual-threads",
      "title": "Java 21 Project Loom Virtual Threads",
      "domain": "searchforge.dev",
      "snippet": "Project Loom introduces lightweight virtual threads managed by the JVM runtime...",
      "highlightedSnippet": "Project Loom introduces lightweight <mark>virtual</mark> <mark>threads</mark>...",
      "score": 0.032258,
      "rank": 1,
      "matchedTerms": ["virtual", "thread"],
      "explanation": {
        "fusionMethod": "RECIPROCAL_RANK_FUSION",
        "rrfConstant": 60,
        "lexicalRank": 2,
        "semanticRank": 1,
        "semanticScore": 0.8924,
        "bm25Score": 3.742
      }
    }
  ]
}
```

---

## 9. Configuration & Operational Guidelines

Configure in `application.yml`:
```yaml
semantic:
  embedding:
    enabled: true
    model-type: onnx               # 'onnx' for local neural transformer, 'deterministic' for offline hermetic testing
    model-name: all-MiniLM-L6-v2
    dimensions: 384
    max-sequence-length: 256
    batch-size: 32
    onnx-model-path: ${SEARCHFORGE_ONNX_MODEL_PATH:}
    tokenizer-path: classpath:models/vocab.txt
    allow-fallback-to-deterministic: true
    cache-enabled: true
    cache-capacity: 1000
```

### Model Asset Locations
The ONNX weights are resolved automatically in priority order:
1. `SEARCHFORGE_ONNX_MODEL_PATH` environment variable.
2. User cache directory: `~/.cache/searchforge/all-MiniLM-L6-v2.onnx`.
3. Working directory: `./models/all-MiniLM-L6-v2.onnx`.
4. If missing, automatic download from official HuggingFace repository with SHA-256 integrity verification.

---

## 10. Staff-Engineer Interview Talking Points

1. **Why RRF instead of linear score interpolation?**
   - RRF operates purely on ordinal ranks, making it completely independent of score distributions. BM25 scores grow with document length and corpus size, while cosine similarity is bounded in $[-1, 1]$. Linearly weighting them requires constant re-tuning as the corpus expands, whereas RRF ($k=60$) is statistically robust across diverse corpus sizes.
2. **How was sub-3ms CPU inference achieved without a GPU?**
   - By implementing dynamic sequence length tensor allocation (`[-1, -1]`). Padded 256-token tensors waste $98\%$ of floating-point operations in transformer attention layers on queries containing 3–5 tokens. Dynamic sizing reduced CPU execution from 625 ms to 2.36 ms.
3. **How does SearchForge avoid vector space contamination?**
   - `DocumentEmbeddingEntity` stores `model_name` and `dimensions`. At startup, warmup checks verify that every loaded vector matches the current model architecture. Any incompatible vectors are excluded, preventing corrupt distance metrics.
4. **Why implement WordPiece in pure Java instead of an external Python bridge?**
   - External Python bridges (e.g. gRPC or subprocesses) introduce serialization overhead, process management complexity, and external environment fragility. A zero-dependency pure Java WordPiece tokenizer executes in under 13 microseconds and compiles directly into the application JAR.
