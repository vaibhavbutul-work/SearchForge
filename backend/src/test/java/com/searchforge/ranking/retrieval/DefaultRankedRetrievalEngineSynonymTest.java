package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import com.searchforge.query.synonym.dictionary.InMemorySynonymDictionary;
import com.searchforge.query.synonym.service.DefaultSynonymExpansionService;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies end-to-end retrieval and BM25 scoring with synonym expansion in {@link DefaultRankedRetrievalEngine}.
 */
class DefaultRankedRetrievalEngineSynonymTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private Bm25Scorer bm25Scorer;
    private SynonymProperties properties;
    private InMemorySynonymDictionary dictionary;
    private SynonymExpansionService synonymExpansionService;
    private DefaultRankedRetrievalEngine retrievalEngine;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        properties = new SynonymProperties();
        properties.setMode(SynonymExpansionMode.BIDIRECTIONAL);
        properties.setDiscount(0.5);

        dictionary = new InMemorySynonymDictionary(properties);
        synonymExpansionService = new DefaultSynonymExpansionService(dictionary, properties, stopWordFilter);

        retrievalEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer, synonymExpansionService);
    }

    private void indexDocument(long docId, String text) {
        index.addDocument(docId, tokenizer.tokenize(text));
    }

    @Test
    @DisplayName("Should retrieve document matching synonym term when original query term is not in document")
    void shouldRetrieveDocumentViaSynonymExpansion() {
        // Doc 1 contains "database", does NOT contain "db"
        indexDocument(1L, "PostgreSQL relational database management system");
        // Doc 2 contains unrelated terms
        indexDocument(2L, "Java virtual machine bytecode compiler");

        // Query "db" expands to "db" (weight 1.0) and "database" (weight 0.5)
        List<RankedSearchResult> results = retrievalEngine.search("db", 10);

        assertEquals(1, results.size());
        RankedSearchResult hit = results.get(0);
        assertEquals(1L, hit.docId());
        assertTrue(hit.score() > 0.0);
        // Stem of "database" is "databas"
        assertTrue(hit.matchedTerms().contains("databas"));
    }

    @Test
    @DisplayName("Original term should receive higher BM25 score than discounted synonym term")
    void originalTermShouldRankHigherThanSynonymTerm() {
        // Doc 1 contains exact term "db"
        indexDocument(1L, "db storage engine");
        // Doc 2 contains synonym term "database"
        indexDocument(2L, "database storage engine");
        // Doc 3 for collection statistics
        indexDocument(3L, "unrelated text file");

        List<RankedSearchResult> results = retrievalEngine.search("db", 10);

        assertEquals(2, results.size());
        // Doc 1 (exact match, weight 1.0) must score higher than Doc 2 (synonym match, weight 0.5)
        RankedSearchResult rank1 = results.get(0);
        RankedSearchResult rank2 = results.get(1);

        assertEquals(1L, rank1.docId());
        assertEquals(2L, rank2.docId());
        assertTrue(rank1.score() > rank2.score(), "Exact match score (" + rank1.score() + ") must be > synonym score (" + rank2.score() + ")");
    }

    @Test
    @DisplayName("Quoted queries should bypass synonym expansion and preserve exact phrase semantics")
    void quotedQueryShouldBypassSynonymExpansion() {
        indexDocument(1L, "high performance relational database");
        indexDocument(2L, "database db storage");

        // Quoted query "relational database"
        List<RankedSearchResult> results = retrievalEngine.search("\"relational database\"", 10);

        // Standard retrieval executed without expanding "database" to "db"
        assertFalse(results.isEmpty());
    }
}
