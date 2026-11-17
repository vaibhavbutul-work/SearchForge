# SearchForge: Architectural Decision Records (ADRs)

This document records the architectural and design decisions established during Phase 1.

---

### ADR-001: Dedicated Backend Subdirectory Layout
- **Context:** Project root initially mixed Java source code, test trees, and `pom.xml` with Docker definitions, PowerShell scripts, and project documentation.
- **Decision:** Relocate all Java application source code, resources, tests, and build tooling into a dedicated `backend/` subdirectory.
- **Trade-off:** Developers must navigate into `backend/` or target `-f backend/pom.xml` for build commands, but isolates application code cleanly from repository-level orchestration and docs.

---

### ADR-002: Credential Externalization via Environment Variables and `.env`
- **Context:** Hardcoded database credentials in configuration files risk leaking sensitive secrets into Git version control.
- **Decision:** Externalize database credentials into a git-ignored `.env` file using environment variables (`DB_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), enforcing fail-fast `${DB_PASSWORD}` without default passwords.
- **Trade-off:** Developers must create `.env` before running locally; mitigated by providing `.env.example` and the `scripts/load-env.ps1` helper.

---

### ADR-003: Direct JDBC Probing for Health Verification
- **Context:** The health check endpoint requires verifying live PostgreSQL socket reachability before any ORM entities or repositories are introduced.
- **Decision:** Use raw JDBC directly via `javax.sql.DataSource` and `connection.isValid(2)` wrapped in `try-with-resources`.
- **Trade-off:** Bypasses Spring Data JPA abstractions, but provides zero-overhead, lightweight socket verification without premature entity modeling.

---

### ADR-004: HTTP 503 and Information Masking on Downstream Outage
- **Context:** Unconditional HTTP 200 responses or raw SQL exception strings leak database connection strings, passwords, and table structures during outages.
- **Decision:** Return HTTP 503 Service Unavailable when the database is DOWN, mask response payloads to safe `"DOWN"` statuses, and log full stack traces server-side via SLF4J.
- **Trade-off:** Clients cannot diagnose root causes directly from the JSON body, but protects infrastructure security and complies with RFC 9110 status semantics.

---

### ADR-005: HikariCP Fast-Fail Connection Timeout and Resilient Boot Policy
- **Context:** Default 30-second HikariCP connection timeouts caused long startup hangs and blocked health check requests when PostgreSQL was stopped.
- **Decision:** Configure `spring.datasource.hikari.initialization-fail-timeout: 0` and reduce `connection-timeout` from 30,000ms to 3,000ms.
- **Trade-off:** Startup when DB is down still waits ~3 seconds ([MEASURED] 3,016 ms) because Hibernate requests JDBC metadata, but cuts total startup freeze by 90% and enables 3-second fail-fast health responses.

---

### ADR-006: Apache Maven Wrapper (`only-script`) Pinned to 3.9.9
- **Context:** Developer environments may lack a pre-installed Apache Maven binary or run conflicting Maven versions.
- **Decision:** Check in a script-only (`only-script`) Maven 3.9.9 wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`) and track executable bit `100755` in Git.
- **Trade-off:** Initial build requires downloading the Maven distribution zip on first run, but eliminates binary `.jar` files from Git and guarantees reproducible builds across machines.

---

### ADR-007: Retention of Spring Boot 3.4.0 with Planned Upgrade to 4.1.x
- **Context:** Spring Boot 3.4.0 reached OSS End of Life on December 31, 2025; Spring Boot 4.1.x is the current stable generation supported through July 2027.
- **Decision:** Retain Spring Boot 3.4.0 for Phase 1 as an accepted known limitation; execute the upgrade to Spring Boot 4.1.x on a dedicated branch before starting Phase 2.
- **Trade-off:** Application runs on an unsupported OSS version during initial foundation setup, but avoids introducing framework-level breaking changes while stabilizing Phase 1.
