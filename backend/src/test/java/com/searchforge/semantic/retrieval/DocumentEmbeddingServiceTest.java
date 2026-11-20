package com.searchforge.semantic.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorSerialization;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentEmbeddingService Unit Tests")
class DocumentEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private VectorStorage vectorStorage;

    @Mock
    private DocumentEmbeddingRepository embeddingRepository;

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    private DocumentEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingModel = new DeterministicEmbeddingModel(384, 256);
        vectorStorage = new InMemoryVectorStorage(384);
        service = new DocumentEmbeddingService(embeddingModel, vectorStorage, embeddingRepository, crawledDocumentRepository);
    }

    @Test
    @DisplayName("Should build weighted document representation prioritizing title")
    void testBuildDocumentRepresentation() {
        String title = "Java Concurrency";
        String content = "Virtual threads reduce the effort of writing high throughput applications.";

        String representation = service.buildDocumentRepresentation(title, content);

        assertNotNull(representation);
        assertTrue(representation.startsWith("Java Concurrency. Java Concurrency."));
        assertTrue(representation.contains("Virtual threads"));

        // Fallback on empty input
        assertEquals("empty document", service.buildDocumentRepresentation(null, ""));
    }

    @Test
    @DisplayName("Should generate, normalize, and store embedding in memory and repository")
    void testGenerateAndStoreEmbedding() {
        when(embeddingRepository.findByDocumentId(1L)).thenReturn(Optional.empty());

        DocumentVector docVec = service.generateAndStoreEmbedding(1L, "PostgreSQL Indexing", "B-tree internals.");

        assertNotNull(docVec);
        assertEquals(1L, docVec.getDocumentId());
        assertEquals(384, docVec.getDimensions());
        assertTrue(docVec.isUnitNormalized());

        // Verify In-Memory storage
        assertTrue(vectorStorage.contains(1L));
        assertEquals(docVec, vectorStorage.get(1L).orElse(null));

        // Verify repository interaction
        verify(embeddingRepository, times(1)).save(any(DocumentEmbeddingEntity.class));
    }

    @Test
    @DisplayName("Should reject null or invalid document entity")
    void testInvalidDocumentEntity() {
        assertThrows(IllegalArgumentException.class, () -> service.generateAndStoreEmbedding(null));
        assertThrows(IllegalArgumentException.class, () -> service.generateAndStoreEmbedding(-1L, "title", "content"));
    }

    @Test
    @DisplayName("Should synchronize embeddings for unindexed documents")
    void testSyncCorpusEmbeddings() {
        CrawledDocument doc1 = new CrawledDocument("https://example.com/1", "example.com", "Title 1", "Body 1", "h1", 200, Instant.now());
        // Use reflection to set ID
        setId(doc1, 100L);

        when(crawledDocumentRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(doc1)));
        when(embeddingRepository.findByDocumentId(100L)).thenReturn(Optional.empty());

        int count = service.syncCorpusEmbeddings(10);
        assertEquals(1, count);
        assertTrue(vectorStorage.contains(100L));
    }

    @Test
    @DisplayName("Should warmup in-memory storage from persisted database records")
    void testWarmupStorageFromDatabase() {
        float[] vector = new float[384];
        vector[0] = 1.0f;
        byte[] binary = VectorSerialization.toByteArray(vector);

        DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(55L, embeddingModel.modelName(), 384, binary);
        when(embeddingRepository.findAll()).thenReturn(List.of(entity));

        int loaded = service.warmupStorageFromDatabase();
        assertEquals(1, loaded);
        assertTrue(vectorStorage.contains(55L));
        assertEquals(1.0f, vectorStorage.get(55L).get().get(0), 1e-4f);
    }

    @Test
    @DisplayName("Should skip incompatible model vectors during warmup")
    void testWarmupSkipsIncompatibleModelVectors() {
        float[] vector = new float[384];
        byte[] binary = VectorSerialization.toByteArray(vector);

        DocumentEmbeddingEntity incompatibleEntity = new DocumentEmbeddingEntity(99L, "legacy-hash-v1", 384, binary);
        when(embeddingRepository.findAll()).thenReturn(List.of(incompatibleEntity));

        int loaded = service.warmupStorageFromDatabase();
        assertEquals(0, loaded);
        assertFalse(vectorStorage.contains(99L));
    }

    private void setId(CrawledDocument doc, Long id) {
        try {
            java.lang.reflect.Field field = CrawledDocument.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(doc, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
