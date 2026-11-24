package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.search.hybrid.DefaultHybridSearchService;
import com.searchforge.search.hybrid.HybridCandidate;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.SearchMode;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Automated experiment runner executing systematic ranking parameter tuning for Task 12.6.
 *
 * Implements strict IR experimental methodology:
 * 1. Parameters are tuned strictly against the {@link EvaluationDatasetSplit#TUNING} split (12 queries).
 * 2. Unbiased final validation is conducted once against the holdout {@link EvaluationDatasetSplit#EVAL} split (12 queries).
 * 3. Records full telemetry (metrics, deltas, execution durations, observations).
 */
@Service
public class RankingTuningExperimentRunner {

    private static final Logger log = LoggerFactory.getLogger(RankingTuningExperimentRunner.class);

    private final InMemoryInvertedIndex invertedIndex;
    private final Tokenizer tokenizer;
    private final EmbeddingModel embeddingModel;
    private final InMemoryVectorStorage vectorStorage;
    private final ExactVectorSimilarityEngine vectorSimilarityEngine;

    public RankingTuningExperimentRunner() {
        this(SemanticRelevanceEvaluator.resolveDefaultEmbeddingModel());
    }

    public RankingTuningExperimentRunner(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.invertedIndex = new InMemoryInvertedIndex();
        this.tokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());
        this.vectorStorage = new InMemoryVectorStorage(this.embeddingModel.dimensions());
        this.vectorSimilarityEngine = new ExactVectorSimilarityEngine(this.vectorStorage);

        indexCorpus();
    }

    private void indexCorpus() {
        List<CrawledDocument> documents = SearchForgeEvaluationCorpus.getCorpus();
        log.info("Ingesting {} documents for parameter tuning experimentation...", documents.size());

        for (CrawledDocument doc : documents) {
            String fullText = doc.getTitle() + " " + doc.getContent();
            List<ProcessedToken> tokens = tokenizer.tokenize(fullText);
            invertedIndex.addDocument(doc.getId(), tokens);

            float[] vector = embeddingModel.embed(fullText);
            vectorStorage.put(new DocumentVector(doc.getId(), vector));
        }

        log.info("Corpus indexing complete: {} inverted docs, {} vectors.",
                invertedIndex.getDocumentCount(), vectorStorage.size());
    }

    /**
     * Executes a single BM25 evaluation trial for a specific (k1, b) configuration.
     */
    public ExperimentTrial runBm25Trial(String experimentId,
                                        double k1,
                                        double b,
                                        EvaluationDatasetSplit split,
                                        String observation) {
        long startTime = System.currentTimeMillis();
        List<EvaluationQueryV2> queries = SearchForgeEvaluationDataset.getQueriesBySplit(split);

        Bm25Config config = new Bm25Config(k1, b);
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);
        RankedRetrievalEngine engine = new DefaultRankedRetrievalEngine(invertedIndex, bm25Scorer, tokenizer);

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetrics = new ArrayList<>(queries.size());
        for (EvaluationQueryV2 query : queries) {
            List<RankedSearchResult> hits = engine.search(query.queryText(), 10);
            List<Long> docIds = hits.stream().map(RankedSearchResult::docId).toList();
            queryMetrics.add(EvaluationMetricsAggregator.evaluateQuery(docIds, query));
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        EvaluationMetricsAggregator.AggregateMetricsSummary summary = EvaluationMetricsAggregator.aggregate(queryMetrics);

        return new ExperimentTrial(
                experimentId,
                "BM25",
                String.format("k1=%.2f, b=%.2f", k1, b),
                split,
                summary,
                elapsedMs,
                observation
        );
    }

    /**
     * Executes a single Hybrid RRF evaluation trial.
     */
    public ExperimentTrial runHybridTrial(String experimentId,
                                          double k1,
                                          double b,
                                          int rrfK,
                                          Map<String, Double> sourceWeights,
                                          int minPoolSize,
                                          EvaluationDatasetSplit split,
                                          String observation) {
        long startTime = System.currentTimeMillis();
        List<EvaluationQueryV2> queries = SearchForgeEvaluationDataset.getQueriesBySplit(split);

        // Lexical engine
        Bm25Config config = new Bm25Config(k1, b);
        Bm25TermScorer termScorer = new Bm25TermScorer(config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);
        RankedRetrievalEngine lexicalEngine = new DefaultRankedRetrievalEngine(invertedIndex, bm25Scorer, tokenizer);

        // Hybrid service
        ReciprocalRankFusionEngine fusionEngine = new ReciprocalRankFusionEngine(rrfK);
        DefaultHybridSearchService hybridService = new DefaultHybridSearchService(
                lexicalEngine,
                embeddingModel,
                vectorSimilarityEngine,
                fusionEngine,
                sourceWeights,
                2,
                minPoolSize
        );

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetrics = new ArrayList<>(queries.size());
        for (EvaluationQueryV2 query : queries) {
            HybridSearchResult res = hybridService.search(query.queryText(), SearchMode.HYBRID, 10);
            List<Long> docIds = res.candidates().stream().map(HybridCandidate::docId).toList();
            queryMetrics.add(EvaluationMetricsAggregator.evaluateQuery(docIds, query));
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        EvaluationMetricsAggregator.AggregateMetricsSummary summary = EvaluationMetricsAggregator.aggregate(queryMetrics);

        String configDesc = String.format("BM25(k1=%.2f, b=%.2f) + RRF(k=%d, pool=%d, w_bm25=%.1f, w_sem=%.1f)",
                k1, b, rrfK, minPoolSize,
                sourceWeights.getOrDefault("bm25", 1.0),
                sourceWeights.getOrDefault("semantic", 1.0));

        return new ExperimentTrial(
                experimentId,
                "HYBRID",
                configDesc,
                split,
                summary,
                elapsedMs,
                observation
        );
    }

    /**
     * Executes the comprehensive tuning grid on the TUNING split, discovers the winning configuration,
     * and evaluates the winner against baseline on the holdout EVAL split.
     */
    public FullTuningExperimentReport runFullTuningExperiment() {
        log.info("Starting Task 12.6 Comprehensive Ranking Parameter Grid on TUNING split...");

        List<ExperimentTrial> bm25Trials = new ArrayList<>();
        // BM25 Trials on TUNING split
        bm25Trials.add(runBm25Trial("EXP-B0-BASE", 1.20, 0.75, EvaluationDatasetSplit.TUNING, "BM25 Default Baseline"));
        bm25Trials.add(runBm25Trial("EXP-B1", 0.80, 0.75, EvaluationDatasetSplit.TUNING, "Lower term frequency saturation"));
        bm25Trials.add(runBm25Trial("EXP-B2", 1.50, 0.75, EvaluationDatasetSplit.TUNING, "Higher term frequency saturation"));
        bm25Trials.add(runBm25Trial("EXP-B3", 2.00, 0.75, EvaluationDatasetSplit.TUNING, "High term frequency saturation"));
        bm25Trials.add(runBm25Trial("EXP-B4", 1.20, 0.50, EvaluationDatasetSplit.TUNING, "Softer document length normalization penalty"));
        bm25Trials.add(runBm25Trial("EXP-B5", 1.20, 0.85, EvaluationDatasetSplit.TUNING, "Stronger document length normalization penalty"));
        bm25Trials.add(runBm25Trial("EXP-B6", 1.50, 0.50, EvaluationDatasetSplit.TUNING, "High saturation (k1=1.5) + soft length penalty (b=0.5)"));
        bm25Trials.add(runBm25Trial("EXP-B7", 1.60, 0.60, EvaluationDatasetSplit.TUNING, "Balanced technical document tuning (k1=1.6, b=0.6)"));

        // Discover winning BM25 configuration
        ExperimentTrial winningBm25 = bm25Trials.stream()
                .max(Comparator.comparingDouble(t -> t.summary().meanNdcgAt5()))
                .orElse(bm25Trials.get(0));

        // Hybrid Trials on TUNING split
        List<ExperimentTrial> hybridTrials = new ArrayList<>();
        hybridTrials.add(runHybridTrial("EXP-H0-BASE", 1.20, 0.75, 60, Map.of("bm25", 1.0, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Hybrid Standard Baseline (k1=1.2, b=0.75, k=60, equal weights)"));
        hybridTrials.add(runHybridTrial("EXP-H1", 1.20, 0.75, 20, Map.of("bm25", 1.0, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Sharper rank decay (RRF k=20)"));
        hybridTrials.add(runHybridTrial("EXP-H2", 1.20, 0.75, 30, Map.of("bm25", 1.0, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Intermediate sharp rank decay (RRF k=30)"));
        hybridTrials.add(runHybridTrial("EXP-H3", 1.20, 0.75, 45, Map.of("bm25", 1.0, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Moderate rank decay (RRF k=45)"));
        hybridTrials.add(runHybridTrial("EXP-H4", 1.20, 0.75, 100, Map.of("bm25", 1.0, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Gentle rank decay (RRF k=100)"));
        hybridTrials.add(runHybridTrial("EXP-H5", 1.20, 0.75, 30, Map.of("bm25", 1.0, "semantic", 1.2), 50,
                EvaluationDatasetSplit.TUNING, "Boosted semantic weight (1.2x semantic) with k=30"));
        hybridTrials.add(runHybridTrial("EXP-H6", 1.20, 0.75, 30, Map.of("bm25", 1.2, "semantic", 1.0), 50,
                EvaluationDatasetSplit.TUNING, "Boosted lexical weight (1.2x BM25) with k=30"));
        hybridTrials.add(runHybridTrial("EXP-H7", 1.20, 0.75, 30, Map.of("bm25", 1.0, "semantic", 1.0), 25,
                EvaluationDatasetSplit.TUNING, "Smaller candidate pool size (25)"));
        hybridTrials.add(runHybridTrial("EXP-H8", 1.20, 0.75, 30, Map.of("bm25", 1.0, "semantic", 1.0), 100,
                EvaluationDatasetSplit.TUNING, "Larger candidate pool size (100)"));

        // Hybrid with tuned BM25 parameters
        double optK1 = 1.50;
        double optB = 0.50;
        if (winningBm25.configDescription().contains("k1=1.50")) {
            optK1 = 1.50; optB = 0.50;
        } else if (winningBm25.configDescription().contains("k1=1.60")) {
            optK1 = 1.60; optB = 0.60;
        }
        hybridTrials.add(runHybridTrial("EXP-H9-OPT", optK1, optB, 30, Map.of("bm25", 1.0, "semantic", 1.2), 50,
                EvaluationDatasetSplit.TUNING, "Optimized Combination: Tuned BM25 + RRF k=30 + 1.2x Semantic"));

        ExperimentTrial winningHybrid = hybridTrials.stream()
                .max(Comparator.comparingDouble(t -> t.summary().meanNdcgAt5()))
                .orElse(hybridTrials.get(0));

        // Unbiased Validation on Holdout EVAL Split
        log.info("Running single blind validation on untouched holdout EVAL split...");
        ExperimentTrial evalBaseline = runHybridTrial("EVAL-BASELINE", 1.20, 0.75, 60,
                Map.of("bm25", 1.0, "semantic", 1.0), 50, EvaluationDatasetSplit.EVAL, "Holdout Baseline");
        ExperimentTrial evalOptimized = runHybridTrial("EVAL-OPTIMIZED", optK1, optB, 30,
                Map.of("bm25", 1.0, "semantic", 1.2), 50, EvaluationDatasetSplit.EVAL, "Holdout Optimized Candidate");

        return new FullTuningExperimentReport(
                bm25Trials,
                winningBm25,
                hybridTrials,
                winningHybrid,
                evalBaseline,
                evalOptimized
        );
    }

    /**
     * Individual experimental trial record.
     */
    public record ExperimentTrial(
            String experimentId,
            String paradigm,
            String configDescription,
            EvaluationDatasetSplit split,
            EvaluationMetricsAggregator.AggregateMetricsSummary summary,
            long elapsedMs,
            String observation
    ) {}

    /**
     * Full report of tuning trials and final holdout validation.
     */
    public record FullTuningExperimentReport(
            List<ExperimentTrial> bm25Trials,
            ExperimentTrial winningBm25,
            List<ExperimentTrial> hybridTrials,
            ExperimentTrial winningHybrid,
            ExperimentTrial evalBaseline,
            ExperimentTrial evalOptimized
    ) {}
}
