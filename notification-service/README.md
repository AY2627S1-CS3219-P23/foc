<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-18.
  Scope: service README written while scaffolding for issue #61;
  broker/foc-contracts notes added for issue #62 (2026-09-19).
  2026-09-19: current-state narrative and migration notes updated for
  the Method-B refactor (author decisions D16-D19).
  2026-09-20, issue #65: retry/DLQ narrative and migration note added
  (author decisions D20-D21; values per team decisions D6/D7).
  Same day: order→request event vocabulary rename applied (author
  decision D22) — exchange/queue/routing-key names and the migration
  note updated.
  2026-09-20, issue #68: retention purge scheduler narrative added
  (team decision D9; author decision on the NOTIF_PURGE_CRON env var).
  2026-09-20, issue #69: ArchUnit broker-isolation enforcement
  narrative added (team decision D11).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Notification Service

Consumes request events from the broker, stores notifications, and pushes
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

Scaffold (issue #61) plus broker infrastructure (issue #62), reworked
2026-09-19 by the **Method-B refactor** (typed event contracts,
decisions D16–D19): app skeleton, health endpoint, the JPA schema —
notification rows (read/unread flag) and processed event IDs (dedupe,
D4) — and the RabbitMQ topology: the durable `request-events`
**topic** exchange (name from the shared `foc-contracts` library,
D15/D16) and this service's durable work queue bound with `request.#`,
declared at startup (D12). The event contracts are typed: one flat
record per request-lifecycle fact plus the `EventTypeRegistry` live in
`foc-contracts` (see its README's **Event conventions** section), each
with a canonical fixture; this service's contract test
(`DomainEventContractTest`) binds every cataloged fixture and locks in
tolerant-reader deserialization of unknown *fields* (F1.3) against
Boot's auto-configured `ObjectMapper`.

The event pipeline (issue #64) is live: `DomainEventsListener` (the
broker adapter, D11 — event-type-agnostic; only its queue reference
is domain-specific) consumes the work queue with **manual ack after
the DB commit** (NFR1.1) through `DomainEventMessageConverter`, which
dispatches on the body's canonical `eventType` via the registry (D18),
and hands each typed event to the `EventProcessor` port;
`IdempotentEventProcessor` deduplicates by event ID (F2.1) and stores
one notification row per party (F1.2) — payload = the event's
business fields — in a single transaction. The former per-entity
sequence stale-discard was removed by author decision (D19; F2.4
retired). The code follows a controller-service-repository layout
(`service/`, `repository/`, `entity/`; the REST controller arrives
with #66).

Failure handling (issue #65, decisions D6/D7/D20/D21) is live: a
processing failure is republished to the durable TTL **retry queue**
(`NOTIFICATION_RETRY_TTL_MS`, default 10 s) — a confirmed publish;
the original delivery is acked only after the broker accepts the
copy — and returns to the work queue for another attempt; after `NOTIFICATION_RETRY_MAX_ATTEMPTS`
(default 3) total attempts — counted via a listener-stamped
`x-retry-attempts` header, because RabbitMQ 4 resets its own `x-death`
count on client republish — the event is nacked to the durable **DLQ**
via the work queue's dead-letter leg. Malformed or unknown-type messages skip the
retry queue and dead-letter intact on first rejection (D21),
replayable after a consumer upgrade. Both legs use the default
exchange as their dead-letter exchange (D20). Inspect and re-publish
via the RabbitMQ management UI (`RABBITMQ_MANAGEMENT_PORT`).

**Migration (dev volumes from before the refactor, #65, or the D22
rename):** the old fanout exchange makes the topic declaration fail
(`PRECONDITION_FAILED`); so does a work queue declared before #65,
since its new dead-letter arguments cannot be added to an existing
queue; after the D22 order→request rename, the old `order-events`
exchange and `notification-service.order-events*` queues declare
nothing anew — they just linger as orphans, stranding any messages
still parked in them (deleting them in the management UI also works);
and the old NOT NULL `entity_type`/`entity_id` columns reject inserts
(`ddl-auto: update` never drops columns). Reset both volumes once:

```sh
docker compose down rabbitmq notification-db
docker volume rm foc_rabbitmq-data foc_notification-db-data
docker compose up -d rabbitmq notification-db
```

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
Docker); one Testcontainers test runs the full broker path including
retry recovery and dead-lettering (auto-skips when Docker is
unavailable).

The retention purge scheduler (issue #68, decision D9) is live:
`RetentionPurgeScheduler` hard-deletes notification rows once
`created_at` is older than `NOTIF_RETENTION_DAYS` (default 30), on a
cron schedule (`NOTIF_PURGE_CRON`, default daily at 03:00) — both
env-overridable and independent of each other. It runs on the app's
sole `TaskScheduler` bean (the STOMP heartbeat scheduler in
`WebSocketStompConfig`; Boot backs off its own default once a
user-defined one exists), and imports nothing broker-related (D11).

The D11 broker-isolation boundary is now enforced in the build (issue
#69): `ArchitectureTest` (ArchUnit) asserts that no package outside
`messaging.rabbitmq` depends on `org.springframework.amqp` or
`com.rabbitmq` types, and runs with the normal `./mvnw test` suite —
a future PR that leaks a broker type into business logic fails CI
instead of just review.

REST API lands in issue #66.
