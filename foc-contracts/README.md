<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-19 – 2026-09-21.
  Scope: wrote and maintained this README alongside the library; all
  contract and convention decisions were made by the team (D15–D22,
  recorded in docs/notification-service.md). Restructured 2026-09-21:
  implementation checklists moved to AGENTS.md, history pruned.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts

Shared **cross-service contracts** — the one deliberate exception
(decision D15) to the "services never share code" convention in
[`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here:

- **Contract names** both producer and consumer must agree on, e.g.
  the broker exchange name (`EventContracts.REQUEST_EVENTS_EXCHANGE`).
- **The typed event contracts** (D17): the `DomainEvent` /
  `RequestEvent` interfaces and one flat record per broker event —
  `RequestCreated`, `RequestAccepted`, `RequestCollected`,
  `RequestCompleted`, `RequestCancelled`, `RequestExpired`,
  `CourierArrived`.
- **The `EventTypeRegistry`** (D18): the single source of truth
  pairing each record class with its canonical identity string
  (e.g. `request.accepted`), which serves as both the JSON body's
  `eventType` and the RabbitMQ routing key.
- **One canonical fixture per event** under
  [`src/main/resources/contracts/`](src/main/resources/contracts/)
  (shipped on this jar's classpath), named
  `<identity dots→hyphens>.example.json` — the contract artifacts
  each side's contract tests assert against.

Package layout (D22): shared machinery — `DomainEvent`,
`EventTypeRegistry`, `EventContracts`, `Nullable` — lives in
`foc.contracts.events.core`; each producing domain's marker interface
and records live in a sibling package (`foc.contracts.events.request`
today; a future domain adds its own).

Service-private names (queue names, table names, internal config) stay
in their service. The published jar has **no dependencies** — plain
constants, interfaces, and records carrying only the module's own
annotations (`Nullable`) — so depending on it never drags Spring,
Jackson, or broker types into a service.
(JUnit is present at *test* scope only, for the registry/record
tests.) Jackson binds records by component name; tolerant reading of
unknown fields is each consumer's `ObjectMapper` configuration, e.g.
Spring Boot's default mapper.

## Event conventions

The rules every producer and consumer follows (decided by
Leong Wei Zhi — D16–D19 in
[`docs/notification-service.md`](../docs/notification-service.md)):

1. **Semantics.** Events are **past-tense facts that already
   happened**, never commands. Calls whose caller needs a result stay
   synchronous REST. One record per event type.

2. **Naming & identity.** The record class is PascalCase past-tense
   (`RequestAccepted`). Its **canonical identity** is
   `<domain>.<event-in-kebab-case>` (`request.accepted`), registered
   once in `EventTypeRegistry`. That one string travels twice by
   design: as the body's `eventType` field — the contract's
   self-describing identity, which consumers dispatch on — and as the
   RabbitMQ routing key (transport metadata). Both come from the same
   registry entry, so they cannot drift. `eventType` is **not** a
   record component: `DomainEvent.eventType()` derives it from the
   registry and the message converter injects it on publish.

3. **Wire shape.** Flat JSON — six metadata fields (`eventId`,
   `eventType`, `occurredAt`, `producer`, `correlationId`,
   `parties`) plus the event's business fields, all top-level.
   Instants are ISO-8601 UTC. Everything rides in the body, never in
   AMQP headers (D11).

4. **Evolution & validation.** **Additive changes only** — add
   fields (marked `@Nullable` until every producer stamps them),
   never rename or repurpose; consumers ignore unknown fields
   (tolerant readers). A **breaking** change ships as a **new event
   type** with its own identity string (e.g. `request.accepted.v2`),
   record and registry entry — the old type keeps flowing during the
   migration and is retired in a later release. Every record
   component is **required on the wire** unless marked with the
   contracts' `@Nullable` annotation (currently only
   `RequestCancelled.courierId`); consumers reject events with
   missing required fields as conversion failures. An unknown event
   *type* is likewise a conversion failure — which is also how a
   breaking change presents to a not-yet-upgraded consumer (all of
   these dead-letter to the consumer's DLQ for replay).

5. **Topology naming.** One durable **topic exchange per producing
   domain** (`request-events`); consumer queues are
   `<service>.<domain>-events` (+ `.retry` / `.dlq` per D13);
   consumers bind patterns (`request.#` for a consumer that wants
   every request event).

Implementation checklists — adding a new event, and the producer-side
publishing conventions — live in [`AGENTS.md`](AGENTS.md).

## Use

Install to the local Maven repository, then depend on it:

```sh
./mvnw install
```

```xml
<dependency>
    <groupId>foc</groupId>
    <artifactId>foc-contracts</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Docker builds don't need the install step — each consuming service's
`Dockerfile` builds this module first (its compose build context is the
repo root).
