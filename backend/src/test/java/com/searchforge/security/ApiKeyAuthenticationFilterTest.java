package com.searchforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 11.2: API Key Authentication & RBAC Filter Unit Tests")
class ApiKeyAuthenticationFilterTest {

    private static final String CONFIGURED_ADMIN_KEY = "test-secret-admin-key-2026";
    private SecurityProperties properties;
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setEnabled(true);
        properties.setAdminApiKey(CONFIGURED_ADMIN_KEY);
        properties.setHeaderName("X-API-KEY");
        filter = new ApiKeyAuthenticationFilter(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/crawler/start",
            "/api/v1/crawler/stop",
            "/api/v1/crawler/reset",
            "/api/v1/crawler/status",
            "/api/v1/crawler/documents",
            "/api/v1/lifecycle/status/1",
            "/api/v1/lifecycle/reindex/42",
            "/api/v1/lifecycle/integrity/repair",
            "/api/v1/lifecycle/integrity/check",
            "/api/v1/lifecycle/retry",
            "/api/v1/lifecycle/metrics",
            "/api/v1/index/build",
            "/api/v1/index/rebuild",
            "/api/v1/index/warmup"
    })
    @DisplayName("isAdminEndpoint returns true for administrative and destructive operations")
    void isAdminEndpoint_shouldReturnTrue_forAdminRoutes(String path) {
        assertTrue(filter.isAdminEndpoint(path), "Expected path to be admin-protected: " + path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/search",
            "/api/v1/search/ranked-boolean",
            "/api/v1/search/filtered",
            "/api/v1/search/autocomplete",
            "/api/v1/search/suggest",
            "/api/v1/index/stats",
            "/actuator/health",
            "/actuator/info",
            "/favicon.ico"
    })
    @DisplayName("isAdminEndpoint returns false for public search and telemetry operations")
    void isAdminEndpoint_shouldReturnFalse_forPublicRoutes(String path) {
        assertFalse(filter.isAdminEndpoint(path), "Expected path to be public: " + path);
    }

    @Test
    @DisplayName("Admin endpoint with missing API key returns 401 Unauthorized")
    void doFilter_missingKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Unauthorized"));
        assertTrue(response.getContentAsString().contains("Missing API key"));
        assertNull(filterChain.getRequest(), "Filter chain must not proceed when unauthorized");
    }

    @Test
    @DisplayName("Admin endpoint with blank API key returns 401 Unauthorized")
    void doFilter_blankKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/index/rebuild");
        request.addHeader("X-API-KEY", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing API key"));
    }

    @Test
    @DisplayName("Admin endpoint with invalid API key returns 401 Unauthorized")
    void doFilter_invalidKey_shouldReturn401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/lifecycle/99");
        request.addHeader("X-API-KEY", "wrong-attacker-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid API key"));
        assertNull(filterChain.getRequest());
    }

    @Test
    @DisplayName("Admin endpoint with valid X-API-KEY proceeds with ROLE_ADMIN attribute")
    void doFilter_validXApiKey_shouldProceed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/crawler/start");
        request.addHeader("X-API-KEY", CONFIGURED_ADMIN_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest(), "Filter chain must proceed when authorized");
        assertEquals("ROLE_ADMIN", request.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
        assertEquals(true, request.getAttribute(ApiKeyAuthenticationFilter.ATTR_AUTHENTICATED));
    }

    @Test
    @DisplayName("Admin endpoint with valid Authorization Bearer token proceeds with ROLE_ADMIN attribute")
    void doFilter_validBearerToken_shouldProceed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lifecycle/integrity/repair");
        request.addHeader("Authorization", "Bearer " + CONFIGURED_ADMIN_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest());
        assertEquals("ROLE_ADMIN", request.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
        assertEquals(true, request.getAttribute(ApiKeyAuthenticationFilter.ATTR_AUTHENTICATED));
    }

    @Test
    @DisplayName("Public search endpoint without API key proceeds with ROLE_SEARCH attribute")
    void doFilter_publicSearch_shouldProceedWithoutKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
        request.setParameter("q", "information retrieval");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest(), "Public endpoint must proceed without credentials");
        assertEquals("ROLE_SEARCH", request.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
        assertEquals(false, request.getAttribute(ApiKeyAuthenticationFilter.ATTR_AUTHENTICATED));
    }

    @Test
    @DisplayName("Public index statistics endpoint without API key proceeds with ROLE_SEARCH attribute")
    void doFilter_publicIndexStats_shouldProceedWithoutKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/index/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest());
        assertEquals("ROLE_SEARCH", request.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
    }

    @Test
    @DisplayName("When security is disabled, admin endpoints proceed without credentials")
    void doFilter_disabledSecurity_shouldPassThrough() throws ServletException, IOException {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/index/rebuild");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest(), "When disabled, filter must pass through");
        assertEquals("ROLE_ADMIN", request.getAttribute(ApiKeyAuthenticationFilter.ATTR_ROLE));
    }
}
