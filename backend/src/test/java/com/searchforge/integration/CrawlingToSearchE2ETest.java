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
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.service.IncrementalIndexUpdateEngine;
import com.searchforge.lifecycle.startup.StartupIndexReconciliationRunner;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Tag("regression")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Task 14.4: Complete Crawling-to-Search End-to-End Test Suite (Scenarios A-H)")
class CrawlingToSearchE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CrawlEngine crawlEngine;
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
    @Autowired private StartupIndexReconciliationRunner reconciliationRunner;

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
    void resetAllState() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse().withStatus(404)));

        termPostingRepository.deleteAll();
        indexedTermRepository.deleteAll();
        metadataRepository.deleteAll();
        embeddingRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.initialize(List.of());
    }

    private void stubHtmlPage(String path, String title, String bodyHtml) {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>" + title + "</title></head><body>" + bodyHtml + "</body></html>")));
    }

    private String url(String path) {
        return "http://localhost:" + wireMockServer.port() + path;
    }

    private void activateAndEmbedAll() {
        for (CrawledDocument doc : crawledDocumentRepository.findAll()) {
            if (doc.getStatus() != DocumentLifecycleStatus.ACTIVE) {
                doc.setStatus(DocumentLifecycleStatus.ACTIVE);
                crawledDocumentRepository.save(doc);
            }
            activeDocumentFilter.markActive(doc.getId());
            documentEmbeddingService.generateAndStoreEmbedding(doc);
        }
    }

    @Test
    @DisplayName("Scenario A: Successful document crawling -> indexing -> embedding -> lexical/semantic/hybrid search")
    void scenarioASuccessfulDocumentIndexingAndSearch() throws Exception {
        stubHtmlPage("/arch", "SearchForge Architecture",
                "<h1>Custom Search Engine</h1><p>SearchForge implements BM25 inverted index and ONNX embeddings.</p>");

        crawlEngine.runCrawl(List.of(url("/arch")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        for (String mode : List.of("lexical", "semantic", "hybrid")) {
            mockMvc.perform(get("/api/v1/search").param("q", "BM25 inverted index").param("mode", mode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.results[0].url").value(url("/arch")))
                    .andExpect(jsonPath("$.results[0].title").value("SearchForge Architecture"));
        }
    }

    @Test
    @DisplayName("Scenario B: Duplicate content / duplicate URL crawling produces no duplicate searchable entries")
    void scenarioBDuplicateDocumentDeduplication() throws Exception {
        String html = "<p>Identical deduplication payload for SearchForge verification.</p>";
        stubHtmlPage("/dup-1", "Duplicate Page", html);
        stubHtmlPage("/dup-2", "Duplicate Page", html);

        crawlEngine.runCrawl(List.of(url("/dup-1"), url("/dup-2"), url("/dup-1")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        // Content-hash and URL deduplication should leave exactly 1 persisted & searchable doc
        assertEquals(1, crawledDocumentRepository.count());
        mockMvc.perform(get("/api/v1/search").param("q", "deduplication").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1));
    }

    @Test
    @DisplayName("Scenario C: Updated document reflects new terms and stops matching removed terms")
    void scenarioCUpdatedDocumentReflectsChanges() throws Exception {
        stubHtmlPage("/evolving", "Version One", "<p>Alphaomegaoldterm appears only in version one.</p>");
        crawlEngine.runCrawl(List.of(url("/evolving")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        CrawledDocument doc = crawledDocumentRepository.findAll().get(0);
        incrementalUpdateEngine.updateDocument(doc.getId(), "Version Two",
                "Betanewreplacedterm is now indexed in version two.");

        mockMvc.perform(get("/api/v1/search").param("q", "Alphaomegaoldterm").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));

        mockMvc.perform(get("/api/v1/search").param("q", "Betanewreplacedterm").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].title").value("Version Two"));
    }

    @Test
    @DisplayName("Scenario D: Deleted document no longer appears in lexical, semantic, or hybrid search")
    void scenarioDDeletedDocumentRemovedFromSearch() throws Exception {
        stubHtmlPage("/ephemeral", "Ephemeral Doc", "<p>Uniquepurgekeyword will be deleted shortly.</p>");
        crawlEngine.runCrawl(List.of(url("/ephemeral")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        CrawledDocument doc = crawledDocumentRepository.findAll().get(0);
        assertTrue(documentDeletionService.deleteDocument(doc.getId()).isSuccess());

        for (String mode : List.of("lexical", "semantic", "hybrid")) {
            mockMvc.perform(get("/api/v1/search").param("q", "Uniquepurgekeyword").param("mode", mode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalHits").value(0));
        }
    }

    @Test
    @DisplayName("Scenario E: Failed crawl (HTTP 500 / 404) does not corrupt index or create invalid documents")
    void scenarioEFailedCrawlingDoesNotCorruptIndex() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/fail-500"))
                .willReturn(aResponse().withStatus(500)));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/missing-404"))
                .willReturn(aResponse().withStatus(404)));

        crawlEngine.runCrawl(List.of(url("/fail-500"), url("/missing-404")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());

        assertEquals(0, crawledDocumentRepository.count());
        assertEquals(0, indexingEngine.getStats().totalIndexedDocuments());
    }

    @Test
    @DisplayName("Scenario F: Missing embedding still allows lexical BM25 search to retrieve document")
    void scenarioFMissingEmbeddingLexicalFallback() throws Exception {
        stubHtmlPage("/no-vec", "Lexical Resilient Doc", "<p>Noembeddingkeyword is searchable via BM25.</p>");
        crawlEngine.runCrawl(List.of(url("/no-vec")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());

        CrawledDocument doc = crawledDocumentRepository.findAll().get(0);
        doc.setStatus(DocumentLifecycleStatus.ACTIVE);
        crawledDocumentRepository.save(doc);
        activeDocumentFilter.markActive(doc.getId());

        assertFalse(vectorStorage.contains(doc.getId()));
        mockMvc.perform(get("/api/v1/search").param("q", "Noembeddingkeyword").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].url").value(url("/no-vec")));
    }

    @Test
    @DisplayName("Scenario G: Re-crawling existing URL preserves consistent DB & index counts")
    void scenarioGReCrawlingConsistency() throws Exception {
        stubHtmlPage("/stable", "Stable Page", "<p>Stablekeyword remains consistent across recrawls.</p>");

        crawlEngine.runCrawl(List.of(url("/stable")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        // Re-crawl the exact same URL
        crawlEngine.runCrawl(List.of(url("/stable")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());

        assertEquals(1, crawledDocumentRepository.count());
        mockMvc.perform(get("/api/v1/search").param("q", "Stablekeyword").param("mode", "lexical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1));
    }

    @Test
    @DisplayName("Scenario H: Simulated application restart clears RAM and StartupIndexReconciliationRunner restores searchability")
    void scenarioHApplicationRestartReconciliation() throws Exception {
        stubHtmlPage("/durable", "Durable Document", "<p>Restartpersistencekeyword survives JVM restart via PostgreSQL.</p>");
        crawlEngine.runCrawl(List.of(url("/durable")));
        mockMvc.perform(post("/api/v1/index/build")).andExpect(status().isOk());
        activateAndEmbedAll();

        // Simulate JVM restart: wipe all in-memory structures
        invertedIndex.clear();
        vectorStorage.clear();
        activeDocumentFilter.initialize(List.of());
        assertEquals(0, invertedIndex.getVocabularySize());
        assertEquals(0, vectorStorage.size());

        // Trigger startup reconciliation
        IndexIntegrityReport report = reconciliationRunner.reconcile();
        assertTrue(report.isHealthy());
        assertTrue(invertedIndex.getVocabularySize() > 0);
        assertEquals(1, vectorStorage.size());

        for (String mode : List.of("lexical", "semantic", "hybrid")) {
            mockMvc.perform(get("/api/v1/search").param("q", "Restartpersistencekeyword").param("mode", mode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalHits", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.results[0].url").value(url("/durable")));
        }
    }
}
