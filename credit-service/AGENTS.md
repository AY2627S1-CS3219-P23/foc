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
- **Entities mirror V1.** With `ddl-auto: none` nothing validates the
  mappings at startup; `EntityMappingTest` saves and reads each one back.
  A new column needs a migration, the entity field and that test.
- **Replace the `NotImplementedReplyOutbox` stub, don't extend it.** When
  the real `ReplyOutbox` bean lands, delete the stub (two beans of one
  interface fail injection) and `NotImplementedStubsTest`.
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
