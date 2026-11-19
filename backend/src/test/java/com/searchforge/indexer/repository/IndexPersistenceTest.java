package com.searchforge.indexer.repository;

import com.searchforge.indexer.entity.IndexedDocumentMetadata;
import com.searchforge.indexer.entity.IndexedTerm;
import com.searchforge.indexer.entity.TermPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IndexPersistenceTest {

    @Autowired
    private IndexedTermRepository termRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @BeforeEach
    void setUp() {
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist and retrieve IndexedTerm with dictionary statistics")
    void shouldPersistAndRetrieveIndexedTerm() {
        IndexedTerm term = new IndexedTerm("search", 5, 23L);
        IndexedTerm saved = termRepository.save(term);

        assertNotNull(saved.getId(), "Term ID should be generated");
        assertEquals("search", saved.getTerm());
        assertEquals(5, saved.getDocumentFrequency());
        assertEquals(23L, saved.getTotalTermFrequency());

        Optional<IndexedTerm> retrieved = termRepository.findByTerm("search");
        assertTrue(retrieved.isPresent(), "Term should be retrievable by term string");
        assertEquals(5, retrieved.get().getDocumentFrequency());
        assertTrue(termRepository.existsByTerm("search"));
        assertFalse(termRepository.existsByTerm("nonexistent"));
    }

    @Test
    @DisplayName("Should enforce unique constraint on IndexedTerm term string")
    void shouldEnforceUniqueTermConstraint() {
        IndexedTerm term1 = new IndexedTerm("algorithm", 1, 1L);
        termRepository.saveAndFlush(term1);

        IndexedTerm term2 = new IndexedTerm("algorithm", 2, 4L);
        assertThrows(DataIntegrityViolationException.class, () -> termRepository.saveAndFlush(term2));
    }

    @Test
    @DisplayName("Should persist and retrieve TermPosting with comma-separated position list")
    void shouldPersistAndRetrieveTermPostingWithPositionsList() {
        IndexedTerm term = termRepository.save(new IndexedTerm("forge", 1, 3L));

        TermPosting posting = new TermPosting(term, 101L, List.of(0, 7, 24));
        TermPosting saved = postingRepository.save(posting);

        assertNotNull(saved.getId(), "Posting ID should be generated");
        assertEquals("0,7,24", saved.getPositions());
        assertEquals(3, saved.getTermFrequency());
        assertEquals(List.of(0, 7, 24), saved.getPositionsList());

        Optional<TermPosting> found = postingRepository.findByTermIdAndDocumentId(term.getId(), 101L);
        assertTrue(found.isPresent(), "Posting should be found by termId and documentId");
        assertEquals(List.of(0, 7, 24), found.get().getPositionsList());

        List<TermPosting> termPostings = postingRepository.findByTermId(term.getId());
        assertEquals(1, termPostings.size());

        List<TermPosting> docPostings = postingRepository.findByDocumentId(101L);
        assertEquals(1, docPostings.size());
    }

    @Test
    @DisplayName("Should enforce unique constraint on (term_id, document_id) in TermPosting")
    void shouldEnforceUniquePostingConstraint() {
        IndexedTerm term = termRepository.save(new IndexedTerm("index", 1, 1L));

        TermPosting posting1 = new TermPosting(term, 42L, List.of(1, 5));
        postingRepository.saveAndFlush(posting1);

        TermPosting posting2 = new TermPosting(term, 42L, List.of(8, 12));
        assertThrows(DataIntegrityViolationException.class, () -> postingRepository.saveAndFlush(posting2));
    }

    @Test
    @DisplayName("Should delete all postings belonging to a specific document ID")
    void shouldDeletePostingsByDocumentId() {
        IndexedTerm term1 = termRepository.save(new IndexedTerm("word1", 1, 1L));
        IndexedTerm term2 = termRepository.save(new IndexedTerm("word2", 1, 1L));

        postingRepository.save(new TermPosting(term1, 200L, List.of(0)));
        postingRepository.save(new TermPosting(term2, 200L, List.of(1)));
        postingRepository.save(new TermPosting(term1, 201L, List.of(0)));
        postingRepository.flush();

        assertEquals(2, postingRepository.findByDocumentId(200L).size());
        assertEquals(1, postingRepository.findByDocumentId(201L).size());

        postingRepository.deleteByDocumentId(200L);
        postingRepository.flush();

        assertTrue(postingRepository.findByDocumentId(200L).isEmpty());
        assertEquals(1, postingRepository.findByDocumentId(201L).size());
    }

    @Test
    @DisplayName("Should track document metadata and compute aggregate statistics")
    void shouldTrackDocumentMetadataAndCalculateAggregates() {
        IndexedDocumentMetadata doc1 = new IndexedDocumentMetadata(1L, 100, Instant.now());
        IndexedDocumentMetadata doc2 = new IndexedDocumentMetadata(2L, 200, Instant.now());
        IndexedDocumentMetadata doc3 = new IndexedDocumentMetadata(3L, 300, Instant.now());

        metadataRepository.saveAll(List.of(doc1, doc2, doc3));

        assertTrue(metadataRepository.existsByDocumentId(1L));
        assertTrue(metadataRepository.existsByDocumentId(2L));
        assertFalse(metadataRepository.existsByDocumentId(999L));
        assertEquals(3, metadataRepository.count());

        Optional<Double> avgLength = metadataRepository.findAverageDocumentLength();
        assertTrue(avgLength.isPresent());
        assertEquals(200.0, avgLength.get(), 0.001);

        Optional<Long> totalTokens = metadataRepository.findTotalTokenCount();
        assertTrue(totalTokens.isPresent());
        assertEquals(600L, totalTokens.get());
    }
}
