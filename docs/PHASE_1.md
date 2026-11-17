# SearchForge — Phase 1: Setup and Backend Foundation

This document details all technical decisions, architecture, and verification steps implemented during **Phase 1**.

---

## 1. Objectives Achieved

- [x] Initialized modern Spring Boot 3.4.0 application targeted for Java 21 LTS in a dedicated `backend/` directory *(Planned: upgrade to Spring Boot 4.1.x on a separate branch before Phase 2)*.
- [x] Configured minimal, strictly justified Maven dependencies in `backend/pom.xml`.
- [x] Generated script-only Apache Maven Wrapper pinned to 3.9.9 (`backend/mvnw`, `backend/mvnw.cmd`).
- [x] Created clean layered package architecture (`controller`, `service`, `dto`).
- [x] Configured PostgreSQL 16 containerization using Docker Compose (`docker-compose.yml`) with persistent volume and healthcheck.
- [x] Externalized database credentials to `.env` with fail-fast `${DB_PASSWORD}` and provided `scripts/load-env.ps1`.
- [x] Configured Spring Boot DataSource with HikariCP connection pooling (`backend/src/main/resources/application.yml`) with `initialization-fail-timeout: 0` and `connection-timeout: 3000ms`.
- [x] Implemented `GET /api/v1/health` REST endpoint returning HTTP 200 OK when UP and HTTP 503 Service Unavailable when DOWN, with error masking and SLF4J stack trace logging.
- [x] Added `@JsonIgnore` to `HealthResponse.isUp()` to protect the public JSON contract.
- [x] Implemented multi-tier automated test suite (6 automated tests across unit, web slice, and hermetic context tests).
- [x] Documented architectural principles, request lifecycle, and database configuration.

---

## 2. Key Components Created

### A. Core Application Entry
- **File:** `backend/src/main/java/com/searchforge/SearchForgeApplication.java`
- **Annotation:** `@SpringBootApplication`
  - Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`.

### B. Health API DTO
- **File:** `backend/src/main/java/com/searchforge/dto/HealthResponse.java`
- **Type:** Java 21 `record`
- **Fields:**
  - `status`: Overall service status (`UP` / `DOWN`).
  - `service`: Name of the service (`SearchForge`).
  - `version`: Version string (`1.0.0`).
  - `database`: PostgreSQL connectivity status (`UP` / `DOWN`).
  - `timestamp`: UTC Instant of health check.
- **Contract Protection:** Method `isUp()` is annotated with `@JsonIgnore` to prevent Jackson from serializing an unintended `"up"` boolean into the JSON payload.

### C. Health Service
- **File:** `backend/src/main/java/com/searchforge/service/HealthService.java`
- **Role:** Directly uses JDBC via `javax.sql.DataSource` and `connection.isValid(2)` with a 2-second timeout to test raw database socket connectivity with zero ORM overhead. Uses `try-with-resources` to ensure connections are immediately closed and returned to the HikariCP pool.
- **Error Masking & Logging:** Catches any connectivity exceptions, logs the full stack trace server-side via SLF4J (`log.error("Database health check failed", e)`), and masks details to return safe `"DOWN"` statuses without leaking SQL errors or credentials to clients.

### D. Health REST Controller
- **File:** `backend/src/main/java/com/searchforge/controller/HealthController.java`
- **Route:** `GET /api/v1/health`
- **Behavior:** Inspects `response.isUp()`. Returns **HTTP 200 OK** when UP, and **HTTP 503 Service Unavailable** when DOWN.
- **Annotations:**
  - `@RestController`: Marks the class as an HTTP request handler where return values are serialized directly into the HTTP response body.
  - `@RequestMapping("/api/v1")`: Base path for versioned REST APIs.
  - `@GetMapping("/health")`: Maps HTTP GET requests.

### E. PostgreSQL Docker Compose
- **File:** `docker-compose.yml`
- **Image:** `postgres:16-alpine`
- **Ports:** `"${DB_PORT:-5432}:5432"`
- **Environment:** Dynamic resolution via `${DB_NAME:-searchforge_db}`, `${DB_USERNAME:-searchforge}`, and fail-fast `${DB_PASSWORD:?DB_PASSWORD is required}`.
- **Volume:** `postgres_data` persistent volume (`driver: local`).
- **Healthcheck:** Utilizes `pg_isready -U ${DB_USERNAME:-searchforge} -d ${DB_NAME:-searchforge_db}` with 5s interval, 5s timeout, and 5 retries.

---

## 3. Automated Test Suite

SearchForge includes **6 automated tests** ([MEASURED] in `.\mvnw.cmd test` executing in 8.388 s):

1. **`HealthControllerTest`** (`backend/src/test/java/com/searchforge/HealthControllerTest.java`):
   - Web slice test using `@WebMvcTest(HealthController.class)`.
   - Test 1: Verifies HTTP 200 OK and valid JSON contract when `healthService.checkHealth()` returns UP.
   - Test 2: Verifies HTTP 503 Service Unavailable, status `DOWN`, and asserts `$.up` does not exist in JSON when DOWN.
2. **`HealthServiceTest`** (`backend/src/test/java/com/searchforge/service/HealthServiceTest.java`):
   - Fast, pure Mockito unit test (zero Spring context overhead).
   - Test 1: Verifies status `UP` when `connection.isValid(2)` returns `true`.
   - Test 2: Verifies status `DOWN` when `connection.isValid(2)` returns `false`.
   - Test 3: Verifies exception handling and error masking when `dataSource.getConnection()` throws `SQLException` (asserting zero exception messages in `response.toString()`). Proves `connection.close()` is verified in all branches.
3. **`SearchForgeApplicationTests`** (`backend/src/test/java/com/searchforge/SearchForgeApplicationTests.java`):
   - Hermetic Spring context bootstrap test using `@SpringBootTest` and `@MockitoBean DataSource`.
   - Configured with `hibernate.boot.allow_jdbc_metadata_access: false` in `backend/src/test/resources/application.yml` (which completely replaces, rather than merges with, the main `application.yml` during test runs).
   - Proves complete Spring application context bean wiring without requiring an active PostgreSQL container (testing bean wiring only, not live database connectivity).

To run the test suite:
```powershell
cd backend
.\mvnw.cmd test
```
*(On Linux/macOS: `./mvnw test`)*

---

## 4. Testing the Live Endpoint

### Prerequisites
1. Ensure `.env` is configured with required credentials (copy from `.env.example`).
2. Start the database container:
   ```powershell
   docker compose up -d
   ```
3. Load environment variables and start the backend:
   ```powershell
   . .\scripts\load-env.ps1
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

### A. When Database is UP (Healthy)
- **HTTP Method:** `GET`
- **URL:** `http://localhost:8080/api/v1/health`
- **Expected Status:** `200 OK`
- **Response Body:**
  ```json
  {
    "status": "UP",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "UP",
    "timestamp": "2026-11-16T12:00:00.000Z"
  }
  ```

### B. When Database is DOWN (Outage)
- **HTTP Method:** `GET`
- **URL:** `http://localhost:8080/api/v1/health`
- **Expected Status:** `503 Service Unavailable`
- **Response Body (Masked, Safe):**
  ```json
  {
    "status": "DOWN",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "DOWN",
    "timestamp": "2026-11-16T12:00:00.000Z"
  }
  ```
  *(Note: Server logs full stack trace via SLF4J, but zero SQL error text is leaked to the client).*

### Command Line Verification
```powershell
curl.exe -i http://localhost:8080/api/v1/health
```
