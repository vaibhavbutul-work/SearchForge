package com.searchforge.lifecycle.transaction;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrentDocumentIndexingTest {

    @Autowired
    private TransactionalIndexingCoordinator coordinator;

    @Autowired
    private DocumentDeletionService deletionService;

    @Autowired
    private CrawledDocumentRepository repository;

    @Autowired
    private DocumentLockManager lockManager;

    @Autowired
    private com.searchforge.indexer.repository.TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository embeddingRepository;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void setUp() {
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        repository.deleteAll();
        lockManager.clear();
    }

    @Test
    @DisplayName("Should prevent lost updates when 10 virtual threads concurrently update the same document")
    void shouldPreventLostUpdatesUnderConcurrentModifications() throws Exception {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/concurrent-doc",
                "example.com",
                "Initial Title",
                "Initial clean body content",
                "hash-initial",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        doc = repository.save(doc);
        Long docId = doc.getId();

        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<StagedIndexingResult> results = new CopyOnWriteArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i + 1;
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize all virtual threads to race simultaneously
                        StagedIndexingResult res = coordinator.processDocument(
                                docId,
                                "Updated Title " + idx,
                                "Unique content payload version " + idx + " with several indexable terms"
                        );
                        results.add(res);
                    } catch (Exception e) {
                        fail("Virtual worker failed unexpectedly: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent updates timed out");
        }

        assertEquals(threadCount, results.size(), "All 10 worker threads must return a result");
        long successCount = results.stream().filter(StagedIndexingResult::isSuccess).count();
        assertTrue(successCount > 0, "At least one update must succeed");

        // Verify database state: clean ACTIVE status, version correctly advanced
        CrawledDocument finalDoc = repository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.ACTIVE, finalDoc.getStatus());
        assertTrue(finalDoc.getVersion() > 1, "Document version must have incremented past 1");
        assertNotNull(finalDoc.getCleanText());
        assertFalse(lockManager.isLocked(docId), "Document lock must be cleanly released");
    }

    @Test
    @DisplayName("Should prevent resurrection of deleted document when update races with deletion")
    void shouldPreventResurrectionOfDeletedDocument() throws Exception {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/delete-race-doc",
                "example.com",
                "Doomed Page",
                "This page will be deleted concurrently",
                "hash-doomed",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        doc = repository.save(doc);
        Long docId = doc.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger updateSuccess = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Worker 1: Deletion
            executor.submit(() -> {
                try {
                    barrier.await();
                    deletionService.deleteDocument(docId, true);
                } catch (Exception e) {
                    fail("Deletion thread failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            // Worker 2: Simultaneous update
            executor.submit(() -> {
                try {
                    barrier.await();
                    StagedIndexingResult res = coordinator.processDocument(
                            docId,
                            "Resurrected Title",
                            "Attempting to overwrite deleted document"
                    );
                    if (res.isSuccess()) {
                        updateSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Handled gracefully
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        // Regardless of which thread won the lock first:
        // If deletion won first, document is deleted and update failed.
        // If update won first, update completed and deletion followed, leaving document deleted.
        // Either way, the final state must NEVER be a resurrected document!
        assertTrue(repository.findById(docId).isEmpty(), "Deleted document must not exist in repository");
        assertFalse(lockManager.isLocked(docId), "Document lock must be cleanly released");
    }

    @Test
    @DisplayName("Should process identical concurrent re-indexing idempotently without redundant commits")
    void shouldHandleConcurrentIdenticalUpdatesIdempotently() throws Exception {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/idempotent-doc",
                "example.com",
                "Static Title",
                "Identical body content for all workers",
                "hash-static",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                2
        );
        doc = repository.save(doc);
        Long docId = doc.getId();

        // Prime the document with its actual SHA-256 hash
        coordinator.processDocument(docId, "Static Title", "Identical body content for all workers");

        int threadCount = 5;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<StagedIndexingResult> results = new CopyOnWriteArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                        StagedIndexingResult res = coordinator.processDocument(
                                docId,
                                "Static Title",
                                "Identical body content for all workers"
                        );
                        results.add(res);
                    } catch (Exception e) {
                        fail("Worker failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertEquals(threadCount, results.size());
        // All subsequent identical calls must be recognized as UNCHANGED
        long unchangedCount = results.stream().filter(StagedIndexingResult::isUnchanged).count();
        assertEquals(threadCount, unchangedCount, "All identical concurrent runs must be recognized as UNCHANGED");
    }

    @Test
    @DisplayName("Should allow independent parallel execution for distinct document IDs without lock contention")
    void shouldAllowIndependentParallelExecutionForDifferentDocuments() throws Exception {
        Long docA = 101L;
        Long docB = 202L;

        CountDownLatch threadAStarted = new CountDownLatch(1);
        CountDownLatch threadBFinished = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Thread A acquires lock on Doc A and holds it for 150ms
            executor.submit(() -> {
                lockManager.withLock(docA, () -> {
                    threadAStarted.countDown();
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });

            // Thread B acquires lock on Doc B: should complete immediately without waiting on Doc A
            executor.submit(() -> {
                try {
                    assertTrue(threadAStarted.await(1, TimeUnit.SECONDS));
                    long start = System.currentTimeMillis();
                    lockManager.withLock(docB, () -> {
                        // Fast operation
                    });
                    long duration = System.currentTimeMillis() - start;
                    assertTrue(duration < 100, "Doc B must not wait for Doc A; took " + duration + "ms");
                } catch (Exception e) {
                    fail("Thread B error: " + e.getMessage());
                } finally {
                    threadBFinished.countDown();
                }
            });

            assertTrue(threadBFinished.await(2, TimeUnit.SECONDS));
        }
    }
}
