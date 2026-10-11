<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-10.
  Scope: service README written while scaffolding the Spring Boot
  skeleton. 2026-10-11: status paragraph updated for the Requests
  entity/repository skeleton.
  Reviewed by: Ko-Khan (via pull request).
-->

# Order Service

Will own request creation, listing, acceptance, courier updates,
completion and expiry.

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned) · RabbitMQ
(shared broker).

Scaffold only for now — one actuator health endpoint, database and
broker connection wired, and the `Requests` entity (table `requests`)
with its repository; no endpoints or event publishing yet.

## Run

```sh
# One-time (and after any foc-contracts change): install the shared
# contracts library into your local Maven repository
cd ../foc-contracts && ./mvnw install && cd ../order-service

# Tests (in-memory H2, no PostgreSQL or RabbitMQ needed)
./mvnw test

# Run locally (needs PostgreSQL; health reports DOWN without RabbitMQ)
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Full stack (from the repo root; copy .env.example to .env first and
# set ORDER_DB_PASSWORD and RABBITMQ_PASSWORD)
docker compose up --build order-service
curl localhost:8088/actuator/health
```
