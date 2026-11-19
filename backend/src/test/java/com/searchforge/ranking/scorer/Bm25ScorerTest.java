package com.searchforge.ranking.scorer;

import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Bm25ScorerTest {

    private Bm25Scorer scorer;

    @BeforeEach
    void setUp() {
        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        scorer = new Bm25Scorer(termScorer, idfCalculator);
    }

    @Test
    @DisplayName("Should score multi-term query matching exact hand-calculated benchmark")
    void shouldScoreMultiTermQueryExactBenchmark() {
        // N = 3, avgdl = 14 / 3.0
        // df("java") = 2, df("spring") = 2
        CollectionStats collectionStats = new CollectionStats(
                3,
                14.0 / 3.0,
                Map.of("java", 2L, "spring", 2L)
        );

        // D1: length 6, tf("java")=2, tf("spring")=2
        DocumentStats d1 = new DocumentStats(1L, 6, Map.of("java", 2, "spring", 2));
        // D2: length 3, tf("java")=1, tf("spring")=1
        DocumentStats d2 = new DocumentStats(2L, 3, Map.of("java", 1, "spring", 1));
        // D3: length 5, no matching terms
        DocumentStats d3 = new DocumentStats(3L, 5, Map.of("python", 1, "django", 1));

        List<String> queryTerms = List.of("java", "spring");

        ScoredDocument scoredD1 = scorer.scoreDocument(d1, queryTerms, collectionStats);
        ScoredDocument scoredD2 = scorer.scoreDocument(d2, queryTerms, collectionStats);
        ScoredDocument scoredD3 = scorer.scoreDocument(d3, queryTerms, collectionStats);

        // Assert D1 values
        assertEquals(1.19637, scoredD1.score(), 1e-4);
        assertEquals(2, scoredD1.termScores().size());
        assertEquals(0.598186, scoredD1.termScores().get("java"), 1e-4);
        assertEquals(0.598186, scoredD1.termScores().get("spring"), 1e-4);

        // Assert D2 values
        assertEquals(1.10085, scoredD2.score(), 1e-4);
        assertEquals(2, scoredD2.termScores().size());
        assertEquals(0.550423, scoredD2.termScores().get("java"), 1e-4);
        assertEquals(0.550423, scoredD2.termScores().get("spring"), 1e-4);

        // Assert D3 values (zero match)
        assertEquals(0.0, scoredD3.score());
        assertTrue(scoredD3.termScores().isEmpty());

        // Batch scoring naturally sorts D1 > D2 > D3
        List<ScoredDocument> ranked = scorer.scoreDocuments(List.of(d3, d1, d2), queryTerms, collectionStats);
        assertEquals(3, ranked.size());
        assertEquals(1L, ranked.get(0).docId());
        assertEquals(2L, ranked.get(1).docId());
        assertEquals(3L, ranked.get(2).docId());
    }

    @Test
    @DisplayName("Should score single-term query correctly")
    void shouldScoreSingleTermQuery() {
        CollectionStats collectionStats = new CollectionStats(
                3,
                14.0 / 3.0,
                Map.of("java", 2L)
        );

        DocumentStats d1 = new DocumentStats(1L, 6, Map.of("java", 2));
        DocumentStats d2 = new DocumentStats(2L, 3, Map.of("java", 1));

        List<String> queryTerms = List.of("java");

        ScoredDocument scoredD1 = scorer.scoreDocument(d1, queryTerms, collectionStats);
        ScoredDocument scoredD2 = scorer.scoreDocument(d2, queryTerms, collectionStats);

        assertEquals(0.598186, scoredD1.score(), 1e-4);
        assertEquals(0.550423, scoredD2.score(), 1e-4);
        assertEquals(1, scoredD1.termScores().size());
    }

    @Test
    @DisplayName("Should return 0.0 for empty query or null query terms")
    void shouldHandleEmptyQuery() {
        CollectionStats collectionStats = new CollectionStats(10, 50.0, Map.of("java", 5L));
        DocumentStats d1 = new DocumentStats(1L, 50, Map.of("java", 3));

        ScoredDocument emptyListResult = scorer.scoreDocument(d1, List.of(), collectionStats);
        assertEquals(0.0, emptyListResult.score());
        assertTrue(emptyListResult.termScores().isEmpty());

        ScoredDocument nullResult = scorer.scoreDocument(d1, null, collectionStats);
        assertEquals(0.0, nullResult.score());
    }

    @Test
    @DisplayName("Should return 0.0 when query terms are not in the corpus (df = 0)")
    void shouldHandleMissingTerms() {
        CollectionStats collectionStats = new CollectionStats(10, 50.0, Map.of());
        DocumentStats d1 = new DocumentStats(1L, 50, Map.of("rust", 3));

        ScoredDocument result = scorer.scoreDocument(d1, List.of("rust"), collectionStats);
        assertEquals(0.0, result.score());
    }

    @Test
    @DisplayName("Should penalize extremely verbose documents compared to concise ones")
    void shouldPenalizeExtremelyVerboseDocuments() {
        double avgdl = 100.0;
        CollectionStats collectionStats = new CollectionStats(100, avgdl, Map.of("keyword", 10L));

        // Both documents have tf = 5 for "keyword"
        DocumentStats conciseDoc = new DocumentStats(1L, 20, Map.of("keyword", 5));
        DocumentStats verboseDoc = new DocumentStats(2L, 2000, Map.of("keyword", 5));

        ScoredDocument scoredConcise = scorer.scoreDocument(conciseDoc, List.of("keyword"), collectionStats);
        ScoredDocument scoredVerbose = scorer.scoreDocument(verboseDoc, List.of("keyword"), collectionStats);

        assertTrue(scoredConcise.score() > scoredVerbose.score(),
                "Concise document should rank higher than verbose document with identical TF");
    }

    @Test
    @DisplayName("Should break ties deterministically by docId ascending")
    void shouldBreakTiesDeterministically() {
        ScoredDocument docA = new ScoredDocument(5L, 3.5, Map.of());
        ScoredDocument docB = new ScoredDocument(2L, 3.5, Map.of());

        // Since scores are equal (3.5), docB (docId 2) should precede docA (docId 5)
        assertTrue(docB.compareTo(docA) < 0);
        assertTrue(docA.compareTo(docB) > 0);
    }
}
