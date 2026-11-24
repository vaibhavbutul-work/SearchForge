package com.searchforge.security;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.exception.ApiErrorResponse;
import com.searchforge.exception.GlobalExceptionHandler;
import com.searchforge.indexer.entity.IndexedTerm;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 11.6 — Database & Persistence Security Test Suite")
class DatabaseSecurityTest {

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private IndexedTermRepository indexedTermRepository;

    @Autowired
    private DocumentEmbeddingRepository documentEmbeddingRepository;

    @Autowired
    private DataSource dataSource;

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("SQL Injection Parameterization Tests")
    class SqlInjectionParameterizationTests {

        @Test
        @Transactional
        @DisplayName("Should treat SQL injection strings in CrawledDocumentRepository as literal parameters")
        void shouldNeutralizeSqlInjectionInCrawledDocumentRepository() {
            CrawledDocument legitimateDoc = new CrawledDocument(
                    "https://searchforge.org/page1",
                    "searchforge.org",
                    "Safe Title",
                    "Safe Content",
                    "hash-12345",
                    200,
                    Instant.now()
            );
            crawledDocumentRepository.save(legitimateDoc);

            // Classic SQL injection payloads
            String orPayload = "https://searchforge.org/page1' OR '1'='1";
            String dropPayload = "https://searchforge.org/page1'; DROP TABLE crawled_documents; --";
            String unionPayload = "' UNION SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL --";

            assertFalse(crawledDocumentRepository.existsByUrl(orPayload));
            assertFalse(crawledDocumentRepository.existsByUrl(dropPayload));
            assertTrue(crawledDocumentRepository.findByUrl(orPayload).isEmpty());
            assertTrue(crawledDocumentRepository.findByUrl(dropPayload).isEmpty());
            assertTrue(crawledDocumentRepository.findByUrl(unionPayload).isEmpty());

            assertEquals(0, crawledDocumentRepository.countByDomain("searchforge.org' OR 1=1 --"));
            assertTrue(crawledDocumentRepository.findByDomain("searchforge.org'; DROP TABLE crawled_documents; --").isEmpty());

            // Verify legitimate document still exists and table was not modified or dropped
            Optional<CrawledDocument> found = crawledDocumentRepository.findByUrl("https://searchforge.org/page1");
            assertTrue(found.isPresent());
            assertEquals("Safe Title", found.get().getTitle());
        }

        @Test
        @Transactional
        @DisplayName("Should treat SQL injection strings in IndexedTermRepository as literal parameters")
        void shouldNeutralizeSqlInjectionInIndexedTermRepository() {
            IndexedTerm legitimateTerm = new IndexedTerm("algorithm", 5, 20);
            indexedTermRepository.save(legitimateTerm);

            String termOrPayload = "algorithm' OR '1'='1";
            String termDropPayload = "algorithm'; DROP TABLE indexed_terms; --";
            String termUnionPayload = "' UNION SELECT NULL, NULL, NULL, NULL --";

            assertFalse(indexedTermRepository.existsByTerm(termOrPayload));
            assertTrue(indexedTermRepository.findByTerm(termOrPayload).isEmpty());
            assertTrue(indexedTermRepository.findByTerm(termDropPayload).isEmpty());

            List<IndexedTerm> batchResults = indexedTermRepository.findAllByTermIn(
                    List.of(termOrPayload, termDropPayload, termUnionPayload)
            );
            assertTrue(batchResults.isEmpty(), "Batch IN query must not execute SQL injection payloads");

            // Verify legitimate term is intact
            assertTrue(indexedTermRepository.findByTerm("algorithm").isPresent());
        }

        @Test
        @Transactional
        @DisplayName("Should treat SQL injection strings in DocumentEmbeddingRepository as literal parameters")
        void shouldNeutralizeSqlInjectionInEmbeddingRepository() {
            String modelPayload = "all-MiniLM-L6-v2' OR '1'='1";
            assertTrue(documentEmbeddingRepository.findByModelName(modelPayload).isEmpty());
        }
    }

    @Nested
    @DisplayName("Database Exception & Schema Masking Tests")
    class ExceptionMaskingTests {

        @Test
        @DisplayName("Should mask DataIntegrityViolationException and return HTTP 409 Conflict")
        void shouldMaskDataIntegrityViolationException() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lifecycle/stage");
            DataIntegrityViolationException ex = new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint 'uk_crawled_documents_url' ON TABLE crawled_documents"
            );

            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex, request);

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            ApiErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(409, body.status());
            assertEquals("Conflict", body.error());
            assertEquals("Database constraint violation: duplicate record or invalid reference", body.message());
            assertFalse(body.message().contains("uk_crawled_documents_url"), "Must not leak constraint name");
            assertFalse(body.message().contains("crawled_documents"), "Must not leak table name");
        }

        @Test
        @DisplayName("Should mask DataAccessException and return sanitized HTTP 500 without leaking SQL syntax")
        void shouldMaskDataAccessException() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
            DataAccessException ex = new DataAccessException("PSQLException: Connection refused to 10.0.0.5:5432. Details: relation 'postings' does not exist") {};

            ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataAccessException(ex, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            ApiErrorResponse body = response.getBody();
            assertNotNull(body);
            assertEquals(500, body.status());
            assertEquals("Internal Server Error", body.error());
            assertEquals("A database error occurred while processing the request.", body.message());
            assertFalse(body.message().contains("10.0.0.5"), "Must not leak internal IP address");
            assertFalse(body.message().contains("postings"), "Must not leak internal table name");
        }
    }

    @Nested
    @DisplayName("HikariCP Pool Hardening Tests")
    class HikariConfigurationTests {

        @Test
        @DisplayName("DataSource should be HikariDataSource with active leak detection")
        void dataSourceShouldBeHikariWithLeakDetection() {
            assertInstanceOf(HikariDataSource.class, dataSource);
            HikariDataSource hikari = (HikariDataSource) dataSource;

            assertTrue(hikari.getLeakDetectionThreshold() > 0, "Leak detection threshold must be configured");
            assertEquals(30000, hikari.getLeakDetectionThreshold());
            assertTrue(hikari.getConnectionTimeout() > 0);
            assertNotNull(hikari.getPoolName());
        }
    }
}
