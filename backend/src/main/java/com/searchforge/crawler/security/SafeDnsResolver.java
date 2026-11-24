package com.searchforge.crawler.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates URLs and resolves hostnames with defensive DNS pre-flight checks to prevent SSRF attacks.
 *
 * Enforces:
 * 1. Strict protocol scheme whitelist (http, https only).
 * 2. Port restrictions to prevent scanning internal ports (e.g., 22, 6379, 5432).
 * 3. Pre-flight DNS resolution verifying that every resolved IP address is public and non-reserved.
 */
@Component
public class SafeDnsResolver {

    private static final Logger log = LoggerFactory.getLogger(SafeDnsResolver.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<Integer> DEFAULT_ALLOWED_PORTS = Set.of(80, 443, 8080, 8443);

    private final IpRangeValidator ipRangeValidator;
    private final Set<Integer> allowedPorts;

    public SafeDnsResolver(IpRangeValidator ipRangeValidator) {
        this(ipRangeValidator, DEFAULT_ALLOWED_PORTS);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SafeDnsResolver(IpRangeValidator ipRangeValidator,
                           @org.springframework.lang.Nullable com.searchforge.security.SecurityProperties securityProperties) {
        this(ipRangeValidator,
                (securityProperties != null && securityProperties.getCrawler() != null && securityProperties.getCrawler().getAllowedPorts() != null)
                        ? securityProperties.getCrawler().getAllowedPorts()
                        : DEFAULT_ALLOWED_PORTS);
    }

    public SafeDnsResolver(IpRangeValidator ipRangeValidator, Set<Integer> allowedPorts) {
        this.ipRangeValidator = ipRangeValidator;
        this.allowedPorts = allowedPorts != null ? allowedPorts : DEFAULT_ALLOWED_PORTS;
    }

    /**
     * Determines whether a given URL is safe to fetch outbound.
     *
     * @param url The target URL string.
     * @return true if scheme, port, and resolved IP addresses are safe; false otherwise.
     */
    public boolean isSafeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                log.warn("Blocked SSRF attempt: Prohibited URL scheme '{}' in '{}'", scheme, url);
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("Blocked SSRF attempt: Missing host in URL '{}'", url);
                return false;
            }

            int port = uri.getPort();
            if (port != -1 && ipRangeValidator.isBlockPrivateIps() && !allowedPorts.isEmpty() && !allowedPorts.contains(port)) {
                log.warn("Blocked SSRF attempt: Prohibited port {} in URL '{}'", port, url);
                return false;
            }

            return isSafeHost(host);

        } catch (URISyntaxException e) {
            log.warn("Blocked invalid URL syntax: '{}'", url);
            return false;
        }
    }

    /**
     * Resolves all IP addresses for a host and verifies none are private or reserved.
     *
     * @param host The hostname or IP literal.
     * @return true if all resolved addresses are safe; false if resolution fails or any IP is unsafe.
     */
    public boolean isSafeHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host.trim());
            if (addresses == null || addresses.length == 0) {
                return false;
            }

            for (InetAddress address : addresses) {
                if (!ipRangeValidator.isSafe(address)) {
                    log.warn("Host '{}' resolved to unsafe IP address '{}' - connection blocked",
                            host, address.getHostAddress());
                    return false;
                }
            }

            return true;

        } catch (UnknownHostException e) {
            log.debug("DNS resolution failed for host '{}': {}", host, e.getMessage());
            return false;
        } catch (SecurityException e) {
            log.warn("Security exception during DNS lookup for '{}': {}", host, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves and returns all verified safe addresses, or throws SecurityException if any address is unsafe.
     *
     * @param host The hostname to resolve.
     * @return Array of safe InetAddresses.
     * @throws UnknownHostException if host cannot be resolved.
     * @throws SecurityException if any resolved IP belongs to a forbidden/private range.
     */
    public InetAddress[] resolveAndValidate(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host.trim());
        for (InetAddress address : addresses) {
            if (!ipRangeValidator.isSafe(address)) {
                throw new SecurityException("Host '" + host + "' resolved to forbidden IP '" + address.getHostAddress() + "'");
            }
        }
        return addresses;
    }

    public IpRangeValidator getIpRangeValidator() {
        return ipRangeValidator;
    }
}
