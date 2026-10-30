# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
# Scope: multi-stage build copied from order-service (repo-root build
# context, shared foc-contracts library built first per team decision
# D15); only the service name changed.
# Reviewed by: [pending]
# 2026-10-30, Claude Code (Fable 5), issue #251: comment only — the pom
# now depends on foc-contracts (the request.completed consumer), so the
# "not used yet" note from #250 is gone; no stage changed. Author
# review: [pending]

# Build context is the REPO ROOT (see compose.yaml) so the shared
# foc-contracts library can be built alongside the service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY foc-contracts/pom.xml foc-contracts/pom.xml
COPY foc-contracts/src foc-contracts/src
RUN mvn -q -B -f foc-contracts/pom.xml install -DskipTests
COPY rating-service/pom.xml rating-service/pom.xml
RUN mvn -q -B -f rating-service/pom.xml dependency:go-offline
COPY rating-service/src rating-service/src
RUN mvn -q -B -f rating-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/rating-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
