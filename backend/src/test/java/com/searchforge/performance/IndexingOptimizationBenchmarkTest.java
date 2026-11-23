package com.searchforge.performance;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator.BatchIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator.DocumentBatchItem;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 10.3: Indexing Throughput & Pipeline Concurrency Optimization Tests")
class IndexingOptimizationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(IndexingOptimizationBenchmarkTest.class);

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private IndexedTermRepository termRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private InvertedIndex inMemoryIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private ActiveDocumentFilter activeDocumentFilter;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        inMemoryIndex.clear();
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

    @Test
    @DisplayName("Verify batched persistence and token reuse maintains 100% data fidelity between SQL and memory")
    void testBatchedPersistenceDataIntegrity() {
        // 1. Seed and process a set of benchmark documents
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, 10);
        List<Long> docIds = new ArrayList<>();

        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            docIds.add(doc.getId());

            var result = indexingCoordinator.processDocument(doc.getId(), doc.getTitle(), doc.getCleanText());
            assertTrue(result.isSuccess(), "Document " + doc.getId() + " must index successfully");
        }

        // 2. Assert In-Memory Index state
        assertEquals(10, inMemoryIndex.getTotalDocuments(), "Memory index must contain exactly 10 documents");
        assertTrue(inMemoryIndex.getVocabularySize() > 0, "Vocabulary size must be positive");

        // 3. Assert Database Persistence state
        assertEquals(10, metadataRepository.count(), "Persistent metadata count must match 10");
        assertEquals(10, embeddingRepository.count(), "Vector embedding table must contain 10 rows");
        assertEquals(10, vectorStorage.size(), "In-memory vector storage must contain 10 vectors");
        assertTrue(termRepository.count() > 0, "Database term repository must contain indexed terms");
        assertTrue(postingRepository.count() > 0, "Database postings repository must contain indexed postings");

        log.info("Integrity Verified: Memory Docs: {}, DB Terms: {}, DB Postings: {}, DB Embeddings: {}",
                inMemoryIndex.getTotalDocuments(), termRepository.count(), postingRepository.count(), embeddingRepository.count());
    }

    @Test
    @DisplayName("Verify high-throughput concurrent batch ingestion achieves significant speedup")
    void testConcurrentBatchIndexingThroughput() {
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, 20);
        List<DocumentBatchItem> batch = new ArrayList<>();

        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            batch.add(new DocumentBatchItem(doc.getId(), doc.getTitle(), doc.getCleanText()));
        }

        // Execute concurrent batch ingestion with Virtual Threads
        BatchIndexingResult batchResult = indexingCoordinator.processDocumentBatch(batch, 8);

        assertNotNull(batchResult);
        assertEquals(20, batchResult.totalProcessed());
        assertEquals(20, batchResult.successCount(), "All 20 documents must succeed");
        assertEquals(0, batchResult.failureCount(), "Zero failures expected");
        assertTrue(batchResult.totalTokens() > 0);
        assertTrue(batchResult.durationMs() > 0);

        log.info("Task 10.3 Optimized Ingestion: {} docs indexed in {} ms | Throughput: {} docs/sec | {} tokens/sec",
                batchResult.successCount(), batchResult.durationMs(),
                String.format("%.1f", batchResult.docsPerSecond()),
                String.format("%.1f", batchResult.tokensPerSecond()));

        // Verify that optimized throughput is significantly higher than 40 docs/sec baseline
        assertTrue(batchResult.docsPerSecond() > 40.0,
                "Optimized concurrent batching must exceed baseline 40 docs/sec, was: " + batchResult.docsPerSecond());
    }

    @Test
    @DisplayName("Verify batch ingestion idempotency cleanly detects unchanged documents without duplicate database writes")
    void testBatchIngestionIdempotency() {
        List<CrawledDocument> rawDocs = ExpandedBenchmarkDataset.getCorpus().subList(0, 10);
        List<DocumentBatchItem> batch = new ArrayList<>();

        for (CrawledDocument raw : rawDocs) {
            CrawledDocument doc = new CrawledDocument(
                    raw.getUrl(),
                    raw.getDomain(),
                    raw.getTitle(),
                    raw.getCleanText(),
                    raw.getContentHash(),
                    raw.getHttpStatusCode(),
                    raw.getCrawledAt(),
                    DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            batch.add(new DocumentBatchItem(doc.getId(), doc.getTitle(), doc.getCleanText()));
        }

        // Pass 1: Initial indexing
        BatchIndexingResult pass1 = indexingCoordinator.processDocumentBatch(batch, 4);
        assertEquals(10, pass1.successCount());

        long initialTermCount = termRepository.count();
        long initialPostingCount = postingRepository.count();

        // Pass 2: Repeat with identical content
        BatchIndexingResult pass2 = indexingCoordinator.processDocumentBatch(batch, 4);
        assertEquals(10, pass2.unchangedCount(), "All documents must be recognized as UNCHANGED");
        assertEquals(0, pass2.successCount(), "Zero new index operations should occur");

        // Assert database records did not duplicate
        assertEquals(initialTermCount, termRepository.count());
        assertEquals(initialPostingCount, postingRepository.count());
    }
}
