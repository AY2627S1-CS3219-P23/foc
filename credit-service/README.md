<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-09.
  Scope: README for the credit-service scaffold: what exists, how to
  build, test and run it. The design is docs/credit-service.md (team).
  2026-10-10, Claude Code (Opus 5.5): status and contents updated for
  the entities, repositories and the two stubbed interfaces.
  Author review: Ryan Ang, pending pull request review.
-->

# credit-service

Credit balances, reservation, transfer and history for FoC — the
design is [`docs/credit-service.md`](../docs/credit-service.md).

**Status: scaffold only.** The service starts, applies the schema and
verifies login tokens, but has no endpoints, event listener, outbox
publisher or business logic yet.

What is here:

- **Flyway V1** (`src/main/resources/db/migration/V1__baseline.sql`): the
  schema from the design doc's "Database" section, with the common
  pool's single row. Flyway owns the schema; Hibernate never changes it
  (`ddl-auto: none`).
- **Entities and repositories** (`entity/`, `repository/`): one JPA
  entity per V1 table and an empty Spring Data repository for each.
- **Two interfaces to build against** (`service/`): `CreditOperations`
  (get-or-create, reserve, transfer, release) and `ReplyOutbox`
  (enqueue a reply inside the caller's transaction). Their only
  implementations are stubs that throw `UnsupportedOperationException("not implemented")`.
- **JWT verification** (`security/`): every route except
  `/actuator/health` needs a bearer token signed with the shared
  `JWT_SECRET`; a missing or invalid one is a 401 problem+json.
- **Messaging dependencies**: `spring-boot-starter-amqp` and
  `foc-contracts` are on the classpath and RabbitMQ is configured, for
  the saga's listener and outbox publisher.

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

The service is on `http://localhost:${CREDIT_SERVICE_PORT:-8088}`, and
its database on `127.0.0.1:${CREDIT_DB_HOST_PORT:-5436}` for local
tooling.
