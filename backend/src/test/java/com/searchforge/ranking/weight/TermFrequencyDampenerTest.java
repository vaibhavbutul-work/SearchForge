package com.searchforge.ranking.weight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TermFrequencyDampenerTest {

    @Test
    @DisplayName("Should return 0.0 for zero and negative term frequencies")
    void shouldReturnZeroForNonPositiveFrequencies() {
        assertEquals(0.0, TermFrequencyDampener.logDampen(0));
        assertEquals(0.0, TermFrequencyDampener.logDampen(-1));
        assertEquals(0.0, TermFrequencyDampener.logDampen(-100));
    }

    @Test
    @DisplayName("Should return 1.0 for term frequency of 1")
    void shouldReturnOneForUnitFrequency() {
        // 1 + ln(1) = 1 + 0 = 1.0
        assertEquals(1.0, TermFrequencyDampener.logDampen(1), 1e-6);
    }

    @Test
    @DisplayName("Should calculate exact hand-calculated logarithmic dampening values")
    void shouldCalculateExactDampenedValues() {
        // tf = 2: 1 + ln(2) = 1 + 0.693147 = 1.69315
        assertEquals(1.69315, TermFrequencyDampener.logDampen(2), 1e-4);

        // tf = 3: 1 + ln(3) = 1 + 1.098612 = 2.09861
        assertEquals(2.09861, TermFrequencyDampener.logDampen(3), 1e-4);

        // tf = 10: 1 + ln(10) = 1 + 2.302585 = 3.30259
        assertEquals(3.30259, TermFrequencyDampener.logDampen(10), 1e-4);

        // tf = 100: 1 + ln(100) = 1 + 4.605170 = 5.60517
        assertEquals(5.60517, TermFrequencyDampener.logDampen(100), 1e-4);
    }

    @Test
    @DisplayName("Should demonstrate sublinear growth to prevent keyword stuffing")
    void shouldDemonstrateSublinearGrowth() {
        double tf1 = TermFrequencyDampener.logDampen(1);
        double tf10 = TermFrequencyDampener.logDampen(10);
        double tf100 = TermFrequencyDampener.logDampen(100);

        // 10 occurrences should be worth significantly less than 10x single occurrence
        assertTrue(tf10 < 10.0 * tf1, "10 occurrences should not scale linearly");
        assertEquals(3.30259, tf10, 1e-4);

        // 100 occurrences gives only ~5.6, not 100
        assertTrue(tf100 < 100.0 * tf1);
    }
}
