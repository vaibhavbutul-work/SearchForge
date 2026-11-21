package com.searchforge.lifecycle.consistency;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
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
@DisplayName("Task 6: Document Version & Embedding Consistency Validator Unit Tests")
class DocumentEmbeddingConsistencyValidatorTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private DocumentEmbeddingRepository embeddingRepository;

    @Mock
    private VectorStorage vectorStorage;

    @Mock
    private EmbeddingModel embeddingModel;

    private DocumentEmbeddingConsistencyValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(embeddingModel.modelName()).thenReturn("all-MiniLM-L6-v2");
        lenient().when(embeddingModel.dimensions()).thenReturn(384);
        validator = new DocumentEmbeddingConsistencyValidator(
                crawledDocumentRepository,
                embeddingRepository,
                vectorStorage,
                embeddingModel
        );
    }

    @Test
    @DisplayName("Active document with matching embedding returns CURRENT status")
    void testActiveDocumentWithMatchingEmbedding() {
        Long docId = 1L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/1", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 2
        );
        doc.setId(docId);

        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                docId, "all-MiniLM-L6-v2", 384, new byte[384 * 4], "hash123", 2
        );

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.of(entity));
        when(vectorStorage.contains(docId)).thenReturn(true);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertTrue(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.CURRENT, report.status());
        assertEquals(2, report.documentVersion());
        assertEquals(2, report.embeddingVersion());
    }

    @Test
    @DisplayName("Active document without embedding returns MISSING status")
    void testActiveDocumentMissingEmbedding() {
        Long docId = 2L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/2", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.empty());
        when(vectorStorage.contains(docId)).thenReturn(false);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertFalse(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.MISSING, report.status());
    }

    @Test
    @DisplayName("Active document with mismatched content hash or version returns OUTDATED status")
    void testActiveDocumentOutdatedEmbedding() {
        Long docId = 3L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/3", "example.com", "Title New", "Content New", "newHash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 3
        );
        doc.setId(docId);

        // Embedding has old hash and old version 2
        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                docId, "all-MiniLM-L6-v2", 384, new byte[384 * 4], "oldHash", 2
        );

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.of(entity));
        when(vectorStorage.contains(docId)).thenReturn(true);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertFalse(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.OUTDATED, report.status());
    }

    @Test
    @DisplayName("Active document with mismatched model name returns INCOMPATIBLE status")
    void testActiveDocumentIncompatibleModel() {
        Long docId = 4L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/4", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                docId, "bert-base-uncased", 768, new byte[768 * 4], "hash123", 1
        );

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.of(entity));
        when(vectorStorage.contains(docId)).thenReturn(true);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertFalse(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.INCOMPATIBLE, report.status());
    }

    @Test
    @DisplayName("Active document in DB but missing from vector storage returns DESYNCHRONIZED")
    void testActiveDocumentDesynchronized() {
        Long docId = 5L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/5", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                docId, "all-MiniLM-L6-v2", 384, new byte[384 * 4], "hash123", 1
        );

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.of(entity));
        when(vectorStorage.contains(docId)).thenReturn(false);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertFalse(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.DESYNCHRONIZED, report.status());
    }

    @Test
    @DisplayName("Deleted document with residual embedding returns ORPHANED status")
    void testDeletedDocumentWithResidualEmbedding() {
        Long docId = 6L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/6", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.DELETED, 1
        );
        doc.setId(docId);

        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                docId, "all-MiniLM-L6-v2", 384, new byte[384 * 4], "hash123", 1
        );

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.of(entity));
        when(vectorStorage.contains(docId)).thenReturn(true);

        EmbeddingConsistencyReport report = validator.validateDocument(docId);

        assertFalse(report.isConsistent());
        assertEquals(EmbeddingConsistencyStatus.ORPHANED, report.status());
    }

    @Test
    @DisplayName("validateAll detects orphaned database embeddings for non-existent documents")
    void testValidateAllOrphanedDetection() {
        when(crawledDocumentRepository.findAll()).thenReturn(List.of());
        DocumentEmbeddingEntity orphaned = new DocumentEmbeddingEntity(
                999L, "all-MiniLM-L6-v2", 384, new byte[384 * 4], "hash999", 1
        );
        when(embeddingRepository.findAll()).thenReturn(List.of(orphaned));

        List<EmbeddingConsistencyReport> reports = validator.validateAll();

        assertEquals(1, reports.size());
        assertEquals(EmbeddingConsistencyStatus.ORPHANED, reports.get(0).status());
        assertEquals(999L, reports.get(0).documentId());
    }
}
