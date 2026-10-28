<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-19 – 2026-09-21.
  Scope: wrote and maintained this README alongside the library; all
  contract and convention decisions were made by the team (D15–D22,
  recorded in docs/notification-service.md). Restructured 2026-09-21:
  implementation checklists moved to AGENTS.md, history pruned.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-08, Claude Code (Opus 5.5): record, exchange and package
  lists updated for the four saga events (team design,
  docs/credit-service.md D7-D8); author review: Ryan Ang, pending
  pull request review.
  2026-10-09, PR #163 review (Leong Wei Zhi): convention 1 reworded for the
  Credit saga's reply events (team design, docs/credit-service.md
  D7-D8), at the convention owner's request. Author review: Ryan Ang,
  pending pull request review.
  2026-10-09, Claude Code (Opus 5.5): the list of @Nullable components
  now includes every request record's note (author's decision, issue
  #165).
  2026-10-21, Claude Code (Opus 5.5), issue #234: record list and
  @Nullable list updated for request.disputed, request.dispute-resolved
  and the rejection's reasonCode (team decisions DSP-12, D18), and the
  team's rules for fixed-value String fields (constants + value set,
  convert at the edge, strict API / tolerant consumers, logging default
  branch) transcribed; author review: Ryan Ang, pending pull request
  review.
  2026-10-22, Claude Code (Opus 5.5), PR #243 review follow-up:
  RequestDropped added to the record list (it was missing). Author
  review: Ryan Ang, pending pull request review.
  2026-10-28, Claude Code (Opus 5), issue #249: exchange, record,
  package-layout and topology-naming lists updated for the rating
  domain (scope decisions on #44); author review: Leong Wei Zhi,
  pending pull request review.
-->

# foc-contracts

Shared **cross-service contracts** — the one deliberate exception
(decision D15) to the "services never share code" convention in
[`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here:

- **Contract names** both producer and consumer must agree on, e.g.
  the broker exchange names (`EventContracts.REQUEST_EVENTS_EXCHANGE`,
  `EventContracts.CREDIT_EVENTS_EXCHANGE`,
  `EventContracts.RATING_EVENTS_EXCHANGE`).
- **The typed event contracts** (D17): the `DomainEvent` /
  `RequestEvent` / `CreditEvent` / `RatingEvent` interfaces and one
  flat record per broker event — `RequestCreated`, `RequestAccepted`,
  `RequestCollected`, `RequestCompleted`, `RequestCancelled`,
  `RequestDropped`, `RequestExpired`, `CourierArrived`, `RequestSubmitted`,
  `RequestRejected`, `RequestDisputed`, `RequestDisputeResolved`,
  `CreditReserved`, `CreditReservationRejected`, `RatingSubmitted`.
  Where a field takes a
  fixed set of values, the field is a `String` and the record holds
  each value as a `String` constant plus one `Set` of them all
  (`RequestDisputed.NOT_DELIVERED`, …, `RequestDisputed.CATEGORIES`;
  `CreditReservationRejected.INSUFFICIENT_CREDITS`, …,
  `CreditReservationRejected.REASON_CODES`). Services use them as
  follows (team decision, issue #234):
  - **Convert at the edge.** A service may keep its own enum for its
    database and logic, and converts the contract string when a message
    comes in. An unknown or missing value maps to a fallback (e.g. an
    unknown or null `reasonCode` → a null `close_reason`), never an
    exception.
  - **API input is strict, incoming messages are tolerant.** A REST
    endpoint taking one of these values answers 400 for anything outside
    the record's `Set`; a consumer never rejects a message for an
    unknown value.
  - **Every consumer `switch` on these strings has a `default` branch
    that logs and carries on**, never throws — otherwise one new value
    dead-letters messages, the problem an enum in the contract would
    have caused.
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
and records live in a sibling package (`foc.contracts.events.request`,
`foc.contracts.events.credit` and `foc.contracts.events.rating` today;
a future domain adds its own).

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
   synchronous REST, unless the exchange is a saga step: there the
   result travels as a reply event, itself a past-tense fact (e.g.
   `request.submitted` answered by `credit.reserved` or
   `credit.reservation-rejected`, `docs/credit-service.md` D7–D8). One
   record per event type.

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
   contracts' `@Nullable` annotation (currently
   `RequestCancelled.courierId`, every request record's `note`,
   `RequestDisputed.disputeNote`, `RequestDisputeResolved.resolutionNote`
   and `CreditReservationRejected.reasonCode` — the last always set by
   credit-service from this version on, null only on messages published
   before it);
   consumers reject events with
   missing required fields as conversion failures. An unknown event
   *type* is likewise a conversion failure — which is also how a
   breaking change presents to a not-yet-upgraded consumer (all of
   these dead-letter to the consumer's DLQ for replay).

5. **Topology naming.** One durable **topic exchange per producing
   domain** (`request-events`, `credit-events`, `rating-events`);
   consumer queues are
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
