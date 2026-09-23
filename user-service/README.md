<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20.
  Scope: service README written while scaffolding for issue #84.
  2026-09-23, Claude Code (Opus 5.5): status and test instructions updated
  for the first-owner bootstrap (issue #97, PR #126 review).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# User Service

Will own accounts, auth, and profiles (User F1–F8): sign-up/OTP, login,
JWT issuance, account update/delete/recover, roles, admin management.
Backlog: issues #84–#98.

Spring Boot 4 · Java 21 · Maven.

Currently: actuator health endpoint and `POST /auth/setup-owner` (first
OWNER bootstrap, #97), backed by Postgres via Spring Data JPA. There is
no production datasource or compose/DB wiring yet (#85/#86), so
`spring-boot:run` fails at startup until that lands; tests supply their
own database.

## Run

```sh
# Tests (need a running Docker daemon: Testcontainers starts Postgres)
./mvnw test

# Run locally
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Container (from the repo root — build context is the repo root)
docker build -f user-service/Dockerfile .
```
