package com.searchforge.query.benchmark;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.query.autocomplete.service.AutocompleteService;
import com.searchforge.query.autocomplete.service.DefaultAutocompleteService;
import com.searchforge.query.autocomplete.trie.AutocompleteTrie;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.service.DefaultSnippetGeneratorService;
import com.searchforge.query.snippet.service.SnippetGeneratorService;
import com.searchforge.query.spelling.algorithm.DamerauLevenshteinDistance;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.service.DefaultSpellingCorrectionService;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import com.searchforge.query.synonym.dictionary.InMemorySynonymDictionary;
import com.searchforge.query.synonym.service.DefaultSynonymExpansionService;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.controller.SearchController;
import com.searchforge.ranking.dto.RankedSearchResponse;
import com.searchforge.ranking.retrieval.DefaultRankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Performance benchmarking suite measuring real microsecond/millisecond latency percentiles
 * (p50, p90, p99, mean) across all Phase 5 Query Intelligence components:
 * 1. Autocomplete Trie prefix lookup
 * 2. Spelling correction candidate generation & scoring
 * 3. Dynamic snippet extraction & highlighting
 * 4. Original baseline search vs. Enriched intelligence search
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryIntelligenceBenchmarkTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private AutocompleteService autocompleteService;
    private SpellingCorrectionService spellingService;
    private SnippetGeneratorService snippetService;
    private SynonymExpansionService synonymService;
    private RankedRetrievalEngine baselineEngine;
    private RankedRetrievalEngine enrichedEngine;
    private SearchController searchController;
    private CrawledDocumentRepository docRepository;

    private static final int NUM_DOCS = 500;
    private static final int WARMUP_ITERATIONS = 50;
    private static final int BENCHMARK_ITERATIONS = 200;

    @BeforeAll
    void setUpCorpusAndEngines() {
        index = new InMemoryInvertedIndex();
        StopWordFilter stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter, new PorterStemmer());

        Bm25Config bm25Config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(bm25Config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);

        // Build 500 synthetic technical documents with rich vocabulary
        List<CrawledDocument> mockDocList = new ArrayList<>(NUM_DOCS);
        for (long i = 1; i <= NUM_DOCS; i++) {
            StringBuilder text = new StringBuilder();
            text.append("Document ").append(i).append(" covers modern software engineering principles and architectural designs. ");
            if (i % 2 == 0) text.append("PostgreSQL database provides btree indexes, relational ACID transactions, and query plans. ");
            if (i % 3 == 0) text.append("Spring Boot microservices framework simplifies cloud native java application development. ");
            if (i % 5 == 0) text.append("Kubernetes container orchestration automates deployment, scaling, and operational management. ");
            if (i % 7 == 0) text.append("Information retrieval inverted index uses BM25 scoring with term saturation and document length normalization. ");
            if (i % 11 == 0) text.append("Python web scraper and crawler handles robots exclusion protocol and politeness rate limiting. ");

            String fullText = text.toString();
            index.addDocument(i, tokenizer.tokenize(fullText));

            CrawledDocument doc = new CrawledDocument(
                    "https://searchforge.dev/doc/" + i,
                    "searchforge.dev",
                    "Engineering Doc " + i,
                    fullText,
                    "hash_" + i,
                    200,
                    Instant.now()
            );
            doc.setId(i);
            mockDocList.add(doc);
        }

        // Initialize Autocomplete
        autocompleteService = new DefaultAutocompleteService(index);

        // Initialize Spelling
        spellingService = new DefaultSpellingCorrectionService(index);

        // Initialize Snippet Generator
        snippetService = new DefaultSnippetGeneratorService(stopWordFilter, 160);

        // Initialize Synonym Service
        SynonymProperties synonymProps = new SynonymProperties();
        synonymProps.setMode(SynonymExpansionMode.BIDIRECTIONAL);
        synonymProps.setDiscount(0.5);
        InMemorySynonymDictionary dictionary = new InMemorySynonymDictionary(synonymProps);
        synonymService = new DefaultSynonymExpansionService(dictionary, synonymProps, stopWordFilter);

        // Baseline Engine (without synonym expansion)
        baselineEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer, null);

        // Enriched Engine (with synonym expansion)
        enrichedEngine = new DefaultRankedRetrievalEngine(index, bm25Scorer, tokenizer, synonymService);

        // Search Controller for Enriched End-to-End Search
        docRepository = mock(CrawledDocumentRepository.class);
        given(docRepository.findAllById(anyList())).willAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<CrawledDocument> found = new ArrayList<>();
            for (Long id : ids) {
                if (id >= 1 && id <= mockDocList.size()) {
                    found.add(mockDocList.get((int) (id - 1)));
                }
            }
            return found;
        });

        RankedBooleanQueryEngine booleanEngine = mock(RankedBooleanQueryEngine.class);
        searchController = new SearchController(
                enrichedEngine,
                booleanEngine,
                docRepository,
                index,
                idfCalculator,
                termScorer,
                spellingService,
                synonymService,
                snippetService
        );
    }

    @Test
    @DisplayName("Benchmark Autocomplete: Top-K prefix lookup latency")
    void benchmarkAutocomplete() {
        String[] prefixes = {"pos", "data", "spr", "jav", "ind", "kuber", "postg", "pyth"};

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String prefix : prefixes) {
                autocompleteService.suggest(prefix, 5);
            }
        }

        // Measurement
        List<Double> latenciesUs = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String prefix : prefixes) {
                long start = System.nanoTime();
                var suggestions = autocompleteService.suggest(prefix, 5);
                long elapsed = System.nanoTime() - start;
                latenciesUs.add(elapsed / 1_000.0);
                assertFalse(suggestions.isEmpty(), "Expected suggestions for " + prefix);
            }
        }

        reportMetrics("Autocomplete Top-K Lookup", latenciesUs, "µs");
        assertTrue(computePercentile(latenciesUs, 99) < 2000.0, "P99 latency must be under 2000µs (2ms)");
    }

    @Test
    @DisplayName("Benchmark Spelling: Candidate generation & Noisy Channel scoring latency")
    void benchmarkSpellingCorrection() {
        String[] typos = {"postgreql", "databse", "sprng", "javva", "framwork", "pythn"};

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String typo : typos) {
                spellingService.suggest(typo);
            }
        }

        // Measurement
        List<Double> latenciesUs = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String typo : typos) {
                long start = System.nanoTime();
                QuerySuggestionResponse suggestion = spellingService.suggest(typo);
                long elapsed = System.nanoTime() - start;
                latenciesUs.add(elapsed / 1_000.0);
                assertTrue(suggestion.hasCorrection(), "Expected correction for " + typo);
            }
        }

        reportMetrics("Spelling Correction Suggestion", latenciesUs, "µs");
        assertTrue(computePercentile(latenciesUs, 90) < 5000.0, "P90 latency must be under 5000µs (5ms)");
    }

    @Test
    @DisplayName("Benchmark Snippets: Dynamic window scoring and HTML highlighting latency")
    void benchmarkSnippetGeneration() {
        String docText = "PostgreSQL is an advanced, enterprise-class open-source relational database system. "
                + "It supports both SQL relational and JSON querying. B-tree indexes are the default index type in PostgreSQL, "
                + "providing highly optimized logarithmic lookup and range query performance across billions of records. "
                + "Spring Boot applications connect to PostgreSQL via connection pooling libraries like HikariCP.";

        Set<String> queryTerms = Set.of("postgresql", "btree", "database");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS * 2; i++) {
            snippetService.generateSnippet(docText, queryTerms, 160);
        }

        // Measurement
        List<Double> latenciesUs = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS * 2; i++) {
            long start = System.nanoTime();
            HighlightedSnippet hs = snippetService.generateSnippet(docText, queryTerms, 160);
            long elapsed = System.nanoTime() - start;
            latenciesUs.add(elapsed / 1_000.0);
            assertTrue(hs.hasMatch());
        }

        reportMetrics("Dynamic Snippet & Highlighting", latenciesUs, "µs");
        assertTrue(computePercentile(latenciesUs, 99) < 1000.0, "P99 latency must be under 1000µs (1ms)");
    }

    @Test
    @DisplayName("Benchmark Search: Original baseline BM25 vs. Enriched intelligence search")
    void benchmarkSearchLatencyComparison() {
        String[] queries = {"postgresql btree", "spring boot java", "database index", "kubernetes microservices"};

        // Warmup baseline
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String q : queries) {
                baselineEngine.search(q, 10);
            }
        }

        // Measure baseline
        List<Double> baselineMs = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String q : queries) {
                long start = System.nanoTime();
                baselineEngine.search(q, 10);
                long elapsed = System.nanoTime() - start;
                baselineMs.add(elapsed / 1_000_000.0);
            }
        }

        // Warmup enriched search
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String q : queries) {
                searchController.searchFreeText(q, 10, false);
            }
        }

        // Measure enriched search
        List<Double> enrichedMs = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String q : queries) {
                long start = System.nanoTime();
                ResponseEntity<?> response = searchController.searchFreeText(q, 10, false);
                long elapsed = System.nanoTime() - start;
                enrichedMs.add(elapsed / 1_000_000.0);
                assertEquals(200, response.getStatusCode().value());
            }
        }

        reportMetrics("Original Baseline BM25 Search", baselineMs, "ms");
        reportMetrics("Enriched Intelligence Search", enrichedMs, "ms");

        // Verify sub-10ms latency for production scale
        assertTrue(computePercentile(enrichedMs, 90) < 10.0, "P90 Enriched Search must be under 10ms");
    }

    private void reportMetrics(String component, List<Double> latencies, String unit) {
        Collections.sort(latencies);
        double mean = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p50 = computePercentile(latencies, 50);
        double p90 = computePercentile(latencies, 90);
        double p99 = computePercentile(latencies, 99);

        System.out.printf("[BENCHMARK REPORT] %-32s | Mean: %6.2f%s | p50: %6.2f%s | p90: %6.2f%s | p99: %6.2f%s%n",
                component, mean, unit, p50, unit, p90, unit, p99, unit);
    }

    private double computePercentile(List<Double> sortedList, int percentile) {
        if (sortedList.isEmpty()) return 0.0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
    }
}
