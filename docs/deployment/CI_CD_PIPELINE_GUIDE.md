# SearchForge CI/CD Pipeline Guide

---

## 1. Pipeline Architecture

SearchForge uses GitHub Actions ([.github/workflows/ci.yml](file:///d:/projects/.github/workflows/ci.yml)) to validate every push and pull request targeting `main` across four isolated jobs:

| Job | Trigger / Dependencies | Validations | Caching |
| :--- | :--- | :--- | :--- |
| **`backend-build-and-test`** | Parallel on push/PR | Java 21 compilation, full JUnit 5 suite (`./mvnw test`), Surefire report upload, fat-JAR packaging | Maven (`~/.m2`) + ONNX model (`~/.cache/searchforge`) |
| **`frontend-build-and-test`** | Parallel on push/PR | `npm ci`, Node test suite (`npm test -- --run`), production Vite build (`VITE_API_BASE_URL=""`) | npm (`frontend/package-lock.json`) |
| **`security-hygiene`** | Parallel on push/PR | Verifies `.env` / `frontend/.env` are not tracked by Git; runs `npm audit --omit=dev --audit-level=high` | npm |
| **`container-validation`** | `needs: [backend-build-and-test, frontend-build-and-test]` | Validates `docker-compose.prod.yml` schema and builds both multi-stage Docker images (`searchforge-backend:ci`, `searchforge-frontend:ci`) | Docker Buildx |

Documentation portal publishing remains separated in [.github/workflows/pages.yml](file:///d:/projects/.github/workflows/pages.yml).

---

## 2. Security & Governance Controls

1. **Minimal Token Permissions:** Top-level `permissions: contents: read` restricts the `GITHUB_TOKEN` to read-only repository checkout.
2. **No Secret Exposure:** CI tests run hermetically against H2 (`MODE=PostgreSQL`), and Compose validation uses `.env.example` placeholder values only.
3. **No Automatic External Deployment:** External server deployment is intentionally decoupled from CI and requires explicit manual/authorized release execution.
