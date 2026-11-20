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
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.search.hybrid.DefaultHybridSearchService;
import com.searchforge.search.hybrid.HybridSearchService;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchQualityBenchmarkRunner Unit and Comparative Evaluation Tests")
class SearchQualityBenchmarkRunnerTest {

    private SearchQualityBenchmarkRunner benchmarkRunner;
    private InMemoryInvertedIndex invertedIndex;
    private Tokenizer tokenizer;
    private DeterministicEmbeddingModel embeddingModel;
    private InMemoryVectorStorage vectorStorage;
    private ExactVectorSimilarityEngine vectorSimilarityEngine;
    private RankedRetrievalEngine rankedRetrievalEngine;
    private ReciprocalRankFusionEngine rankFusionEngine;
    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        invertedIndex = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        Bm25Config config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);
        rankedRetrievalEngine = new DefaultRankedRetrievalEngine(invertedIndex, bm25Scorer, tokenizer);

        embeddingModel = new DeterministicEmbeddingModel();
        vectorStorage = new InMemoryVectorStorage();
        vectorSimilarityEngine = new ExactVectorSimilarityEngine(vectorStorage);
        rankFusionEngine = new ReciprocalRankFusionEngine(60);

        hybridSearchService = new DefaultHybridSearchService(
                rankedRetrievalEngine,
                embeddingModel,
                vectorSimilarityEngine,
                rankFusionEngine,
                2,
                10
        );

        RelevanceEvaluationRunner evaluationRunner = new RelevanceEvaluationRunner();

        benchmarkRunner = new SearchQualityBenchmarkRunner(
                hybridSearchService,
                rankedRetrievalEngine,
                embeddingModel,
                vectorSimilarityEngine,
                rankFusionEngine,
                evaluationRunner
        );

        populateCorpus();
    }

    private void addDoc(long id, String text) {
        invertedIndex.addDocument(id, tokenizer.tokenize(text));
        float[] vector = embeddingModel.embed(text);
        vectorStorage.put(new DocumentVector(id, vector));
    }

    private void populateCorpus() {
        // Doc 1: Books & printing press
        addDoc(1L, "History of books, printing press, Gutenberg literature, and libraries.");
        // Doc 2: Dogs
        addDoc(2L, "Domestic dog breeds, canine behavior, training puppies, obedience.");
        // Doc 3: Cats
        addDoc(3L, "Domestic cat characteristics, feline agility, nocturnal hunting, reflexes.");
        // Doc 4: Bottles
        addDoc(4L, "Bottles glass, plastic, liquid beverage storage containers.");
        // Doc 5: Chairs & ergonomics
        addDoc(5L, "Chair furniture design, ergonomic seating, lumbar support, back fatigue.");
        // Doc 6: Tables & desks
        addDoc(6L, "Table furniture types, dining surfaces, study desks.");
        // Doc 7: Clocks & timekeeping
        addDoc(7L, "Mechanical clocks, quartz wristwatches, atomic clocks, timekeeping accuracy.");
        // Doc 8: Cars
        addDoc(8L, "Automobile engineering, electric vehicles, lithium-ion battery transportation.");
        // Doc 9: Bicycles
        addDoc(9L, "Bicycle mechanics, gearing, pedals, sustainable urban cycling.");
        // Doc 10: Computers
        addDoc(10L, "Personal computer hardware, microprocessors, dynamic RAM, solid-state NVMe operating systems.");
        // Doc 11: Coffee
        addDoc(11L, "Coffee bean roasting, espresso brewing, caffeine hot morning beverage.");
        // Doc 12: Bread
        addDoc(12L, "Bread making, sourdough fermentation, wheat flour, baking.");
        // Doc 13: Galaxies
        addDoc(13L, "Galaxies, stars, supermassive black holes, cosmic dust, dark matter universe.");
        // Doc 14: Oceans
        addDoc(14L, "Oceans, marine ecosystems, global water cycle, saline water.");
        // Doc 15: Trees & photosynthesis
        addDoc(15L, "Trees, forest ecosystems, absorbing carbon, solar photosynthesis releasing oxygen.");
        // Doc 16: Music
        addDoc(16L, "Musical instruments, acoustic guitars, pianos, harmony, strings.");
        // Doc 17: Heart
        addDoc(17L, "Human heart anatomy, cardiovascular blood circulation, pumping arteries veins.");
        // Doc 18: Sun & solar
        addDoc(18L, "The Sun, solar radiation, nuclear fusion, clean renewable energy.");
        // Doc 19: Spring Boot
        addDoc(19L, "Spring Boot microservices, cloud native Java, enterprise applications.");
        // Doc 20: Spring Framework Core
        addDoc(20L, "Spring Framework Core, dependency injection, enterprise Java plumbing.");
        // Doc 21: PostgreSQL
        addDoc(21L, "PostgreSQL relational database, SQL query optimization, transaction reliability.");
        // Doc 22: Okapi BM25
        addDoc(22L, "Okapi BM25 probabilistic relevance ranking, scoring, Robertson Sparck Jones.");
        // Doc 23: Microservices Guide
        addDoc(23L, "Microservices architecture, distributed systems, Martin Fowler independently deployable.");
        // Doc 24: Java 21 LTS
        addDoc(24L, "Java 21 LTS documentation, virtual threads, lightweight concurrency high throughput.");
    }

    @Test
    @DisplayName("Should run automated multi-mode benchmark comparing BM25, Semantic, and Hybrid across all categories")
    void runBenchmark_shouldProduceCompleteComparisonReport() {
        List<CategorizedEvaluationQuery> queries = BenchmarkDataset.getStandardDataset();

        ModeComparisonReport report = benchmarkRunner.runBenchmark(queries, 3, 2);

        assertNotNull(report);
        assertEquals(queries.size(), report.totalQueries());
        assertEquals(3, report.k());

        // 1. Verify all three mode evaluation reports are populated
        EvaluationReport bm25Rep = report.bm25Report();
        EvaluationReport semRep = report.semanticReport();
        EvaluationReport hybRep = report.hybridReport();

        assertNotNull(bm25Rep);
        assertNotNull(semRep);
        assertNotNull(hybRep);

        assertEquals(queries.size(), bm25Rep.totalQueries());
        assertEquals(queries.size(), semRep.totalQueries());
        assertEquals(queries.size(), hybRep.totalQueries());

        assertTrue(bm25Rep.meanNdcgAtK() > 0.0, "BM25 NDCG should be positive");
        assertTrue(semRep.meanNdcgAtK() > 0.0, "Semantic NDCG should be positive");
        assertTrue(hybRep.meanNdcgAtK() > 0.0, "Hybrid NDCG should be positive");

        assertTrue(bm25Rep.meanReciprocalRank() > 0.0, "BM25 MRR should be positive");
        assertTrue(semRep.meanReciprocalRank() > 0.0, "Semantic MRR should be positive");
        assertTrue(hybRep.meanReciprocalRank() > 0.0, "Hybrid MRR should be positive");

        // 2. Verify sub-millisecond component latency breakdown is populated
        ComponentTimingBreakdown timings = report.timings();
        assertNotNull(timings);
        assertTrue(timings.avgBm25Ms() >= 0.0);
        assertTrue(timings.avgInferenceMs() >= 0.0);
        assertTrue(timings.avgVectorSearchMs() >= 0.0);
        assertTrue(timings.avgFusionMs() >= 0.0);
        assertTrue(timings.avgTotalHybridMs() >= 0.0);
        assertTrue(timings.p95TotalHybridMs() >= timings.avgTotalHybridMs());

        // 3. Verify category breakdown covers all 8 categories
        List<CategoryPerformance> categories = report.categoryBreakdown();
        assertEquals(8, categories.size());

        for (CategoryPerformance catPerf : categories) {
            assertNotNull(catPerf.category());
            assertTrue(catPerf.queryCount() >= 2);
            assertNotNull(catPerf.winningMode());
            assertNotNull(catPerf.analyticalTakeaway());
            assertFalse(catPerf.analyticalTakeaway().isBlank());
        }

        // 4. Verify Hybrid achieves robust overall MRR and NDCG across the heterogeneous benchmark
        System.out.println("=========================================================================================");
        System.out.println("PHASE 7 BENCHMARK RESULTS (K=3):");
        System.out.println(String.format("  BM25:     NDCG@3=%.4f, MRR=%.4f, P@3=%.4f, R@3=%.4f",
                bm25Rep.meanNdcgAtK(), bm25Rep.meanReciprocalRank(), bm25Rep.meanPrecisionAtK(), bm25Rep.meanRecallAtK()));
        System.out.println(String.format("  Semantic: NDCG@3=%.4f, MRR=%.4f, P@3=%.4f, R@3=%.4f",
                semRep.meanNdcgAtK(), semRep.meanReciprocalRank(), semRep.meanPrecisionAtK(), semRep.meanRecallAtK()));
        System.out.println(String.format("  Hybrid:   NDCG@3=%.4f, MRR=%.4f, P@3=%.4f, R@3=%.4f",
                hybRep.meanNdcgAtK(), hybRep.meanReciprocalRank(), hybRep.meanPrecisionAtK(), hybRep.meanRecallAtK()));
        System.out.println("TIMINGS (ms):");
        System.out.println(String.format("  BM25=%.3fms, Inference=%.3fms, VectorSearch=%.3fms, Fusion=%.3fms, Total=%.3fms (P95=%.3fms)",
                timings.avgBm25Ms(), timings.avgInferenceMs(), timings.avgVectorSearchMs(), timings.avgFusionMs(), timings.avgTotalHybridMs(), timings.p95TotalHybridMs()));
        System.out.println("CATEGORY BREAKDOWN:");
        for (CategoryPerformance cp : categories) {
            System.out.println(String.format("  [%-16s] (N=%d) BM25=%.4f | Sem=%.4f | Hyb=%.4f -> Winner: %s (%s)",
                    cp.category(), cp.queryCount(), cp.bm25Ndcg(), cp.semanticNdcg(), cp.hybridNdcg(), cp.winningMode(), cp.analyticalTakeaway()));
        }
        System.out.println("=========================================================================================");

        assertTrue(hybRep.meanNdcgAtK() >= 0.70, "Hybrid NDCG should be strong across diverse queries: " + hybRep.meanNdcgAtK());
        assertTrue(hybRep.meanReciprocalRank() >= 0.70, "Hybrid MRR should be strong: " + hybRep.meanReciprocalRank());

        assertNotNull(report.methodologyNotes());
        assertTrue(report.methodologyNotes().contains("384 dimensions"));
    }

    @Test
    @DisplayName("Should reject empty query list with IllegalArgumentException")
    void runBenchmark_emptyQueries_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> benchmarkRunner.runBenchmark(List.of(), 5, 1));
        assertThrows(IllegalArgumentException.class, () -> benchmarkRunner.runBenchmark(null, 5, 1));
    }
}
