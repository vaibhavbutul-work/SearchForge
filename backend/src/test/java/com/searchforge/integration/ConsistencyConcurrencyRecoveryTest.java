package com.searchforge.integration;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.startup.StartupIndexReconciliationRunner;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.HybridSearchService;
import com.searchforge.search.hybrid.SearchMode;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("regression")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Task 14.6: Data Consistency, Concurrency & Recovery Integration Suite")
class ConsistencyConcurrencyRecoveryTest {

    @Autowired private CrawledDocumentRepository crawledDocumentRepository;
    @Autowired private TermPostingRepository termPostingRepository;
    @Autowired private IndexedTermRepository indexedTermRepository;
    @Autowired private IndexedDocumentMetadataRepository metadataRepository;
    @Autowired private DocumentEmbeddingRepository embeddingRepository;
    @Autowired private InvertedIndex invertedIndex;
    @Autowired private VectorStorage vectorStorage;
    @Autowired private ActiveDocumentFilter activeDocumentFilter;
    @Autowired private IndexingEngine indexingEngine;
    @Autowired private DocumentEmbeddingService documentEmbeddingService;
    @Autowired private DocumentDeletionService documentDeletionService;
    @Autowired private HybridSearchService hybridSearchService;
    @Autowired private IndexIntegrityChecker integrityChecker;
    @Autowired private IndexRepairService repairService;
    @Autowired private StartupIndexReconciliationRunner reconciliationRunner;
    @Autowired private TransactionalIndexingCoordinator indexingCoordinator;

    @BeforeEach
    void resetAllState() {
        termPostingRepository.deleteAll();
        indexedTermRepository.deleteAll();
        metadataRepository.deleteAll();
        embeddingRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.initialize(List.of());
    }

    private CrawledDocument persistAndIndex(String url, String title, String text) {
        CrawledDocument doc = new CrawledDocument(
                url, "example.com", title, text, "hash-" + url.hashCode(),
                200, Instant.now(), DocumentLifecycleStatus.FETCHED, 1);
        CrawledDocument saved = crawledDocumentRepository.save(doc);
        StagedIndexingResult res = indexingCoordinator.processDocument(saved.getId(), title, text);
        assertTrue(res.isSuccess(), "TransactionalIndexingCoordinator must succeed for " + url);
        return crawledDocumentRepository.findById(saved.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Scenario A: Concurrent search readers and indexing writers maintain snapshot safety")
    void scenarioAConcurrentSearchAndIndexing() throws Exception {
        persistAndIndex("https://example.com/base", "Base Concurrency Doc", "Concurrencykeyword baseline search.");

        int readers = 4;
        int writers = 4;
        CyclicBarrier barrier = new CyclicBarrier(readers + writers);
        ExecutorService pool = Executors.newFixedThreadPool(readers + writers);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < readers; i++) {
            futures.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                for (int q = 0; q < 10; q++) {
                    HybridSearchResult res = hybridSearchService.search("Concurrencykeyword", SearchMode.HYBRID, 10);
                    assertNotNull(res);
                    assertFalse(res.candidates().isEmpty());
                }
                return null;
            }));
        }

        for (int i = 0; i < writers; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                persistAndIndex("https://example.com/writer-" + idx, "Writer " + idx,
                        "Concurrencykeyword indexed concurrently by writer " + idx);
                return null;
            }));
        }

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        HybridSearchResult finalRes = hybridSearchService.search("Concurrencykeyword", SearchMode.LEXICAL, 20);
        assertEquals(1 + writers, finalRes.candidates().size());
    }

    @Test
    @DisplayName("Scenario B: Concurrent document updates trigger OptimisticLockingFailureException without lost updates")
    void scenarioBConcurrentDocumentUpdatesOptimisticLocking() throws Exception {
        CrawledDocument initial = persistAndIndex("https://example.com/opt-lock", "Initial", "Initial content");

        CyclicBarrier loadBarrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        Callable<Void> updater = () -> {
            CrawledDocument loaded = crawledDocumentRepository.findById(initial.getId()).orElseThrow();
            loadBarrier.await(5, TimeUnit.SECONDS);
            loaded.setTitle("Updated by " + Thread.currentThread().getName());
            try {
                crawledDocumentRepository.saveAndFlush(loaded);
                successes.incrementAndGet();
            } catch (OptimisticLockingFailureException ex) {
                conflicts.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(updater);
        Future<Void> f2 = pool.submit(updater);
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successes.get(), "Exactly one concurrent update must succeed");
        assertEquals(1, conflicts.get(), "Second concurrent update must fail with OptimisticLockingFailureException");
    }

    @Test
    @DisplayName("Scenario C: Concurrent deletion and search never leak deleted documents once deletion completes")
    void scenarioCConcurrentDeletionAndSearch() throws Exception {
        CrawledDocument d1 = persistAndIndex("https://example.com/del-1", "Delete One", "Deleteconcurrentkeyword one");
        CrawledDocument d2 = persistAndIndex("https://example.com/del-2", "Keep Two", "Deleteconcurrentkeyword two");

        CyclicBarrier barrier = new CyclicBarrier(3);
        ExecutorService pool = Executors.newFixedThreadPool(3);

        Future<?> deleter = pool.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            assertTrue(documentDeletionService.deleteDocument(d1.getId()).isSuccess());
            return null;
        });

        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            readers.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                for (int j = 0; j < 10; j++) {
                    HybridSearchResult res = hybridSearchService.search("Deleteconcurrentkeyword", SearchMode.HYBRID, 10);
                    assertNotNull(res);
                }
                return null;
            }));
        }

        deleter.get(10, TimeUnit.SECONDS);
        for (Future<?> r : readers) {
            r.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // After deletion visibility, d1 must never appear; d2 remains searchable
        HybridSearchResult afterDelete = hybridSearchService.search("Deleteconcurrentkeyword", SearchMode.HYBRID, 10);
        assertEquals(1, afterDelete.candidates().size());
        assertEquals(d2.getId(), afterDelete.candidates().get(0).docId());
    }

    @Test
    @DisplayName("Scenario D & F: Vector/index failure detection, repair, and restart reconciliation restore consistency")
    void scenarioDFVectorFailureRepairAndRestartReconciliation() {
        CrawledDocument doc = persistAndIndex("https://example.com/vec-repair", "Vector Repair Doc",
                "Consistencyrepairkeyword tests vector and index self-healing.");

        // Inject vector storage + DB embedding loss
        vectorStorage.remove(doc.getId());
        embeddingRepository.deleteAll();

        IndexIntegrityReport broken = integrityChecker.checkIntegrity();
        assertFalse(broken.isHealthy(), "Integrity checker must detect missing embedding");

        IndexRepairResult repair = repairService.executeRepair(RepairMode.REPAIR);
        assertTrue(repair.isCompletelyRepaired());
        assertTrue(vectorStorage.contains(doc.getId()));

        // Simulate full JVM restart
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.initialize(List.of());

        IndexIntegrityReport reconciled = reconciliationRunner.reconcile();
        assertTrue(reconciled.isHealthy());
        HybridSearchResult res = hybridSearchService.search("Consistencyrepairkeyword", SearchMode.HYBRID, 5);
        assertEquals(1, res.candidates().size());
    }
}
