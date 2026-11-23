package com.searchforge.performance;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.performance.harness.SearchPerformanceBenchmarkRunner;
import com.searchforge.performance.telemetry.IndexingBenchmarkTelemetry;
import com.searchforge.performance.telemetry.LatencyHistogram;
import com.searchforge.performance.telemetry.SearchBenchmarkTelemetry;
import com.searchforge.ranking.evaluation.CategorizedEvaluationQuery;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 10.1: Performance Baseline & Latency Telemetry Harness Tests")
class SearchPerformanceBenchmarkRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(SearchPerformanceBenchmarkRunnerTest.class);

    @Autowired
    private SearchPerformanceBenchmarkRunner benchmarkRunner;

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private InvertedIndex invertedIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    private com.searchforge.indexer.repository.TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedDocumentMetadataRepository metadataRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        invertedIndex.clear();
        vectorStorage.clear();
        if (activeDocumentFilter != null) {
            activeDocumentFilter.clear();
        }
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify LatencyHistogram calculates mathematically exact percentiles and summary metrics")
    void testLatencyHistogramPercentileAccuracy() {
        LatencyHistogram histogram = new LatencyHistogram("TestHistogram");

        // Feed exact sequence: 1.0ms, 2.0ms, ..., 100.0ms
        for (int i = 1; i <= 100; i++) {
            histogram.recordMillis((double) i);
        }

        LatencyHistogram.LatencySnapshot snapshot = histogram.snapshot();

        assertEquals(100, snapshot.sampleCount());
        assertEquals(1.0, snapshot.minMs(), 0.001);
        assertEquals(100.0, snapshot.maxMs(), 0.001);
        assertEquals(50.5, snapshot.meanMs(), 0.001);
        assertEquals(50.0, snapshot.p50Ms(), 0.001);
        assertEquals(90.0, snapshot.p90Ms(), 0.001);
        assertEquals(95.0, snapshot.p95Ms(), 0.001);
        assertEquals(99.0, snapshot.p99Ms(), 0.001);
    }

    @Test
    @DisplayName("Verify end-to-end search benchmark runner collects valid multi-mode percentiles")
    void testEndToEndSearchBenchmarkExecution() {
        // 1. Seed small corpus with fresh entities
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, 10);
        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    com.searchforge.lifecycle.model.DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            indexingCoordinator.processDocument(doc.getId(), doc.getTitle(), doc.getCleanText());
        }

        List<CategorizedEvaluationQuery> queries = ExpandedBenchmarkDataset.getQueries().subList(0, 8);

        // 2. Run benchmark: 2 warmup iterations, 3 measurement repetitions
        SearchBenchmarkTelemetry telemetry = benchmarkRunner.runSearchBenchmark(queries, 2, 3, 5);

        assertNotNull(telemetry);
        assertEquals(queries.size() * 3, telemetry.totalQueriesExecuted());
        assertTrue(telemetry.totalExecutionDurationMs() >= 0);

        // Check BM25 telemetry
        LatencyHistogram.LatencySnapshot bm25 = telemetry.getMode("BM25");
        assertNotNull(bm25);
        assertEquals(24, bm25.sampleCount());
        assertTrue(bm25.p50Ms() >= 0.0);
        assertTrue(bm25.p95Ms() >= bm25.p50Ms());
        assertTrue(bm25.maxMs() >= bm25.p95Ms());

        // Check Hybrid telemetry
        LatencyHistogram.LatencySnapshot hybrid = telemetry.getMode("Hybrid");
        assertNotNull(hybrid);
        assertEquals(24, hybrid.sampleCount());
        assertTrue(hybrid.p50Ms() >= 0.0);
        assertTrue(hybrid.p95Ms() >= hybrid.p50Ms());

        // Check Component breakdown
        LatencyHistogram.LatencySnapshot tokenization = telemetry.getComponent("Tokenization");
        assertNotNull(tokenization);
        assertEquals(24, tokenization.sampleCount());

        log.info("BM25 Latency Baseline: {}", bm25);
        log.info("Hybrid Latency Baseline: {}", hybrid);
    }

    @Test
    @DisplayName("Verify indexing throughput telemetry computes valid DPS and TPS metrics")
    void testIndexingBenchmarkExecution() {
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, 5);
        List<CrawledDocument> freshCorpus = new java.util.ArrayList<>();
        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    com.searchforge.lifecycle.model.DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            freshCorpus.add(crawledDocumentRepository.saveAndFlush(doc));
        }

        IndexingBenchmarkTelemetry telemetry = benchmarkRunner.runIndexingBenchmark(freshCorpus, indexingCoordinator);

        assertNotNull(telemetry);
        assertEquals(5, telemetry.totalDocumentsIndexed());
        assertTrue(telemetry.totalTokensIndexed() > 0);
        assertTrue(telemetry.documentsPerSecond() > 0.0);
        assertTrue(telemetry.tokensPerSecond() > 0.0);
        assertTrue(telemetry.totalIndexingLatency().p50Ms() > 0.0);

        log.info("Indexing Baseline: {} docs, {} tokens in {} ms (DPS={}/s, TPS={}/s)",
                telemetry.totalDocumentsIndexed(),
                telemetry.totalTokensIndexed(),
                telemetry.totalDurationMs(),
                String.format("%.1f", telemetry.documentsPerSecond()),
                String.format("%.1f", telemetry.tokensPerSecond()));
    }
}
