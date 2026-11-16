# SearchForge — Phase 1: Setup and Backend Foundation

This document details all technical decisions, architecture, and verification steps implemented during **Phase 1**.

---

## 1. Objectives Achieved

- [x] Initialized modern Spring Boot 3.4.0 application targeted for Java 21 LTS.
- [x] Configured minimal, strictly justified Maven dependencies (`pom.xml`).
- [x] Created clean layered package architecture (`controller`, `service`, `dto`).
- [x] Configured PostgreSQL containerization using Docker Compose (`docker-compose.yml`).
- [x] Configured Spring Boot DataSource with HikariCP connection pooling (`application.yml`).
- [x] Implemented `GET /api/v1/health` REST endpoint returning structured JSON.
- [x] Verified unit testing with Spring MockMvc and `@WebMvcTest`.
- [x] Documented architectural principles, request lifecycle, and database configuration.

---

## 2. Key Components Created

### A. Core Application Entry
- **File:** `src/main/java/com/searchforge/SearchForgeApplication.java`
- **Annotation:** `@SpringBootApplication`
  - Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`.

### B. Health API DTO
- **File:** `src/main/java/com/searchforge/dto/HealthResponse.java`
- **Type:** Java 21 `record`
- **Fields:**
  - `status`: Overall service status (`UP` / `DEGRADED`).
  - `service`: Name of the service (`SearchForge`).
  - `version`: Version string (`1.0.0`).
  - `database`: PostgreSQL connectivity status (`UP` / `DOWN`).
  - `timestamp`: UTC Instant of health check.

### C. Health Service
- **File:** `src/main/java/com/searchforge/service/HealthService.java`
- **Role:** Borrows a connection from `DataSource` (HikariCP) and tests `connection.isValid(2)` with a 2-second timeout. Uses `try-with-resources` to ensure connections return to the pool immediately.

### D. Health REST Controller
- **File:** `src/main/java/com/searchforge/controller/HealthController.java`
- **Route:** `GET /api/v1/health`
- **Annotations:**
  - `@RestController`: Marks the class as an HTTP request handler where return values are serialized directly into the HTTP response body.
  - `@RequestMapping("/api/v1")`: Base path for versioned REST APIs.
  - `@GetMapping("/health")`: Maps HTTP GET requests.

### E. PostgreSQL Docker Compose
- **File:** `docker-compose.yml`
- **Image:** `postgres:16-alpine`
- **Port:** `5432:5432`
- **Volume:** `postgres_data` persistent volume.
- **Healthcheck:** Utilizes `pg_isready` to monitor database readiness.

---

## 3. Testing the Endpoint

### Using Postman
1. Open Postman.
2. Select **GET** as the HTTP Method.
3. Enter URL: `http://localhost:8080/api/v1/health`
4. Click **Send**.
5. Expected Status: `200 OK`
6. Expected Response Body:
   ```json
   {
     "status": "UP",
     "service": "SearchForge",
     "version": "1.0.0",
     "database": "UP",
     "timestamp": "2026-11-16T12:00:00.000Z"
   }
   ```

### Using cURL
```bash
curl -X GET http://localhost:8080/api/v1/health
```
