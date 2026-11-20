package com.searchforge.semantic.retrieval.controller;

import com.searchforge.semantic.retrieval.SemanticRetrievalEngine;
import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller exposing standalone dense semantic search capabilities.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SemanticSearchController {

    private final SemanticRetrievalEngine semanticRetrievalEngine;

    public SemanticSearchController(SemanticRetrievalEngine semanticRetrievalEngine) {
        this.semanticRetrievalEngine = semanticRetrievalEngine;
    }

    /**
     * GET /api/v1/search/semantic?q={query}&amp;limit={limit}
     *
     * Executes standalone dense semantic search using local embedding vector representations.
     *
     * @param query natural language search query
     * @param limit maximum Top-K results to return (default 10)
     * @return 200 OK with SemanticSearchResponse
     */
    @GetMapping("/semantic")
    public ResponseEntity<SemanticSearchResponse> searchSemantic(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        SemanticSearchResponse response = semanticRetrievalEngine.search(query, limit);
        return ResponseEntity.ok(response);
    }
}
