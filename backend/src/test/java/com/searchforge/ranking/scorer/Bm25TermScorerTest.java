package com.searchforge.ranking.scorer;

import com.searchforge.ranking.config.Bm25Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Bm25TermScorerTest {

    private Bm25TermScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new Bm25TermScorer(Bm25Config.defaultConfig());
    }

    @Test
    @DisplayName("Should match exact hand-calculated BM25 scores for sample corpus")
    void shouldMatchExactHandCalculatedScores() {
        // Sample corpus:
        // D1: length 6, tf=2
        // D2: length 3, tf=1
        // avgdl = 14 / 3.0
        // idf = ln(1 + 1.5 / 2.5) = ln(1.6) = 0.4700036
        double avgdl = 14.0 / 3.0;
        double idf = Math.log(1.6); // 0.4700036

        // D1 calculation
        double d1JavaScore = scorer.scoreTerm(2, 6, avgdl, idf);
        assertEquals(0.598186, d1JavaScore, 1e-4);

        // D2 calculation
        double d2JavaScore = scorer.scoreTerm(1, 3, avgdl, idf);
        assertEquals(0.550423, d2JavaScore, 1e-4);

        // Combined score for query "java spring" (sum of 2 matching terms)
        double d1Total = d1JavaScore * 2.0;
        double d2Total = d2JavaScore * 2.0;

        assertEquals(1.19637, d1Total, 1e-4);
        assertEquals(1.10085, d2Total, 1e-4);

        // Even with double the TF (2 vs 1), D1 is only slightly higher than D2 due to length penalty and saturation
        assertTrue(d1Total > d2Total);
        assertTrue(d1Total < 2.0 * d2Total, "D1 should not have double the score of D2");
    }

    @Test
    @DisplayName("Should saturate asymptotically towards (k1 + 1) as term frequency increases")
    void shouldSaturateAsymptotically() {
        double avgdl = 100.0;
        int docLength = 100; // |D| == avgdl => B = 1.0

        // S(1) with k1=1.2, B=1.0 is 1.0
        double s1 = scorer.calculateSaturation(1, docLength, avgdl);
        assertEquals(1.0, s1, 1e-6);

        // S(2) = 4.4 / 3.2 = 1.375
        double s2 = scorer.calculateSaturation(2, docLength, avgdl);
        assertEquals(1.375, s2, 1e-6);

        // S(10) = 22.0 / 11.2 = 1.964286
        double s10 = scorer.calculateSaturation(10, docLength, avgdl);
        assertEquals(1.964286, s10, 1e-4);

        // S(1000) approaches k1 + 1 = 2.2
        double s1000 = scorer.calculateSaturation(1000, docLength, avgdl);
        assertTrue(s1000 < 2.2, "Saturation should never exceed k1 + 1");
        assertEquals(2.2, s1000, 0.01);
    }

    @Test
    @DisplayName("Should respect length penalty parameter b")
    void shouldRespectLengthPenaltyB() {
        // When b = 0, document length has no effect
        Bm25Config configNoLengthPenalty = new Bm25Config(1.2, 0.0);
        Bm25TermScorer scorerNoPenalty = new Bm25TermScorer(configNoLengthPenalty);

        double sShort = scorerNoPenalty.calculateSaturation(3, 10, 100.0);
        double sLong = scorerNoPenalty.calculateSaturation(3, 1000, 100.0);
        assertEquals(sShort, sLong, 1e-9, "When b=0, document length should not affect saturation");

        // When b = 0.75 (standard), longer document receives lower saturation
        double sStandardShort = scorer.calculateSaturation(3, 10, 100.0);
        double sStandardLong = scorer.calculateSaturation(3, 1000, 100.0);
        assertTrue(sStandardShort > sStandardLong, "Shorter document should score higher for same TF");
    }

    @Test
    @DisplayName("Should behave as binary presence matching when k1 = 0")
    void shouldActAsBinaryMatcherWhenK1IsZero() {
        Bm25Config binaryConfig = new Bm25Config(0.0, 0.75);
        Bm25TermScorer binaryScorer = new Bm25TermScorer(binaryConfig);

        double s1 = binaryScorer.calculateSaturation(1, 50, 50.0);
        double s50 = binaryScorer.calculateSaturation(50, 50, 50.0);

        assertEquals(1.0, s1, 1e-6);
        assertEquals(1.0, s50, 1e-6, "When k1=0, saturation is 1.0 regardless of frequency");
    }

    @Test
    @DisplayName("Should handle edge cases safely without division by zero or NaN")
    void shouldHandleEdgeCasesSafely() {
        assertEquals(0.0, scorer.scoreTerm(0, 100, 100.0, 1.5));
        assertEquals(0.0, scorer.scoreTerm(-5, 100, 100.0, 1.5));
        assertEquals(0.0, scorer.scoreTerm(2, 100, 100.0, 0.0));
        assertEquals(0.0, scorer.scoreTerm(2, 100, 100.0, -1.0));

        // When avgDocLength is 0 (empty corpus), fallback to B=1.0
        double scoreZeroAvg = scorer.scoreTerm(1, 50, 0.0, 1.0);
        assertEquals(1.0, scoreZeroAvg, 1e-6);
        assertFalse(Double.isNaN(scoreZeroAvg));
        assertFalse(Double.isInfinite(scoreZeroAvg));

        // When docLength is 0, fallback to B=1.0
        double scoreZeroDocLen = scorer.scoreTerm(1, 0, 50.0, 1.0);
        assertEquals(1.0, scoreZeroDocLen, 1e-6);
        assertFalse(Double.isNaN(scoreZeroDocLen));
    }
}
