package com.searchforge.search.hybrid;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.retrieval.BooleanQueryParser;
import com.searchforge.indexer.retrieval.DefaultBooleanRetrievalEngine;
import com.searchforge.indexer.search.PositionalIndexSearcher;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.retrieval.*;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Boolean and Phrase Search Correctness Regression Tests (Phase 7.1 Task 8)")
class BooleanAndPhraseCorrectnessRegressionTest {

    private InMemoryInvertedIndex index;
    private Tokenizer lexicalTokenizer;
    private RankedRetrievalEngine rankedRetrievalEngine;
    private RankedBooleanQueryEngine rankedBooleanQueryEngine;
    private DefaultHybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        lexicalTokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        PositionalIndexSearcher positionalSearcher = new PositionalIndexSearcher(index, lexicalTokenizer);
        BooleanQueryParser parser = new BooleanQueryParser();
        DefaultBooleanRetrievalEngine booleanEngine = new DefaultBooleanRetrievalEngine(index, positionalSearcher, lexicalTokenizer);

        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        rankedRetrievalEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, lexicalTokenizer);
        rankedBooleanQueryEngine = new DefaultRankedBooleanQueryEngine(
                parser,
                booleanEngine,
                index,
                bm25Scorer,
                lexicalTokenizer,
                positionalSearcher
        );

        DeterministicEmbeddingModel embeddingModel = new DeterministicEmbeddingModel(384, 256);
        InMemoryVectorStorage vectorStorage = new InMemoryVectorStorage(384);
        ExactVectorSimilarityEngine vectorEngine = new ExactVectorSimilarityEngine(vectorStorage);
        ReciprocalRankFusionEngine rrfEngine = new ReciprocalRankFusionEngine(60);

        hybridSearchService = new DefaultHybridSearchService(
                rankedRetrievalEngine,
                embeddingModel,
                vectorEngine,
                rrfEngine
        );

        // Seed 4 controlled documents:
        // Doc 1: "virtual threads lightweight concurrency"
        indexDocument(1L, "virtual threads lightweight concurrency");
        // Doc 2: "legacy threads heavy concurrency"
        indexDocument(2L, "legacy threads heavy concurrency");
        // Doc 3: "spring framework microservices architecture"
        indexDocument(3L, "spring framework microservices architecture");
        // Doc 4: "microservices and legacy architecture"
        indexDocument(4L, "microservices and legacy architecture");
    }

    private void indexDocument(long docId, String text) {
        index.addDocument(docId, lexicalTokenizer.tokenize(text));
    }

    @Test
    @DisplayName("Strict Boolean NOT: Excluded document must never appear in Boolean ranked results")
    void booleanNot_mustStrictlyExcludeNegatedDocument() {
        // Query: "threads AND NOT legacy"
        // Eligible: Doc 1 (virtual threads)
        // Disqualified: Doc 2 (legacy threads)
        List<RankedSearchResult> results = rankedBooleanQueryEngine.searchRankedBoolean("threads AND NOT legacy", 10);

        assertFalse(results.isEmpty());
        List<Long> docIds = results.stream().map(RankedSearchResult::docId).toList();

        assertTrue(docIds.contains(1L), "Doc 1 must be present");
        assertFalse(docIds.contains(2L), "Doc 2 must be strictly excluded by NOT legacy");
        assertFalse(docIds.contains(4L), "Doc 4 must be strictly excluded by NOT legacy");
    }

    @Test
    @DisplayName("Strict Boolean AND: Only documents satisfying all conjuncts are returned")
    void booleanAnd_mustStrictlyIntersectCandidates() {
        // Query: "virtual AND concurrency" -> Only Doc 1 contains both
        List<RankedSearchResult> results = rankedBooleanQueryEngine.searchRankedBoolean("virtual AND concurrency", 10);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).docId());
    }

    @Test
    @DisplayName("Strict Boolean Filter: Free-text search bounded strictly to filter candidate set")
    void filteredSearch_strictlyBindsCandidateUniverse() {
        // Search "architecture" filtered strictly by "NOT legacy"
        // Doc 3 (spring framework microservices architecture) passes
        // Doc 4 (microservices and legacy architecture) fails filter
        List<RankedSearchResult> results = rankedBooleanQueryEngine.searchFiltered("architecture", "NOT legacy", 10);

        assertFalse(results.isEmpty());
        List<Long> docIds = results.stream().map(RankedSearchResult::docId).toList();

        assertTrue(docIds.contains(3L), "Doc 3 must pass the filter");
        assertFalse(docIds.contains(4L), "Doc 4 must be rejected because it contains 'legacy'");
    }

    @Test
    @DisplayName("Exact Phrase Search: Only adjacent positional occurrences match")
    void phraseSearch_enforcesExactPositionalAdjacency() {
        // Doc 3 has "spring framework" adjacent
        List<RankedSearchResult> hits = rankedRetrievalEngine.search("\"spring framework\"", 10);

        assertFalse(hits.isEmpty());
        assertEquals(3L, hits.get(0).docId());
    }

    @Test
    @DisplayName("Lexical mode in HybridSearchService executes BM25 without semantic contamination")
    void hybridSearchService_lexicalModePreservesBm25Ranking() {
        HybridSearchResult result = hybridSearchService.search("threads concurrency", SearchMode.LEXICAL, 10);

        assertEquals(SearchMode.LEXICAL, result.effectiveMode());
        assertFalse(result.candidates().isEmpty());
        List<Long> docIds = result.candidates().stream().map(HybridCandidate::docId).toList();
        assertTrue(docIds.contains(1L));
        assertTrue(docIds.contains(2L));
        assertNull(result.candidates().get(0).semanticScore(), "Semantic score must be null in pure lexical mode");
    }
}
