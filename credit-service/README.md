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
  2026-10-12, Claude Code (Opus 5.5), issue #170: status and contents
  updated for the reply outbox and the outbox publisher.
  2026-10-13, Claude Code (Opus 5.5), PR #188 review (Leong Wei Zhi):
  V2, the 30 s wait of a returned reply and the scheduler threads.
  2026-10-13, Claude Code (Opus 5.5), issue #172: status and contents
  updated for the read API, with how to call it.
  2026-10-14, Claude Code (Opus 5.5), PR #189 review (Leong Wei Zhi):
  merge resolution redone so the #170 and PR #188 text is kept.
  2026-10-14, Claude Code (Opus 5.5), issue #171: status and contents
  updated for the request event listener.
  2026-10-16, Claude Code (Opus 5.5), PR #190 review (Leong Wei Zhi):
  the listener's handling of a refused retry copy (team decisions).
  Same day, re-review: the confirm timeout's environment variable, and
  the listener paragraph reflowed.
  2026-10-16, Claude Code (Opus 5.5), PR #193 review (Leong Wei Zhi):
  the scheduler paragraph for the single daily and startup routine.
  2026-10-16, Claude Code (Opus 5.5), issue #173: status and contents
  updated for the expiry and redistribution scheduler.
  2026-10-17, Claude Code (Opus 5.5), issue #174: the admin API.
  Author review: Ryan Ang, pending pull request review.
  2026-10-11, Claude Code (Fable 5), PR #176 Copilot review: the run
  section's service URL updated for the gateway cutover (#151) — the
  host port is unpublished; calls go via the gateway prefix.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# credit-service

Credit balances, reservation, transfer and history for FoC — the
design is [`docs/credit-service.md`](../docs/credit-service.md).

**Status: the credit operations, the read API, the event listener and
handler, the reply outbox with its publisher, the expiry and
redistribution scheduler, and the broker topology.** The service
starts, applies the schema, declares its queues and verifies login
tokens. A signed-in user can read their balance and history. A request
event published to `request-events` now reserves, transfers or releases
credits, and a reservation's reply is published to `credit-events`.
Nothing publishes those events until the Order Service does. Earned
credits expire into the common pool daily, and the pool is shared out
monthly.

What is here:

- **Flyway V1** (`src/main/resources/db/migration/V1__baseline.sql`): the
  schema from the design doc's "Database" section, with the common
  pool's single row. Flyway owns the schema; Hibernate never changes it
  (`ddl-auto: none`).
- **Flyway V2** (`V2__outbox_next_attempt_at.sql`): adds
  `outbox_event.next_attempt_at`. It is applied on the next start of
  the service; an existing `credit-db` volume is kept.
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
- **Read API** (`controller/`, `service/CreditQueryService`): the two
  routes of the design doc's "Read API contract", for the token's user
  only.
  - `GET /credits/me`: `available`, `reserved` and their `total`.
  - `GET /credits/me/history`: a page of history rows, newest first,
    with the optional filters `minAmount`, `maxAmount`, `from` and `to`
    and the paging parameters `page` (from 0) and `size` (1 to 100,
    default 20).

  Either one creates the caller's account with its sign-up credits on
  first use, so neither answers 404. An invalid parameter is a 400
  problem+json whose `detail` names it.
- **Admin API** (`controller/AdminCreditController`,
  `service/PoolQueryService`), for `ADMIN` and `OWNER` only (403
  otherwise):
  - `GET /credits/admin/pool`: the common pool's balance, the number
    of accounts it is shared among, a preview of each one's share, the
    last redistribution and when the next is due.
  - `GET /credits/admin/redistributions`: the recorded months, newest
    first, with `page` and `size` as above.
- **Reply outbox** (`service/ReplyOutbox`, `ReplyOutboxService`):
  enqueue writes the reply as an `outbox_event` row inside the
  caller's transaction, so it commits with the balance change or not at
  all. A call outside a transaction is refused. The row is inserted,
  not saved, so a repeated event ID fails instead of overwriting the
  reply already stored.
- **Outbox publisher** (`service/OutboxPublisher`, with
  `messaging/rabbitmq/RabbitReplyPublisher`): every second, publishes
  the unsent replies that are due, oldest first and at most 100 in a
  poll, to the `credit-events` exchange, and marks each one sent once
  the broker confirmed it without returning it. A reply no queue took
  stays unsent and is due again 30 s later, with a warning that names
  its event ID, while the rest of the poll goes on; until then no poll
  reads it, so such replies cannot fill every batch. Any other failure
  (the broker unreachable, a nack, no confirm within 5 s) stops the
  poll until the next one. The interval, the batch, the 5 s confirm
  wait and the 30 s retry delay are under `credit.outbox.publisher` in
  `application.yaml`. For each returned reply the log also carries
  Spring's own "Returned message but no callback available" warning:
  the publisher registers no returns callback on the shared
  `RabbitTemplate`, which takes only one.
- **Expiry and redistribution** (`service/ExpiryAndRedistributionScheduler`,
  `LotExpiryService`, `RedistributionService`), in Singapore time:
  - Daily at 00:00, what remains in each lot past its expiry moves to
    the common pool with an `EXPIRY` history row, one transaction per
    account; an account that fails is logged and tried again the next
    day. Held credits are untouched until they are released.
  - Straight after that sweep, if the month has no recorded run, the
    pool is shared out (so on the 1st at 00:00): `floor(pool /
    accounts)` to every account and the remainder 1 credit each to
    accounts chosen at random, each share a new lot that expires the
    earned-credit lifetime after it is received. The month (the
    Singapore month) is recorded in `redistribution_run` even when the
    pool was empty, so a second run for it changes nothing. A run that
    fails records nothing, so the next day's run completes it.
  - The same routine runs at startup, so a month missed while the
    service was down is shared out then.
  - Off in tests (`credit.scheduler.enabled: false`).
- **Scheduler threads**: `spring.task.scheduling.pool.size` is 2, so
  an outbox poll waiting on the broker does not hold up another
  `@Scheduled` job.
- **JWT verification** (`security/`): every route except
  `/actuator/health` needs a bearer token signed with the shared
  `JWT_SECRET`; a missing or invalid one is a 401 problem+json.
- **Request event listener** (`messaging/rabbitmq/RequestEventsListener`):
  consumes the work queue with manual acknowledgement and settles each
  delivery on the handler's outcome. A handled event is acknowledged
  after its transaction commits; one that conflicts with its request's
  record is dead-lettered at once; any failure goes to the retry queue
  and is dead-lettered after `CREDIT_RETRY_MAX_ATTEMPTS` (default 5)
  attempts. If the broker returns the retry copy (no retry queue) the
  event is dead-lettered; if it nacks it or does not confirm it within
  `CREDIT_RETRY_CONFIRM_TIMEOUT_MS` (default 5000), the event is
  requeued after a pause of the same length. A message that cannot be
  read, such as a fractional reward, is dead-lettered on its first
  delivery. Each settled delivery logs its
  event type, event ID, request and outcome.
- **Request event handler** (`service/RequestEventHandler`): applies
  one request event in one transaction, calling `CreditOperations` and,
  for a reservation, `ReplyOutbox`.
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

To call the read API, log in through the gateway and pass the access
token:

```sh
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8090/api/credit/credits/me
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8090/api/credit/credits/me/history?minAmount=2&from=2026-10-01T00:00:00Z&size=10"
```

`from` and `to` are full instants with an offset. Send UTC (`Z`); a
`+08:00` offset must be written `%2B08:00` in the query string.

**If the service fails to start with a Flyway checksum mismatch for
version 1:** your `credit-db` volume was created before V1 was amended
(issue #169: slices reference their lot). It held no real data, so
recreate it. This deletes the credit database only:

```sh
docker compose rm -sf credit-service credit-db
docker volume rm foc_credit-db-data
docker compose up -d credit-service
```
