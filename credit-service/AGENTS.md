<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-09.
  Scope: agent guide for the credit-service scaffold, written from the
  code and docs/credit-service.md; the design decisions it cites are
  the team's.
  2026-10-10, Claude Code (Opus 5.5): package map and invariants extended
  for the entities, repositories and the two stubbed interfaces.
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
| `security/` | `JwtVerifier` (HS256, shared `JWT_SECRET`), `JwtAuthenticationFilter`, 401/403 problem+json handlers |
| `entity/` | One JPA entity per V1 table (`CreditAccount`, `Reserved`, `CreditReservationSlice`, `CreditLot`, `CreditHistoryEntry`, `CommonPool`, `RedistributionRun`, `OutboxEvent`) and the `ReservationStatus` / `CreditHistoryType` enums. No logic |
| `repository/` | One empty `JpaRepository` per entity |
| `service/` | `CreditOperations` and `ReplyOutbox` (interfaces), `ReserveResult`, and the `NotImplemented*` stub beans, which throw |

No controllers, listener, outbox publisher or scheduler yet, and no
business logic: the two interfaces are the seams the features are built
against.

## Invariants — do not break

- **Flyway owns the schema** (`db/migration`, `ddl-auto: none`). Change it
  with a new `V<n>__*.sql`, never by editing an applied migration.
- **The schema follows the design doc's "Database" section**: a schema
  change is a design change, so update the doc with it.
- **Entities mirror V1.** With `ddl-auto: none` nothing validates the
  mappings at startup; `EntityMappingTest` saves and reads each one back.
  A new column needs a migration, the entity field and that test.
- **Replace the `NotImplemented*` stubs, don't extend them.** When the
  real `CreditOperations` / `ReplyOutbox` bean lands, delete its stub (two
  beans of one interface fail injection) and its case in
  `NotImplementedStubsTest`.
- **No shared code except `foc-contracts`** (root `AGENTS.md`, D15). The
  JWT classes are copies of supplier-service's, on purpose.
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
