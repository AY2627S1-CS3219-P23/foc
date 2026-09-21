<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21.
  Scope: wrote this agent guide from the existing code, README, and
  design doc; the checklists and producer conventions were moved here
  from the README. All decisions are team-made (D15–D22, recorded in
  docs/notification-service.md); the tool made none.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts/ — Agent Guide

Framework-free Maven module holding the cross-service event contracts —
the **sole exception** (D15) to the no-shared-code convention. Read the
root `AGENTS.md` first; the contract rules every producer and consumer
follows are in this module's
[`README.md` § Event conventions](README.md#event-conventions), and the
design authority is
[`docs/notification-service.md`](../docs/notification-service.md)
(decisions D15–D22).

## Module map (`src/main/java/foc/contracts/`)

| Package / path | Contents |
| --- | --- |
| `events/core/` | `DomainEvent` (base interface; `eventType()` derives the identity from the registry), `EventContracts` (shared names, e.g. `REQUEST_EVENTS_EXCHANGE`), `EventTypeRegistry` (single class↔identity source, D18), `Nullable` (marks optional wire fields) |
| `events/request/` | `RequestEvent` (domain marker) + the seven records: `RequestCreated`, `RequestAccepted`, `RequestCollected`, `RequestCompleted`, `RequestCancelled`, `RequestExpired`, `CourierArrived` |
| `src/main/resources/contracts/` | One canonical fixture per event, `<identity dots→hyphens>.example.json` (e.g. `request-courier-arrived.example.json`) — shipped on the jar's classpath |
| `src/test/java/` | `EventTypeRegistryTest`, `RequestEventRecordsTest` — registry-driven: a new registry entry without a fixture (or vice versa) fails loudly |

## Invariants — do not break

- **The published jar stays dependency-free**: plain constants,
  interfaces, and annotation-free records only — never add Spring,
  Jackson, or broker types. JUnit is allowed at *test* scope only.
- **Additive evolution only** (D18): add fields marked `@Nullable`
  until every producer stamps them; never rename or repurpose a field.
  A breaking change ships as a **new event type** with its own
  identity string (`request.accepted.v2`), record, and fixture — the
  old type keeps flowing until every producer migrated.
- **`EventTypeRegistry` is the single class↔identity source**: the
  identity string is both the body `eventType` and the routing key;
  `eventType` is never a record component.
- **Every event has exactly one fixture**, named from its identity.
- **Service-private names stay out**: queue names, table names, and
  internal config belong to their service, not here.
- Currently the only `@Nullable` component is
  `RequestCancelled.courierId` (a pre-acceptance cancel has no
  courier).

## Adding a new event

1. Create the record implementing `RequestEvent` (or `DomainEvent` via
   a new domain marker for a new domain).
2. Add its `EventTypeRegistry` entry.
3. Add `contracts/<identity dots→hyphens>.example.json`.
4. `./mvnw install` — the registry-driven contract tests pick the new
   event up automatically (and fail loudly if the fixture is missing).
   Consumers need only a jar bump; pattern bindings (`request.#`)
   don't change.

A **breaking change** to an existing event follows the same checklist
as a new event type with its own identity string (e.g.
`request.accepted.v2` — new record + entry + fixture); keep the old
type until every producer has migrated and dead-lettered backlog is
replayed, then retire it in a later release. Consumers that read
events only through `DomainEvent`/`RequestEvent` (e.g. the
notification pipeline) need only the jar bump.

## Producer conventions

For any service that publishes events (the future order-service):

- Publish **after** the local DB commit; enable **publisher
  confirms**; send messages **persistent** (RabbitTemplate's default).
- Propagate `correlationId` from the triggering request.
- Always take the routing key from
  `EventTypeRegistry.routingKeyFor(event.getClass())` — never
  hand-write it.
- **Inject the body `eventType`**: it is not a record component, so a
  stock Jackson message converter serializes a body the consumer
  rejects. Use a producer-owned `MessageConverter` whose `toMessage`
  serializes the event to a JSON tree, puts `eventType` =
  `event.eventType()` (the registry string), and writes the bytes —
  mirroring the consumer-side `DomainEventMessageConverter` in
  notification-service, which is service-private by D11 and not
  importable.

## Build & test

```sh
./mvnw install   # build + test + install to the local Maven repo
./mvnw test      # tests only
```
