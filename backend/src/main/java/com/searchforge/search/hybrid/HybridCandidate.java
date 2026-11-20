package com.searchforge.search.hybrid;

import java.util.List;
import java.util.Map;

/**
 * Candidate item retrieved and optionally fused across lexical and dense semantic engines.
 *
 * @param docId         unique document identifier
 * @param score         final ranking score (RRF score for hybrid, BM25 score for lexical, cosine similarity for semantic)
 * @param rank          1-based position in final results
 * @param bm25Score     raw BM25 score (null if document was not retrieved by BM25)
 * @param semanticScore raw cosine similarity score (null if document was not retrieved by semantic engine)
 * @param bm25Rank      1-based rank in BM25 results (null if not in BM25 candidate list)
 * @param semanticRank  1-based rank in semantic results (null if not in semantic candidate list)
 * @param rrfScore      reciprocal rank fusion score (null for pure lexical/semantic mode)
 * @param matchedTerms  lexical terms matched in document (empty list if semantic only)
 * @param termScores    individual BM25 term scores for explainability (empty map if none)
 */
public record HybridCandidate(
        long docId,
        double score,
        int rank,
        Double bm25Score,
        Double semanticScore,
        Integer bm25Rank,
        Integer semanticRank,
        Double rrfScore,
        List<String> matchedTerms,
        Map<String, Double> termScores
) {
    public HybridCandidate {
        matchedTerms = matchedTerms != null ? List.copyOf(matchedTerms) : List.of();
        termScores = termScores != null ? Map.copyOf(termScores) : Map.of();
    }
}
