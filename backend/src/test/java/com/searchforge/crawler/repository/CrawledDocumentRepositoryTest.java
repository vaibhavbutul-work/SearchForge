package com.searchforge.crawler.repository;

import com.searchforge.crawler.model.CrawledDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CrawledDocumentRepositoryTest {

    @Autowired
    private CrawledDocumentRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully persist and retrieve a CrawledDocument by URL")
    void shouldPersistAndRetrieveDocument() {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/java-guide",
                "example.com",
                "Java Guide",
                "Comprehensive Java 21 documentation text.",
                "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                200,
                Instant.now()
        );

        CrawledDocument saved = repository.save(doc);

        assertNotNull(saved.getId(), "Generated ID should not be null after persist");
        assertEquals("https://example.com/java-guide", saved.getUrl());
        assertEquals("example.com", saved.getDomain());
        assertEquals("Java Guide", saved.getTitle());
        assertEquals(200, saved.getHttpStatusCode());

        Optional<CrawledDocument> retrieved = repository.findByUrl("https://example.com/java-guide");
        assertTrue(retrieved.isPresent(), "Document should be found by URL");
        assertEquals("Java Guide", retrieved.get().getTitle());
    }

    @Test
    @DisplayName("Should verify existsByUrl and existsByContentHash query methods")
    void shouldVerifyExistsQueries() {
        String testUrl = "https://example.com/page1";
        String testHash = "hash_unique_abc_123";

        assertFalse(repository.existsByUrl(testUrl));
        assertFalse(repository.existsByContentHash(testHash));

        CrawledDocument doc = new CrawledDocument(
                testUrl,
                "example.com",
                "Page 1",
                "Some page content",
                testHash,
                200,
                Instant.now()
        );
        repository.save(doc);

        assertTrue(repository.existsByUrl(testUrl));
        assertTrue(repository.existsByContentHash(testHash));
        assertFalse(repository.existsByUrl("https://example.com/non-existent"));
        assertFalse(repository.existsByContentHash("hash_different"));
    }

    @Test
    @DisplayName("Should correctly count and filter documents by domain")
    void shouldCountAndFilterByDomain() {
        CrawledDocument doc1 = new CrawledDocument(
                "https://example.com/doc1", "example.com", "Doc 1", "Content 1", "hash1", 200, Instant.now()
        );
        CrawledDocument doc2 = new CrawledDocument(
                "https://example.com/doc2", "example.com", "Doc 2", "Content 2", "hash2", 200, Instant.now()
        );
        CrawledDocument doc3 = new CrawledDocument(
                "https://otherdomain.org/about", "otherdomain.org", "About", "Content 3", "hash3", 200, Instant.now()
        );

        repository.saveAll(List.of(doc1, doc2, doc3));

        assertEquals(2, repository.countByDomain("example.com"));
        assertEquals(1, repository.countByDomain("otherdomain.org"));
        assertEquals(0, repository.countByDomain("nonexistent.com"));

        List<CrawledDocument> exampleDocs = repository.findByDomain("example.com");
        assertEquals(2, exampleDocs.size());
    }

    @Test
    @DisplayName("Should throw DataIntegrityViolationException on duplicate URL due to unique constraint")
    void shouldEnforceUniqueUrlConstraint() {
        CrawledDocument doc1 = new CrawledDocument(
                "https://example.com/duplicate", "example.com", "First Doc", "Content A", "hashA", 200, Instant.now()
        );
        repository.saveAndFlush(doc1);

        CrawledDocument doc2 = new CrawledDocument(
                "https://example.com/duplicate", "example.com", "Second Doc", "Content B", "hashB", 200, Instant.now()
        );

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(doc2));
    }
}
