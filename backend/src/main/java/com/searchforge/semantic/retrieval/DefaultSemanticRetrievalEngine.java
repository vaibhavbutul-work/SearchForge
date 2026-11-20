package com.searchforge.semantic.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.retrieval.dto.SemanticSearchHitResponse;
import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;
import com.searchforge.semantic.vector.VectorSearchResult;
import com.searchforge.semantic.vector.VectorSimilarityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link SemanticRetrievalEngine}.
 *
 * Vectorizes queries into latent continuous space, performs Top-K similarity search,
 * and hydrates document hits with title, domain, and contextual snippets.
 */
@Service
public class DefaultSemanticRetrievalEngine implements SemanticRetrievalEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultSemanticRetrievalEngine.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int SNIPPET_CHAR_LIMIT = 180;

    private final EmbeddingModel embeddingModel;
    private final VectorSimilarityEngine vectorSimilarityEngine;
    private final CrawledDocumentRepository crawledDocumentRepository;
    private final com.searchforge.semantic.embedding.QueryEmbeddingCache queryEmbeddingCache;

    public DefaultSemanticRetrievalEngine(EmbeddingModel embeddingModel,
                                         VectorSimilarityEngine vectorSimilarityEngine,
                                         CrawledDocumentRepository crawledDocumentRepository) {
        this(embeddingModel, vectorSimilarityEngine, crawledDocumentRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultSemanticRetrievalEngine(EmbeddingModel embeddingModel,
                                         VectorSimilarityEngine vectorSimilarityEngine,
                                         CrawledDocumentRepository crawledDocumentRepository,
                                         @org.springframework.beans.factory.annotation.Autowired(required = false)
                                         com.searchforge.semantic.embedding.QueryEmbeddingCache queryEmbeddingCache) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.vectorSimilarityEngine = Objects.requireNonNull(vectorSimilarityEngine, "VectorSimilarityEngine cannot be null");
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.queryEmbeddingCache = queryEmbeddingCache;
    }

    @Override
    public SemanticSearchResponse search(String query, int limit) {
        long startTime = System.currentTimeMillis();

        if (query == null || query.isBlank()) {
            return new SemanticSearchResponse(query != null ? query : "", "semantic", 0, 0, 0L, List.of());
        }

        int resolvedLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        // 1. Vectorize query using the active embedding model (with optional LRU caching)
        float[] queryVector;
        try {
            if (queryEmbeddingCache != null) {
                queryVector = queryEmbeddingCache.getOrCompute(query.trim(), embeddingModel::embed);
            } else {
                queryVector = embeddingModel.embed(query.trim());
            }
        } catch (Exception e) {
            log.error("Failed to vectorize query '{}': {}", query, e.getMessage());
            long elapsed = System.currentTimeMillis() - startTime;
            return new SemanticSearchResponse(query, "semantic", 0, 0, elapsed, List.of());
        }

        // 2. Perform exact Top-K vector similarity search
        List<VectorSearchResult> vectorHits = vectorSimilarityEngine.search(queryVector, resolvedLimit);
        if (vectorHits.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new SemanticSearchResponse(query, "semantic", 0, 0, elapsed, List.of());
        }

        // 3. Hydrate matching documents from database in a single batch query
        List<Long> docIds = vectorHits.stream()
                .map(VectorSearchResult::documentId)
                .toList();

        Map<Long, CrawledDocument> documentMap = crawledDocumentRepository.findAllById(docIds).stream()
                .collect(Collectors.toMap(CrawledDocument::getId, Function.identity()));

        // 4. Assemble response hits in sorted rank order
        List<SemanticSearchHitResponse> hits = new ArrayList<>(vectorHits.size());
        for (VectorSearchResult hit : vectorHits) {
            CrawledDocument doc = documentMap.get(hit.documentId());

            String url = (doc != null && doc.getUrl() != null) ? doc.getUrl() : "doc://" + hit.documentId();
            String title = (doc != null && doc.getTitle() != null && !doc.getTitle().isBlank())
                    ? doc.getTitle()
                    : url;
            String domain = (doc != null && doc.getDomain() != null) ? doc.getDomain() : "unknown";
            String snippet = buildSnippet(doc);

            // Round score to 4 decimal places
            double roundedScore = Math.round(hit.similarityScore() * 10000.0) / 10000.0;

            hits.add(new SemanticSearchHitResponse(
                    hit.documentId(),
                    url,
                    title,
                    domain,
                    snippet,
                    roundedScore,
                    hit.rank()
            ));
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        log.debug("Semantic search for '{}' completed in {} ms ({} hits)", query, executionTimeMs, hits.size());

        return new SemanticSearchResponse(
                query,
                "semantic",
                hits.size(),
                hits.size(),
                executionTimeMs,
                hits
        );
    }

    private String buildSnippet(CrawledDocument doc) {
        if (doc == null || doc.getCleanText() == null || doc.getCleanText().isBlank()) {
            return "";
        }
        String text = doc.getCleanText().trim();
        if (text.length() <= SNIPPET_CHAR_LIMIT) {
            return text;
        }
        int boundary = text.lastIndexOf(' ', SNIPPET_CHAR_LIMIT);
        int cutIndex = boundary > 0 ? boundary : SNIPPET_CHAR_LIMIT;
        return text.substring(0, cutIndex) + "...";
    }
}
