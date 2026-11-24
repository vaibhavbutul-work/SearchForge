package com.searchforge.query.autocomplete.controller;

import com.searchforge.query.autocomplete.dto.AutocompleteResponse;
import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;
import com.searchforge.query.autocomplete.service.AutocompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller exposing prefix-based autocomplete search suggestions.
 */
@RestController
@RequestMapping("/api/v1/search/autocomplete")
public class AutocompleteController {

    public static final int MAX_PREFIX_LENGTH = 100;

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = Objects.requireNonNull(autocompleteService, "AutocompleteService cannot be null");
    }

    /**
     * GET /api/v1/search/autocomplete?prefix={prefix}&limit={limit}
     *
     * Retrieves ranked autocomplete suggestions for a given prefix.
     *
     * @param prefix Prefix query string.
     * @param limit  Maximum number of suggestions to return (default 5, max 100).
     * @return 200 OK with AutocompleteResponse, or 400 Bad Request if parameters are invalid.
     */
    @GetMapping
    public ResponseEntity<?> autocomplete(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {

        if (prefix == null || prefix.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Prefix parameter 'prefix' must not be blank"
            ));
        }

        if (prefix.length() > MAX_PREFIX_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Prefix parameter 'prefix' must not exceed " + MAX_PREFIX_LENGTH + " characters"
            ));
        }

        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Parameter 'limit' must be between 1 and 100"
            ));
        }

        String cleanedPrefix = prefix.trim();
        List<AutocompleteSuggestion> suggestions = autocompleteService.suggest(cleanedPrefix, limit);

        return ResponseEntity.ok(new AutocompleteResponse(cleanedPrefix, suggestions));
    }
}
