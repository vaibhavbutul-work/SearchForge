package com.searchforge.lifecycle.filter;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.service.DeletionResult;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.startup.StartupIndexReconciliationRunner;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.VectorSearchResult;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 8: Search Isolation (Active-State Filtering) & Startup Crash Reconciliation Tests")
class SearchIsolationAndStartupReconciliationTest {

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private DocumentDeletionService deletionService;

    @Autowired
    private ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    private RankedRetrievalEngine rankedRetrievalEngine;

    @Autowired
    private ExactVectorSimilarityEngine vectorSimilarityEngine;

    @Autowired
    private StartupIndexReconciliationRunner reconciliationRunner;

    @Autowired
    private InvertedIndex invertedIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.clear();
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify BM25 search immediately isolates deleting documents without candidate scoring")
    void testBM25SearchIsolationOnDeletion() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.internal/docs/consensus-" + System.nanoTime(),
                "searchforge.internal",
                "Distributed Consensus Architecture",
                "Raft and Paxos are foundational distributed consensus algorithms for fault-tolerant state machines.",
                "consensus-hash-1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        Long docId = doc.getId();

        StagedIndexingResult indexResult = indexingCoordinator.processDocument(
                docId, doc.getTitle(), doc.getCleanText()
        );
        assertTrue(indexResult.isSuccess(), "Document indexing should succeed");
        assertTrue(activeDocumentFilter.isActive(docId), "Document must be marked ACTIVE in filter");

        // Verify BM25 search returns document
        List<RankedSearchResult> initialHits = rankedRetrievalEngine.search("consensus algorithms", 10);
        assertTrue(initialHits.stream().anyMatch(hit -> hit.docId() == docId),
                "Active document should be retrieved by BM25 query");

        // Soft-delete document via DeletionService
        DeletionResult deletionResult = deletionService.deleteDocument(docId, false);
        assertTrue(deletionResult.isSuccess(), "Deletion should succeed");

        // Active filter should immediately mark inactive
        assertFalse(activeDocumentFilter.isActive(docId),
                "ActiveDocumentFilter must instantaneously report document as inactive upon deletion");

        // Verify BM25 search immediately excludes the deleted document in O(1) time
        List<RankedSearchResult> postDeleteHits = rankedRetrievalEngine.search("consensus algorithms", 10);
        assertFalse(postDeleteHits.stream().anyMatch(hit -> hit.docId() == docId),
                "Deleted document must be completely isolated and excluded from BM25 search hits");
    }

    @Test
    @DisplayName("Verify dense semantic vector search immediately isolates inactive documents")
    void testSemanticVectorSearchIsolationOnInactiveState() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.internal/docs/neural-" + System.nanoTime(),
                "searchforge.internal",
                "Deep Learning Vector Embeddings",
                "High-dimensional embedding representations map linguistic semantics into continuous geometric vector spaces.",
                "neural-hash-1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        Long docId = doc.getId();

        StagedIndexingResult indexResult = indexingCoordinator.processDocument(
                docId, doc.getTitle(), doc.getCleanText()
        );
        assertTrue(indexResult.isSuccess(), "Document indexing should succeed");
        assertTrue(activeDocumentFilter.isActive(docId), "Document must be marked ACTIVE in filter");

        float[] queryVector = embeddingModel.embed("vector space semantics");

        // Query vector similarity engine
        List<VectorSearchResult> initialResults = vectorSimilarityEngine.search(queryVector, 10);
        assertTrue(initialResults.stream().anyMatch(r -> r.documentId() == docId),
                "Active document vector must be retrieved in semantic search");

        // Mark document inactive via active filter
        activeDocumentFilter.markInactive(docId);
        assertFalse(activeDocumentFilter.isActive(docId), "Document must now be inactive");

        // Vector similarity engine must immediately exclude inactive candidate
        List<VectorSearchResult> filteredResults = vectorSimilarityEngine.search(queryVector, 10);
        assertFalse(filteredResults.stream().anyMatch(r -> r.documentId() == docId),
                "Inactive document must be immediately excluded from vector search results");

        // Restore active state
        activeDocumentFilter.markActive(docId);
        assertTrue(activeDocumentFilter.isActive(docId), "Document restored to active");
    }

    @Test
    @DisplayName("Verify startup reconciliation pre-warms indexes, seeds filter, and auto-heals after simulated crash")
    void testStartupReconciliationAndCrashAutoHealing() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.internal/docs/crash-healing-" + System.nanoTime(),
                "searchforge.internal",
                "Crash Recovery and State Reconciliation",
                "SearchForge guarantees transactional integrity across memory restarts and database crashes.",
                "crash-hash-1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        Long docId = doc.getId();

        StagedIndexingResult indexResult = indexingCoordinator.processDocument(
                docId, doc.getTitle(), doc.getCleanText()
        );
        assertTrue(indexResult.isSuccess(), "Document indexing should succeed");

        // Verify document is active in database
        CrawledDocument freshDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.ACTIVE, freshDoc.getStatus());

        // Simulate an ungraceful crash: RAM structures wiped while DB retains state
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.clear();

        assertEquals(0, invertedIndex.getTotalDocuments(), "In-memory inverted index must be empty after crash wipe");
        assertEquals(0, vectorStorage.size(), "In-memory vector storage must be empty after crash wipe");

        // Trigger Startup Crash Reconciliation Runner
        IndexIntegrityReport finalReport = reconciliationRunner.reconcile();

        // 1. Verify ActiveDocumentFilter seeded
        assertTrue(activeDocumentFilter.isActive(docId),
                "Startup reconciliation must seed ActiveDocumentFilter with active document ID");

        // 2. Verify Inverted Index re-warmed into RAM
        assertTrue(invertedIndex.getTotalDocuments() > 0,
                "Inverted index document count must be restored during startup warmup");

        // 3. Verify Vector Storage re-warmed into RAM
        assertTrue(vectorStorage.size() > 0,
                "Vector storage must be re-populated with embeddings during startup warmup");

        // 4. Verify BM25 search works again post-reconciliation
        List<RankedSearchResult> hits = rankedRetrievalEngine.search("reconciliation integrity", 10);
        assertTrue(hits.stream().anyMatch(hit -> hit.docId() == docId),
                "BM25 search must retrieve recovered document after startup reconciliation");

        // 5. Verify system integrity is 100% healthy
        assertTrue(finalReport.isHealthy(), "System must report 100% health after startup reconciliation");
    }
}
