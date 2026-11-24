package com.searchforge.ranking.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.service.SnippetGeneratorService;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.dto.RankedSearchHitResponse;
import com.searchforge.ranking.dto.RankedSearchResponse;
import com.searchforge.ranking.dto.TermExplanation;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.IdfCalculator;
import com.searchforge.search.hybrid.HybridCandidate;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.HybridSearchService;
import com.searchforge.search.hybrid.SearchMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production REST controller exposing multi-modal search (lexical BM25, dense semantic,
 * and hybrid RRF retrieval), ranked Boolean retrieval, and filtered relevance search enriched with query intelligence:
 * 1. Automatic spelling error detection and didYouMean suggestions.
 * 2. Query expansion via synonym dictionaries.
 * 3. Dynamic relevant snippet generation with XSS-safe &lt;mark&gt; highlighting.
 * 4. Multi-modal rank fusion explainability and graceful degradation telemetry.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    public static final int MAX_QUERY_LENGTH = 500;

    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final RankedBooleanQueryEngine rankedBooleanQueryEngine;
    private final CrawledDocumentRepository crawledDocumentRepository;
    private final InvertedIndex invertedIndex;
    private final IdfCalculator idfCalculator;
    private final Bm25TermScorer termScorer;
    private final SpellingCorrectionService spellingCorrectionService;
    private final SynonymExpansionService synonymExpansionService;
    private final SnippetGeneratorService snippetGeneratorService;
    private final HybridSearchService hybridSearchService;

    public SearchController(RankedRetrievalEngine rankedRetrievalEngine,
                            RankedBooleanQueryEngine rankedBooleanQueryEngine,
                            CrawledDocumentRepository crawledDocumentRepository,
                            InvertedIndex invertedIndex,
                            @Qualifier("bm25IdfCalculator") IdfCalculator idfCalculator,
                            Bm25TermScorer termScorer) {
        this(rankedRetrievalEngine, rankedBooleanQueryEngine, crawledDocumentRepository,
                invertedIndex, idfCalculator, termScorer, null, null, null, null);
    }

    public SearchController(RankedRetrievalEngine rankedRetrievalEngine,
                            RankedBooleanQueryEngine rankedBooleanQueryEngine,
                            CrawledDocumentRepository crawledDocumentRepository,
                            InvertedIndex invertedIndex,
                            @Qualifier("bm25IdfCalculator") IdfCalculator idfCalculator,
                            Bm25TermScorer termScorer,
                            SpellingCorrectionService spellingCorrectionService,
                            SynonymExpansionService synonymExpansionService,
                            SnippetGeneratorService snippetGeneratorService) {
        this(rankedRetrievalEngine, rankedBooleanQueryEngine, crawledDocumentRepository,
                invertedIndex, idfCalculator, termScorer, spellingCorrectionService,
                synonymExpansionService, snippetGeneratorService, null);
    }

    @Autowired
    public SearchController(RankedRetrievalEngine rankedRetrievalEngine,
                            RankedBooleanQueryEngine rankedBooleanQueryEngine,
                            CrawledDocumentRepository crawledDocumentRepository,
                            InvertedIndex invertedIndex,
                            @Qualifier("bm25IdfCalculator") IdfCalculator idfCalculator,
                            Bm25TermScorer termScorer,
                            @Autowired(required = false) SpellingCorrectionService spellingCorrectionService,
                            @Autowired(required = false) SynonymExpansionService synonymExpansionService,
                            @Autowired(required = false) SnippetGeneratorService snippetGeneratorService,
                            @Autowired(required = false) HybridSearchService hybridSearchService) {
        this.rankedRetrievalEngine = Objects.requireNonNull(rankedRetrievalEngine, "RankedRetrievalEngine cannot be null");
        this.rankedBooleanQueryEngine = Objects.requireNonNull(rankedBooleanQueryEngine, "RankedBooleanQueryEngine cannot be null");
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.idfCalculator = Objects.requireNonNull(idfCalculator, "IdfCalculator cannot be null");
        this.termScorer = Objects.requireNonNull(termScorer, "Bm25TermScorer cannot be null");
        this.spellingCorrectionService = spellingCorrectionService;
        this.synonymExpansionService = synonymExpansionService;
        this.snippetGeneratorService = snippetGeneratorService;
        this.hybridSearchService = hybridSearchService;
    }

    /**
     * Backward-compatible Java overload for searches defaulting to lexical retrieval mode.
     */
    public ResponseEntity<?> searchFreeText(String query, int limit, boolean explain) {
        return searchFreeText(query, "lexical", limit, explain);
    }

    /**
     * GET /api/v1/search?q={query}&amp;mode={mode}&amp;limit={limit}&amp;explain={boolean}
     * Executes free-text search across lexical (BM25), dense semantic, or hybrid (RRF) retrieval modes,
     * enriched with spelling suggestions, synonym expansion, and dynamic snippet generation with highlighting.
     */
    @GetMapping
    public ResponseEntity<?> searchFreeText(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "mode", defaultValue = "lexical") String modeStr,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "explain", defaultValue = "false") boolean explain) {

        ResponseEntity<?> validationError = validateQueryParams(query, limit);
        if (validationError != null) {
            return validationError;
        }

        SearchMode searchMode;
        try {
            searchMode = SearchMode.fromString(modeStr);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()
            ));
        }

        long startNs = System.nanoTime();
        String trimmedQuery = query.trim();

        // 1. Detect potential spelling errors and suggest didYouMean query
        String didYouMean = null;
        if (spellingCorrectionService != null && !trimmedQuery.contains("\"")) {
            QuerySuggestionResponse suggestion = spellingCorrectionService.suggest(trimmedQuery);
            if (suggestion != null && suggestion.hasCorrection()) {
                didYouMean = suggestion.suggestedQuery();
            }
        }

        // 2. Delegate to HybridSearchService if available
        if (hybridSearchService != null) {
            HybridSearchResult hybridResult = hybridSearchService.search(trimmedQuery, searchMode, limit);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            Set<String> highlightTerms = extractHighlightTerms(trimmedQuery, didYouMean);

            return ResponseEntity.ok(buildHybridResponse(
                    trimmedQuery,
                    hybridResult,
                    didYouMean,
                    durationMs,
                    explain,
                    highlightTerms
            ));
        }

        // Fallback for minimal testing harnesses where HybridSearchService is not wired
        if (searchMode != SearchMode.LEXICAL) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Hybrid and semantic search services are not configured in this profile"
            ));
        }

        // 3. Fallback standard BM25 retrieval
        List<RankedSearchResult> rankedResults = rankedRetrievalEngine.search(trimmedQuery, limit);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        Set<String> highlightTerms = extractHighlightTerms(trimmedQuery, didYouMean);

        return ResponseEntity.ok(buildSearchResponse(trimmedQuery, didYouMean, rankedResults, durationMs, explain, highlightTerms));
    }

    /**
     * GET /api/v1/search/ranked-boolean?q={booleanQuery}&amp;limit={limit}&amp;explain={boolean}
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
            String trimmedQuery = query.trim();
            List<RankedSearchResult> rankedResults = rankedBooleanQueryEngine.searchRankedBoolean(trimmedQuery, limit);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            Set<String> highlightTerms = extractHighlightTerms(trimmedQuery, null);
            return ResponseEntity.ok(buildSearchResponse(trimmedQuery, null, rankedResults, durationMs, explain, highlightTerms));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid Boolean query syntax: " + ex.getMessage()
            ));
        }
    }

    /**
     * GET /api/v1/search/filtered?q={query}&amp;filter={booleanFilter}&amp;limit={limit}&amp;explain={boolean}
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
        if (filter.length() > MAX_QUERY_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Filter parameter 'filter' must not exceed " + MAX_QUERY_LENGTH + " characters"
            ));
        }

        try {
            long startNs = System.nanoTime();
            String trimmedQuery = query.trim();
            List<RankedSearchResult> rankedResults = rankedBooleanQueryEngine.searchFiltered(trimmedQuery, filter.trim(), limit);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            Set<String> highlightTerms = extractHighlightTerms(trimmedQuery, null);
            return ResponseEntity.ok(buildSearchResponse(trimmedQuery, null, rankedResults, durationMs, explain, highlightTerms));
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
        if (query.length() > MAX_QUERY_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Search query parameter 'q' must not exceed " + MAX_QUERY_LENGTH + " characters"
            ));
        }
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Parameter 'limit' must be between 1 and 100"
            ));
        }
        return null;
    }

    private RankedSearchResponse buildHybridResponse(String query,
                                                     HybridSearchResult hybridResult,
                                                     String didYouMean,
                                                     long durationMs,
                                                     boolean explain,
                                                     Set<String> highlightTerms) {
        String effectiveModeStr = hybridResult.effectiveMode().name().toLowerCase(Locale.ROOT);
        Boolean fallbackFlag = hybridResult.fallback() ? Boolean.TRUE : null;
        String fallbackReason = hybridResult.fallbackReason();

        if (hybridResult.candidates().isEmpty()) {
            return new RankedSearchResponse(
                    query,
                    effectiveModeStr,
                    didYouMean,
                    0,
                    0,
                    durationMs,
                    fallbackFlag,
                    fallbackReason,
                    List.of()
            );
        }

        List<Long> docIds = hybridResult.candidates().stream().map(HybridCandidate::docId).toList();
        Map<Long, CrawledDocument> docMap = crawledDocumentRepository.findAllById(docIds)
                .stream()
                .collect(Collectors.toMap(CrawledDocument::getId, Function.identity()));

        List<RankedSearchHitResponse> hits = new ArrayList<>(hybridResult.candidates().size());
        for (HybridCandidate candidate : hybridResult.candidates()) {
            CrawledDocument doc = docMap.get(candidate.docId());

            String url = doc != null ? doc.getUrl() : "urn:doc:" + candidate.docId();
            String title = (doc != null && doc.getTitle() != null && !doc.getTitle().isBlank())
                    ? doc.getTitle()
                    : url;
            String domain = doc != null ? doc.getDomain() : "";

            String cleanText = doc != null ? doc.getCleanText() : null;
            String snippet;
            String highlightedSnippet;

            if (cleanText != null && !cleanText.isBlank()) {
                Set<String> docTerms = new HashSet<>(highlightTerms);
                docTerms.addAll(candidate.matchedTerms());

                HighlightedSnippet hs = snippetGeneratorService != null
                        ? snippetGeneratorService.generateSnippet(cleanText, docTerms)
                        : null;
                if (hs != null) {
                    snippet = hs.rawSnippet();
                    highlightedSnippet = hs.highlightedHtml();
                } else {
                    snippet = createSnippet(cleanText);
                    highlightedSnippet = snippet;
                }
            } else {
                snippet = "";
                highlightedSnippet = "";
            }

            Map<String, TermExplanation> explanation = (explain && !candidate.matchedTerms().isEmpty())
                    ? buildExplanation(candidate.docId(), candidate.matchedTerms(), candidate.termScores())
                    : null;

            hits.add(new RankedSearchHitResponse(
                    candidate.docId(),
                    url,
                    title,
                    domain,
                    snippet,
                    highlightedSnippet,
                    candidate.score(),
                    candidate.rank(),
                    candidate.matchedTerms(),
                    explanation,
                    candidate.bm25Score(),
                    candidate.semanticScore(),
                    candidate.bm25Rank(),
                    candidate.semanticRank(),
                    candidate.rrfScore()
            ));
        }

        return new RankedSearchResponse(
                query,
                effectiveModeStr,
                didYouMean,
                hits.size(),
                hits.size(),
                durationMs,
                fallbackFlag,
                fallbackReason,
                hits
        );
    }

    private RankedSearchResponse buildSearchResponse(String query,
                                                     String didYouMean,
                                                     List<RankedSearchResult> rankedResults,
                                                     long durationMs,
                                                     boolean explain,
                                                     Set<String> highlightTerms) {
        if (rankedResults.isEmpty()) {
            return new RankedSearchResponse(query, "lexical", didYouMean, 0, 0, durationMs, List.of());
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

            String cleanText = doc != null ? doc.getCleanText() : null;
            String snippet;
            String highlightedSnippet;

            if (cleanText != null && !cleanText.isBlank()) {
                Set<String> docTerms = new HashSet<>(highlightTerms);
                docTerms.addAll(result.matchedTerms());

                HighlightedSnippet hs = snippetGeneratorService != null
                        ? snippetGeneratorService.generateSnippet(cleanText, docTerms)
                        : null;
                if (hs != null) {
                    snippet = hs.rawSnippet();
                    highlightedSnippet = hs.highlightedHtml();
                } else {
                    snippet = createSnippet(cleanText);
                    highlightedSnippet = snippet;
                }
            } else {
                snippet = "";
                highlightedSnippet = "";
            }

            Map<String, TermExplanation> explanation = explain
                    ? buildExplanation(result.docId(), result.matchedTerms(), result.termScores())
                    : null;

            hits.add(new RankedSearchHitResponse(
                    result.docId(),
                    url,
                    title,
                    domain,
                    snippet,
                    highlightedSnippet,
                    round4(result.score()),
                    result.rank(),
                    result.matchedTerms(),
                    explanation
            ));
        }

        return new RankedSearchResponse(
                query,
                "lexical",
                didYouMean,
                hits.size(),
                hits.size(),
                durationMs,
                hits
        );
    }

    private Set<String> extractHighlightTerms(String query, String didYouMean) {
        Set<String> terms = new HashSet<>();
        collectWordTokens(query, terms);
        if (didYouMean != null) {
            collectWordTokens(didYouMean, terms);
        }
        if (synonymExpansionService != null && !query.contains("\"")) {
            ExpandedQuery expanded = synonymExpansionService.expandQuery(query);
            if (expanded != null) {
                terms.addAll(expanded.getUniqueTermStrings());
            }
        }
        return terms;
    }

    private void collectWordTokens(String text, Set<String> target) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (!isBooleanKeyword(word)) {
                target.add(word);
            }
        }
    }

    private boolean isBooleanKeyword(String word) {
        return "and".equalsIgnoreCase(word) || "or".equalsIgnoreCase(word) || "not".equalsIgnoreCase(word);
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
