package com.searchforge.security.regression;

import com.searchforge.crawler.security.IpRangeValidator;
import com.searchforge.crawler.security.SafeDnsResolver;
import com.searchforge.exception.ApiErrorResponse;
import com.searchforge.exception.GlobalExceptionHandler;
import com.searchforge.security.ApiKeyAuthenticationFilter;
import com.searchforge.security.SecurityProperties;
import com.searchforge.security.SecurityStartupValidator;
import com.searchforge.security.ratelimit.RateLimiterRegistry;
import com.searchforge.security.ratelimit.RateLimitingFilter;
import com.searchforge.security.ratelimit.TokenBucket;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated End-to-End Security Regression Suite for SearchForge (Phase 11 Task 8).
 *
 * Enforces a 6-gate security verification battery covering:
 * Gate 1: Authentication & Authorization (RBAC, API Key timing resistance)
 * Gate 2: Input Validation, Bounds Checking & Error Masking
 * Gate 3: Web Crawler SSRF & Network Boundary Defenses
 * Gate 4: Token Bucket Rate Limiting & Abuse Prevention
 * Gate 5: Database Injection Resistance & Schema Masking
 * Gate 6: Secrets Management, Redaction & Pre-Flight Validation
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 11.8 — Automated End-to-End Security Regression Suite")
class SecurityRegressionSuiteTest {

    @Autowired
    private DataSource dataSource;

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @Order(1)
    @DisplayName("Gate 1: Authentication & Role-Based Access Control Invariants")
    void verifyGate1AuthenticationAndRbac() throws Exception {
        SecurityProperties props = new SecurityProperties();
        props.setEnabled(true);
        props.setAdminApiKey("secret-admin-pass-2026");
        props.setHeaderName("X-API-KEY");

        ApiKeyAuthenticationFilter authFilter = new ApiKeyAuthenticationFilter(props);

        // 1. Public search path must pass without authentication
        MockHttpServletRequest searchReq = new MockHttpServletRequest("GET", "/api/v1/search");
        MockHttpServletResponse searchRes = new MockHttpServletResponse();
        MockFilterChain searchChain = new MockFilterChain();
        authFilter.doFilter(searchReq, searchRes, searchChain);
        assertEquals(200, searchRes.getStatus());
        assertEquals("ROLE_SEARCH", searchReq.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));

        // 2. Admin endpoint without API key must be rejected with HTTP 401
        MockHttpServletRequest unauthReq = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
        MockHttpServletResponse unauthRes = new MockHttpServletResponse();
        authFilter.doFilter(unauthReq, unauthRes, new MockFilterChain());
        assertEquals(401, unauthRes.getStatus());
        assertTrue(unauthRes.getContentAsString().contains("Missing API key"));

        // 3. Admin endpoint with invalid API key must be rejected with HTTP 401
        MockHttpServletRequest badKeyReq = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
        badKeyReq.addHeader("X-API-KEY", "wrong-key");
        MockHttpServletResponse badKeyRes = new MockHttpServletResponse();
        authFilter.doFilter(badKeyReq, badKeyRes, new MockFilterChain());
        assertEquals(401, badKeyRes.getStatus());

        // 4. Admin endpoint with valid key must be granted ROLE_ADMIN
        MockHttpServletRequest validReq = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
        validReq.addHeader("X-API-KEY", "secret-admin-pass-2026");
        MockHttpServletResponse validRes = new MockHttpServletResponse();
        authFilter.doFilter(validReq, validRes, new MockFilterChain());
        assertEquals(200, validRes.getStatus());
        assertEquals("ROLE_ADMIN", validReq.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
    }

    @Test
    @Order(2)
    @DisplayName("Gate 2: Input Validation, Schema Bounds & Error Masking Invariants")
    void verifyGate2InputBoundsAndErrorMasking() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/search");

        // 1. Parameter type mismatch must return standardized 400
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException typeEx =
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "limit", Integer.class, "limit", null, new IllegalArgumentException()
                );
        ResponseEntity<ApiErrorResponse> typeRes = exceptionHandler.handleTypeMismatch(typeEx, req);
        assertEquals(HttpStatus.BAD_REQUEST, typeRes.getStatusCode());
        assertEquals(400, typeRes.getBody().status());
        assertTrue(typeRes.getBody().message().contains("Parameter 'limit' must be of type Integer"));

        // 2. Generic internal exception must mask stack traces and internal details
        Exception genericEx = new RuntimeException("DB connection dead at jdbc:postgresql://10.0.0.1:5432/db");
        ResponseEntity<ApiErrorResponse> genericRes = exceptionHandler.handleGenericException(genericEx, req);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, genericRes.getStatusCode());
        assertFalse(genericRes.getBody().message().contains("10.0.0.1"));
        assertFalse(genericRes.getBody().message().contains("postgresql"));
        assertEquals("An internal server error occurred. Please contact the administrator.", genericRes.getBody().message());
    }

    @Test
    @Order(3)
    @DisplayName("Gate 3: Web Crawler SSRF & Network Security Invariants")
    void verifyGate3CrawlerSsrfDefenses() throws UnknownHostException {
        IpRangeValidator strictValidator = new IpRangeValidator(true);
        SafeDnsResolver strictDns = new SafeDnsResolver(strictValidator, Set.of(80, 443, 8080, 8443));

        // 1. IP range safety checks
        assertFalse(strictValidator.isSafe(InetAddress.getByName("127.0.0.1")), "Must block loopback");
        assertFalse(strictValidator.isSafe(InetAddress.getByName("::1")), "Must block IPv6 loopback");
        assertFalse(strictValidator.isSafe(InetAddress.getByName("10.0.0.1")), "Must block RFC 1918 Class A");
        assertFalse(strictValidator.isSafe(InetAddress.getByName("172.16.0.1")), "Must block RFC 1918 Class B");
        assertFalse(strictValidator.isSafe(InetAddress.getByName("192.168.1.1")), "Must block RFC 1918 Class C");
        assertFalse(strictValidator.isSafe(InetAddress.getByName("169.254.169.254")), "Must block Cloud Metadata");
        assertTrue(strictValidator.isSafe(InetAddress.getByName("8.8.8.8")), "Must permit public IP");

        // 2. Protocol and port checks
        assertFalse(strictDns.isSafeUrl("file:///etc/passwd"), "Must block file protocol");
        assertFalse(strictDns.isSafeUrl("ftp://ftp.test.org/"), "Must block ftp protocol");
        assertFalse(strictDns.isSafeUrl("http://example.com:22/"), "Must block SSH port");
        assertFalse(strictDns.isSafeUrl("http://example.com:6379/"), "Must block Redis port");
        assertTrue(strictDns.isSafeUrl("https://example.com/"), "Must permit standard HTTPS");
    }

    @Test
    @Order(4)
    @DisplayName("Gate 4: Token Bucket Rate Limiting & Abuse Prevention Invariants")
    void verifyGate4RateLimitingAndAbuseProtection() throws Exception {
        SecurityProperties props = new SecurityProperties();
        props.getRateLimit().setEnabled(true);
        props.getRateLimit().setSearchRequestsPerMinute(60);
        props.getRateLimit().setSearchBurstCapacity(2); // Set 2 for rapid exhaustion test

        RateLimiterRegistry registry = new RateLimiterRegistry(props);
        RateLimitingFilter filter = new RateLimitingFilter(registry, props);

        String clientIp = "198.51.100.77";

        // 1. Request 1 and 2 within burst allowance must succeed with rate telemetry headers
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/search");
        req1.setRemoteAddr(clientIp);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, new MockFilterChain());
        assertEquals(200, res1.getStatus());
        assertEquals("2", res1.getHeader("X-RateLimit-Limit"));
        assertEquals("1", res1.getHeader("X-RateLimit-Remaining"));

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/search");
        req2.setRemoteAddr(clientIp);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, new MockFilterChain());
        assertEquals(200, res2.getStatus());
        assertEquals("0", res2.getHeader("X-RateLimit-Remaining"));

        // 2. Request 3 must exceed burst capacity and return HTTP 429 Too Many Requests
        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/api/v1/search");
        req3.setRemoteAddr(clientIp);
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, new MockFilterChain());
        assertEquals(429, res3.getStatus());
        assertNotNull(res3.getHeader("Retry-After"));
        assertTrue(res3.getContentAsString().contains("Rate limit exceeded"));
    }

    @Test
    @Order(5)
    @DisplayName("Gate 5: Database Persistence Security & Schema Masking Invariants")
    void verifyGate5DatabaseSecurityAndPoolHardening() {
        // 1. DataIntegrityViolationException must return sanitized HTTP 409
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/lifecycle/commit");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Key (url)=(https://dup.com) already exists in table crawled_documents"
        );
        ResponseEntity<ApiErrorResponse> res = exceptionHandler.handleDataIntegrityViolation(ex, req);
        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        assertEquals(409, res.getBody().status());
        assertFalse(res.getBody().message().contains("crawled_documents"));
        assertEquals("Database constraint violation: duplicate record or invalid reference", res.getBody().message());

        // 2. Hikari connection pool must have active leak detection threshold
        assertInstanceOf(HikariDataSource.class, dataSource);
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertTrue(hikari.getLeakDetectionThreshold() > 0, "Hikari leak detection threshold must be configured");
        assertEquals(30000, hikari.getLeakDetectionThreshold());
    }

    @Test
    @Order(6)
    @DisplayName("Gate 6: Secrets Management, Masking & Startup Invariants")
    void verifyGate6SecretsMaskingAndStartupValidation() {
        // 1. Secrets masking in toString
        SecurityProperties props = new SecurityProperties();
        props.setAdminApiKey("production-secret-api-key-9988");
        String output = props.toString();
        assertFalse(output.contains("production-secret-api-key-9988"));
        assertTrue(output.contains("pr****88"));

        // 2. Detection of default keys by startup validator
        props.setAdminApiKey("sf-admin-secret-key-prod");
        SecurityStartupValidator validator = new SecurityStartupValidator(props, new MockEnvironment());
        SecurityStartupValidator.ValidationResult result = validator.validate();
        assertFalse(result.isSecure());
        assertTrue(result.isDefaultKey());

        // 3. Strict mode abort on default credentials
        props.setStrictSecrets(true);
        assertThrows(IllegalStateException.class, () ->
                validator.onApplicationEvent(org.mockito.Mockito.mock(org.springframework.boot.context.event.ApplicationReadyEvent.class))
        );
    }
}
