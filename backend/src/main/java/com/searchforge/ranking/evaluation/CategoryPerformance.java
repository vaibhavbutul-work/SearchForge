package com.searchforge.ranking.evaluation;

/**
 * Performance comparison for a specific query category across retrieval paradigms.
 *
 * @param category           the query category
 * @param queryCount         number of evaluated queries in this category
 * @param bm25Ndcg           mean NDCG@K for BM25
 * @param semanticNdcg       mean NDCG@K for dense semantic retrieval
 * @param hybridNdcg         mean NDCG@K for hybrid RRF retrieval
 * @param bm25Mrr            mean MRR for BM25
 * @param semanticMrr        mean MRR for dense semantic retrieval
 * @param hybridMrr          mean MRR for hybrid RRF retrieval
 * @param winningMode        retrieval mode achieving highest NDCG
 * @param analyticalTakeaway qualitative summary of why this mode succeeded or struggled
 */
public record CategoryPerformance(
        QueryCategory category,
        int queryCount,
        double bm25Ndcg,
        double semanticNdcg,
        double hybridNdcg,
        double bm25Mrr,
        double semanticMrr,
        double hybridMrr,
        String winningMode,
        String analyticalTakeaway
) {}
