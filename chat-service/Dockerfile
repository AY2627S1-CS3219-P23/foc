# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
# Scope: multi-stage build copied from supplier-service's (repo-root
# build context, no foc-contracts stage: chat-service uses no event
# contracts); only the service name changed.
# Author review: Ko-Khan, pending pull request review.

# Build context is the REPO ROOT (see compose.yaml), the repo convention.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY chat-service/pom.xml chat-service/pom.xml
RUN mvn -q -B -f chat-service/pom.xml dependency:go-offline
COPY chat-service/src chat-service/src
RUN mvn -q -B -f chat-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/chat-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
