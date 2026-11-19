package com.searchforge.ranking.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.ranking.dto.RankedSearchHitResponse;
import com.searchforge.ranking.dto.RankedSearchResponse;
import com.searchforge.ranking.dto.TermExplanation;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.IdfCalculator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Production REST controller exposing BM25 ranked search, ranked Boolean retrieval,
 * and filtered relevance search.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final RankedBooleanQueryEngine rankedBooleanQueryEngine;
    private final CrawledDocumentRepository crawledDocumentRepository;
    private final InvertedIndex invertedIndex;
    private final IdfCalculator idfCalculator;
    private final Bm25TermScorer termScorer;

    public SearchController(RankedRetrievalEngine rankedRetrievalEngine,
                            RankedBooleanQueryEngine rankedBooleanQueryEngine,
                            CrawledDocumentRepository crawledDocumentRepository,
                            InvertedIndex invertedIndex,
                            @Qualifier("bm25IdfCalculator") IdfCalculator idfCalculator,
                            Bm25TermScorer termScorer) {
        this.rankedRetrievalEngine = Objects.requireNonNull(rankedRetrievalEngine, "RankedRetrievalEngine cannot be null");
        this.rankedBooleanQueryEngine = Objects.requireNonNull(rankedBooleanQueryEngine, "RankedBooleanQueryEngine cannot be null");
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.idfCalculator = Objects.requireNonNull(idfCalculator, "IdfCalculator cannot be null");
        this.termScorer = Objects.requireNonNull(termScorer, "Bm25TermScorer cannot be null");
    }

    /**
     * GET /api/v1/search?q={query}&limit={limit}&explain={boolean}
     * Executes free-text BM25 ranked retrieval (Mode A).
     */
    @GetMapping
    public ResponseEntity<?> searchFreeText(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "explain", defaultValue = "false") boolean explain) {

        ResponseEntity<?> validationError = validateQueryParams(query, limit);
        if (validationError != null) {
            return validationError;
        }

        long startNs = System.nanoTime();
        List<RankedSearchResult> rankedResults = rankedRetrievalEngine.search(query.trim(), limit);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        return ResponseEntity.ok(buildSearchResponse(query, rankedResults, durationMs, explain));
    }

    /**
     * GET /api/v1/search/ranked-boolean?q={booleanQuery}&limit={limit}&explain={boolean}
     * Evaluates a Boolean query AST and ranks matching candidates via BM25 (Mode C).
     */
    @GetMapping("/ranked-boolean")
    public ResponseEntity<?> searchRankedBoolean(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "explain", defaultValue = "false") boolean explain) {

        ResponseEntity<?> validationError = validateQueryParams(query, limit);
        if (validationError != null) {
            return validationError;
        }

        try {
            long startNs = System.nanoTime();
            List<RankedSearchResult> rankedResults = rankedBooleanQueryEngine.searchRankedBoolean(query.trim(), limit);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            return ResponseEntity.ok(buildSearchResponse(query, rankedResults, durationMs, explain));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid Boolean query syntax: " + ex.getMessage()
            ));
        }
    }

    /**
     * GET /api/v1/search/filtered?q={query}&filter={booleanFilter}&limit={limit}&explain={boolean}
     * Executes free-text BM25 search restricted strictly to candidate documents passing a Boolean filter (Mode B).
     */
    @GetMapping("/filtered")
    public ResponseEntity<?> searchFiltered(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "explain", defaultValue = "false") boolean explain) {

        ResponseEntity<?> validationError = validateQueryParams(query, limit);
        if (validationError != null) {
            return validationError;
        }

        if (filter == null || filter.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Filter parameter 'filter' must not be blank"
            ));
        }

        try {
            long startNs = System.nanoTime();
            List<RankedSearchResult> rankedResults = rankedBooleanQueryEngine.searchFiltered(query.trim(), filter.trim(), limit);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            return ResponseEntity.ok(buildSearchResponse(query, rankedResults, durationMs, explain));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid Boolean filter syntax: " + ex.getMessage()
            ));
        }
    }

    private ResponseEntity<?> validateQueryParams(String query, int limit) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Search query parameter 'q' must not be blank"
            ));
        }
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Parameter 'limit' must be between 1 and 100"
            ));
        }
        return null;
    }

    private RankedSearchResponse buildSearchResponse(String query,
                                                     List<RankedSearchResult> rankedResults,
                                                     long durationMs,
                                                     boolean explain) {
        if (rankedResults.isEmpty()) {
            return new RankedSearchResponse(query, 0, 0, durationMs, List.of());
        }

        // Efficient batch metadata lookup to prevent N+1 queries
        List<Long> docIds = rankedResults.stream().map(RankedSearchResult::docId).toList();
        Map<Long, CrawledDocument> docMap = crawledDocumentRepository.findAllById(docIds)
                .stream()
                .collect(Collectors.toMap(CrawledDocument::getId, Function.identity()));

        List<RankedSearchHitResponse> hits = new ArrayList<>(rankedResults.size());
        for (RankedSearchResult result : rankedResults) {
            CrawledDocument doc = docMap.get(result.docId());

            String url = doc != null ? doc.getUrl() : "urn:doc:" + result.docId();
            String title = (doc != null && doc.getTitle() != null && !doc.getTitle().isBlank())
                    ? doc.getTitle()
                    : url;
            String domain = doc != null ? doc.getDomain() : "";
            String snippet = doc != null ? createSnippet(doc.getCleanText()) : "";

            Map<String, TermExplanation> explanation = explain
                    ? buildExplanation(result.docId(), result.matchedTerms(), result.termScores())
                    : null;

            hits.add(new RankedSearchHitResponse(
                    result.docId(),
                    url,
                    title,
                    domain,
                    snippet,
                    round4(result.score()),
                    result.rank(),
                    result.matchedTerms(),
                    explanation
            ));
        }

        return new RankedSearchResponse(
                query,
                hits.size(),
                hits.size(),
                durationMs,
                hits
        );
    }

    private Map<String, TermExplanation> buildExplanation(long docId,
                                                          List<String> matchedTerms,
                                                          Map<String, Double> termScores) {
        Map<String, TermExplanation> explanationMap = new LinkedHashMap<>();
        int docLength = invertedIndex.getDocLength(docId);
        double avgdl = invertedIndex.getAverageDocumentLength();
        long totalDocs = invertedIndex.getTotalDocuments();

        for (String term : matchedTerms) {
            Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
            int rawTf = 0;
            long df = 0;
            if (postingsOpt.isPresent()) {
                PostingsList pl = postingsOpt.get();
                df = pl.size();
                Optional<Posting> postingOpt = pl.getPostingForDocument(docId);
                rawTf = postingOpt.map(Posting::termFrequency).orElse(0);
            }

            double idf = idfCalculator.calculateIdf(df, totalDocs);
            double saturation = termScorer.calculateSaturation(rawTf, docLength, avgdl);
            double score = termScores.getOrDefault(term, saturation * idf);

            explanationMap.put(term, new TermExplanation(
                    rawTf,
                    round4(idf),
                    round4(saturation),
                    round4(score)
            ));
        }

        // Include any phrase bonuses in the explanation map
        for (Map.Entry<String, Double> entry : termScores.entrySet()) {
            if (entry.getKey().startsWith("PHRASE:")) {
                explanationMap.put(entry.getKey(), new TermExplanation(
                        1,
                        0.0,
                        0.0,
                        round4(entry.getValue())
                ));
            }
        }

        return explanationMap;
    }

    private String createSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
