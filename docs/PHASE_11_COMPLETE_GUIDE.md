# SearchForge: Phase 11 Masterclass Guide
## Security Engineering, API Hardening & Production Security

**Author:** SearchForge Architecture & Security Engineering Team  
**Status:** Complete & Hermetically Verified  
**Total Test Baseline:** 657 Automated Tests (644 Backend + 13 Frontend, 100% Passing)

---

## 1. Overview: Objectives & Outcomes

Phase 11 represents the security engineering and production hardening capstone of SearchForge. Following the performance, memory, and scalability optimizations of Phase 10, Phase 11 systematically audits, hardens, and verifies every internal and external attack surface across the search engine without regressing query latency, crawler throughput, or index consistency.

By adopting a strict **Defense-in-Depth** model, SearchForge transitions from an open-network research prototype into an enterprise-grade search system resilient to unauthenticated tampering, Server-Side Request Forgery (SSRF), distributed abuse, algorithmic resource exhaustion, and secret leakage.

### Key Outcomes:
- **100% Baseline Threat Mitigation:** Remediated all 6 critical vulnerabilities identified during initial threat modeling across all STRIDE threat categories.
- **Constant-Time Administrative Access Control:** Implemented `ApiKeyAuthenticationFilter` guarding administrative routes (`/api/v1/lifecycle/**`, `/api/v1/index/**`, `/api/v1/crawler/**`) using `MessageDigest.isEqual` to prevent timing side-channel attacks.
- **SSRF Crawler Sandbox:** Built `SafeDnsResolver` and `IpRangeValidator` blocking all IPv4 loopback (`127.0.0.0/8`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local / cloud metadata (`169.254.169.254`), multicast, and IPv6 unique local. Enforced `followRedirects(false)` with a manual 3-hop cycle-detecting redirect loop.
- **Lock-Free Monotonic Token Bucket Rate Limiting:** Implemented `RateLimitingFilter` at `Ordered.HIGHEST_PRECEDENCE + 5` supporting tiered capacity (`SEARCH`: 60 req/min, `ADMIN`: 10 req/min) with RFC 6585 HTTP 429 and `Retry-After` headers.
- **Strict Input Validation & Algorithmic DoS Defense:** Defensively bounded search queries ($q \le 500$), autocomplete prefixes ($\le 100$), spelling suggestions ($\le 200$), and seed URLs ($\le 50$), neutralizing quadratic Damerau-Levenshtein and ONNX tensor allocation churn.
- **RFC 7807 Error Sanitization:** Centralized exception handling via `GlobalExceptionHandler` and `ApiErrorResponse`, masking database constraint violations (HTTP 409) and unexpected exceptions (HTTP 500) to protect schema internals.
- **Secure Configuration & Pre-Flight Validation:** Created `SecurityStartupValidator` to prevent production startup with default secrets, masked keys in logs (`sf****od`), and sanitized Spring Boot Actuator endpoints.
- **Automated Security Regression Suite:** Created `SecurityRegressionSuiteTest` enforcing 6 end-to-end security gates in continuous integration.
- **Hermetic Test Suite Milestone:** **657 hermetic tests (644 backend + 13 frontend)** passing at 100%.

---

## 2. Threat Modeling & Trust Boundaries (STRIDE)

In Task 11.1, the system was decomposed into four distinct trust boundaries:
1. **Trust Boundary 1 (Untrusted Internet → Crawler Fetcher):** Arbitrary HTML, malicious HTTP redirects, private IP targeting.
2. **Trust Boundary 2 (Public Clients → Search APIs):** Maliciously long queries, query parameter fuzzing, request flooding.
3. **Trust Boundary 3 (Admin Clients → Administrative Ops):** Unauthorized index rebuilds, document deletion, crawler control.
4. **Trust Boundary 4 (Application Logic → Database & Storage):** SQL injection, connection pool exhaustion, schema leaks.

### STRIDE Coverage Matrix:
- **Spoofing (S):** Prevented via `ApiKeyAuthenticationFilter` requiring valid `X-API-KEY`.
- **Tampering (T):** Prevented by partitioning public read-only search from protected mutating administrative routes.
- **Repudiation (R):** Prevented via structured security audit logging capturing client IP, endpoint, and action.
- **Information Disclosure (I):** Prevented via SSRF network sandboxing, RFC 7807 exception masking, and secret redaction.
- **Denial of Service (D):** Prevented via TokenBucket rate limiting and strict input string length bounds.
- **Elevation of Privilege (E):** Prevented by enforcing strict RBAC URL matching before Spring MVC dispatch.

---

## 3. Defense-in-Depth Filter Chain Architecture

Servlet filters execute in a strict deterministic order configured via Spring's `Ordered` interface:

```
[ Inbound HTTP Request ]
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. RateLimitingFilter (HIGHEST_PRECEDENCE + 5)              │
│    - Early drop: drops abusive flooding before any auth     │
│    - Returns HTTP 429 + Retry-After + X-RateLimit-* headers │
└─────────────────────────────┬───────────────────────────────┘
                              │ Allowed
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. ApiKeyAuthenticationFilter (HIGHEST_PRECEDENCE + 10)     │
│    - Enforces X-API-KEY on admin routes                     │
│    - MessageDigest.isEqual() constant-time comparison       │
│    - Returns HTTP 401 + RFC 7807 ApiErrorResponse           │
└─────────────────────────────┬───────────────────────────────┘
                              │ Authenticated / Public
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Spring MVC DispatcherServlet & Controllers               │
│    - Bound validation: q <= 500, seeds <= 50, url <= 2048   │
│    - GlobalExceptionHandler: Sanitized RFC 7807 responses   │
└─────────────────────────────────────────────────────────────┘
```

### Why Order Matters:
- If `ApiKeyAuthenticationFilter` executed before `RateLimitingFilter`, unauthenticated attackers could flood the server with invalid API keys, forcing expensive cryptographic comparisons and database lookups.
- By placing `RateLimitingFilter` at `HIGHEST_PRECEDENCE + 5`, malicious traffic is dropped immediately with minimal CPU overhead.

---

## 4. Constant-Time Authentication & Timing Attack Defense

Standard string equality (`String.equals()`) in Java terminates on the first mismatched character:
```java
// VULNERABLE TO TIMING ATTACKS:
if (configuredKey.equals(providedKey)) { ... }
```
An attacker measuring nanosecond latency differences over millions of requests can deduce the key character-by-character based on how long the comparison takes.

SearchForge prevents timing attacks using `MessageDigest.isEqual()`:
```java
public boolean validateKey(String providedKey) {
    if (providedKey == null || configuredKey == null) {
        return false;
    }
    byte[] a = providedKey.getBytes(StandardCharsets.UTF_8);
    byte[] b = configuredKey.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);
}
```
`MessageDigest.isEqual()` compares every byte in $O(N)$ constant time regardless of where the first difference occurs, eliminating timing side channels.

---

## 5. Web Crawler Security & SSRF Protection

Allowing an autonomous crawler to fetch arbitrary URLs introduces critical Server-Side Request Forgery (SSRF) risks. An attacker can supply `http://169.254.169.254/latest/meta-data/` to harvest cloud IAM credentials or `http://127.0.0.1:8080/` to invoke internal microservices.

SearchForge's SSRF defense consists of three layers:
1. **`IpRangeValidator`:** Evaluates resolved `InetAddress` instances against:
   - Loopback (`127.0.0.0/8`, `::1`)
   - RFC 1918 Private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
   - Link-local and Cloud Metadata (`169.254.0.0/16`, `169.254.169.254`)
   - Multicast, AnyLocal (`0.0.0.0`), and IPv6 unique local (`fc00::/7`)
2. **`SafeDnsResolver`:**
   - Pre-flight DNS resolution inspects all multi-homed IP addresses returned for a hostname. If *any* IP is prohibited, the connection is blocked.
   - Enforces web schemes (`http`, `https`) and standard ports (80, 443, 8080, 8443).
3. **Controlled Redirect Loop:**
   - Jsoup's automatic redirect following is disabled (`.followRedirects(false)`).
   - `DocumentFetcher` manually executes a redirect loop (maximum 3 hops), re-evaluating `SafeDnsResolver` on every hop and tracking visited URLs in a `Set<String>` to prevent cyclic redirect tarpits.

---

## 6. Token Bucket Rate Limiting Architecture

The rate limiter employs a lock-free `TokenBucket` algorithm based on monotonic hardware clocks:
- **Refill Mechanism:** Uses `System.nanoTime()` to compute tokens added since the last request:
  $$\Delta t = \frac{t_{\text{now}} - t_{\text{last}}}{10^9}$$
  $$\text{tokens} = \min(\text{capacity}, \text{tokens} + \Delta t \times \text{refillRate})$$
- **Monotonic Clock Advantage:** `System.nanoTime()` is unaffected by system clock skew, NTP synchronizations, or leap seconds, avoiding negative token jumps or sudden bucket refills.
- **Tier Partitioning:**
  - `SEARCH` tier: Capacity 60 tokens, Refill 1.0 token/sec (60 req/min continuous).
  - `ADMIN` tier: Capacity 10 tokens, Refill 0.167 tokens/sec (10 req/min continuous).
- **Client IP Extraction:** Inspects `X-Forwarded-For` and `X-Real-IP` (sanitized at the reverse proxy boundary) with fallback to `remoteAddr`.

---

## 7. Automated Security Regression Suite (105 Tests)

To prevent security regressions during future feature development, Phase 11 established 105 automated security tests:

| Test Class | Focus Area | Test Count |
| :--- | :--- | :---: |
| `ApiKeyAuthenticationFilterTest` | Unit verification of API key parsing, path matching, and timing safety | 31 |
| `AdminSecurityIntegrationTest` | End-to-end MockMvc testing across protected administrative endpoints | 7 |
| `InputValidationAndHardeningTest` | Boundary verification for queries, URLs, pagination, and suggest strings | 13 |
| `SsrfProtectionTest` | IP range classification, pre-flight DNS, redirect limits, and cycle detection | 18 |
| `RateLimitingTest` | Token bucket mechanics, client IP extraction, tier isolation, and HTTP 429 headers | 14 |
| `DatabaseSecurityTest` | Parameterized query safety, SQL exception masking, HikariCP settings | 6 |
| `SecretsAndConfigurationSecurityTest` | Secret redaction, startup pre-flight checks, Actuator masking, gitignore rules | 10 |
| `SecurityRegressionSuiteTest` | Six comprehensive automated regression gates | 6 |
| **Total Security Tests** | **Complete Phase 11 Security Verification Suite** | **105** |

---

## 8. Staff-Engineer Interview Talking Points

1. **Why not Spring Security?**  
   "We evaluated Spring Security, but for our lightweight, high-performance search engine architecture, Spring Security introduces substantial dependency bloat, complex context propagation across Project Loom virtual threads, and unnecessary overhead. Instead, we implemented purpose-built, zero-allocation servlet filters (`ApiKeyAuthenticationFilter` and `RateLimitingFilter`) that execute in nanoseconds, integrate directly with Spring's filter ordering, and provide exact constant-time byte comparisons without thread-local baggage."

2. **How does your crawler handle SSRF via DNS rebinding?**  
   "We perform pre-flight DNS resolution before opening any socket connection, evaluating every IP address associated with the domain against our `IpRangeValidator`. We disable Jsoup's automatic redirect following (`.followRedirects(false)`) and implement a manual redirect loop capped at 3 hops, ensuring every redirect target undergoes full DNS and IP validation before the crawler issues an HTTP request."

3. **What happens when an admin API key is compromised?**  
   "Our system supports zero-downtime key rotation. The operator generates a 32-byte cryptographic secret (`openssl rand -hex 32`), updates the secret in the environment/Secrets Manager, performs a rolling restart of backend instances, verifies health on the new key, and updates administrative clients. Startup validation guarantees that deployment will halt if a default key is detected in production mode."
