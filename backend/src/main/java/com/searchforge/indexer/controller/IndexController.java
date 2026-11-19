package com.searchforge.indexer.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.dto.BooleanSearchResponse;
import com.searchforge.indexer.dto.IndexStatsResponse;
import com.searchforge.indexer.dto.SearchHitResponse;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.retrieval.BooleanRetrievalEngine;
import com.searchforge.indexer.retrieval.BooleanSearchResult;
import com.searchforge.indexer.service.CorpusSyncResult;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST API controller for index management, corpus synchronization, and Boolean search retrieval.
 */
@RestController
@RequestMapping("/api/v1")
public class IndexController {

    private final IndexingEngine indexingEngine;
    private final BooleanRetrievalEngine retrievalEngine;
    private final InvertedIndex inMemoryIndex;
    private final CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    public IndexController(IndexingEngine indexingEngine,
                           BooleanRetrievalEngine retrievalEngine,
                           InvertedIndex inMemoryIndex,
                           CrawledDocumentRepository crawledDocumentRepository) {
        this.indexingEngine = indexingEngine;
        this.retrievalEngine = retrievalEngine;
        this.inMemoryIndex = inMemoryIndex;
        this.crawledDocumentRepository = crawledDocumentRepository;
    }

    /**
     * POST /api/v1/index/build
     * Synchronizes unindexed documents from the crawling database into the inverted index.
     */
    @PostMapping("/index/build")
    public ResponseEntity<CorpusSyncResult> buildIndex(
            @RequestParam(name = "batchSize", defaultValue = "100") int batchSize) {
        CorpusSyncResult result = indexingEngine.synchronizeCorpus(batchSize);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/index/rebuild
     * Drops existing index tables, clears in-memory structures, and indexes all crawled documents.
     */
    @PostMapping("/index/rebuild")
    public ResponseEntity<CorpusSyncResult> rebuildIndex() {
        CorpusSyncResult result = indexingEngine.rebuildIndex();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/index/warmup
     * Warm up in-memory index cache from persistent database storage.
     */
    @PostMapping("/index/warmup")
    public ResponseEntity<Map<String, String>> warmupIndex() {
        indexingEngine.warmupInMemoryIndex();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "In-memory inverted index successfully warmed up from persistent storage"
        ));
    }

    /**
     * GET /api/v1/index/stats
     * Returns corpus-wide indexing metrics, vocabulary sizes, and memory usage.
     */
    @GetMapping("/index/stats")
    public ResponseEntity<IndexStatsResponse> getStats() {
        IndexingStats stats = indexingEngine.getStats();
        IndexStatsResponse response = new IndexStatsResponse(
                stats.totalIndexedDocuments(),
                stats.totalTerms(),
                stats.totalPostings(),
                stats.averageDocumentLength(),
                inMemoryIndex.getTotalDocuments(),
                inMemoryIndex.getVocabularySize()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/search/boolean?q={query}&limit={limit}
     * Evaluates a Boolean search expression and resolves matching document metadata.
     */
    @GetMapping("/search/boolean")
    public ResponseEntity<?> searchBoolean(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Search query parameter 'q' must not be blank"
            ));
        }

        BooleanSearchResult searchResult = retrievalEngine.search(query);

        List<Long> matchedIds = searchResult.documentIds();
        int safeLimit = Math.max(1, limit);
        List<Long> pagedIds = matchedIds.stream().limit(safeLimit).toList();

        // Resolve document metadata while preserving search ranking / ordering
        Map<Long, CrawledDocument> docMap = crawledDocumentRepository.findAllById(pagedIds)
                .stream()
                .collect(Collectors.toMap(CrawledDocument::getId, Function.identity()));

        List<SearchHitResponse> hits = new ArrayList<>();
        for (Long id : pagedIds) {
            CrawledDocument doc = docMap.get(id);
            if (doc != null) {
                hits.add(new SearchHitResponse(
                        doc.getId(),
                        doc.getUrl(),
                        doc.getDomain(),
                        doc.getTitle(),
                        createSnippet(doc.getCleanText())
                ));
            }
        }

        BooleanSearchResponse response = new BooleanSearchResponse(
                query,
                searchResult.totalHits(),
                searchResult.durationMs(),
                hits
        );

        return ResponseEntity.ok(response);
    }

    private String createSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > 180 ? clean.substring(0, 180) + "..." : clean;
    }
}
