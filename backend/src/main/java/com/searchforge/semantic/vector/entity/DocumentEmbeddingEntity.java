package com.searchforge.semantic.vector.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * JPA entity representing a persisted dense document embedding in the relational store.
 *
 * Stores the vector in raw binary format (4 bytes per dimension) to minimize disk size
 * and provide fast binary serialization without JSON serialization penalties.
 */
@Entity
@Table(name = "document_embeddings", indexes = {
        @Index(name = "idx_doc_embeddings_doc_id", columnList = "document_id", unique = true),
        @Index(name = "idx_doc_embeddings_model", columnList = "model_name")
})
public class DocumentEmbeddingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "dimensions", nullable = false)
    private int dimensions;

    @Lob
    @Column(name = "vector_data", nullable = false)
    private byte[] vectorData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentEmbeddingEntity() {
        // JPA constructor
    }

    public DocumentEmbeddingEntity(Long documentId, String modelName, int dimensions, byte[] vectorData) {
        this.documentId = Objects.requireNonNull(documentId, "documentId cannot be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.dimensions = dimensions;
        this.vectorData = Objects.requireNonNull(vectorData, "vectorData cannot be null");
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getModelName() {
        return modelName;
    }

    public int getDimensions() {
        return dimensions;
    }

    public byte[] getVectorData() {
        return vectorData != null ? vectorData.clone() : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentEmbeddingEntity that)) return false;
        return Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId);
    }
}
