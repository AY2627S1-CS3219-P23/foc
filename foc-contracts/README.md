<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-19.
  Scope: README written while creating the shared contracts library for
  issue #62 per team decision D15 (docs/notification-service.md).
  Same day, issue #63: extended for the event-envelope record and
  canonical fixture, per the author's D15-amendment decision.
  Same day, Method-B refactor: rewritten for the author's decisions
  D16-D19 (typed event records, registry, per-event fixtures) and
  extended with the Event conventions section the author requested.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts

Shared **cross-service contracts** — the one deliberate exception
(decision D15, 2026-09-19; amended same day for the Method-B refactor,
D16–D19) to the "services never share code" convention in
[`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here:

- **Contract names** both producer and consumer must agree on, e.g.
  the broker exchange name (`EventContracts.ORDER_EVENTS_EXCHANGE`).
- **The typed event contracts** (D17): the `DomainEvent` /
  `OrderEvent` interfaces and one flat record per broker event —
  `OrderCreated`, `OrderAccepted`, `OrderCollected`, `OrderCompleted`,
  `OrderCancelled`, `OrderExpired`, `CourierArrived`.
- **The `EventTypeRegistry`** (D18): the single source of truth
  pairing each record class with its canonical identity string
  (e.g. `order.accepted`), which serves as both the JSON body's
  `eventType` and the RabbitMQ routing key.
- **One canonical fixture per event** under
  [`src/main/resources/contracts/`](src/main/resources/contracts/)
  (shipped on this jar's classpath) — the contract artifacts each
  side's contract tests assert against.

Service-private names (queue names, table names, internal config) stay
in their service. The published jar has **no dependencies** — plain
constants, interfaces, and annotation-free records only — so depending
on it never drags Spring, Jackson, or broker types into a service.
(JUnit is present at *test* scope only, for the registry/record
tests.) Jackson binds records by component name; tolerant reading of
unknown fields is each consumer's `ObjectMapper` configuration, e.g.
Spring Boot's default mapper.

## Event conventions

The rules every producer and consumer follows (decided by
Leong Wei Zhi, 2026-09-19 — D16–D19 in
[`docs/notification-service.md`](../docs/notification-service.md)):

1. **Semantics.** Events are **past-tense facts that already
   happened**, never commands. Calls whose caller needs a result stay
   synchronous REST. One record per event type.

2. **Naming & identity.** The record class is PascalCase past-tense
   (`OrderAccepted`). Its **canonical identity** is
   `<domain>.<event-in-kebab-case>` (`order.accepted`), registered
   once in `EventTypeRegistry`. That one string travels twice by
   design: as the body's `eventType` field — the contract's
   self-describing identity, which consumers dispatch on — and as the
   RabbitMQ routing key (transport metadata). Both come from the same
   registry entry, so they cannot drift. `eventType` is **not** a
   record component: `DomainEvent.eventType()` derives it from the
   registry and the message converter injects it on publish.

3. **Wire shape.** Flat JSON — seven metadata fields (`eventId`,
   `eventType`, `schemaVersion`, `occurredAt`, `producer`,
   `correlationId`, `parties`) plus the event's business fields, all
   top-level. Instants are ISO-8601 UTC. Everything rides in the body,
   never in AMQP headers (D11).

4. **Evolution & validation.** Within a `schemaVersion`: **additive
   changes only** — add fields, never rename or repurpose; consumers
   ignore unknown fields (tolerant readers). A breaking change bumps
   `schemaVersion`, and consumers **reject any version other than the
   one their registry entry supports** as a conversion failure. Every
   record component is **required on the wire** unless marked with the
   contracts' `@Nullable` annotation (currently only
   `OrderCancelled.courierId`); consumers reject events with missing
   required fields the same way. An unknown event *type* is likewise
   a conversion failure (all of these dead-letter for replay once the
   DLQ exists — issue #65).

5. **Topology naming.** One durable **topic exchange per producing
   domain** (`order-events`); consumer queues are
   `<service>.<domain>-events` (+ `.retry` / `.dlq` per D13, arriving
   with issue #65); consumers bind patterns (`order.#` for a consumer
   that wants every order event).

6. **Adding a new event** (checklist):
   1. create the record implementing `OrderEvent` (or `DomainEvent`
      for a new domain);
   2. add its `EventTypeRegistry` entry;
   3. add `contracts/<identity dots→hyphens>.example.json`;
   4. `./mvnw install` — the registry-driven contract tests pick the
      new event up automatically (and fail loudly if the fixture is
      missing). Consumers need only a jar bump; pattern bindings don't
      change.

7. **Producer conventions** (for the future order-service): publish
   **after** the local DB commit; enable **publisher confirms**; send
   messages **persistent** (RabbitTemplate's default); propagate
   `correlationId` from the triggering request; always take the
   routing key from
   `EventTypeRegistry.routingKeyFor(event.getClass())` — never
   hand-write it. **The producer must also inject the body
   `eventType`**: it is not a record component, so a stock Jackson
   message converter will serialize a body the consumer rejects. Use
   a producer-owned `MessageConverter` whose `toMessage` serializes
   the event to a JSON tree, puts `eventType` =
   `event.eventType()` (the registry string), and writes the bytes —
   mirroring the consumer-side `DomainEventMessageConverter` in
   notification-service, which is service-private by D11 and not
   importable.

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
