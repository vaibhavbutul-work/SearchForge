# SearchForge — Phase 2: Web Crawler Architecture & Preparation Notes

> **Phase Status:** Planned (Preparation & Architecture Only — No Code in Phase 1)  
> **Prerequisite:** Completion of Spring Boot 4.1.x upgrade on a dedicated branch prior to starting Phase 2 code.

---

## 1. Overview & Vision for Phase 2

Phase 1 established a hardened backend foundation (Java 21, Spring Boot, PostgreSQL container, HikariCP resilience, and multi-tier automated testing).

In **Phase 2**, SearchForge begins constructing the **Indexing Pipeline** by implementing a custom, standalone **Web Crawler**. The crawler's objective is to systematically discover, download, and extract text and hyperlinks from web pages within configured domain boundaries, preparing raw document payloads for subsequent parsing and inverted indexing (Phases 3 & 4).

---

## 2. Core Crawler Architectural Components

```
   [ Seed URLs ]
         │
         ▼
 ┌────────────────────────────────────────────────────────────┐
 │                   URL Frontier (Queue)                     │
 │  - Priority Queue / ConcurrentLinkedQueue                  │
 │  - BFS (Breadth-First Search) exploration order            │
 └──────────────┬────────────────────────────▲────────────────┘
                │ Pop Next URL               │ Push Discovered URLs
                ▼                            │ (if unvisited & allowed)
 ┌──────────────────────────────────────┐    │
 │       Visited URL Filter / Deduplication │
 │  - ConcurrentHashSet / Bloom Filter  │    │
 └──────────────┬───────────────────────┘    │
                │ Not Visited                │
                ▼                            │
 ┌──────────────────────────────────────┐    │
 │       Politeness & Rate Limiter      │    │
 │  - robots.txt parser & compliance    │    │
 │  - Per-domain crawl delay (e.g. 1s)  │    │
 └──────────────┬───────────────────────┘    │
                │ Fetch HTTP                 │
                ▼                            │
 ┌──────────────────────────────────────┐    │
 │            Jsoup HTTP Client         │    │
 │  - Fetches HTML over TCP/TLS         │    │
 │  - Respects HTTP timeouts & headers  │    │
 └──────────────┬───────────────────────┘    │
                │ HTML Document              │
                ▼                            │
 ┌──────────────────────────────────────┐    │
 │       HTML Parser & Extractor        │    │
 │  - Extracts: title, body, metadata   │    │
 │  - Extracts: outbound <a href> links ├────┘
 └──────────────┬───────────────────────┘
                │ Raw Document Payload
                ▼
 ┌────────────────────────────────────────────────────────────┐
 │                PostgreSQL Staging Storage                  │
 │  - crawled_documents table (URL, title, text, timestamp)   │
 └────────────────────────────────────────────────────────────┘
```

### Detailed Component Roles:

1. **URL Frontier (`UrlFrontier`):**
   - Stores URLs scheduled for crawling.
   - Operates in Breadth-First Search (BFS) order to guarantee discovery across diverse site sections before descending into deep hierarchies.
   - Implemented using thread-safe data structures (`ConcurrentLinkedQueue` or domain-partitioned priority queues).

2. **Visited URL Filter / Deduplicator (`VisitedFilter`):**
   - Guarantees each canonical URL is crawled at most once per indexing cycle, preventing infinite cycles (e.g., recursive links or calendar traps).
   - In-memory `ConcurrentHashMap.newKeySet()` for initial scale; scalable to Bloom Filters or database indexing for large-scale crawling.

3. **Politeness & `robots.txt` Compliance (`PolitenessManager`):**
   - Automatically downloads and parses `/robots.txt` for each unique host before fetching pages.
   - Enforces `Disallow` directives and `Crawl-delay` intervals.
   - Introduces per-domain rate limiting (e.g., minimum 1,000ms pause between successive requests to the same host) to avoid overloading target servers.

4. **Document Fetcher & Parser (`DocumentFetcher`):**
   - Uses **Jsoup** to download HTML via HTTP GET with custom `User-Agent: SearchForgeBot/1.0 (+https://github.com/vaibhavbutul-work/SearchForge)`.
   - Strips non-content tags (`<script>`, `<style>`, `<nav>`, `<footer>`) to isolate high-value readable body text.
   - Extracts outbound hyperlinks, normalizes them against base URIs, and filters for matching domain scopes.

5. **Concurrency Model:**
   - Leveraging Java 21's **Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor()`).
   - Because web crawling is heavily I/O-bound (waiting on network sockets and DNS resolution), virtual threads allow running hundreds of concurrent fetchers with minimal RAM footprint and zero OS thread exhaustion.

---

## 3. Storage & Entity Modeling (Phase 2 Preview)

Crawled web documents will be staged into PostgreSQL using Spring Data JPA.

### Preliminary Schema Conception:
```sql
CREATE TABLE crawled_documents (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL UNIQUE,
    domain VARCHAR(255) NOT NULL,
    title VARCHAR(512),
    raw_content TEXT NOT NULL,
    http_status_code INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    crawled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_crawled_docs_domain ON crawled_documents (domain);
CREATE INDEX idx_crawled_docs_url ON crawled_documents (url);
```

---

## 4. Dependencies Planned for Phase 2

The following dependency will be introduced to `backend/pom.xml` during Phase 2 setup:
```xml
<!-- Jsoup for HTML Parsing & Link Extraction -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.3</version>
</dependency>
```

---

## 5. Prerequisites Checklist Before Phase 2 Implementation

- [x] Phase 1 backend foundation hardened, tested, and fully documented.
- [ ] **Spring Boot 4.1.x Upgrade Branch:** Create branch `feature/spring-boot-4.1-upgrade` from `main` to execute the planned framework upgrade and verify all 6 tests pass cleanly before writing crawler code.
- [ ] **WireMock / Mock HTTP Server Setup:** Configure test harness so crawler unit tests run hermetically against local mock HTTP endpoints without making live network requests during CI builds.
- [ ] **Crawl Boundary Configuration:** Define seed URLs and domain whitelist in `application.yml` (`crawler.seed-urls`, `crawler.allowed-domains`).
