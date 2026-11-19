package com.searchforge.indexer.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BooleanRetrievalEngineTest {

    @Autowired
    private BooleanRetrievalEngine retrievalEngine;

    @Autowired
    private IndexingEngine indexingEngine;

    @Autowired
    private InvertedIndex inMemoryIndex;

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private IndexedTermRepository termRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    private Long doc1Id;
    private Long doc2Id;
    private Long doc3Id;
    private Long doc4Id;

    @BeforeEach
    void setUp() {
        inMemoryIndex.clear();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();

        // Populate test corpus
        CrawledDocument doc1 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.dev/1", "test.dev", "Java Concurrency",
                "Java concurrency with virtual threads and reactive streams.",
                "h1", 200, Instant.now()
        ));
        CrawledDocument doc2 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.dev/2", "test.dev", "Python Web",
                "Python web development using Django and Flask frameworks.",
                "h2", 200, Instant.now()
        ));
        CrawledDocument doc3 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.dev/3", "test.dev", "Modern Backend",
                "Modern Java backend architecture with Spring Boot.",
                "h3", 200, Instant.now()
        ));
        CrawledDocument doc4 = crawledDocumentRepository.save(new CrawledDocument(
                "https://test.dev/4", "test.dev", "Full Stack",
                "Full-stack development using Python and modern JavaScript frontend.",
                "h4", 200, Instant.now()
        ));

        doc1Id = doc1.getId();
        doc2Id = doc2.getId();
        doc3Id = doc3.getId();
        doc4Id = doc4.getId();

        indexingEngine.indexDocument(doc1);
        indexingEngine.indexDocument(doc2);
        indexingEngine.indexDocument(doc3);
        indexingEngine.indexDocument(doc4);
    }

    @Test
    @DisplayName("Should evaluate single term Boolean query with linguistic stemming")
    void shouldEvaluateSingleTermQuery() {
        BooleanSearchResult result = retrievalEngine.search("java");
        assertEquals(2, result.totalHits());
        assertEquals(List.of(doc1Id, doc3Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate AND intersection query across two terms")
    void shouldEvaluateAndQuery() {
        // "java AND modern" matches doc3 only (doc1 has java, doc4 has modern)
        BooleanSearchResult result = retrievalEngine.search("java AND modern");
        assertEquals(1, result.totalHits());
        assertEquals(List.of(doc3Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate OR union query across two disjoint terms")
    void shouldEvaluateOrQuery() {
        // "concurrency OR django" matches doc1 and doc2
        BooleanSearchResult result = retrievalEngine.search("concurrency OR django");
        assertEquals(2, result.totalHits());
        assertEquals(List.of(doc1Id, doc2Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate NOT difference query excluding negated term")
    void shouldEvaluateNotQuery() {
        // "java NOT modern" -> matches doc1, excludes doc3
        BooleanSearchResult result = retrievalEngine.search("java NOT modern");
        assertEquals(1, result.totalHits());
        assertEquals(List.of(doc1Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate exact phrase query inside Boolean expression")
    void shouldEvaluateExactPhraseQuery() {
        // "Spring Boot" exact phrase appears in doc3
        BooleanSearchResult result = retrievalEngine.search("\"Spring Boot\"");
        assertEquals(1, result.totalHits());
        assertEquals(List.of(doc3Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate complex nested Boolean query with parentheses and phrases")
    void shouldEvaluateComplexNestedExpression() {
        // "(java OR python) AND modern NOT frontend"
        // (java OR python) -> [1, 2, 3, 4]
        // modern -> [3, 4]
        // (java OR python) AND modern -> [3, 4]
        // NOT frontend -> excludes 4 -> result is [3]
        BooleanSearchResult result = retrievalEngine.search("(java OR python) AND modern NOT frontend");
        assertEquals(1, result.totalHits());
        assertEquals(List.of(doc3Id), result.documentIds());
    }

    @Test
    @DisplayName("Should evaluate pure negation query against the document universe")
    void shouldEvaluatePureNegationAgainstUniverse() {
        // "NOT python" -> Universe [1, 2, 3, 4] minus [2, 4] = [1, 3]
        BooleanSearchResult result = retrievalEngine.search("NOT python");
        assertEquals(2, result.totalHits());
        assertEquals(List.of(doc1Id, doc3Id), result.documentIds());
    }

    @Test
    @DisplayName("Should return empty result when query terms do not exist in corpus")
    void shouldReturnEmptyForNonExistentTerms() {
        BooleanSearchResult result = retrievalEngine.search("quantum AND blockchain");
        assertEquals(0, result.totalHits());
        assertTrue(result.documentIds().isEmpty());
    }

    @Test
    @DisplayName("Should return empty result for blank or null query")
    void shouldReturnEmptyForBlankQuery() {
        BooleanSearchResult result = retrievalEngine.search("");
        assertEquals(0, result.totalHits());
        assertTrue(result.documentIds().isEmpty());
    }
}
