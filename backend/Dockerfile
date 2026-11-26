# ==============================================================================
# SearchForge Backend - Multi-Stage Production Dockerfile
# Uses Jammy (glibc) for Microsoft ONNX Runtime native library compatibility
# ==============================================================================

# --- Stage 1: Build Stage ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Copy Maven wrapper and pom.xml first for optimal dependency layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline --batch-mode --no-transfer-progress

# Copy source code and build executable fat-JAR
COPY src src
RUN ./mvnw package -DskipTests --batch-mode --no-transfer-progress

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install curl for container health checks and clean apt cache
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create unprivileged user and group
RUN groupadd --gid 1001 searchforge \
    && useradd --uid 1001 --gid searchforge --shell /bin/false --create-home searchforge

WORKDIR /app

# Prepare ONNX model directory owned by non-root user
RUN mkdir -p /app/models && chown -R searchforge:searchforge /app

# Copy compiled fat-JAR from builder stage
COPY --from=builder --chown=searchforge:searchforge /build/target/*.jar /app/searchforge.jar

USER searchforge:searchforge

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE="prod"

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/searchforge.jar"]
