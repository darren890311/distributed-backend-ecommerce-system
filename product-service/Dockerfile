# Multi-stage build for Product Service
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy parent POM and OpenAPI spec
COPY pom.xml /build/pom.xml
COPY openapi.yaml /build/openapi.yaml

# Install parent POM
RUN mvn -f /build/pom.xml install -DskipTests -N

# Copy module POM
COPY product-service/pom.xml /build/product-service/pom.xml

# Download dependencies
WORKDIR /build/product-service
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY product-service/src /build/product-service/src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage - Amazon Corretto for ARM64 compatibility
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy JAR from build stage
COPY --from=build /build/product-service/target/*.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# JVM optimization flags
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

