package com.searchforge.lifecycle.transaction;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.crawler.url.UrlNormalizer;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingResult;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 5: Transactional Indexing Coordinator Unit Tests")
class TransactionalIndexingCoordinatorTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private Tokenizer lexicalTokenizer;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private IndexingEngine indexingEngine;

    @Mock
    private DocumentEmbeddingRepository embeddingRepository;

    @Mock
    private VectorStorage vectorStorage;

    private UrlNormalizer urlNormalizer;
    private HtmlParser htmlParser;
    private DocumentChangeDetector changeDetector;
    private TransactionalIndexingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        urlNormalizer = new UrlNormalizer();
        htmlParser = new HtmlParser(urlNormalizer);
        changeDetector = new DocumentChangeDetector(crawledDocumentRepository, urlNormalizer, htmlParser);
        coordinator = new TransactionalIndexingCoordinator(
                crawledDocumentRepository,
                changeDetector,
                lexicalTokenizer,
                embeddingModel,
                indexingEngine,
                embeddingRepository,
                vectorStorage,
                htmlParser
        );
    }

    @Test
    @DisplayName("Successful staged execution executes Stage 1 pre-computation and Stage 2 atomic relational commit")
    void testSuccessfulStagedIndexing() {
        Long docId = 42L;
        String title = "Java 21 Features";
        String content = "Virtual threads and pattern matching.";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument doc = new CrawledDocument(
                "https://example.com/java21", "example.com", "Old Title", "Old Content", "oldhash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(lexicalTokenizer.tokenize(anyString())).thenReturn(List.of(
                new ProcessedToken("java", 0),
                new ProcessedToken("virtual", 1)
        ));

        float[] mockVector = new float[384];
        mockVector[0] = 0.5f;
        when(embeddingModel.embed(anyString())).thenReturn(mockVector);
        when(embeddingModel.modelName()).thenReturn("all-MiniLM-L6-v2");
        when(embeddingModel.dimensions()).thenReturn(384);

        when(indexingEngine.indexDocument(eq(docId), eq(title), eq(content)))
                .thenReturn(IndexingResult.success(docId, 2, 2, 5));
        when(embeddingRepository.findByDocumentId(docId)).thenReturn(Optional.empty());

        StagedIndexingResult result = coordinator.processDocument(docId, title, content);

        assertNotNull(result);
        assertEquals(StagedIndexingResult.Status.SUCCESS, result.status());
        assertEquals(2, result.version());
        assertEquals(DocumentLifecycleStatus.ACTIVE, doc.getStatus());
        assertEquals(hash, doc.getContentHash());

        // Verify relational commit
        verify(indexingEngine).indexDocument(docId, title, content);
        verify(embeddingRepository).save(any(DocumentEmbeddingEntity.class));
        verify(vectorStorage).put(any());
    }

    @Test
    @DisplayName("Stage 1 model inference failure preserves previous document state without attempting database commit")
    void testStage1EmbeddingFailure() {
        Long docId = 42L;
        String title = "Java 21 Features";
        String content = "Virtual threads.";

        CrawledDocument doc = new CrawledDocument(
                "https://example.com/java21", "example.com", "Old Title", "Old Content", "oldhash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(lexicalTokenizer.tokenize(anyString())).thenReturn(List.of(new ProcessedToken("java", 0)));
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("ONNX Out of Memory"));

        StagedIndexingResult result = coordinator.processDocument(docId, title, content);

        assertEquals(StagedIndexingResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("ONNX Out of Memory"));

        // Stage 2 relational commit was NOT invoked
        verifyNoInteractions(indexingEngine);
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    @DisplayName("Stage 2 relational indexing failure records failure and aborts commit")
    void testStage2DatabaseFailure() {
        Long docId = 42L;
        String title = "Title";
        String content = "Content";

        CrawledDocument doc = new CrawledDocument(
                "https://example.com/doc", "example.com", "Old Title", "Old Content", "oldhash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(lexicalTokenizer.tokenize(anyString())).thenReturn(List.of(new ProcessedToken("term", 0)));
        when(embeddingModel.embed(anyString())).thenReturn(new float[384]);

        when(indexingEngine.indexDocument(eq(docId), eq(title), eq(content)))
                .thenReturn(IndexingResult.failure(docId, "Database connection reset", 10));

        StagedIndexingResult result = coordinator.processDocument(docId, title, content);

        assertEquals(StagedIndexingResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("Database connection reset"));
    }

    @Test
    @DisplayName("Idempotent check skips processing when content is identical to active document")
    void testIdempotentReprocessing() {
        Long docId = 42L;
        String title = "Same Title";
        String content = "Same Content";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument doc = new CrawledDocument(
                "https://example.com/doc", "example.com", title, content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 3
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        StagedIndexingResult result = coordinator.processDocument(docId, title, content);

        assertEquals(StagedIndexingResult.Status.UNCHANGED, result.status());
        assertEquals(3, result.version());

        verifyNoInteractions(lexicalTokenizer);
        verifyNoInteractions(embeddingModel);
        verifyNoInteractions(indexingEngine);
    }
}
