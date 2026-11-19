package com.searchforge.ranking.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Bm25ConfigTest {

    @Test
    @DisplayName("Should use sensible defaults when parameters are null")
    void shouldUseDefaultsWhenNull() {
        Bm25Config config = new Bm25Config(null, null);

        assertEquals(1.2, config.k1());
        assertEquals(0.75, config.b());
    }

    @Test
    @DisplayName("Should validate k1 is non-negative")
    void shouldValidateK1NonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Bm25Config(-0.1, 0.75));
    }

    @Test
    @DisplayName("Should validate b is within [0.0, 1.0]")
    void shouldValidateBRange() {
        assertThrows(IllegalArgumentException.class, () -> new Bm25Config(1.2, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Config(1.2, 1.01));
    }

    @Test
    @DisplayName("Should create default config using factory")
    void shouldCreateDefaultConfig() {
        Bm25Config config = Bm25Config.defaultConfig();
        assertEquals(1.2, config.k1());
        assertEquals(0.75, config.b());
    }
}
