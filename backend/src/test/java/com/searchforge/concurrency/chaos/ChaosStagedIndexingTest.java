package com.searchforge.concurrency.chaos;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 9: Chaos Engineering & Failure Injection Test Suite")
class ChaosStagedIndexingTest {

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
    private IndexingEngine indexingEngine;

    @Autowired
    private Tokenizer lexicalTokenizer;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DocumentChangeDetector changeDetector;

    @Autowired
    private HtmlParser htmlParser;

    @Autowired
    private DocumentLockManager documentLockManager;

    @Autowired
    private IndexIntegrityChecker integrityChecker;

    @Autowired
    private IndexRepairService repairService;

    @Autowired
    private TransactionalIndexingCoordinator productionCoordinator;

    private ChaosFailureInjector chaosInjector;

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
        if (chaosInjector != null) {
            chaosInjector.reset();
        }
    }

    @Test
    @DisplayName("Stage 1 Fault Injection: ONNX neural inference crash does not corrupt DB or RAM")
    void testStage1InferenceFailureDoesNotTouchDatabase() {
        chaosInjector = new ChaosFailureInjector();

        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.internal/chaos/stage1-" + System.nanoTime(),
                "searchforge.internal",
                "Stage 1 Chaos Target",
                "Text intended to fail during neural vector calculation",
                "hash-stage1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        Long docId = doc.getId();

        chaosInjector.targetDocument(docId);

        // Wrapped EmbeddingModel that injects failure when docId matches
        EmbeddingModel faultyEmbeddingModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                chaosInjector.maybeInjectFailure(
                        ChaosFailureInjector.FailurePoint.STAGE1_INFERENCE_OOM,
                        docId,
                        new RuntimeException("Simulated ONNX Tensor Runtime OOM Error")
                );
                return embeddingModel.embed(text);
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return embeddingModel.embedBatch(texts);
            }

            @Override
            public int dimensions() {
                return embeddingModel.dimensions();
            }

            @Override
            public String modelName() {
                return embeddingModel.modelName();
            }
        };

        TransactionalIndexingCoordinator chaosCoordinator = new TransactionalIndexingCoordinator(
                crawledDocumentRepository,
                changeDetector,
                lexicalTokenizer,
                faultyEmbeddingModel,
                indexingEngine,
                embeddingRepository,
                vectorStorage,
                htmlParser,
                documentLockManager,
                activeDocumentFilter
        );

        StagedIndexingResult result = chaosCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());

        // Verify result is a failure and exception was intercepted
        assertTrue(result.isFailure(), "Result must report failure on Stage 1 crash");
        assertEquals(1, chaosInjector.getInjectedFailureCount(), "Chaos failure must be injected exactly once");

        // Verify database is completely intact: document is NOT marked ACTIVE
        CrawledDocument uncommittedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertNotEquals(DocumentLifecycleStatus.ACTIVE, uncommittedDoc.getStatus(),
                "Document must not be marked ACTIVE after Stage 1 crash");

        // Verify no postings or embeddings leaked into DB
        assertFalse(metadataRepository.existsByDocumentId(docId), "No metadata must be saved in DB");
        assertTrue(postingRepository.findByDocumentId(docId).isEmpty(), "No postings must be persisted in DB");
        assertTrue(embeddingRepository.findByDocumentId(docId).isEmpty(), "No embeddings must be persisted in DB");

        // Verify RAM is completely clean
        assertFalse(vectorStorage.contains(docId), "Vector storage must not contain failed document");
        assertFalse(activeDocumentFilter.isActive(docId), "Active filter must not contain failed document");
    }

    @Test
    @DisplayName("Stage 3 Fault Injection: Memory sync failure purges vector, sets quarantine alert, and auto-heals")
    void testStage3VectorStorageFaultAndAutoHealing() {
        chaosInjector = new ChaosFailureInjector();

        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.internal/chaos/stage3-" + System.nanoTime(),
                "searchforge.internal",
                "Stage 3 Chaos Target",
                "Text succeeding in relational commit but failing in memory sync",
                "hash-stage3",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        Long docId = doc.getId();

        // Faulty VectorStorage that fails during Stage 3 put
        VectorStorage faultyVectorStorage = new VectorStorage() {
            private final VectorStorage delegate = new InMemoryVectorStorage(384);

            @Override
            public void put(long documentId, float[] vector) {
                if (documentId == docId) {
                    throw new RuntimeException("Simulated JVM direct buffer memory allocation failure");
                }
                delegate.put(documentId, vector);
            }

            @Override
            public void put(DocumentVector documentVector) {
                if (documentVector != null && documentVector.getDocumentId() == docId) {
                    throw new RuntimeException("Simulated JVM direct buffer memory allocation failure");
                }
                delegate.put(documentVector);
            }

            @Override
            public Optional<DocumentVector> putAndGetPrevious(DocumentVector documentVector) {
                return delegate.putAndGetPrevious(documentVector);
            }

            @Override
            public boolean replace(long documentId, DocumentVector expectedOld, DocumentVector newVector) {
                return delegate.replace(documentId, expectedOld, newVector);
            }

            @Override
            public Optional<DocumentVector> get(long documentId) {
                return delegate.get(documentId);
            }

            @Override
            public boolean contains(long documentId) {
                return delegate.contains(documentId);
            }

            @Override
            public boolean remove(long documentId) {
                return delegate.remove(documentId);
            }

            @Override
            public void clear() {
                delegate.clear();
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public int dimensions() {
                return 384;
            }

            @Override
            public List<Long> getAllDocumentIds() {
                return delegate.getAllDocumentIds();
            }

            @Override
            public List<DocumentVector> getAllVectors() {
                return delegate.getAllVectors();
            }
        };

        TransactionalIndexingCoordinator chaosCoordinator = new TransactionalIndexingCoordinator(
                crawledDocumentRepository,
                changeDetector,
                lexicalTokenizer,
                embeddingModel,
                indexingEngine,
                embeddingRepository,
                faultyVectorStorage,
                htmlParser,
                documentLockManager,
                activeDocumentFilter
        );

        StagedIndexingResult result = chaosCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());

        // Assert PARTIAL_SUCCESS with transactional alert
        assertTrue(result.isPartialSuccess(), "Result must be PARTIAL_SUCCESS when Stage 3 memory sync fails");

        // Verify relational commit succeeded
        CrawledDocument committedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.ACTIVE, committedDoc.getStatus());
        assertTrue(committedDoc.getStatusMessage().contains("DESYNCHRONIZED"),
                "Document entity must be flagged with DESYNCHRONIZED quarantine alert");

        // Verify vector storage defensively purged (no partial vector)
        assertFalse(faultyVectorStorage.contains(docId), "Faulty vector storage must be purged to prevent split-brain");

        // Verify active search filter excludes the desynchronized document
        assertFalse(activeDocumentFilter.isActive(docId),
                "ActiveDocumentFilter must quarantine desynchronized document from search queries");

        // ====================================================================
        // Auto-Healing Verification
        // ====================================================================
        // Reconcile via production coordinator into healthy production vector storage
        boolean healed = productionCoordinator.reconcileDocumentVector(docId);
        assertTrue(healed, "Production coordinator must successfully reconcile document vector from persistent entity");

        // Verify healthy state restored
        assertTrue(vectorStorage.contains(docId), "Vector storage must now contain reconciled vector");
        assertTrue(activeDocumentFilter.isActive(docId), "Document must be restored to active in search filter");

        CrawledDocument healedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertNull(healedDoc.getStatusMessage(), "Quarantine alert message must be cleared upon reconciliation");

        // Verify system audit reports complete health
        IndexIntegrityReport report = integrityChecker.checkIntegrity();
        assertTrue(report.isHealthy(), "System must be 100% healthy post-reconciliation");
    }
}
