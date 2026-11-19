package com.searchforge.ranking.weight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Bm25IdfCalculatorTest {

    private Bm25IdfCalculator idfCalculator;

    @BeforeEach
    void setUp() {
        idfCalculator = new Bm25IdfCalculator();
    }

    @Test
    @DisplayName("Should calculate exact hand-calculated BM25 IDF values for N=4")
    void shouldCalculateExactBm25IdfValues() {
        long N = 4;

        // df = 1: ln(1 + (4 - 1 + 0.5) / (1 + 0.5)) = ln(1 + 3.5 / 1.5) = ln(10/3) = 1.20397
        double idf1 = idfCalculator.calculateIdf(1, N);
        assertEquals(1.20397, idf1, 1e-4);

        // df = 2: ln(1 + (4 - 2 + 0.5) / (2 + 0.5)) = ln(1 + 2.5 / 2.5) = ln(2.0) = 0.69315
        double idf2 = idfCalculator.calculateIdf(2, N);
        assertEquals(0.69315, idf2, 1e-4);

        // df = 3: ln(1 + (4 - 3 + 0.5) / (3 + 0.5)) = ln(1 + 1.5 / 3.5) = ln(10/7) = 0.35667
        double idf3 = idfCalculator.calculateIdf(3, N);
        assertEquals(0.35667, idf3, 1e-4);

        // df = 4 (term in all documents): ln(1 + 0.5 / 4.5) = ln(10/9) = 0.10536
        double idf4 = idfCalculator.calculateIdf(4, N);
        assertEquals(0.10536, idf4, 1e-4);
    }

    @Test
    @DisplayName("Should strictly decrease as document frequency increases (monotonicity)")
    void shouldBeMonotonicallyDecreasing() {
        long N = 100;
        double previousIdf = Double.MAX_VALUE;

        for (long df = 1; df <= N; df++) {
            double currentIdf = idfCalculator.calculateIdf(df, N);
            assertTrue(currentIdf < previousIdf, "IDF for df=" + df + " should be less than for df=" + (df - 1));
            assertTrue(currentIdf > 0.0, "BM25 IDF should remain strictly positive even when df=N");
            previousIdf = currentIdf;
        }
    }

    @Test
    @DisplayName("Should handle edge cases gracefully without negative values or exceptions")
    void shouldHandleEdgeCases() {
        assertEquals(0.0, idfCalculator.calculateIdf(0, 10));
        assertEquals(0.0, idfCalculator.calculateIdf(-1, 10));
        assertEquals(0.0, idfCalculator.calculateIdf(5, 0));
        assertEquals(0.0, idfCalculator.calculateIdf(5, -10));

        // df > N clamped to N
        double idfClamped = idfCalculator.calculateIdf(15, 10);
        double idfMax = idfCalculator.calculateIdf(10, 10);
        assertEquals(idfMax, idfClamped, 1e-9);
    }
}
