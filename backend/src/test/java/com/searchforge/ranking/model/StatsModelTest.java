package com.searchforge.ranking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatsModelTest {

    @Test
    @DisplayName("Should enforce non-negative constraints on DocumentStats")
    void shouldValidateDocumentStats() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentStats(0, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new DocumentStats(-1, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new DocumentStats(1, -5, Map.of()));

        // Defensive copy check
        Map<String, Integer> mutableTf = new HashMap<>();
        mutableTf.put("java", 2);
        DocumentStats stats = new DocumentStats(1, 10, mutableTf);

        mutableTf.put("spring", 3);
        assertEquals(2, stats.getTermFrequency("java"));
        assertEquals(0, stats.getTermFrequency("spring"), "DocumentStats should make an immutable defensive copy");
        assertEquals(0, stats.getTermFrequency(null));
    }

    @Test
    @DisplayName("Should enforce non-negative constraints on CollectionStats")
    void shouldValidateCollectionStats() {
        assertThrows(IllegalArgumentException.class, () -> new CollectionStats(-1, 10.0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CollectionStats(10, -1.0, Map.of()));

        Map<String, Long> mutableDf = new HashMap<>();
        mutableDf.put("java", 5L);
        CollectionStats stats = new CollectionStats(10, 15.5, mutableDf);

        mutableDf.put("spring", 2L);
        assertEquals(5L, stats.getDocumentFrequency("java"));
        assertEquals(0L, stats.getDocumentFrequency("spring"), "CollectionStats should make an immutable defensive copy");
        assertEquals(0L, stats.getDocumentFrequency(null));
    }

    @Test
    @DisplayName("Should enforce bounds on ScoredDocument")
    void shouldValidateScoredDocument() {
        assertThrows(IllegalArgumentException.class, () -> new ScoredDocument(0, 1.0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ScoredDocument(1, -0.1, Map.of()));

        ScoredDocument doc = new ScoredDocument(1, 2.5, Map.of("java", 2.5));
        assertEquals(1, doc.docId());
        assertEquals(2.5, doc.score());
        assertEquals(2.5, doc.termScores().get("java"));
    }
}
