# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-09-20.
# Scope: multi-stage build scaffolded for issue #84 following the
# notification-service pattern; foc-contracts stage omitted (no
# contracts dependency yet — shape chosen by Leong Wei Zhi via
# options Q&A).
# Reviewed by: Leong Wei Zhi (via pull request).
# 2026-10-28, Claude Code (Opus 5.5), issue #267: the foc-contracts stage,
# copied from order-service's, now that the service publishes user
# events. Author review: Ryan Ang, pending pull request review.

# Build context is the REPO ROOT (see compose.yaml) so the shared
# foc-contracts library can be built alongside the service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY foc-contracts/pom.xml foc-contracts/pom.xml
COPY foc-contracts/src foc-contracts/src
RUN mvn -q -B -f foc-contracts/pom.xml install -DskipTests
COPY user-service/pom.xml user-service/pom.xml
RUN mvn -q -B -f user-service/pom.xml dependency:go-offline
COPY user-service/src user-service/src
RUN mvn -q -B -f user-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/user-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
