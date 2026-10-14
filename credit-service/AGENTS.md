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
  2026-10-13, Claude Code (Opus 5.5), issue #172: the package map and
  invariants updated for the read API.
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
| `config/` | `SecurityConfig`: CORS, health open, everything else needs a valid JWT |
| `controller/` | `CreditController` (the two read routes and the checks behind the contract's 400s), `ApiExceptionHandler` (400 problem+json), `InvalidQueryParameterException` |
| `dto/` | The read API's bodies: `BalanceResponse`, `HistoryEntryResponse`, and `PageResponse`, a copy of supplier-service's shape |
| `security/` | `JwtVerifier` (HS256, shared `JWT_SECRET`), `JwtAuthenticationFilter`, 401/403 problem+json handlers |
| `entity/` | One JPA entity per table (`CreditAccount`, `Reserved`, `CreditReservationSlice`, `CreditLot`, `CreditHistoryEntry`, `CommonPool`, `RedistributionRun`, `OutboxEvent`) and the `ReservationStatus` / `CreditHistoryType` enums. No logic |
| `repository/` | One `JpaRepository` per entity, with the locking and insert-once queries the credit operations use |
| `service/` | `CreditOperations` and its implementation `CreditOperationsService`, `ReplyOutbox` (interface) and its `NotImplementedReplyOutbox` stub, which throws, `ReserveResult` / `SettleResult`, `RequestEventHandler`, which owns the transaction for one request event, and `CreditQueryService` with `HistoryFilter`, the two reads behind the REST API |
| `messaging/rabbitmq/` | The only package that imports broker types: `RabbitMqTopology` (queues, the four bindings, both exchanges), `RabbitMqTopologyInitializer`, and `DomainEventMessageConverter`, a copy of notification-service's |

No listener, outbox publisher or scheduler yet. The two interfaces are
the seams the features are built against, and the handler already
calls them; `ReplyOutbox` is still a stub.

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
- **`CreditOperationsServiceTest` and
  `RequestEventHandlerCreditOperationsTest` commit**, so they never clean
  up and every test uses fresh IDs. The first one's after-each checks read the whole
  database: a test elsewhere that commits credit rows must keep the
  doc's invariants too.
- **No shared code except `foc-contracts`** (root `AGENTS.md`, D15). The
  JWT classes are copies of supplier-service's, and the message converter
  and topology initializer copies of notification-service's, on purpose.
- **Broker types stay in `messaging/rabbitmq/`.** `service/` imports
  none; the handler returns an outcome and the listener settles the
  delivery.
- **What each operation answers is specified** in the design doc ("What
  each operation answers"); the handler depends on it.
- **The REST API only reads, and only the caller's own data.** The user
  is the token's subject (`Authentication.getName()`); no route takes a
  user ID. The one write a read causes is get-or-create (D6). Every
  balance change arrives as an event.
- **The read API is specified** in the design doc's "Read API
  contract": routes, fields, order, bounds and each 400. A change to
  any of them is a design change, so update the doc with it.
  `CreditControllerTest` follows the contract row by row.
- **`page` and `size` are plain integers, never Spring's `Pageable`**,
  which turns a negative page into 0, caps an oversized size silently
  and admits a `sort` parameter: the contract's 400s would never
  happen. `from` and `to` bind as `OffsetDateTime`, so a date alone or
  an instant with no offset is refused.
- **`CreditControllerTest` is `@Transactional` and commits nothing.** Its
  history rows are made up for the filters and do not add up to a
  balance; committed, they would fail the whole-database checks below.
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
