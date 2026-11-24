package com.searchforge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SearchForge API security and role-based access control.
 */
@ConfigurationProperties(prefix = "searchforge.security")
public class SecurityProperties {

    /**
     * Whether API security authentication filters are active.
     */
    private boolean enabled = true;

    /**
     * Secret API key required for privileged administrative operations
     * (crawler management, index lifecycle, rebuilds, and repairs).
     */
    private String adminApiKey = "sf-admin-secret-key-prod";

    /**
     * Optional search API key for public endpoints.
     * When left empty or null, search endpoints remain public (ROLE_SEARCH).
     */
    private String searchApiKey = "";

    /**
     * HTTP Header used for API key transmission. Defaults to "X-API-KEY".
     */
    private String headerName = "X-API-KEY";

    /**
     * Crawler security and SSRF protection settings.
     */
    private CrawlerSecurity crawler = new CrawlerSecurity();

    /**
     * Token Bucket API rate limiting settings.
     */
    private RateLimitSecurity rateLimit = new RateLimitSecurity();

    /**
     * Whether strict secret enforcement is enabled (fails startup if default/weak keys are detected).
     */
    private boolean strictSecrets = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public void setAdminApiKey(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    public String getSearchApiKey() {
        return searchApiKey;
    }

    public void setSearchApiKey(String searchApiKey) {
        this.searchApiKey = searchApiKey;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public CrawlerSecurity getCrawler() {
        return crawler;
    }

    public void setCrawler(CrawlerSecurity crawler) {
        this.crawler = crawler;
    }

    public RateLimitSecurity getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitSecurity rateLimit) {
        this.rateLimit = rateLimit;
    }

    public boolean isStrictSecrets() {
        return strictSecrets;
    }

    public void setStrictSecrets(boolean strictSecrets) {
        this.strictSecrets = strictSecrets;
    }

    @Override
    public String toString() {
        return "SecurityProperties{" +
                "enabled=" + enabled +
                ", adminApiKey='" + maskSecret(adminApiKey) + '\'' +
                ", searchApiKey='" + maskSecret(searchApiKey) + '\'' +
                ", headerName='" + headerName + '\'' +
                ", strictSecrets=" + strictSecrets +
                '}';
    }

    /**
     * Masks sensitive credential strings to prevent accidental leakage in logs and telemetry.
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "[EMPTY]";
        }
        if (secret.length() <= 4) {
            return "****";
        }
        return secret.substring(0, 2) + "****" + secret.substring(secret.length() - 2);
    }

    /**
     * API Token Bucket Rate Limiting configuration.
     */
    public static class RateLimitSecurity {
        private boolean enabled = true;
        private int searchRequestsPerMinute = 60;
        private int searchBurstCapacity = 20;
        private int adminRequestsPerMinute = 20;
        private int adminBurstCapacity = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSearchRequestsPerMinute() {
            return searchRequestsPerMinute;
        }

        public void setSearchRequestsPerMinute(int searchRequestsPerMinute) {
            this.searchRequestsPerMinute = searchRequestsPerMinute;
        }

        public int getSearchBurstCapacity() {
            return searchBurstCapacity;
        }

        public void setSearchBurstCapacity(int searchBurstCapacity) {
            this.searchBurstCapacity = searchBurstCapacity;
        }

        public int getAdminRequestsPerMinute() {
            return adminRequestsPerMinute;
        }

        public void setAdminRequestsPerMinute(int adminRequestsPerMinute) {
            this.adminRequestsPerMinute = adminRequestsPerMinute;
        }

        public int getAdminBurstCapacity() {
            return adminBurstCapacity;
        }

        public void setAdminBurstCapacity(int adminBurstCapacity) {
            this.adminBurstCapacity = adminBurstCapacity;
        }
    }

    /**
     * Web crawler security configuration for SSRF, port filtering, and redirect controls.
     */
    public static class CrawlerSecurity {
        private boolean ssrfProtectionEnabled = true;
        private boolean blockPrivateIps = true;
        private int maxRedirects = 3;
        private java.util.Set<Integer> allowedPorts = java.util.Set.of(80, 443, 8080, 8443);

        public boolean isSsrfProtectionEnabled() {
            return ssrfProtectionEnabled;
        }

        public void setSsrfProtectionEnabled(boolean ssrfProtectionEnabled) {
            this.ssrfProtectionEnabled = ssrfProtectionEnabled;
        }

        public boolean isBlockPrivateIps() {
            return blockPrivateIps;
        }

        public void setBlockPrivateIps(boolean blockPrivateIps) {
            this.blockPrivateIps = blockPrivateIps;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        public java.util.Set<Integer> getAllowedPorts() {
            return allowedPorts;
        }

        public void setAllowedPorts(java.util.Set<Integer> allowedPorts) {
            this.allowedPorts = allowedPorts;
        }
    }
}
