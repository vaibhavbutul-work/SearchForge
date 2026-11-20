package com.searchforge.semantic.vector.repository;

import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for persisted document vector embeddings.
 */
@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbeddingEntity, Long> {

    Optional<DocumentEmbeddingEntity> findByDocumentId(Long documentId);

    boolean existsByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    List<DocumentEmbeddingEntity> findByModelName(String modelName);
}
