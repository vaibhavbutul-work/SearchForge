package com.searchforge.ranking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * REST API response record for an individual ranked, semantic, or hybrid search hit.
 *
 * @param docId              Unique identifier of the matching document.
 * @param url                Original web URL of the crawled page.
 * @param title              Page title (fallback to URL if missing).
 * @param domain             Domain name of the crawled page.
 * @param snippet            Extracted plain text summary snippet.
 * @param highlightedSnippet Extracted summary snippet with <mark> tags around matched terms.
 * @param score              Effective ranking score (BM25 score, cosine similarity, or fused RRF score).
 * @param rank               1-based position in final search results (1 = most relevant).
 * @param matchedTerms       List of distinct query terms matched in this document.
 * @param explanation        Detailed explainability map (only present when explain=true).
 * @param bm25Score          Original BM25 score (present in lexical and hybrid modes when matched).
 * @param semanticScore      Original dense semantic cosine similarity score (present in semantic and hybrid modes when matched).
 * @param bm25Rank           Original 1-based rank position in BM25 retrieval (present in hybrid mode).
 * @param semanticRank       Original 1-based rank position in semantic retrieval (present in hybrid mode).
 * @param rrfScore           Reciprocal Rank Fusion score (present in hybrid mode).
 */
public record RankedSearchHitResponse(
        long docId,
        String url,
        String title,
        String domain,
        String snippet,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String highlightedSnippet,
        double score,
        int rank,
        List<String> matchedTerms,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, TermExplanation> explanation,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double bm25Score,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double semanticScore,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer bm25Rank,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer semanticRank,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double rrfScore
) {
    public RankedSearchHitResponse {
        matchedTerms = matchedTerms != null ? List.copyOf(matchedTerms) : List.of();
        explanation = explanation != null ? Map.copyOf(explanation) : null;
    }

    /**
     * Alias getter providing "documentId" in JSON output alongside "docId" for API consistency.
     */
    @JsonProperty("documentId")
    public Long getDocumentId() {
        return docId;
    }

    /**
     * Alias getter providing "finalRank" in JSON output alongside "rank" for API consistency.
     */
    @JsonProperty("finalRank")
    public Integer getFinalRank() {
        return rank;
    }

    /**
     * Backwards-compatible 10-parameter constructor (lexical search without hybrid ranking fields).
     */
    public RankedSearchHitResponse(
            long docId,
            String url,
            String title,
            String domain,
            String snippet,
            String highlightedSnippet,
            double score,
            int rank,
            List<String> matchedTerms,
            Map<String, TermExplanation> explanation
    ) {
        this(docId, url, title, domain, snippet, highlightedSnippet, score, rank, matchedTerms, explanation,
                null, null, null, null, null);
    }

    /**
     * Backwards-compatible 9-parameter constructor.
     */
    public RankedSearchHitResponse(
            long docId,
            String url,
            String title,
            String domain,
            String snippet,
            double score,
            int rank,
            List<String> matchedTerms,
            Map<String, TermExplanation> explanation
    ) {
        this(docId, url, title, domain, snippet, snippet, score, rank, matchedTerms, explanation,
                null, null, null, null, null);
    }
}
