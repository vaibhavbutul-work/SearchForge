# SearchForge Docker Deployment Guide

This guide covers building and running the containerized SearchForge production stack (`postgres`, `backend`, `frontend`) using multi-stage Dockerfiles and `docker-compose.prod.yml`.

---

## 1. Container Architecture

| Service | Base Image (Runtime) | User | Port (Internal / Host) | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `postgres` | `postgres:16-alpine` | `postgres` | `5432` (internal only) | Persistent relational storage (`postgres_prod_data`) |
| `backend` | `eclipse-temurin:21-jre-jammy` | `searchforge` (`1001`) | `8080` (internal only) | Spring Boot search engine + ONNX Runtime (`glibc`) |
| `frontend` | `nginxinc/nginx-unprivileged:1.27-alpine` | `nginx` (`101`) | `8080` -> `${FRONTEND_PORT:-80}` | Static SPA + `/api/` & `/actuator/health` reverse proxy |

### Key Design Decisions
1. **Jammy (`glibc`) for Backend Runtime:** `com.microsoft.onnxruntime:onnxruntime` ships native C++ shared libraries built against `glibc`, not Alpine `musl`. Using `eclipse-temurin:21-jre-jammy` prevents `UnsatisfiedLinkError` during neural embedding initialization.
2. **Same-Origin Nginx Reverse Proxy:** The frontend container is built with `VITE_API_BASE_URL=""` and proxies `/api/` to `http://backend:8080`, forwarding `X-Real-IP` and `X-Forwarded-For` so `RateLimitingFilter` tracks client IPs accurately.
3. **Internal-Only Database & Backend:** Neither PostgreSQL (`5432`) nor the Spring Boot backend (`8080`) is published to host ports in `docker-compose.prod.yml`; only Nginx (`FRONTEND_PORT`) is exposed.

---

## 2. Quickstart Deployment

1. Prepare `.env` from `.env.example` and set strong `DB_PASSWORD` and `SEARCHFORGE_ADMIN_API_KEY` ($\ge 16$ chars).
2. Validate Compose configuration:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env config
   ```
3. Build and start the stack:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env up -d --build
   ```
4. If using ONNX embeddings (`SEMANTIC_EMBEDDING_MODEL_TYPE=onnx`), copy `all-MiniLM-L6-v2.onnx` into the `searchforge_models` volume or set `SEMANTIC_EMBEDDING_ALLOW_FALLBACK=true` for deterministic fallback mode.
5. Check container health:
   ```bash
   docker compose -f docker-compose.prod.yml ps
   curl -i http://localhost/api/v1/health
   ```
