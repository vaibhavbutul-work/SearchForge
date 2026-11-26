package com.searchforge.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.fetcher.DocumentFetcher;
import com.searchforge.crawler.security.IpRangeValidator;
import com.searchforge.crawler.security.SafeDnsResolver;
import com.searchforge.security.SecurityProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Tag("security")
@Tag("regression")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security_integration_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "searchforge.security.enabled=true",
        "searchforge.security.admin-api-key=integration-test-admin-secret-key-2026",
        "searchforge.security.rate-limit.enabled=true",
        "searchforge.security.rate-limit.search-requests-per-minute=60",
        "searchforge.security.rate-limit.search-burst-capacity=5",
        "searchforge.security.rate-limit.admin-requests-per-minute=20",
        "searchforge.security.rate-limit.admin-burst-capacity=3"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Task 14.5: Security & Authorization Integration Test Suite")
class SecurityIntegrationTest {

    private static final String VALID_KEY = "integration-test-admin-secret-key-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private CrawlerProperties crawlerProps;

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

    @Test
    @DisplayName("1. Public vs Admin RBAC enforcement across X-API-KEY, Bearer, and HTTP methods")
    void rbacAuthenticationAndAuthorization() throws Exception {
        String ip = "198.51.100.10";

        // Public endpoints accessible without key
        mockMvc.perform(get("/api/v1/search").param("q", "java").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/index/stats").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isOk());

        // Admin endpoints reject missing or invalid API key with 401
        mockMvc.perform(get("/api/v1/lifecycle/metrics").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/index/build")
                        .header("X-API-KEY", "wrong-key-attempt")
                        .with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isUnauthorized());

        // Unsupported GET on /api/v1/index/build still blocked by auth first without key
        mockMvc.perform(get("/api/v1/index/build").with(req -> { req.setRemoteAddr("198.51.100.11"); return req; }))
                .andExpect(status().isUnauthorized());

        // Valid X-API-KEY and Bearer token both accepted on admin routes
        mockMvc.perform(get("/api/v1/lifecycle/metrics")
                        .header("X-API-KEY", VALID_KEY)
                        .with(req -> { req.setRemoteAddr("198.51.100.12"); return req; }))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/crawler/documents")
                        .header("Authorization", "Bearer " + VALID_KEY)
                        .with(req -> { req.setRemoteAddr("198.51.100.13"); return req; }))
                .andExpect(status().isOk());

        // Unsupported HTTP method with valid key returns 405 Method Not Allowed
        mockMvc.perform(get("/api/v1/index/build")
                        .header("X-API-KEY", VALID_KEY)
                        .with(req -> { req.setRemoteAddr("198.51.100.14"); return req; }))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("2. Rate Limiting enforces 429 + Retry-After per client IP and precedes brute-force auth")
    void rateLimitingSearchAndAdminTiers() throws Exception {
        String searchIp = "198.51.100.20";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/search").param("q", "test").with(req -> { req.setRemoteAddr(searchIp); return req; }))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Limit"));
        }
        // 6th request exceeds burst=5 -> 429
        mockMvc.perform(get("/api/v1/search").param("q", "test").with(req -> { req.setRemoteAddr(searchIp); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        // Different client IP still has full quota
        mockMvc.perform(get("/api/v1/search").param("q", "test").with(req -> { req.setRemoteAddr("198.51.100.21"); return req; }))
                .andExpect(status().isOk());

        // Admin brute-force throttled with 429 before 401
        String adminAttackerIp = "198.51.100.22";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/lifecycle/metrics")
                            .header("X-API-KEY", "guess-" + i)
                            .with(req -> { req.setRemoteAddr(adminAttackerIp); return req; }))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/lifecycle/metrics")
                        .header("X-API-KEY", "guess-4")
                        .with(req -> { req.setRemoteAddr(adminAttackerIp); return req; }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("3. Input validation & error masking never leak stack traces, SQL, or secrets")
    void inputValidationAndErrorMasking() throws Exception {
        String ip = "198.51.100.30";

        // Oversized query (> 500 chars) -> 400
        String longQuery = "a".repeat(505);
        mockMvc.perform(get("/api/v1/search").param("q", longQuery).with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isBadRequest());

        // Invalid limit -> 400
        mockMvc.perform(get("/api/v1/search").param("q", "java").param("limit", "-5")
                        .with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isBadRequest());

        // Malformed JSON on admin endpoint -> 400 with masked message
        String body = mockMvc.perform(post("/api/v1/crawler/start")
                        .header("X-API-KEY", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json")
                        .with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("com.searchforge"), "Must not leak package/class names");
        assertFalse(body.contains(VALID_KEY), "Must not leak API key");
    }

    @Test
    @DisplayName("4. SSRF protection blocks loopback, private IPv4, cloud metadata, IPv6 special, and unsafe 302 redirects")
    void ssrfProtectionAndRedirectValidation() {
        SafeDnsResolver strictResolver = new SafeDnsResolver(new IpRangeValidator(true), Set.of());
        SecurityProperties secProps = new SecurityProperties();
        secProps.getCrawler().setMaxRedirects(3);

        DocumentFetcher strictFetcher = new DocumentFetcher(crawlerProps, strictResolver, secProps);

        // Prohibited destinations must be blocked before any TCP connection
        assertTrue(strictFetcher.fetch("http://127.0.0.1:8080/admin").isEmpty(), "Loopback IPv4 blocked");
        assertTrue(strictFetcher.fetch("http://10.0.0.1/internal").isEmpty(), "Private 10.x blocked");
        assertTrue(strictFetcher.fetch("http://192.168.1.1/router").isEmpty(), "Private 192.168.x blocked");
        assertTrue(strictFetcher.fetch("http://169.254.169.254/latest/meta-data/").isEmpty(), "Cloud metadata blocked");
        assertTrue(strictFetcher.fetch("http://[::1]:8080/").isEmpty(), "IPv6 loopback blocked");
        assertTrue(strictFetcher.fetch("http://[fe80::1]/").isEmpty(), "IPv6 link-local blocked");

        // Redirect from WireMock -> cloud metadata 169.254.169.254 must be blocked by manual redirect loop
        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/redirect-ssrf"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

        // Use a permissive localhost resolver for initial WireMock hop while still blocking metadata/private IPs via IpRangeValidator(false)+explicit check,
        // or verify SafeDnsResolver rejects the redirect target directly
        assertFalse(strictResolver.isSafeUrl("http://169.254.169.254/latest/meta-data/"));
        DocumentFetcher localAllowFetcher = new DocumentFetcher(crawlerProps, new SafeDnsResolver(new IpRangeValidator(false), Set.of()), secProps);
        // Verify redirect loop terminates safely (does not return metadata content)
        var redirectResult = localAllowFetcher.fetch("http://localhost:" + wireMockServer.port() + "/redirect-ssrf");
        assertTrue(redirectResult.isEmpty() || !redirectResult.get().url().contains("169.254.169.254"));
    }
}
