package com.searchforge.indexer.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.entity.IndexedDocumentMetadata;
import com.searchforge.indexer.entity.IndexedTerm;
import com.searchforge.indexer.entity.TermPosting;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IndexingEngineTest {

    @Autowired
    private IndexingEngine indexingEngine;

    @Autowired
    private InvertedIndex inMemoryIndex;

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private IndexedTermRepository termRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @BeforeEach
    void setUp() {
        inMemoryIndex.clear();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should index a single crawled document and populate both persistent tables and memory cache")
    void shouldIndexSingleCrawledDocumentSuccessfully() {
        CrawledDocument doc = crawledDocumentRepository.save(new CrawledDocument(
                "https://searchforge.dev/intro",
                "searchforge.dev",
                "SearchForge Inverted Index Architecture",
                "Inverted indices map terms to postings containing document IDs and token positions.",
                "hash_abc_1",
                200,
                Instant.now()
        ));

        IndexingResult result = indexingEngine.indexDocument(doc);

        assertTrue(result.success());
        assertEquals(doc.getId(), result.documentId());
        assertTrue(result.tokenCount() > 0);
        assertTrue(result.uniqueTerms() > 0);

        // Verify database persistence
        assertTrue(metadataRepository.existsByDocumentId(doc.getId()));
        IndexedDocumentMetadata metadata = metadataRepository.findById(doc.getId()).orElseThrow();
        assertEquals(result.tokenCount(), metadata.getTokenCount());

        List<TermPosting> docPostings = postingRepository.findByDocumentId(doc.getId());
        assertEquals(result.uniqueTerms(), docPostings.size());

        // Verify In-Memory Inverted Index
        assertEquals(1, inMemoryIndex.getTotalDocuments());
        assertEquals(result.tokenCount(), inMemoryIndex.getDocLength(doc.getId()));
        assertTrue(inMemoryIndex.getPostings("invert").isPresent());
    }

    @Test
    @DisplayName("Should support idempotent re-indexing of an existing document without duplicate key violation")
    void shouldBeIdempotentAndAllowReindexingDocument() {
        CrawledDocument doc = crawledDocumentRepository.save(new CrawledDocument(
                "https://searchforge.dev/page",
                "searchforge.dev",
                "Initial Title",
                "Quick brown foxes jumping.",
                "hash_v1",
                200,
                Instant.now()
        ));

        IndexingResult result1 = indexingEngine.indexDocument(doc);
        assertTrue(result1.success());

        // Now document is re-crawled and has updated content
        doc.setTitle("Updated Title");
        doc.setCleanText("Distributed database clusters scaling horizontally.");
        crawledDocumentRepository.saveAndFlush(doc);

        IndexingResult result2 = indexingEngine.indexDocument(doc);
        assertTrue(result2.success());

        // Verify persistent layer reflects new content and does not duplicate
        assertEquals(1, metadataRepository.count());
        Optional<IndexedTerm> oldTerm = termRepository.findByTerm("fox");
        assertTrue(oldTerm.isEmpty(), "Previous term specific to version 1 should have been removed");

        Optional<IndexedTerm> newTerm = termRepository.findByTerm("distribut");
        assertTrue(newTerm.isPresent(), "New term should be indexed");

        // Verify memory index reflects update
        assertEquals(1, inMemoryIndex.getTotalDocuments());
        assertTrue(inMemoryIndex.getPostings("distribut").isPresent());
        assertFalse(inMemoryIndex.getPostings("fox").isPresent());
    }

    @Test
    @DisplayName("Should synchronize all unindexed documents from crawler repository in batches")
    void shouldSynchronizeUnindexedDocumentsFromCorpus() {
        CrawledDocument doc1 = new CrawledDocument("https://example.com/1", "example.com", "Title 1", "Content Alpha", "h1", 200, Instant.now());
        CrawledDocument doc2 = new CrawledDocument("https://example.com/2", "example.com", "Title 2", "Content Beta", "h2", 200, Instant.now());
        CrawledDocument doc3 = new CrawledDocument("https://example.com/3", "example.com", "Title 3", "Content Gamma", "h3", 200, Instant.now());
        crawledDocumentRepository.saveAllAndFlush(List.of(doc1, doc2, doc3));

        CorpusSyncResult syncResult = indexingEngine.synchronizeCorpus(2);

        assertEquals(3, syncResult.documentsProcessed());
        assertEquals(3, syncResult.documentsIndexed());
        assertEquals(0, syncResult.documentsSkipped());
        assertTrue(syncResult.totalTokensIndexed() > 0);

        assertEquals(3, metadataRepository.count());
        assertEquals(3, inMemoryIndex.getTotalDocuments());

        // A second synchronization pass should find 0 unindexed documents
        CorpusSyncResult secondPass = indexingEngine.synchronizeCorpus(2);
        assertEquals(0, secondPass.documentsProcessed());
        assertEquals(0, secondPass.documentsIndexed());
    }

    @Test
    @DisplayName("Should warm up in-memory index from database state on startup")
    void shouldWarmupInMemoryIndexFromDatabase() {
        CrawledDocument doc1 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.org/a", "test.org", "Java Guide", "Java virtual machine optimization", "ha", 200, Instant.now()
        ));
        CrawledDocument doc2 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.org/b", "test.org", "Python Guide", "Python dynamic interpreter execution", "hb", 200, Instant.now()
        ));

        indexingEngine.indexDocument(doc1);
        indexingEngine.indexDocument(doc2);

        assertEquals(2, inMemoryIndex.getTotalDocuments());

        // Simulate server restart by clearing in-memory index
        inMemoryIndex.clear();
        assertEquals(0, inMemoryIndex.getTotalDocuments());
        assertEquals(0, inMemoryIndex.getVocabularySize());

        // Trigger cold startup warmup
        indexingEngine.warmupInMemoryIndex();

        assertEquals(2, inMemoryIndex.getTotalDocuments());
        assertTrue(inMemoryIndex.getVocabularySize() > 0);
        assertTrue(inMemoryIndex.getPostings("java").isPresent());
        assertTrue(inMemoryIndex.getPostings("python").isPresent());
        assertEquals(metadataRepository.findAverageDocumentLength().orElse(0.0),
                inMemoryIndex.getAverageDocumentLength(), 0.01);
    }

    @Test
    @DisplayName("Should handle empty or blank documents gracefully without failure")
    void shouldHandleEmptyOrBlankDocumentGracefully() {
        CrawledDocument emptyDoc = crawledDocumentRepository.save(new CrawledDocument(
                "https://empty.org", "empty.org", "", "", "hempty", 200, Instant.now()
        ));

        IndexingResult result = indexingEngine.indexDocument(emptyDoc);

        assertTrue(result.success());
        assertEquals(0, result.tokenCount());
        assertEquals(0, result.uniqueTerms());

        assertTrue(metadataRepository.existsByDocumentId(emptyDoc.getId()));
        assertEquals(0, metadataRepository.findById(emptyDoc.getId()).orElseThrow().getTokenCount());
    }

    @Test
    @DisplayName("Should accurately report indexing telemetry and statistics")
    void shouldComputeAccurateIndexingStats() {
        CrawledDocument doc1 = crawledDocumentRepository.save(new CrawledDocument(
                "https://stats.org/1", "stats.org", "Doc One", "Search engine indexing", "s1", 200, Instant.now()
        ));
        CrawledDocument doc2 = crawledDocumentRepository.save(new CrawledDocument(
                "https://stats.org/2", "stats.org", "Doc Two", "Information retrieval ranking", "s2", 200, Instant.now()
        ));

        indexingEngine.indexDocument(doc1);
        indexingEngine.indexDocument(doc2);

        IndexingStats stats = indexingEngine.getStats();

        assertEquals(2, stats.totalIndexedDocuments());
        assertTrue(stats.totalTerms() > 0);
        assertTrue(stats.totalPostings() > 0);
        assertTrue(stats.averageDocumentLength() > 0);
    }
}
