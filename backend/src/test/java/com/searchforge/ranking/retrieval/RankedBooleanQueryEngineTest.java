package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.retrieval.BooleanQueryParser;
import com.searchforge.indexer.retrieval.DefaultBooleanRetrievalEngine;
import com.searchforge.indexer.retrieval.PostingsMerger;
import com.searchforge.indexer.search.PositionalIndexSearcher;
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

class RankedBooleanQueryEngineTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private RankedBooleanQueryEngine rankedBooleanEngine;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        PositionalIndexSearcher positionalSearcher = new PositionalIndexSearcher(index, tokenizer);
        BooleanQueryParser parser = new BooleanQueryParser();
        DefaultBooleanRetrievalEngine booleanEngine = new DefaultBooleanRetrievalEngine(index, positionalSearcher, tokenizer);

        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        rankedBooleanEngine = new DefaultRankedBooleanQueryEngine(
                parser,
                booleanEngine,
                index,
                bm25Scorer,
                tokenizer,
                positionalSearcher
        );
    }

    private void indexDoc(long docId, String text) {
        index.addDocument(docId, tokenizer.tokenize(text));
    }

    @Test
    @DisplayName("Should rank Boolean AND query with relevance scoring")
    void shouldRankBooleanAndQuery() {
        // Doc 1: java spring boot microservices java spring (length 6)
        indexDoc(1L, "java spring boot microservices java spring");
        // Doc 2: java spring framework (length 3)
        indexDoc(2L, "java spring framework");
        // Doc 3: java python backend (contains java, but NOT spring)
        indexDoc(3L, "java python backend");

        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean("java AND spring", 10);

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).docId());
        assertEquals(1, results.get(0).rank());
        assertEquals(2L, results.get(1).docId());
        assertEquals(2, results.get(1).rank());

        // Doc 3 must be excluded by the Boolean AND filter
        assertTrue(results.stream().noneMatch(r -> r.docId() == 3L));
    }

    @Test
    @DisplayName("Should rank Boolean OR query rewarding multi-term matches over single-term matches")
    void shouldRankBooleanOrQuery() {
        // Doc 1: contains both java and spring
        indexDoc(1L, "java spring framework");
        // Doc 2: contains only java
        indexDoc(2L, "java backend development");
        // Doc 3: contains neither
        indexDoc(3L, "python django web framework");

        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean("java OR spring", 10);

        assertEquals(2, results.size());
        // Doc 1 has matches on both terms, so it should rank #1
        assertEquals(1L, results.get(0).docId());
        assertEquals(2L, results.get(1).docId());
        assertTrue(results.get(0).score() > results.get(1).score());
        assertTrue(results.stream().noneMatch(r -> r.docId() == 3L));
    }

    @Test
    @DisplayName("Should enforce Boolean NOT filter without allowing negated terms to inflate score")
    void shouldEnforceBooleanNotFilter() {
        indexDoc(1L, "java microservices modern");
        indexDoc(2L, "java microservices legacy monolith");

        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean("java AND NOT legacy", 10);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).docId());

        // Verify "legacy" is not in the explainability map
        assertFalse(results.get(0).termScores().containsKey("legaci"));
        assertFalse(results.get(0).termScores().containsKey("legacy"));
        assertTrue(results.get(0).termScores().containsKey("java"));
    }

    @Test
    @DisplayName("Should apply phrase boost to prioritize exact consecutive phrase matches")
    void shouldApplyPhraseBoostForQuotedPhrase() {
        // Doc 1: exact consecutive phrase "spring boot" appearing twice
        indexDoc(1L, "spring boot microservices with spring boot framework");
        // Doc 2: exact consecutive phrase "spring boot" appearing once
        indexDoc(2L, "spring boot application");

        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean("\"spring boot\"", 10);

        assertEquals(2, results.size());
        // Doc 1 should be rank 1 due to 2x phrase boost and higher term frequencies
        RankedSearchResult rank1 = results.get(0);
        assertEquals(1L, rank1.docId());
        assertTrue(rank1.score() > results.get(1).score());
        assertTrue(rank1.termScores().containsKey("PHRASE: \"spring boot\""));
    }

    @Test
    @DisplayName("Should support filtered search (free-text query + Boolean filter)")
    void shouldSupportFilteredSearch() {
        indexDoc(1L, "performance tuning for modern web service");
        indexDoc(2L, "performance tuning for legacy enterprise system");

        // Free-text "performance" filtered by "NOT legacy"
        List<RankedSearchResult> results = rankedBooleanEngine.searchFiltered("performance", "NOT legacy", 10);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).docId());
        assertTrue(results.get(0).matchedTerms().contains("perform"));
    }

    @Test
    @DisplayName("Should handle complex nested Boolean expressions")
    void shouldHandleComplexNestedBooleanQuery() {
        indexDoc(1L, "java backend service");
        indexDoc(2L, "python backend service");
        indexDoc(3L, "java backend legacy service");

        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean(
                "(java OR python) AND backend AND NOT legacy",
                10
        );

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.docId() == 1L));
        assertTrue(results.stream().anyMatch(r -> r.docId() == 2L));
        assertTrue(results.stream().noneMatch(r -> r.docId() == 3L));
    }

    @Test
    @DisplayName("Should return empty list for non-matching Boolean query")
    void shouldReturnEmptyForNonMatchingBooleanQuery() {
        indexDoc(1L, "java spring framework");
        List<RankedSearchResult> results = rankedBooleanEngine.searchRankedBoolean("golang AND rust", 10);
        assertTrue(results.isEmpty());
    }
}
