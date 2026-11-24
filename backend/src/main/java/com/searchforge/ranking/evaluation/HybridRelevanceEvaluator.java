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
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.search.hybrid.DefaultHybridSearchService;
import com.searchforge.search.hybrid.HybridCandidate;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.HybridSearchService;
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
 * Automated evaluation harness for measuring multi-modal Hybrid Search retrieval quality.
 *
 * Simultaneously evaluates Lexical BM25, Dense Semantic Retrieval, and Reciprocal Rank Fusion (RRF)
 * against the standardized 120-document {@link SearchForgeEvaluationCorpus} and 48 {@link SearchForgeEvaluationDataset} queries.
 */
@Service
public class HybridRelevanceEvaluator {

    private static final Logger log = LoggerFactory.getLogger(HybridRelevanceEvaluator.class);

    private final InMemoryInvertedIndex invertedIndex;
    private final Tokenizer tokenizer;
    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final EmbeddingModel embeddingModel;
    private final InMemoryVectorStorage vectorStorage;
    private final ExactVectorSimilarityEngine vectorSimilarityEngine;
    private final ReciprocalRankFusionEngine rankFusionEngine;
    private final HybridSearchService hybridSearchService;

    public HybridRelevanceEvaluator() {
        this(SemanticRelevanceEvaluator.resolveDefaultEmbeddingModel(), 60);
    }

    public HybridRelevanceEvaluator(EmbeddingModel embeddingModel, int rrfK) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");

        // 1. Lexical Subsystem Setup
        this.invertedIndex = new InMemoryInvertedIndex();
        this.tokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());
        Bm25Config bm25Config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(bm25Config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);
        this.rankedRetrievalEngine = new DefaultRankedRetrievalEngine(this.invertedIndex, bm25Scorer, this.tokenizer);

        // 2. Semantic Subsystem Setup
        this.vectorStorage = new InMemoryVectorStorage(this.embeddingModel.dimensions());
        this.vectorSimilarityEngine = new ExactVectorSimilarityEngine(this.vectorStorage);

        // 3. Fusion & Hybrid Service Setup
        this.rankFusionEngine = new ReciprocalRankFusionEngine(rrfK > 0 ? rrfK : 60);
        this.hybridSearchService = new DefaultHybridSearchService(
                this.rankedRetrievalEngine,
                this.embeddingModel,
                this.vectorSimilarityEngine,
                this.rankFusionEngine,
                2,
                50
        );

        indexCorpus();
    }

    /**
     * Ingests all 120 documents into both inverted index and vector storage.
     */
    private void indexCorpus() {
        List<CrawledDocument> documents = SearchForgeEvaluationCorpus.getCorpus();
        log.info("Indexing {} documents into dual Lexical + Vector storage...", documents.size());

        for (CrawledDocument doc : documents) {
            String fullText = doc.getTitle() + " " + doc.getContent();
            // Inverted index
            List<ProcessedToken> tokens = tokenizer.tokenize(fullText);
            invertedIndex.addDocument(doc.getId(), tokens);

            // Vector store
            float[] vector = embeddingModel.embed(fullText);
            vectorStorage.put(new DocumentVector(doc.getId(), vector));
        }

        log.info("Dual indexing complete: {} lexical documents, {} semantic vectors.",
                invertedIndex.getDocumentCount(), vectorStorage.size());
    }

    /**
     * Executes 3-way evaluation across all 48 queries for BM25, Semantic, and Hybrid.
     */
    public ThreeWayEvaluationResult evaluateAll() {
        return evaluateQueries(SearchForgeEvaluationDataset.getAllQueries());
    }

    /**
     * Executes 3-way evaluation for a custom list of queries.
     */
    public ThreeWayEvaluationResult evaluateQueries(List<EvaluationQueryV2> queries) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Queries cannot be null or empty");
        }

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> bm25Metrics = new ArrayList<>(queries.size());
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> semanticMetrics = new ArrayList<>(queries.size());
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> hybridMetrics = new ArrayList<>(queries.size());

        Map<String, List<Long>> bm25Hits = new LinkedHashMap<>();
        Map<String, List<Long>> semanticHits = new LinkedHashMap<>();
        Map<String, List<Long>> hybridHits = new LinkedHashMap<>();

        for (EvaluationQueryV2 query : queries) {
            // 1. Lexical BM25
            HybridSearchResult lexRes = hybridSearchService.search(query.queryText(), SearchMode.LEXICAL, 10);
            List<Long> lexIds = lexRes.candidates().stream().map(HybridCandidate::docId).toList();
            bm25Hits.put(query.queryId(), lexIds);
            bm25Metrics.add(EvaluationMetricsAggregator.evaluateQuery(lexIds, query));

            // 2. Dense Semantic
            HybridSearchResult semRes = hybridSearchService.search(query.queryText(), SearchMode.SEMANTIC, 10);
            List<Long> semIds = semRes.candidates().stream().map(HybridCandidate::docId).toList();
            semanticHits.put(query.queryId(), semIds);
            semanticMetrics.add(EvaluationMetricsAggregator.evaluateQuery(semIds, query));

            // 3. Hybrid RRF
            HybridSearchResult hybRes = hybridSearchService.search(query.queryText(), SearchMode.HYBRID, 10);
            List<Long> hybIds = hybRes.candidates().stream().map(HybridCandidate::docId).toList();
            hybridHits.put(query.queryId(), hybIds);
            hybridMetrics.add(EvaluationMetricsAggregator.evaluateQuery(hybIds, query));
        }

        return new ThreeWayEvaluationResult(
                buildModeResult("BM25", bm25Metrics, bm25Hits),
                buildModeResult("SEMANTIC", semanticMetrics, semanticHits),
                buildModeResult("HYBRID", hybridMetrics, hybridHits)
        );
    }

    private ModeEvaluationResult buildModeResult(String modeName,
                                                 List<EvaluationMetricsAggregator.QueryEvaluationMetrics> metrics,
                                                 Map<String, List<Long>> hits) {
        return new ModeEvaluationResult(
                modeName,
                EvaluationMetricsAggregator.aggregate(metrics),
                EvaluationMetricsAggregator.aggregateByCategory(metrics),
                EvaluationMetricsAggregator.aggregateBySplit(metrics),
                metrics,
                hits
        );
    }

    public int getIndexedDocumentCount() {
        return (int) invertedIndex.getDocumentCount();
    }

    public int getRrfK() {
        return rankFusionEngine.getK();
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Complete evaluation result bundle across all three search modes.
     */
    public record ThreeWayEvaluationResult(
            ModeEvaluationResult bm25,
            ModeEvaluationResult semantic,
            ModeEvaluationResult hybrid
    ) {}

    /**
     * Per-mode evaluation summary.
     */
    public record ModeEvaluationResult(
            String modeName,
            EvaluationMetricsAggregator.AggregateMetricsSummary overallSummary,
            Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> categorySummary,
            Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary,
            List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetrics,
            Map<String, List<Long>> queryHitsMap
    ) {}
}
