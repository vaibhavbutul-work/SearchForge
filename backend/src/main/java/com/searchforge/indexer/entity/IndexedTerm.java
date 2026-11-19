package com.searchforge.indexer.entity;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * JPA Entity representing a unique dictionary term in the persistent inverted index.
 */
@Entity
@Table(
        name = "indexed_terms",
        indexes = {
                @Index(name = "idx_indexed_terms_term", columnList = "term", unique = true)
        }
)
public class IndexedTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String term;

    @Column(name = "document_frequency", nullable = false)
    private int documentFrequency;

    @Column(name = "total_term_frequency", nullable = false)
    private long totalTermFrequency;

    protected IndexedTerm() {
    }

    public IndexedTerm(String term, int documentFrequency, long totalTermFrequency) {
        this.term = term;
        this.documentFrequency = documentFrequency;
        this.totalTermFrequency = totalTermFrequency;
    }

    public Long getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public int getDocumentFrequency() {
        return documentFrequency;
    }

    public void setDocumentFrequency(int documentFrequency) {
        this.documentFrequency = documentFrequency;
    }

    public long getTotalTermFrequency() {
        return totalTermFrequency;
    }

    public void setTotalTermFrequency(long totalTermFrequency) {
        this.totalTermFrequency = totalTermFrequency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexedTerm that = (IndexedTerm) o;
        return Objects.equals(term, that.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term);
    }
}
