<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-09.
  Scope: agent guide for the credit-service scaffold, written from the
  code and docs/credit-service.md; the design decisions it cites are
  the team's.
  2026-10-10, Claude Code (Opus 5.5): package map and invariants extended
  for the entities, repositories and the two stubbed interfaces.
  2026-10-10, Claude Code (Opus 5.5), PR #168: the package map and
  invariants updated for the request event handler, the broker
  topology and the message converter.
  2026-10-11, Claude Code (Opus 5.5), issue #169: the package map and
  invariants updated for the credit operations implementation; then
  for slices referencing their lot (written into V1) and the record
  insert moving to the repository.
  2026-10-12, Claude Code (Opus 5.5), issue #170: the package map and
  invariants updated for the reply outbox and the outbox publisher.
  2026-10-13, Claude Code (Opus 5.5), PR #188 review (Leong Wei Zhi):
  invariants updated for V2, the due-replies poll, the outbox insert
  and the returns callback; "scheduler" narrowed to the one still
  missing (Copilot review).
  2026-10-13, Claude Code (Opus 5.5), issue #172: the package map and
  invariants updated for the read API.
  2026-10-14, Claude Code (Opus 5.5), PR #189 review (Leong Wei Zhi):
  the CreditControllerTest invariant names its two committing cases,
  per the reviewer's comment; the package map and status rebuilt on
  main's after a bad merge resolution.
  2026-10-14, Claude Code (Opus 5.5), issue #171: the package map,
  status and invariants updated for the request event listener.
  2026-10-16, Claude Code (Opus 5.5), PR #190 review (Leong Wei Zhi):
  the listener invariant updated for the team's decisions on a refused
  retry copy, and the exhaustive outcome switch. Re-review: "never
  dropped" narrowed to a missing dead-letter queue.
  2026-10-16, Claude Code (Opus 5.5), issue #173: the package map,
  status and invariants updated for the expiry and redistribution
  scheduler.
  2026-10-16, Claude Code (Opus 5.5), PR #193 review (Leong Wei Zhi):
  the single-routine invariant and EarnedCreditLifetime in the map.
  2026-10-17, Claude Code (Opus 5.5), issue #174: the admin routes in
  the package map and the read-only invariant.
  2026-10-17, Claude Code (Opus 5.5), issue #196: the expiries route in
  the package map.
  PR #210 review (Leong Wei Zhi): the CreditControllerTest invariant
  counts three first-read cases.
  Author review: Ryan Ang, pending pull request review.
-->

# credit-service/ — Agent Guide

Java 21 + Spring Boot 4 service for credit balances, reservation,
transfer and history. Read the root `AGENTS.md` first (architecture,
course constraints, AI usage policy). The design authority is
[`docs/credit-service.md`](../docs/credit-service.md): cite its decision
IDs (D1–D12) when touching related code.

## Package map (`src/main/java/foc/credit/`)

| Package | Contents |
| --- | --- |
| `config/` | `SecurityConfig`: CORS, health open, `/credits/admin/**` for `ADMIN` and `OWNER`, everything else needs a valid JWT |
| `controller/` | `CreditController` (the three read routes and the checks behind the contract's 400s), `ApiExceptionHandler` (400 problem+json), `InvalidQueryParameterException`, and `AdminCreditController` (the pool summary and the redistribution runs) |
| `dto/` | The read API's bodies: `BalanceResponse`, `HistoryEntryResponse`, `ExpiriesResponse`, `PoolSummaryResponse`, `RedistributionRunResponse`, and `PageResponse`, a copy of supplier-service's shape |
| `security/` | `JwtVerifier` (HS256, shared `JWT_SECRET`), `JwtAuthenticationFilter`, 401/403 problem+json handlers |
| `entity/` | One JPA entity per table (`CreditAccount`, `Reserved`, `CreditReservationSlice`, `CreditLot`, `CreditHistoryEntry`, `CommonPool`, `RedistributionRun`, `OutboxEvent`) and the `ReservationStatus` / `CreditHistoryType` enums. No logic |
| `repository/` | One `JpaRepository` per entity, with the locking and insert-once queries the credit operations use |
| `service/` | `CreditOperations` and its implementation `CreditOperationsService`, `ReplyOutbox` and its implementation `ReplyOutboxService`, `OutboxPublisher` (the scheduled poll) and the `ReplyPublisher` interface it sends through, `ReserveResult` / `SettleResult`, `RequestEventHandler`, which owns the transaction for one request event, `CreditQueryService` with `HistoryFilter`, the two reads behind the REST API, `PoolQueryService`, the admin routes' reads, and `ExpiryAndRedistributionScheduler` with `LotExpiryService` and `RedistributionService` (D12), and `EarnedCreditLifetime`, the earned-credit expiry both the transfer and the redistribution use (D4) |
| `messaging/rabbitmq/` | The only package that imports broker types: `RabbitMqTopology` (queues, the four bindings, both exchanges), `RabbitMqTopologyInitializer`, `RabbitReplyPublisher` (the `ReplyPublisher` implementation: mandatory publish, correlated confirm), `RequestEventsListener` (settles each request event on the handler's outcome: ack, dead-letter, or a confirmed republish to the retry queue), and `DomainEventMessageConverter`, a copy of notification-service's |

## Invariants — do not break

- **Flyway owns the schema** (`db/migration`, `ddl-auto: none`). Change it
  with a new `V<n>__*.sql`, never by editing an applied migration. V1 was
  amended once, in the #169 change, before the service had run anywhere
  but the tests; from here on it is fixed.
- **The schema follows the design doc's "Database" section**: a schema
  change is a design change, so update the doc with it.
- **Entities mirror the migrations** (V1, and V2 for
  `outbox_event.next_attempt_at`). With `ddl-auto: none` nothing validates the
  mappings at startup; `EntityMappingTest` saves and reads each one back.
  A new column needs a migration, the entity field and that test.
- **A reply is enqueued inside the transaction that owes it.**
  `ReplyOutboxService.enqueue` is `MANDATORY`: it joins the caller's
  transaction and refuses a call outside one.
- **The stored payload is the wire body.** The publisher sends
  `outbox_event.payload` as it is, so whatever writes a row writes the
  JSON a consumer reads, `eventType` included.
- **The outbox publisher marks a reply sent only on `SENT`.** Anything
  else leaves the row unsent. Only a returned reply (`NOT_SENT`) lets
  the poll go on; a nack, a missing confirm or an unreachable broker
  (`BROKER_UNAVAILABLE`) stops it. Each reply is marked in its own
  transaction, never one around the batch. It polls on a fixed delay,
  not a fixed rate, so one poll never overlaps the next.
- **The poll reads only the replies that are due.** A returned reply
  gets `next_attempt_at` = now + `unroutable-retry-delay-ms` (30 s) and
  `OutboxEventRepository.findDue` leaves it out until then. Do not go
  back to reading every unsent row: replies no queue takes would fill
  every batch and nothing behind them would be published (PR #188).
  `BROKER_UNAVAILABLE` sets no delay; those replies are due on the
  next poll.
- **A reply is inserted, never saved.** `OutboxEventRepository.save`
  merges: it reads the row first, and for an event ID already stored
  would set a published reply back to unsent. `insert` fails on the
  primary key instead. `save` is for the publisher's updates only.
- **`RabbitReplyPublisher` registers no returns callback.** The
  `RabbitTemplate` is the application's shared one and takes a single
  returns callback; a return is read from the `CorrelationData`
  without it. The template's "Returned message but no callback
  available" warning is the accepted cost: do not add a callback to
  silence it.
- **The publisher is off in the `test` profile**
  (`credit.outbox.publisher.enabled: false`), as nothing there runs a
  broker; a test that needs it turns it on against Testcontainers.
- **A request's record is inserted, never saved.** `ReservedRepository.save`
  merges, and would overwrite the record a concurrent first delivery just
  committed; use `ReservedRepository.insert`, so the second delivery fails
  on the primary key and is retried.
- **A lot is never deleted.** A drained lot stays at `remaining = 0`
  because its slices reference it and a release refills it. Any query
  that spends or expires lots must filter `remaining > 0`.
- **Lots change only under their owner's account lock**, and locks are
  taken in the design doc's order (D2): reservation, accounts by
  ascending user ID, then the common pool.
- **`CreditOperationsServiceTest`,
  `RequestEventHandlerCreditOperationsTest` and
  `RequestEventsListenerIntegrationTest` commit**, so they never clean
  up and every test uses fresh IDs. The first one's after-each checks read the whole
  database: a test elsewhere that commits credit rows must keep the
  doc's invariants too.
- **No shared code except `foc-contracts`** (root `AGENTS.md`, D15). The
  JWT classes are copies of supplier-service's, and the message converter
  and topology initializer copies of notification-service's, on purpose.
- **Broker types stay in `messaging/rabbitmq/`.** `service/` imports
  none; the handler returns an outcome and the listener settles the
  delivery.
- **The listener acknowledges only after the handler returns**, so the
  transaction has committed first (D11). `HANDLED` is acked,
  `INVALID_STATE` nacked without requeue (straight to the DLQ), and any
  exception republished to the retry queue until
  `credit.rabbitmq.retry.max-attempts`, then dead-lettered. Attempts
  are counted in the listener's `x-retry-attempts` header, never
  `x-death`, which RabbitMQ 4 resets on every client republish. The
  original is acked only once the retry copy is confirmed and not
  returned. A returned copy (no retry queue) dead-letters the original;
  any other failure to publish the copy requeues it after a pause of
  `credit.rabbitmq.retry.confirm-timeout-ms` (design doc failure
  table). Never left unacked, never requeued in a tight loop, and
  dropped only if the dead-letter queue itself is missing (the broker
  discards a dead-letter it cannot route). The three dead-letters the listener decides are logged at
  ERROR. The outcome `switch` is an expression so that a new `Outcome`
  does not compile until it is settled: keep it one.
- **The expiry and redistribution runs keep the D2 lock order.** Expiry
  is one transaction per account: the owner's account, then their lots
  read again under that lock, then the pool. Redistribution locks every
  account with `CreditAccountRepository.findAllForUpdate`, then the
  pool. That query orders by `user_id collate "C"` so it matches the
  Java `String.compareTo` order the transfer locks two accounts in;
  do not drop the collation.
- **A month is redistributed once.** `RedistributionService` checks
  `redistribution_run` before and after taking its locks, and inserts
  the row (`RedistributionRunRepository.insert`, never `save`), so the
  primary key stops a run that overlaps. An empty pool is still
  recorded (team decision).
- **One routine runs both jobs** (`ExpiryAndRedistributionScheduler.run`,
  D12): daily at 00:00 and at startup, the sweep, then the month's
  redistribution if it is missing. It is also the retry for a failed
  run, so do not add a second schedule for either job, and keep it from
  throwing: an exception at startup stops the service starting.
- **The scheduler is off in the `test` profile**
  (`credit.scheduler.enabled: false`), so a test context does not run
  the startup routine; tests call the two services with their own
  instants and periods. `ExpiryAndRedistributionTest` commits and its
  redistributions share the pool out to every account in the test
  database, so its assertions are about changes, not totals.
- **What each operation answers is specified** in the design doc ("What
  each operation answers"); the handler depends on it.
- **The REST API only reads, and only the caller's own data.** The user
  is the token's subject (`Authentication.getName()`); no route takes a
  user ID. The one write a read causes is get-or-create (D6). Every
  balance change arrives as an event. The one exception to "own data"
  is `/credits/admin/**`, gated to `ADMIN` and `OWNER` in
  `SecurityConfig`: it reads the common pool and the redistribution
  runs, never a user's rows, and writes nothing.
- **The read API is specified** in the design doc's "Read API
  contract": routes, fields, order, bounds and each 400. A change to
  any of them is a design change, so update the doc with it.
  `CreditControllerTest` follows the contract row by row.
- **`page` and `size` are plain integers, never Spring's `Pageable`**,
  which turns a negative page into 0, caps an oversized size silently
  and admits a `sort` parameter: the contract's 400s would never
  happen. `from` and `to` bind as `OffsetDateTime`, so a date alone or
  an instant with no offset is refused.
- **`CreditControllerTest` commits nothing but a provisioned account.**
  It is `@Transactional`: its history rows are made up for the filters
  and do not add up to a balance, so committed they would fail the
  whole-database checks below. The three first-read cases marked
  `Propagation.NOT_SUPPORTED` are the exception: they commit, as a real
  first request does, and what they commit keeps every invariant. A new
  `NOT_SUPPORTED` case must write nothing else.
- **Amounts are integers** (Credit F1.2); balances never go negative
  (`CHECK` constraints back this up).

## Build & test

```sh
(cd ../foc-contracts && ./mvnw install)   # once, before the first build
./mvnw test                               # Testcontainers Postgres: needs Docker
```

The tests run on the `test` profile (`src/test/resources/application-test.yaml`
over the main config) against a real Postgres, so Flyway's V1 runs as in
production.
