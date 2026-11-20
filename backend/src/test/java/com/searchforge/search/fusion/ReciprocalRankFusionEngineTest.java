package com.searchforge.search.fusion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReciprocalRankFusionEngine Unit Tests")
class ReciprocalRankFusionEngineTest {

    private static final double EPSILON = 1e-6;
    private ReciprocalRankFusionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReciprocalRankFusionEngine(60);
    }

    @Test
    @DisplayName("Should calculate exact RRF scores for documents appearing in both rankings")
    void testDualRankingExactMath() {
        // Doc 10: Rank 1 in BM25, Rank 2 in Semantic
        List<RankedItem> bm25 = List.of(
                new RankedItem(10L, 1, 8.5, "bm25"),
                new RankedItem(20L, 2, 6.2, "bm25")
        );
        List<RankedItem> semantic = List.of(
                new RankedItem(30L, 1, 0.95, "semantic"),
                new RankedItem(10L, 2, 0.88, "semantic")
        );

        Map<String, List<RankedItem>> inputs = Map.of("bm25", bm25, "semantic", semantic);
        List<FusedDocumentResult> results = engine.fuse(inputs, 5);

        assertEquals(3, results.size());

        // Expected math for Doc 10: 1/(60+1) + 1/(60+2) = 1/61 + 1/62 = 0.01639344 + 0.01612903 = 0.03252247
        FusedDocumentResult topHit = results.get(0);
        assertEquals(10L, topHit.documentId());
        assertEquals(1, topHit.finalRank());
        double expectedScore = (1.0 / 61.0) + (1.0 / 62.0);
        assertEquals(expectedScore, topHit.rrfScore(), EPSILON);

        // Verify explainability maps
        assertEquals(1, topHit.sourceRanks().get("bm25"));
        assertEquals(2, topHit.sourceRanks().get("semantic"));
        assertEquals(8.5, topHit.sourceScores().get("bm25"));
        assertEquals(0.88, topHit.sourceScores().get("semantic"));
        assertEquals(1.0 / 61.0, topHit.rrfContributions().get("bm25"), EPSILON);
        assertEquals(1.0 / 62.0, topHit.rrfContributions().get("semantic"), EPSILON);
    }

    @Test
    @DisplayName("Should demonstrate consensus: Document at Rank 3 in both lists beats Rank 1 in only one list")
    void testConsensusAdvantage() {
        // Doc 100 appears at Rank 3 in both BM25 and Semantic
        // Doc 200 appears at Rank 1 in only BM25
        List<RankedItem> bm25 = List.of(
                new RankedItem(200L, 1, 12.0, "bm25"),
                new RankedItem(100L, 3, 7.0, "bm25")
        );
        List<RankedItem> semantic = List.of(
                new RankedItem(300L, 1, 0.91, "semantic"),
                new RankedItem(100L, 3, 0.82, "semantic")
        );

        List<FusedDocumentResult> results = engine.fuse(Map.of("bm25", bm25, "semantic", semantic), 5);

        // Doc 100 score: 1/(60+3) + 1/(60+3) = 2/63 = 0.031746
        // Doc 200 score: 1/(60+1) = 1/61 = 0.016393
        // Doc 300 score: 1/(60+1) = 1/61 = 0.016393
        assertEquals(100L, results.get(0).documentId(), "Consensus document must rank #1");
        assertEquals(0.031746, results.get(0).rrfScore(), 1e-4);
    }

    @Test
    @DisplayName("Should perform deterministic tie-breaking on identical RRF scores by ascending document ID")
    void testDeterministicTieBreaking() {
        // Doc 20 and Doc 10 both have identical Rank 1 in disjoint lists
        List<RankedItem> listA = List.of(new RankedItem(20L, 1, 5.0, "sourceA"));
        List<RankedItem> listB = List.of(new RankedItem(10L, 1, 0.9, "sourceB"));

        List<FusedDocumentResult> results = engine.fuse(Map.of("A", listA, "B", listB), 5);

        assertEquals(2, results.size());
        assertEquals(1.0 / 61.0, results.get(0).rrfScore(), EPSILON);
        assertEquals(1.0 / 61.0, results.get(1).rrfScore(), EPSILON);

        // Doc 10 must rank first due to lower document ID (10 < 20)
        assertEquals(10L, results.get(0).documentId());
        assertEquals(1, results.get(0).finalRank());
        assertEquals(20L, results.get(1).documentId());
        assertEquals(2, results.get(1).finalRank());
    }

    @Test
    @DisplayName("Should prevent score inflation if a document appears twice in the same ranking list")
    void testDuplicateItemPrevention() {
        List<RankedItem> duplicates = List.of(
                new RankedItem(5L, 1, 10.0, "bm25"),
                new RankedItem(5L, 2, 8.0, "bm25") // Duplicate doc ID in same list
        );

        List<FusedDocumentResult> results = engine.fuse(Map.of("bm25", duplicates), 5);
        assertEquals(1, results.size());
        assertEquals(1.0 / 61.0, results.get(0).rrfScore(), EPSILON);
    }

    @Test
    @DisplayName("Should return empty list on null, empty, or all-empty inputs")
    void testEmptyInputs() {
        assertTrue(engine.fuse(null, 5).isEmpty());
        assertTrue(engine.fuse(Collections.emptyMap(), 5).isEmpty());
        assertTrue(engine.fuse(Map.of("bm25", List.of()), 5).isEmpty());
    }

    @Test
    @DisplayName("Should handle fusion when one list is populated and other is empty")
    void testPartialEmptyLists() {
        List<RankedItem> bm25 = List.of(new RankedItem(1L, 1, 5.0, "bm25"));
        List<FusedDocumentResult> results = engine.fuse(Map.of("bm25", bm25, "semantic", List.of()), 5);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).documentId());
        assertEquals(1.0 / 61.0, results.get(0).rrfScore(), EPSILON);
    }

    @Test
    @DisplayName("Should truncate results to requested Top-K limit")
    void testTopKTruncation() {
        List<RankedItem> items = List.of(
                new RankedItem(1L, 1, 10.0, "src"),
                new RankedItem(2L, 2, 9.0, "src"),
                new RankedItem(3L, 3, 8.0, "src"),
                new RankedItem(4L, 4, 7.0, "src"),
                new RankedItem(5L, 5, 6.0, "src")
        );

        List<FusedDocumentResult> results = engine.fuse(Map.of("src", items), 2);
        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).documentId());
        assertEquals(2L, results.get(1).documentId());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on non-positive k or topK")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusionEngine(0));
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusionEngine(-10));
        assertThrows(IllegalArgumentException.class, () -> engine.fuse(Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> engine.fuse(Map.of(), -1));
    }
}
