package com.searchforge.query.evaluation;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.service.DefaultSnippetGeneratorService;
import com.searchforge.query.snippet.service.SnippetGeneratorService;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.service.DefaultSpellingCorrectionService;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import com.searchforge.query.synonym.dictionary.InMemorySynonymDictionary;
import com.searchforge.query.synonym.service.DefaultSynonymExpansionService;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.evaluation.EvaluationQuery;
import com.searchforge.ranking.evaluation.QueryEvaluationResult;
import com.searchforge.ranking.evaluation.RelevanceEvaluationRunner;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates information retrieval relevance criteria for Phase 5 (Task 6):
 * 1. Spelling correction improves retrieval effectiveness for misspelled queries.
 * 2. Synonym expansion increases Recall@K without sacrificing Precision@1.
 * 3. Dynamic snippet windowing selects the most informative query-dense excerpt.
 * 4. Disabling optional features guarantees 100% consistent baseline BM25 scoring.
 */
class QueryIntelligenceRelevanceEvaluationTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private SpellingCorrectionService spellingService;
    private SnippetGeneratorService snippetService;
    private SynonymProperties synonymProperties;
    private InMemorySynonymDictionary synonymDictionary;
    private SynonymExpansionService synonymService;
    private RankedRetrievalEngine baselineEngine;
    private RankedRetrievalEngine enrichedEngine;
    private RelevanceEvaluationRunner evaluationRunner;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        Bm25Config bm25Config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(bm25Config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        // Populate test corpus
        // Doc 1: Target for PostgreSQL queries
        index.addDocument(1L, tokenizer.tokenize("PostgreSQL uses a btree index for fast indexing and high performance queries."));
        // Doc 2: Target for Database queries
        index.addDocument(2L, tokenizer.tokenize("Relational database systems ensure ACID transactional consistency across tables."));
        // Doc 3: Target for exact DB term
        index.addDocument(3L, tokenizer.tokenize("Distributed db engine scaling for modern cloud workloads."));
        // Doc 4: Unrelated distractor document
        index.addDocument(4L, tokenizer.tokenize("Python programming language for machine learning and numerical analysis."));

        spellingService = new DefaultSpellingCorrectionService(index);
        snippetService = new DefaultSnippetGeneratorService(stopWordFilter, 120);

        synonymProperties = new SynonymProperties();
        synonymProperties.setMode(SynonymExpansionMode.BIDIRECTIONAL);
        synonymProperties.setDiscount(0.5);
        synonymDictionary = new InMemorySynonymDictionary(synonymProperties);
        synonymService = new DefaultSynonymExpansionService(synonymDictionary, synonymProperties, stopWordFilter);

        baselineEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer, null);
        enrichedEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer, synonymService);
        evaluationRunner = new RelevanceEvaluationRunner();
    }

    @Test
    @DisplayName("Evaluation 1: Spelling correction recovers 100% recall for misspelled queries")
    void spellingCorrectionImprovesRetrieval() {
        String misspelledQuery = "postgreql btre";

        // Baseline retrieval on misspelled terms yields 0 hits
        List<RankedSearchResult> baselineHits = enrichedEngine.search(misspelledQuery, 10);
        assertTrue(baselineHits.isEmpty(), "Direct search on misspelled query should yield 0 hits");

        // Spelling suggestion detects correction
        QuerySuggestionResponse suggestion = spellingService.suggest(misspelledQuery);
        assertTrue(suggestion.hasCorrection());
        assertEquals("postgresql btree", suggestion.suggestedQuery());

        // Evaluating retrieval using the suggested query achieves 100% recall on Doc 1
        EvaluationQuery evalQuery = new EvaluationQuery(
                suggestion.suggestedQuery(),
                Map.of(1L, 3) // Doc 1 is highly relevant
        );
        QueryEvaluationResult result = evaluationRunner.evaluateQuery(enrichedEngine, evalQuery, 1);

        assertEquals(1.0, result.precisionAtK(), "Precision@1 must be 1.0 for suggested query");
        assertEquals(1.0, result.recallAtK(), "Recall@1 must be 1.0 for suggested query");
        assertEquals(1.0, result.reciprocalRank(), "MRR must be 1.0 for suggested query");
    }

    @Test
    @DisplayName("Evaluation 2: Synonym expansion improves Recall@K by surfacing synonym-matched documents")
    void synonymExpansionImprovesRecall() {
        // Doc 2 has "database" but not "db"
        EvaluationQuery query = new EvaluationQuery(
                "db",
                Map.of(3L, 3, 2L, 2) // Both Doc 3 (exact "db") and Doc 2 (synonym "database") are relevant
        );

        // Baseline engine without synonym expansion only retrieves Doc 3 (Recall@2 = 0.5)
        QueryEvaluationResult baselineResult = evaluationRunner.evaluateQuery(baselineEngine, query, 2);
        assertEquals(1, baselineResult.retrievedCount());
        assertEquals(0.5, baselineResult.recallAtK());

        // Enriched engine with synonym expansion retrieves BOTH Doc 3 and Doc 2 (Recall@2 = 1.0)
        QueryEvaluationResult enrichedResult = evaluationRunner.evaluateQuery(enrichedEngine, query, 2);
        assertEquals(2, enrichedResult.retrievedCount());
        assertEquals(1.0, enrichedResult.recallAtK());
    }

    @Test
    @DisplayName("Evaluation 3: Synonym discounting prevents query drift (Exact match outranks synonym)")
    void synonymDiscountingMaintainsHighPrecisionAt1() {
        List<RankedSearchResult> hits = enrichedEngine.search("db", 10);

        assertEquals(2, hits.size());
        RankedSearchResult rank1 = hits.get(0);
        RankedSearchResult rank2 = hits.get(1);

        // Doc 3 (contains literal "db") must rank ahead of Doc 2 (contains discounted synonym "database")
        assertEquals(3L, rank1.docId(), "Exact match must be Rank 1");
        assertEquals(2L, rank2.docId(), "Synonym match must be Rank 2");
        assertTrue(rank1.score() > rank2.score(), "Exact match score must strictly exceed discounted synonym score");
    }

    @Test
    @DisplayName("Evaluation 4: Snippet generation selects the dense relevance window over a blind prefix")
    void snippetGenerationSelectsDenseRelevanceWindow() {
        // Document with 50 filler words before the actual relevant section
        String longText = "Introduction to modern application development with many introductory words that do not match "
                + "the user query. Here is more filler content padding the beginning of the text to push the query terms "
                + "further down the page. Finally, PostgreSQL uses a btree index for high throughput transactions and low latency.";

        HighlightedSnippet snippet = snippetService.generateSnippet(longText, Set.of("postgresql", "btree"), 120);

        assertTrue(snippet.hasMatch());
        assertTrue(snippet.highlightedHtml().contains("<mark>PostgreSQL</mark>"));
        assertTrue(snippet.highlightedHtml().contains("<mark>btree</mark>"));
        // Ensure filler prefix was skipped in favor of the relevant window
        assertFalse(snippet.rawSnippet().startsWith("Introduction to modern"), "Relevance scorer must skip low-scoring prefix");
    }

    @Test
    @DisplayName("Evaluation 5: Baseline BM25 scoring remains 100% consistent when synonyms are disabled")
    void scoringConsistencyWhenSynonymsDisabled() {
        // Query "postgresql btree" has no synonyms
        List<RankedSearchResult> baselineHits = baselineEngine.search("postgresql btree", 5);
        List<RankedSearchResult> enrichedHits = enrichedEngine.search("postgresql btree", 5);

        assertEquals(baselineHits.size(), enrichedHits.size());
        for (int i = 0; i < baselineHits.size(); i++) {
            assertEquals(baselineHits.get(i).docId(), enrichedHits.get(i).docId());
            assertEquals(baselineHits.get(i).score(), enrichedHits.get(i).score(), 1e-6);
        }
    }
}
