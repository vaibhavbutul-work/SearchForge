# SearchForge: REST API Documentation (Phase 1)

This document provides the technical contract, request formats, response schemas, and error handling for all REST and actuator endpoints available in Phase 1.

---

## 1. Custom Health Endpoint (`/api/v1/health`)

Checks the health of the SearchForge backend service and verifies database connectivity over a raw JDBC socket.

- **Method:** `GET`
- **Path:** `/api/v1/health`
- **Authentication:** None (Public)
- **Headers:**
  - `Accept: application/json`

### Success Response (Database UP)
- **HTTP Status:** `200 OK`
- **Content-Type:** `application/json`
- **Payload Schema:**
  | Field | Type | Description |
  | :--- | :--- | :--- |
  | `status` | `string` | Overall service health: `"UP"` or `"DOWN"` |
  | `service` | `string` | Service identifier name (`"SearchForge"`) |
  | `version` | `string` | Current semantic release version (`"1.0.0"`) |
  | `database` | `string` | Direct PostgreSQL connectivity status (`"UP"` or `"DOWN"`) |
  | `timestamp` | `string` (ISO-8601) | UTC timestamp of health check execution |
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

### Failure Response (Database DOWN / Outage)
- **HTTP Status:** `503 Service Unavailable`
- **Content-Type:** `application/json`
- **Example Response:**
  ```json
  {
    "status": "DOWN",
    "service": "SearchForge",
    "version": "1.0.0",
    "database": "DOWN",
    "timestamp": "2026-11-16T12:00:00Z"
  }
  ```

### Contract Guarantees & Security Controls:
1. **JSON Contract Preservation:** The `HealthResponse` record method `boolean isUp()` is annotated with `@JsonIgnore`. This prevents Jackson from serializing an unintended `"up": true/false` property into the JSON contract.
2. **Information Leakage Prevention:** During downstream database failures, all raw SQL exceptions, connection pool messages, and host addresses are masked into safe `"DOWN"` statuses. Full exception traces are logged server-side via SLF4J (`log.error("Database health check failed", e)`).

---

## 2. Spring Boot Actuator Health Probe (`/actuator/health`)

Standard Spring Boot Actuator endpoint used by orchestration platforms (e.g. Kubernetes, Docker healthchecks, load balancers) to probe application readiness and component statuses.

- **Method:** `GET`
- **Path:** `/actuator/health`
- **Authentication:** None
- **Headers:**
  - `Accept: application/json`

### Success Response (Application & Database UP)
- **HTTP Status:** `200 OK`
- **Content-Type:** `application/vnd.spring-boot.actuator.v3+json;charset=UTF-8`
- **Example Response:**
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL",
          "validationQuery": "isValid()"
        }
      },
      "diskSpace": {
        "status": "UP",
        "details": {
          "total": 107374182400,
          "free": 53687091200,
          "threshold": 10485760,
          "exists": true
        }
      },
      "ping": {
        "status": "UP"
      }
    }
  }
  ```

### Failure Response (Database DOWN)
- **HTTP Status:** `503 Service Unavailable`
- **Content-Type:** `application/vnd.spring-boot.actuator.v3+json;charset=UTF-8`
- **Example Response:**
  ```json
  {
    "status": "DOWN",
    "components": {
      "db": {
        "status": "DOWN"
      },
      "diskSpace": {
        "status": "UP"
      },
      "ping": {
        "status": "UP"
      }
    }
  }
  ```

### Security & Information Disclosure Audit:
- Actuator endpoint details expose only component statuses and disk thresholds (`show-details: always`).
- Zero database credentials, user passwords, internal private network IPs, or environment variables are exposed on any public endpoint.
