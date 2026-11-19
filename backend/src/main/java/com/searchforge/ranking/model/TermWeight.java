package com.searchforge.ranking.model;

/**
 * Immutable record representing the computed weight of a term in a document.
 *
 * @param term         The normalized/stemmed term.
 * @param rawTf        Raw frequency of the term in the document (tf >= 0).
 * @param normalizedTf Transformed/dampened term frequency (e.g. logarithmic or BM25 saturated).
 * @param idf          Inverse document frequency weight.
 * @param totalWeight  Combined product of normalized term frequency and IDF.
 */
public record TermWeight(
        String term,
        int rawTf,
        double normalizedTf,
        double idf,
        double totalWeight
) {
    public TermWeight {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term cannot be null or blank");
        }
        if (rawTf < 0) {
            throw new IllegalArgumentException("Raw TF cannot be negative: " + rawTf);
        }
        if (normalizedTf < 0.0) {
            throw new IllegalArgumentException("Normalized TF cannot be negative: " + normalizedTf);
        }
        if (idf < 0.0) {
            throw new IllegalArgumentException("IDF cannot be negative: " + idf);
        }
        if (totalWeight < 0.0) {
            throw new IllegalArgumentException("Total weight cannot be negative: " + totalWeight);
        }
    }

    public static TermWeight of(String term, int rawTf, double normalizedTf, double idf) {
        return new TermWeight(term, rawTf, normalizedTf, idf, normalizedTf * idf);
    }
}
