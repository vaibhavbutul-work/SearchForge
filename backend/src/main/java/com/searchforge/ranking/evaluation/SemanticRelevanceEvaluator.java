package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.embedding.OnnxEmbeddingModel;
import com.searchforge.semantic.tokenizer.WordPieceTokenizer;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Automated evaluation harness for measuring dense semantic search retrieval quality.
 *
 * Vectorizes the standardized 120-document {@link SearchForgeEvaluationCorpus} into an
 * in-memory vector store, executes all 48 {@link SearchForgeEvaluationDataset} queries
 * through {@link ExactVectorSimilarityEngine}, and computes Information Retrieval metrics
 * via {@link EvaluationMetricsAggregator}.
 */
@Service
public class SemanticRelevanceEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SemanticRelevanceEvaluator.class);
    private static final String DEFAULT_ONNX_CACHE_PATH =
            System.getProperty("user.home") + File.separator + ".cache" + File.separator + "searchforge" + File.separator + "all-MiniLM-L6-v2.onnx";

    private final EmbeddingModel embeddingModel;
    private final InMemoryVectorStorage vectorStorage;
    private final ExactVectorSimilarityEngine similarityEngine;

    public SemanticRelevanceEvaluator() {
        this(resolveDefaultEmbeddingModel());
    }

    public SemanticRelevanceEvaluator(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.vectorStorage = new InMemoryVectorStorage(this.embeddingModel.dimensions());
        this.similarityEngine = new ExactVectorSimilarityEngine(this.vectorStorage);

        indexCorpus();
    }

    /**
     * Resolves the primary embedding model: prefers local ONNX all-MiniLM-L6-v2 if present,
     * falling back to DeterministicEmbeddingModel for lightweight hermetic test environments.
     */
    public static EmbeddingModel resolveDefaultEmbeddingModel() {
        File onnxFile = new File(DEFAULT_ONNX_CACHE_PATH);
        if (onnxFile.exists() && onnxFile.isFile()) {
            try {
                log.info("SemanticRelevanceEvaluator: Initializing ONNX embedding model from {}", DEFAULT_ONNX_CACHE_PATH);
                return new OnnxEmbeddingModel(
                        DEFAULT_ONNX_CACHE_PATH,
                        new WordPieceTokenizer("default"),
                        384,
                        256
                );
            } catch (Exception e) {
                log.warn("Failed to load ONNX model from cache ({}). Falling back to DeterministicEmbeddingModel.", e.getMessage());
            }
        }
        log.info("SemanticRelevanceEvaluator: Using DeterministicEmbeddingModel (384-D)");
        return new DeterministicEmbeddingModel();
    }

    /**
     * Ingests and vectorizes all 120 documents from the evaluation corpus.
     */
    private void indexCorpus() {
        List<CrawledDocument> documents = SearchForgeEvaluationCorpus.getCorpus();
        log.info("Vectorizing {} evaluation corpus documents using model '{}'...", documents.size(), embeddingModel.modelName());

        for (CrawledDocument doc : documents) {
            String text = doc.getTitle() + ". " + doc.getContent();
            float[] vector = embeddingModel.embed(text);
            vectorStorage.put(new DocumentVector(doc.getId(), vector));
        }

        log.info("Evaluation corpus vectorization complete. Total stored vectors: {}", vectorStorage.size());
    }

    /**
     * Executes baseline semantic evaluation across all 48 queries in the dataset.
     */
    public SemanticEvaluationResult evaluateAll() {
        return evaluateQueries(SearchForgeEvaluationDataset.getAllQueries());
    }

    /**
     * Executes semantic evaluation across a specific split (DEV, TUNING, or EVAL).
     */
    public SemanticEvaluationResult evaluateSplit(EvaluationDatasetSplit split) {
        return evaluateQueries(SearchForgeEvaluationDataset.getQueriesBySplit(split));
    }

    /**
     * Executes semantic evaluation across a specific query category archetype.
     */
    public SemanticEvaluationResult evaluateCategory(EvaluationCategory category) {
        return evaluateQueries(SearchForgeEvaluationDataset.getQueriesByCategory(category));
    }

    /**
     * Executes semantic evaluation for a custom list of queries.
     */
    public SemanticEvaluationResult evaluateQueries(List<EvaluationQueryV2> queries) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Queries cannot be null or empty");
        }

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetricsList = new ArrayList<>(queries.size());
        Map<String, List<Long>> queryHitsMap = new LinkedHashMap<>();

        for (EvaluationQueryV2 query : queries) {
            // Vectorize query and retrieve top-10 candidates
            float[] queryVector = embeddingModel.embed(query.queryText());
            List<VectorSearchResult> hits = similarityEngine.search(queryVector, 10);
            List<Long> docIds = hits.stream().map(VectorSearchResult::documentId).toList();
            queryHitsMap.put(query.queryId(), docIds);

            EvaluationMetricsAggregator.QueryEvaluationMetrics metrics =
                    EvaluationMetricsAggregator.evaluateQuery(docIds, query);
            queryMetricsList.add(metrics);
        }

        EvaluationMetricsAggregator.AggregateMetricsSummary overallSummary =
                EvaluationMetricsAggregator.aggregate(queryMetricsList);
        Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> categorySummary =
                EvaluationMetricsAggregator.aggregateByCategory(queryMetricsList);
        Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary =
                EvaluationMetricsAggregator.aggregateBySplit(queryMetricsList);

        return new SemanticEvaluationResult(
                embeddingModel.modelName(),
                embeddingModel.dimensions(),
                overallSummary,
                categorySummary,
                splitSummary,
                queryMetricsList,
                queryHitsMap
        );
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public int getIndexedDocumentCount() {
        return vectorStorage.size();
    }

    /**
     * Complete semantic evaluation result bundle containing overall metrics, sliced summaries, and raw hits.
     */
    public record SemanticEvaluationResult(
            String modelName,
            int dimensions,
            EvaluationMetricsAggregator.AggregateMetricsSummary overallSummary,
            Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> categorySummary,
            Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary,
            List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetrics,
            Map<String, List<Long>> queryHitsMap
    ) {}
}
