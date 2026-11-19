package com.searchforge.ranking.evaluation;

import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

        double pAtK = RelevanceMetricsCalculator.precisionAtK(retrievedDocIds, query, k);
        double rAtK = RelevanceMetricsCalculator.recallAtK(retrievedDocIds, query, k);
        double rr = RelevanceMetricsCalculator.reciprocalRank(retrievedDocIds, query);
        double ndcg = RelevanceMetricsCalculator.ndcgAtK(retrievedDocIds, query, k);
        int relevantRetrieved = (int) retrievedDocIds.stream().limit(k).filter(query::isRelevant).count();

        return new QueryEvaluationResult(
                query.query(),
                k,
                pAtK,
                rAtK,
                rr,
                ndcg,
                retrievedDocIds.size(),
                relevantRetrieved
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
