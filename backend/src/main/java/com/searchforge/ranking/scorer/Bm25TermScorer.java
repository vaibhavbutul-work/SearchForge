package com.searchforge.ranking.scorer;

import com.searchforge.ranking.config.Bm25Config;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Mathematical scoring engine for individual query term contributions under the BM25 model.
 *
 * Implements:
 * Score(q, D) = IDF(q) * [ tf(q, D) * (k1 + 1) ] / [ tf(q, D) + k1 * (1 - b + b * (|D| / avgdl)) ]
 *
 * Thread-safe and stateless with respect to documents and queries.
 */
@Component
public class Bm25TermScorer {

    private final Bm25Config config;

    public Bm25TermScorer(Bm25Config config) {
        this.config = Objects.requireNonNull(config, "Bm25Config cannot be null");
    }

    /**
     * Calculates the saturation component S(tf, D) of the BM25 formula.
     *
     * S(tf, D) = [ tf * (k1 + 1) ] / [ tf + k1 * (1 - b + b * (|D| / avgdl)) ]
     *
     * @param tf           Raw frequency of the term in the document (tf >= 0).
     * @param docLength    Total length of document D in tokens.
     * @param avgDocLength Average document length across the indexed corpus.
     * @return Asymptotic term frequency saturation factor S(tf).
     */
    public double calculateSaturation(int tf, int docLength, double avgDocLength) {
        if (tf <= 0) {
            return 0.0;
        }

        double k1 = config.k1();
        double b = config.b();

        // Calculate length normalization factor B
        double lengthFactorB;
        if (avgDocLength <= 0.0 || docLength <= 0) {
            // Guard against division by zero or uninitialized corpus statistics
            lengthFactorB = 1.0;
        } else {
            lengthFactorB = 1.0 - b + b * ((double) docLength / avgDocLength);
        }

        double numerator = tf * (k1 + 1.0);
        double denominator = tf + k1 * lengthFactorB;

        if (denominator <= 0.0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    /**
     * Calculates the complete BM25 score contribution for a single term.
     *
     * @param tf           Raw frequency of the term in the document.
     * @param docLength    Total length of document D in tokens.
     * @param avgDocLength Average document length across the indexed corpus.
     * @param idf          Precomputed non-negative Inverse Document Frequency weight.
     * @return Term score contribution (>= 0.0).
     */
    public double scoreTerm(int tf, int docLength, double avgDocLength, double idf) {
        if (tf <= 0 || idf <= 0.0) {
            return 0.0;
        }
        double saturation = calculateSaturation(tf, docLength, avgDocLength);
        return saturation * idf;
    }

    public Bm25Config getConfig() {
        return config;
    }
}
