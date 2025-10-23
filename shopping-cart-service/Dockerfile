# Multi-stage build for Shopping Cart Service
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy parent POM and OpenAPI spec
COPY pom.xml /build/pom.xml
COPY openapi.yaml /build/openapi.yaml

# Install parent POM
RUN mvn -f /build/pom.xml install -DskipTests -N

# Copy module POM
COPY shopping-cart-service/pom.xml /build/shopping-cart-service/pom.xml

# Download dependencies
WORKDIR /build/shopping-cart-service
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY shopping-cart-service/src /build/shopping-cart-service/src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy JAR from build stage
COPY --from=build /build/shopping-cart-service/target/*.jar app.jar

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

