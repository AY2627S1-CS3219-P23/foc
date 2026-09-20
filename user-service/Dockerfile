# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-09-20.
# Scope: multi-stage build scaffolded for issue #84 following the
# notification-service pattern; foc-contracts stage omitted (no
# contracts dependency yet — shape chosen by Leong Wei Zhi via
# options Q&A).
# Reviewed by: Leong Wei Zhi (via pull request).

# Build context is the REPO ROOT (repo compose convention), even though
# this service has no shared-library build stage yet.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY user-service/pom.xml user-service/pom.xml
RUN mvn -q -B -f user-service/pom.xml dependency:go-offline
COPY user-service/src user-service/src
RUN mvn -q -B -f user-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/user-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
