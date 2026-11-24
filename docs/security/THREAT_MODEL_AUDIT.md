# SearchForge: Phase 11 Security Audit & Threat Model

**Author:** SearchForge Architecture & Security Engineering Team  
**Status:** Audit Complete — Baseline Threat Catalog Established  
**Date:** November 23, 2026  
**Applicable Scope:** SearchForge Core (Crawling, Indexing, Retrieval, Semantic Search, Admin REST APIs)

---

## 1. Executive Summary

SearchForge operates as an autonomous, high-throughput search engine. It features an active web crawler fetching arbitrary external web pages, a dual-tier in-memory and relational inverted index, an ONNX neural embedding transformer, and public HTTP REST APIs.

While Phases 1 through 10 achieved robust functional capabilities, concurrency protection, transactional recovery, and microsecond-level query latencies, the application has hitherto operated under an "open-network, honest-caller" assumption. Prior to Phase 11, **zero authentication filters exist**, **the web crawler has no IP-range or DNS-rebinding guards**, and **no rate limiting or global input length validation is enforced**.

This document establishes an evidence-based threat model using the STRIDE methodology to catalog, prioritize, and structure remediation for all identified vulnerabilities.

---

## 2. System Architecture & Trust Boundaries

SearchForge's execution architecture spans four distinct trust boundaries:

```
[Untrusted Internet / Web Pages]
             │
             │ Trust Boundary 1 (Inbound Crawled Content & Redirects)
             ▼
   [Web Crawler Fetcher] ──────► [HTML Parser & Sanitizer]
                                              │
[Public Client Traffic]                       ▼
             │                     [Indexing Pipeline]
             │ Trust Boundary 2               │
             ▼                                ▼
     [REST API Controllers] ───► [In-Memory Inverted Index & Vectors]
             │                                │
             │ Trust Boundary 3 (Admin Auth)   ▼
             ▼                     [PostgreSQL 16 / H2 Tier]
   [Lifecycle & Crawl Admin]
```

### Trust Boundaries:
1. **Trust Boundary 1 (Internet → Crawler Fetcher):** Untrusted remote HTTP servers return arbitrary HTML, HTTP status codes, headers, and redirect targets.
2. **Trust Boundary 2 (Public Client → REST APIs):** Untrusted external search callers provide arbitrary query parameters (`q`, `prefix`, `mode`, `limit`).
3. **Trust Boundary 3 (Client → Administrative Operations):** Destructive operational commands (`/rebuild`, `/repair`, `/delete`, `/crawler/start`).
4. **Trust Boundary 4 (Application Logic → Database & File System):** Internal persistence layer and model weights.

---

## 3. Asset Inventory

| Asset Identifier | Asset Description | Impact of Compromise |
| :--- | :--- | :--- |
| **A-1: Internal Network Services** | Private RFC 1918 subnets, cloud metadata (`169.254.169.254`), and loopback services (`127.0.0.1`). | Critical: Full cloud credential theft, intranet infrastructure takeover. |
| **A-2: Inverted Index & Postings** | In-memory RAM index and relational posting tables. | High: Data destruction, index poisoning, unauthorized table drops. |
| **A-3: Neural Vector Storage** | 384-dimensional dense embeddings and ONNX runtime session. | Medium: Memory corruption, inference thread starvation. |
| **A-4: Relational Database** | PostgreSQL tables (`crawled_documents`, `term_postings`, metadata). | High: Schema disruption, data deletion, connection pool exhaustion. |
| **A-5: Service Availability (SLA)** | CPU cycles, memory allocations, Project Loom worker threads. | High: Denial of Service, JVM OutOfMemoryError, search unresponsiveness. |

---

## 4. STRIDE Threat Analysis

| Threat Category | Target Subsystem | Identified Vulnerability & Exploit Scenario | Severity |
| :--- | :--- | :--- | :---: |
| **Spoofing (S)** | Admin Endpoints | **Unauthenticated API Access:** Any anonymous client can issue `POST /api/v1/crawler/start` or `DELETE /api/v1/lifecycle/{id}`, spoofing admin actions without credentials. | **CRITICAL** |
| **Tampering (T)** | Inverted Index | **Destructive Index Wipe:** `POST /api/v1/index/rebuild` drops all database tables and clears memory without authentication or CSRF tokens. | **CRITICAL** |
| **Repudiation (R)** | Audit Logging | **Lack of Security Audit Trail:** Administrative deletions and crawl starts lack caller identification or IP audit logging. | **MEDIUM** |
| **Information Disclosure (I)** | Crawler Fetcher | **Server-Side Request Forgery (SSRF):** Attacker submits `http://169.254.169.254/latest/meta-data` as a seed URL, prompting the crawler to fetch cloud IAM role credentials. | **CRITICAL** |
| **Information Disclosure (I)** | API Exceptions | **Internal Exception Exposure:** Absence of a global `@ControllerAdvice` exposes full stack traces, database schema details, and file paths on unhandled errors. | **MEDIUM** |
| **Denial of Service (D)** | Crawler Fetcher | **DNS Rebinding & Redirect Traps:** Remote host resolves to public IP on first check, but redirects or rebinds to `127.0.0.1:8080`, bypassing naive hostname filters. | **HIGH** |
| **Denial of Service (D)** | Search Pipeline | **Algorithmic DoS via Unbounded Queries:** An attacker sends 10MB search queries, forcing millions of Damerau-Levenshtein matrix calculations and ONNX tokenizations. | **HIGH** |
| **Denial of Service (D)** | REST API | **Unthrottled Query Floods:** Absence of rate limiting allows single clients to flood `/api/v1/search`, exhausting CPU and Hikari pool threads. | **HIGH** |
| **Elevation of Privilege (E)** | Admin Controllers | **Missing RBAC Separation:** No distinction between search consumers and administrators; all endpoints are exposed uniformly on port 8080. | **CRITICAL** |

---

## 5. Evidence-Based Vulnerability Catalog

### Finding 1: Server-Side Request Forgery (SSRF) & Private Subnet Probing
- **Component:** `com.searchforge.crawler.fetcher.DocumentFetcher` (Lines 51–59)
- **Code Inspection:**
  ```java
  Connection.Response response = Jsoup.connect(url)
          .userAgent(properties.userAgent())
          .timeout(properties.readTimeoutMs())
          .maxBodySize(MAX_BODY_SIZE_BYTES)
          .followRedirects(true)
          .execute();
  ```
- **Evidence:** Jsoup uses JVM default DNS resolution without IP pre-flight filtering. It connects directly to private IPv4 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), loopback (`127.0.0.1`, `::1`), link-local (`169.254.0.0/16`), and AWS/GCP metadata endpoints (`169.254.169.254`).
- **Impact:** An attacker can map internal company intranets, query local Docker services, or harvest AWS EC2 IAM role credentials via the crawler.
- **Remediation Task:** **Task 11.2 & 11.4** (Implement `SafeDnsResolver` and `IpRangeValidator`).

---

### Finding 2: Unauthenticated Administrative & Destructive Endpoints
- **Components:**
  - `IndexController`: `POST /api/v1/index/rebuild`, `POST /api/v1/index/build`
  - `IndexLifecycleAdminController`: `DELETE /api/v1/lifecycle/{id}`, `POST /api/v1/lifecycle/integrity/repair`
  - `CrawlerController`: `POST /api/v1/crawler/start`, `POST /api/v1/crawler/reset`, `POST /api/v1/crawler/stop`
- **Evidence:** Zero Spring Security filters, interceptors, or token checks exist in the codebase. Every administrative route is completely open to any HTTP client on port 8080.
- **Impact:** Any user on the local network or through SSRF can wipe the entire search index (`/index/rebuild`), delete active documents, or manipulate crawler state.
- **Remediation Task:** **Task 11.2** (API Key Authentication & RBAC).

---

### Finding 3: Unbounded Search Query Length & Algorithmic Resource Exhaustion
- **Components:**
  - `SearchController`: Lines 120–129
  - `SpellingSuggestionController`: Lines 36–45
  - `AutocompleteController`: Lines 39–50
- **Evidence:**
  `validateQueryParams` in `SearchController` checks that `query` is not blank and `limit` is between 1 and 100, but has **no upper bound on string length**:
  ```java
  if (query == null || query.isBlank()) { ... }
  if (limit < 1 || limit > 100) { ... }
  // Query length is unbounded!
  ```
- **Impact:** Sending a 100KB–10MB query causes the Damerau-Levenshtein spelling corrector to construct an $O(|q| \times |w|)$ matrix across 2,000 vocabulary words, stalling the CPU. It also floods BERT WordPiece subword segmentation and ONNX tensor allocation.
- **Remediation Task:** **Task 11.3** (Input Validation & API Hardening: max 500 characters).

---

### Finding 4: Lack of Rate Limiting & DoS Protection
- **Component:** Entire HTTP Layer (`com.searchforge.ranking.controller`, `com.searchforge.config`)
- **Evidence:** No servlet filters or interceptors inspect client request frequency or enforce token buckets.
- **Impact:** A simple `curl` loop or script can flood SearchForge with hundreds of complex Boolean or hybrid search queries per second, consuming all Project Loom virtual threads and starving Hikari connection pool connections.
- **Remediation Task:** **Task 11.5** (In-Memory Token-Bucket Rate Limiter & RateLimitingFilter).

---

### Finding 5: Missing Standard Security Headers & CORS Permissiveness
- **Component:** `com.searchforge.config.WebConfig`
- **Evidence:** `WebConfig` configures CORS origins but sets `allowedHeaders("*")` and does not set any standard HTTP security headers:
  - Missing `Content-Security-Policy` (CSP)
  - Missing `X-Content-Type-Options: nosniff`
  - Missing `X-Frame-Options: DENY`
  - Missing `Referrer-Policy: strict-origin-when-cross-origin`
- **Impact:** Increases vulnerability to clickjacking and MIME-type confusion attacks in browser environments.
- **Remediation Task:** **Task 11.7** (Security Headers Filter & CORS Hardening).

---

### Finding 6: Unhandled Internal Exception Exposure
- **Component:** Application-wide error handling
- **Evidence:** No `@ControllerAdvice` or `ResponseEntityExceptionHandler` exists. When unexpected runtime exceptions (such as `IllegalArgumentException`, `NoSuchElementException`, or database constraint violations) occur, Spring Boot falls back to default `BasicErrorController`, which can expose internal class paths and stack traces.
- **Impact:** Reconnaissance aid for attackers discovering software versions and table schemas.
- **Remediation Task:** **Task 11.3** (Global Exception Handler & RFC 7807 Problem Details).

---

## 6. Existing Security Controls (Verified Present)

1. **XSS Snippet Sanitization (`HtmlSanitizer.java`):**
   - Correctly escapes entity characters (`&`, `<`, `>`, `"`, `'`) before wrapping matched query keywords in `<mark>` tags.
2. **2MB Body Ceiling (`DocumentFetcher.java`):**
   - Configures `maxBodySize(2 * 1024 * 1024)` to reject single HTML documents over 2MB.
3. **MIME-Type Guard (`DocumentFetcher.java`):**
   - Inspects `Content-Type` header to reject binary/media payloads (PDF, PNG, ZIP, etc.).
4. **Parameterized JPQL Queries (Spring Data JPA Repositories):**
   - All database queries use parameterized placeholders (`:documentId`, etc.), preventing SQL injection.
5. **CORS Origin Whitelist (`WebConfig.java`):**
   - Confines CORS origins strictly to frontend dev ports (`http://localhost:5173`, etc.) rather than wildcard `*`.

---

## 7. Prioritized Remediation Roadmap (Phase 11 Tasks)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SearchForge Phase 11 Security Plan                   │
├──────┬──────────────────────────────┬──────────┬────────────────────────┤
│ Task │ Title                        │ Priority │ Key Remediations       │
├──────┼──────────────────────────────┼──────────┼────────────────────────┤
│ 11.1 │ Threat Model & Audit         │ Done     │ STRIDE Threat Catalog  │
│ 11.2 │ Authentication & RBAC        │ CRITICAL │ API Key & Admin Roles  │
│ 11.3 │ Input Validation & API Guard │ HIGH     │ Query Length & Errors  │
│ 11.4 │ Crawler Security & SSRF      │ CRITICAL │ SafeDns & IP Filtering │
│ 11.5 │ Rate Limiting & DoS Guard    │ HIGH     │ Token Bucket (429)     │
│ 11.6 │ Database & Persistence Sec   │ MEDIUM   │ Least Privilege & ORM  │
│ 11.7 │ Secrets & Config Security    │ MEDIUM   │ Externalized Env & Key │
│ 11.8 │ Security Regression Suite    │ CRITICAL │ Automated Sec Gates    │
│ 11.9 │ Final Audit & Documentation  │ HIGH     │ Verification Report    │
└──────┴──────────────────────────────┴──────────┴────────────────────────┘
```
