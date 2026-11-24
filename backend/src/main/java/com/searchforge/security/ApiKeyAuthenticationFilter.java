package com.searchforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight, zero-dependency API Key authentication filter for SearchForge.
 *
 * Enforces Role-Based Access Control (RBAC):
 * - Public Endpoints (ROLE_SEARCH): Free-text lexical, semantic, hybrid, boolean search,
 *   autocomplete, spelling suggestions, corpus statistics, and Actuator health probes.
 * - Administrative Endpoints (ROLE_ADMIN): Web crawler orchestration, index builds/rebuilds,
 *   staged document lifecycle management, deletions, and self-healing index repair.
 *
 * Implements constant-time key comparison to protect against timing side-channel attacks.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    public static final String ATTR_ROLE = "searchforge.role";
    public static final String ATTR_AUTHENTICATED = "searchforge.authenticated";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_SEARCH = "ROLE_SEARCH";

    private final SecurityProperties properties;

    public ApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "SecurityProperties cannot be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. If security filter is globally disabled (e.g. in targeted slice tests), pass through
        if (!properties.isEnabled()) {
            request.setAttribute(ATTR_ROLE, ROLE_ADMIN);
            request.setAttribute(ATTR_AUTHENTICATED, true);
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 2. Determine if the requested URI requires administrative authorization
        if (isAdminEndpoint(path)) {
            String providedKey = extractApiKey(request);

            if (providedKey == null || providedKey.isBlank()) {
                log.warn("Unauthorized access attempt to administrative endpoint '{}' from IP '{}': Missing API key",
                        path, request.getRemoteAddr());
                writeUnauthorizedResponse(response, path, "Missing API key. Provide a valid 'X-API-KEY' header.");
                return;
            }

            if (!isKeyValid(providedKey, properties.getAdminApiKey())) {
                log.warn("Unauthorized access attempt to administrative endpoint '{}' from IP '{}': Invalid API key",
                        path, request.getRemoteAddr());
                writeUnauthorizedResponse(response, path, "Invalid API key.");
                return;
            }

            // Key verified successfully: attach authentication details to request
            request.setAttribute(ATTR_ROLE, ROLE_ADMIN);
            request.setAttribute(ATTR_AUTHENTICATED, true);
            log.debug("Authenticated administrative request to '{}' with ROLE_ADMIN", path);

        } else {
            // Public endpoint (search, stats, health)
            request.setAttribute(ATTR_ROLE, ROLE_SEARCH);
            request.setAttribute(ATTR_AUTHENTICATED, false);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Identifies endpoints requiring ROLE_ADMIN privileges.
     */
    public boolean isAdminEndpoint(String path) {
        if (path == null) {
            return false;
        }

        // Web Crawler control endpoints
        if (path.startsWith("/api/v1/crawler")) {
            return true;
        }

        // Document Lifecycle & Self-Healing administrative endpoints
        if (path.startsWith("/api/v1/lifecycle")) {
            return true;
        }

        // Destructive / heavy indexing operations (stats endpoint /api/v1/index/stats remains public)
        if (path.equals("/api/v1/index/build")
                || path.equals("/api/v1/index/rebuild")
                || path.equals("/api/v1/index/warmup")) {
            return true;
        }

        return false;
    }

    /**
     * Extracts API key from 'X-API-KEY' or 'Authorization: Bearer <token>' headers.
     */
    private String extractApiKey(HttpServletRequest request) {
        String key = request.getHeader(properties.getHeaderName());
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }

        return null;
    }

    /**
     * Constant-time byte array comparison using MessageDigest.isEqual
     * to eliminate timing side-channel attacks during credential verification.
     */
    private boolean isKeyValid(String providedKey, String configuredAdminKey) {
        if (providedKey == null || configuredAdminKey == null || configuredAdminKey.isBlank()) {
            return false;
        }

        byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = configuredAdminKey.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    /**
     * Writes standardized RFC-compliant JSON 401 Unauthorized response body.
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String path, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String json = String.format("""
                {
                  "timestamp": "%s",
                  "status": 401,
                  "error": "Unauthorized",
                  "message": "%s",
                  "path": "%s"
                }
                """, Instant.now(), detail, path);

        response.getWriter().write(json);
    }
}
