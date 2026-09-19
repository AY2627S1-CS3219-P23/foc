# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-09-18.
# Scope: multi-stage build scaffolded for issue #61 (M7: everything
# containerized).
# Reviewed by: Leong Wei Zhi (via pull request).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
