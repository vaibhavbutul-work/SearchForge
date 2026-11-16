# SearchForge 🔍
> **Build Your Own Search Engine from First Principles**

SearchForge is a custom search engine designed and built from scratch using **Java 21**, **Spring Boot 3.4**, and **PostgreSQL**. Rather than relying on external search APIs, SearchForge implements core information retrieval concepts: web crawling, document extraction, tokenization, inverted indexing, and relevance ranking algorithms.

---

## 🛠️ Technology Stack (Phase 1)

- **Language:** Java 21 (LTS) — Records, Pattern Matching, Modern JVM features
- **Framework:** Spring Boot 3.4.0
- **Data Persistence:** Spring Data JPA & Hibernate 6
- **Database:** PostgreSQL 16 (Containerized via Docker Compose)
- **Connection Pool:** HikariCP
- **Build & Dependency Tool:** Apache Maven
- **Testing:** JUnit 5, Mockito, Spring Boot Test (MockMvc)
- **Monitoring:** Spring Boot Actuator

---

## 📁 Repository Structure

```
d:\projects\
├── src\
│   ├── main\
│   │   ├── java\com\searchforge\
│   │   │   ├── SearchForgeApplication.java  # Main application entry point
│   │   │   ├── controller\
│   │   │   │   └── HealthController.java    # REST API (@RestController)
│   │   │   ├── service\
│   │   │   │   └── HealthService.java       # Health verification logic (@Service)
│   │   │   └── dto\
│   │   │       └── HealthResponse.java      # Java 21 Record DTO
│   │   └── resources\
│   │       └── application.yml              # Database & Spring configuration
│   └── test\
│       └── java\com\searchforge\
│           └── HealthControllerTest.java    # Automated WebMvc unit test
├── docs\
│   ├── ARCHITECTURE.md                      # System design and request lifecycle
│   ├── PHASE_1.md                           # Phase 1 deep-dive and technical log
│   └── PROJECT_OVERVIEW.md                  # Complete project vision and roadmap
├── docker-compose.yml                       # PostgreSQL 16 container definition
├── pom.xml                                  # Maven dependencies and build plugin
├── .gitignore                               # Git ignore rules
└── README.md                                # Project summary and quickstart
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Java:** JDK 21 (Eclipse Temurin 21 LTS recommended)
- **Maven:** Apache Maven 3.9+
- **Docker & Docker Compose:** For running the PostgreSQL container

### 2. Start PostgreSQL Database
```powershell
docker compose up -d
```

### 3. Build & Run SearchForge Backend
```powershell
# Compile the project
mvn clean compile

# Run automated tests
mvn test

# Start the Spring Boot application
mvn spring-boot:run
```

The application will start on port `8080`.

---

## 📡 API Endpoints (Phase 1)

### 1. Application Health Check
- **Endpoint:** `GET /api/v1/health`
- **Description:** Verifies that the backend server is running and the PostgreSQL database connection pool is active.
- **Example Response:**
  ```json
  {
    "status": "UP",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "UP",
    "timestamp": "2026-11-16T12:00:00Z"
  }
  ```

### 2. Spring Boot Actuator Health Probe
- **Endpoint:** `GET /actuator/health`
- **Description:** Production-ready health check exposing database readiness and disk health.

---

## 🗺️ Project Roadmap

- [x] **Phase 1: Project Setup and Backend Foundation**
- [ ] **Phase 2: Web Crawler (Jsoup, BFS URL Queue, Politeness)**
- [ ] **Phase 3: Document Processing (Tokenization, Stop Words, Normalization)**
- [ ] **Phase 4: Inverted Index (Postings Lists, Term Frequencies)**
- [ ] **Phase 5: Search & Ranking (TF-IDF, BM25 Scoring)**
- [ ] **Phase 6: Search Interface (React Frontend)**
- [ ] **Phase 7: Advanced Features (Pagination, Caching, Concurrent Crawling)**
- [ ] **Phase 8: Dockerization, Deployment & Benchmarking**
