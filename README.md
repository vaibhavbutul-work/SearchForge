# SearchForge 🔍
> **Build Your Own Search Engine from First Principles**

SearchForge is a custom search engine designed and built from scratch using **Java 21**, **Spring Boot 3.4**, and **PostgreSQL**. Rather than relying on external search APIs, SearchForge implements core information retrieval concepts: web crawling, document extraction, tokenization, inverted indexing, and relevance ranking algorithms.

---

## 🛠️ Technology Stack (Phase 1)

- **Language:** Java 21 (LTS) — Records, Pattern Matching, Modern JVM features
- **Framework:** Spring Boot 3.4.0 *(Planned: upgrade to Spring Boot 4.1.x on a separate branch before Phase 2)*
- **Data Persistence:** Direct JDBC via `javax.sql.DataSource` and `connection.isValid(2)` for Phase 1 health probing; Spring Data JPA & Hibernate 6 on classpath for future document storage (Phase 3)
- **Database:** PostgreSQL 16 (Containerized via Docker Compose)
- **Connection Pool:** HikariCP (`connection-timeout: 3000ms`, `initialization-fail-timeout: 0`)
- **Build & Dependency Tool:** Apache Maven 3.9.9 via Maven Wrapper (`.\mvnw.cmd` / `./mvnw`, `only-script`)
- **Testing:** JUnit 5, Mockito 5, Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`) — 6 automated tests
- **Monitoring:** Spring Boot Actuator

---

## 📁 Repository Structure

```
SearchForge/
├── .gitattributes                           # Enforces LF for mvnw/sh and CRLF for cmd/bat
├── .gitignore                               # Git ignore rules (build artifacts, IDEs, .env)
├── .env.example                             # Environment variable template
├── docker-compose.yml                       # PostgreSQL 16 Alpine container definition
├── README.md                                # Project summary and quickstart guide
├── backend/                                 # Java backend application root
│   ├── mvnw                                 # Maven wrapper shell script (LF, +x)
│   ├── mvnw.cmd                             # Maven wrapper Windows batch script (CRLF)
│   ├── pom.xml                              # Build configuration & dependencies
│   ├── .mvn/wrapper/
│   │   └── maven-wrapper.properties         # Wrapper config pinned to Maven 3.9.9 (only-script)
│   └── src/
│       ├── main/
│       │   ├── java/com/searchforge/
│       │   │   ├── SearchForgeApplication.java  # Main application entry point
│       │   │   ├── controller/
│       │   │   │   └── HealthController.java    # REST API (HTTP 200 / 503)
│       │   │   ├── service/
│       │   │   │   └── HealthService.java       # Direct JDBC health check (@Service)
│       │   │   └── dto/
│       │   │       └── HealthResponse.java      # Java 21 Record DTO (@JsonIgnore on isUp())
│       │   └── resources/
│       │       └── application.yml              # Database & Spring configuration
│       └── test/
│           ├── java/com/searchforge/
│           │   ├── HealthControllerTest.java    # Web slice tests (@WebMvcTest)
│           │   ├── SearchForgeApplicationTests.java # Hermetic context test (@SpringBootTest)
│           │   └── service/
│           │       └── HealthServiceTest.java   # Unit tests with Mockito (connection close, error masking)
│           └── resources/
│               └── application.yml              # Hermetic test config (replaces main application.yml)
├── docs/                                    # Architectural and technical documentation
│   ├── PHASE_1_COMPLETE_GUIDE.md            # Comprehensive masterclass & review corrections guide
│   ├── PHASE_1.md                           # Phase 1 summary, verification, and tests
│   ├── ARCHITECTURE.md                      # Layered architecture & request lifecycle
│   └── PROJECT_OVERVIEW.md                  # Project motivation, pipeline, and tech rationale
└── scripts/
    └── load-env.ps1                         # PowerShell script to load .env into session
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Java:** JDK 21 (Eclipse Temurin 21 LTS recommended; verify with `java -version`)
- **Docker & Docker Compose:** For running the PostgreSQL container (`docker compose version`)
- **Maven:** Not required on host! The repository includes Apache Maven Wrapper 3.9.9 (`mvnw` / `mvnw.cmd`).

### 2. Configure Environment Variables
Copy the template to `.env` and set your database credentials:
```powershell
# From project root (D:\projects)
Copy-Item .env.example .env
```
Open `.env` and set your credentials:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=searchforge_db
DB_USERNAME=searchforge
DB_PASSWORD=your_secure_local_password
```
*(Note: `.env` is git-ignored and must never be committed).*

### 3. Start PostgreSQL Database
```powershell
docker compose up -d
```
Verify the container is healthy:
```powershell
docker compose ps
```
> **Important Docker Volume Note:** PostgreSQL initializes database credentials only on the first run of a new volume. If you change `DB_PASSWORD` in `.env` later, run `docker compose down -v` followed by `docker compose up -d` to re-initialize the persistent volume (note: `-v` deletes existing database data).

### 4. Load Environment Variables into Session
Spring Boot runs locally on your host machine while PostgreSQL runs in Docker. Before launching the app, export the `.env` variables into your terminal session:

```powershell
# Dot-source the script (notice the leading dot and space):
. .\scripts\load-env.ps1
```

> **Why Dot-Sourcing is Required:**  
> In PowerShell, running `.\scripts\load-env.ps1` executes the script inside an isolated child scope; all exported environment variables are discarded when the script finishes. By **dot-sourcing** (`. .\scripts\load-env.ps1`), PowerShell runs the script directly in your **current terminal session scope**, allowing `$env:DB_PASSWORD` and other variables to remain available for subsequent Maven wrapper commands.

### 5. Build, Test, and Run Backend
Navigate to the `backend/` directory and use the Maven Wrapper:

```powershell
cd backend

# Compile application
.\mvnw.cmd compile

# Execute all 6 automated tests
.\mvnw.cmd test

# Run the Spring Boot application
.\mvnw.cmd spring-boot:run
```
*(On Linux/macOS, use `./mvnw compile`, `./mvnw test`, and `./mvnw spring-boot:run`)*.

The application starts on port `8080`.

---

## 🧪 Automated Testing

SearchForge includes **6 automated tests** ([MEASURED] in `.\mvnw.cmd test` running in ~8.4 seconds):
- **Web Slice Tests (`HealthControllerTest`):** Verifies HTTP 200 mapping on `UP`, HTTP 503 mapping on `DOWN`, and verifies that `@JsonIgnore` prevents `"up"` from leaking into JSON.
- **Unit Tests (`HealthServiceTest`):** Pure Mockito unit tests verifying `isValid(true)`, `isValid(false)`, exception handling with error masking, and proves that `connection.close()` is invoked in all branches.
- **Hermetic Context Test (`SearchForgeApplicationTests`):** Boots the Spring `@SpringBootTest` context using `@MockitoBean DataSource` and `hibernate.boot.allow_jdbc_metadata_access: false` without needing a running database container (verifying bean wiring only, not live database connectivity).

---

## 📡 API Endpoints (Phase 1)

### 1. Application Health Check
- **Endpoint:** `GET /api/v1/health`
- **When Database is UP:** Returns **HTTP 200 OK**
  ```json
  {
    "status": "UP",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "UP",
    "timestamp": "2026-11-16T12:00:00Z"
  }
  ```
- **When Database is DOWN:** Returns **HTTP 503 Service Unavailable** (fast failure within 3.127 s [MEASURED]):
  ```json
  {
    "status": "DOWN",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "DOWN",
    "timestamp": "2026-11-16T12:00:00Z"
  }
  ```
  *(All internal exception strings are masked from clients; full stack traces are logged server-side via SLF4J).*

### 2. Spring Boot Actuator Health Probe
- **Endpoint:** `GET /actuator/health`
- **Description:** Exposes standard Spring Boot Actuator component readiness (`/actuator/health`, `/actuator/info`, `/actuator/metrics`).

---

## 🗺️ Project Roadmap

- [x] **Phase 1: Project Setup and Backend Foundation** *(Hardened & Fully Tested)*
- [ ] **Phase 2: Web Crawler (Jsoup, BFS URL Queue, Politeness, Robotstxt)**
- [ ] **Phase 3: Document Processing & Storage (Tokenization, Normalization, Spring Data JPA Entities)**
- [ ] **Phase 4: Inverted Index (Postings Lists, Term Frequencies, Dictionary)**
- [ ] **Phase 5: Search & Ranking Engine (TF-IDF, BM25 Scoring)**
- [ ] **Phase 6: Search Interface (Web Frontend)**
- [ ] **Phase 7: Advanced Features (Caching, Concurrent Crawling via Virtual Threads)**
- [ ] **Phase 8: Dockerization, Deployment & Benchmarking**
