<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-18.
  Scope: service README written while scaffolding for issue #61;
  broker/foc-contracts notes added for issue #62 (2026-09-19).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Notification Service

Consumes order events from the broker, stores notifications, and pushes
them to users. Design: [`docs/notification-service.md`](../docs/notification-service.md).

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned, decision D3).

## Run

```sh
# One-time: install the shared contracts library (decision D15) to the
# local Maven repository — needed before the first build/test
(cd ../foc-contracts && ./mvnw install)

# Tests (uses in-memory H2 and no broker — no infrastructure needed)
./mvnw test

# Full stack (from the repo root; copy .env.example to .env first and
# set NOTIFICATION_DB_PASSWORD and RABBITMQ_PASSWORD)
docker compose up --build notification-service
```

Health check: `GET http://localhost:${NOTIFICATION_SERVICE_PORT}/actuator/health`.

## Current state

Scaffold (issue #61) plus broker infrastructure (issue #62): app
skeleton, health endpoint, the JPA schema — notification rows
(read/unread flag), processed event IDs (dedupe, D4), and last-applied
sequence per order (staleness, D5) — and the RabbitMQ topology: the
durable `order-events` fanout exchange (name from the shared
`foc-contracts` library, D15) and this service's durable work queue,
declared at startup (D12). Listener, retry/DLQ, REST API, push
gateway, and retention purge land in issues #63–#68.
