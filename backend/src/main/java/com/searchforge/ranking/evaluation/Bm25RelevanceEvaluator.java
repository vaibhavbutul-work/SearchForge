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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Automated evaluation harness for measuring pure BM25 ranking quality.
 *
 * Indexes the standardized 120-document {@link SearchForgeEvaluationCorpus} into an
 * in-memory inverted index, executes all 48 {@link SearchForgeEvaluationDataset} queries
 * through {@link DefaultRankedRetrievalEngine}, and computes Information Retrieval metrics
 * via {@link EvaluationMetricsAggregator}.
 */
@Service
public class Bm25RelevanceEvaluator {

    private static final Logger log = LoggerFactory.getLogger(Bm25RelevanceEvaluator.class);

    private final InMemoryInvertedIndex invertedIndex;
    private final Tokenizer tokenizer;
    private final RankedRetrievalEngine retrievalEngine;
    private final Bm25Config config;

    public Bm25RelevanceEvaluator() {
        this(Bm25Config.defaultConfig());
    }

    public Bm25RelevanceEvaluator(Bm25Config config) {
        this.config = Objects.requireNonNull(config, "Bm25Config cannot be null");
        this.invertedIndex = new InMemoryInvertedIndex();
        this.tokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());

        Bm25TermScorer termScorer = new Bm25TermScorer(this.config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        // Pure BM25 baseline without synonym expansion
        this.retrievalEngine = new DefaultRankedRetrievalEngine(this.invertedIndex, bm25Scorer, this.tokenizer);

        indexCorpus();
    }

    /**
     * Ingests and indexes all 120 documents from the standardized evaluation corpus.
     */
    private void indexCorpus() {
        List<CrawledDocument> documents = SearchForgeEvaluationCorpus.getCorpus();
        log.info("Indexing {} evaluation corpus documents into InMemoryInvertedIndex...", documents.size());

        for (CrawledDocument doc : documents) {
            String fullText = doc.getTitle() + " " + doc.getContent();
            List<ProcessedToken> tokens = tokenizer.tokenize(fullText);
            invertedIndex.addDocument(doc.getId(), tokens);
        }

        log.info("Evaluation corpus indexing complete. Total indexed documents: {}", invertedIndex.getDocumentCount());
    }

    /**
     * Executes baseline evaluation across all 48 queries in the dataset.
     */
    public Bm25EvaluationResult evaluateAll() {
        return evaluateQueries(SearchForgeEvaluationDataset.getAllQueries());
    }

    /**
     * Executes evaluation across a specific split (DEV, TUNING, or EVAL).
     */
    public Bm25EvaluationResult evaluateSplit(EvaluationDatasetSplit split) {
        return evaluateQueries(SearchForgeEvaluationDataset.getQueriesBySplit(split));
    }

    /**
     * Executes evaluation across a specific query category archetype.
     */
    public Bm25EvaluationResult evaluateCategory(EvaluationCategory category) {
        return evaluateQueries(SearchForgeEvaluationDataset.getQueriesByCategory(category));
    }

    /**
     * Executes evaluation for a custom list of queries.
     */
    public Bm25EvaluationResult evaluateQueries(List<EvaluationQueryV2> queries) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Queries cannot be null or empty");
        }

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetricsList = new ArrayList<>(queries.size());
        Map<String, List<Long>> queryHitsMap = new LinkedHashMap<>();

        for (EvaluationQueryV2 query : queries) {
            // Retrieve top-10 candidates
            List<RankedSearchResult> hits = retrievalEngine.search(query.queryText(), 10);
            List<Long> docIds = hits.stream().map(RankedSearchResult::docId).toList();
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

        return new Bm25EvaluationResult(
                config,
                overallSummary,
                categorySummary,
                splitSummary,
                queryMetricsList,
                queryHitsMap
        );
    }

    public Bm25Config getConfig() {
        return config;
    }

    public int getIndexedDocumentCount() {
        return (int) invertedIndex.getDocumentCount();
    }

    /**
     * Complete evaluation result bundle containing overall metrics, sliced summaries, and raw hits.
     */
    public record Bm25EvaluationResult(
            Bm25Config config,
            EvaluationMetricsAggregator.AggregateMetricsSummary overallSummary,
            Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> categorySummary,
            Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary,
            List<EvaluationMetricsAggregator.QueryEvaluationMetrics> queryMetrics,
            Map<String, List<Long>> queryHitsMap
    ) {}
}
