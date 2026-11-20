package com.searchforge.search.hybrid;

import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.semantic.embedding.exception.EmbeddingException;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.VectorSearchResult;
import com.searchforge.semantic.vector.VectorSimilarityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultHybridSearchService Unit Tests")
class DefaultHybridSearchServiceTest {

    @Mock
    private RankedRetrievalEngine rankedRetrievalEngine;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private VectorSimilarityEngine vectorSimilarityEngine;

    private ReciprocalRankFusionEngine rankFusionEngine;
    private DefaultHybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        rankFusionEngine = new ReciprocalRankFusionEngine(60);
        hybridSearchService = new DefaultHybridSearchService(
                rankedRetrievalEngine,
                embeddingModel,
                vectorSimilarityEngine,
                rankFusionEngine,
                2,
                50
        );
    }

    @Test
    @DisplayName("Empty or null query returns empty HybridSearchResult")
    void search_emptyQuery_shouldReturnEmptyResult() {
        HybridSearchResult res1 = hybridSearchService.search(null, SearchMode.HYBRID, 10);
        assertTrue(res1.candidates().isEmpty());
        assertFalse(res1.fallback());

        HybridSearchResult res2 = hybridSearchService.search("   ", SearchMode.LEXICAL, 10);
        assertTrue(res2.candidates().isEmpty());

        HybridSearchResult res3 = hybridSearchService.search("java", SearchMode.HYBRID, 0);
        assertTrue(res3.candidates().isEmpty());
    }

    @Test
    @DisplayName("LEXICAL mode executes BM25 retrieval without embedding generation")
    void search_lexicalMode_shouldReturnBm25Candidates() {
        RankedSearchResult hit = new RankedSearchResult(
                101L,
                4.5213,
                1,
                List.of("java", "performance"),
                Map.of("java", 2.5, "performance", 2.0213)
        );
        given(rankedRetrievalEngine.search("java performance", 10)).willReturn(List.of(hit));

        HybridSearchResult result = hybridSearchService.search("java performance", SearchMode.LEXICAL, 10);

        assertEquals("java performance", result.query());
        assertEquals(SearchMode.LEXICAL, result.requestedMode());
        assertEquals(SearchMode.LEXICAL, result.effectiveMode());
        assertFalse(result.fallback());
        assertNull(result.fallbackReason());
        assertEquals(1, result.candidates().size());

        HybridCandidate candidate = result.candidates().getFirst();
        assertEquals(101L, candidate.docId());
        assertEquals(4.5213, candidate.score());
        assertEquals(1, candidate.rank());
        assertEquals(4.5213, candidate.bm25Score());
        assertEquals(1, candidate.bm25Rank());
        assertNull(candidate.semanticScore());
        assertNull(candidate.semanticRank());
        assertNull(candidate.rrfScore());
        assertEquals(List.of("java", "performance"), candidate.matchedTerms());
    }

    @Test
    @DisplayName("SEMANTIC mode executes vector similarity search")
    void search_semanticMode_shouldReturnVectorCandidates() {
        float[] queryVec = new float[]{0.1f, 0.2f, 0.3f};
        given(embeddingModel.embed("machine learning")).willReturn(queryVec);

        VectorSearchResult vecHit = new VectorSearchResult(202L, 0.8924f, 1);
        given(vectorSimilarityEngine.search(queryVec, 5)).willReturn(List.of(vecHit));

        HybridSearchResult result = hybridSearchService.search("machine learning", SearchMode.SEMANTIC, 5);

        assertEquals("machine learning", result.query());
        assertEquals(SearchMode.SEMANTIC, result.requestedMode());
        assertEquals(SearchMode.SEMANTIC, result.effectiveMode());
        assertFalse(result.fallback());
        assertEquals(1, result.candidates().size());

        HybridCandidate candidate = result.candidates().getFirst();
        assertEquals(202L, candidate.docId());
        assertEquals(0.8924, candidate.score());
        assertEquals(1, candidate.rank());
        assertNull(candidate.bm25Score());
        assertEquals(0.8924, candidate.semanticScore());
        assertEquals(1, candidate.semanticRank());
    }

    @Test
    @DisplayName("SEMANTIC mode gracefully degrades to lexical BM25 when model fails")
    void search_semanticMode_shouldFallbackToLexical_whenModelFails() {
        given(embeddingModel.embed("fail query"))
                .willThrow(new EmbeddingException("ONNX native runtime crashed", null));

        RankedSearchResult lexHit = new RankedSearchResult(303L, 2.15, 1, List.of("fail"), Map.of("fail", 2.15));
        given(rankedRetrievalEngine.search("fail query", 10)).willReturn(List.of(lexHit));

        HybridSearchResult result = hybridSearchService.search("fail query", SearchMode.SEMANTIC, 10);

        assertEquals(SearchMode.SEMANTIC, result.requestedMode());
        assertEquals(SearchMode.LEXICAL, result.effectiveMode());
        assertTrue(result.fallback());
        assertNotNull(result.fallbackReason());
        assertTrue(result.fallbackReason().contains("ONNX native runtime crashed"));
        assertEquals(1, result.candidates().size());
        assertEquals(303L, result.candidates().getFirst().docId());
    }

    @Test
    @DisplayName("HYBRID mode fuses BM25 and semantic candidates via Reciprocal Rank Fusion")
    void search_hybridMode_shouldFuseResultsWithRrf() {
        // BM25 candidates (limit 2, candidate pool max(4, 50) = 50)
        RankedSearchResult bmHit1 = new RankedSearchResult(1L, 5.0, 1, List.of("query"), Map.of("query", 5.0));
        RankedSearchResult bmHit2 = new RankedSearchResult(2L, 3.0, 2, List.of("query"), Map.of("query", 3.0));
        given(rankedRetrievalEngine.search("query", 50)).willReturn(List.of(bmHit1, bmHit2));

        // Semantic candidates (Doc 2 is rank 1, Doc 3 is rank 2, Doc 1 is rank 3)
        float[] queryVec = new float[]{0.5f, 0.5f};
        given(embeddingModel.embed("query")).willReturn(queryVec);

        VectorSearchResult semHit1 = new VectorSearchResult(2L, 0.95f, 1);
        VectorSearchResult semHit2 = new VectorSearchResult(3L, 0.90f, 2);
        VectorSearchResult semHit3 = new VectorSearchResult(1L, 0.85f, 3);
        given(vectorSimilarityEngine.search(queryVec, 50)).willReturn(List.of(semHit1, semHit2, semHit3));

        HybridSearchResult result = hybridSearchService.search("query", SearchMode.HYBRID, 3);

        assertEquals(SearchMode.HYBRID, result.requestedMode());
        assertEquals(SearchMode.HYBRID, result.effectiveMode());
        assertFalse(result.fallback());
        assertEquals(3, result.candidates().size());

        // Doc 2: BM25 rank 2 (1/62) + Semantic rank 1 (1/61) = 0.016129 + 0.016393 = 0.032522
        // Doc 1: BM25 rank 1 (1/61) + Semantic rank 3 (1/63) = 0.016393 + 0.015873 = 0.032266
        // Doc 3: Semantic rank 2 (1/62) = 0.016129
        // Therefore, Doc 2 wins rank 1 by consensus!
        HybridCandidate top = result.candidates().get(0);
        assertEquals(2L, top.docId());
        assertEquals(1, top.rank());
        assertEquals(3.0, top.bm25Score());
        assertEquals(0.95, top.semanticScore());
        assertEquals(2, top.bm25Rank());
        assertEquals(1, top.semanticRank());
        assertEquals(0.032523, top.rrfScore(), 1e-4);

        HybridCandidate second = result.candidates().get(1);
        assertEquals(1L, second.docId());
        assertEquals(2, second.rank());
        assertEquals(5.0, second.bm25Score());
        assertEquals(0.85, second.semanticScore());
        assertEquals(1, second.bm25Rank());
        assertEquals(3, second.semanticRank());
        assertEquals(0.032267, second.rrfScore(), 1e-4);

        HybridCandidate third = result.candidates().get(2);
        assertEquals(3L, third.docId());
        assertEquals(3, third.rank());
        assertNull(third.bm25Score());
        assertNull(third.bm25Rank());
        assertEquals(0.9, third.semanticScore());
        assertEquals(2, third.semanticRank());
    }

    @Test
    @DisplayName("HYBRID mode gracefully falls back to lexical BM25 when vector search fails")
    void search_hybridMode_shouldDegradeToLexical_whenSemanticFails() {
        RankedSearchResult bmHit = new RankedSearchResult(10L, 4.2, 1, List.of("test"), Map.of("test", 4.2));
        given(rankedRetrievalEngine.search("test", 50)).willReturn(List.of(bmHit));
        given(embeddingModel.embed("test")).willThrow(new RuntimeException("Vector engine memory exhausted"));

        HybridSearchResult result = hybridSearchService.search("test", SearchMode.HYBRID, 10);

        assertEquals(SearchMode.HYBRID, result.requestedMode());
        assertEquals(SearchMode.LEXICAL, result.effectiveMode());
        assertTrue(result.fallback());
        assertNotNull(result.fallbackReason());
        assertTrue(result.fallbackReason().contains("Vector engine memory exhausted"));
        assertEquals(1, result.candidates().size());
        assertEquals(10L, result.candidates().getFirst().docId());
        assertEquals(4.2, result.candidates().getFirst().bm25Score());
    }
}
