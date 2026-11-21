package com.searchforge.crawler.repository;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for persisting and querying CrawledDocument entities.
 */
@Repository
public interface CrawledDocumentRepository extends JpaRepository<CrawledDocument, Long> {

    /**
     * Checks if a document with the exact URL has already been crawled and persisted.
     */
    boolean existsByUrl(String url);

    /**
     * Checks if identical content (based on SHA-256 hash) has already been persisted.
     */
    boolean existsByContentHash(String contentHash);

    /**
     * Finds a crawled document by its canonical URL.
     */
    Optional<CrawledDocument> findByUrl(String url);

    /**
     * Counts how many documents have been crawled for a given domain.
     */
    long countByDomain(String domain);

    /**
     * Retrieves all crawled documents for a given domain.
     */
    List<CrawledDocument> findByDomain(String domain);

    /**
     * Finds all documents matching a specific lifecycle status.
     */
    List<CrawledDocument> findByStatus(DocumentLifecycleStatus status);

    /**
     * Finds a paginated batch of documents matching a specific lifecycle status.
     */
    List<CrawledDocument> findByStatus(DocumentLifecycleStatus status, Pageable pageable);

    /**
     * Counts the number of documents in a specific lifecycle status.
     */
    long countByStatus(DocumentLifecycleStatus status);

    /**
     * Checks if a document with the given URL exists and is in a specific status.
     */
    boolean existsByUrlAndStatus(String url, DocumentLifecycleStatus status);

    /**
     * Finds all crawled documents that have not yet been indexed.
     */
    @Query("SELECT d FROM CrawledDocument d WHERE d.id NOT IN (SELECT m.documentId FROM IndexedDocumentMetadata m)")
    List<CrawledDocument> findUnindexedDocuments();

    /**
     * Finds a paginated batch of crawled documents that have not yet been indexed.
     */
    @Query("SELECT d FROM CrawledDocument d WHERE d.id NOT IN (SELECT m.documentId FROM IndexedDocumentMetadata m)")
    List<CrawledDocument> findUnindexedDocuments(Pageable pageable);

    /**
     * Counts how many crawled documents are currently unindexed.
     */
    @Query("SELECT COUNT(d) FROM CrawledDocument d WHERE d.id NOT IN (SELECT m.documentId FROM IndexedDocumentMetadata m)")
    long countUnindexedDocuments();
}
