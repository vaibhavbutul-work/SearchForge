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

class RankedRetrievalPerformanceTest {

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

        // Populate a synthetic corpus of 200 documents
        for (long i = 1; i <= 200; i++) {
            StringBuilder text = new StringBuilder();
            text.append("document ").append(i).append(" about search engineering. ");
            if (i % 2 == 0) text.append("java spring boot microservices framework. ");
            if (i % 3 == 0) text.append("python crawling scraper data parsing. ");
            if (i % 5 == 0) text.append("inverted index posting list bm25 relevance scoring. ");
            if (i % 7 == 0) text.append("high performance low latency distributed system. ");
            index.addDocument(i, tokenizer.tokenize(text.toString()));
        }
    }

    @Test
    @DisplayName("Should execute single-term ranked retrieval under 15ms for 200 documents")
    void shouldBenchmarkSingleTermQuery() {
        // Warmup JIT
        for (int i = 0; i < 50; i++) {
            retrievalEngine.search("java", 10);
        }

        long startNs = System.nanoTime();
        List<RankedSearchResult> results = retrievalEngine.search("java", 10);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertEquals(10, results.size());
        assertTrue(elapsedMs < 15, "Single term search took " + elapsedMs + "ms, expected < 15ms");
        assertTrue(results.get(0).score() > 0.0);
    }

    @Test
    @DisplayName("Should execute multi-term ranked retrieval under 20ms for 200 documents")
    void shouldBenchmarkMultiTermQuery() {
        // Warmup JIT
        for (int i = 0; i < 50; i++) {
            retrievalEngine.search("java spring boot microservices", 10);
        }

        long startNs = System.nanoTime();
        List<RankedSearchResult> results = retrievalEngine.search("java spring boot microservices", 10);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertEquals(10, results.size());
        assertTrue(elapsedMs < 20, "Multi term search took " + elapsedMs + "ms, expected < 20ms");
        assertTrue(results.get(0).score() >= results.get(1).score());
    }

    @Test
    @DisplayName("Should maintain descending score order and correct ranks under batch load")
    void shouldMaintainStrictScoreInvariance() {
        List<RankedSearchResult> results = retrievalEngine.search("inverted index scoring relevance", 25);

        assertFalse(results.isEmpty());
        for (int i = 0; i < results.size() - 1; i++) {
            RankedSearchResult current = results.get(i);
            RankedSearchResult next = results.get(i + 1);

            assertEquals(i + 1, current.rank());
            assertTrue(current.score() >= next.score(), "Scores must be strictly non-increasing");
            if (Double.compare(current.score(), next.score()) == 0) {
                assertTrue(current.docId() < next.docId(), "Ties must be broken by docId ascending");
            }
        }
    }
}
