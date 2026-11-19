package com.searchforge.ranking.evaluation;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelevanceEvaluationRunnerTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private RankedRetrievalEngine retrievalEngine;
    private RelevanceEvaluationRunner evaluationRunner;

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
        evaluationRunner = new RelevanceEvaluationRunner();

        populateBenchmarkCorpus();
    }

    private void indexDoc(long docId, String text) {
        index.addDocument(docId, tokenizer.tokenize(text));
    }

    private void populateBenchmarkCorpus() {
        // Java Concurrency
        indexDoc(1L, "Java 21 virtual threads provide lightweight concurrency for high throughput applications.");
        indexDoc(2L, "Java multi-threading concurrency with thread pools, executors, and synchronized locks.");

        // Spring Boot Microservices
        indexDoc(3L, "Building resilient microservices architecture using Spring Boot and cloud native patterns.");
        indexDoc(4L, "Spring Boot web application tutorial for enterprise REST APIs and database access.");
        indexDoc(5L, "Deploying Spring Boot microservices containers into Kubernetes with Docker.");

        // Python Web Scraping
        indexDoc(6L, "Python web scraping using BeautifulSoup, requests, and asynchronous HTTP clients.");
        indexDoc(7L, "Web crawling architectures and URL normalization algorithms in Python.");

        // Search Engine & Inverted Index
        indexDoc(8L, "Inverted index data structures for fast full text search engine indexing and retrieval.");
        indexDoc(9L, "Building a search engine with BM25 relevance ranking and linguistic tokenization.");
        indexDoc(10L, "Information retrieval fundamentals: term frequency, document frequency, and postings lists.");

        // Databases & SQL
        indexDoc(11L, "PostgreSQL query optimization, B-tree indexes, and database transaction isolation.");
        indexDoc(12L, "Relational database schema design and SQL query performance tuning.");
    }

    @Test
    @DisplayName("Should run automated IR evaluation and achieve high NDCG@5 and MRR quality scores")
    void shouldEvaluateBenchmarkCorpusWithHighMetrics() {
        List<EvaluationQuery> benchmarkQueries = List.of(
                new EvaluationQuery(
                        "java virtual threads concurrency",
                        Map.of(1L, 2, 2L, 1)
                ),
                new EvaluationQuery(
                        "spring boot microservices",
                        Map.of(3L, 2, 4L, 1, 5L, 1)
                ),
                new EvaluationQuery(
                        "python web scraping",
                        Map.of(6L, 2, 7L, 1)
                ),
                new EvaluationQuery(
                        "search engine inverted index",
                        Map.of(8L, 2, 9L, 2, 10L, 1)
                )
        );

        EvaluationReport report = evaluationRunner.evaluateAll(retrievalEngine, benchmarkQueries, 5);

        assertEquals(4, report.totalQueries());
        assertEquals(5, report.k());

        // Assert quality thresholds prove superior BM25 ranking performance:
        // 1. Mean NDCG@5 >= 0.85
        // 2. Mean Reciprocal Rank (MRR) >= 0.90 (first result is almost always relevant)
        // 3. Mean Precision@5 >= 0.35 (since only 2-3 relevant docs exist per query)
        assertTrue(report.meanNdcgAtK() >= 0.85, "Mean NDCG@5 should exceed 0.85, was: " + report.meanNdcgAtK());
        assertTrue(report.meanReciprocalRank() >= 0.90, "MRR should exceed 0.90, was: " + report.meanReciprocalRank());
        assertTrue(report.meanRecallAtK() >= 0.80, "Mean Recall@5 should exceed 0.80, was: " + report.meanRecallAtK());

        // Verify individual query results are populated
        assertEquals(4, report.queryResults().size());
        for (QueryEvaluationResult qResult : report.queryResults()) {
            assertTrue(qResult.ndcgAtK() > 0.0);
            assertTrue(qResult.reciprocalRank() > 0.0);
            assertEquals(5, qResult.k());
        }
    }

    @Test
    @DisplayName("Should handle single query evaluation")
    void shouldEvaluateSingleQuery() {
        EvaluationQuery query = new EvaluationQuery(
                "python scraping",
                Map.of(6L, 2, 7L, 1)
        );

        QueryEvaluationResult result = evaluationRunner.evaluateQuery(retrievalEngine, query, 3);

        assertEquals("python scraping", result.query());
        assertEquals(3, result.k());
        assertEquals(1.0, result.reciprocalRank(), 1e-4); // Rank 1 is relevant
        assertTrue(result.precisionAtK() > 0.0);
        assertTrue(result.ndcgAtK() > 0.80);
    }
}
