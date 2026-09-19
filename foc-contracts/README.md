<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-19.
  Scope: README written while creating the shared contracts library for
  issue #62 per team decision D15 (docs/notification-service.md).
  Same day, issue #63: extended for the event-envelope record and
  canonical fixture, per the author's D15-amendment decision.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# foc-contracts

Shared **cross-service contracts** — the one deliberate exception
(decision D15, 2026-09-19; extended under issue #63) to the "services
never share code" convention in [`AGENTS.md`](../AGENTS.md).

Only things that are a *contract between services* belong here:

- **Contract names** both producer and consumer must agree on, e.g.
  the broker exchange name (`EventContracts.ORDER_EVENTS_EXCHANGE`).
- **The event envelope**: the `EventEnvelope` record every broker
  event travels in — its fields carry only event-handling semantics
  (dedupe, ordering, fan-out, display); domain data rides in
  `payload` — plus its canonical example
  [`src/main/resources/contracts/order-event.example.json`](src/main/resources/contracts/order-event.example.json)
  — the contract artifact each side's contract test asserts against
  (loaded from this jar's classpath at `/contracts/order-event.example.json`).
  The envelope evolves additively only: add fields, never rename or
  repurpose (design doc, "Extensibility").

Service-private names (queue names, table names, internal config) stay
in their service. The library has **no framework dependencies** —
plain constants and annotation-free records only — so depending on it
never drags Spring, Jackson, or broker types into a service. (Jackson
binds records by component name; tolerant reading of unknown fields is
each consumer's `ObjectMapper` configuration, e.g. Spring Boot's
default mapper.)

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
