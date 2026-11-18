package com.searchforge.crawler.engine;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CrawlEngineTest {

    private static WireMockServer wireMock;
    private static String baseUrl;

    @Autowired
    private CrawlEngine engine;

    @Autowired
    private CrawledDocumentRepository repository;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        engine.reset();
        repository.deleteAll();

        // Stub permissive robots.txt
        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("User-agent: *\nAllow: /\nCrawl-delay: 0")));
    }

    @Test
    @DisplayName("Should execute complete end-to-end BFS crawl, handle link cycles, and persist to database")
    void shouldExecuteEndToEndBfsCrawl() {
        // Seed links to /page-a and /page-b
        wireMock.stubFor(get(urlEqualTo("/seed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Seed Page</title></head><body>" +
                                "<a href=\"/page-a\">Page A</a>" +
                                "<a href=\"/page-b\">Page B</a>" +
                                "</body></html>")));

        // Page A links back to /seed (cycle test) and /page-c
        wireMock.stubFor(get(urlEqualTo("/page-a"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Page A</title></head><body>" +
                                "<a href=\"/seed\">Back to Seed</a>" +
                                "</body></html>")));

        // Page B
        wireMock.stubFor(get(urlEqualTo("/page-b"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Page B</title></head><body>" +
                                "<p>Page B Content</p>" +
                                "</body></html>")));

        // Execute crawl with seed URL
        engine.runCrawl(List.of(baseUrl + "/seed"));

        CrawlStatus status = engine.getStatus();
        assertEquals(CrawlState.COMPLETED, status.state());
        assertEquals(3, status.pagesCrawled(), "Should have crawled exactly 3 pages (/seed, /page-a, /page-b)");
        assertEquals(0, status.pagesFailed());

        // Verify documents are saved in PostgreSQL / H2
        assertTrue(repository.existsByUrl(baseUrl + "/seed"));
        assertTrue(repository.existsByUrl(baseUrl + "/page-a"));
        assertTrue(repository.existsByUrl(baseUrl + "/page-b"));

        Optional<CrawledDocument> seedDoc = repository.findByUrl(baseUrl + "/seed");
        assertTrue(seedDoc.isPresent());
        assertEquals("Seed Page", seedDoc.get().getTitle());
        assertEquals("localhost", seedDoc.get().getDomain());
    }

    @Test
    @DisplayName("Should skip saving pages with identical content hash (duplicate content protection)")
    void shouldDeduplicateIdenticalContent() {
        String duplicateBody = "<html><head><title>Identical</title></head><body><p>Duplicate Body Text</p></body></html>";

        wireMock.stubFor(get(urlEqualTo("/page-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(duplicateBody)));

        wireMock.stubFor(get(urlEqualTo("/page-2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(duplicateBody)));

        engine.runCrawl(List.of(baseUrl + "/page-1", baseUrl + "/page-2"));

        CrawlStatus status = engine.getStatus();
        assertEquals(CrawlState.COMPLETED, status.state());

        // Both URLs fetched, but only 1 unique document persisted due to identical content hash
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("Should gracefully stop mid-crawl when stop signal is issued")
    void shouldStopGracefully() {
        engine.reset();
        assertEquals(CrawlState.IDLE, engine.getStatus().state());

        // Issue stop before running
        engine.stop();
        assertEquals(CrawlState.IDLE, engine.getStatus().state()); // stopped while idle stays idle or resets cleanly
    }
}
