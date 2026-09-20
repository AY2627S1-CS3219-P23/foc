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
  2026-09-20: schemaVersion removed by author decision (breaking
  changes ship as new event types); conventions updated accordingly.
  Same day: order→request event vocabulary rename and the
  events.core/events.request package split applied (author decision
  D22); names and layout notes updated throughout.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts

Shared **cross-service contracts** — the one deliberate exception
(decision D15, 2026-09-19; amended same day for the Method-B refactor,
D16–D19) to the "services never share code" convention in
[`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here:

- **Contract names** both producer and consumer must agree on, e.g.
  the broker exchange name (`EventContracts.REQUEST_EVENTS_EXCHANGE`).
- **The typed event contracts** (D17): the `DomainEvent` /
  `RequestEvent` interfaces and one flat record per broker event —
  `RequestCreated`, `RequestAccepted`, `RequestCollected`, `RequestCompleted`,
  `RequestCancelled`, `RequestExpired`, `CourierArrived`.

Package layout (D22): shared machinery — `DomainEvent`,
`EventTypeRegistry`, `EventContracts`, `Nullable` — lives in
`foc.contracts.events.core`; each producing domain's marker interface
and records live in a sibling package (`foc.contracts.events.request`
today; a future domain adds its own).
- **The `EventTypeRegistry`** (D18): the single source of truth
  pairing each record class with its canonical identity string
  (e.g. `request.accepted`), which serves as both the JSON body's
  `eventType` and the RabbitMQ routing key.
- **One canonical fixture per event** under
  [`src/main/resources/contracts/`](src/main/resources/contracts/)
  (shipped on this jar's classpath), named
  `<identity dots→hyphens>.example.json` — the contract artifacts
  each side's contract tests assert against.

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
   migration and is retired in a later release (decided 2026-09-20,
   replacing the earlier `schemaVersion` mechanism as unneeded
   standing complexity). Every record component is **required on the
   wire** unless marked with the contracts' `@Nullable` annotation
   (currently only `RequestCancelled.courierId`); consumers reject
   events with missing required fields as conversion failures. An
   unknown event *type* is likewise a conversion failure — which is
   also how a breaking change presents to a not-yet-upgraded
   consumer (all of these dead-letter to the consumer's DLQ for
   replay — issue #65).

5. **Topology naming.** One durable **topic exchange per producing
   domain** (`request-events`); consumer queues are
   `<service>.<domain>-events` (+ `.retry` / `.dlq` per D13, issue
   #65); consumers bind patterns (`request.#` for a consumer that
   wants every request event).

6. **Adding a new event** (checklist):
   1. create the record implementing `RequestEvent` (or `DomainEvent`
      for a new domain);
   2. add its `EventTypeRegistry` entry;
   3. add `contracts/<identity dots→hyphens>.example.json`;
   4. `./mvnw install` — the registry-driven contract tests pick the
      new event up automatically (and fail loudly if the fixture is
      missing). Consumers need only a jar bump; pattern bindings don't
      change.

   A **breaking change** to an existing event follows the same
   checklist, as a new event type with its own identity string (e.g.
   `request.accepted.v2` — new record + entry + fixture, old type kept
   until every producer has migrated and dead-lettered backlog is
   replayed, then retired in a later release). Consumers that read
   events only through `DomainEvent`/`RequestEvent` (e.g. the
   notification pipeline) need only the jar bump.

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
