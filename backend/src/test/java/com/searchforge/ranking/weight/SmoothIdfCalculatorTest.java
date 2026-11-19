package com.searchforge.ranking.weight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothIdfCalculatorTest {

    private SmoothIdfCalculator idfCalculator;

    @BeforeEach
    void setUp() {
        idfCalculator = new SmoothIdfCalculator();
    }

    @Test
    @DisplayName("Should calculate exact hand-calculated Smoothed VSM IDF values for N=4")
    void shouldCalculateExactSmoothIdfValues() {
        long N = 4;

        // df = 1: ln(1 + 4 / 1) = ln(5.0) = 1.60944
        double idf1 = idfCalculator.calculateIdf(1, N);
        assertEquals(1.60944, idf1, 1e-4);

        // df = 2: ln(1 + 4 / 2) = ln(3.0) = 1.09861
        double idf2 = idfCalculator.calculateIdf(2, N);
        assertEquals(1.09861, idf2, 1e-4);

        // df = 3: ln(1 + 4 / 3) = ln(7/3) = 0.84730
        double idf3 = idfCalculator.calculateIdf(3, N);
        assertEquals(0.84730, idf3, 1e-4);

        // df = 4: ln(1 + 4 / 4) = ln(2.0) = 0.69315
        double idf4 = idfCalculator.calculateIdf(4, N);
        assertEquals(0.69315, idf4, 1e-4);
    }

    @Test
    @DisplayName("Should strictly decrease as document frequency increases (monotonicity)")
    void shouldBeMonotonicallyDecreasing() {
        long N = 50;
        double previousIdf = Double.MAX_VALUE;

        for (long df = 1; df <= N; df++) {
            double currentIdf = idfCalculator.calculateIdf(df, N);
            assertTrue(currentIdf < previousIdf, "Smoothed IDF for df=" + df + " should be less than for df=" + (df - 1));
            assertTrue(currentIdf > 0.0, "Smoothed IDF should remain strictly positive");
            previousIdf = currentIdf;
        }
    }

    @Test
    @DisplayName("Should handle edge cases gracefully")
    void shouldHandleEdgeCases() {
        assertEquals(0.0, idfCalculator.calculateIdf(0, 10));
        assertEquals(0.0, idfCalculator.calculateIdf(-1, 10));
        assertEquals(0.0, idfCalculator.calculateIdf(5, 0));
        assertEquals(0.0, idfCalculator.calculateIdf(5, -10));

        // df > N clamped to N
        double idfClamped = idfCalculator.calculateIdf(20, 10);
        double idfMax = idfCalculator.calculateIdf(10, 10);
        assertEquals(idfMax, idfClamped, 1e-9);
    }
}
