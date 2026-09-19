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
sequence per `(entityType, entityId)` (staleness, D5) — and the
RabbitMQ topology: the durable `order-events` fanout exchange (name
from the shared `foc-contracts` library, D15) and this service's
durable work queue, declared at startup (D12). The event-envelope
contract (issue #63) is in place: the `EventEnvelope` record and
canonical fixture live in `foc-contracts` (D15 amendment), and this
service's contract test (`EventEnvelopeContractTest`) locks in
tolerant-reader deserialization (unknown fields/types ignored, F1.3)
against Boot's auto-configured `ObjectMapper`.

The event pipeline (issue #64) is live: `OrderEventsListener` (the
broker adapter, D11) consumes the work queue with **manual ack after
the DB commit** (NFR1.1) through the Jackson 3 JSON converter wrapping
Boot's mapper (D15), and hands each envelope to the `EventProcessor`
port; `IdempotentEventProcessor` deduplicates by event ID (F2.1),
discards stale per-entity sequences (F2.4), and stores one
notification row per party (F1.2) in a single transaction — the code
follows a controller-service-repository layout (`service/`,
`repository/`, `entity/`; the REST controller arrives with #66).
Until #65's retry/DLQ topology, processing failures are requeued and
malformed messages dropped by the container's default error handler.

The STOMP push gateway (issue #67, backend half) is live: `/ws`
endpoint (SockJS fallback), JWT verified at the STOMP CONNECT
(`Authorization: Bearer`, HS256 against the shared `JWT_SECRET` —
required at startup; the token's `sub` is the platform user ID), and
each stored notification is pushed to `/user/queue/notifications`
after the processor's commit via a transactional event listener
(`NotificationDto`, payload parsed — the shape #66's REST list will
reuse). The frontend STOMP client is deferred until the SPA exists;
the provisional reconnect policy is recorded in the design doc's
"Session mechanics". Tests: unit tests drive the port directly on H2;
STOMP integration tests run real sessions on a random port (no
Docker); one Testcontainers test runs the full broker path
(auto-skips when Docker is unavailable). Retry/DLQ, REST API, and
retention purge land in issues #65, #66, #68.
