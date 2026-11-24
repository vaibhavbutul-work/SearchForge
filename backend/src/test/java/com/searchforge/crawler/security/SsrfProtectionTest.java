package com.searchforge.crawler.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.fetcher.DocumentFetcher;
import com.searchforge.crawler.fetcher.FetchedDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 11.4 — Web Crawler SSRF Protection & IP Range Security Suite")
class SsrfProtectionTest {

    private static WireMockServer wireMock;
    private static String baseUrl;

    private IpRangeValidator strictValidator;
    private SafeDnsResolver strictDnsResolver;
    private DocumentFetcher strictFetcher;
    private CrawlerProperties properties;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://127.0.0.1:" + wireMock.port();
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

        strictValidator = new IpRangeValidator(true); // Enforce strict blocking of private/loopback/metadata IPs
        strictDnsResolver = new SafeDnsResolver(strictValidator, Set.of(80, 443, 8080, 8443));

        properties = new CrawlerProperties(
                "SearchForgeBot/1.0",
                List.of(),
                List.of(),
                2,
                100,
                1000,
                1000,
                1000,
                2,
                3,
                500L
        );

        strictFetcher = new DocumentFetcher(properties, strictDnsResolver);
    }

    @Test
    @DisplayName("Should block IPv4 loopback addresses (127.0.0.1, 127.0.0.2, 127.255.255.255)")
    void shouldBlockIpv4Loopback() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("127.0.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("127.0.0.2")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("127.255.255.255")));
    }

    @Test
    @DisplayName("Should block IPv6 loopback (::1)")
    void shouldBlockIpv6Loopback() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("::1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("0:0:0:0:0:0:0:1")));
    }

    @Test
    @DisplayName("Should block RFC 1918 Class A private addresses (10.0.0.0/8)")
    void shouldBlockRfc1918ClassA() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("10.0.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("10.254.1.5")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("10.255.255.255")));
    }

    @Test
    @DisplayName("Should block RFC 1918 Class B private addresses (172.16.0.0/12)")
    void shouldBlockRfc1918ClassB() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("172.16.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("172.25.10.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("172.31.255.255")));
    }

    @Test
    @DisplayName("Should block RFC 1918 Class C private addresses (192.168.0.0/16)")
    void shouldBlockRfc1918ClassC() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("192.168.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("192.168.1.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("192.168.254.254")));
    }

    @Test
    @DisplayName("Should block AWS/GCP/Azure Cloud Instance Metadata service (169.254.169.254 and 169.254.0.0/16)")
    void shouldBlockCloudMetadataAndLinkLocal() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("169.254.169.254")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("169.254.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("169.254.255.255")));
    }

    @Test
    @DisplayName("Should block IPv6 Link-Local (fe80::/10) and Unique-Local (fc00::/7, fd00::/7)")
    void shouldBlockIpv6PrivateAndLocal() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("fe80::1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("fc00::1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("fd12:3456:789a:1::1")));
    }

    @Test
    @DisplayName("Should block AnyLocal (0.0.0.0, ::) and Multicast (224.0.0.0/4)")
    void shouldBlockAnyLocalAndMulticast() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("0.0.0.0")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("224.0.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("239.255.255.250")));
    }

    @Test
    @DisplayName("Should block Carrier Grade NAT (100.64.0.0/10)")
    void shouldBlockCarrierGradeNat() throws UnknownHostException {
        assertFalse(strictValidator.isSafe(InetAddress.getByName("100.64.0.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("100.100.1.1")));
        assertFalse(strictValidator.isSafe(InetAddress.getByName("100.127.255.255")));
    }

    @Test
    @DisplayName("Should permit legitimate public IP addresses (e.g. 8.8.8.8, 1.1.1.1, 93.184.216.34)")
    void shouldPermitLegitimatePublicIps() throws UnknownHostException {
        assertTrue(strictValidator.isSafe(InetAddress.getByName("8.8.8.8")));
        assertTrue(strictValidator.isSafe(InetAddress.getByName("1.1.1.1")));
        assertTrue(strictValidator.isSafe(InetAddress.getByName("93.184.216.34")));
    }

    @Test
    @DisplayName("Should reject non-HTTP URL schemes (file://, ftp://, gopher://, data:)")
    void shouldRejectNonHttpSchemes() {
        assertFalse(strictDnsResolver.isSafeUrl("file:///etc/passwd"));
        assertFalse(strictDnsResolver.isSafeUrl("ftp://ftp.example.com/file.txt"));
        assertFalse(strictDnsResolver.isSafeUrl("gopher://gopher.example.com/"));
        assertFalse(strictDnsResolver.isSafeUrl("data:text/html,<h1>Pwned</h1>"));
    }

    @Test
    @DisplayName("Should reject restricted sensitive ports (22, 25, 6379, 5432, 27017)")
    void shouldRejectRestrictedPorts() {
        assertFalse(strictDnsResolver.isSafeUrl("http://example.com:22/"));
        assertFalse(strictDnsResolver.isSafeUrl("http://example.com:25/"));
        assertFalse(strictDnsResolver.isSafeUrl("http://example.com:6379/"));
        assertFalse(strictDnsResolver.isSafeUrl("http://example.com:5432/"));
        assertFalse(strictDnsResolver.isSafeUrl("http://example.com:27017/"));
    }

    @Test
    @DisplayName("DocumentFetcher should block direct outbound requests to 127.0.0.1 or localhost")
    void shouldBlockDirectLoopbackFetch() {
        wireMock.stubFor(get(urlEqualTo("/secret.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<h1>Sensitive Internal Data</h1>")));

        Optional<FetchedDocument> doc = strictFetcher.fetch(baseUrl + "/secret.html");
        assertTrue(doc.isEmpty(), "Outbound request to loopback must be blocked by SSRF filter");
    }

    @Test
    @DisplayName("DocumentFetcher should block requests targeting Cloud Metadata IP (169.254.169.254)")
    void shouldBlockCloudMetadataFetch() {
        Optional<FetchedDocument> doc = strictFetcher.fetch("http://169.254.169.254/latest/meta-data/");
        assertTrue(doc.isEmpty(), "Outbound request to 169.254.169.254 must be blocked immediately");
    }

    @Test
    @DisplayName("DocumentFetcher should block SSRF redirect traps pointing to internal or metadata IPs")
    void shouldBlockSsrfRedirectTrap() {
        // WireMock permits local connection when fetcher allows local, but let's test redirect logic with permissive initial hop
        // Create a fetcher that permits localhost only for hop 1 to test redirect trap rejection
        DocumentFetcher hybridFetcher = new DocumentFetcher(properties, new SafeDnsResolver(new IpRangeValidator() {
            @Override
            public boolean isSafe(InetAddress address) {
                // Allow wiremock host for the first request, but strictly block cloud metadata
                if (address.getHostAddress().equals("169.254.169.254")) {
                    return false;
                }
                return true;
            }
        }, Set.of()));

        wireMock.stubFor(get(urlEqualTo("/open-redirect"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

        Optional<FetchedDocument> result = hybridFetcher.fetch(baseUrl + "/open-redirect");
        assertTrue(result.isEmpty(), "Redirect to cloud metadata must be blocked and aborted");
    }

    @Test
    @DisplayName("DocumentFetcher should detect and abort redirect loops (A -> B -> A)")
    void shouldDetectRedirectLoops() {
        DocumentFetcher permissiveFetcher = new DocumentFetcher(properties, new SafeDnsResolver(new IpRangeValidator(false), Set.of()));

        wireMock.stubFor(get(urlEqualTo("/loop1"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", baseUrl + "/loop2")));

        wireMock.stubFor(get(urlEqualTo("/loop2"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", baseUrl + "/loop1")));

        Optional<FetchedDocument> result = permissiveFetcher.fetch(baseUrl + "/loop1");
        assertTrue(result.isEmpty(), "Redirect loop must be detected and aborted without hanging");
    }

    @Test
    @DisplayName("DocumentFetcher should abort when redirect chain exceeds maxRedirects limit")
    void shouldAbortWhenMaxRedirectsExceeded() {
        DocumentFetcher permissiveFetcher = new DocumentFetcher(properties, new SafeDnsResolver(new IpRangeValidator(false), Set.of()));

        wireMock.stubFor(get(urlEqualTo("/chain1"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl + "/chain2")));
        wireMock.stubFor(get(urlEqualTo("/chain2"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl + "/chain3")));
        wireMock.stubFor(get(urlEqualTo("/chain3"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl + "/chain4")));
        wireMock.stubFor(get(urlEqualTo("/chain4"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl + "/chain5")));
        wireMock.stubFor(get(urlEqualTo("/chain5"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody("Finished")));

        Optional<FetchedDocument> result = permissiveFetcher.fetch(baseUrl + "/chain1");
        assertTrue(result.isEmpty(), "Chains exceeding maxRedirects (3) must be discarded");
    }

    @Test
    @DisplayName("DocumentFetcher should follow legitimate safe redirects within limit")
    void shouldFollowLegitimateSafeRedirect() {
        DocumentFetcher permissiveFetcher = new DocumentFetcher(properties, new SafeDnsResolver(new IpRangeValidator(false), Set.of()));

        wireMock.stubFor(get(urlEqualTo("/start"))
                .willReturn(aResponse().withStatus(301).withHeader("Location", baseUrl + "/final.html")));
        wireMock.stubFor(get(urlEqualTo("/final.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<h1>Target Content</h1>")));

        Optional<FetchedDocument> result = permissiveFetcher.fetch(baseUrl + "/start");
        assertTrue(result.isPresent(), "Safe redirect must be successfully followed");
        assertEquals("<h1>Target Content</h1>", result.get().rawHtml());
    }
}
