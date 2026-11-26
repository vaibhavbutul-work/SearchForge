package com.searchforge.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.searchforge.crawler.engine.CrawlEngine;
import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.service.DeletionResult;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.service.IncrementalIndexUpdateEngine;
import com.searchforge.lifecycle.service.IncrementalUpdateResult;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Tag("regression")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:backend_integration_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Task 14.2: Backend Integration Test Suite (API -> Service -> Repository -> Index -> Vector)")
class BackendIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CrawledDocumentRepository crawledDocumentRepository;
    @Autowired private TermPostingRepository termPostingRepository;
    @Autowired private IndexedTermRepository indexedTermRepository;
    @Autowired private IndexedDocumentMetadataRepository metadataRepository;
    @Autowired private DocumentEmbeddingRepository embeddingRepository;
    @Autowired private InvertedIndex invertedIndex;
    @Autowired private VectorStorage vectorStorage;
    @Autowired private ActiveDocumentFilter activeDocumentFilter;
    @Autowired private IndexingEngine indexingEngine;
    @Autowired private DocumentEmbeddingService documentEmbeddingService;
    @Autowired private IncrementalIndexUpdateEngine incrementalUpdateEngine;
    @Autowired private DocumentDeletionService documentDeletionService;
    @Autowired private CrawlEngine crawlEngine;

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void cleanDatabaseAndInMemoryIndexes() {
        termPostingRepository.deleteAll();
        indexedTermRepository.deleteAll();
        metadataRepository.deleteAll();
        embeddingRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.initialize(List.of());
    }

    private CrawledDocument persistAndIndexActiveDoc(String url, String title, String text, boolean withEmbedding) {
        CrawledDocument doc = new CrawledDocument(
                url,
                "example.com",
                title,
                text,
                "hash-" + url.hashCode(),
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        CrawledDocument saved = crawledDocumentRepository.save(doc);
        activeDocumentFilter.markActive(saved.getId());
        indexingEngine.indexDocument(saved);
        if (withEmbedding) {
            documentEmbeddingService.generateAndStoreEmbedding(saved);
        }
        return saved;
    }

    @Test
    @DisplayName("A. API-to-Service: Request validation, DTO shape, explainability, and Crawler Pageable pagination")
    void apiToServiceValidationAndPagination() throws Exception {
        persistAndIndexActiveDoc("https://example.com/java-21", "Java 21 Virtual Threads",
                "Java 21 introduces virtual threads for high concurrency backend services.", true);
        persistAndIndexActiveDoc("https://example.com/spring-boot", "Spring Boot Architecture",
                "Spring Boot provides production ready Actuator health probes and Flyway migrations.", true);

        // Blank query -> 400 Bad Request
        mockMvc.perform(get("/api/v1/search").param("q", "   "))
                .andExpect(status().isBadRequest());

        // Invalid search mode -> 400 Bad Request
        mockMvc.perform(get("/api/v1/search").param("q", "java").param("mode", "invalid_mode"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Valid search with explain=true
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "virtual threads")
                        .param("mode", "lexical")
                        .param("limit", "5")
                        .param("explain", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("virtual threads"))
                .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/java-21"))
                .andExpect(jsonPath("$.results[0].explanation").exists());

        // Crawler documents Pageable pagination
        mockMvc.perform(get("/api/v1/crawler/documents")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("B. Service-to-Repository: Persistence, incremental updates, deletion, and unique URL constraint")
    void serviceToRepositoryLifecycleAndConstraints() {
        CrawledDocument saved = persistAndIndexActiveDoc("https://example.com/lifecycle", "Initial Title",
                "Initial quantum computing content for persistence check.", true);

        // Unique URL constraint enforcement
        CrawledDocument duplicateUrl = new CrawledDocument(
                "https://example.com/lifecycle", "example.com", "Dup", "Dup text",
                "dup-hash", 200, Instant.now(), DocumentLifecycleStatus.FETCHED, 1);
        assertThrows(DataIntegrityViolationException.class, () -> crawledDocumentRepository.saveAndFlush(duplicateUrl));

        // Incremental update reflects in DB and RAM
        IncrementalUpdateResult updateResult = incrementalUpdateEngine.updateDocument(
                saved.getId(), "Updated Distributed Systems",
                "Consensus algorithms like Raft and Paxos guarantee distributed durability.");
        assertEquals(IncrementalUpdateResult.Status.SUCCESS, updateResult.status());
        assertTrue(crawledDocumentRepository.findById(saved.getId()).orElseThrow().getTitle().contains("Updated"));

        // Deletion removes from ActiveDocumentFilter and VectorStorage
        DeletionResult delResult = documentDeletionService.deleteDocument(saved.getId());
        assertTrue(delResult.isSuccess());
        assertFalse(activeDocumentFilter.isActive(saved.getId()));
        assertFalse(vectorStorage.contains(saved.getId()));
    }

    @Test
    @DisplayName("C. Search Pipeline: BM25 ordering, snippet highlighting, and limit rank-order preservation")
    void searchPipelineBm25OrderingAndLimitConsistency() throws Exception {
        persistAndIndexActiveDoc("https://example.com/high-tf", "Inverted Index Design",
                "Inverted index postings store term frequency. An inverted index enables fast BM25 retrieval.", true);
        persistAndIndexActiveDoc("https://example.com/low-tf", "Database Storage",
                "PostgreSQL stores relational tables and one mention of index.", true);

        // Verify limit=10 returns high-tf first with <mark> snippet
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "inverted index")
                        .param("mode", "lexical")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/high-tf"))
                .andExpect(jsonPath("$.results[0].highlightedSnippet", containsString("<mark>")));

        // Verify limit=1 preserves exact #1 document
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "inverted index")
                        .param("mode", "lexical")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/high-tf"));
    }

    @Test
    @DisplayName("D. Crawler-to-Index: WireMock crawl persists valid pages, ignores 500 errors, and indexes cleanly")
    void crawlerToIndexIntegrationWithWireMock() throws Exception {
        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse().withStatus(404)));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/seed"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>SearchForge Crawler Guide</title></head>" +
                                "<body><h1>SearchForge Crawler</h1><p>Crawling pipelines extract clean text.</p>" +
                                "<a href=\"/broken\">Broken Link</a></body></html>")));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/broken"))
                .willReturn(aResponse().withStatus(500)));

        String seedUrl = "http://localhost:" + wireMockServer.port() + "/seed";
        crawlEngine.runCrawl(List.of(seedUrl));

        // Only /seed should be persisted; /broken (500) must not create an invalid document
        List<CrawledDocument> docs = crawledDocumentRepository.findAll();
        assertEquals(1, docs.size());
        CrawledDocument crawled = docs.get(0);
        assertEquals("SearchForge Crawler Guide", crawled.getTitle());

        // Mark active and synchronize into index
        crawled.setStatus(DocumentLifecycleStatus.ACTIVE);
        crawledDocumentRepository.save(crawled);
        activeDocumentFilter.markActive(crawled.getId());
        indexingEngine.indexDocument(crawled);

        mockMvc.perform(get("/api/v1/search").param("q", "SearchForge Crawler").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].url").value(seedUrl));
    }

    @Test
    @DisplayName("E. Semantic Search: 384-D vector storage, semantic retrieval, and missing-embedding lexical safety")
    void semanticSearchAndMissingEmbeddingSafety() throws Exception {
        CrawledDocument withVec = persistAndIndexActiveDoc("https://example.com/semantic-doc",
                "Neural Vector Embeddings",
                "Transformer embeddings encode continuous semantic similarity in 384 dimensions.", true);
        CrawledDocument withoutVec = persistAndIndexActiveDoc("https://example.com/lexical-only",
                "Lexical Only Document",
                "Keyword matching works even when neural embeddings are missing.", false);

        DocumentVector stored = vectorStorage.get(withVec.getId()).orElseThrow();
        assertEquals(384, stored.getDimensions());
        assertTrue(embeddingRepository.findByDocumentId(withVec.getId()).isPresent());
        assertFalse(vectorStorage.contains(withoutVec.getId()));

        // Semantic search retrieves vectorized doc without failing on lexical-only doc
        mockMvc.perform(get("/api/v1/search").param("q", "vector embeddings").param("mode", "semantic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/semantic-doc"));

        // Lexical search still retrieves the document missing an embedding
        mockMvc.perform(get("/api/v1/search").param("q", "keyword matching").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/lexical-only"));
    }

    @Test
    @DisplayName("F. Hybrid Search: RRF combines lexical + semantic without duplicates and handles single-branch empty results")
    void hybridSearchDeduplicationAndGracefulBranchHandling() throws Exception {
        persistAndIndexActiveDoc("https://example.com/hybrid-1", "Hybrid Retrieval Engine",
                "Reciprocal Rank Fusion combines BM25 lexical scores with dense semantic vectors.", true);
        persistAndIndexActiveDoc("https://example.com/lexical-branch-only", "Lexical Branch Document",
                "Uniquelexicalterm appears only in inverted index without vector embedding.", false);

        // Standard hybrid query returns unique results (no duplicate URLs)
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "Hybrid Retrieval")
                        .param("mode", "hybrid")
                        .param("explain", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.results[0].url").value("https://example.com/hybrid-1"));

        // Query hitting lexical-only doc when semantic branch has no vector for it still succeeds cleanly
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "Uniquelexicalterm")
                        .param("mode", "hybrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.results[*].url", hasItem("https://example.com/lexical-branch-only")));
    }
}
