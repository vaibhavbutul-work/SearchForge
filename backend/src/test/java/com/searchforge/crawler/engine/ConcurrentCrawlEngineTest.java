package com.searchforge.crawler.engine;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrentCrawlEngineTest {

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

        // Permissive robots.txt
        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("User-agent: *\nAllow: /\nCrawl-delay: 0")));
    }

    @Test
    @DisplayName("Should execute concurrent crawl with Java 21 Virtual Threads via startAsync")
    void shouldCrawlConcurrentlyUsingVirtualThreads() throws Exception {
        // Seed page linking to 4 pages
        wireMock.stubFor(get(urlEqualTo("/seed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Seed</title></head><body>" +
                                "<a href=\"/p1\">P1</a>" +
                                "<a href=\"/p2\">P2</a>" +
                                "<a href=\"/p3\">P3</a>" +
                                "<a href=\"/p4\">P4</a>" +
                                "</body></html>")));

        // 4 subpages with 50ms delay
        for (int i = 1; i <= 4; i++) {
            wireMock.stubFor(get(urlEqualTo("/p" + i))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(50)
                            .withHeader("Content-Type", "text/html")
                            .withBody("<html><head><title>Page " + i + "</title></head><body>Content " + i + "</body></html>")));
        }

        // Start crawl asynchronously
        CompletableFuture<CrawlStatus> future = engine.startAsync(List.of(baseUrl + "/seed"));

        // Wait for async completion
        CrawlStatus status = future.get(10, TimeUnit.SECONDS);

        assertEquals(CrawlState.COMPLETED, status.state());
        assertEquals(5, status.pagesCrawled(), "Should have crawled 5 pages (/seed + /p1 + /p2 + /p3 + /p4)");
        assertEquals(0, status.pagesFailed());

        List<CrawledDocument> saved = repository.findAll();
        assertEquals(5, saved.size());
    }

    @Test
    @DisplayName("Should gracefully stop concurrent virtual thread workers when stop() is invoked")
    void shouldStopConcurrentCrawlGracefully() throws Exception {
        // Infinite chain or slow pages
        wireMock.stubFor(get(urlEqualTo("/seed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Seed</title></head><body>" +
                                "<a href=\"/slow-1\">Slow 1</a>" +
                                "<a href=\"/slow-2\">Slow 2</a>" +
                                "</body></html>")));

        wireMock.stubFor(get(urlMatching("/slow-.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Slow</title></head><body>Slow Content</body></html>")));

        CompletableFuture<CrawlStatus> future = engine.startAsync(List.of(baseUrl + "/seed"));

        // Wait briefly for crawl to start, then signal stop
        Thread.sleep(100);
        engine.stop();

        CrawlStatus status = future.get(10, TimeUnit.SECONDS);
        assertEquals(CrawlState.STOPPED, status.state());
        assertFalse(status.isRunning());
    }
}
