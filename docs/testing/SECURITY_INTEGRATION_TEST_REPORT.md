# SearchForge Phase 14 — Security & Authorization Integration Test Report

**Execution Date:** 2026-11-26  
**Test Suites:** [SecurityIntegrationTest.java](file:///d:/projects/backend/src/test/java/com/searchforge/integration/SecurityIntegrationTest.java) (`4/4`) + [SecurityRegressionSuiteTest.java](file:///d:/projects/backend/src/test/java/com/searchforge/security/regression/SecurityRegressionSuiteTest.java) (`6/6`)  
**Total Result:** **10 / 10 passed** (`0` failures, `0` errors, `0` skipped)

---

## 1. Verified Security Integration Controls

| Control Area | Verified Behavior |
| :--- | :--- |
| **Authentication & RBAC** | Public `/api/v1/search` and `/api/v1/index/stats` return `200`. Admin routes (`/api/v1/lifecycle/metrics`, `/api/v1/index/build`, `/api/v1/crawler/documents`) return `401` on missing/invalid keys, `200` on valid `X-API-KEY` or `Authorization: Bearer`, and `405` (with valid key) vs `401` (without key) on unsupported HTTP methods. |
| **Rate Limiting** | `RateLimitingFilter` executes before `ApiKeyAuthenticationFilter`, throttling `SEARCH` and `ADMIN` tiers independently per client IP with `429 Too Many Requests`, `Retry-After`, and `X-RateLimit-Remaining: 0`. |
| **Input Validation & Masking** | Oversized queries (`> 500` chars), negative `limit`, and malformed JSON return `400 Bad Request` without exposing Java class names, SQL statements, or API keys. |
| **SSRF & Redirect Protection** | `SafeDnsResolver` + `DocumentFetcher` block IPv4 loopback (`127.0.0.1`), RFC 1918 private ranges (`10.0.0.1`, `192.168.1.1`), cloud metadata (`169.254.169.254`), IPv6 special addresses (`[::1]`, `[fe80::1]`), and HTTP `302` redirects to `169.254.169.254` via manual redirect loop (`followRedirects(false)`). |

---

## 2. Residual Architectural Observation (DNS Rebinding / TOCTOU)

* `DocumentFetcher.fetch()` validates DNS resolution via `SafeDnsResolver.isSafeUrl(currentUrl)` **before** calling `Jsoup.connect(currentUrl)`. Because `Jsoup.connect` performs its own socket DNS lookup rather than pinning the pre-validated `InetAddress`, a theoretical **DNS rebinding (TOCTOU)** window exists if an attacker controls a 0-TTL DNS server that swaps from a public IP to `127.0.0.1` between validation and TCP connect. Documented as a known limitation; mitigated partially by JVM DNS caching (`networkaddress.cache.ttl`).
