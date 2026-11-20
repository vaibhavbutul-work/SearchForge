package com.searchforge.semantic.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.retrieval.dto.SemanticSearchHitResponse;
import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;
import com.searchforge.semantic.vector.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticRetrievalEngine Unit Tests")
class SemanticRetrievalEngineTest {

    private EmbeddingModel embeddingModel;
    private VectorStorage vectorStorage;
    private VectorSimilarityEngine vectorSimilarityEngine;

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    private DefaultSemanticRetrievalEngine retrievalEngine;

    @BeforeEach
    void setUp() {
        embeddingModel = new DeterministicEmbeddingModel(384, 256);
        vectorStorage = new InMemoryVectorStorage(384);
        vectorSimilarityEngine = new ExactVectorSimilarityEngine(vectorStorage);
        retrievalEngine = new DefaultSemanticRetrievalEngine(embeddingModel, vectorSimilarityEngine, crawledDocumentRepository);
    }

    @Test
    @DisplayName("Should retrieve, rank, and hydrate matching documents for semantic query")
    void testSemanticRetrievalSuccess() {
        // Doc 1: Java Virtual Threads
        float[] vJava = embeddingModel.embed("Java 21 LTS Documentation and Virtual Threads lightweight concurrency");
        vectorStorage.put(1L, vJava);

        // Doc 2: PostgreSQL Indexing
        float[] vPg = embeddingModel.embed("PostgreSQL B-Tree index internals and query planner optimization");
        vectorStorage.put(2L, vPg);

        CrawledDocument doc1 = new CrawledDocument("https://oracle.com/java", "oracle.com", "Java 21 LTS", "Virtual threads in Java...", "h1", 200, Instant.now());
        setId(doc1, 1L);
        CrawledDocument doc2 = new CrawledDocument("https://postgresql.org", "postgresql.org", "PostgreSQL", "B-tree indexes in PostgreSQL...", "h2", 200, Instant.now());
        setId(doc2, 2L);

        when(crawledDocumentRepository.findAllById(anyList())).thenReturn(List.of(doc1, doc2));

        // Query semantically aligned with Java concurrency
        SemanticSearchResponse response = retrievalEngine.search("lightweight concurrency threads", 5);

        assertNotNull(response);
        assertEquals("lightweight concurrency threads", response.query());
        assertEquals("semantic", response.mode());
        assertEquals(2, response.totalHits());
        assertEquals(2, response.returnedHits());
        assertTrue(response.executionTimeMs() >= 0);

        List<SemanticSearchHitResponse> results = response.results();
        assertEquals(2, results.size());

        // Rank 1 must be Java (highest semantic similarity)
        assertEquals(1L, results.get(0).documentId());
        assertEquals(1, results.get(0).rank());
        assertEquals("Java 21 LTS", results.get(0).title());
        assertEquals("oracle.com", results.get(0).domain());
        assertTrue(results.get(0).semanticScore() > results.get(1).semanticScore());
    }

    @Test
    @DisplayName("Should return 0 hits when query is null, empty, or whitespace")
    void testEmptyQuery() {
        SemanticSearchResponse r1 = retrievalEngine.search(null, 10);
        assertEquals(0, r1.totalHits());
        assertTrue(r1.results().isEmpty());

        SemanticSearchResponse r2 = retrievalEngine.search("   ", 10);
        assertEquals(0, r2.totalHits());
        assertTrue(r2.results().isEmpty());
    }

    @Test
    @DisplayName("Should return 0 hits when vector storage has no indexed documents")
    void testEmptyVectorStorage() {
        SemanticSearchResponse response = retrievalEngine.search("search query", 10);
        assertNotNull(response);
        assertEquals(0, response.totalHits());
        assertTrue(response.results().isEmpty());
    }

    @Test
    @DisplayName("Should respect Top-K limit parameter")
    void testLimitParameter() {
        for (long i = 1; i <= 10; i++) {
            float[] vec = new float[384];
            vec[(int) i] = 1.0f;
            vectorStorage.put(i, vec);
        }

        when(crawledDocumentRepository.findAllById(anyList())).thenReturn(List.of());

        SemanticSearchResponse response = retrievalEngine.search("test query", 3);
        assertEquals(3, response.returnedHits());
        assertEquals(3, response.results().size());
    }

    private void setId(CrawledDocument doc, Long id) {
        try {
            java.lang.reflect.Field field = CrawledDocument.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(doc, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
