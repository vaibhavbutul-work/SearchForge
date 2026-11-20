package com.searchforge.semantic.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.retrieval.dto.SemanticSearchHitResponse;
import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("Semantic Retrieval Quality and Correctness Tests (Phase 7.1 Task 7)")
class SemanticRetrievalQualityTest {

    private EmbeddingModel embeddingModel;
    private InMemoryVectorStorage vectorStorage;
    private ExactVectorSimilarityEngine vectorEngine;
    private DefaultSemanticRetrievalEngine semanticEngine;

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    private CrawledDocument createDoc(long id, String title, String content) {
        CrawledDocument doc = new CrawledDocument("https://example.com/" + id, "example.com", title, content, "h_" + id, 200, Instant.now());
        doc.setId(id);
        return doc;
    }

    @BeforeEach
    void setUp() {
        embeddingModel = new DeterministicEmbeddingModel(384, 256);
        vectorStorage = new InMemoryVectorStorage(384);
        vectorEngine = new ExactVectorSimilarityEngine(vectorStorage);
        semanticEngine = new DefaultSemanticRetrievalEngine(embeddingModel, vectorEngine, crawledDocumentRepository);

        // Seed 3 distinct topic vectors:
        // Doc 101: Java 21 virtual threads lightweight concurrency
        float[] v101 = embeddingModel.embed("Java 21 Virtual Threads reduce overhead for high-throughput concurrent socket applications.");
        vectorStorage.put(new DocumentVector(101L, v101));

        // Doc 102: Human heart cardiovascular circulation blood pressure
        float[] v102 = embeddingModel.embed("Human heart anatomy and cardiovascular circulation pumping oxygenated blood through arteries.");
        vectorStorage.put(new DocumentVector(102L, v102));

        // Doc 103: Johannes Gutenberg printing press movable type books
        float[] v103 = embeddingModel.embed("Johannes Gutenberg invented movable type printing press in Mainz expanding literature and books.");
        vectorStorage.put(new DocumentVector(103L, v103));
    }

    @Test
    @DisplayName("Query with related semantic terms ranks matching document at top")
    void search_relatedTerms_ranksRelevantDocument() {
        CrawledDocument doc101 = createDoc(101L, "Java Virtual Threads", "Lightweight threads reduce overhead for high-throughput concurrency.");
        CrawledDocument doc102 = createDoc(102L, "Heart Anatomy", "Cardiovascular blood pumping.");
        given(crawledDocumentRepository.findAllById(anyList())).willReturn(List.of(doc101, doc102));

        SemanticSearchResponse response = semanticEngine.search("lightweight virtual threads concurrency", 5);

        assertNotNull(response);
        assertFalse(response.results().isEmpty());
        assertEquals(101L, response.results().get(0).documentId(), "Doc 101 (Virtual Threads) must rank #1 for concurrency query");
    }

    @Test
    @DisplayName("Irrelevant documents receive lower similarity scores and are ranked below relevant documents")
    void search_irrelevantDocumentRejection() {
        CrawledDocument doc102 = createDoc(102L, "Heart Anatomy", "Cardiovascular blood pumping.");
        CrawledDocument doc103 = createDoc(103L, "Printing Press", "Gutenberg books.");
        given(crawledDocumentRepository.findAllById(anyList())).willReturn(List.of(doc102, doc103));

        // Query about arterial blood pressure should rank Doc 102 high and Doc 103 low
        SemanticSearchResponse response = semanticEngine.search("arterial blood pressure and cardiovascular circulation", 5);

        assertNotNull(response);
        assertEquals(102L, response.results().get(0).documentId(), "Doc 102 (Cardiovascular) must rank higher than unrelated docs");
        if (response.results().size() > 1) {
            assertTrue(response.results().get(0).semanticScore() >= response.results().get(1).semanticScore(),
                    "Similarity scores must be sorted strictly descending");
        }
    }

    @Test
    @DisplayName("Similarity scores are strictly bounded within [-1.0, 1.0] and sorted descending")
    void search_scoresAreBoundedAndDescending() {
        CrawledDocument doc101 = createDoc(101L, "Java Virtual Threads", "Text");
        CrawledDocument doc102 = createDoc(102L, "Heart Anatomy", "Text");
        CrawledDocument doc103 = createDoc(103L, "Printing Press", "Text");
        given(crawledDocumentRepository.findAllById(anyList())).willReturn(List.of(doc101, doc102, doc103));

        SemanticSearchResponse response = semanticEngine.search("general text query", 10);

        double lastScore = 1.0;
        for (SemanticSearchHitResponse hit : response.results()) {
            assertTrue(hit.semanticScore() >= -1.0 && hit.semanticScore() <= 1.0,
                    "Cosine score must be bounded [-1.0, 1.0], got: " + hit.semanticScore());
            assertTrue(hit.semanticScore() <= lastScore, "Scores must be in non-increasing order");
            lastScore = hit.semanticScore();
        }
    }

    @Test
    @DisplayName("Empty, blank, or null queries return empty response without throwing exception")
    void search_emptyOrNullQuery_returnsCleanEmptyResponse() {
        SemanticSearchResponse r1 = semanticEngine.search("", 10);
        assertTrue(r1.results().isEmpty());
        assertEquals(0, r1.totalHits());

        SemanticSearchResponse r2 = semanticEngine.search("   ", 10);
        assertTrue(r2.results().isEmpty());

        SemanticSearchResponse r3 = semanticEngine.search(null, 10);
        assertTrue(r3.results().isEmpty());
    }

    @Test
    @DisplayName("Empty vector storage returns empty response cleanly")
    void search_emptyStorage_returnsEmptyHits() {
        vectorStorage.clear();
        SemanticSearchResponse response = semanticEngine.search("java", 10);
        assertTrue(response.results().isEmpty());
        assertEquals(0, response.totalHits());
    }
}
