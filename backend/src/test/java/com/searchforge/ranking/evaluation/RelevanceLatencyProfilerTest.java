package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.7: Relevance, Latency & Scalability Trade-offs Test Suite")
class RelevanceLatencyProfilerTest {

    private static RelevanceLatencyProfiler profiler;
    private static RelevanceLatencyProfiler.CompleteTradeoffReport report;

    @BeforeAll
    static void setUp() {
        profiler = new RelevanceLatencyProfiler();
        // 3 profiling repetitions across all 48 queries = 144 samples per retrieval mode
        report = profiler.profileTradeoffs(3);
    }

    @Test
    @DisplayName("Should execute multi-round profiling and collect sufficient samples")
    void shouldProfileCompleteTradeoffs() {
        assertNotNull(report);
        assertEquals(144, report.totalSamplesPerMode(), "Must profile 144 requests per mode (3 reps x 48 queries)");
        assertNotNull(report.bm25Latency());
        assertNotNull(report.semanticLatency());
        assertNotNull(report.hybridLatency());
        assertNotNull(report.subComponents());
        assertNotNull(report.memoryFootprint());
    }

    @Test
    @DisplayName("Should verify strict latency budgets and relevance guarantees")
    void shouldValidateLatencyBounds() {
        // BM25: Ultra-fast in-memory lexical retrieval (< 1.0 ms)
        assertTrue(report.bm25Latency().meanMs() < 1.0,
                "BM25 mean latency must be < 1.0 ms (got " + report.bm25Latency().meanMs() + " ms)");

        // Semantic: Real local ONNX transformer inference (< 10.0 ms)
        assertTrue(report.semanticLatency().meanMs() < 10.0,
                "Semantic mean latency must be < 10.0 ms (got " + report.semanticLatency().meanMs() + " ms)");

        // Hybrid: End-to-end multi-modal retrieval (< 15.0 ms)
        assertTrue(report.hybridLatency().meanMs() < 15.0,
                "Hybrid mean latency must be < 15.0 ms (got " + report.hybridLatency().meanMs() + " ms)");

        // Relevance guarantees
        assertTrue(report.hybridQuality().ndcgAt5() >= 0.90, "Hybrid NDCG@5 must be >= 0.90");
        assertTrue(report.hybridQuality().mrr() >= 0.95, "Hybrid MRR must be >= 0.95");
    }

    @Test
    @DisplayName("Should print comprehensive Relevance vs Latency Pareto report")
    void shouldPrintComprehensiveTradeoffReport() {
        var b = report.bm25Latency();
        var s = report.semanticLatency();
        var h = report.hybridLatency();
        var sub = report.subComponents();
        var bQ = report.bm25Quality();
        var sQ = report.semanticQuality();
        var hQ = report.hybridQuality();
        var mem = report.memoryFootprint();

        System.out.println("=========================================================================================");
        System.out.println("           SEARCHFORGE RELEVANCE, LATENCY & SCALABILITY TRADE-OFF REPORT                 ");
        System.out.println("=========================================================================================");
        System.out.printf("Samples per mode: %d | Corpus: 120 docs | Embeddings: 384-D ONNX MiniLM%n", report.totalSamplesPerMode());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("PART 1: RELEVANCE VS. LATENCY SUMMARY (PARETO ANALYSIS):");
        System.out.printf("%-14s | %-8s | %-8s | %-8s | %-8s | %-8s | %-8s | %-8s%n",
                "Mode", "NDCG@5", "NDCG@10", "MAP", "MRR", "Mean(ms)", "P95(ms)", "P99(ms)");
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("%-14s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f%n",
                "BM25 Lexical", bQ.ndcgAt5(), bQ.ndcgAt10(), bQ.map(), bQ.mrr(), b.meanMs(), b.p95Ms(), b.p99Ms());
        System.out.printf("%-14s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f%n",
                "Dense Semantic", sQ.ndcgAt5(), sQ.ndcgAt10(), sQ.map(), sQ.mrr(), s.meanMs(), s.p95Ms(), s.p99Ms());
        System.out.printf("%-14s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-8.4f%n",
                "Hybrid (RRF)", hQ.ndcgAt5(), hQ.ndcgAt10(), hQ.map(), hQ.mrr(), h.meanMs(), h.p95Ms(), h.p99Ms());

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("PART 2: SUB-COMPONENT TIMING BREAKDOWN (HYBRID SEARCH EXECUTION):");
        System.out.printf("  1. Lexical BM25 (Posting traversal & scoring): %.4f ms (%.1f%%)%n",
                sub.bm25LookupMs(), (sub.bm25LookupMs() / sub.endToEndHybridMs()) * 100.0);
        System.out.printf("  2. Neural Query Inference (WordPiece + ONNX):  %.4f ms (%.1f%%)%n",
                sub.onnxInferenceMs(), (sub.onnxInferenceMs() / sub.endToEndHybridMs()) * 100.0);
        System.out.printf("  3. Vector Similarity Search (Exact Dot Product):%.4f ms (%.1f%%)%n",
                sub.vectorSearchMs(), (sub.vectorSearchMs() / sub.endToEndHybridMs()) * 100.0);
        System.out.printf("  4. Reciprocal Rank Fusion (Aggregation & Sort): %.4f ms (%.1f%%)%n",
                sub.rrfFusionMs(), (sub.rrfFusionMs() / sub.endToEndHybridMs()) * 100.0);
        System.out.printf("  >> Total End-to-End Hybrid Latency:            %.4f ms%n", sub.endToEndHybridMs());

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("PART 3: THROUGHPUT & MEMORY FOOTPRINT:");
        System.out.printf("  - BM25 Single-Thread Throughput:     %,.1f QPS%n", b.qps());
        System.out.printf("  - Semantic Single-Thread Throughput: %,.1f QPS%n", s.qps());
        System.out.printf("  - Hybrid Single-Thread Throughput:   %,.1f QPS%n", h.qps());
        System.out.printf("  - Inverted Index Memory Footprint:   %,d KB (est.)%n", mem.invertedIndexBytes() / 1024);
        System.out.printf("  - Vector Storage Memory Footprint:   %,d KB (est.)%n", mem.vectorStorageBytes() / 1024);
        System.out.println("=========================================================================================");
    }
}
