package com.searchforge.crawler.fetcher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.searchforge.crawler.config.CrawlerProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class DocumentFetcherTest {

    private static WireMockServer wireMock;
    private static String baseUrl;

    private DocumentFetcher fetcher;

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
        CrawlerProperties properties = new CrawlerProperties(
                "SearchForgeBot/1.0",
                List.of(baseUrl),
                List.of("localhost"),
                2,
                100,
                1000,
                1000,
                1000, // 1000ms read timeout
                2,
                3,
                500L
        );
        fetcher = new DocumentFetcher(properties);
    }

    @Test
    @DisplayName("Should successfully fetch valid HTML document with HTTP 200 status")
    void shouldFetchValidHtmlDocument() {
        String htmlContent = "<html><head><title>Test</title></head><body><p>Hello SearchForge</p></body></html>";

        wireMock.stubFor(get(urlEqualTo("/test.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBody(htmlContent)));

        Optional<FetchedDocument> result = fetcher.fetch(baseUrl + "/test.html");

        assertTrue(result.isPresent());
        FetchedDocument doc = result.get();
        assertEquals(baseUrl + "/test.html", doc.url());
        assertEquals(200, doc.statusCode());
        assertTrue(doc.contentType().contains("text/html"));
        assertEquals(htmlContent, doc.rawHtml());
    }

    @Test
    @DisplayName("Should discard binary and non-HTML Content-Types (e.g. application/pdf, image/png)")
    void shouldDiscardNonHtmlContentTypes() {
        wireMock.stubFor(get(urlEqualTo("/manual.pdf"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody("%PDF-1.4 binary data")));

        Optional<FetchedDocument> result = fetcher.fetch(baseUrl + "/manual.pdf");
        assertTrue(result.isEmpty(), "Non-HTML content must be discarded");
    }

    @Test
    @DisplayName("Should gracefully handle HTTP 404 Not Found and 500 Server Error without throwing exceptions")
    void shouldHandleHttpErrorsGracefully() {
        wireMock.stubFor(get(urlEqualTo("/missing"))
                .willReturn(aResponse().withStatus(404)));

        wireMock.stubFor(get(urlEqualTo("/error"))
                .willReturn(aResponse().withStatus(500)));

        assertTrue(fetcher.fetch(baseUrl + "/missing").isEmpty());
        assertTrue(fetcher.fetch(baseUrl + "/error").isEmpty());
    }

    @Test
    @DisplayName("Should abort and handle socket read timeout without crashing")
    void shouldHandleSocketTimeoutGracefully() {
        // WireMock delays response by 2500ms, exceeding fetcher's 1000ms read timeout
        wireMock.stubFor(get(urlEqualTo("/hanging-endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2500)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Delayed</body></html>")));

        Optional<FetchedDocument> result = fetcher.fetch(baseUrl + "/hanging-endpoint");
        assertTrue(result.isEmpty(), "Timed out request must return empty Optional without crashing");
    }

    @Test
    @DisplayName("Should handle null or blank URLs safely")
    void shouldHandleNullOrBlankSafely() {
        assertTrue(fetcher.fetch(null).isEmpty());
        assertTrue(fetcher.fetch("   ").isEmpty());
    }
}
