# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-09-18; revised 2026-09-19.
# Scope: multi-stage build scaffolded for issue #61 (M7: everything
# containerized). 2026-09-19: build context moved to the repo root and
# the shared foc-contracts library built first (issue #62, team
# decision D15).
# Reviewed by: Leong Wei Zhi (via pull request).

# Build context is the REPO ROOT (see compose.yaml) so the shared
# foc-contracts library can be built alongside the service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY foc-contracts/pom.xml foc-contracts/pom.xml
COPY foc-contracts/src foc-contracts/src
RUN mvn -q -B -f foc-contracts/pom.xml install -DskipTests
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -q -B -f notification-service/pom.xml dependency:go-offline
COPY notification-service/src notification-service/src
RUN mvn -q -B -f notification-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/notification-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
