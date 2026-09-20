<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20.
  Scope: service README written while scaffolding for issue #84.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# User Service

Will own accounts, auth, and profiles (User F1–F8): sign-up/OTP, login,
JWT issuance, account update/delete/recover, roles, admin management.
Backlog: issues #84–#98.

Spring Boot 4 · Java 21 · Maven.

Scaffold only for now — one actuator health endpoint, no database.
Compose/DB wiring lands with #85/#86.

## Run

```sh
# Tests (no infrastructure needed)
./mvnw test

# Run locally
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Container (from the repo root — build context is the repo root)
docker build -f user-service/Dockerfile .
```
