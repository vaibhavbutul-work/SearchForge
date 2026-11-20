package com.searchforge.search.hybrid;

import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.search.fusion.FusedDocumentResult;
import com.searchforge.search.fusion.RankFusionEngine;
import com.searchforge.search.fusion.RankedItem;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.VectorSearchResult;
import com.searchforge.semantic.vector.VectorSimilarityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of {@link HybridSearchService}.
 *
 * Coordinates multi-modal search execution across:
 * 1. Lexical retrieval via {@link RankedRetrievalEngine} (BM25 with phrase boosting and query intelligence).
 * 2. Dense semantic retrieval via {@link EmbeddingModel} and {@link VectorSimilarityEngine}.
 * 3. Reciprocal Rank Fusion (RRF) via {@link RankFusionEngine} to combine disparate ranking spaces.
 * 4. Graceful degradation / fallback to lexical BM25 retrieval upon semantic subsystem failure.
 */
@Service
public class DefaultHybridSearchService implements HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultHybridSearchService.class);

    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final EmbeddingModel embeddingModel;
    private final VectorSimilarityEngine vectorSimilarityEngine;
    private final RankFusionEngine rankFusionEngine;
    private final com.searchforge.semantic.embedding.QueryEmbeddingCache queryEmbeddingCache;
    private final int poolMultiplier;
    private final int minPoolSize;

    public DefaultHybridSearchService(
            RankedRetrievalEngine rankedRetrievalEngine,
            EmbeddingModel embeddingModel,
            VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine) {
        this(rankedRetrievalEngine, embeddingModel, vectorSimilarityEngine, rankFusionEngine, null, 2, 50);
    }

    public DefaultHybridSearchService(
            RankedRetrievalEngine rankedRetrievalEngine,
            EmbeddingModel embeddingModel,
            VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine,
            int poolMultiplier,
            int minPoolSize) {
        this(rankedRetrievalEngine, embeddingModel, vectorSimilarityEngine, rankFusionEngine, null, poolMultiplier, minPoolSize);
    }

    public DefaultHybridSearchService(
            RankedRetrievalEngine rankedRetrievalEngine,
            EmbeddingModel embeddingModel,
            VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine,
            com.searchforge.semantic.embedding.QueryEmbeddingCache queryEmbeddingCache) {
        this(rankedRetrievalEngine, embeddingModel, vectorSimilarityEngine, rankFusionEngine, queryEmbeddingCache, 2, 50);
    }

    @Autowired
    public DefaultHybridSearchService(
            RankedRetrievalEngine rankedRetrievalEngine,
            @Autowired(required = false) EmbeddingModel embeddingModel,
            @Autowired(required = false) VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine,
            @Autowired(required = false) com.searchforge.semantic.embedding.QueryEmbeddingCache queryEmbeddingCache,
            @Value("${search.hybrid.candidate-pool-multiplier:2}") int poolMultiplier,
            @Value("${search.hybrid.min-candidate-pool-size:50}") int minPoolSize) {
        this.rankedRetrievalEngine = Objects.requireNonNull(rankedRetrievalEngine, "RankedRetrievalEngine cannot be null");
        this.rankFusionEngine = Objects.requireNonNull(rankFusionEngine, "RankFusionEngine cannot be null");
        this.embeddingModel = embeddingModel;
        this.vectorSimilarityEngine = vectorSimilarityEngine;
        this.queryEmbeddingCache = queryEmbeddingCache;
        this.poolMultiplier = poolMultiplier > 0 ? poolMultiplier : 2;
        this.minPoolSize = minPoolSize > 0 ? minPoolSize : 50;
    }

    @Override
    public HybridSearchResult search(String query, SearchMode mode, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return HybridSearchResult.of(query != null ? query : "", mode != null ? mode : SearchMode.LEXICAL, List.of());
        }

        SearchMode resolvedMode = mode != null ? mode : SearchMode.LEXICAL;
        String trimmedQuery = query.trim();

        return switch (resolvedMode) {
            case LEXICAL -> executeLexical(trimmedQuery, limit);
            case SEMANTIC -> executeSemantic(trimmedQuery, limit);
            case HYBRID -> executeHybrid(trimmedQuery, limit);
        };
    }

    private HybridSearchResult executeLexical(String query, int limit) {
        List<RankedSearchResult> lexicalHits = rankedRetrievalEngine.search(query, limit);
        List<HybridCandidate> candidates = mapLexicalHits(lexicalHits);
        return HybridSearchResult.of(query, SearchMode.LEXICAL, candidates);
    }

    private HybridSearchResult executeSemantic(String query, int limit) {
        try {
            if (embeddingModel == null || vectorSimilarityEngine == null) {
                throw new IllegalStateException("Semantic components (EmbeddingModel or VectorSimilarityEngine) are not configured");
            }

            float[] queryVector = vectorizeQuery(query);
            List<VectorSearchResult> vectorHits = vectorSimilarityEngine.search(queryVector, limit);

            List<HybridCandidate> candidates = new ArrayList<>(vectorHits.size());
            for (VectorSearchResult hit : vectorHits) {
                double score = round4(hit.similarityScore());
                candidates.add(new HybridCandidate(
                        hit.documentId(),
                        score,
                        hit.rank(),
                        null,
                        score,
                        null,
                        hit.rank(),
                        null,
                        List.of(),
                        Map.of()
                ));
            }

            return HybridSearchResult.of(query, SearchMode.SEMANTIC, candidates);
        } catch (Exception ex) {
            log.warn("Semantic retrieval failed for query '{}': {}. Falling back to lexical BM25.", query, ex.getMessage());
            List<RankedSearchResult> lexicalHits = rankedRetrievalEngine.search(query, limit);
            return HybridSearchResult.fallback(
                    query,
                    SearchMode.SEMANTIC,
                    SearchMode.LEXICAL,
                    "Semantic retrieval unavailable: " + ex.getMessage(),
                    mapLexicalHits(lexicalHits)
            );
        }
    }

    private HybridSearchResult executeHybrid(String query, int limit) {
        int candidatePoolSize = Math.max(limit * poolMultiplier, minPoolSize);

        // 1. Lexical BM25 candidate generation
        List<RankedSearchResult> lexicalHits = rankedRetrievalEngine.search(query, candidatePoolSize);

        // 2. Dense semantic candidate generation with safety fallback
        List<VectorSearchResult> semanticHits = null;
        String semanticFailureReason = null;
        try {
            if (embeddingModel == null || vectorSimilarityEngine == null) {
                throw new IllegalStateException("Semantic components (EmbeddingModel or VectorSimilarityEngine) are not configured");
            }
            float[] queryVector = vectorizeQuery(query);
            semanticHits = vectorSimilarityEngine.search(queryVector, candidatePoolSize);
        } catch (Exception ex) {
            log.warn("Semantic retrieval failed during hybrid search for query '{}': {}. Degrading to lexical BM25.",
                    query, ex.getMessage());
            semanticFailureReason = ex.getMessage();
        }

        // If semantic retrieval encountered an error, degrade gracefully to lexical
        if (semanticFailureReason != null) {
            List<RankedSearchResult> truncatedLexical = lexicalHits.subList(0, Math.min(lexicalHits.size(), limit));
            return HybridSearchResult.fallback(
                    query,
                    SearchMode.HYBRID,
                    SearchMode.LEXICAL,
                    "Semantic retrieval failed; degraded to lexical BM25: " + semanticFailureReason,
                    mapLexicalHits(truncatedLexical)
            );
        }

        if (lexicalHits.isEmpty() && (semanticHits == null || semanticHits.isEmpty())) {
            return HybridSearchResult.of(query, SearchMode.HYBRID, List.of());
        }

        // 3. Map candidates to RankedItem instances for RRF
        List<RankedItem> bm25Items = new ArrayList<>(lexicalHits.size());
        Map<Long, RankedSearchResult> lexicalHitMap = new HashMap<>(lexicalHits.size());
        for (int i = 0; i < lexicalHits.size(); i++) {
            RankedSearchResult hit = lexicalHits.get(i);
            bm25Items.add(new RankedItem(hit.docId(), i + 1, hit.score(), "bm25"));
            lexicalHitMap.put(hit.docId(), hit);
        }

        List<RankedItem> semanticItems = new ArrayList<>();
        if (semanticHits != null) {
            for (int i = 0; i < semanticHits.size(); i++) {
                VectorSearchResult hit = semanticHits.get(i);
                semanticItems.add(new RankedItem(hit.documentId(), i + 1, hit.similarityScore(), "semantic"));
            }
        }

        Map<String, List<RankedItem>> inputs = new LinkedHashMap<>();
        inputs.put("bm25", bm25Items);
        inputs.put("semantic", semanticItems);

        // 4. Execute Reciprocal Rank Fusion
        List<FusedDocumentResult> fusedResults = rankFusionEngine.fuse(inputs, limit);

        // 5. Enrich fused candidates with individual score provenance
        List<HybridCandidate> candidates = new ArrayList<>(fusedResults.size());
        for (FusedDocumentResult fused : fusedResults) {
            long docId = fused.documentId();
            Double rawBm25 = fused.sourceScores().get("bm25");
            Double rawSemantic = fused.sourceScores().get("semantic");
            Integer bm25Rank = fused.sourceRanks().get("bm25");
            Integer semanticRank = fused.sourceRanks().get("semantic");

            Double roundedBm25 = rawBm25 != null ? round4(rawBm25) : null;
            Double roundedSemantic = rawSemantic != null ? round4(rawSemantic) : null;
            double roundedRrf = round6(fused.rrfScore());

            RankedSearchResult lexHit = lexicalHitMap.get(docId);
            List<String> matchedTerms = lexHit != null ? lexHit.matchedTerms() : List.of();
            Map<String, Double> termScores = lexHit != null ? lexHit.termScores() : Map.of();

            candidates.add(new HybridCandidate(
                    docId,
                    roundedRrf,
                    fused.finalRank(),
                    roundedBm25,
                    roundedSemantic,
                    bm25Rank,
                    semanticRank,
                    roundedRrf,
                    matchedTerms,
                    termScores
            ));
        }

        return HybridSearchResult.of(query, SearchMode.HYBRID, candidates);
    }

    private float[] vectorizeQuery(String query) {
        if (queryEmbeddingCache != null) {
            return queryEmbeddingCache.getOrCompute(query, embeddingModel::embed);
        }
        return embeddingModel.embed(query);
    }

    private List<HybridCandidate> mapLexicalHits(List<RankedSearchResult> hits) {
        List<HybridCandidate> candidates = new ArrayList<>(hits.size());
        for (RankedSearchResult hit : hits) {
            double score = round4(hit.score());
            candidates.add(new HybridCandidate(
                    hit.docId(),
                    score,
                    hit.rank(),
                    score,
                    null,
                    hit.rank(),
                    null,
                    null,
                    hit.matchedTerms(),
                    hit.termScores()
            ));
        }
        return candidates;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double round6(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }
}
