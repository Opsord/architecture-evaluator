# Stage 1: Build the Spring Boot fat JAR with Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml for dependency resolution
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code and package application
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Lightweight non-root runtime with JRE 21
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create non-root user and group
RUN addgroup -S app && adduser -S app -G app

# Copy packaged JAR from builder stage
COPY --chown=app:app --from=builder /app/target/architecture_evaluator_backend-*.jar /app/app.jar

USER app

# Expose the port that Spring Boot uses (default 8080)
EXPOSE 8080

# Health check using 127.0.0.1 to avoid Alpine IPv6 resolution issues
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Run the application
CMD ["java", "-jar", "/app/app.jar"]
