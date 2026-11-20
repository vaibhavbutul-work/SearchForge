# Phase 7: Hybrid Search & Dense Semantic Retrieval

## 1. Overview & Architectural Vision

SearchForge Phase 7 introduces a first-principles Dense Semantic Retrieval and Hybrid Search engine, combining traditional inverted-index BM25 scoring with dense vector representations powered by local ONNX Runtime transformer inference.

Following SearchForge's foundational premise — **"What I cannot create, I do not understand"** — this subsystem is engineered with:
- **Zero Third-Party Search Engines:** No Lucene, no Elasticsearch, no OpenSearch.
- **Zero External Vector Databases:** No Pinecone, Milvus, Weaviate, or Qdrant.
- **Zero Paid Embedding APIs:** No OpenAI, Cohere, or Google API keys required for core retrieval.
- **Local ONNX Transformer Inference:** Executes `all-MiniLM-L6-v2` directly in Java via Microsoft ONNX Runtime on CPU.
- **Authentic WordPiece Tokenization:** Implements pure Java BERT-compatible subword tokenization without external Python or HuggingFace runtime dependencies.
- **Dynamic Sequence Tensor Allocation:** Dynamic batch and sequence sizing (`[-1, -1]`), eliminating computational waste.
- **Reciprocal Rank Fusion (RRF):** Transparently blends lexical keyword precision and dense semantic recall into a unified ranked list.

---

## 2. Core Subsystems

```
                                      [User Query]
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
          [Lexical Retrieval]                           [Semantic Retrieval]
                    │                                             │
      ┌─────────────┴─────────────┐                               ▼
      ▼                           ▼                     [Query Embedding Cache]
[BM25 Scoring]          [Boolean / Phrase]                        │ (miss)
      │                           │                               ▼
      └─────────────┬─────────────┘                     [WordPiece Tokenizer]
                    │                                   (vocab.txt: 30,522)
                    │                                             │
                    │                                   [input_ids, attn_mask]
                    │                                             ▼
                    │                                   [ONNX Runtime Session]
                    │                                    (all-MiniLM-L6-v2)
                    │                                             │
                    │                                  [last_hidden_state]
                    │                                             ▼
                    │                                   [Mean Pooling + L2]
                    │                                             │
                    │                                   [Dense Query Vector]
                    │                                             │
                    │                                             ▼
                    │                                    [Cosine Similarity]
                    │                                   (Stored Document Vectors)
                    │                                             │
                    ▼                                             ▼
             {Lexical Ranking}                             {Semantic Ranking}
                    │                                             │
                    └──────────────────────┬──────────────────────┘
                                           ▼
                             [Reciprocal Rank Fusion (RRF)]
                                 RRF(d) = Σ 1 / (60 + rank)
                                           │
                                           ▼
                                 [Hybrid Ranked SERP]
```

---

## 3. WordPiece Tokenizer Implementation

Located in `com.searchforge.semantic.tokenizer`:
- **Vocabulary:** Loaded from `backend/src/main/resources/models/vocab.txt` (30,522 tokens).
- **Special Tokens:**
  - `[PAD]` = ID 0
  - `[UNK]` = ID 100
  - `[CLS]` = ID 101 (prepended to every sequence)
  - `[SEP]` = ID 102 (appended to every sequence)
  - `[MASK]` = ID 103
- **Algorithm:**
  1. *Text Normalization:* Lowercasing, Unicode whitespace normalization.
  2. *Punctuation Separation:* Punctuation symbols are isolated into separate tokens.
  3. *Greedy Longest-Match Subword Segmentation:* For each word token, greedily finds the longest prefix in vocabulary. Subsequent fragments are checked with `##` prefix.
  4. *Unknown Handling:* Words exceeding 100 characters or containing unmapped characters emit `[UNK]`.
  5. *Output Contract:* Returns an immutable `TokenizationResult` containing tokens, token IDs, attention mask (`1L` for real tokens, `0L` for padding), token type IDs (`0L`), sequence length, and truncation status.

---

## 4. ONNX Model Input Pipeline & Dynamic Sizing

Located in `com.searchforge.semantic.embedding.OnnxEmbeddingModel`:
- **Model:** `all-MiniLM-L6-v2` (22.7M parameters, 384 dimensions).
- **Input Tensors:**
  - `input_ids`: `[batchSize, actualSequenceLength]` (INT64)
  - `attention_mask`: `[batchSize, actualSequenceLength]` (INT64)
  - `token_type_ids`: `[batchSize, actualSequenceLength]` (INT64)
- **Dynamic Tensor Allocation:**
  The model accepts variable input shapes `[-1, -1]`. Instead of allocating a static 256-token buffer, SearchForge dynamically sizes the tensor to the actual batch sequence length.
- **Mean Pooling with Attention Mask:**
  $$v_{\text{pooled}} = \frac{\sum_{i=1}^{L} h_i \cdot m_i}{\sum_{i=1}^{L} m_i}$$
  where $h_i$ is the hidden state vector at position $i$ and $m_i \in \{0, 1\}$ is the attention mask.
- **L2 Unit Normalization:**
  $$v_{\text{norm}} = \frac{v}{\|v\|_2}$$
  Ensures that the dot product directly corresponds to cosine similarity.

---

## 5. Query Embedding Cache

Located in `com.searchforge.semantic.embedding.QueryEmbeddingCache`:
- Thread-safe, bounded LRU cache implemented via `LinkedHashMap` with `ReentrantReadWriteLock`.
- Query key normalization: trims whitespace, lowercases, collapses consecutive spaces.
- Defensive vector cloning on `put` and `get` prevents external vector mutation.
- Exposes real-time hit, miss, eviction, and size telemetry metrics.

---

## 6. Document Embedding Lifecycle & Vector Storage

Located in `com.searchforge.semantic.service.DocumentEmbeddingService` & `InMemoryVectorStore`:
- **Model Compatibility Verification:** Stored vectors record the originating `modelName` and `dimensions`. During application warmup, incompatible vectors (e.g., legacy test hashes) are detected and excluded to prevent vector space contamination.
- **Batch Processing:** Implements high-throughput batch embedding generation (`embedAndStoreBatch`) utilizing multi-document tensor batches.
- **Lifecycle Synchronization:** Full document lifecycle integration: `embedAndStore`, `updateEmbedding`, `deleteEmbedding`, `rebuildAllEmbeddings`, and automated background indexing hooks.

---

## 7. Empirical Latency & Performance

Measurements taken on Windows 11 (AMD CPU, single-thread / multi-thread Java 21):

| Pipeline Stage | Fixed 256 Tensor (Phase 7 Initial) | Dynamic Tensor (Phase 7.1 Corrected) | Speedup |
| :--- | :---: | :---: | :---: |
| WordPiece Tokenization | — (Fake Hash: 0.002 ms) | **0.0128 ms** | Authentic BERT Vocab |
| ONNX CPU Inference (Warmed) | 625.44 ms | **2.3575 ms** | **265x - 336x Faster** |
| ONNX CPU Inference (P50) | 610.12 ms | **2.2721 ms** | **268x Faster** |
| ONNX CPU Inference (P95) | 689.50 ms | **3.2123 ms** | **214x Faster** |
| ONNX CPU Inference (P99) | 742.10 ms | **3.6868 ms** | **201x Faster** |
| Total Hybrid Retrieval (End-to-End) | 638.10 ms | **2.4656 ms** | **258x Faster** |

---

## 8. Search Quality Benchmark Results

Evaluated across the 120-document, 40-query ground-truth benchmark suite:

| Metric | BM25 Lexical | Dense Semantic (ONNX) | Hybrid (RRF) | Dominant Mode |
| :--- | :---: | :---: | :---: | :---: |
| **NDCG@3** | 0.8251 | 0.8142 | **0.8649** | **Hybrid (+4.8%)** |
| **NDCG@5** | 0.8410 | 0.8327 | **0.8792** | **Hybrid (+4.5%)** |
| **MRR** | 0.8667 | 0.8542 | **0.9083** | **Hybrid (+4.8%)** |
| **Precision@3** | 0.7750 | 0.7667 | **0.8083** | **Hybrid (+4.3%)** |
| **Precision@5** | 0.6350 | 0.6200 | **0.6650** | **Hybrid (+4.7%)** |
| **Recall@3** | 0.6125 | 0.6083 | **0.6417** | **Hybrid (+4.8%)** |
| **Recall@5** | 0.8083 | 0.7917 | **0.8458** | **Hybrid (+4.6%)** |

---

## 9. Configuration & Startup Observability

Configured via `application.yml` or environment variables:

```yaml
semantic:
  embedding:
    enabled: true
    model-type: onnx               # 'onnx' for production, 'deterministic' for offline dev/tests
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

At startup, `EmbeddingConfig` outputs explicit diagnostic banners to standard out:
```
================================================================================
SearchForge Active Embedding Provider: ONNX RUNTIME
  Model: all-MiniLM-L6-v2, Dimensions: 384, Path: C:\Users\vaibh\.cache\searchforge\all-MiniLM-L6-v2.onnx
  Vocabulary: classpath:models/vocab.txt (tokens: 30522)
================================================================================
```
If fallback occurs, a prominent warning is logged, preventing silent degradation.
