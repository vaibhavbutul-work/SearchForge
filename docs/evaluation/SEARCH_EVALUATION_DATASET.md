# SearchForge: Search Evaluation Dataset & Relevance Judgments

**Document Version:** 1.0.0  
**Status:** COMPLETE & VERIFIED  
**Phase:** 12 — Task 12.1  
**Author:** SearchForge Architecture & Relevance Engineering Team  
**Corpus Scope:** 120 Multi-Domain Documents (IDs 101L - 220L)  
**Query Scope:** 48 Evaluated Queries Across 8 Information Retrieval Archetypes  

---

## 1. Executive Summary & Objective

In Phase 12, SearchForge transitions from building search infrastructure to rigorously evaluating and optimizing retrieval and ranking quality. Prior to this phase, search quality was assessed primarily via functional unit tests or small-scale sanity checks.

Task 12.1 establishes the foundational benchmark of the Cranfield Paradigm:
1. **A Standardized Document Corpus:** 120 deterministic, multi-domain technical documents.
2. **A Representative Query Set:** 48 categorized search queries covering 8 distinct IR archetypes.
3. **Ground-Truth Relevance Judgments:** 4-level graded relevance assessments $[0, 3]$ with documented rationales.
4. **Data Isolation Strategy:** Mutually exclusive `DEV` (50%), `TUNING` (25%), and `EVAL` (25%) splits to prevent data leakage and optimization overfitting.

---

## 2. Evaluation Corpus Architecture

The evaluation corpus consists of **120 documents** (`SearchForgeEvaluationCorpus.java`) spanning 10 core technical and encyclopedic domains, plus 35 realistic background distractor documents:

| Domain | Document ID Range | Primary Topics Covered |
| :--- | :---: | :--- |
| **Java & Concurrency** | `101L - 105L` | Java 21 virtual threads, Project Reactor, JVM GC, thread pools, serialization. |
| **Databases & Distributed Systems** | `106L - 110L` | PostgreSQL MVCC, Raft consensus, microservices, B-Tree layouts, basic SQL. |
| **OS & Hardware Architecture** | `111L - 115L` | Linux CFS scheduler, NVMe flash drives, CPU cache hierarchies, vintage PCs. |
| **Biomedical & Human Anatomy** | `121L - 125L` | Heart chambers, arterial blood pressure, aerobic exercise, ergonomics. |
| **Botany & Plant Biology** | `131L - 135L` | Chloroplast photosynthesis, forest carbon sequestration, xylem water transport. |
| **Astronomy & Astrophysics** | `141L - 145L` | Supermassive black holes, stellar collapse, gravitational lensing, telescopes. |
| **Printing & Literature** | `151L - 155L` | Gutenberg movable type press, Library of Alexandria, e-ink, bookbinding. |
| **Acoustics & Music** | `161L - 165L` | Acoustic guitar soundboard, grand piano action, amplifiers, violin varnish. |
| **Urban Transit & Bicycles** | `171L - 175L` | Commuter cycling, bicycle derailleurs, mountain bikes, electric scooters. |
| **Culinary Science & Fermentation** | `181L - 185L` | Sourdough wild yeast fermentation, espresso roasting chemistry, pasta, skillets. |
| **Background Distractors** | `186L - 220L` | 35 diverse encyclopedic articles to simulate corpus noise and competition. |

---

## 3. Query Archetypes & Information Retrieval Challenges

The 48 queries are evenly partitioned across **8 Information Retrieval archetypes** (6 queries each), ensuring that evaluation does not favor lexical or semantic search unfairly:

```
[ 48 Benchmark Queries ]
  ├── 1. EXACT_KEYWORD (Q01 - Q06)      -> High lexical overlap with target canonical terminology
  ├── 2. BROAD_TOPICAL (Q07 - Q12)     -> Multi-faceted subject areas matching multiple target docs
  ├── 3. CONCEPTUAL (Q13 - Q18)         -> Intent-based queries with near-zero literal keyword overlap
  ├── 4. MULTI_CONCEPT (Q19 - Q24)       -> Intersecting multiple independent technical disciplines
  ├── 5. AMBIGUOUS (Q25 - Q30)           -> Terse, underspecified single-word or short terms
  ├── 6. NATURAL_LANGUAGE (Q31 - Q36)   -> Conversational phrasing and full grammatical questions
  ├── 7. RARE_TERM (Q37 - Q42)          -> Low-frequency specialized jargon and domain acronyms
  └── 8. SYNONYM (Q43 - Q48)            -> Paraphrased queries using vocabulary absent from target docs
```

---

## 4. Four-Level Graded Relevance Scale

Relevance judgments are recorded in `SearchForgeEvaluationDataset.java` using a 4-level scale:

| Grade | Label | Operational Definition | Example |
| :---: | :--- | :--- | :--- |
| **3** | **Highly Relevant** | The primary authoritative target document that directly, specifically, and comprehensively answers the query intent. | For query `"java 21 virtual threads"`, Doc 101 (*Java 21 Virtual Threads Documentation*) = **Grade 3**. |
| **2** | **Relevant** | Substantively addresses the core topic or answers the query, but is secondary in depth or broader in scope. | For query `"java 21 virtual threads"`, Doc 104 (*Java Concurrency Utilities and Thread Pools*) = **Grade 2**. |
| **1** | **Slightly Relevant** | Topically adjacent or mentions relevant sub-concepts, but does not directly answer the user's information need. | For query `"java 21 virtual threads"`, Doc 102 (*Reactive Concurrency with Project Reactor*) = **Grade 1**. |
| **0** | **Not Relevant** | Irrelevant, off-topic, or distractor document. All unjudged documents are treated as Grade 0. | Any other document in the corpus = **Grade 0**. |

---

## 5. Candidate Pooling & Ground-Truth Methodology

To avoid algorithmic bias toward either BM25 or neural dense embeddings, relevance judgments were created using **Candidate Pooling**:
1. Top-10 candidates were retrieved using baseline **BM25** ($k_1=1.2, b=0.75$).
2. Top-10 candidates were retrieved using baseline **ONNX Dense Semantic Search** (`all-MiniLM-L6-v2` cosine similarity).
3. Top-10 candidates were retrieved using **Hybrid Search** (Reciprocal Rank Fusion with $k=60$).
4. The union of these candidate sets was combined with known domain targets.
5. SearchForge engineering team members independently assessed each pooled document against the query intent, assigning grades $[0, 3]$ and recording documented rationales.

*Transparency Note on Origin:* These judgments are expert-curated ground truth authored for this synthetic multi-domain corpus. They are not simulated or generated randomly by an LLM.

---

## 6. Dataset Split Strategy: Preventing Data Leakage

To guarantee that ranking optimizations in Task 12.6 represent true generalization rather than overfitting, queries are partitioned into three mutually exclusive splits:

| Split | Query Count | Percentage | Purpose | Invariant Rule |
| :--- | :---: | :---: | :--- | :--- |
| **`DEV`** | 24 | 50% | Exploratory baseline benchmarking, error analysis, and profiling across Tasks 12.3, 12.4, 12.5. | 3 queries per category (8 categories). |
| **`TUNING`** | 12 | 25% | Used strictly in Task 12.6 to tune BM25 $k_1/b$, candidate depth, and fusion weights. | Zero overlap with `DEV` or `EVAL`. |
| **`EVAL`** | 12 | 25% | **Held-out blind evaluation split.** Evaluated only in Tasks 12.8 and 12.9 to verify out-of-sample ranking quality. | Never inspected or tuned against during parameter optimization. |

### Query Catalog by Split:

```
[ DEV Split - 24 Queries ]
  Q01: java 21 virtual threads (EXACT_KEYWORD)
  Q02: postgresql acid transaction isolation mvcc (EXACT_KEYWORD)
  Q03: raft consensus algorithm distributed state (EXACT_KEYWORD)
  Q07: relational database systems storage and transactions (BROAD_TOPICAL)
  Q08: human cardiovascular system circulation and blood pressure (BROAD_TOPICAL)
  Q09: astronomical universe galaxies stars and black holes (BROAD_TOPICAL)
  Q13: how can I make software run faster with lightweight concurrency (CONCEPTUAL)
  Q14: preventing lower back strain when sitting at a desk all day (CONCEPTUAL)
  Q15: deep space gravitational attraction bending light beams (CONCEPTUAL)
  Q19: database indexing and crash recovery logs (MULTI_CONCEPT)
  Q20: microservices communication and distributed consensus (MULTI_CONCEPT)
  Q21: cardiac blood circulation and arterial resistance (MULTI_CONCEPT)
  Q25: concurrency (AMBIGUOUS)
  Q26: postgres (AMBIGUOUS)
  Q27: scheduler (AMBIGUOUS)
  Q31: how do green trees absorb atmospheric carbon dioxide from the air (NATURAL_LANGUAGE)
  Q32: how do microservice architectures communicate across decentralized services (NATURAL_LANGUAGE)
  Q33: what happens inside the heart chambers during blood circulation (NATURAL_LANGUAGE)
  Q37: cfs red-black tree virtual runtime (RARE_TERM)
  Q38: nvme pcie flash storage controller iops (RARE_TERM)
  Q39: mvcc write-ahead logging wal (RARE_TERM)
  Q43: espresso bean brewing and hot morning caffeinated beverage (SYNONYM)
  Q44: cardiac muscular organ pumping arterial blood (SYNONYM)
  Q45: two-wheeled human pedal transit for urban travel (SYNONYM)

[ TUNING Split - 12 Queries ]
  Q04: supermassive black holes event horizons (EXACT_KEYWORD)
  Q05: gutenberg movable type printing press (EXACT_KEYWORD)
  Q10: plant biology photosynthesis and carbon absorption (BROAD_TOPICAL)
  Q16: how living organisms turn sunlight into chemical energy (CONCEPTUAL)
  Q17: preventing data loss when a computer server crashes suddenly (CONCEPTUAL)
  Q22: solid state storage and microprocessor cache memory (MULTI_CONCEPT)
  Q28: photosynthesis (AMBIGUOUS)
  Q29: black holes (AMBIGUOUS)
  Q34: how does a bicycle derailleur shift gears across the chain (NATURAL_LANGUAGE)
  Q40: chloroplast thylakoids calvin cycle (RARE_TERM)
  Q41: xylem tracheids transpiration pull capillary action (RARE_TERM)
  Q46: wild yeast sourdough fermentation and baked wheat loaf (SYNONYM)

[ EVAL Split - 12 Queries ]
  Q06: sourdough bread wild yeast fermentation (EXACT_KEYWORD)
  Q11: acoustic and keyboard musical instruments (BROAD_TOPICAL)
  Q12: urban transportation and bicycle mobility (BROAD_TOPICAL)
  Q18: amplifying string vibrations into warm acoustic tones (CONCEPTUAL)
  Q23: plant water transport and forest carbon storage (MULTI_CONCEPT)
  Q24: coffee roasting chemistry and bread fermentation (MULTI_CONCEPT)
  Q30: derailleur (AMBIGUOUS)
  Q35: how do CPUs use memory caches to prevent slow RAM access (NATURAL_LANGUAGE)
  Q36: how was information printed and distributed before modern computers (NATURAL_LANGUAGE)
  Q42: escapement mechanism hammer strikes piano (RARE_TERM)
  Q47: stellar core collapse and catastrophic supernova explosion (SYNONYM)
  Q48: ancient papyrus manuscript repository in egypt (SYNONYM)
```

---

## 7. Known Dataset Limitations

1. **Synthetic Nature:** Documents are synthetically generated encyclopedic summaries rather than multi-megabyte raw web pages with boilerplate, advertising, and broken HTML.
2. **Corpus Scale ($N=120$):** While 120 documents provide sufficient competition to evaluate precision and recall without CPU saturation in CI, real-world web corpora contain millions of pages with higher lexical collision rates.
3. **Absence of User Behavioral Signals:** Relevance judgments reflect topical and semantic alignment, but do not incorporate click-through rate (CTR), dwell time, or user query logs.
