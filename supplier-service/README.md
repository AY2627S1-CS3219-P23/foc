<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Sonnet 5), 2026-09-22.
  Scope: service README written while scaffolding the Spring Boot
  skeleton.
  Reviewed by: Ko-Khan (via pull request).
-->

# Supplier Service

Will own vendor/landmark CRUD (admin), browsing, search and filtering.

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned).

Scaffold only for now — one actuator health endpoint, database wired
but no entities/endpoints yet.

## Run

```sh
# Tests
./mvnw test

# Run locally
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Full stack (from the repo root; copy .env.example to .env first and
# set SUPPLIER_DB_PASSWORD)
docker compose up --build supplier-service
```
