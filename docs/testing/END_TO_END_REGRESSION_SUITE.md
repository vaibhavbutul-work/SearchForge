# SearchForge End-to-End Regression Suite Guide

---

## 1. Test Categorization (JUnit 5 `@Tag` & Node Test Runner)

| Category | Scope | Execution Command |
| :--- | :--- | :--- |
| **Backend Unit Tests** | Fast isolated unit tests (excludes integration, performance, evaluation) | `cd backend && ./mvnw test -Dgroups="!integration & !performance & !evaluation"` |
| **Backend Integration & E2E** | `@Tag("integration")`: DB, Flyway, Crawler-to-Search, Security, Concurrency, Observability, Resilience | `cd backend && ./mvnw test -Dgroups=integration` |
| **Security Regression** | Auth RBAC, Rate Limit, Input Validation, SSRF, Secret Masking | `cd backend && ./mvnw test -Dtest="SecurityIntegrationTest,SecurityRegressionSuiteTest,SecretsAndConfigurationSecurityTest"` |
| **Performance & Quality** | `@Tag("performance")` & `@Tag("evaluation")`: Benchmarks & NDCG/MRR gates (not run as ordinary unit tests) | `cd backend && ./mvnw test -Dgroups="performance | evaluation"` |
| **Frontend Unit Tests** | `SearchModeSelector` & `SearchService` routing/normalization | `cd frontend && npm test` |
| **Frontend-Backend Live HTTP** | Real HTTP against running stack + error matrix (`400`/`401`/`403`/`429`/`500`/timeout) | `cd frontend && npm run test:integration` |
| **Full Regression Runner** | Combined integration + frontend runner | `.\scripts\run-regression.ps1 -IncludeLiveHttp` |

---

## 2. Coverage Matrix (All 20 Core Workflows)

| # | Workflow | Automated Verification Class / Script |
| :--- | :--- | :--- |
| 1 | Application startup | `ProductionConfigurationProfileTest`, `ObservabilityIntegrationTest` |
| 2 | Database connectivity | `FlywayMigrationTest`, `OperationalResilienceTest` |
| 3 | Authentication | `SecurityIntegrationTest`, `SecurityRegressionSuiteTest` |
| 4 | Authorization | `SecurityIntegrationTest`, `AdminSecurityIntegrationTest` |
| 5 | Rate limiting | `SecurityIntegrationTest`, `RateLimitingTest` |
| 6 | Crawler workflow | `CrawlingToSearchE2ETest`, `BackendIntegrationTest` |
| 7 | Document processing | `CrawlingToSearchE2ETest`, `HtmlParserTest` |
| 8 | Document indexing | `CrawlingToSearchE2ETest`, `BackendIntegrationTest` |
| 9 | BM25 search | `BackendIntegrationTest`, `CrawlingToSearchE2ETest` |
| 10 | Semantic search | `BackendIntegrationTest`, `CrawlingToSearchE2ETest` |
| 11 | Hybrid search | `BackendIntegrationTest`, `CrawlingToSearchE2ETest` |
| 12 | Query intelligence | `frontendBackend.integration.test.js` (`/autocomplete`, `/suggest`) |
| 13 | Pagination | `BackendIntegrationTest` (`limit` rank stability + `Pageable`) |
| 14 | Document updates | `BackendIntegrationTest`, `CrawlingToSearchE2ETest`, `ConsistencyConcurrencyRecoveryTest` |
| 15 | Document deletion | `BackendIntegrationTest`, `CrawlingToSearchE2ETest`, `ConsistencyConcurrencyRecoveryTest` |
| 16 | Index consistency | `ConsistencyConcurrencyRecoveryTest`, `IndexIntegrityCheckerTest` |
| 17 | Vector consistency | `ConsistencyConcurrencyRecoveryTest`, `BackendIntegrationTest` |
| 18 | Frontend search workflow | `frontendBackend.integration.test.js`, `smoke-test.ps1` |
| 19 | Error handling | `SecurityIntegrationTest`, `frontendBackend.integration.test.js` |
| 20 | Application restart & recovery | `CrawlingToSearchE2ETest` (Scenario H), `OperationalResilienceTest`, `ConsistencyConcurrencyRecoveryTest` |
