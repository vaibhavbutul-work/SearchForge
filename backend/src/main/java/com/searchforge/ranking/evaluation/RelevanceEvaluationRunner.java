package com.searchforge.ranking.evaluation;

import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service orchestrating batch Information Retrieval evaluation against benchmark queries and ground-truth judgments.
 */
@Component
public class RelevanceEvaluationRunner {

    /**
     * Evaluates a single query against the provided search engine.
     */
    public QueryEvaluationResult evaluateQuery(RankedRetrievalEngine engine, EvaluationQuery query, int k) {
        Objects.requireNonNull(engine, "RankedRetrievalEngine cannot be null");
        Objects.requireNonNull(query, "EvaluationQuery cannot be null");
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }

        List<RankedSearchResult> hits = engine.search(query.query(), k);
        List<Long> retrievedDocIds = hits.stream().map(RankedSearchResult::docId).toList();
        return evaluateHits(retrievedDocIds, query, k);
    }

    /**
     * Evaluates a pre-computed list of retrieved document IDs against ground truth.
     */
    public QueryEvaluationResult evaluateHits(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        Objects.requireNonNull(query, "EvaluationQuery cannot be null");
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }

        List<Long> docIds = retrievedDocIds != null ? retrievedDocIds : List.of();
        double pAtK = RelevanceMetricsCalculator.precisionAtK(docIds, query, k);
        double rAtK = RelevanceMetricsCalculator.recallAtK(docIds, query, k);
        double rr = RelevanceMetricsCalculator.reciprocalRank(docIds, query);
        double ndcg = RelevanceMetricsCalculator.ndcgAtK(docIds, query, k);
        int relevantRetrieved = (int) docIds.stream().limit(k).filter(query::isRelevant).count();

        return new QueryEvaluationResult(
                query.query(),
                k,
                pAtK,
                rAtK,
                rr,
                ndcg,
                docIds.size(),
                relevantRetrieved
        );
    }

    /**
     * Executes evaluation across all benchmark queries using pre-computed hit lists.
     */
    public EvaluationReport evaluateAllHits(Map<EvaluationQuery, List<Long>> queryHitsMap, int k) {
        if (queryHitsMap == null || queryHitsMap.isEmpty()) {
            return new EvaluationReport(0, k, 0.0, 0.0, 0.0, 0.0, List.of());
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }

        List<QueryEvaluationResult> results = new ArrayList<>(queryHitsMap.size());
        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalRR = 0.0;
        double totalNdcg = 0.0;

        for (Map.Entry<EvaluationQuery, List<Long>> entry : queryHitsMap.entrySet()) {
            QueryEvaluationResult res = evaluateHits(entry.getValue(), entry.getKey(), k);
            results.add(res);
            totalPrecision += res.precisionAtK();
            totalRecall += res.recallAtK();
            totalRR += res.reciprocalRank();
            totalNdcg += res.ndcgAtK();
        }

        int n = queryHitsMap.size();
        return new EvaluationReport(
                n,
                k,
                totalPrecision / n,
                totalRecall / n,
                totalRR / n,
                totalNdcg / n,
                results
        );
    }

    /**
     * Executes evaluation across all benchmark queries and aggregates corpus-wide mean metrics.
     */
    public EvaluationReport evaluateAll(RankedRetrievalEngine engine, List<EvaluationQuery> queries, int k) {
        Objects.requireNonNull(engine, "RankedRetrievalEngine cannot be null");
        if (queries == null || queries.isEmpty()) {
            return new EvaluationReport(0, k, 0.0, 0.0, 0.0, 0.0, List.of());
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }

        List<QueryEvaluationResult> results = new ArrayList<>();
        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalRR = 0.0;
        double totalNdcg = 0.0;

        for (EvaluationQuery query : queries) {
            QueryEvaluationResult res = evaluateQuery(engine, query, k);
            results.add(res);
            totalPrecision += res.precisionAtK();
            totalRecall += res.recallAtK();
            totalRR += res.reciprocalRank();
            totalNdcg += res.ndcgAtK();
        }

        int n = queries.size();
        return new EvaluationReport(
                n,
                k,
                totalPrecision / n,
                totalRecall / n,
                totalRR / n,
                totalNdcg / n,
                results
        );
    }
}
