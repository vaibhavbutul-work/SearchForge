package com.searchforge.crawler.model;

import com.searchforge.lifecycle.model.DocumentLifecycleStateMachine;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity representing a web document fetched and parsed by the crawler.
 * Persisted in the "crawled_documents" table in PostgreSQL.
 */
@Entity
@Table(
        name = "crawled_documents",
        indexes = {
                @Index(name = "idx_crawled_docs_domain", columnList = "domain"),
                @Index(name = "idx_crawled_docs_content_hash", columnList = "content_hash"),
                @Index(name = "idx_crawled_docs_status", columnList = "lifecycle_status")
        }
)
public class CrawledDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String url;

    @Column(nullable = false, length = 255)
    private String domain;

    @Column(length = 512)
    private String title;

    @Column(name = "clean_text", columnDefinition = "TEXT")
    private String cleanText;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "http_status_code", nullable = false)
    private int httpStatusCode;

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private DocumentLifecycleStatus status = DocumentLifecycleStatus.FETCHED;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "status_message", length = 1024)
    private String statusMessage;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * Default no-arg constructor required by JPA/Hibernate reflection.
     */
    protected CrawledDocument() {
    }

    /**
     * Convenience constructor for instantiating new crawled documents.
     */
    public CrawledDocument(String url,
                           String domain,
                           String title,
                           String cleanText,
                           String contentHash,
                           int httpStatusCode,
                           Instant crawledAt) {
        this(url, domain, title, cleanText, contentHash, httpStatusCode, crawledAt, DocumentLifecycleStatus.FETCHED, 1);
    }

    /**
     * Full constructor for instantiating crawled documents with explicit lifecycle status and version.
     */
    public CrawledDocument(String url,
                           String domain,
                           String title,
                           String cleanText,
                           String contentHash,
                           int httpStatusCode,
                           Instant crawledAt,
                           DocumentLifecycleStatus status,
                           int version) {
        this.url = url;
        this.domain = domain;
        this.title = title;
        this.cleanText = cleanText;
        this.contentHash = contentHash;
        this.httpStatusCode = httpStatusCode;
        this.crawledAt = crawledAt != null ? crawledAt : Instant.now();
        this.status = status != null ? status : DocumentLifecycleStatus.FETCHED;
        this.version = version > 0 ? version : 1;
        this.retryCount = 0;
    }

    /**
     * Transitions this document to a new lifecycle state.
     * Validates transition legality via DocumentLifecycleStateMachine.
     */
    public void transitionTo(DocumentLifecycleStatus newStatus) {
        DocumentLifecycleStateMachine.validateTransition(this.status, newStatus);
        this.status = newStatus;
        if (newStatus == DocumentLifecycleStatus.ACTIVE) {
            this.lastProcessedAt = Instant.now();
            this.statusMessage = null;
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCleanText() {
        return cleanText;
    }

    public String getContent() {
        return cleanText;
    }

    public void setCleanText(String cleanText) {
        this.cleanText = cleanText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Instant getCrawledAt() {
        return crawledAt;
    }

    public void setCrawledAt(Instant crawledAt) {
        this.crawledAt = crawledAt;
    }

    public DocumentLifecycleStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentLifecycleStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Instant getLastProcessedAt() {
        return lastProcessedAt;
    }

    public void setLastProcessedAt(Instant lastProcessedAt) {
        this.lastProcessedAt = lastProcessedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isSearchable() {
        return status != null && status.isSearchable();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrawledDocument that)) return false;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(url);
    }

    @Override
    public String toString() {
        return "CrawledDocument{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", domain='" + domain + '\'' +
                ", title='" + title + '\'' +
                ", httpStatusCode=" + httpStatusCode +
                ", crawledAt=" + crawledAt +
                '}';
    }
}
