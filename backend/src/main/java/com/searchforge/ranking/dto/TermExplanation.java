package com.searchforge.ranking.dto;

/**
 * Detailed explainability breakdown for a single query term's contribution to a document's score.
 *
 * @param rawTf      Raw occurrence count of the term in this document.
 * @param idf        Computed Inverse Document Frequency weight.
 * @param saturation Term frequency saturation factor S(tf).
 * @param termScore  Combined score contribution (termScore = saturation * idf).
 */
public record TermExplanation(
        int rawTf,
        double idf,
        double saturation,
        double termScore
) {}
