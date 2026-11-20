# SearchForge — Phase 5: Query Intelligence & Search Experience Complete Guide

This comprehensive guide is available in full at [`docs/phases/PHASE_5_QUERY_INTELLIGENCE.md`](phases/PHASE_5_QUERY_INTELLIGENCE.md).

It contains:
- Complete architectural pipeline from user input to enriched JSON results.
- Mathematical formulations and algorithmic derivations for:
  - Weighted Trie Autocomplete ($\text{Score} = \ln(1+\text{ctf}) \cdot \sqrt{\text{df}}$).
  - Optimal String Alignment Damerau-Levenshtein distance & Noisy Channel spelling model.
  - Dynamic sliding-window query density scoring & anti-XSS `<mark>` tagging.
  - Multi-table directional synonym expansion & discounted BM25 scoring.
- Empirical latency benchmark percentiles (mean, p50, p90, p99).
- Information Retrieval relevance evaluation (P@K, R@K, MRR).
- Exhaustive audit of all 273 hermetic automated tests.
