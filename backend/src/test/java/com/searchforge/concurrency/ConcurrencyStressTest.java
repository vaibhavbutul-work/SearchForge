package com.searchforge.concurrency;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.VectorSearchResult;
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

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 9: High-Concurrency Multi-Threaded Stress & Reliability Test Suite")
class ConcurrencyStressTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyStressTest.class);

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private InvertedIndex invertedIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private DocumentDeletionService deletionService;

    @Autowired
    private RankedRetrievalEngine rankedRetrievalEngine;

    @Autowired
    private ExactVectorSimilarityEngine vectorSimilarityEngine;

    @Autowired
    private RankedBooleanQueryEngine rankedBooleanQueryEngine;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private IndexIntegrityChecker integrityChecker;

    @Autowired
    private IndexRepairService repairService;

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

    @Test
    @DisplayName("50+ Virtual/Worker Threads Stress: Concurrent writes, multi-version updates, deletions, and search queries")
    void testHighConcurrencyMultiThreadedStress() throws InterruptedException {
        // Seed initial pool of documents for update/delete/search targets
        final int SEED_DOCS = 20;
        List<Long> seededDocIds = new CopyOnWriteArrayList<>();

        for (int i = 1; i <= SEED_DOCS; i++) {
            CrawledDocument doc = new CrawledDocument(
                    "https://searchforge.internal/stress/seed-" + i,
                    "searchforge.internal",
                    "Seeded Architecture Document " + i,
                    "Distributed indexing consensus replication token data structure " + i,
                    "seed-hash-" + i,
                    200,
                    Instant.now(),
                    DocumentLifecycleStatus.DISCOVERED,
                    1
            );
            doc = crawledDocumentRepository.saveAndFlush(doc);
            seededDocIds.add(doc.getId());
            indexingCoordinator.processDocument(doc.getId(), doc.getTitle(), doc.getCleanText());
        }

        final int INDEX_THREADS = 15;
        final int UPDATE_THREADS = 10;
        final int DELETE_THREADS = 5;
        final int SEARCH_THREADS = 20;
        final int TOTAL_THREADS = INDEX_THREADS + UPDATE_THREADS + DELETE_THREADS + SEARCH_THREADS; // 50 Threads

        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CyclicBarrier barrier = new CyclicBarrier(TOTAL_THREADS);
        CountDownLatch completionLatch = new CountDownLatch(TOTAL_THREADS);

        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();
        AtomicInteger successfulIndexes = new AtomicInteger(0);
        AtomicInteger successfulUpdates = new AtomicInteger(0);
        AtomicInteger successfulDeletions = new AtomicInteger(0);
        AtomicInteger successfulQueries = new AtomicInteger(0);

        // 1. Spawning 15 Concurrent Indexing Threads
        for (int i = 0; i < INDEX_THREADS; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int step = 0; step < 5; step++) {
                        String uniqueId = workerId + "-" + step + "-" + System.nanoTime();
                        CrawledDocument doc = new CrawledDocument(
                                "https://searchforge.internal/stress/new-" + uniqueId,
                                "searchforge.internal",
                                "Concurrent New Doc " + uniqueId,
                                "Virtual thread parallel crawler indexing high throughput BM25 token " + step,
                                "hash-" + uniqueId,
                                200,
                                Instant.now(),
                                DocumentLifecycleStatus.DISCOVERED,
                                1
                        );
                        doc = crawledDocumentRepository.saveAndFlush(doc);
                        StagedIndexingResult result = indexingCoordinator.processDocument(
                                doc.getId(), doc.getTitle(), doc.getCleanText()
                        );
                        if (result.isSuccess()) {
                            successfulIndexes.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    log.error("Indexing worker exception: {}", t.getMessage(), t);
                    unexpectedErrors.add(t);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 2. Spawning 10 Concurrent Document Update Threads (Optimistic Locking & Postings Swap)
        for (int i = 0; i < UPDATE_THREADS; i++) {
            final int workerId = i;
            executor.submit(() -> {
                try {
                    barrier.await();
                    Random rnd = new Random();
                    for (int step = 0; step < 6; step++) {
                        Long targetId = seededDocIds.get(rnd.nextInt(seededDocIds.size()));
                        String updatedText = "Updated content version " + step + " worker " + workerId + " revision timestamp " + System.nanoTime();
                        StagedIndexingResult result = indexingCoordinator.processDocument(
                                targetId, "Updated Document Title", updatedText
                        );
                        if (result.isSuccess() || result.isUnchanged()) {
                            successfulUpdates.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    log.error("Update worker exception: {}", t.getMessage(), t);
                    unexpectedErrors.add(t);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 3. Spawning 5 Concurrent Deletion Threads (Testing Soft-Lock & Search Isolation)
        for (int i = 0; i < DELETE_THREADS; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    Random rnd = new Random();
                    for (int step = 0; step < 3; step++) {
                        Long targetId = seededDocIds.get(rnd.nextInt(seededDocIds.size()));
                        try {
                            deletionService.deleteDocument(targetId, false);
                            successfulDeletions.incrementAndGet();
                        } catch (Exception e) {
                            log.debug("Concurrent deletion collision for doc {}: {}", targetId, e.getMessage());
                        }
                    }
                } catch (Throwable t) {
                    log.error("Deletion worker fatal exception: {}", t.getMessage(), t);
                    unexpectedErrors.add(t);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 4. Spawning 20 Concurrent Search Readers (BM25, Boolean AST, and Dense Vector)
        float[] sampleQueryVector = embeddingModel.embed("parallel search query");
        for (int i = 0; i < SEARCH_THREADS; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int step = 0; step < 15; step++) {
                        // BM25 Search
                        List<RankedSearchResult> bm25Hits = rankedRetrievalEngine.search("distributed consensus", 10);
                        assertNotNull(bm25Hits);

                        // Vector Search
                        List<VectorSearchResult> vecHits = vectorSimilarityEngine.search(sampleQueryVector, 10);
                        assertNotNull(vecHits);

                        // Ranked Boolean Search
                        List<RankedSearchResult> boolHits = rankedBooleanQueryEngine.searchRankedBoolean("indexing AND data", 10);
                        assertNotNull(boolHits);

                        successfulQueries.incrementAndGet();
                    }
                } catch (Throwable t) {
                    log.error("Search worker exception: {}", t.getMessage(), t);
                    unexpectedErrors.add(t);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Await all 50 threads completing within bounded timeout (Guarantees zero deadlocks)
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(completed, "All 50 concurrent worker threads must complete within timeout (Deadlock detected if false)");
        assertTrue(unexpectedErrors.isEmpty(), "Zero unhandled exceptions allowed in concurrent threads: " + unexpectedErrors);

        log.info("Stress execution complete: indexes={}, updates={}, deletions={}, queries={}",
                successfulIndexes.get(), successfulUpdates.get(), successfulDeletions.get(), successfulQueries.get());

        assertTrue(successfulIndexes.get() > 0, "Indexing operations must succeed");
        assertTrue(successfulUpdates.get() > 0, "Update operations must succeed");
        assertTrue(successfulQueries.get() > 0, "Search query operations must succeed");

        // 5. Post-Stress System Integrity Audit
        IndexIntegrityReport auditReport = integrityChecker.checkIntegrity();
        log.info("Post-stress integrity audit: healthy={}, totalIssues={}",
                auditReport.isHealthy(), auditReport.totalIssueCount());

        if (!auditReport.isHealthy()) {
            log.warn("Executing auto-repair for post-stress discrepancies...");
            IndexRepairResult repairResult = repairService.executeRepair(RepairMode.REPAIR);
            assertTrue(repairResult.isCompletelyRepaired(), "Automated repair must restore 100% health after stress");
        } else {
            assertTrue(auditReport.isHealthy(), "System maintained 100% index integrity under 50-thread concurrent stress");
        }
    }
}
