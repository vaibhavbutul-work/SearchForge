package com.searchforge.indexer.repository;

import com.searchforge.indexer.entity.TermPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermPostingRepository extends JpaRepository<TermPosting, Long> {

    List<TermPosting> findByTermId(Long termId);

    List<TermPosting> findByDocumentId(Long documentId);

    Optional<TermPosting> findByTermIdAndDocumentId(Long termId, Long documentId);

    @Modifying
    @Query("DELETE FROM TermPosting p WHERE p.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
