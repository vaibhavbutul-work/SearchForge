# SearchForge: Project Overview & Vision

SearchForge is a custom search engine designed to index and search across technical documentation and web resources. Rather than consuming pre-built search APIs, SearchForge implements information retrieval from foundational computer science principles.

---

## 1. Motivation & Goals

1. **Understand Search Engine Fundamentals:** Learn how search engines actually work by building the crawler, document parser, inverted index, and ranking engines from scratch.
2. **Master Enterprise Java Backend Development:** Utilize Spring Boot 3, Spring Data JPA, HikariCP, PostgreSQL, and RESTful architectures following industry best practices.
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

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | Java 21 | Modern LTS release providing Records, Pattern Matching, and Virtual Threads (Project Loom). |
| **Framework** | Spring Boot 3.4 | Industry-standard dependency injection, autoconfiguration, and REST API support. |
| **Persistence** | Spring Data JPA / Hibernate | High-productivity data access abstraction over relational database tables. |
| **Database** | PostgreSQL 16 | Proven relational database offering ACID compliance, robust indexing, and JSON support. |
| **Connection Pool** | HikariCP | High-performance, low-latency JDBC connection pooling bundled with Spring Boot. |
| **Build Tool** | Apache Maven | Declarative dependency management and standard build lifecycle. |
| **Containerization** | Docker Compose | Reproducible, isolated local environment for development databases. |
