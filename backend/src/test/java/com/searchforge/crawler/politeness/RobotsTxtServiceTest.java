package com.searchforge.crawler.politeness;

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

class RobotsTxtServiceTest {

    private static WireMockServer wireMock;
    private static String baseUrl;

    private RobotsTxtService service;

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
                3000,
                5000,
                2
        );
        service = new RobotsTxtService(properties);
    }

    @Test
    @DisplayName("Should respect Allow and Disallow rules and enforce RFC 9309 longest-match precedence")
    void shouldRespectRulesAndLongestMatch() {
        String robotsContent = """
                User-agent: *
                Disallow: /admin/
                Allow: /admin/public/
                Disallow: /private/
                Crawl-delay: 3
                """;

        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(robotsContent)));

        // /admin/ is disallowed
        assertFalse(service.isAllowed(baseUrl + "/admin/dashboard"));
        assertFalse(service.isAllowed(baseUrl + "/admin/settings"));

        // /admin/public/ is allowed because Allow rule is longer than Disallow rule (longest match wins)
        assertTrue(service.isAllowed(baseUrl + "/admin/public/index.html"));

        // /private/ is disallowed
        assertFalse(service.isAllowed(baseUrl + "/private/data.json"));

        // /public/ is allowed (unmentioned paths allowed by default)
        assertTrue(service.isAllowed(baseUrl + "/public/home"));

        // Crawl-delay of 3 seconds = 3000ms
        assertEquals(Optional.of(3000L), service.getCrawlDelayMs(baseUrl));
    }

    @Test
    @DisplayName("Should prioritize specific SearchForgeBot User-Agent block over generic wildcard block")
    void shouldPrioritizeSpecificUserAgent() {
        String robotsContent = """
                User-agent: *
                Disallow: /generic-block/

                User-agent: SearchForgeBot
                Disallow: /bot-specific-block/
                Allow: /generic-block/
                """;

        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(robotsContent)));

        // Generic block is allowed for SearchForgeBot because bot-specific rule explicitly overrides it
        assertTrue(service.isAllowed(baseUrl + "/generic-block/page.html"));

        // Bot specific block is disallowed
        assertFalse(service.isAllowed(baseUrl + "/bot-specific-block/page.html"));
    }

    @Test
    @DisplayName("Should allow all URLs when robots.txt returns HTTP 404 Not Found")
    void shouldAllowAllWhenRobotsTxtIsNotFound() {
        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse().withStatus(404)));

        assertTrue(service.isAllowed(baseUrl + "/anything/at/all"));
        assertTrue(service.isAllowed(baseUrl + "/secret/admin"));
    }

    @Test
    @DisplayName("Should cache robots.txt rules in memory and avoid redundant HTTP network calls")
    void shouldCacheRobotsRulesInMemory() {
        wireMock.stubFor(get(urlEqualTo("/robots.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("User-agent: *\nDisallow: /blocked/")));

        // First call triggers network fetch
        assertFalse(service.isAllowed(baseUrl + "/blocked/1"));

        // Subsequent calls for same domain must use cache
        assertFalse(service.isAllowed(baseUrl + "/blocked/2"));
        assertTrue(service.isAllowed(baseUrl + "/allowed/3"));

        // WireMock should have received exactly ONE request for /robots.txt
        wireMock.verify(1, getRequestedFor(urlEqualTo("/robots.txt")));
    }
}
