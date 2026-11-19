package com.searchforge.indexer.repository;

import com.searchforge.indexer.entity.IndexedDocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndexedDocumentMetadataRepository extends JpaRepository<IndexedDocumentMetadata, Long> {

    boolean existsByDocumentId(Long documentId);

    @Query("SELECT AVG(m.tokenCount) FROM IndexedDocumentMetadata m")
    Optional<Double> findAverageDocumentLength();

    @Query("SELECT SUM(m.tokenCount) FROM IndexedDocumentMetadata m")
    Optional<Long> findTotalTokenCount();
}
