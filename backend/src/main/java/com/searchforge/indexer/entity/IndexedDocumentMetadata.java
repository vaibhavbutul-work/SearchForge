package com.searchforge.indexer.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity tracking per-document indexing metadata (token count, indexed timestamp).
 */
@Entity
@Table(name = "indexed_document_metadata")
public class IndexedDocumentMetadata {

    @Id
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    protected IndexedDocumentMetadata() {
    }

    public IndexedDocumentMetadata(Long documentId, int tokenCount, Instant indexedAt) {
        this.documentId = documentId;
        this.tokenCount = tokenCount;
        this.indexedAt = indexedAt != null ? indexedAt : Instant.now();
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexedDocumentMetadata that = (IndexedDocumentMetadata) o;
        return Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId);
    }
}
