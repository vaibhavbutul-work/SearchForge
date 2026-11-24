package com.searchforge.security;

import com.searchforge.security.ratelimit.RateLimiterRegistry;
import com.searchforge.security.ratelimit.RateLimitingFilter;
import com.searchforge.security.ratelimit.TokenBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 11.5 — API Rate Limiting & Abuse Protection Test Suite")
class RateLimitingTest {

    private SecurityProperties properties;
    private RateLimiterRegistry registry;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setSearchRequestsPerMinute(60);
        properties.getRateLimit().setSearchBurstCapacity(5); // Small burst for deterministic testing
        properties.getRateLimit().setAdminRequestsPerMinute(20);
        properties.getRateLimit().setAdminBurstCapacity(2);

        registry = new RateLimiterRegistry(properties);
        filter = new RateLimitingFilter(registry, properties);
    }

    @Nested
    @DisplayName("TokenBucket Algorithm Unit Tests")
    class TokenBucketUnitTests {

        @Test
        @DisplayName("Should reject invalid non-positive capacity or refill rates")
        void shouldRejectInvalidBucketParameters() {
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-5, 1.0));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, 0));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, -2.5));
        }

        @Test
        @DisplayName("Should consume up to burst capacity and reject once exhausted")
        void shouldConsumeUpToCapacityAndReject() {
            TokenBucket bucket = new TokenBucket(3, 1.0); // 3 capacity, 1 token/sec

            assertTrue(bucket.tryConsume(), "Hop 1 must succeed");
            assertTrue(bucket.tryConsume(), "Hop 2 must succeed");
            assertTrue(bucket.tryConsume(), "Hop 3 must succeed");
            assertFalse(bucket.tryConsume(), "Hop 4 must fail (capacity exhausted)");
        }

        @Test
        @DisplayName("Should report correct remaining tokens and retry duration")
        void shouldReportRemainingTokensAndRetryDuration() {
            TokenBucket bucket = new TokenBucket(2, 0.5); // 2 capacity, 1 token every 2 seconds

            assertEquals(2, bucket.getRemainingTokens());
            bucket.tryConsume();
            assertEquals(1, bucket.getRemainingTokens());
            bucket.tryConsume();
            assertEquals(0, bucket.getRemainingTokens());

            long retryAfter = bucket.getSecondsUntilNextRefill();
            assertTrue(retryAfter >= 1, "Retry duration must be at least 1 second when empty");
        }

        @Test
        @DisplayName("Should refill tokens over time up to capacity ceiling")
        void shouldRefillTokensOverTime() throws InterruptedException {
            TokenBucket bucket = new TokenBucket(2, 20.0); // 2 capacity, 20 tokens/sec (1 token per 50ms)

            assertTrue(bucket.tryConsume());
            assertTrue(bucket.tryConsume());
            assertFalse(bucket.tryConsume(), "Exhausted immediately");

            // Sleep 70ms to allow at least 1 token refill
            Thread.sleep(70);

            assertTrue(bucket.tryConsume(), "Should succeed after refill window");
        }
    }

    @Nested
    @DisplayName("RateLimiterRegistry & Client IP Resolution Tests")
    class RegistryTests {

        @Test
        @DisplayName("Should correctly extract client IP from X-Forwarded-For proxy header")
        void shouldExtractClientIpFromXForwardedFor() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

            String ip = registry.resolveClientIp(request);
            assertEquals("203.0.113.195", ip, "First IP in X-Forwarded-For chain must be identified as client IP");
        }

        @Test
        @DisplayName("Should correctly extract client IP from X-Real-IP header")
        void shouldExtractClientIpFromXRealIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Real-IP", "198.51.100.42");

            String ip = registry.resolveClientIp(request);
            assertEquals("198.51.100.42", ip);
        }

        @Test
        @DisplayName("Should fallback to getRemoteAddr when no proxy headers exist")
        void shouldFallbackToRemoteAddr() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.0.2.1");

            String ip = registry.resolveClientIp(request);
            assertEquals("192.0.2.1", ip);
        }

        @Test
        @DisplayName("Different client IPs should have completely isolated token buckets")
        void differentIpsShouldHaveIsolatedBuckets() {
            TokenBucket clientA = registry.getBucket("1.1.1.1", RateLimiterRegistry.Tier.SEARCH);
            TokenBucket clientB = registry.getBucket("2.2.2.2", RateLimiterRegistry.Tier.SEARCH);

            assertNotSame(clientA, clientB, "Different client IPs must get distinct bucket instances");

            // Exhaust client A
            for (int i = 0; i < 5; i++) {
                clientA.tryConsume();
            }
            assertFalse(clientA.tryConsume(), "Client A must be exhausted");
            assertTrue(clientB.tryConsume(), "Client B must still have full tokens");
        }

        @Test
        @DisplayName("Same client IP should have separate buckets for search vs admin tiers")
        void sameIpShouldHaveSeparateBucketsForTiers() {
            TokenBucket searchBucket = registry.getBucket("1.1.1.1", RateLimiterRegistry.Tier.SEARCH);
            TokenBucket adminBucket = registry.getBucket("1.1.1.1", RateLimiterRegistry.Tier.ADMIN);

            assertNotSame(searchBucket, adminBucket, "Search and Admin tiers must have isolated buckets");
            assertEquals(5, searchBucket.getCapacity());
            assertEquals(2, adminBucket.getCapacity());
        }
    }

    @Nested
    @DisplayName("RateLimitingFilter HTTP Pipeline Tests")
    class FilterPipelineTests {

        @Test
        @DisplayName("Should permit request within burst capacity and append telemetry headers")
        void shouldPermitRequestWithinLimit() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
            request.setRemoteAddr("10.20.30.40");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            assertEquals("5", response.getHeader("X-RateLimit-Limit"));
            assertEquals("4", response.getHeader("X-RateLimit-Remaining"));
            assertNull(response.getHeader("Retry-After"));
        }

        @Test
        @DisplayName("Should return HTTP 429 Too Many Requests when burst capacity is exceeded")
        void shouldReturn429WhenBurstExceeded() throws Exception {
            String clientIp = "192.168.100.5";

            // Exhaust the 5 burst tokens
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
                request.setRemoteAddr(clientIp);
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, new MockFilterChain());
                assertEquals(200, response.getStatus(), "Request " + (i + 1) + " must succeed");
            }

            // 6th request must trigger HTTP 429
            MockHttpServletRequest throttledRequest = new MockHttpServletRequest("GET", "/api/v1/search");
            throttledRequest.setRemoteAddr(clientIp);
            MockHttpServletResponse throttledResponse = new MockHttpServletResponse();

            filter.doFilter(throttledRequest, throttledResponse, new MockFilterChain());

            assertEquals(429, throttledResponse.getStatus(), "Must return HTTP 429 Too Many Requests");
            assertEquals("5", throttledResponse.getHeader("X-RateLimit-Limit"));
            assertEquals("0", throttledResponse.getHeader("X-RateLimit-Remaining"));
            assertNotNull(throttledResponse.getHeader("Retry-After"), "Retry-After header must be present");
            assertTrue(throttledResponse.getContentType().contains("application/json"));

            String body = throttledResponse.getContentAsString();
            assertTrue(body.contains("\"status\": 429"));
            assertTrue(body.contains("\"error\": \"Too Many Requests\""));
            assertTrue(body.contains("Rate limit exceeded"));
        }

        @Test
        @DisplayName("Admin endpoints should enforce stricter admin burst limit")
        void shouldEnforceAdminLimitOnAdminEndpoints() throws Exception {
            String clientIp = "172.16.50.1";

            // Admin burst capacity is 2
            for (int i = 0; i < 2; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
                request.setRemoteAddr(clientIp);
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, new MockFilterChain());
                assertEquals(200, response.getStatus());
            }

            // 3rd admin request must trigger HTTP 429
            MockHttpServletRequest throttledAdmin = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
            throttledAdmin.setRemoteAddr(clientIp);
            MockHttpServletResponse throttledResponse = new MockHttpServletResponse();

            filter.doFilter(throttledAdmin, throttledResponse, new MockFilterChain());

            assertEquals(429, throttledResponse.getStatus());
            assertEquals("2", throttledResponse.getHeader("X-RateLimit-Limit"));
            assertEquals("0", throttledResponse.getHeader("X-RateLimit-Remaining"));
        }

        @Test
        @DisplayName("Should bypass rate limiting when globally disabled")
        void shouldBypassWhenDisabled() throws Exception {
            properties.getRateLimit().setEnabled(false);

            String clientIp = "10.0.0.99";

            // Fire 10 requests with burst of 5 — all must pass because rate limiting is disabled
            for (int i = 0; i < 10; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
                request.setRemoteAddr(clientIp);
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, new MockFilterChain());
                assertEquals(200, response.getStatus());
                assertNull(response.getHeader("X-RateLimit-Limit"), "No headers should be added when disabled");
            }
        }

        @Test
        @DisplayName("Should bypass non-API paths (e.g. actuator, health)")
        void shouldBypassNonApiPaths() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertEquals(200, response.getStatus());
            assertNull(response.getHeader("X-RateLimit-Limit"));
        }
    }
}
