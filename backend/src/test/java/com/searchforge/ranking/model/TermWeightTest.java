package com.searchforge.ranking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TermWeightTest {

    @Test
    @DisplayName("Should create TermWeight using of() factory and compute totalWeight")
    void shouldCreateTermWeightWithFactory() {
        TermWeight tw = TermWeight.of("java", 3, 2.09861, 1.20397);

        assertEquals("java", tw.term());
        assertEquals(3, tw.rawTf());
        assertEquals(2.09861, tw.normalizedTf(), 1e-5);
        assertEquals(1.20397, tw.idf(), 1e-5);
        assertEquals(2.09861 * 1.20397, tw.totalWeight(), 1e-5);
    }

    @Test
    @DisplayName("Should validate bounds and reject invalid parameters")
    void shouldValidateInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new TermWeight(null, 1, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TermWeight("  ", 1, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TermWeight("java", -1, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TermWeight("java", 1, -0.5, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TermWeight("java", 1, 1.0, -0.5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TermWeight("java", 1, 1.0, 1.0, -1.0));
    }
}
