package com.searchforge.lifecycle.change;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.crawler.url.UrlNormalizer;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 2: Document Change Detector Unit Tests")
class DocumentChangeDetectorTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    private UrlNormalizer urlNormalizer;
    private HtmlParser htmlParser;
    private DocumentChangeDetector detector;

    @BeforeEach
    void setUp() {
        urlNormalizer = new UrlNormalizer();
        htmlParser = new HtmlParser(urlNormalizer);
        detector = new DocumentChangeDetector(crawledDocumentRepository, urlNormalizer, htmlParser);
    }

    @Test
    @DisplayName("First crawl of brand new URL returns NEW_DOCUMENT requiring reindexing")
    void testFirstCrawlNewDocument() {
        String url = "https://example.com/java-spring";
        String content = "Spring Boot application framework for Java 21.";
        String hash = htmlParser.computeSha256(content);

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.empty());
        when(crawledDocumentRepository.existsByContentHash(hash)).thenReturn(false);

        ChangeDetectionResult result = detector.detectChange(url, "Spring Boot", content, null);

        assertNotNull(result);
        assertEquals(ChangeType.NEW_DOCUMENT, result.changeType());
        assertTrue(result.requiresReindexing());
        assertEquals(1, result.targetVersion());
        assertNull(result.existingDocumentId());
        assertEquals(hash, result.contentHash());
    }

    @Test
    @DisplayName("Same URL with identical content returns UNCHANGED and skips reindexing")
    void testSameUrlSameContent() {
        String url = "https://example.com/java-spring";
        String content = "Spring Boot application framework for Java 21.";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument existing = new CrawledDocument(
                url, "example.com", "Spring Boot", content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        existing.setId(42L);

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.of(existing));

        ChangeDetectionResult result = detector.detectChange(url, "Spring Boot", content, hash);

        assertNotNull(result);
        assertEquals(ChangeType.UNCHANGED, result.changeType());
        assertFalse(result.requiresReindexing());
        assertEquals(42L, result.existingDocumentId());
        assertEquals(1, result.currentVersion());
        assertEquals(1, result.targetVersion());
        assertFalse(result.titleChanged());
    }

    @Test
    @DisplayName("Same URL with modified content returns MODIFIED and increments target version")
    void testSameUrlModifiedContent() {
        String url = "https://example.com/java-spring";
        String oldContent = "Spring Boot application framework for Java 17.";
        String oldHash = htmlParser.computeSha256(oldContent);

        String newContent = "Spring Boot application framework for Java 21 LTS with Virtual Threads.";
        String newHash = htmlParser.computeSha256(newContent);

        CrawledDocument existing = new CrawledDocument(
                url, "example.com", "Spring Boot", oldContent, oldHash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 3
        );
        existing.setId(101L);

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.of(existing));

        ChangeDetectionResult result = detector.detectChange(url, "Spring Boot Guide", newContent, newHash);

        assertNotNull(result);
        assertEquals(ChangeType.MODIFIED, result.changeType());
        assertTrue(result.requiresReindexing());
        assertEquals(101L, result.existingDocumentId());
        assertEquals(3, result.currentVersion());
        assertEquals(4, result.targetVersion());
        assertEquals(newHash, result.contentHash());
    }

    @Test
    @DisplayName("Same URL with unchanged text but changed title marks titleChanged=true but UNCHANGED")
    void testSameUrlTitleChangedOnly() {
        String url = "https://example.com/java-spring";
        String content = "Spring Boot application framework for Java 21.";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument existing = new CrawledDocument(
                url, "example.com", "Old Title", content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 2
        );
        existing.setId(55L);

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.of(existing));

        ChangeDetectionResult result = detector.detectChange(url, "Updated New Title", content, hash);

        assertEquals(ChangeType.UNCHANGED, result.changeType());
        assertFalse(result.requiresReindexing());
        assertTrue(result.titleChanged());
        assertEquals(2, result.targetVersion());
    }

    @Test
    @DisplayName("New URL containing identical content hash to another URL is flagged as DUPLICATE")
    void testDifferentUrlIdenticalContent() {
        String newUrl = "https://example.com/mirror-page";
        String content = "Standard PostgreSQL ACID isolation levels.";
        String hash = htmlParser.computeSha256(content);

        when(crawledDocumentRepository.findByUrl(anyString())).thenReturn(Optional.empty());
        when(crawledDocumentRepository.existsByContentHash(hash)).thenReturn(true);

        ChangeDetectionResult result = detector.detectChange(newUrl, "PostgreSQL ACID", content, hash);

        assertNotNull(result);
        assertEquals(ChangeType.DUPLICATE_CONTENT_DIFFERENT_URL, result.changeType());
        assertFalse(result.requiresReindexing());
        assertNull(result.targetVersion());
    }

    @Test
    @DisplayName("URL normalization canonicalizes scheme, default ports, and trailing fragments")
    void testUrlNormalizationConsistency() {
        String raw1 = "http://EXAMPLE.com:80/path#section";
        String raw2 = "http://example.com/path";
        String content = "Sample text content";
        String hash = htmlParser.computeSha256(content);

        CrawledDocument existing = new CrawledDocument(
                raw2, "example.com", "Title", content, hash, 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        existing.setId(12L);

        when(crawledDocumentRepository.findByUrl("http://example.com/path")).thenReturn(Optional.of(existing));

        // raw1 with uppercase and port:80 should normalize to raw2
        ChangeDetectionResult result = detector.detectChange(raw1, "Title", content, hash);

        assertEquals("http://example.com/path", result.normalizedUrl());
        assertEquals(ChangeType.UNCHANGED, result.changeType());
    }

    @Test
    @DisplayName("Content hash stability: SHA-256 is deterministic across multiple invocations")
    void testContentHashStability() {
        String text = "Deterministic search engine indexing verification.";
        String hash1 = htmlParser.computeSha256(text);
        String hash2 = htmlParser.computeSha256(text);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    @DisplayName("Invalid empty or null URL throws IllegalArgumentException")
    void testInvalidUrlInput() {
        assertThrows(IllegalArgumentException.class, () -> detector.detectChange(null, "Title", "Text", "hash"));
        assertThrows(IllegalArgumentException.class, () -> detector.detectChange("   ", "Title", "Text", "hash"));
    }
}
