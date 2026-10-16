# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-10-10.
# Scope: multi-stage build rewritten following the notification-service
# pattern (repo-root build context, shared foc-contracts library built
# first per team decision D15), replacing the earlier placeholder whose
# context was the service folder.
# Reviewed by: Ko-Khan (via pull request).

# Build context is the REPO ROOT (see compose.yaml) so the shared
# foc-contracts library can be built alongside the service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY foc-contracts/pom.xml foc-contracts/pom.xml
COPY foc-contracts/src foc-contracts/src
RUN mvn -q -B -f foc-contracts/pom.xml install -DskipTests
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -q -B -f order-service/pom.xml dependency:go-offline
COPY order-service/src order-service/src
RUN mvn -q -B -f order-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
