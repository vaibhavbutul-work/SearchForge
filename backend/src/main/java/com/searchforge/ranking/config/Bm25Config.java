package com.searchforge.ranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the BM25 relevance scoring engine.
 * Maps to the "ranking.bm25.*" hierarchy in application.yml.
 *
 * Implemented as a Java 21 Record for immutability and thread safety.
 *
 * @param k1 Controls term frequency saturation. Typically between 1.2 and 2.0 (default: 1.2).
 *           k1 = 0 yields binary occurrence matching (frequency ignored).
 * @param b  Controls document length normalization penalty. Range [0.0, 1.0] (default: 0.75).
 *           b = 0 ignores document length; b = 1 applies 100% proportional length normalization.
 */
@ConfigurationProperties(prefix = "ranking.bm25")
public record Bm25Config(
        Double k1,
        Double b
) {
    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    public Bm25Config {
        if (k1 == null) {
            k1 = DEFAULT_K1;
        }
        if (b == null) {
            b = DEFAULT_B;
        }
        if (k1 < 0.0) {
            throw new IllegalArgumentException("BM25 parameter k1 must be non-negative: " + k1);
        }
        if (b < 0.0 || b > 1.0) {
            throw new IllegalArgumentException("BM25 parameter b must be within [0.0, 1.0]: " + b);
        }
    }

    public static Bm25Config defaultConfig() {
        return new Bm25Config(DEFAULT_K1, DEFAULT_B);
    }
}
