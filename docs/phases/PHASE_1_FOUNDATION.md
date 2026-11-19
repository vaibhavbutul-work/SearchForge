# SearchForge — Phase 1: Complete Masterclass & Engineering Guide

> **Project:** SearchForge (Build Your Own Search Engine from Scratch)  
> **Phase:** 1 — Project Setup and Backend Foundation  
> **Author:** Vaibhav Kumar  
> **Date:** November 2026  
> **Repository:** [https://github.com/vaibhavbutul-work/SearchForge](https://github.com/vaibhavbutul-work/SearchForge)

---

## Table of Contents
1. [Executive Summary: What Did We Build in Phase 1?](#1-executive-summary)
2. [Chronological Journey: Step-by-Step Implementation](#2-chronological-journey)
3. [Deep-Dive into Every Technology & Software Used](#3-technology-deep-dive)
   - [3.1 Java 21 (LTS) & The Java Virtual Machine](#31-java-21-lts--the-jvm)
   - [3.2 Apache Maven (Build Automation & Dependency Management)](#32-apache-maven)
   - [3.3 Spring Boot 3.4 & The Spring Framework](#33-spring-boot-34)
   - [3.4 Embedded Apache Tomcat Server](#34-embedded-apache-tomcat)
   - [3.5 Spring Data JPA, Hibernate ORM, and JDBC](#35-jpa-hibernate-and-jdbc)
   - [3.6 PostgreSQL 16 (Relational Database Management System)](#36-postgresql-16)
   - [3.7 Docker & Docker Compose (Containerization)](#37-docker--docker-compose)
   - [3.8 HikariCP (High-Performance Connection Pooling)](#38-hikaricp-connection-pool)
   - [3.9 Jackson (JSON Serialization / Deserialization)](#39-jackson-json-processor)
   - [3.10 Git & GitHub (Distributed Version Control)](#310-git--github)
4. [Software Architecture & Design Patterns](#4-software-architecture)
   - [Layered Architecture (Separation of Concerns)](#layered-architecture)
   - [The Complete HTTP Request-Response Lifecycle](#the-http-request-response-lifecycle)
   - [Why We Use Data Transfer Objects (DTOs) Instead of Entities](#dtos-vs-entities)
5. [Codebase Anatomy & Detailed File Explanations](#5-codebase-anatomy)
6. [Troubleshooting Guide: Windows Environment Gotchas](#6-troubleshooting-guide)
7. [Comprehensive Interview Preparation: Questions & Model Answers](#7-interview-preparation)
8. [Corrections After Phase 1 Review](#8-corrections-after-phase-1-review)

---

# 1. Executive Summary

In **Phase 1**, we did not jump straight into building web scrapers or complex indexing data structures. Instead, we followed real-world software engineering best practices by establishing a **production-ready backend foundation**.

### What we accomplished:
1. Replaced legacy **Java 8** with modern **Java 21 LTS (Eclipse Temurin)** and configured system environment variables (`JAVA_HOME`, `PATH`).
2. Installed and configured **Apache Maven 3.10.1** for dependency management and build lifecycles.
3. Created the core project structure based on the **Layered Architecture Pattern** (`controller`, `service`, `dto`).
4. Designed and declared minimal, strictly justified dependencies in `pom.xml` using **Spring Boot 3.4.0**.
5. Containerized **PostgreSQL 16** using **Docker Compose** with persistent disk volumes and native health checks.
6. Configured **HikariCP** database connection pooling with resilient startup policies in `application.yml`.
7. Implemented the first production-grade REST endpoint (`GET /api/v1/health`) using Java 21 **Records**.
8. Wrote automated unit/slice tests using **Spring Boot Test (`@WebMvcTest`)** and **Mockito**.
9. Successfully built, booted, and tested the live server via browser, cURL, and Postman.
10. Initialized Git version control with a comprehensive `.gitignore` and pushed to **GitHub**.

---

# 2. Chronological Journey

### Step 1: Machine & Environment Discovery
We inspected the development environment and discovered:
- Workspace: `D:\projects` (clean, no existing files).
- Java: `1.8.0_51` (Java 8, released in 2014) — incompatible with Spring Boot 3.
- Maven: Missing from system `PATH`.
- Docker: Installed, daemon stopped.
- Git: Configured globally for `Vaibhav Kumar`.

### Step 2: Upgrading to JDK 21 LTS
- Downloaded and installed **Eclipse Temurin JDK 21 (Adoptium)** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot`.
- Updated Windows system environment variables so `JAVA_HOME` permanently targets JDK 21.

### Step 3: Setting up Apache Maven
- Downloaded binary archive `apache-maven-3.10.1-bin.zip` (avoiding source code archives which require compiling).
- Extracted to `C:\apache-maven-3.10.1`.
- Added `C:\apache-maven-3.10.1\bin` to Windows User `PATH`.
- Fixed the Windows cached RAM environment bug by dynamically refreshing process environment variables.

### Step 4: Initializing the Spring Boot Maven Project
- Created `.gitignore` ignoring build artifacts (`target/`), IDE configurations (`.idea/`, `.vscode/`), and environment secrets (`.env`).
- Created `pom.xml` inheriting from `spring-boot-starter-parent:3.4.0` with Java 21 properties *(initially at repository root; updated in the review, see Section 8)*.
- Declared the 5 foundational dependencies.
- Created the main application entry point: `SearchForgeApplication.java`.
- Ran initial compilation check to download dependencies from Maven Central and compile bytecode *(updated in the review, see Section 8)*.

### Step 5: Database Containerization with Docker Compose
- Created `docker-compose.yml` declaring a containerized PostgreSQL 16 Alpine image.
- Set up named volume `postgres_data` so database records survive container restarts.
- Configured native healthcheck (`pg_isready`).

### Step 6: Application Configuration & HikariCP
- Created `application.yml` *(initially at repository root; updated in the review to `backend/src/main/resources/application.yml`, see Section 8)*.
- Configured database credentials using environment variables *(updated in the review to no default password (fail-fast if DB_PASSWORD is missing), see Section 8)*.
- Added `initialization-fail-timeout: 0` to HikariCP so the application can boot even if the database is temporarily starting up *(updated in the review, see Section 8)*.

### Step 7: Implementing the Health REST API
- Created `HealthResponse.java` as an immutable Java 21 `record` *(updated in the review, see Section 8)*.
- Created `HealthService.java` to test live JDBC database connectivity using `connection.isValid(2)` *(updated in the review, see Section 8)*.
- Created `HealthController.java` to expose `GET /api/v1/health` *(updated in the review, see Section 8)*.
- Created `HealthControllerTest.java` verifying HTTP 200 and JSON structure using `MockMvc` *(updated in the review, see Section 8)*.

### Step 8: Live Verification
- Started the database container: `docker compose up -d`.
- Started the Spring Boot application: `mvn spring-boot:run` *(updated in the review, see Section 8)*.
- Verified live response: `{"status":"UP","service":"SearchForge","version":"1.0.0","database":"UP",...}`.

### Step 9: Version Control & GitHub
- Initialized local Git repository on branch `main`.
- Created atomic commits with descriptive commit messages (`feat: ...`, `fix: ...`).
- Added remote origin `https://github.com/vaibhavbutul-work/SearchForge.git` and pushed to GitHub.

---

# 3. Technology Deep-Dive

## 3.1 Java 21 (LTS) & The JVM

### What is Java?
Java is a strongly typed, class-based, object-oriented programming language designed on the philosophy of **"Write Once, Run Anywhere" (WORA)**.

### How Does Java Work Under the Hood?
```
Java Source Code (.java)
         │
         │ javac (Java Compiler)
         ▼
Java Bytecode (.class)   <── Platform Independent!
         │
         ▼
┌────────────────────────────────────────────────────────┐
│               Java Virtual Machine (JVM)               │
│                                                        │
│  [ ClassLoader ] ──> Loads .class files into memory   │
│  [ Bytecode Verifier ] ──> Checks security rules      │
│  [ Execution Engine ]:                                │
│       ├── Interpreter (executes bytecode line by line)│
│       └── JIT Compiler (Just-In-Time compiles hot     │
│           code into native machine instructions)      │
│  [ Garbage Collector (GC) ] ──> Frees unused RAM      │
└────────────────────────────────────────────────────────┘
         │
         ▼
Operating System / CPU (Windows x64 / Linux / macOS)
```

### Key Java Definitions:
- **JDK (Java Development Kit):** The full developer toolkit containing the compiler (`javac`), runtime (`java`), archiver (`jar`), and development tools.
- **JRE (Java Runtime Environment):** A subset of the JDK containing only what is needed to run compiled Java programs (no compiler).
- **JVM (Java Virtual Machine):** The virtual machine that executes bytecode instructions on host hardware.

### Why Did We Upgrade from Java 8 to Java 21?
1. **Spring Boot 3 Baseline:** Spring Boot 3.0+ raised its absolute minimum requirement to **Java 17**. Trying to run Spring Boot 3 on Java 8 throws `UnsupportedClassVersionError: class file version 65.0 (Java 21) unrecognized by class file version 52.0 (Java 8)`.
2. **The Jakarta EE Transition:** In Java 8, web APIs used `javax.*` (`javax.servlet`, `javax.persistence`). Oracle transferred Java EE to the Eclipse Foundation, which renamed the standard namespace to `jakarta.*`. Spring Boot 3 strictly uses `jakarta.*`.
3. **Java 21 Records:** Introduced to eliminate boilerplate in Data Transfer Objects.
4. **Virtual Threads (Project Loom):** Traditional Java threads map 1:1 to OS kernel threads (~1 MB memory per thread, limited to ~2,000 threads). Java 21 introduced virtual threads: lightweight JVM-managed threads consuming only bytes, allowing **100,000+ concurrent threads**. This will be essential when we build concurrent web crawlers in later phases.

---

## 3.2 Apache Maven

### What is a Build Tool?
A build tool automates the creation of executable applications from source code. This includes compiling code, downloading libraries, running tests, and packaging binaries into executable `.jar` files.

### What Problem Did Maven Solve? ("JAR Hell")
Before Maven, developers had to manually download `.jar` files from various websites, paste them into a `lib/` directory, and manually configure classpaths. If Library A required Library B v1.0, but Library C required Library B v2.0, applications would crash with `ClassNotFoundException` or `NoClassDefFoundError`. This was called **"JAR Hell"**.

### How Maven Works:
```
pom.xml (Declarative Specification)
       │
       ▼
Maven Core Engine
       │
       ├── Reads dependencies (groupId, artifactId, version)
       │
       ├── Checks Local Repository: ~/.m2/repository/
       │     └── If missing, downloads from Maven Central Repository
       │
       ├── Builds Directed Acyclic Graph (DAG) of dependencies
       │     └── Automatically resolves Transitive Dependencies
       │
       └── Executes Standard Lifecycle Plugins:
             ├── clean    (deletes target/ folder)
             ├── compile  (invokes javac on backend/src/main/java)
             ├── test     (executes JUnit tests in backend/src/test/java)
             └── package  (bundles compiled code into a .jar)
```

### Understanding `pom.xml`:
- **POM:** Project Object Model.
- **`groupId`:** The unique namespace identifying your organization or project (e.g. `com.searchforge`). Reverse domain name convention.
- **`artifactId`:** The unique name of the specific project or module (e.g. `searchforge`).
- **`version`:** The version of the project (`0.0.1-SNAPSHOT` means a work-in-progress build).
- **`spring-boot-starter-parent`:** A special parent POM that pre-configures plugin versions, compiler versions, UTF-8 encoding, and compatible version numbers for hundreds of third-party libraries.

---

## 3.3 Spring Boot 3.4

### Spring Framework vs. Spring Boot
- **Spring Framework:** A massive enterprise Java framework providing **Inversion of Control (IoC)**, **Dependency Injection (DI)**, and transaction management. In classic Spring, developers spent days writing complex XML files to configure beans, servlets, and database connections.
- **Spring Boot:** An opinionated, convention-over-configuration layer built on top of Spring. It eliminates XML configuration by providing **Starters**, **Auto-Configuration**, and an **Embedded Web Server**.

### Core Spring Concepts:
1. **Inversion of Control (IoC):** Instead of you writing `HealthService service = new HealthService()`, the Spring IoC container creates, configures, and manages the lifecycle of your objects.
2. **Spring Bean:** Any Java object that is instantiated, assembled, and managed by the Spring IoC container.
3. **Dependency Injection (DI):** The pattern where the container passes dependent beans into a class (via constructor) rather than the class creating them itself:
   ```java
   @RestController
   public class HealthController {
       private final HealthService healthService;

       // Spring automatically passes the HealthService bean here!
       public HealthController(HealthService healthService) {
           this.healthService = healthService;
       }
   }
   ```
4. **`@SpringBootApplication` Explained:**
   - `@Configuration`: Marks the class as a configuration class capable of declaring beans.
   - `@EnableAutoConfiguration`: Tells Spring Boot to guess how to configure beans based on the JARs on the classpath. (e.g., finding `postgresql.jar` causes Spring Boot to automatically configure a PostgreSQL `DataSource`).
   - `@ComponentScan`: Automatically scans `com.searchforge` and all sub-packages for classes annotated with `@Component`, `@Service`, `@Repository`, or `@RestController`.

---

## 3.4 Embedded Apache Tomcat

### Standalone Server vs. Embedded Server:
- **Classic Way:** You installed Apache Tomcat on Windows/Linux as a separate operating system service. You compiled your Java project into a `.war` file and manually copied it into `tomcat/webapps/`.
- **Modern Spring Boot Way:** The Apache Tomcat server is embedded inside your application's executable `.jar` file. When `SpringApplication.run()` executes, Spring programmatically starts Tomcat on port `8080`. Your application is completely self-contained!

---

## 3.5 JPA, Hibernate, and JDBC

Understanding this relationship is one of the most common technical interview topics:

```
┌──────────────────────────────────────────────────────────┐
│                        Your Code                         │
│  - Phase 1: HealthService calls DataSource directly      │
│  - Planned (Phase 3): Repositories call Spring Data      │
└────────────────────┬─────────────────────────┬───────────┘
                     │                         │
                     │ (Planned: Repositories) │ (Phase 1: Direct JDBC)
                     ▼                         │
┌──────────────────────────────────────────┐   │
│      Spring Data JPA (Abstraction)       │   │
│  - JpaRepository interface               │   │
│  - Generates query logic automatically   │   │
└────────────────────┬─────────────────────┘   │
                     │ Uses specification      │
                     ▼                         │
┌──────────────────────────────────────────┐   │
│   JPA: Jakarta Persistence API (Spec)    │   │
│  - Interfaces (@Entity, EntityManager)   │   │
│  - ZERO implementation code              │   │
└────────────────────┬─────────────────────┘   │
                     │ Implemented by          │
                     ▼                         │
┌──────────────────────────────────────────┐   │
│      Hibernate (ORM Engine / Impl)       │   │
│  - Object-Relational Mapping             │   │
│  - Translates objects to SQL             │   │
│  - 1st-level cache: ON by default        │   │
│  - 2nd-level cache: OFF by default       │   │
└────────────────────┬─────────────────────┘   │
                     │ Executes SQL via        │
                     ▼                         ▼
┌────────────────────┴─────────────────────────┴───────────┐
│            JDBC Driver (Low-Level Transport)             │
│  - org.postgresql.Driver & HikariCP DataSource           │
│  - Opens TCP socket, handles database authentication,    │
│    and transmits raw SQL / validation pings              │
└────────────────────────────┬─────────────────────────────┘
                             │ Network TCP Socket (Port 5432)
                             ▼
┌────────────────────────────┴─────────────────────────────┐
│                   PostgreSQL 16 Server                   │
└──────────────────────────────────────────────────────────┘
```

- **JDBC:** The raw, low-level database communication protocol. In Phase 1, `HealthService` communicates **directly with JDBC** via `javax.sql.DataSource` and `connection.isValid(2)` to verify database socket reachability with zero ORM overhead.
  - *Package Namespace Note:* `javax.sql.DataSource` is part of the JDK and did **NOT** move to `jakarta.*`. Only Java EE packages (servlet, persistence) did.
- **JPA:** The official Java/Jakarta standard specification (interfaces only, e.g., `jakarta.persistence.*`).
- **Hibernate:** The industry-standard ORM framework that implements JPA. It automatically writes SQL dialect queries on your behalf.
  - **1st-Level Cache (Session):** Enabled by default in Hibernate; caches entities within a single transaction/session.
  - **2nd-Level Cache (SessionFactory):** **Disabled by default** in Hibernate (confirmed by our server boot log: `HHH000026: Second-level cache disabled`). It requires an explicit caching provider (e.g., a JCache/Ehcache provider) and configuring `hibernate.cache.use_second_level_cache: true`.
- **Spring Data JPA:** An additional convenience layer that lets you define an interface like `interface DocumentRepository extends JpaRepository<Document, Long>` and automatically generates the complete CRUD implementation at runtime. (Planned for Phase 3: Crawled Document Storage).

---

## 3.6 PostgreSQL 16

### What is PostgreSQL?
PostgreSQL is an open-source, enterprise-grade Object-Relational Database Management System (RDBMS).

### Why PostgreSQL for SearchForge?
1. **ACID Transactions:** Atomicity, Consistency, Isolation, Durability. Ensures document indexing and database updates never corrupt the index if a crash occurs.
2. **Advanced Indexing:** PostgreSQL natively supports **B-Tree**, **Hash**, and crucially **GIN (Generalized Inverted Index)** and **GiST** indexes. GIN indexes are designed specifically for document search and array lookups.
3. **JSONB Support:** Allows storing semi-structured metadata alongside relational tables.
4. **Full-Text Search (FTS):** PostgreSQL includes built-in text search primitives (`tsvector`, `tsquery`) which will serve as an excellent benchmark against our custom inverted index in later phases.

---

## 3.7 Docker & Docker Compose

### Virtual Machine vs. Container:
- **Virtual Machine (VM):** Bundles a full guest operating system (Windows/Linux), virtualized hardware, and the application. Heavyweight (takes gigabytes of RAM and minutes to boot).
- **Docker Container:** An isolated user-space process running on the host OS kernel. Shares the host kernel, boots in seconds, and consumes minimal RAM.

### Key Docker Concepts:
- **Docker Image:** A read-only template with instructions for creating a Docker container (e.g. `postgres:16-alpine`).
- **Docker Container:** A runnable, isolated instance of a Docker image.
- **Docker Compose:** A tool for defining and running multi-container Docker applications using a single `docker-compose.yml` file.

### How Our `docker-compose.yml` Works:
```yaml
services:
  postgres:
    image: postgres:16-alpine      # Uses ultra-lightweight Alpine Linux (~40MB)
    container_name: searchforge-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME:-searchforge_db}
      POSTGRES_USER: ${DB_USERNAME:-searchforge}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
    ports:
      - "${DB_PORT:-5432}:5432"     # Forwards host port -> container port 5432
    volumes:
      - postgres_data:/var/lib/postgresql/data  # Data persists even if container is deleted!
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-searchforge} -d ${DB_NAME:-searchforge_db}"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
    driver: local
```

---

## 3.8 HikariCP Connection Pool

### Why Do We Need a Connection Pool?
Establishing a database connection over a network requires:
1. TCP 3-way handshake (`SYN`, `SYN-ACK`, `ACK`).
2. TLS/SSL certificate exchange and encryption handshake.
3. Database authentication and process allocation on PostgreSQL.

This handshake takes **30ms to 100ms per connection**. If our search engine had to create a new connection for every search query, throughput would drop dramatically.

### How HikariCP Solves This:
HikariCP creates a pool of pre-established, authenticated database connections on application startup.
- When an API request needs to query the database, it **borrows** an existing connection from the pool in **under 1 millisecond**.
- After executing the query, the connection is **returned to the pool** for the next request.
- In `application.yml`, we configured:
  ```yaml
  hikari:
    pool-name: SearchForgeHikariPool
    maximum-pool-size: 10          # Max active database connections
    minimum-idle: 2               # Always keep 2 connections ready in RAM
    initialization-fail-timeout: 0 # Resilient: boot even if DB is unreachable
    connection-timeout: 3000      # 3s fast-fail socket timeout
  ```

---

## 3.9 Jackson (JSON Processor)

When our controller returns `ResponseEntity.ok(response)`, Java does not speak JSON natively.
- **Jackson:** The high-performance JSON library included with `spring-boot-starter-web`.
- **Serialization:** Converts Java objects (such as our `HealthResponse` record) into JSON strings:
  `HealthResponse(status="UP")` ➔ `{"status": "UP"}`
- **Deserialization:** Converts incoming JSON request bodies into Java objects.

---

## 3.10 Git & GitHub

### What is Git?
Git is a distributed version control system that tracks changes in source code over time.
- **Working Tree:** The physical files on your hard drive (`D:\projects`).
- **Staging Area (Index):** The preview area where changes are prepared before committing (`git add`).
- **Local Repository (`.git`):** The local database storing all committed snapshots and branch history.
- **Remote Repository (GitHub):** The cloud-hosted copy of the Git database allowing sharing and collaboration (`origin`).

---

# 4. Software Architecture & Design Patterns

## Layered Architecture (Separation of Concerns)

SearchForge strictly separates concerns into independent layers:

```
┌────────────────────────────────────────────────────────────┐
│                     Presentation Layer                     │
│    com.searchforge.controller (HealthController.java)      │
│  - Routes HTTP requests (/api/v1/health)                   │
│  - Maps response to HTTP 200 (UP) or 503 (DOWN)            │
│  - Thin: Zero business calculations or raw DB queries      │
└─────────────────────────────┬──────────────────────────────┘
                              │ Calls Service
                              ▼
┌────────────────────────────────────────────────────────────┐
│                       Service Layer                        │
│       com.searchforge.service (HealthService.java)         │
│  - Business logic & health check orchestration             │
│  - Phase 1: Borrows Connection directly from DataSource    │
│  - Planned (Phase 3): Injects Document Repositories        │
└──────────────┬─────────────────────────────┬───────────────┘
               │                             │
               │ (Phase 1: Direct JDBC)      │ (Planned: Phase 3)
               ▼                             ▼
┌──────────────────────────────┐ ┌───────────────────────────┐
│  HikariCP DataSource / JDBC  │ │     Persistence Layer     │
│  - Manages Connection Pool   │ │  - Spring Data Repos      │
│  - connection.isValid(2)     │ │  - Hibernate Entities     │
└──────────────┬───────────────┘ └───────────────────────────┘
               │ JDBC TCP Socket (Port 5432)
               ▼
┌────────────────────────────────────────────────────────────┐
│                    PostgreSQL 16 Server                    │
│               searchforge-postgres Container               │
└────────────────────────────────────────────────────────────┘
```

---

## The HTTP Request-Response Lifecycle

Here is the exact step-by-step path of a health check request:

```
1. Client (Browser / Postman / cURL) issues HTTP GET to http://localhost:8080/api/v1/health
   │
2. OS routes TCP packet to Port 8080
   │
3. Apache Tomcat receives raw HTTP bytes and creates an HttpServletRequest object
   │
4. DispatcherServlet (Spring's Front Controller) receives the request
   │
5. HandlerMapping inspects URL path "/api/v1/health" and locates HealthController.getHealth()
   │
6. HealthController delegates execution to HealthService.checkHealth()
   │
7. HealthService borrows a connection from HikariCP pool and calls connection.isValid(2)
   │
8. Database Connectivity Outcome:
   ├── Branch A (Database is UP):
   │     - Socket ping succeeds; connection is returned to pool via try-with-resources
   │     - HealthService creates HealthResponse record with status: "UP", database: "UP"
   │     - HealthController inspects response.isUp() (true) -> returns ResponseEntity.ok(response)
   │     - Jackson serializes record into JSON (ignoring boolean isUp() via @JsonIgnore)
   │     - Client receives HTTP 200 OK + {"status":"UP","database":"UP",...}
   │
   └── Branch B (Database is DOWN / Unreachable):
         - HikariCP times out or throws SQLException; connection is closed safely
         - HealthService logs full stack trace via SLF4J (log.error("...", e))
         - HealthService masks all errors, returning safe status: "DOWN", database: "DOWN"
         - HealthController inspects response.isUp() (false) -> returns ResponseEntity.status(503).body(response)
         - Jackson serializes record into JSON (zero exception text leaked)
         - Client receives HTTP 503 Service Unavailable + {"status":"DOWN","database":"DOWN",...}
```

---

## DTOs vs. Entities

In our project, we introduced `HealthResponse` as a **Data Transfer Object (DTO)** using a Java 21 `record`.

### Why You Should Never Return Database Entities in REST APIs:
1. **Security:** An `@Entity` represents a physical database table and often contains sensitive fields (passwords, internal IDs, audit timestamps). Returning it directly leaks internal schema details.
2. **Decoupling (API Stability):** If you rename a database column from `first_name` to `given_name`, your frontend APIs will break immediately if you return entities. DTOs form an independent, stable contract.
3. **Circular Reference Infinite Loops:** If Entity A references Entity B, and Entity B references Entity A, Jackson will enter an infinite serialization loop throwing `StackOverflowError`.
4. **Performance:** DTOs allow returning only the specific fields required for a given view, saving network bandwidth.

---

# 5. Codebase Anatomy

### 1. `pom.xml`
- **Location:** `backend/pom.xml`
- **Role:** Declares dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-actuator`, and `spring-boot-starter-test`. Targets Java 21 and Spring Boot 3.4.0.

### 2. `SearchForgeApplication.java`
- **Location:** `backend/src/main/java/com/searchforge/SearchForgeApplication.java`
- **Role:** Application bootstrapper. Annotated with `@SpringBootApplication`.

### 3. `HealthResponse.java`
- **Location:** `backend/src/main/java/com/searchforge/dto/HealthResponse.java`
- **Code:**
  ```java
  public record HealthResponse(
      String status,
      String service,
      String version,
      String database,
      Instant timestamp
  ) {
      public static final String STATUS_UP = "UP";
      public static final String STATUS_DOWN = "DOWN";

      @JsonIgnore
      public boolean isUp() {
          return STATUS_UP.equals(status);
      }
  }
  ```
- **Why a Record?** In Java 21, a `record` provides immutable data carriers with auto-generated constructors, getters, `equals()`, `hashCode()`, and `toString()`. Annotating `isUp()` with `@JsonIgnore` ensures Jackson does not serialize an unintended `"up": true/false` field into the JSON contract.

### 4. `HealthService.java`
- **Location:** `backend/src/main/java/com/searchforge/service/HealthService.java`
- **Role:** Verifies database connectivity directly over JDBC:
  ```java
  private String verifyDatabaseConnection() {
      try (Connection connection = dataSource.getConnection()) {
          if (connection.isValid(2)) {
              return HealthResponse.STATUS_UP;
          }
          log.warn("Database connection is invalid or socket validation timed out");
          return HealthResponse.STATUS_DOWN;
      } catch (Exception e) {
          // SLF4J logs full stack trace server-side; response masks all details
          log.error("Database health check failed", e);
          return HealthResponse.STATUS_DOWN;
      }
  }
  ```
  *(Uses `try-with-resources` to guarantee that the database connection is immediately closed and returned to the HikariCP pool).*

### 5. `HealthController.java`
- **Location:** `backend/src/main/java/com/searchforge/controller/HealthController.java`
- **Role:** Exposes `GET /api/v1/health`. Maps `response.isUp()` to **HTTP 200 OK** and downstream failure to **HTTP 503 Service Unavailable**.

### 6. `HealthControllerTest.java`
- **Location:** `backend/src/test/java/com/searchforge/HealthControllerTest.java`
- **Role:** Web slice test using `@WebMvcTest(HealthController.class)`. Mocks `HealthService` to verify that `UP` maps to HTTP 200, `DOWN` maps to HTTP 503, and `$.up` does not exist in JSON.

### 7. `HealthServiceTest.java`
- **Location:** `backend/src/test/java/com/searchforge/service/HealthServiceTest.java`
- **Role:** Pure Mockito unit test (no Spring context). Tests `isValid(true)`, `isValid(false)`, verifies error masking when `getConnection()` throws `SQLException` (asserting zero sensitive text or class names in `response.toString()`), and verifies `verify(connection).close()` is called in all branches.

### 8. `SearchForgeApplicationTests.java`
- **Location:** `backend/src/test/java/com/searchforge/SearchForgeApplicationTests.java`
- **Role:** Hermetic context test using `@SpringBootTest` and `@MockitoBean DataSource`. Configured with `hibernate.boot.allow_jdbc_metadata_access: false` in `backend/src/test/resources/application.yml` (which replaces, rather than merges with, the main `application.yml` during test execution) to prove complete Spring bean wiring without requiring an active PostgreSQL instance (proving bean wiring only, not database connectivity).

### 9. Maven Wrapper (`mvnw`, `mvnw.cmd`)
- **Location:** `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- **Role:** Script-only (`only-script`) Maven 3.9.9 wrapper. Guarantees reproducible builds across developer machines and CI pipelines with zero binary `.jar` files in Git. Executable bit `100755` tracked in Git for Unix compatibility.

---

# 6. Troubleshooting Guide (Windows Environment Gotchas)

### Gotcha 1: `The system cannot find the file specified` in Background Runner
- **Cause:** Antigravity was configured to run PowerShell 7 (`pwsh.exe`), but Windows only had standard PowerShell 5.1 (`powershell.exe`).
- **Fix:** Installed PowerShell 7 (`winget install Microsoft.PowerShell`).

### Gotcha 2: Stale Environment Variables in Open Terminals
- **Cause:** When you set `JAVA_HOME` or edit `PATH`, Windows updates the **Registry**. However, processes that are already running (like an IDE or existing terminal) keep the old environment cached in their own RAM.
- **Fix:** In PowerShell, dynamically refresh the session's in-memory PATH:
  ```powershell
  $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
  ```

### Gotcha 3: Terminal Appears "Frozen" After `.\mvnw.cmd spring-boot:run`
- **Cause:** Unlike normal CLI programs, a Spring Boot backend is a **long-running daemon process**. Tomcat holds open port 8080 and blocks the terminal while listening for incoming network traffic.
- **Fix:** This is normal behavior! Leave the terminal running and send requests from a browser or Postman.

---

# 7. Comprehensive Interview Preparation

Master these answers for your software engineering interviews:

### Q1: What is the difference between JPA, Hibernate, and JDBC? Which one does our code interact with?
> **Answer:**  
> - **JDBC** is the low-level Java API that directly opens network sockets and sends raw SQL strings to the database.  
> - **JPA (Jakarta Persistence API)** is an official Java **specification** containing interfaces and annotations (like `@Entity` and `EntityManager`), but no actual implementation code.  
> - **Hibernate** is the **ORM engine** that implements the JPA specification. It translates Java object operations into dialect-specific SQL.  
> - In Phase 1, our code interacts **directly with plain JDBC** via `javax.sql.DataSource` and `connection.isValid(2)`. We do not use Spring Data JPA or Hibernate yet (there are zero entities and zero repositories). While `spring-boot-starter-data-jpa` is present on the classpath for future document storage (Phase 3), Phase 1 health probing is deliberately written with raw JDBC to avoid ORM overhead and verify database reachability directly.

### Q2: Why did we use a Java 21 `record` for our DTO instead of a standard class or returning a database Entity?
> **Answer:**  
> We used a Java 21 `record` because DTOs should be immutable data carriers. Records automatically provide constructors, getters, `equals()`, `hashCode()`, and `toString()` without boilerplate.  
> We never return database Entities directly because:
> 1. Entities expose internal database table structures and sensitive columns.
> 2. Changes to the database schema would break public API contracts.
> 3. Circular relationships between entities cause infinite recursion errors during JSON serialization.

### Q3: Why is a connection pool like HikariCP necessary instead of opening a new database connection whenever an HTTP request arrives?
> **Answer:**  
> Establishing a physical database connection over TCP requires a 3-way handshake, TLS exchange, and database process authentication, taking **30ms to 100ms** per connection.  
> HikariCP maintains a pool of pre-established, authenticated connections in memory. Requests borrow a connection in **under 1 millisecond**, execute their query, and return it to the pool, allowing the server to handle high concurrency without overwhelming the database.

### Q4: What does the `@SpringBootApplication` annotation combine, and what is the role of each?
> **Answer:**  
> It is a convenience annotation combining three core annotations:
> 1. **`@Configuration`**: Tags the class as a source of bean definitions for the Spring ApplicationContext.
> 2. **`@EnableAutoConfiguration`**: Instructs Spring Boot to automatically configure beans based on the JAR libraries found on the classpath.
> 3. **`@ComponentScan`**: Tells Spring to scan the base package and all child packages to automatically discover and register beans annotated with `@Component`, `@Service`, `@Repository`, and `@RestController`.

### Q5: Walk me through the lifecycle of an HTTP request in Spring Boot.
> **Answer:**  
> 1. The client sends an HTTP GET request to port 8080.
> 2. Embedded **Apache Tomcat** accepts the TCP socket and constructs an `HttpServletRequest`.
> 3. Tomcat delegates to Spring's **`DispatcherServlet`** (Front Controller).
> 4. `DispatcherServlet` queries `HandlerMapping` to find the matching controller method (`HealthController.getHealth()`).
> 5. The controller delegates business logic to `HealthService`, which borrows a connection from the **HikariCP pool** to verify database health (`connection.isValid(2)`).
> 6. Outcome handling:
>    - **If UP:** The controller returns `ResponseEntity.ok(response)`. Jackson serializes the record into JSON (omitting `isUp()` via `@JsonIgnore`). Tomcat writes the HTTP 200 OK response with `Content-Type: application/json`.
>    - **If DOWN:** `HealthService` logs the stack trace via SLF4J, masks all error details into safe `"DOWN"` statuses, and the controller returns `ResponseEntity.status(503).body(response)`. Tomcat writes the HTTP 503 Service Unavailable response.

---

# 8. Corrections After Phase 1 Review

Following an in-depth architecture and reliability review of Phase 1, several targeted hardening improvements were implemented:

### 1. Repository Layout Restructuring
- **Change:** Relocated Java backend source code, tests, resources, and build configuration from project root into a dedicated `backend/` directory (`backend/src/...` and `backend/pom.xml`).
- **Why:** Separates backend Java application code from repository-level files, Docker orchestration, documentation, and future multi-module expansion.

### 2. Credential Externalization & Security Hardening
- **Change:** Removed hardcoded database credentials from `application.yml` and `docker-compose.yml`. Credentials now resolve dynamically via environment variables (`DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`).
- **Why:** Prevents credentials from leaking into Git. Provided `.env.example` as a template, added a PowerShell helper (`scripts/load-env.ps1`), and configured fail-fast `${DB_PASSWORD}` without default passwords. Confirmed `.env` is git-ignored and never tracked.

### 3. Health Endpoint Hardening & Information Leakage Prevention
- **Change:** Updated `GET /api/v1/health` to return **HTTP 200 OK** when database is `UP` and **HTTP 503 Service Unavailable** when database is `DOWN` (replacing unconditional HTTP 200 OK).
- **Why:** HTTP 503 accurately signals an upstream service/dependency outage rather than an unhandled application bug. Masked all exception text (returning safe status `"DOWN"` instead of raw SQL error strings). Added `@JsonIgnore` to `HealthResponse.isUp()` to avoid leaking `"up": false` in JSON. Configured SLF4J (`log.error("Database health check failed", e)`) to log full stack traces on the server side without polluting client responses.

### 4. HikariCP Resilient Startup & Fast-Fail Timeout
- **Change:** Configured `spring.datasource.hikari.initialization-fail-timeout: 0` and reduced `spring.datasource.hikari.connection-timeout` from 30,000 ms to 3,000 ms in `backend/src/main/resources/application.yml`.
- **Why:** 
  - `initialization-fail-timeout: 0`: HikariCP still attempts to obtain and validate an initial connection; if the database is unreachable, the pool starts anyway (preventing immediate JVM crash on boot). If a connection is obtained but validation fails, startup fails.
  - Startup duration: When PostgreSQL is down, Hibernate's `JdbcEnvironmentInitiator` requests JDBC metadata during startup, which blocks the main thread waiting for Hikari's connection timeout.
  - Measured Impact:
    - With default 30s timeout: startup froze for **30,019 ms** ([MEASURED], total boot time **33.463 s**).
    - With 3s timeout: startup freeze dropped to **3,016 ms** ([MEASURED], total boot time **6.748 s**).
    - Live health endpoint call with database down responds in **3.127 s** ([MEASURED]) with HTTP 503 instead of hanging the client for 30 seconds.

### 5. Multi-Tier Automated Testing
- **Change:** Expanded test coverage to **6 automated tests** ([MEASURED] in `.\mvnw.cmd test` executing in **8.388 s**):
  - `HealthControllerTest` (2 tests, `@WebMvcTest`): Verifies HTTP 200 mapping for UP, HTTP 503 mapping for DOWN, and asserts `$.up` does not exist in JSON.
  - `HealthServiceTest` (3 tests, pure Mockito unit tests): Verifies `isValid(true)`, `isValid(false)`, exception handling with error masking, and proves connection return via `verify(connection).close()`.
  - `SearchForgeApplicationTests` (1 test, `@SpringBootTest`): Verifies complete Spring Boot bean wiring and context bootstrap using `@MockitoBean DataSource` and `hibernate.boot.allow_jdbc_metadata_access: false` in `backend/src/test/resources/application.yml`. Note that `backend/src/test/resources/application.yml` replaces (does not merge with) the main `application.yml` during test execution, and this test proves application context and bean wiring only, not live database connectivity.

### 6. Apache Maven Wrapper (`only-script`) Pinned to 3.9.9
- **Change:** Generated the Maven 3.9.9 wrapper using `mvn wrapper:wrapper "-Dmaven=3.9.9" "-Dtype=only-script"`.
- **Why:** Guarantees deterministic, reproducible builds across developer environments and CI/CD pipelines without requiring pre-installed Maven on the host system. The `only-script` distribution stores zero binary `.jar` files in Git. Set executable bit `100755` on `backend/mvnw` in the Git index, and created root `.gitattributes` to enforce LF endings for `mvnw` and CRLF for `*.cmd`.

### 7. Spring Boot Version Audit & Lifecycle Status
- **Change:** Audited the official support lifecycle at `spring.io/projects/spring-boot#support`.
- **Why:** Spring Boot 3.4 reached OSS End of Life on December 31, 2025. Per project decision, SearchForge remains on Spring Boot 3.4.0 for Phase 1 as an accepted limitation; **Planned: upgrade to Spring Boot 4.1.x** on a separate branch before Phase 2.
