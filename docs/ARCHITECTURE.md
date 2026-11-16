# SearchForge: Architecture & Design Principles

This document explains the software architecture, design patterns, and request lifecycle of the SearchForge backend.

---

## 1. Layered Architecture

SearchForge follows the industry-standard **Layered Architecture Pattern**:

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│  com.searchforge.controller (HealthController.java)   │
│  - Accepts HTTP requests and parses headers/params     │
│  - Returns ResponseEntity<T> with HTTP status codes    │
│  - Thin layer: zero business logic                     │
└───────────────────────────┬────────────────────────────┘
                            │ Calls Service
                            ▼
┌────────────────────────────────────────────────────────┐
│                     Service Layer                      │
│     com.searchforge.service (HealthService.java)       │
│  - Encapsulates business logic & calculations          │
│  - Manages transaction boundaries (@Transactional)     │
│  - Checks database health via DataSource               │
└───────────────────────────┬────────────────────────────┘
                            │ Interacts with Data
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Persistence Layer                    │
│      com.searchforge.repository / entity (Future)      │
│  - Spring Data JPA repositories                        │
│  - Hibernate ORM mapping Java objects to SQL tables    │
└───────────────────────────┬────────────────────────────┘
                            │ JDBC via HikariCP Pool
                            ▼
┌────────────────────────────────────────────────────────┐
│                    PostgreSQL 16                       │
│              searchforge-postgres Container            │
└────────────────────────────────────────────────────────┘
```

---

## 2. The HTTP Request-Response Lifecycle

Understanding the path of an HTTP request through Spring Boot is essential for backend engineering:

```
Client (Postman / Browser)
       │  1. HTTP GET /api/v1/health
       ▼
 [ Embedded Apache Tomcat Server ] (Port 8080)
       │  2. Accepts TCP socket, parses raw HTTP bytes into HttpServletRequest
       ▼
 [ DispatcherServlet ] (Front Controller Pattern)
       │  3. Consults HandlerMapping to find the matching @RestController method
       ▼
 [ HandlerMapping ] -> Finds HealthController.getHealth()
       │  4. Invokes method via reflection
       ▼
 [ HealthController ]
       │  5. Delegates to HealthService.checkHealth()
       ▼
 [ HealthService ]
       │  6. Borrows connection from HikariCP, checks connection.isValid(2)
       ▼
 [ HealthResponse Record ]
       │  7. Returns DTO to Controller wrapped in ResponseEntity.ok()
       ▼
 [ HttpMessageConverter (Jackson ObjectMapper) ]
       │  8. Serializes Java record into JSON text
       ▼
Client receives HTTP 200 OK + JSON payload
```

---

## 3. Data Transfer Objects (DTOs) vs. Entities

SearchForge uses Java 21 Records as DTOs (e.g. `HealthResponse.java`):

* **Why never return Entities directly in REST APIs?**
  1. **Security:** Entities map to database tables and might contain internal IDs or sensitive data.
  2. **Coupling:** If the database schema changes, client API consumers should not break. DTOs form a stable contract.
  3. **Serialization Bugs:** Circular relationships (e.g., Parent -> Children -> Parent) cause infinite recursion during JSON serialization with Jackson.
  4. **Performance:** DTOs only serialize the exact fields needed by the client.

---

## 4. Database Connection Pooling (HikariCP)

Creating a new TCP socket connection to a PostgreSQL database takes 30-100ms (TCP handshake, SSL negotiation, authentication). If done per request, the API would be slow and brittle.

SearchForge uses **HikariCP**:
- Maintains a pool of pre-established database connections (`maximum-pool-size: 10`).
- A request borrows a connection, executes its query in <1ms, and returns it to the pool in a `try-with-resources` block.
- If the database restarts or goes down, HikariCP automatically detects stale connections and evicts them.
