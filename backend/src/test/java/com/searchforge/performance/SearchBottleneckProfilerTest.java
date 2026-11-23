package com.searchforge.performance;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.performance.profiler.SearchBottleneckProfiler;
import com.searchforge.performance.profiler.SearchBottleneckReport;
import com.searchforge.performance.profiler.SearchBottleneckReport.PhaseTiming;
import com.searchforge.performance.profiler.SearchBottleneckReport.QueryProfileResult;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 10.2: Search Latency Profiling & Bottleneck Analysis Tests")
class SearchBottleneckProfilerTest {

    private static final Logger log = LoggerFactory.getLogger(SearchBottleneckProfilerTest.class);

    @Autowired
    private SearchBottleneckProfiler profiler;

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private InvertedIndex invertedIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    private com.searchforge.indexer.repository.TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedDocumentMetadataRepository metadataRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        invertedIndex.clear();
        vectorStorage.clear();
        if (activeDocumentFilter != null) {
            activeDocumentFilter.clear();
        }
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
    }

    private void seedCorpus(int count) {
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, Math.min(count, ExpandedBenchmarkDataset.getCorpus().size()));
        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    com.searchforge.lifecycle.model.DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            indexingCoordinator.processDocument(doc.getId(), doc.getTitle(), doc.getCleanText());
        }
    }

    @Test
    @DisplayName("Verify Lexical BM25 micro-profiler captures all internal phases and valid percentage distribution")
    void testLexicalSearchPhaseBreakdown() {
        seedCorpus(10);

        QueryProfileResult result = profiler.profileLexicalQuery("search engine architecture", 5);

        assertNotNull(result);
        assertEquals("LEXICAL", result.searchMode());
        assertTrue(result.totalDurationNanos() > 0);
        assertTrue(result.totalDurationMs() > 0.0);
        assertEquals(6, result.phases().size(), "Lexical search must decompose into exactly 6 phases");

        double totalPct = 0.0;
        for (PhaseTiming pt : result.phases()) {
            assertTrue(pt.durationNanos() > 0, "Phase " + pt.phaseName() + " must have positive duration");
            assertTrue(pt.percentageOfTotal() >= 0.0);
            totalPct += pt.percentageOfTotal();
            log.info("Lexical Phase: {} -> {} nanos ({} ms, {}%)", pt.phaseName(), pt.durationNanos(), pt.durationMs(), pt.percentageOfTotal());
        }
        assertEquals(100.0, totalPct, 1.0, "Phase percentages must sum to ~100%");
    }

    @Test
    @DisplayName("Verify Boolean search micro-profiler captures AST parsing, set operations, and positional scoring")
    void testBooleanSearchPhaseBreakdown() {
        seedCorpus(10);

        QueryProfileResult result = profiler.profileBooleanQuery("search AND engine", 5);

        assertNotNull(result);
        assertEquals("BOOLEAN", result.searchMode());
        assertEquals(6, result.phases().size(), "Boolean search must decompose into exactly 6 phases");

        double totalPct = 0.0;
        for (PhaseTiming pt : result.phases()) {
            assertTrue(pt.durationNanos() > 0, "Phase " + pt.phaseName() + " must have positive duration");
            totalPct += pt.percentageOfTotal();
            log.info("Boolean Phase: {} -> {} nanos ({} ms, {}%)", pt.phaseName(), pt.durationNanos(), pt.durationMs(), pt.percentageOfTotal());
        }
        assertEquals(100.0, totalPct, 1.0, "Phase percentages must sum to ~100%");
    }

    @Test
    @DisplayName("Verify Semantic search micro-profiler captures vector storage access, cosine calculations, and heap")
    void testSemanticSearchPhaseBreakdown() {
        seedCorpus(10);

        QueryProfileResult result = profiler.profileSemanticQuery("neural information retrieval", 5);

        assertNotNull(result);
        assertEquals("SEMANTIC", result.searchMode());
        assertEquals(4, result.phases().size(), "Semantic search must decompose into exactly 4 phases");

        double totalPct = 0.0;
        for (PhaseTiming pt : result.phases()) {
            assertTrue(pt.durationNanos() > 0, "Phase " + pt.phaseName() + " must have positive duration");
            totalPct += pt.percentageOfTotal();
            log.info("Semantic Phase: {} -> {} nanos ({} ms, {}%)", pt.phaseName(), pt.durationNanos(), pt.durationMs(), pt.percentageOfTotal());
        }
        assertEquals(100.0, totalPct, 1.0, "Phase percentages must sum to ~100%");
    }

    @Test
    @DisplayName("Verify Hybrid search micro-profiler captures lexical, semantic, and RRF fusion phases")
    void testHybridSearchPhaseBreakdown() {
        seedCorpus(10);

        QueryProfileResult result = profiler.profileHybridQuery("java distributed systems", 5);

        assertNotNull(result);
        assertEquals("HYBRID", result.searchMode());
        assertEquals(5, result.phases().size(), "Hybrid search must decompose into exactly 5 phases");

        double totalPct = 0.0;
        for (PhaseTiming pt : result.phases()) {
            assertTrue(pt.durationNanos() > 0, "Phase " + pt.phaseName() + " must have positive duration");
            totalPct += pt.percentageOfTotal();
            log.info("Hybrid Phase: {} -> {} nanos ({} ms, {}%)", pt.phaseName(), pt.durationNanos(), pt.durationMs(), pt.percentageOfTotal());
        }
        assertEquals(100.0, totalPct, 1.0, "Phase percentages must sum to ~100%");
    }

    @Test
    @DisplayName("Verify Full Profiling Session executes warmup, measurement, and generates formatted report")
    void testFullProfilingSessionAndReport() {
        seedCorpus(15);

        List<String> lexicalQueries = List.of("search engine", "inverted index", "ranking algorithm");
        List<String> booleanQueries = List.of("search AND engine", "index OR ranking", "algorithm NOT java");
        List<String> semanticQueries = List.of("dense vector embeddings", "neural search", "semantic similarity");
        List<String> hybridQueries = List.of("search engine architecture", "distributed indexing", "vector retrieval");

        SearchBottleneckReport report = profiler.runFullProfilingSession(
                lexicalQueries,
                booleanQueries,
                semanticQueries,
                hybridQueries,
                2, // warmup rounds
                3  // measurement rounds
        );

        assertNotNull(report);
        assertEquals(4, report.profiles().size());
        assertFalse(report.majorFindings().isEmpty());
        assertTrue(report.totalCorpusDocuments() > 0);
        assertTrue(report.totalCorpusVectors() > 0);

        String summary = report.toFormattedSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("SEARCHFORGE PHASE 10: SEARCH LATENCY BOTTLENECK REPORT"));
        assertTrue(summary.contains("MAJOR BOTTLENECK FINDINGS"));

        log.info("\n{}", summary);
    }
}
