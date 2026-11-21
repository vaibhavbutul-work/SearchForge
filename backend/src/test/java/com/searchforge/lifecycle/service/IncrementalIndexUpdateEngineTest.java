package com.searchforge.lifecycle.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.parser.ParsedDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.crawler.url.UrlNormalizer;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingResult;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
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
@DisplayName("Task 3: Incremental Index Update Engine Unit Tests")
class IncrementalIndexUpdateEngineTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private IndexingEngine indexingEngine;

    @Mock
    private DocumentEmbeddingService embeddingService;

    private UrlNormalizer urlNormalizer;
    private HtmlParser htmlParser;
    private DocumentChangeDetector changeDetector;
    private IncrementalIndexUpdateEngine updateEngine;

    @BeforeEach
    void setUp() {
        urlNormalizer = new UrlNormalizer();
        htmlParser = new HtmlParser(urlNormalizer);
        changeDetector = new DocumentChangeDetector(crawledDocumentRepository, urlNormalizer, htmlParser);
        updateEngine = new IncrementalIndexUpdateEngine(
                crawledDocumentRepository,
                changeDetector,
                indexingEngine,
                embeddingService,
                htmlParser
        );
    }

    @Test
    @DisplayName("Incrementally updating an existing document with modified text updates version, lexical index, and embedding")
    void testIncrementalUpdateModifiedDocument() {
        Long docId = 42L;
        String url = "https://searchforge.dev/doc1";
        String oldTitle = "Initial Title";
        String oldContent = "Initial content about Java 17 and virtual machines.";
        String oldHash = htmlParser.computeSha256(oldContent);

        CrawledDocument existingDoc = new CrawledDocument(
                url, "searchforge.dev", oldTitle, oldContent, oldHash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        existingDoc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(existingDoc));
        when(indexingEngine.indexDocument(eq(docId), anyString(), anyString()))
                .thenReturn(IndexingResult.success(docId, 15, 8, 10));

        String newTitle = "Updated Title";
        String newContent = "Updated content about Java 21, Project Loom, and modern virtual threads.";
        String newHash = htmlParser.computeSha256(newContent);

        IncrementalUpdateResult result = updateEngine.updateDocument(docId, newTitle, newContent);

        assertNotNull(result);
        assertEquals(IncrementalUpdateResult.Status.SUCCESS, result.status());
        assertEquals(1, result.previousVersion());
        assertEquals(2, result.newVersion());
        assertTrue(result.embeddingUpdated());
        assertEquals(DocumentLifecycleStatus.ACTIVE, existingDoc.getStatus());
        assertEquals(2, existingDoc.getVersion());
        assertEquals(newHash, existingDoc.getContentHash());

        // Verify that lexical index was reindexed for this specific document
        verify(indexingEngine).indexDocument(docId, newTitle, newContent);

        // Verify that embedding was refreshed with the new hash and incremented version
        verify(embeddingService).generateAndStoreEmbedding(docId, newTitle, newContent, newHash, 2);
    }

    @Test
    @DisplayName("Updating a document with identical content returns UNCHANGED and skips reindexing")
    void testIncrementalUpdateUnchangedContent() {
        Long docId = 42L;
        String url = "https://searchforge.dev/doc1";
        String title = "Same Title";
        String content = "Same unchanging text.";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument existingDoc = new CrawledDocument(
                url, "searchforge.dev", title, content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 3
        );
        existingDoc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(existingDoc));

        IncrementalUpdateResult result = updateEngine.updateDocument(docId, title, content);

        assertEquals(IncrementalUpdateResult.Status.UNCHANGED, result.status());
        assertEquals(3, result.previousVersion());
        assertEquals(3, result.newVersion());
        assertFalse(result.embeddingUpdated());

        // Neither lexical indexing nor embedding should be called
        verifyNoInteractions(indexingEngine);
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("Lexical indexing failure marks document as FAILED and returns FAILED result")
    void testIncrementalUpdateIndexingFailure() {
        Long docId = 99L;
        String url = "https://searchforge.dev/doc99";
        CrawledDocument existingDoc = new CrawledDocument(
                url, "searchforge.dev", "Title", "Old text", "oldhash", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        existingDoc.setId(docId);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(existingDoc));
        when(indexingEngine.indexDocument(eq(docId), anyString(), anyString()))
                .thenReturn(IndexingResult.failure(docId, "Disk I/O error", 5));

        IncrementalUpdateResult result = updateEngine.updateDocument(docId, "New Title", "New Text");

        assertEquals(IncrementalUpdateResult.Status.FAILED, result.status());
        assertEquals(DocumentLifecycleStatus.FAILED, existingDoc.getStatus());
        assertTrue(existingDoc.getStatusMessage().contains("Disk I/O error"));

        // Embedding should not be updated if lexical indexing failed
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("Processing a brand new ParsedDocument from crawler creates, indexes, and activates it")
    void testProcessNewParsedDocument() {
        String url = "https://searchforge.dev/new-page";
        String content = "Brand new documentation page.";
        String hash = htmlParser.computeSha256(content);

        ParsedDocument parsed = new ParsedDocument(
                url, "searchforge.dev", "New Page", content, hash, 200, List.of(), Instant.now()
        );

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.empty());
        when(crawledDocumentRepository.existsByContentHash(hash)).thenReturn(false);

        CrawledDocument savedDoc = new CrawledDocument(
                url, "searchforge.dev", "New Page", content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.PROCESSING, 1
        );
        savedDoc.setId(77L);

        when(crawledDocumentRepository.save(any(CrawledDocument.class))).thenReturn(savedDoc);
        when(indexingEngine.indexDocument(savedDoc)).thenReturn(IndexingResult.success(77L, 10, 6, 8));

        IncrementalUpdateResult result = updateEngine.processParsedDocument(parsed);

        assertEquals(IncrementalUpdateResult.Status.SUCCESS, result.status());
        assertEquals(0, result.previousVersion());
        assertEquals(1, result.newVersion());
        assertEquals(77L, result.documentId());

        verify(indexingEngine).indexDocument(savedDoc);
        verify(embeddingService).generateAndStoreEmbedding(eq(77L), eq("New Page"), eq(content), eq(hash), eq(1));
    }
}
