package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankedRetrievalEngineTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private RankedRetrievalEngine retrievalEngine;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        retrievalEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer);
    }

    private void indexDocument(long docId, String text) {
        index.addDocument(docId, tokenizer.tokenize(text));
    }

    @Test
    @DisplayName("Should retrieve and rank multi-term query matching the Phase 4 benchmark")
    void shouldRetrieveAndRankMultiTermQuery() {
        // Doc 1: "java spring boot microservices java spring" (length 6)
        indexDocument(1L, "java spring boot microservices java spring");
        // Doc 2: "java spring framework" (length 3)
        indexDocument(2L, "java spring framework");
        // Doc 3: "python django flask web framework" (length 5)
        indexDocument(3L, "python django flask web framework");

        List<RankedSearchResult> results = retrievalEngine.search("java spring", 10);

        assertEquals(2, results.size());

        // Doc 1 should be rank 1
        RankedSearchResult rank1 = results.get(0);
        assertEquals(1L, rank1.docId());
        assertEquals(1, rank1.rank());
        assertEquals(1.19637, rank1.score(), 1e-4);
        assertTrue(rank1.matchedTerms().containsAll(List.of("java", "spring")));
        assertEquals(2, rank1.termScores().size());

        // Doc 2 should be rank 2
        RankedSearchResult rank2 = results.get(1);
        assertEquals(2L, rank2.docId());
        assertEquals(2, rank2.rank());
        assertEquals(1.10085, rank2.score(), 1e-4);
        assertTrue(rank2.matchedTerms().containsAll(List.of("java", "spring")));
        assertEquals(2, rank2.termScores().size());

        // Doc 3 does not contain "java" or "spring", so it must not appear
        assertTrue(results.stream().noneMatch(r -> r.docId() == 3L));
    }

    @Test
    @DisplayName("Should handle single-term query correctly")
    void shouldHandleSingleTermQuery() {
        indexDocument(1L, "java java java");
        indexDocument(2L, "java python c++");

        List<RankedSearchResult> results = retrievalEngine.search("java");

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).docId());
        assertEquals(1, results.get(0).rank());
        assertEquals(2L, results.get(1).docId());
        assertEquals(2, results.get(1).rank());
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    @DisplayName("Should transparently filter stop words from queries")
    void shouldFilterStopWordsInQuery() {
        indexDocument(1L, "java programming language");
        indexDocument(2L, "python scripting language");

        // "the", "and", "a" are English stop words and will be filtered out
        List<RankedSearchResult> results = retrievalEngine.search("the java and a language");

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).docId());
        assertTrue(results.get(0).matchedTerms().contains("java"));
        assertTrue(results.get(0).matchedTerms().contains("languag")); // stemmed
    }

    @Test
    @DisplayName("Should support morphological stemming in queries")
    void shouldSupportStemmingInQueries() {
        indexDocument(1L, "developer develops developing web application");

        // Searching for inflected forms should match stemmed index term "develop"
        List<RankedSearchResult> results = retrievalEngine.search("developments");

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).docId());
        assertTrue(results.get(0).matchedTerms().contains("develop"));
    }

    @Test
    @DisplayName("Should truncate results to requested limit (top-K)")
    void shouldTruncateToRequestedLimit() {
        for (long i = 1; i <= 10; i++) {
            indexDocument(i, "search engine index document " + i);
        }

        List<RankedSearchResult> top3 = retrievalEngine.search("search engine", 3);

        assertEquals(3, top3.size());
        assertEquals(1, top3.get(0).rank());
        assertEquals(2, top3.get(1).rank());
        assertEquals(3, top3.get(2).rank());

        // Verify strictly descending score
        assertTrue(top3.get(0).score() >= top3.get(1).score());
        assertTrue(top3.get(1).score() >= top3.get(2).score());
    }

    @Test
    @DisplayName("Should return empty list for queries with no matching documents")
    void shouldReturnEmptyWhenNoMatch() {
        indexDocument(1L, "java spring framework");

        List<RankedSearchResult> results = retrievalEngine.search("rust golang kubernetes");

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty, blank, or invalid queries")
    void shouldHandleEmptyOrBlankQueries() {
        indexDocument(1L, "sample document");

        assertTrue(retrievalEngine.search("").isEmpty());
        assertTrue(retrievalEngine.search("   ").isEmpty());
        assertTrue(retrievalEngine.search(null).isEmpty());
        assertTrue(retrievalEngine.search("sample", 0).isEmpty());
        assertTrue(retrievalEngine.search("sample", -5).isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when index is completely empty")
    void shouldHandleEmptyIndex() {
        List<RankedSearchResult> results = retrievalEngine.search("java spring");
        assertTrue(results.isEmpty());
    }
}
