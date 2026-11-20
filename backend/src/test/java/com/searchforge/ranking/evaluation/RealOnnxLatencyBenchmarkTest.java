package com.searchforge.ranking.evaluation;

import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.FusedDocumentResult;
import com.searchforge.search.fusion.RankFusionEngine;
import com.searchforge.search.fusion.RankedItem;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.OnnxEmbeddingModel;
import com.searchforge.semantic.tokenizer.TokenizationResult;
import com.searchforge.semantic.tokenizer.WordPieceTokenizer;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Real ONNX vs Deterministic Latency Benchmarking (Phase 7.1 Task 10)")
class RealOnnxLatencyBenchmarkTest {

    private static final String DEFAULT_ONNX_CACHE = System.getProperty("user.home") + "/.cache/searchforge/all-MiniLM-L6-v2.onnx";

    @Test
    @DisplayName("Execute real ONNX vs Deterministic comparative latency benchmark")
    void executeComparativeLatencyBenchmark() {
        File onnxFile = new File(DEFAULT_ONNX_CACHE);
        boolean hasRealOnnx = onnxFile.exists() && onnxFile.isFile();

        System.out.println("=========================================================================================");
        System.out.println("PHASE 7.1 REAL INFERENCE & LATENCY BENCHMARK AUDIT");
        System.out.println("  ONNX Model Asset Present: " + hasRealOnnx + " (" + DEFAULT_ONNX_CACHE + ")");
        System.out.println("=========================================================================================");

        // 1. Setup Corpus and Lexical Index
        InMemoryInvertedIndex index = new InMemoryInvertedIndex();
        Tokenizer lexicalTokenizer = new Tokenizer(new com.searchforge.indexer.token.StopWordFilter(), new PorterStemmer());
        Bm25Config config = Bm25Config.defaultConfig();
        Bm25Scorer bm25Scorer = new Bm25Scorer(new Bm25TermScorer(config), new Bm25IdfCalculator());
        RankedRetrievalEngine lexicalEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, lexicalTokenizer);

        // Seed 24 documents
        List<CategorizedEvaluationQuery> queries = BenchmarkDataset.getStandardDataset();
        for (long id = 1; id <= 24; id++) {
            index.addDocument(id, lexicalTokenizer.tokenize("document " + id + " content about computer science and technical concepts"));
        }

        // 2. Deterministic Hash Benchmark
        DeterministicEmbeddingModel deterministicModel = new DeterministicEmbeddingModel(384, 256);
        InMemoryVectorStorage detStorage = new InMemoryVectorStorage(384);
        for (long id = 1; id <= 24; id++) {
            detStorage.put(new DocumentVector(id, deterministicModel.embed("document " + id + " content")));
        }
        ExactVectorSimilarityEngine detVectorEngine = new ExactVectorSimilarityEngine(detStorage);
        ReciprocalRankFusionEngine rrfEngine = new ReciprocalRankFusionEngine(60);

        LatencyStats detStats = measureEngineLatency(queries, deterministicModel, null, lexicalEngine, detVectorEngine, rrfEngine, 50);

        System.out.println("\n[1] DETERMINISTIC HASH RETRIEVAL PROFILE (Test-Mode Baseline):");
        System.out.printf("  - Query Embedding:       %.4f ms (Mean), %.4f ms (P95)%n", detStats.meanInferenceMs, detStats.p95InferenceMs);
        System.out.printf("  - Lexical BM25:          %.4f ms (Mean)%n", detStats.meanBm25Ms);
        System.out.printf("  - Vector Search (24-D):  %.4f ms (Mean)%n", detStats.meanVectorSearchMs);
        System.out.printf("  - RRF Fusion (k=60):     %.4f ms (Mean)%n", detStats.meanFusionMs);
        System.out.printf("  - Total Hybrid Search:   %.4f ms (Mean), %.4f ms (P95)%n", detStats.meanTotalMs, detStats.p95TotalMs);

        // 3. Real ONNX Transformer Benchmark (if model available)
        if (hasRealOnnx) {
            long t0Init = System.nanoTime();
            WordPieceTokenizer wpTokenizer = new WordPieceTokenizer("default");
            OnnxEmbeddingModel onnxModel = new OnnxEmbeddingModel(onnxFile.getAbsolutePath(), wpTokenizer, 384, 256);
            long onnxInitMs = (System.nanoTime() - t0Init) / 1_000_000;

            InMemoryVectorStorage onnxStorage = new InMemoryVectorStorage(384);
            for (long id = 1; id <= 24; id++) {
                onnxStorage.put(new DocumentVector(id, onnxModel.embed("document " + id + " content")));
            }
            ExactVectorSimilarityEngine onnxVectorEngine = new ExactVectorSimilarityEngine(onnxStorage);

            // Cold start inference
            long tCold = System.nanoTime();
            float[] coldVec = onnxModel.embed("java virtual threads concurrency");
            long coldInferenceMs = (System.nanoTime() - tCold) / 1_000_000;
            assertNotNull(coldVec);
            assertTrue(coldVec.length == 384);

            LatencyStats onnxStats = measureEngineLatency(queries, onnxModel, wpTokenizer, lexicalEngine, onnxVectorEngine, rrfEngine, 50);

            System.out.println("\n[2] REAL ONNX RUNTIME TRANSFORMER PROFILE (all-MiniLM-L6-v2, Dynamic SeqLen):");
            System.out.printf("  - Model Session Init:    %d ms (Cold start)%n", onnxInitMs);
            System.out.printf("  - Cold-Start Inference:  %d ms (First query)%n", coldInferenceMs);
            System.out.printf("  - WordPiece Tokenize:    %.4f ms (Mean)%n", onnxStats.meanTokenizeMs);
            System.out.printf("  - Dynamic ONNX Infer:    %.4f ms (Mean), %.4f ms (P50), %.4f ms (P95), %.4f ms (P99)%n",
                    onnxStats.meanInferenceMs, onnxStats.p50InferenceMs, onnxStats.p95InferenceMs, onnxStats.p99InferenceMs);
            System.out.printf("  - Lexical BM25:          %.4f ms (Mean)%n", onnxStats.meanBm25Ms);
            System.out.printf("  - Vector Search (24-D):  %.4f ms (Mean)%n", onnxStats.meanVectorSearchMs);
            System.out.printf("  - RRF Fusion (k=60):     %.4f ms (Mean)%n", onnxStats.meanFusionMs);
            System.out.printf("  - Total Hybrid Search:   %.4f ms (Mean), %.4f ms (P95)%n", onnxStats.meanTotalMs, onnxStats.p95TotalMs);

            onnxModel.close();
        } else {
            System.out.println("\n[2] REAL ONNX RUNTIME: Skipped (model asset not present at " + DEFAULT_ONNX_CACHE + ")");
        }
        System.out.println("=========================================================================================\n");
    }

    private LatencyStats measureEngineLatency(
            List<CategorizedEvaluationQuery> queries,
            com.searchforge.semantic.embedding.EmbeddingModel model,
            WordPieceTokenizer wpTokenizer,
            RankedRetrievalEngine lexicalEngine,
            ExactVectorSimilarityEngine vectorEngine,
            RankFusionEngine fusionEngine,
            int runs) {

        List<Double> tokenizeList = new ArrayList<>();
        List<Double> inferList = new ArrayList<>();
        List<Double> bm25List = new ArrayList<>();
        List<Double> vectorList = new ArrayList<>();
        List<Double> fusionList = new ArrayList<>();
        List<Double> totalList = new ArrayList<>();

        int poolSize = 50;

        for (int r = 0; r < runs; r++) {
            for (CategorizedEvaluationQuery eq : queries) {
                String q = eq.query();
                long tTotalStart = System.nanoTime();

                // Tokenization (if WordPiece)
                if (wpTokenizer != null) {
                    long tTok = System.nanoTime();
                    wpTokenizer.tokenize(q, 256);
                    tokenizeList.add((System.nanoTime() - tTok) / 1_000_000.0);
                }

                // Query embedding
                long tInfer = System.nanoTime();
                float[] qVec = model.embed(q);
                inferList.add((System.nanoTime() - tInfer) / 1_000_000.0);

                // BM25
                long tBm25 = System.nanoTime();
                List<RankedSearchResult> lexHits = lexicalEngine.search(q, poolSize);
                bm25List.add((System.nanoTime() - tBm25) / 1_000_000.0);

                // Vector search
                long tVec = System.nanoTime();
                List<VectorSearchResult> vecHits = vectorEngine.search(qVec, poolSize);
                vectorList.add((System.nanoTime() - tVec) / 1_000_000.0);

                // Fusion
                long tFuse = System.nanoTime();
                List<RankedItem> lexItems = new ArrayList<>(lexHits.size());
                for (int i = 0; i < lexHits.size(); i++) {
                    lexItems.add(new RankedItem(lexHits.get(i).docId(), i + 1, lexHits.get(i).score(), "bm25"));
                }
                List<RankedItem> vecItems = new ArrayList<>(vecHits.size());
                for (int i = 0; i < vecHits.size(); i++) {
                    vecItems.add(new RankedItem(vecHits.get(i).documentId(), i + 1, vecHits.get(i).similarityScore(), "semantic"));
                }
                fusionEngine.fuse(Map.of("bm25", lexItems, "semantic", vecItems), 10);
                fusionList.add((System.nanoTime() - tFuse) / 1_000_000.0);

                totalList.add((System.nanoTime() - tTotalStart) / 1_000_000.0);
            }
        }

        return new LatencyStats(
                avg(tokenizeList),
                avg(inferList), percentile(inferList, 0.50), percentile(inferList, 0.95), percentile(inferList, 0.99),
                avg(bm25List),
                avg(vectorList),
                avg(fusionList),
                avg(totalList), percentile(totalList, 0.95)
        );
    }

    private static double avg(List<Double> list) {
        return list.isEmpty() ? 0.0 : list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double percentile(List<Double> list, double p) {
        if (list.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int idx = (int) Math.min(sorted.size() - 1, Math.max(0, p * sorted.size()));
        return sorted.get(idx);
    }

    private record LatencyStats(
            double meanTokenizeMs,
            double meanInferenceMs, double p50InferenceMs, double p95InferenceMs, double p99InferenceMs,
            double meanBm25Ms,
            double meanVectorSearchMs,
            double meanFusionMs,
            double meanTotalMs, double p95TotalMs
    ) {}
}
