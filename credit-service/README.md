<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-09.
  Scope: README for the credit-service scaffold: what exists, how to
  build, test and run it. The design is docs/credit-service.md (team).
  2026-10-10, Claude Code (Opus 5.5): status and contents updated for
  the entities, repositories and the two stubbed interfaces.
  2026-10-10, Claude Code (Opus 5.5), PR #168: status, contents and
  configuration updated for the request event handler, the broker
  topology and the message converter.
  2026-10-11, Claude Code (Opus 5.5), issue #169: status and contents
  updated for the credit operations implementation; then for the
  amended V1 and the two configuration values.
  Author review: Ryan Ang, pending pull request review.
  2026-10-11, Claude Code (Fable 5), PR #176 Copilot review: the run
  section's service URL updated for the gateway cutover (#151) — the
  host port is unpublished; calls go via the gateway prefix.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# credit-service

Credit balances, reservation, transfer and history for FoC — the
design is [`docs/credit-service.md`](../docs/credit-service.md).

**Status: the credit operations, the event handler and the broker
topology.** The service starts, applies the schema, declares its queues
and verifies login tokens, and the credit logic is implemented and
tested, but nothing reaches it yet: there are no endpoints, event
listener or outbox publisher.

What is here:

- **Flyway V1** (`src/main/resources/db/migration/V1__baseline.sql`): the
  schema from the design doc's "Database" section, with the common
  pool's single row. Flyway owns the schema; Hibernate never changes it
  (`ddl-auto: none`).
- **Entities and repositories** (`entity/`, `repository/`): one JPA
  entity per table and a Spring Data repository for each.
- **Credit operations** (`service/CreditOperationsService`): get-or-create
  (the sign-up credits, once per user), reserve, transfer and release,
  each in one transaction under row locks, with the outcomes of the
  design doc's "What each operation answers". Each one that changes credits logs one line with
  the request, the users, the amount and the outcome, written after the
  transaction commits. The sign-up amount
  (`CREDIT_SIGN_UP_CREDITS`, default 5) and the months before earned
  credits expire (`CREDIT_EARNED_CREDIT_LIFETIME_MONTHS`, default 3) are
  configuration.
- **Reply outbox interface** (`service/ReplyOutbox`): enqueue a reply
  inside the caller's transaction. Its only implementation is a stub
  that throws `UnsupportedOperationException("not implemented")`.
- **JWT verification** (`security/`): every route except
  `/actuator/health` needs a bearer token signed with the shared
  `JWT_SECRET`; a missing or invalid one is a 401 problem+json.
- **Request event handler** (`service/RequestEventHandler`): applies
  one request event in one transaction, calling `CreditOperations` and,
  for a reservation, `ReplyOutbox`, which is still the stub: a
  reservation fails at the reply until the real outbox lands.
- **Broker topology** (`messaging/rabbitmq/`): at startup the service
  declares the `request-events` and `credit-events` exchanges and its
  own work, retry and dead-letter queues
  (`credit-service.request-events`, `.retry`, `.dlq`), bound to the four
  events it acts on. It therefore needs RabbitMQ to start. The retry
  delay (`CREDIT_RETRY_TTL_MS`, default 2000) is fixed into the retry
  queue when it is declared; changing it means deleting that queue.
- **Message converter**: a copy of notification-service's, which reads
  events by their `eventType`. A fractional reward is refused.

## Build and test

```sh
(cd ../foc-contracts && ./mvnw install)   # once, before the first build
./mvnw test                               # needs Docker (Testcontainers Postgres)
```

## Run

With the whole stack (from the repo root, after setting
`CREDIT_DB_PASSWORD` in `.env`):

```sh
docker compose up -d credit-service
```

Since the gateway cutover (#151) the service has no published host
port — it is reached through the gateway at
`http://localhost:${GATEWAY_PORT:-8090}/api/credit/**`
(`docs/gateway.md`). Its database stays on
`127.0.0.1:${CREDIT_DB_HOST_PORT:-5436}` for local tooling.

**If the service fails to start with a Flyway checksum mismatch for
version 1:** your `credit-db` volume was created before V1 was amended
(issue #169: slices reference their lot). It held no real data, so
recreate it. This deletes the credit database only:

```sh
docker compose rm -sf credit-service credit-db
docker volume rm foc_credit-db-data
docker compose up -d credit-service
```
