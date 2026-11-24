package com.searchforge.crawler.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Validates IP addresses to prevent Server-Side Request Forgery (SSRF) and unauthorized internal network scanning.
 *
 * Checks IPv4 and IPv6 addresses against reserved, loopback, private, link-local,
 * multicast, and cloud instance metadata address spaces.
 */
@Component
public class IpRangeValidator {

    private static final Logger log = LoggerFactory.getLogger(IpRangeValidator.class);

    private final boolean blockPrivateIps;

    public IpRangeValidator() {
        this(true);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IpRangeValidator(@org.springframework.lang.Nullable com.searchforge.security.SecurityProperties securityProperties) {
        this(securityProperties != null && securityProperties.getCrawler() != null
                ? securityProperties.getCrawler().isBlockPrivateIps()
                : true);
    }

    public IpRangeValidator(boolean blockPrivateIps) {
        this.blockPrivateIps = blockPrivateIps;
    }

    public boolean isBlockPrivateIps() {
        return blockPrivateIps;
    }

    /**
     * Determines whether an IP address is safe for outbound crawler connections.
     *
     * @param address The resolved InetAddress to evaluate.
     * @return true if the address is a safe public IP; false if it is reserved/private/loopback.
     */
    public boolean isSafe(InetAddress address) {
        if (address == null) {
            return false;
        }

        // If private IP blocking is explicitly disabled (e.g., in test environments with local WireMock)
        if (!blockPrivateIps) {
            return true;
        }

        if (address.isLoopbackAddress()) {
            log.warn("Blocked SSRF attempt: Loopback address '{}'", address.getHostAddress());
            return false;
        }

        if (address.isAnyLocalAddress()) {
            log.warn("Blocked SSRF attempt: AnyLocal address '{}'", address.getHostAddress());
            return false;
        }

        if (address.isSiteLocalAddress()) {
            log.warn("Blocked SSRF attempt: RFC 1918 Private site-local address '{}'", address.getHostAddress());
            return false;
        }

        if (address.isLinkLocalAddress()) {
            log.warn("Blocked SSRF attempt: Link-local address '{}'", address.getHostAddress());
            return false;
        }

        if (address.isMulticastAddress()) {
            log.warn("Blocked SSRF attempt: Multicast address '{}'", address.getHostAddress());
            return false;
        }

        byte[] octets = address.getAddress();

        if (address instanceof Inet4Address) {
            return isSafeIpv4(octets);
        } else if (address instanceof Inet6Address) {
            return isSafeIpv6(octets);
        }

        return false;
    }

    private boolean isSafeIpv4(byte[] octets) {
        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;

        // 0.0.0.0/8 (Current network)
        if (b0 == 0) {
            log.warn("Blocked SSRF attempt: IPv4 0.0.0.0/8");
            return false;
        }

        // 10.0.0.0/8 (RFC 1918 Private)
        if (b0 == 10) {
            log.warn("Blocked SSRF attempt: IPv4 10.0.0.0/8");
            return false;
        }

        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) {
            log.warn("Blocked SSRF attempt: IPv4 127.0.0.0/8");
            return false;
        }

        // 169.254.0.0/16 (Link-local & AWS/GCP/Azure Metadata: 169.254.169.254)
        if (b0 == 169 && b1 == 254) {
            log.warn("Blocked SSRF attempt: IPv4 Cloud Metadata / Link-local 169.254.0.0/16");
            return false;
        }

        // 172.16.0.0/12 (RFC 1918 Private: 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
            log.warn("Blocked SSRF attempt: IPv4 172.16.0.0/12");
            return false;
        }

        // 192.168.0.0/16 (RFC 1918 Private)
        if (b0 == 192 && b1 == 168) {
            log.warn("Blocked SSRF attempt: IPv4 192.168.0.0/16");
            return false;
        }

        // 100.64.0.0/10 (Carrier-Grade NAT RFC 6598: 100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
            log.warn("Blocked SSRF attempt: IPv4 CGNAT 100.64.0.0/10");
            return false;
        }

        // 192.0.2.0/24 (TEST-NET-1), 198.51.100.0/24 (TEST-NET-2), 203.0.113.0/24 (TEST-NET-3)
        if (b0 == 192 && b1 == 0 && (octets[2] & 0xFF) == 2) return false;
        if (b0 == 198 && b1 == 51 && (octets[2] & 0xFF) == 100) return false;
        if (b0 == 203 && b1 == 0 && (octets[2] & 0xFF) == 113) return false;

        // 224.0.0.0/4 (Multicast 224-239) & 240.0.0.0/4 (Reserved 240-255)
        if (b0 >= 224) {
            log.warn("Blocked SSRF attempt: IPv4 Multicast/Reserved 224.0.0.0/3");
            return false;
        }

        return true;
    }

    private boolean isSafeIpv6(byte[] octets) {
        // IPv4-mapped IPv6 address (::ffff:x.x.x.x)
        if (isIpv4MappedIpv6(octets)) {
            byte[] ipv4Octets = new byte[4];
            System.arraycopy(octets, 12, ipv4Octets, 0, 4);
            return isSafeIpv4(ipv4Octets);
        }

        // Loopback (::1)
        if (isIpv6Loopback(octets)) {
            log.warn("Blocked SSRF attempt: IPv6 Loopback ::1");
            return false;
        }

        // Unique Local Address (fc00::/7 - RFC 4193, includes fc00:: and fd00::)
        int firstByte = octets[0] & 0xFF;
        if ((firstByte & 0xFE) == 0xFC) {
            log.warn("Blocked SSRF attempt: IPv6 Unique Local Address fc00::/7");
            return false;
        }

        // Link-Local Address (fe80::/10 - RFC 4291)
        int secondByte = octets[1] & 0xFF;
        if (firstByte == 0xFE && (secondByte & 0xC0) == 0x80) {
            log.warn("Blocked SSRF attempt: IPv6 Link-Local fe80::/10");
            return false;
        }

        // Multicast (ff00::/8)
        if (firstByte == 0xFF) {
            log.warn("Blocked SSRF attempt: IPv6 Multicast ff00::/8");
            return false;
        }

        return true;
    }

    private boolean isIpv4MappedIpv6(byte[] octets) {
        for (int i = 0; i < 10; i++) {
            if (octets[i] != 0) return false;
        }
        return (octets[10] & 0xFF) == 0xFF && (octets[11] & 0xFF) == 0xFF;
    }

    private boolean isIpv6Loopback(byte[] octets) {
        for (int i = 0; i < 15; i++) {
            if (octets[i] != 0) return false;
        }
        return octets[15] == 1;
    }
}
