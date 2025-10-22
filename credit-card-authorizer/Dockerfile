# Multi-stage build for credit-card-authorizer
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy parent POM and install it first
COPY pom.xml /build/pom.xml
COPY openapi.yaml /build/openapi.yaml

# Install parent POM to local Maven repository
RUN mvn -f /build/pom.xml install -DskipTests -N

# Copy module POM
COPY credit-card-authorizer/pom.xml /build/credit-card-authorizer/pom.xml

# Download dependencies
WORKDIR /build/credit-card-authorizer
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY credit-card-authorizer/src /build/credit-card-authorizer/src

# Build the module
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy JAR from build stage
COPY --from=build /build/credit-card-authorizer/target/*.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]