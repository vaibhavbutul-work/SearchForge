package com.searchforge.search.fusion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of Reciprocal Rank Fusion (RRF).
 *
 * Combines independent ranking lists (e.g. Lexical BM25 and Dense Semantic Vectors)
 * without requiring score calibration or normalization:
 *
 * <pre>
 *   RRF(d) = sum_{r in R} [ 1 / (k + rank_r(d)) ]
 * </pre>
 *
 * Uses the Cormack et al. (SIGIR 2009) standard constant k = 60 by default,
 * ensuring consensus across multiple retrieval engines while mitigating outlier bias.
 */
@Service
public class ReciprocalRankFusionEngine implements RankFusionEngine {

    private static final Logger log = LoggerFactory.getLogger(ReciprocalRankFusionEngine.class);
    public static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusionEngine() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusionEngine(@Value("${search.fusion.rrf-constant:60}") int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("RRF constant k must be positive, got: " + k);
        }
        this.k = k;
    }

    @Override
    public List<FusedDocumentResult> fuse(Map<String, List<RankedItem>> rankedLists, int topK) {
        return fuse(rankedLists, Map.of(), topK);
    }

    @Override
    public List<FusedDocumentResult> fuse(Map<String, List<RankedItem>> rankedLists, Map<String, Double> sourceWeights, int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive, got: " + topK);
        }
        if (rankedLists == null || rankedLists.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Accumulator> accumulators = new HashMap<>();

        for (Map.Entry<String, List<RankedItem>> entry : rankedLists.entrySet()) {
            String source = entry.getKey() != null ? entry.getKey() : "unknown";
            List<RankedItem> items = entry.getValue();
            if (items == null || items.isEmpty()) {
                continue;
            }

            double sourceMultiplier = (sourceWeights != null && sourceWeights.containsKey(source))
                    ? sourceWeights.get(source) : 1.0;

            Set<Long> seenInThisList = new HashSet<>();

            for (RankedItem item : items) {
                if (item == null) {
                    continue;
                }
                long docId = item.documentId();
                if (seenInThisList.contains(docId)) {
                    // Prevent duplicate score inflation from duplicate document IDs in the same ranking list
                    continue;
                }
                seenInThisList.add(docId);

                int rank = item.rank();
                double contribution = sourceMultiplier * (1.0 / (k + rank));

                Accumulator acc = accumulators.computeIfAbsent(docId, id -> new Accumulator(id));
                acc.totalRrf += contribution;
                acc.sourceRanks.put(source, rank);
                acc.sourceScores.put(source, item.originalScore());
                acc.rrfContributions.put(source, contribution);
            }
        }

        if (accumulators.isEmpty()) {
            return Collections.emptyList();
        }

        // Convert accumulators to FusedDocumentResult records
        List<FusedDocumentResult> candidates = new ArrayList<>(accumulators.size());
        for (Accumulator acc : accumulators.values()) {
            candidates.add(new FusedDocumentResult(
                    acc.documentId,
                    acc.totalRrf,
                    0,
                    acc.sourceRanks,
                    acc.sourceScores,
                    acc.rrfContributions
            ));
        }

        // Sort descending by RRF score, breaking ties by documentId ASC
        candidates.sort(FusedDocumentResult.COMPARATOR);

        // Truncate to topK and assign 1-based ranks
        int resultCount = Math.min(candidates.size(), topK);
        List<FusedDocumentResult> finalResults = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            finalResults.add(candidates.get(i).withFinalRank(i + 1));
        }

        log.debug("RRF fusion completed across {} sources with k={}: {} unique candidates, {} returned",
                rankedLists.size(), k, accumulators.size(), finalResults.size());

        return Collections.unmodifiableList(finalResults);
    }

    public int getK() {
        return k;
    }

    private static class Accumulator {
        final long documentId;
        double totalRrf = 0.0;
        final Map<String, Integer> sourceRanks = new LinkedHashMap<>();
        final Map<String, Double> sourceScores = new LinkedHashMap<>();
        final Map<String, Double> rrfContributions = new LinkedHashMap<>();

        Accumulator(long documentId) {
            this.documentId = documentId;
        }
    }
}
