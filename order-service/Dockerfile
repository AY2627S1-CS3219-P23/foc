# ===== Build stage =====
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven configuration first for better layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the Spring Boot application
RUN mvn clean package -DskipTests


# ===== Runtime stage =====
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the generated JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Spring Boot default port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
