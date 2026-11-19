package com.searchforge.indexer.repository;

import com.searchforge.indexer.entity.IndexedTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndexedTermRepository extends JpaRepository<IndexedTerm, Long> {

    Optional<IndexedTerm> findByTerm(String term);

    boolean existsByTerm(String term);
}
