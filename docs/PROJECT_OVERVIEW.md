# SearchForge: Project Overview & Vision

SearchForge is a custom search engine designed to index and search across technical documentation and web resources. Rather than consuming pre-built search APIs, SearchForge implements information retrieval from foundational computer science principles.

---

## 1. Motivation & Goals

1. **Understand Search Engine Fundamentals:** Learn how search engines actually work by building the crawler, document parser, inverted index, and ranking engines from scratch.
2. **Master Enterprise Java Backend Development:** Utilize Spring Boot, Spring Data JPA, HikariCP, PostgreSQL, and RESTful architectures following industry best practices.
3. **Dual Pipeline Decoupling:** Understand that crawling/indexing runs asynchronously and independently from user search queries.
4. **Portfolio & Interview Preparation:** Provide deep technical justification for architectural and design decisions.

---

## 2. High-Level Architecture

The system consists of two decoupled pipelines:

### A. The Indexing Pipeline (Asynchronous Background Job)
```
Target Websites
       │
       ▼
 [ Web Crawler ] (Jsoup, BFS traversal, Politeness delays)
       │
       ▼
 [ Document Parser ] (Extract Title, Body, Metadata, Clean HTML)
       │
       ▼
 [ Text Preprocessor ] (Tokenization, Lowercasing, Stop-Word Filtering, Stemming)
       │
       ▼
 [ Inverted Index ] (Term -> Document Postings List, Frequency counts)
       │
       ▼
 [ PostgreSQL Storage ] (Indexed documents and posting records)
```

### B. The Search Pipeline (Synchronous User Query)
```
User Query ("dependency injection")
       │
       ▼
 [ Spring Boot REST API ] (@RestController)
       │
       ▼
 [ Query Preprocessor ] (Normalize, Tokenize, Remove Stop Words)
       │
       ▼
 [ Inverted Index Lookup ] (Retrieve candidate document lists)
       │
       ▼
 [ Ranking Engine ] (TF-IDF / BM25 Scoring & Relevance Sorting)
       │
       ▼
 [ JSON Search Results ] (Title, URL, Snippet, Relevance Score)
```

---

## 3. Technology Stack Rationale

| Component | Technology | Rationale & Phase Status |
| :--- | :--- | :--- |
| **Language** | Java 21 LTS | Modern LTS release providing Records, Pattern Matching, and Virtual Threads (Project Loom). |
| **Framework** | Spring Boot 3.4.0 | Foundation framework. Current version is 3.4.0 (*Planned: upgrade to Spring Boot 4.1.x on a separate branch before Phase 2*). |
| **Persistence** | Direct JDBC (Phase 1) / Spring Data JPA & Hibernate (Phase 3) | In Phase 1, `HealthService` uses raw JDBC via `javax.sql.DataSource` and `connection.isValid(2)` for zero-overhead socket verification. Spring Data JPA / Hibernate is on classpath for future document storage (Phase 3). |
| **Database** | PostgreSQL 16 | Relational database offering ACID compliance, robust indexing (B-Tree, GIN, GiST), and JSONB support. |
| **Connection Pool** | HikariCP | High-performance, low-latency JDBC connection pool configured with 3000ms timeout and resilient boot policy. |
| **Build Tool** | Apache Maven 3.9.9 Wrapper | Script-only (`only-script`) Maven Wrapper (`.\mvnw.cmd` / `./mvnw`) guaranteeing reproducible builds without installing Maven locally. |
| **Containerization** | Docker Compose | Isolated local PostgreSQL 16 container (`docker-compose.yml`) with persistent volume and healthcheck. |
| **Repository Layout** | `backend/` Subdirectory | Application source and build configuration isolated in `backend/` to keep root clean for Docker, scripts, and documentation. |
