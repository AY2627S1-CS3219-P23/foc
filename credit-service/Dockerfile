# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-10-09.
# Scope: multi-stage build scaffolded following the notification-service
# pattern, including its foc-contracts build stage (the service depends
# on the shared event contracts, D15).
# Author review: Ryan Ang, pending pull request review.

# Build context is the REPO ROOT (see compose.yaml) so the shared
# foc-contracts library can be built alongside the service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY foc-contracts/pom.xml foc-contracts/pom.xml
COPY foc-contracts/src foc-contracts/src
RUN mvn -q -B -f foc-contracts/pom.xml install -DskipTests
COPY credit-service/pom.xml credit-service/pom.xml
RUN mvn -q -B -f credit-service/pom.xml dependency:go-offline
COPY credit-service/src credit-service/src
RUN mvn -q -B -f credit-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/credit-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
