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
- Created `pom.xml` inheriting from `spring-boot-starter-parent:3.4.0` with Java 21 properties.
- Declared the 5 foundational dependencies.
- Created the main application entry point: `SearchForgeApplication.java`.
- Ran `mvn clean compile` in `D:\projects` to download dependencies from Maven Central and compile bytecode.

### Step 5: Database Containerization with Docker Compose
- Created `docker-compose.yml` declaring a containerized PostgreSQL 16 Alpine image.
- Set up named volume `postgres_data` so database records survive container restarts.
- Configured native healthcheck (`pg_isready`).

### Step 6: Application Configuration & HikariCP
- Created `src/main/resources/application.yml`.
- Configured database credentials using environment variables with safe development defaults (`${DB_URL:...}`).
- Added `initialization-fail-timeout: 0` to HikariCP so the application can boot even if the database is temporarily starting up.

### Step 7: Implementing the Health REST API
- Created `HealthResponse.java` as an immutable Java 21 `record`.
- Created `HealthService.java` to test live JDBC database connectivity using `connection.isValid(2)`.
- Created `HealthController.java` to expose `GET /api/v1/health`.
- Created `HealthControllerTest.java` verifying HTTP 200 and JSON structure using `MockMvc`.

### Step 8: Live Verification
- Started the database container: `docker compose up -d`.
- Started the Spring Boot application: `mvn spring-boot:run`.
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
             ├── compile  (invokes javac on src/main/java)
             ├── test     (executes JUnit tests in src/test/java)
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
┌────────────────────────────────────────────────────────┐
│                      Your Code                         │
│             (HealthService / Repositories)             │
└───────────────────────────┬────────────────────────────┘
                            │ Calls methods
                            ▼
┌────────────────────────────────────────────────────────┐
│            Spring Data JPA (Abstraction)               │
│  - JpaRepository interface                             │
│  - Generates query logic automatically                 │
└───────────────────────────┬────────────────────────────┘
                            │ Uses specification
                            ▼
┌────────────────────────────────────────────────────────┐
│        JPA: Jakarta Persistence API (Specification)    │
│  - Collection of interfaces (@Entity, EntityManager)   │
│  - Contains ZERO implementation code                   │
└───────────────────────────┬────────────────────────────┘
                            │ Implemented by
                            ▼
┌────────────────────────────────────────────────────────┐
│             Hibernate (ORM Engine / Implementation)    │
│  - Object-Relational Mapping                           │
│  - Translates Java objects into SQL statements         │
│  - Manages 1st/2nd level cache & dirty checking        │
└───────────────────────────┬────────────────────────────┘
                            │ Executes SQL via
                            ▼
┌────────────────────────────────────────────────────────┐
│                JDBC Driver (Low-Level Transport)       │
│  - org.postgresql.Driver                               │
│  - Opens TCP socket, handles database authentication,  │
│    and transmits raw SQL strings across the network    │
└───────────────────────────┬────────────────────────────┘
                            │ Network TCP Socket (Port 5432)
                            ▼
┌────────────────────────────────────────────────────────┐
│                 PostgreSQL 16 Server                   │
└────────────────────────────────────────────────────────┘
```

- **JDBC:** The raw, low-level database communication protocol. Writing raw JDBC requires manual string queries, opening connections, and mapping `ResultSet` rows.
- **JPA:** The official Java/Jakarta standard specification (interfaces only).
- **Hibernate:** The industry-standard ORM (Object-Relational Mapping) framework that implements JPA. It automatically writes SQL dialect queries on your behalf.
- **Spring Data JPA:** An additional convenience layer that lets you define an interface like `interface UserRepository extends JpaRepository<User, Long>` and automatically generates the complete CRUD implementation at runtime.

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
    environment:
      POSTGRES_DB: searchforge_db
      POSTGRES_USER: searchforge
      POSTGRES_PASSWORD: searchforge_password
    ports:
      - "5432:5432"                 # Forwards host port 5432 -> container port 5432
    volumes:
      - postgres_data:/var/lib/postgresql/data  # Data persists even if container is deleted!
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U searchforge -d searchforge_db"]
```

---

## 3.8 HikariCP Connection Pool

### Why Do We Need a Connection Pool?
Establishing a database connection over a network requires:
1. TCP 3-way handshake (`SYN`, `SYN-ACK`, `ACK`).
2. TLS/SSL certificate exchange and encryption handshake.
3. Database authentication and process allocation on PostgreSQL.

This handshake takes **30ms to 100ms per connection**. If our search engine had to create a new connection for every search query, throughput would be severely degraded.

### How HikariCP Solves This:
HikariCP creates a pool of pre-established, authenticated database connections on application startup.
- When an API request needs to query the database, it **borrows** an existing connection from the pool in **under 1 millisecond**.
- After executing the query, the connection is **returned to the pool** for the next request.
- In `application.yml`, we configured:
  ```yaml
  hikari:
    maximum-pool-size: 10          # Max active database connections
    minimum-idle: 2               # Always keep 2 connections ready in RAM
    initialization-fail-timeout: 0 # Resilient: boot even if DB is still starting
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
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│  com.searchforge.controller (HealthController.java)    │
│  - Routes HTTP requests (/api/v1/health)               │
│  - Validates query params and headers                  │
│  - Thin: Zero business calculations or DB queries      │
└───────────────────────────┬────────────────────────────┘
                            │ Passes DTO
                            ▼
┌────────────────────────────────────────────────────────┐
│                     Service Layer                      │
│     com.searchforge.service (HealthService.java)       │
│  - The "brain": Business logic, ranking, parsing       │
│  - Manages transactional boundaries (@Transactional)   │
│  - Interacts with DataSource and Repositories          │
└───────────────────────────┬────────────────────────────┘
                            │ Borrows Connection
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Persistence Layer                    │
│      com.searchforge.repository / entity (Future)      │
│  - Spring Data JPA repositories & Entities             │
│  - Interacts with HikariCP Connection Pool             │
└────────────────────────────────────────────────────────┘
```

---

## The HTTP Request-Response Lifecycle

Here is the exact step-by-step path of a request:

```
1. Client (Browser / Postman) issues HTTP GET to http://localhost:8080/api/v1/health
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
8. PostgreSQL replies "OK" over TCP socket; connection is returned to pool
   │
9. HealthService constructs an immutable HealthResponse record
   │
10. HealthController wraps the record in ResponseEntity.ok(record)
   │
11. Jackson (HttpMessageConverter) serializes the HealthResponse record into JSON bytes
   │
12. Tomcat constructs HTTP 200 OK response with Content-Type: application/json
   │
13. Browser receives packet and displays the formatted JSON on screen!
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
- **Location:** `D:\projects\pom.xml`
- **Role:** Declares dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-actuator`, and `spring-boot-starter-test`.

### 2. `SearchForgeApplication.java`
- **Location:** `D:\projects\src\main\java\com\searchforge\SearchForgeApplication.java`
- **Role:** Application bootstrapper. Annotated with `@SpringBootApplication`.

### 3. `HealthResponse.java`
- **Location:** `D:\projects\src\main\java\com\searchforge\dto\HealthResponse.java`
- **Code:**
  ```java
  public record HealthResponse(
      String status,
      String service,
      String version,
      String database,
      Instant timestamp
  ) {}
  ```
- **Why a Record?** In Java 8, creating an immutable class with 5 fields required ~60 lines of code (private final fields, constructor, getters, `equals()`, `hashCode()`, and `toString()`). In Java 21, a `record` generates all of this automatically in 7 lines.

### 4. `HealthService.java`
- **Location:** `D:\projects\src\main\java\com\searchforge\service\HealthService.java`
- **Role:** Verifies database health:
  ```java
  private String verifyDatabaseConnection() {
      try (Connection connection = dataSource.getConnection()) {
          if (connection.isValid(2)) {
              return "UP";
          }
          return "DOWN (Validation timeout)";
      } catch (Exception e) {
          return "DOWN (" + e.getMessage() + ")";
      }
  }
  ```
  *(Uses `try-with-resources` to guarantee that the database connection is immediately closed and returned to the HikariCP pool).*

### 5. `HealthController.java`
- **Location:** `D:\projects\src\main\java\com\searchforge\controller\HealthController.java`
- **Role:** Exposes `GET /api/v1/health`. Uses Constructor Injection to receive `HealthService`.

### 6. `HealthControllerTest.java`
- **Location:** `D:\projects\src\test\java\com\searchforge\HealthControllerTest.java`
- **Role:** Slice test using `@WebMvcTest(HealthController.class)`. It does not boot the whole server or database; it mocks `HealthService` and verifies that the endpoint produces valid JSON with HTTP status 200.

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

### Gotcha 3: Terminal Appears "Frozen" After `mvn spring-boot:run`
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
> - Our code interacts with **Spring Data JPA**, which is an abstraction layer on top of Hibernate.

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
> 5. The controller delegates business logic to `HealthService`, which borrows a connection from the **HikariCP pool** to verify database health.
> 6. The controller returns a `HealthResponse` record wrapped in `ResponseEntity.ok()`.
> 7. Spring's `HttpMessageConverter` (powered by **Jackson**) serializes the Java record into JSON text.
> 8. Tomcat writes the HTTP 200 OK response with `Content-Type: application/json` back across the TCP socket to the client.
