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
SearchForge/
├── backend/
│   ├── pom.xml                              # Maven build file & dependencies
│   └── src/
│       ├── main/
│       │   ├── java/com/searchforge/
│       │   │   ├── SearchForgeApplication.java  # Main application entry point
│       │   │   ├── controller/
│       │   │   │   └── HealthController.java    # REST API (@RestController)
│       │   │   ├── service/
│       │   │   │   └── HealthService.java       # Health verification logic (@Service)
│       │   │   └── dto/
│       │   │       └── HealthResponse.java      # Java 21 Record DTO
│       │   └── resources/
│       │       └── application.yml              # Database & Spring configuration
│       └── test/
│           └── java/com/searchforge/
│               └── HealthControllerTest.java    # WebMvc unit test
├── docs/                                    # Architectural and phase documentation
├── scripts/
│   └── load-env.ps1                         # Helper script to load .env in PowerShell
├── docker-compose.yml                       # PostgreSQL 16 container definition
├── .env.example                             # Environment variable template
├── .gitignore                               # Git ignore rules (includes .env)
└── README.md                                # Project summary and quickstart
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
- **Java:** JDK 21 (Eclipse Temurin 21 LTS recommended)
- **Maven:** Apache Maven 3.9+
- **Docker & Docker Compose:** For running the PostgreSQL container

### 2. Configure Environment Variables
Copy the template to `.env` and set your database password:
```powershell
# From project root (D:\projects)
Copy-Item .env.example .env
```
Open `.env` and replace `replace_with_your_secure_password` with your desired local password:
- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=searchforge_db`
- `DB_USERNAME=searchforge`
- `DB_PASSWORD=your_chosen_password`

### 3. Start PostgreSQL Database
```powershell
docker compose up -d
```
> **Important Docker Volume Note:** PostgreSQL only sets the database password during initial container creation. If you change `DB_PASSWORD` in `.env` later, you must run `docker compose down -v` followed by `docker compose up -d` to re-initialize the volume (note: `-v` resets stored database data).

### 4. Load Environment Variables & Run Backend
Because Spring Boot runs outside Docker, load the `.env` variables into your active PowerShell session before running the app:
```powershell
# Dot-source the helper script to export variables into current terminal
. .\scripts\load-env.ps1

# Navigate to backend and run
cd backend
mvn clean compile
mvn test
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
