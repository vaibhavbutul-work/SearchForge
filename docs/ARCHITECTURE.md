# SearchForge: Architecture & Design Principles

This document explains the software architecture, design patterns, and request lifecycle of the SearchForge backend.

---

## 1. Layered Architecture

SearchForge follows the industry-standard **Layered Architecture Pattern**:

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

### Architectural Separation:
1. **Presentation Layer (`com.searchforge.controller`):**
   - Purely handles HTTP protocol concerns: mapping URLs, status code negotiation (HTTP 200 vs 503), and JSON response generation.
   - Thin layer: zero business algorithms or direct database queries.
2. **Service Layer (`com.searchforge.service`):**
   - Orchestrates business workflows and dependency checks.
   - In Phase 1, `HealthService` directly accesses `javax.sql.DataSource` to run low-level socket validation (`connection.isValid(2)`) with zero ORM overhead.
   - Planned for Phase 3: Injects Spring Data repositories for document and posting storage.
3. **Persistence Layer (Phase 3 Planned):**
   - Will encapsulate entity mapping (`@Entity`) and query generation via `JpaRepository`.
   - In Phase 1, Spring Data JPA / Hibernate is on the classpath for future use, but no entities or repositories exist.
4. **Database Transport:**
   - HikariCP connection pool manages persistent TCP sockets to PostgreSQL 16 on port 5432.

---

## 2. The HTTP Request-Response Lifecycle

Understanding the path of an HTTP request through Spring Boot is essential for backend engineering:

```
Client (Browser / Postman / cURL)
       │  1. HTTP GET /api/v1/health
       ▼
 [ Embedded Apache Tomcat Server ] (Port 8080)
       │  2. Accepts TCP socket, parses raw HTTP bytes into HttpServletRequest
       ▼
 [ DispatcherServlet ] (Front Controller Pattern)
       │  3. Consults HandlerMapping to find the matching @RestController method
       ▼
 [ HandlerMapping ] -> Locates HealthController.getHealth()
       │  4. Invokes method via reflection
       ▼
 [ HealthController ]
       │  5. Delegates to HealthService.checkHealth()
       ▼
 [ HealthService ]
       │  6. Borrows connection from HikariCP pool and tests connection.isValid(2)
       ▼
 Database Connectivity Outcome:
       ├── Branch A (Database is UP):
       │     - Socket ping succeeds; connection returned to pool via try-with-resources
       │     - HealthService creates HealthResponse record with status: "UP", database: "UP"
       │     - HealthController inspects response.isUp() (true) -> returns ResponseEntity.ok(response)
       │     - Jackson serializes record into JSON (omitting isUp() via @JsonIgnore)
       │     - Client receives HTTP 200 OK + {"status":"UP","database":"UP",...}
       │
       └── Branch B (Database is DOWN / Unreachable):
             - HikariCP times out or throws SQLException; connection is closed safely
             - HealthService logs full stack trace via SLF4J (log.error("...", e))
             - HealthService masks all error details, returning safe status: "DOWN", database: "DOWN"
             - HealthController inspects response.isUp() (false) -> returns ResponseEntity.status(503).body(response)
             - Jackson serializes record into JSON (zero SQL error details leaked)
             - Client receives HTTP 503 Service Unavailable + {"status":"DOWN","database":"DOWN",...}
```

---

## 3. Data Transfer Objects (DTOs) vs. Entities

SearchForge uses Java 21 Records as DTOs (e.g. `HealthResponse.java`):

* **Why never return Entities directly in REST APIs?**
  1. **Security & Information Leakage:** Entities map to physical database tables and often contain internal IDs, audit metadata, or sensitive fields. Returning them leaks internal schema details.
  2. **API Contract Stability:** If the database column changes from `title` to `doc_title`, frontend clients will break if entities are exposed. DTOs form an independent, decoupled contract.
  3. **Circular Reference Infinite Loops:** Bidirectional entity relationships (e.g., Parent -> Children -> Parent) cause Jackson to enter an infinite serialization recursion, throwing `StackOverflowError`.
  4. **Performance:** DTOs serialize only the fields required for the specific client view, avoiding over-fetching and unnecessary JSON payload size.
  5. **Jackson Serialization Protection:** Helper methods on DTO records (such as `boolean isUp()`) must be annotated with `@JsonIgnore` to prevent Jackson's JavaBean reflection from serializing an unintended `"up": true/false` key into the public JSON response.

---

## 4. Database Connection Pooling (HikariCP) & Resilience

Establishing a physical TCP socket connection to a PostgreSQL database takes **30ms to 100ms** (TCP 3-way handshake, TLS exchange, and PostgreSQL process authentication). If performed per request, throughput would drop dramatically.

SearchForge uses **HikariCP** (configured in `backend/src/main/resources/application.yml`):
- **Pre-allocation:** Maintains a pool of ready-to-use database connections (`maximum-pool-size: 10`, `minimum-idle: 2`).
- **Sub-millisecond Acquisition:** Requests borrow an existing authenticated connection in <1ms, execute queries, and return it to the pool in a `try-with-resources` block.
- **Resilient Boot Policy (`initialization-fail-timeout: 0`):**
  - HikariCP still attempts to obtain and validate one initial connection on startup; if the database is unreachable, the pool starts anyway rather than crashing the JVM on boot. (If connected but validation fails, startup aborts).
  - Hibernate's `JdbcEnvironmentInitiator` still queries JDBC metadata on boot, waiting for Hikari's connection timeout.
  - With default 30s timeout: startup froze for **30,019 ms** ([MEASURED], total boot time 33.463 s).
  - With tuned `connection-timeout: 3000ms`: startup freeze dropped to **3,016 ms** ([MEASURED], total boot time 6.748 s).
  - Downstream health checks fail fast in **3.127 s** ([MEASURED]) returning HTTP 503 instead of hanging client requests for 30 seconds.
