package com.searchforge.indexer.entity;

import jakarta.persistence.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * JPA Entity representing a posting entry for a term in a specific document.
 */
@Entity
@Table(
        name = "term_postings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_term_postings_term_doc", columnNames = {"term_id", "document_id"})
        },
        indexes = {
                @Index(name = "idx_term_postings_term_id", columnList = "term_id"),
                @Index(name = "idx_term_postings_doc_id", columnList = "document_id")
        }
)
public class TermPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false, foreignKey = @ForeignKey(name = "fk_term_postings_term"))
    private IndexedTerm term;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "term_frequency", nullable = false)
    private int termFrequency;

    @Column(name = "positions", nullable = false, columnDefinition = "TEXT")
    private String positions;

    protected TermPosting() {
    }

    public TermPosting(IndexedTerm term, Long documentId, int termFrequency, String positions) {
        this.term = term;
        this.documentId = documentId;
        this.termFrequency = termFrequency;
        this.positions = positions != null ? positions : "";
    }

    public TermPosting(IndexedTerm term, Long documentId, List<Integer> positionList) {
        this.term = term;
        this.documentId = documentId;
        setPositionsList(positionList);
    }

    public Long getId() {
        return id;
    }

    public IndexedTerm getTerm() {
        return term;
    }

    public void setTerm(IndexedTerm term) {
        this.term = term;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public int getTermFrequency() {
        return termFrequency;
    }

    public void setTermFrequency(int termFrequency) {
        this.termFrequency = termFrequency;
    }

    public String getPositions() {
        return positions;
    }

    public void setPositions(String positions) {
        this.positions = positions != null ? positions : "";
    }

    public List<Integer> getPositionsList() {
        if (positions == null || positions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(positions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    public void setPositionsList(List<Integer> positionList) {
        if (positionList == null || positionList.isEmpty()) {
            this.positions = "";
            this.termFrequency = 1;
        } else {
            this.positions = positionList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            this.termFrequency = positionList.size();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TermPosting that = (TermPosting) o;
        return Objects.equals(term, that.term) && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, documentId);
    }
}
