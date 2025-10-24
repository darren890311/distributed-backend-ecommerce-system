# Stage 1: Build stage using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration
# This allows Docker to cache dependencies separately
COPY warehouse-service/pom.xml .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline

# Copy source code
COPY warehouse-service/src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage using lightweight JDK
FROM openjdk:17.0.1-jdk-slim

# Create non-root user for security
RUN groupadd -g 1000 spring && useradd -u 1000 -g spring -s /bin/sh -m spring


# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# JVM memory settings for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]