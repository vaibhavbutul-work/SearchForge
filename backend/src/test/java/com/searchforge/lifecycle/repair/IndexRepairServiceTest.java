package com.searchforge.lifecycle.repair;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingResult;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyStatus;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 8: Automated Repair Mechanism Unit Tests")
class IndexRepairServiceTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private InvertedIndex inMemoryIndex;

    @Mock
    private IndexedDocumentMetadataRepository metadataRepository;

    @Mock
    private TermPostingRepository postingRepository;

    @Mock
    private IndexingEngine indexingEngine;

    @Mock
    private DocumentEmbeddingService documentEmbeddingService;

    @Mock
    private DocumentEmbeddingRepository embeddingRepository;

    @Mock
    private VectorStorage vectorStorage;

    @Mock
    private IndexIntegrityChecker integrityChecker;

    private IndexRepairService repairService;

    @BeforeEach
    void setUp() {
        repairService = new IndexRepairService(
                crawledDocumentRepository,
                inMemoryIndex,
                metadataRepository,
                postingRepository,
                indexingEngine,
                documentEmbeddingService,
                embeddingRepository,
                vectorStorage,
                integrityChecker
        );
    }

    @Test
    @DisplayName("Repair on already healthy index executes 0 changes and returns healthy")
    void testAlreadyHealthyIndex() {
        IndexIntegrityReport healthyReport = new IndexIntegrityReport(
                Instant.now(), 5, 5, 0, 5, List.of(), List.of(), List.of(), List.of()
        );
        when(integrityChecker.checkIntegrity()).thenReturn(healthyReport);

        IndexRepairResult result = repairService.executeRepair(RepairMode.REPAIR);

        assertEquals(0, result.missingDocumentsReindexed());
        assertEquals(0, result.orphanedPostingsPurged());
        assertTrue(result.isCompletelyRepaired());
        verifyNoInteractions(indexingEngine);
        verifyNoInteractions(documentEmbeddingService);
    }

    @Test
    @DisplayName("DRY_RUN mode identifies defects without mutating state")
    void testDryRunMode() {
        EmbeddingConsistencyReport embIssue = new EmbeddingConsistencyReport(
                10L, EmbeddingConsistencyStatus.MISSING, 1, null, "hash", null,
                "all-MiniLM-L6-v2", null, 384, 0, false, false, "Missing"
        );
        IndexIntegrityReport preReport = new IndexIntegrityReport(
                Instant.now(), 5, 4, 1, 2,
                List.of(1L),       // 1 missing lexical doc
                List.of(99L),      // 1 orphaned doc
                List.of("Stats drift"),
                List.of(embIssue)  // 1 missing embedding
        );
        when(integrityChecker.checkIntegrity()).thenReturn(preReport);

        IndexRepairResult result = repairService.executeRepair(RepairMode.DRY_RUN);

        assertEquals(RepairMode.DRY_RUN, result.mode());
        assertEquals(1, result.missingDocumentsReindexed());
        assertEquals(1, result.orphanedPostingsPurged());
        assertEquals(1, result.embeddingsRegenerated());
        assertNull(result.postRepairReport());

        // Verify NO mutations were performed
        verifyNoInteractions(indexingEngine);
        verifyNoInteractions(inMemoryIndex);
        verifyNoInteractions(documentEmbeddingService);
    }

    @Test
    @DisplayName("REPAIR mode re-indexes missing docs, purges orphaned postings, and repairs embeddings")
    void testRepairModeFixesDefects() {
        Long missingDocId = 5L;
        Long orphanedDocId = 99L;
        Long outdatedDocId = 7L;

        CrawledDocument missingDoc = new CrawledDocument(
                "https://example.com/5", "example.com", "Missing Title", "Missing Content", "hash5", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        missingDoc.setId(missingDocId);

        CrawledDocument outdatedDoc = new CrawledDocument(
                "https://example.com/7", "example.com", "Outdated Title", "Outdated Content", "newHash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 2
        );
        outdatedDoc.setId(outdatedDocId);

        EmbeddingConsistencyReport outdatedReport = new EmbeddingConsistencyReport(
                outdatedDocId, EmbeddingConsistencyStatus.OUTDATED, 2, 1, "newHash", "oldHash",
                "all-MiniLM-L6-v2", "all-MiniLM-L6-v2", 384, 384, true, true, "Outdated"
        );

        IndexIntegrityReport preReport = new IndexIntegrityReport(
                Instant.now(), 2, 2, 0, 0,
                List.of(missingDocId),
                List.of(orphanedDocId),
                List.of("Stats issue"),
                List.of(outdatedReport)
        );

        IndexIntegrityReport postReport = new IndexIntegrityReport(
                Instant.now(), 2, 2, 0, 2,
                List.of(), List.of(), List.of(), List.of()
        );

        when(integrityChecker.checkIntegrity())
                .thenReturn(preReport)
                .thenReturn(postReport);

        when(crawledDocumentRepository.findById(missingDocId)).thenReturn(Optional.of(missingDoc));
        when(crawledDocumentRepository.findById(outdatedDocId)).thenReturn(Optional.of(outdatedDoc));
        when(indexingEngine.indexDocument(eq(missingDocId), anyString(), anyString()))
                .thenReturn(IndexingResult.success(missingDocId, 2, 2, 5));

        IndexRepairResult result = repairService.executeRepair(RepairMode.REPAIR);

        assertEquals(1, result.missingDocumentsReindexed());
        assertEquals(1, result.orphanedPostingsPurged());
        assertEquals(1, result.embeddingsRegenerated());
        assertTrue(result.isCompletelyRepaired());

        // Verify actions executed
        verify(indexingEngine).indexDocument(missingDocId, "Missing Title", "Missing Content");
        verify(inMemoryIndex).removeDocument(orphanedDocId);
        verify(metadataRepository).deleteById(orphanedDocId);
        verify(postingRepository).deleteByDocumentId(orphanedDocId);
        verify(documentEmbeddingService).generateAndStoreEmbedding(outdatedDocId, "Outdated Title", "Outdated Content", "newHash", 2);
        verify(indexingEngine).warmupInMemoryIndex();
    }
}
