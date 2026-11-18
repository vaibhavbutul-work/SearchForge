# SearchForge: Technology Stack & Standards

This document catalogs every technology, framework, database, and library used in SearchForge Phase 1, along with its version, purpose, and configuration details.

---

## 1. Core Runtime & Language

| Technology | Version | Purpose & Architectural Justification |
| :--- | :--- | :--- |
| **Java** | 21 LTS (Eclipse Temurin) | Long-Term Support release providing Java Records for immutable DTOs, pattern matching, strong memory model, and Virtual Threads (Project Loom) for future high-concurrency web crawling. |
| **Java Standard Library (JDK)** | Java SE 21 | Provides `javax.sql.DataSource` and `java.sql.Connection`. Note that `javax.sql.*` and `java.sql.*` are core JDK SE packages that did **NOT** move to `jakarta.*` during the Jakarta EE namespace migration. |

---

## 2. Application Framework & Infrastructure

| Technology | Version | Purpose & Configuration |
| :--- | :--- | :--- |
| **Spring Boot** | 4.1.2 | Foundational framework providing autoconfiguration, dependency injection, and modular WebMvc starters. Upgraded to 4.1.2 before Phase 2. |
| **Embedded Apache Tomcat** | 10.1.x (via Spring Boot) | Servlet container listening on HTTP port 8080. Handles TCP handshakes, HTTP parsing, and request dispatching to Spring's `DispatcherServlet`. |
| **Spring Web MVC** | 6.2.x (via Spring Boot) | REST controller layer (`@RestController`, `@GetMapping`, `ResponseEntity`) routing `/api/v1/health` and negotiating HTTP 200 vs. HTTP 503 status codes. |
| **Spring Boot Actuator** | 3.4.0 | Exposes standard component health probes (`/actuator/health`), application metadata (`/actuator/info`), and metrics (`/actuator/metrics`). |

---

## 3. Data Transport, Persistence & Connection Pooling

| Technology | Version | Role & Configuration |
| :--- | :--- | :--- |
| **HikariCP** | 5.1.0 (via Spring Boot) | High-performance JDBC connection pool. Configured with `pool-name: SearchForgeHikariPool`, `maximum-pool-size: 10`, `minimum-idle: 2`, `initialization-fail-timeout: 0`, and `connection-timeout: 3000ms`. |
| **Direct JDBC** | JDBC 4.2 / Java SE 21 | In Phase 1, `HealthService` directly invokes `connection.isValid(2)` on `javax.sql.DataSource` to test raw TCP socket connectivity without ORM overhead. |
| **PostgreSQL JDBC Driver** | 42.7.x | Low-level transport driver (`org.postgresql.Driver`) implementing the PostgreSQL wire protocol over TCP port 5432. |
| **Spring Data JPA & Hibernate** | 6.6.x (via Spring Boot) | Present on classpath. In Phase 1, no `@Entity` classes or `JpaRepository` interfaces are instantiated. Planned for Phase 3 (Crawled Document & Postings Storage). |
| **Hibernate 2nd-Level Cache** | Disabled by default | Hibernate second-level cache is **OFF by default** (`HHH000026: Second-level cache disabled`). Enabling it requires configuring a JCache/Ehcache provider and setting `hibernate.cache.use_second_level_cache: true`. |

---

## 4. Database & Containerization

| Technology | Version | Purpose & Configuration |
| :--- | :--- | :--- |
| **PostgreSQL** | 16-Alpine | Relational database providing ACID guarantees, JSONB support, and full-text search indexing (B-Tree, GIN, GiST). Containerized via Docker Compose. |
| **Docker Compose** | Compose v2 | Local container orchestration (`docker-compose.yml`) defining `searchforge-postgres`, named volume `postgres_data`, port forwarding `"${DB_PORT:-5432}:5432"`, and native healthcheck (`pg_isready`). |

---

## 5. Serialization & Logging

| Technology | Version | Purpose & Security Controls |
| :--- | :--- | :--- |
| **Jackson** | 2.18.x (via Spring Boot) | JSON object mapper. Serializes `HealthResponse` record into JSON. Method `isUp()` is annotated with `@JsonIgnore` to prevent leaking internal boolean fields into the API contract. |
| **SLF4J / Logback** | 2.0.x / 1.5.x | Enterprise logging abstraction. Full exception stack traces are logged server-side via `log.error("Database health check failed", e)` while client responses mask all SQL errors. |

---

## 6. Build, Tooling & Environment

| Technology | Version | Role & Enforced Policies |
| :--- | :--- | :--- |
| **Apache Maven Wrapper** | 3.9.9 (`only-script`) | Standalone wrapper (`backend/mvnw`, `backend/mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`). Guarantees reproducible builds without committing `.jar` binaries. Executable bit `100755` tracked in Git. |
| **Git & `.gitattributes`** | Modern Git | Root `.gitattributes` enforces LF line endings for shell scripts (`mvnw`) and CRLF for Windows scripts (`*.cmd`, `*.bat`). |
| **Environment Config** | `.env` + `scripts/load-env.ps1` | Database credentials externalized from source code into git-ignored `.env` with fail-fast `${DB_PASSWORD}` enforcement. Script is dot-sourced (`. .\scripts\load-env.ps1`) to export variables into the caller's terminal session scope. |

---

## 7. Automated Testing Suite

| Technology | Scope | Executed Tests & Execution Time |
| :--- | :--- | :--- |
| **JUnit 5 (Jupiter)** | Test Framework | Underpins all unit, slice, and integration tests. |
| **Mockito 5** | Mocking Engine | Pure unit testing (`HealthServiceTest`) and Spring bean mocking (`@MockitoBean DataSource`). |
| **Spring Boot Test** | Integration & Slice | `@WebMvcTest(HealthController.class)` for controller slice tests; `@SpringBootTest` for hermetic context tests using `backend/src/test/resources/application.yml` (replacing main configuration, proving bean wiring only). |
| **Measured Suite Time** | 6 Automated Tests | **8.388 seconds** ([MEASURED] in `.\mvnw.cmd test`). |
