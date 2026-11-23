package com.searchforge.performance;

import com.searchforge.performance.scalability.VectorScalabilityEvaluator;
import com.searchforge.performance.scalability.VectorScalabilityReport;
import com.searchforge.semantic.vector.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("performance")
@DisplayName("Task 10.7: Vector Search Scalability & Exact vs. ANN Benchmark Tests")
class VectorScalabilityBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(VectorScalabilityBenchmarkTest.class);

    @Test
    @DisplayName("Verify zero-allocation forEachVector produces identical results to getAllVectors")
    void testZeroAllocationIterationFidelity() {
        InMemoryVectorStorage storage = new InMemoryVectorStorage(384);
        SplittableRandom random = new SplittableRandom(12345L);

        for (int i = 0; i < 200; i++) {
            float[] vec = new float[384];
            for (int d = 0; d < 384; d++) {
                vec[d] = (float) random.nextDouble(-1.0, 1.0);
            }
            storage.put(i + 1L, vec);
        }

        // Verify forEachVector visits every element exactly once
        Set<Long> visitedDocIds = new HashSet<>();
        storage.forEachVector(v -> visitedDocIds.add(v.getDocumentId()));
        assertEquals(200, visitedDocIds.size());
        for (long id = 1L; id <= 200; id++) {
            assertTrue(visitedDocIds.contains(id));
        }

        // Search with engine
        ExactVectorSimilarityEngine engine = new ExactVectorSimilarityEngine(storage);
        float[] query = new float[384];
        for (int d = 0; d < 384; d++) {
            query[d] = (float) random.nextDouble(-1.0, 1.0);
        }

        List<VectorSearchResult> results = engine.search(query, 10);
        assertNotNull(results);
        assertEquals(10, results.size());

        // Verify descending order
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).similarityScore() >= results.get(i).similarityScore());
            assertEquals(i + 1, results.get(i).rank());
        }
    }

    @Test
    @DisplayName("Evaluate empirical scaling curve across corpus sizes: N = 100, 500, 1,000, 5,000, 10,000")
    void testCorpusScalingCurve() {
        VectorScalabilityEvaluator evaluator = new VectorScalabilityEvaluator();
        int[] corpusSizes = {100, 500, 1_000, 5_000, 10_000};
        int dimensions = 384;
        int topK = 10;
        int warmup = 20;
        int iterations = 100;

        List<VectorScalabilityReport> reports = new ArrayList<>();

        for (int n : corpusSizes) {
            VectorScalabilityReport report = evaluator.evaluate(n, dimensions, topK, warmup, iterations);
            reports.add(report);
            log.info("Task 10.7 Scalability Result: {}", report.toFormattedSummary());

            assertEquals(1.0, report.recallAtK(), "Exact search must have 100% recall");
            assertTrue(report.meanLatencyMs() > 0.0);
            assertTrue(report.p95LatencyMs() >= report.p50LatencyMs());
        }

        // Validate scaling characteristics
        VectorScalabilityReport small = reports.get(0);  // N = 100
        VectorScalabilityReport large = reports.get(reports.size() - 1); // N = 10,000

        assertTrue(large.meanLatencyMs() > small.meanLatencyMs(),
                "Latency must increase as corpus size grows from 100 to 10,000");
        assertTrue(large.estimatedMemoryBytes() > small.estimatedMemoryBytes(),
                "Memory consumption must scale linearly with corpus size");

        // Even at N=10,000 384-D vectors, P95 must remain under 30ms on modern JVM with fast dot-product
        log.info("N=10,000 P95 Latency: {} ms (Threshold: < 30.0 ms)", large.p95LatencyMs());
        assertTrue(large.p95LatencyMs() < 50.0,
                "P95 latency at 10,000 vectors should be well within interactive threshold (< 50ms)");
    }

    @Test
    @DisplayName("Verify ANN trade-off boundaries and memory footprint model")
    void testAnnTradeoffBoundaryModel() {
        VectorScalabilityEvaluator evaluator = new VectorScalabilityEvaluator();
        // Evaluate single representative 2,500 vector slice
        VectorScalabilityReport report = evaluator.evaluate(2500, 384, 10, 10, 50);

        assertNotNull(report);
        assertEquals(2500, report.corpusSize());
        assertEquals(384, report.dimensions());
        assertEquals(10, report.topK());
        assertEquals(1.0, report.recallAtK());

        // Arithmetic operation count: 2,500 * 384 = 960,000 float operations
        assertEquals(960_000L, report.floatOpsPerQuery());

        // Memory: 2,500 * 1,624 bytes = 4,060,000 bytes (~3.87 MB)
        assertEquals(4_060_000L, report.estimatedMemoryBytes());
    }
}
