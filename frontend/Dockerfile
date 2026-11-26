# ==============================================================================
# SearchForge Frontend - Multi-Stage Production Dockerfile
# ==============================================================================

# --- Stage 1: Build Stage ---
FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html vite.config.js ./
COPY public public
COPY src src

# Empty string configures relative /api/* paths through Nginx reverse proxy
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# --- Stage 2: Unprivileged Nginx Runtime ---
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --chown=101:101 nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder --chown=101:101 /build/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
