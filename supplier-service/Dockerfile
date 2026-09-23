# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Sonnet 5), 2026-09-22.
# Scope: multi-stage build scaffolded following the user-service /
# notification-service pattern; foc-contracts stage omitted (no
# contracts dependency).
# Reviewed by: Ko-Khan (via pull request).

# Build context is the REPO ROOT (repo compose convention), even though
# this service has no shared-library build stage yet.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY supplier-service/pom.xml supplier-service/pom.xml
RUN mvn -q -B -f supplier-service/pom.xml dependency:go-offline
COPY supplier-service/src supplier-service/src
RUN mvn -q -B -f supplier-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/supplier-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
