package com.searchforge.ranking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * REST API response record for an individual ranked search hit.
 *
 * @param docId              Unique identifier of the matching document.
 * @param url                Original web URL of the crawled page.
 * @param title              Page title (fallback to URL if missing).
 * @param domain             Domain name of the crawled page.
 * @param snippet            Extracted plain text summary snippet.
 * @param highlightedSnippet Extracted summary snippet with <mark> tags around matched terms.
 * @param score              Computed BM25 relevance score rounded to 4 decimal places.
 * @param rank               1-based position in the search results (1 = most relevant).
 * @param matchedTerms       List of distinct query terms matched in this document.
 * @param explanation        Detailed explainability map (only present when explain=true).
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
        Map<String, TermExplanation> explanation
) {
    public RankedSearchHitResponse {
        matchedTerms = matchedTerms != null ? List.copyOf(matchedTerms) : List.of();
        explanation = explanation != null ? Map.copyOf(explanation) : null;
    }

    /**
     * Backwards-compatible constructor for hits without a separate highlighted snippet.
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
        this(docId, url, title, domain, snippet, snippet, score, rank, matchedTerms, explanation);
    }
}
