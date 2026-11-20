package com.searchforge.query.spelling.controller;

import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * REST controller exposing query spelling correction and "Did You Mean?" suggestions.
 */
@RestController
@RequestMapping("/api/v1/search/suggest")
public class SpellingSuggestionController {

    private final SpellingCorrectionService spellingCorrectionService;

    public SpellingSuggestionController(SpellingCorrectionService spellingCorrectionService) {
        this.spellingCorrectionService = Objects.requireNonNull(spellingCorrectionService, "SpellingCorrectionService cannot be null");
    }

    /**
     * GET /api/v1/search/suggest?q={query}
     *
     * Evaluates a search query for typographical errors and returns spelling recommendations.
     *
     * @param query Raw search query text.
     * @return 200 OK with QuerySuggestionResponse, or 400 Bad Request if query parameter is blank.
     */
    @GetMapping
    public ResponseEntity<?> suggest(
            @RequestParam(name = "q", required = false) String query) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Query parameter 'q' must not be blank"
            ));
        }

        String cleanedQuery = query.trim();
        QuerySuggestionResponse response = spellingCorrectionService.suggest(cleanedQuery);

        return ResponseEntity.ok(response);
    }
}
