package com.searchforge.ranking.weight;

/**
 * Utility for applying sublinear logarithmic dampening to raw term frequencies.
 *
 * Implements:
 * TF_LOG(tf) = 1 + ln(tf) for tf > 0, and 0 for tf <= 0.
 *
 * This sublinear transformation prevents a document that mentions a term 20 times
 * from being deemed twenty times more relevant than a document that mentions it once.
 * (Note: Used primarily in Vector Space Models and baseline TF-IDF weighting;
 * BM25 uses its own hyperbolic saturation curve).
 */
public final class TermFrequencyDampener {

    private TermFrequencyDampener() {
        // Utility class - non-instantiable
    }

    /**
     * Calculates the logarithmically dampened term frequency.
     *
     * @param rawTf Raw occurrence count of the term in the document.
     * @return Sublinearly dampened term frequency (0.0 if rawTf <= 0, 1.0 + ln(rawTf) otherwise).
     */
    public static double logDampen(int rawTf) {
        if (rawTf <= 0) {
            return 0.0;
        }
        return 1.0 + Math.log(rawTf);
    }
}
