package com.searchforge.lifecycle.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 4: Document Deletion Service Unit Tests")
class DocumentDeletionServiceTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private IndexingEngine indexingEngine;

    @Mock
    private DocumentEmbeddingService embeddingService;

    private DocumentDeletionService deletionService;

    @BeforeEach
    void setUp() {
        deletionService = new DocumentDeletionService(
                crawledDocumentRepository,
                indexingEngine,
                embeddingService
        );
    }

    @Test
    @DisplayName("Hard deletion removes document from lexical index, vector storage, and repository")
    void testHardDeletionSuccess() {
        Long docId = 42L;
        String url = "https://searchforge.dev/page42";
        CrawledDocument doc = new CrawledDocument(
                url, "searchforge.dev", "Title", "Content body", "hash42", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DeletionResult result = deletionService.deleteDocument(docId, true);

        assertNotNull(result);
        assertEquals(DeletionResult.Status.SUCCESS, result.status());
        assertTrue(result.hardDeleted());
        assertEquals(docId, result.documentId());

        // Verify order: soft-lock as DELETING, remove lexical, remove embedding, delete repository
        InOrder inOrder = inOrder(crawledDocumentRepository, indexingEngine, embeddingService);
        inOrder.verify(crawledDocumentRepository).save(doc); // marks DELETING
        inOrder.verify(indexingEngine).removeDocument(docId);
        inOrder.verify(embeddingService).deleteEmbedding(docId);
        inOrder.verify(crawledDocumentRepository).delete(doc);
    }

    @Test
    @DisplayName("Soft deletion purges lexical index and embedding but preserves CrawledDocument in DELETED state")
    void testSoftDeletionSuccess() {
        Long docId = 55L;
        String url = "https://searchforge.dev/page55";
        CrawledDocument doc = new CrawledDocument(
                url, "searchforge.dev", "Title", "Content body", "hash55", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 2
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DeletionResult result = deletionService.deleteDocument(docId, false);

        assertNotNull(result);
        assertEquals(DeletionResult.Status.SUCCESS, result.status());
        assertFalse(result.hardDeleted());
        assertEquals(DocumentLifecycleStatus.DELETED, doc.getStatus());

        verify(indexingEngine).removeDocument(docId);
        verify(embeddingService).deleteEmbedding(docId);
        verify(crawledDocumentRepository, never()).delete(doc);
    }

    @Test
    @DisplayName("Deleting nonexistent document cleans up orphaned index and vector entries and returns NOT_FOUND")
    void testDeleteNonexistentDocument() {
        Long docId = 9999L;
        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.empty());

        DeletionResult result = deletionService.deleteDocument(docId);

        assertEquals(DeletionResult.Status.NOT_FOUND, result.status());
        verify(indexingEngine).removeDocument(docId);
        verify(embeddingService).deleteEmbedding(docId);
    }

    @Test
    @DisplayName("Deleting null document ID returns FAILED result without throwing exception")
    void testDeleteNullId() {
        DeletionResult result = deletionService.deleteDocument(null);
        assertEquals(DeletionResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("Document ID cannot be null"));
        verifyNoInteractions(indexingEngine);
        verifyNoInteractions(embeddingService);
    }
}
