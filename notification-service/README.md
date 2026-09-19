<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-18.
  Scope: service README written while scaffolding for issue #61.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Notification Service

Consumes order events from the broker, stores notifications, and pushes
them to users. Design: [`docs/notification-service.md`](../docs/notification-service.md).

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned, decision D3).

## Run

```sh
# Tests (uses in-memory H2, no database needed)
./mvnw test

# Full stack (from the repo root; copy .env.example to .env first and
# set NOTIFICATION_DB_PASSWORD)
docker compose up --build notification-service
```

Health check: `GET http://localhost:${NOTIFICATION_SERVICE_PORT}/actuator/health`.

## Current state

Scaffold only (issue #61): app skeleton, health endpoint, and the JPA
schema — notification rows (read/unread flag), processed event IDs
(dedupe, D4), and last-applied sequence per order (staleness, D5) in
one schema so later processing commits atomically. Broker topology,
listener, REST API, push gateway, and retention purge land in issues
#62–#68.
