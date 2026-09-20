<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-15; revised 2026-09-18.
  Scope: transcribed the team-decided notification-service design into
  this document and the companion diagram. Every design decision below
  was made by a team member (Leong Wei Zhi, 2026-09-15, recorded in the
  Decisions table); the tool documented the decisions, derived the
  requirement-traceability mapping, and formatted the result. The
  2026-09-18 revision re-aligned requirement references with the latest
  D1 backlog (Template-7): Order F11.1 renumbered to Order F0.2, and the
  courier collection/arrival notifications (Order F4.1.1-F4.1.2) traced
  to the existing generic-envelope design. Also on 2026-09-18: a Q&A in
  which the author decided broker isolation via ports-and-adapters
  (D11), keeping broker-native retry/DLQ (D6/D7 unchanged), and
  recording multi-producer/consumer mechanics as extension notes with
  exchange topology left open; the tool explained the options neutrally
  and documented the outcomes. The broker-ownership note and the
  connection-topology section transcribe rationale already implied by
  decisions D1/D2 and the system diagram's REST edges; their open
  points (WebSocket handshake auth, reconnect policy, N6 chat
  transport) are recorded as pending team/implementation decisions.
  No architecture, technology, or trade-off decisions were made by the
  tool.
  2026-09-19 revision: recorded decisions D12-D15 (topology
  provisioning, naming convention, exchange type, shared contracts
  library), made by the author via neutral options Q&As while
  implementing issue #62; the tool presented the options factually —
  including the industry conventions the author asked about before
  D15 — and documented the outcomes.
  2026-09-19 (issue #63): recorded the D15 amendment — the author
  decided, from neutral options (per-service DTO copies + shared
  fixture vs. shared DTO in foc-contracts; fixture location; payload
  Java type), that the EventEnvelope record and canonical fixture live
  in foc-contracts; when asked for a view on placement the tool
  restated constraints already documented in the repo (fixture ships
  in the shared jar, the library's framework-free rule, additive-only
  evolution), the author decided, and the tool updated this document
  and the Event envelope section accordingly.
  Same day, on PR review, the author decided envelope fields must be
  restricted to event-handling semantics and chose (from neutral
  options: grouping-key generality, parties element shape) to adopt
  the Extensibility section's platform-wide shape — entityType +
  entityId + parties[] as plain user IDs — replacing orderId /
  requesterId / courierId; the tool updated the field table and the
  Extensibility bullet to match. The author also renamed the type
  field to eventType (symmetry with the entity-type field), and chose
  entityType/entityId over the DDD-conventional
  aggregateType/aggregateId (from neutral options: aggregate — the
  Debezium-outbox/Axon convention this section originally cited —
  entity, or CloudEvents-style subject), preferring plain English
  over pattern jargon.
  2026-09-19 (issue #64): the author decided, via neutral options
  Q&As, the listener/processor implementation choices: DB schema
  renamed to the envelope's entity vocabulary (D5 wording generalized
  to per-entity below), sequence tracking keyed by the composite
  (entityType, entityId) pair, literal manual acknowledgement,
  interim failure handling (conversion failures dropped, processing
  failures requeued) pending #65's retry/DLQ, and a classic
  controller-service-repository package layout; the tool implemented
  those decisions and updated the affected wording here.
  2026-09-19 (issue #67): the author decided, via neutral options
  Q&As, the push-gateway session mechanics recorded in the new
  "Session mechanics" subsection: backend-only scope (no SPA exists
  yet), CONNECT-frame Bearer JWT auth, jjwt/HS256 with the shared
  secret, sub-claim-as-user-ID provisional platform convention (also
  noted in docs/architecture.md), SockJS fallback on the /ws
  endpoint, 10 s heartbeats, and the provisional client reconnect
  policy; the tool implemented them and struck the settled Open item.
  2026-09-19 (Method-B refactor): recorded decisions D16-D19, all made
  by the author via neutral options Q&As (with previews) after judging
  the generic envelope too weakly typed: topic exchange with canonical
  routing keys (D16, supersedes D14), flat typed event records with no
  envelope (D17, deletes EventEnvelope), the registry as the single
  class-to-identity source with the one identity string serving as
  both body eventType and routing key (D18 — the author consulted an
  external LLM and then chose from neutral options here), and removal
  of the entity-identity/sequence stale-discard mechanism plus the
  sequence field (D19, supersedes D5, retires F2.4). Retry values
  (3 attempts / 10 s TTL) and unknown-type dead-lettering were decided
  by the author for issue #65 but deliberately not implemented in this
  refactor-only change. The tool presented options factually, then
  implemented the outcomes and updated this document.
  2026-09-20: after PR #75 merged, the author reconsidered and decided
  (from neutral options: remove entirely / keep versioned registry /
  keep single-version gate) to remove the schemaVersion field and its
  versioned-registry mechanism as unneeded standing complexity;
  breaking contract changes now ship as new event types (D18
  amended). The tool implemented the removal.
  2026-09-20 (issue #65): the author decided, via neutral options
  Q&As, the two implementation choices left open by the D6/D7
  retry/DLQ design, recorded as D20-D21 (default exchange as the
  dead-letter exchange on both legs; unconvertible messages
  dead-letter on first rejection, skipping the retry queue); the tool
  implemented the team-decided topology with those choices and
  updated the affected wording here.
  2026-09-20 (order→request rename): recorded D22 — the author's
  decisions, from neutral options Q&As, to rename the event
  vocabulary from order to request across the wire contract and to
  split foc-contracts into events.core / events.request packages;
  the tool applied the mechanical rename here and in both modules,
  leaving historical notes (D14, struck Open items, this header)
  under the old names.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# FoC Notification Service — Architecture Design

**Scope:** component-level design of `notification-service/`, covering
every D1 Notification requirement (F1 event delivery, F2 delivery
reliability, F3 notification retrieval, NFR1 reliability) plus the
Order-side requirements it serves: events on every request state
transition (Order F0.2), requester updates on collection and courier
arrival (Order F4.1.1–F4.1.2), the request state-change latency budget
(Order NFR1.1–1.2), and the course's mandatory async workflow (M6). Companion diagram:
[`notification-service.mmd`](notification-service.mmd) (GitHub renders
it in the file view). System-wide context: [`architecture.md`](architecture.md).

## Design decisions (made by the team)

All decisions below were made by Leong Wei Zhi (D1–D10 on 2026-09-15,
D11 on 2026-09-18, D12–D19 on 2026-09-19, D20–D22 on 2026-09-20) and
count as the team's finalized design for this service.

| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Message broker | **RabbitMQ** | M6; Notif F1.1, NFR1 |
| D2 | Client push transport | **WebSocket with STOMP** (Spring simple broker, per-user destinations) | Notif F1.2; Order NFR1.1–1.2 |
| D3 | Notification DB engine | **PostgreSQL**, accessed via Spring Data JPA | Notif F2.1, F2.4, F3 |
| D4 | Duplicate detection | **Unique event ID** recorded in the DB (unique constraint), written in the *same transaction* as the notification insert | Notif F2.1 |
| D5 | ~~Stale-event discard~~ | ~~Per-entity sequence number stamped by the producer; discard sequence ≤ last applied~~ — **superseded by D19** (2026-09-19): the mechanism, its `sequence` wire field, and requirement F2.4 were removed | — |
| D6 | Retry policy | **Broker redelivery with TTL/delay-queue backoff**, fixed maximum attempts. Values decided 2026-09-19: **3 total attempts, 10 s TTL**, env-overridable (`NOTIFICATION_RETRY_TTL_MS`, `NOTIFICATION_RETRY_MAX_ATTEMPTS`). Implemented under issue #65 (2026-09-20): the listener republishes a failed event to the retry queue while attempts remain and the queue's TTL leg returns it to the work queue; attempts ride in a listener-stamped `x-retry-attempts` header, since RabbitMQ 4 resets its own `x-death` count on client republish (verified against a real broker) | Notif F2.2 |
| D7 | Dead-letter handling | **RabbitMQ dead-letter exchange → durable dead-letter queue**; inspected via the management UI or a consumer, replayable by re-publishing. Decided 2026-09-19: once the DLQ exists, **unknown event types dead-letter** (replayable after a consumer upgrade) rather than being silently dropped. Implemented under issue #65 (2026-09-20) with the D20/D21 choices | Notif F2.3 |
| D8 | Broker persistence | **Durable exchanges/queues + persistent messages** (must be explicitly configured — durability is opt-in in RabbitMQ) | Notif NFR1.2 |
| D9 | Retention window | **Configurable via environment variable** `NOTIF_RETENTION_DAYS`, **default 30 days**; scheduled purge job | Notif F3.4 |
| D10 | Redis / scale-out | **No Redis** in the current single-instance design (see Open items) | — |
| D11 | Broker isolation | **Ports and adapters**: business logic (event processor, REST API, purge job) has zero broker imports; all RabbitMQ-specific code is confined to a single messaging adapter package behind service-owned interfaces (see "Broker decoupling") | maintainability; broker swap surface |
| D12 | Topology provisioning | **App-declared at startup via Spring AMQP**: the consuming service declares its exchange/queue/binding beans and forces the declaration when it boots (no broker definitions file) | Notif NFR1.2; topology lives beside its owner |
| D13 | Naming convention | Exchange named after the producing domain (**`request-events`**); queues prefixed with the consuming service (**`notification-service.request-events`**, later `….retry` / `….dlq`) | ownership visible in the management UI; Extensibility |
| D14 | ~~`order-events` exchange type~~ | ~~Fanout~~ — **superseded by D16** (2026-09-19) | — |
| D15 | Cross-service contract names | **Shared Java library** `foc-contracts/` (top-level Maven module, zero runtime framework dependencies): contract names that producer and consumer must agree on — e.g. the `request-events` exchange — are compile-time constants imported by each backend service. The sole exception to the no-shared-code convention (recorded in `AGENTS.md`). Service-private names (queues) stay in their service. Amended 2026-09-19 (Method-B refactor, superseding the issue #63 envelope scope): the library holds the **typed event contracts** — the `DomainEvent`/`RequestEvent` interfaces, the seven event records, the `EventTypeRegistry`, and one canonical fixture per event on its classpath (`contracts/<identity dots→hyphens>.example.json`), which both producer and consumer contract-test against. Test-scoped JUnit was added for the registry/record tests; the published jar stays dependency-free. Tolerant reading of unknown *fields* (F1.3) is consumer-side `ObjectMapper` behavior, locked in by the Notification Service's contract test — so the AMQP message converter must use Boot's auto-configured mapper. | one definition per contract name and per event shape; drift caught at compile time or by the contract tests |
| D16 | `request-events` exchange type | **Topic exchange** (supersedes D14): producers publish each event under its canonical routing key (`request.created` … `request.courier-arrived`, from the registry); each consuming service gets its own durable queue with its own bindings — this service binds **`request.#`** (it notifies on every request event, and a new event type ships via a contracts release anyway, so the binding never changes). One exchange per producing domain; future domains get their own. | Notif F1.1–F1.3; Extensibility |
| D17 | Event contract shape | **Flat typed event records, no envelope** (Method B): `EventEnvelope` and its free-form `Map` payload are deleted; each of the seven request-lifecycle facts — `RequestCreated`, `RequestAccepted`, `RequestCollected`, `RequestCompleted`, `RequestCancelled`, `RequestExpired`, `CourierArrived` — is one record implementing the plain-Java `DomainEvent` interface, carrying the wire metadata (`eventId`, `eventType`, `occurredAt`, `producer`, `correlationId`, `parties`) plus its own business fields (full fixture vocabulary: `requestId`, `requesterId`, `courierId` where a courier exists — nullable on `RequestCancelled` — `pickupLocation`, `dropoffLocation`, `note`) at the top level. Events are past-tense facts, never commands. | schema per event; Notif F1.1–F1.2 |
| D18 | Event identity & dispatch | **One canonical identity string per event** (e.g. `request.accepted`), registered once in `EventTypeRegistry` (class ↔ identity). It travels twice by design: as the body `eventType` — the contract's self-describing identity, which consumers **dispatch on** (D11's body-only rule stands; no `__TypeId__` headers) — and as the RabbitMQ routing key (transport metadata). `eventType` is *not* a record component: `DomainEvent.eventType()` derives it from the registry and the message converter injects it on publish, so an instance can never carry a mismatched type. Stored in `notifications.event_type` and pushed in the STOMP frame. Consumers reject unknown types and events missing required fields (components not marked `@Nullable`). A **breaking contract change ships as a new event type** (e.g. `request.accepted.v2`) — decided 2026-09-20, removing the interim `schemaVersion` field and its versioned-registry mechanism (added on PR #75 review the day before) as unneeded standing complexity; the unknown-type rejection already covers the migration window. | Notif F1.3; portability (self-describing bodies) |
| D19 | Idempotency scope | **Event-ID dedupe only** (supersedes D5): the per-entity sequence mechanism, its `sequence` wire field, the `entity_sequences` table, and the `entity_type`/`entity_id` columns/DTO fields were removed; requirement F2.4 is retired. Accepted consequence: out-of-order deliveries each produce notifications. Re-adding ordering later is an additive contract change (a new `@Nullable` field). | Notif F2.1 |
| D20 | Dead-letter exchange form | **The default exchange (`""`)** serves as the dead-letter exchange on both legs (work queue → DLQ, retry queue → work queue), with `x-dead-letter-routing-key` = the target queue name — zero extra exchanges/bindings to declare or migrate. Accepted consequence: the routing key is rewritten to the queue name in transit; nothing is lost, since the body `eventType` **is** the original key (D18) and the broker's `x-death` header records the original keys. Decided 2026-09-20 | Notif F2.2, F2.3; minimal topology |
| D21 | Unconvertible-message path | **Dead-letter on first rejection**: a message the converter rejects (unknown `eventType`, malformed body, missing required field) is rejected by the listener container before the listener runs and dead-letters straight to the DLQ, skipping the retry queue — retrying a message that cannot convert could never succeed; replay happens from the DLQ after a consumer upgrade (D7). Falls out of the container's existing rejection plus the work queue's dead-letter leg, with no custom error-handler code. Decided 2026-09-20 | Notif F2.3; D7 addendum |
| D22 | Event vocabulary & contracts layout | **`request` replaces `order`** across the event contracts, matching the D1 backlog's request vocabulary (decided 2026-09-20): classes (`RequestEvent`, `RequestCreated`, …), identities/routing keys (`request.created` … `request.courier-arrived`), the exchange (`request-events`), this service's queues (`notification-service.request-events` + `.retry`/`.dlq`), the `requestId` body field, and the fixtures. Also decided: `foc-contracts` splits into `events.core` (`DomainEvent`, `EventTypeRegistry`, `EventContracts`, `Nullable`) and `events.request` (the domain marker + records); future domains get sibling packages. Applied as a wholesale rename rather than as new event types (the D18 evolution rule) — a one-time pre-production break, acceptable because no producer exists yet (order-service is an empty stub, issue #55 open) and the notification service migrates in the same PR. Old broker entities linger on dev volumes; see the migration note. | vocabulary consistency with the D1 backlog; Extensibility |

## Components

Ownership note — the first four rows are **broker infrastructure, not
part of the Notification Service process**: RabbitMQ is shared platform
infrastructure running as its own container (like the databases), usable
by any service. Within it, the `request-events` exchange is conceptually
the *Order Service's* publishing surface (its contract that state-change
facts appear there), while the work, retry and dead-letter queues are
**broker-hosted resources dedicated to this service**: it is their sole
consumer and the party responsible for their configuration, but the
queues — and the in-flight events they hold — live on the shared
broker, not inside the service. A future second consumer would get its
own dedicated queues (see Extensibility).
The Notification Service's durable state lives only in its PostgreSQL
database; the broker holds in-flight messages, not service state, so the
database-per-service rule is untouched.

| Component | Responsibility |
| --- | --- |
| **request-events exchange** (RabbitMQ) | Durable **topic** exchange (D16) the Order Service publishes request-state and courier-arrival events to under their canonical routing keys; publishing is fire-and-forget, so a delivery failure never affects the producing operation (F1.4). |
| **Work queue** (RabbitMQ) | Durable queue bound to the exchange with `request.#` (D16); holds undelivered events across restarts (NFR1.2) and delivers them at-least-once (NFR1.1). |
| **Retry queue** (RabbitMQ) | Durable TTL/delay queue; the listener parks a failed event here while attempts remain, and the queue's dead-letter leg (D20) re-routes it to the work queue when its TTL expires, giving backoff between attempts (F2.2). |
| **Dead-letter queue** (RabbitMQ) | Durable queue fed by the work queue's dead-letter leg (D20) after an event exhausts its maximum attempts — or immediately, for a message that cannot convert (D21); retained for later inspection and manual re-publish (F2.3). |
| **AMQP listener** (Spring Boot) | Consumes events with manual acknowledgement; acks only after the processor's DB transaction commits, so a crash before commit leads to redelivery, never loss (NFR1.1). |
| **Idempotent event processor** (Spring Boot) | Core logic: rejects already-seen event IDs (F2.1) and creates one notification row per associated party (F1.2), storing the event's business fields as the payload — all from the typed event alone, never querying another service (F1.1). Works against the `DomainEvent` interface, so every cataloged event type flows through generically. |
| **Notification REST API** (Spring Boot) | Lets the Web App list a user's recent notifications and mark them read/unread (F3.1, F3.2). |
| **STOMP push gateway** (Spring Boot) | WebSocket endpoint with Spring's STOMP simple broker; pushes each stored notification to the affected users' `/user/...` destinations within the 5-second budget (F1.2; Order NFR1.1–1.2). |
| **Retention purge scheduler** (Spring Boot) | Scheduled job deleting notifications older than the configured window (F3.4). |
| **Notification DB** (PostgreSQL) | Owned exclusively by this service (database-per-service): notification rows with read/unread state and processed event IDs — one schema so dedupe and notification insert commit atomically. |

## Event contracts (D17/D18, Method-B refactor)

The Order Service publishes **explicit, business-specific typed
events** — one flat record per fact, no envelope wrapper and no
free-form payload map (the generic `EventEnvelope` was judged too
weakly typed and deleted). The contracts are code in `foc-contracts/`:
the `DomainEvent`/`RequestEvent` interfaces, the seven records, and the
`EventTypeRegistry` — with one canonical fixture per event checked in
under
[`foc-contracts/src/main/resources/contracts/`](../foc-contracts/src/main/resources/contracts/)
(`<identity dots→hyphens>.example.json`), the contract artifacts both
producer and consumer contract-test against (D15). The full
conventions — naming, wire shape, evolution rules, the
add-a-new-event checklist, and producer conventions — live in the
["Event conventions" section of the foc-contracts README](../foc-contracts/README.md#event-conventions).

Wire metadata carried by every event (top-level, alongside the
event's business fields):

| Field | Why it must be present |
| --- | --- |
| `eventId` (unique) | duplicate detection (D4, F2.1) |
| `eventType` (canonical identity, e.g. `request.accepted` — injected by the converter, derived from the class; same string as the routing key) | consumer dispatch to the record class; self-describing, transport-portable bodies (D18) |
| `occurredAt` timestamp | notification display and audit |
| `producer` (e.g. `order-service`) | provenance/audit |
| `correlationId` | correlates the event with the request/flow that caused it |
| `parties` (user IDs to notify) | one notification per entry, without querying other services (F1.1, F1.2) |

The seven cataloged events (Order F0.2 state transitions plus the
courier-arrival update, F4.1.1–F4.1.2): `request.created`,
`request.accepted`, `request.collected`, `request.completed`,
`request.cancelled`, `request.expired`, `request.courier-arrived`. Business
fields per event: all carry `requestId`, `requesterId`,
`pickupLocation`, `dropoffLocation`, `note`; the post-acceptance
events add `courierId` (nullable on `request.cancelled` — a
pre-acceptance cancel has no courier). Adding an event type is a
contracts release (record + registry entry + fixture) plus a consumer
jar bump — the `request.#` binding never changes (D16).

## Diagram legend

Same notation as [`architecture.md`](architecture.md):

| Notation | Meaning |
| --- | --- |
| Rectangle | A software component (Spring Boot beans inside the service, or a neighbouring service) |
| Hexagon / double-bordered rectangle | RabbitMQ exchange / queue (broker infrastructure) |
| Cylinder | The PostgreSQL database owned by this service alone |
| Thin arrow `-->` | Synchronous call (REST/JSON over HTTP, or an in-process call), caller → callee |
| Thick arrow `==>` | Asynchronous message flow (AMQP or STOMP/WebSocket), in the direction the data travels |
| Plain line `---` | Database access via Spring Data JPA |
| `F… / NFR… / M…` on a line | The D1 requirement or course-mandated item the relationship implements |

**Abbreviations:** AMQP = Advanced Message Queuing Protocol (RabbitMQ's
protocol) · STOMP = Simple Text Oriented Messaging Protocol (messaging
protocol layered over WebSocket) · TTL = time-to-live · DLQ =
dead-letter queue · JPA = Java Persistence API · REST = HTTP/JSON web
APIs · ack/nack = message (negative) acknowledgement.

## Requirement traceability

| Requirement | Satisfied by |
| --- | --- |
| Notif F1.1 — notify on events without querying the producing service | each typed event carries all needed data (party IDs, business fields); processor reads only the event + own DB |
| Notif F1.2 — notify each party of an event | processor creates one notification per entry in the event's `parties`; push gateway targets each party's per-user destination |
| Notif F1.3 — new event types without publisher changes | reworded under D17/D18: a new event type is a contracts release consumed via a jar bump (no *code* change — registry-driven converter, `DomainEvent`-generic processor, unchanged `request.#` binding). Consumers stay tolerant readers of unknown *fields*; an unknown *type* is a conversion failure — dead-lettered intact on first rejection for replay after a consumer upgrade (D7/D21) |
| Notif F1.4 — delivery failure never affects the producing operation | fire-and-forget publish to the exchange; all retry/failure handling stays on the consumer side of the broker |
| Notif F2.1 — no duplicate notification on redelivery | unique event-ID constraint checked in the same DB transaction as the notification insert (D4) |
| Notif F2.2 — retry failed deliveries | listener republish → TTL retry queue → redelivery with backoff, up to max attempts counted via the listener-stamped `x-retry-attempts` header (D6/D20) |
| Notif F2.3 — record events that exhaust retries | the work queue's dead-letter leg routes them to the durable DLQ for inspection/re-publish (D7/D20); unconvertible messages arrive on first rejection (D21) |
| ~~Notif F2.4 — discard events older than the last applied for the same order~~ | **retired 2026-09-19 (D19)**: the per-entity sequence mechanism was removed; out-of-order deliveries each notify |
| Notif F3.1 — view recent notifications in-app | Notification REST API + stored rows |
| Notif F3.2 — mark read/unread | read/unread flag on the notification row, toggled via the REST API |
| Notif F3.4 — retention window | purge scheduler, `NOTIF_RETENTION_DAYS` (default 30) |
| Notif NFR1.1 — at-least-once delivery | durable queue + manual ack after transaction commit; unacked events are redelivered |
| Notif NFR1.2 — persist undelivered events across restarts | durable exchanges/queues + persistent messages (D8) |
| Order F0.2 — event on every request state transition | Order Service publishes all six state-transition events to the exchange |
| Order F4.1.1–F4.1.2 — requester updated on collection and on courier arrival at the dropoff | `collected` state event plus the `courier-arrived` event, delivered through the same pipeline and pushed to the requester |
| Order NFR1.1–1.2 — requester and assigned courier see every state change within 5 s | broker push path end-to-end: consume → process → STOMP push, no polling |
| M6 — meaningful async workflow | the entire request-events → broker → notification pipeline |

## Configuration notes

- `NOTIF_RETENTION_DAYS` — retention window for stored notifications;
  default **30** (D9).
- Retry TTL and maximum attempts (D6): **10 s backoff, 3 total
  attempts**, overridable via `NOTIFICATION_RETRY_TTL_MS` /
  `NOTIFICATION_RETRY_MAX_ATTEMPTS`. The TTL is baked into the retry
  queue's arguments at declaration, so changing it against a broker
  that already holds the queue fails (`PRECONDITION_FAILED`) until the
  queue is deleted or the volume reset; max attempts is read by the
  listener and only needs a service restart.
- Migration note (Method-B refactor / issue #65 / D22 rename): a dev
  broker volume from the fanout era makes the topic-exchange
  declaration fail (`PRECONDITION_FAILED`), as does one holding the
  pre-#65 work queue (no dead-letter arguments); after the D22 rename
  the old `order-events` exchange and `notification-service.order-events*`
  queues simply linger as orphans (the new names declare cleanly)
  and any messages still parked in them are stranded; a dev DB volume
  still carrying the removed NOT NULL `entity_type`/`entity_id`
  columns rejects inserts (`ddl-auto: update` never drops columns) —
  reset the volumes once (see the service README).
- RabbitMQ durability is **opt-in**: exchanges and queues must be
  declared durable and messages published persistent, or NFR1.2 is
  silently violated.
- RabbitMQ runs as its own container in `compose.yaml` with a named
  volume for its data directory (M7).

## Broker decoupling (D11)

Goal: a broker swap (e.g. RabbitMQ → Kafka) touches one adapter package
and `compose.yaml`, never the business logic.

- **Ports (service-owned interfaces):** inbound, the AMQP listener is a
  thin adapter — the message converter dispatches on the body
  `eventType` via the registry (D18), the listener calls
  `eventProcessor.process(DomainEvent)` and acks/nacks on the result;
  outbound (Order Service side), publishing goes through an
  `EventPublisher.publish(DomainEvent)` interface with the RabbitMQ
  implementation as one class (see the producer conventions in the
  foc-contracts README). The processor, REST API, and purge job
  import nothing from `org.springframework.amqp` / `com.rabbitmq`.
- **Enforcement:** broker code lives in its own package (e.g.
  `messaging.rabbitmq`); an ArchUnit test can assert no other package
  imports broker types (not yet written — see Open items).
- **Events stay broker-agnostic:** everything — metadata and business
  fields, including the canonical `eventType` — rides in the JSON body,
  never in AMQP headers or other broker-specific message properties
  (D11/D18). The routing key duplicates the identity for RabbitMQ
  delivery only; the body alone is self-describing, so bodies stored,
  logged, or moved to another transport keep their meaning.
- **Already portable by construction:** duplicate detection (D4) is
  enforced in this service's own database, not by broker features, so
  the logic satisfying F2.1 is unchanged by any broker swap. The design
  assumes only the weakest common guarantee — events may arrive twice
  or late — which every mainstream broker meets.
- **Known non-portable surface (accepted trade-off):** the retry and
  dead-letter topology (D6/D7 — TTL retry queue, dead-letter exchange)
  and per-message ack/nack are RabbitMQ mechanisms and are treated as
  part of the adapter. The team considered an application-level
  alternative (a `failed_events` table plus a retry scheduler, fully
  broker-portable) and decided to keep the broker-native design: it is
  battle-tested and requires no custom code, at the cost that a broker
  swap must re-implement retry/dead-lettering in the new broker's idiom
  (Kafka, for example, has no per-message acks, TTL requeue, or DLX).
- **Testing follows the port:** the processor is unit-tested by passing
  envelopes to the interface directly (no broker); one integration test
  exercises the RabbitMQ adapter (e.g. via Testcontainers).

## Extensibility: future producers and consumers (extension notes)

Nothing in the committed D1 scope needs more than one producer (Order)
and one consumer (this service); these notes record how the design
extends without rework. The most plausible next consumer in the backlog
is centralized logging (nice-to-have N4).

- **New consumers are free for the publisher.** The exchange is the
  broadcast point: a new consuming service gets **its own durable
  queue** bound to the existing exchange; RabbitMQ delivers a copy of
  each event to every bound queue. The producer is untouched —
  the same decoupling F1.3 gives for new event types.
- **One queue per consuming service.** Consumers on the same queue
  *compete* for messages (each event reaches only one of them), so two
  services must never share a queue. Multiple instances of the *same*
  service do share that service's queue — that competition is the
  desired load balancing for scale-out.
- **Each consumer brings its own reliability machinery:** its own retry
  queue and DLQ (dead-lettering is configured per queue, so one broken
  consumer never blocks another), its own processed-event-ID inbox
  (redelivery is per queue, so dedupe state cannot be shared), and its
  own backlog (a slow consumer affects nobody else). Bindings are also
  per queue: a consumer that needs only some request events binds
  narrower patterns than this service's `request.#`.
- **The contracts library is already the public contract.** The typed
  records in `foc-contracts` (D17) are what every producer and
  consumer compiles against; they evolve additively only (add
  `@Nullable` fields, never rename or repurpose; consumers ignore
  unknown fields), and a breaking change ships as a new event type —
  see the "Event conventions" section of the foc-contracts README.
  New consumers reuse the same records and registry unchanged; a new
  producing domain adds its own event records, registry entries and
  exchange.
- **Events are facts, not commands.** Calls whose caller needs the
  result — e.g. Order → Credit reserve/transfer (Credit F2.1.3, F3.1) —
  stay synchronous REST; the broker carries only "this happened"
  notifications. This is the existing system-level boundary in
  [`architecture.md`](architecture.md).
- **Exchange topology is settled (D16, 2026-09-19):** one **topic
  exchange per producing domain** (`request-events` today; `user-events`
  etc. when they appear), routing keys = the registry's canonical
  identity strings, pattern bindings per consumer (`request.#` here).
  This also maps cleanly onto Kafka topics, should D11's swap scenario
  ever happen.

## Connection topology: one WebSocket, everything else REST

There is exactly **one standing connection** in the whole system: the
browser's WebSocket (STOMP) session to this service's push gateway (D2),
one per logged-in browser session. Every other frontend–service
interaction — User, Supplier, Order, Credit, and this service's own
notification-list API — is stateless request/response REST carrying the
JWT per call. No other service holds connections to the frontend.

Why the push channel is centralized here rather than per service:

- **Other services get real-time delivery by riding the event
  pipeline.** A producing service publishes a fact to the broker; this
  service consumes, stores, and pushes it over the existing STOMP
  session — meeting the 5 s budget (Order NFR1.1–1.2) while the
  producer stays a purely REST-facing service with zero connection
  state.
- **Connection state is the operationally expensive part** (heartbeats,
  reconnects, session tracking, cross-instance fan-out — the D10
  scale-out question). Centralizing it means exactly one service ever
  has to solve it.
- **One channel serves any number of event sources.** Because
  publishing goes through the broker, future producers get browser push
  without new frontend connection handling (see Extensibility); the
  browser keeps one STOMP client subscribed to its per-user
  destination.

### Session mechanics (decided under issue #67)

- **Endpoint:** `/ws`, STOMP over WebSocket **with SockJS fallback** —
  native WebSocket is SockJS's first transport, so capable browsers
  pay nothing; networks/proxies that block the upgrade degrade to XHR
  transports instead of failing. Allowed handshake origins come from
  `NOTIFICATION_WS_ALLOWED_ORIGINS` (no API gateway exists, so the
  browser connects cross-origin).
- **Authentication:** the handshake/upgrade is unauthenticated; the
  STOMP `CONNECT` frame carries `Authorization: Bearer <JWT>` (HS256,
  shared `JWT_SECRET`, verified with jjwt). A channel interceptor
  validates it and sets the session principal to the token's `sub` —
  the platform user ID, the same ID in envelope `parties[]` and
  notification `recipientId`, which is what `/user/...` routing
  matches on. Invalid or missing tokens reject the CONNECT with a
  STOMP ERROR frame and the session closes.
- **Destination:** clients subscribe to `/user/queue/notifications`;
  the push listener sends each stored notification there after the
  processor's transaction commits (`AFTER_COMMIT`), synchronously —
  well inside the 5 s budget. Push is best-effort: an offline user
  misses the frame and catches up via the REST list (F3.1).
- **Heartbeats:** 10 s / 10 s both directions (server side configured
  on the simple broker).
- **Provisional client policy** (for the SPA PR — the frontend does
  not exist yet, so the client half of issue #67 is deferred):
  a single STOMP client in the shared app shell, auto-reconnect with
  exponential backoff 1 s doubling to a 30 s cap, resubscribe on
  reconnect.

Historical note: the earlier backlog's committed requester–courier chat
implied a second real-time channel (Web ↔ Order Service). The latest
backlog moved chat to nice-to-have N6; if it is ever built, choosing
its transport (own socket vs riding this service's channel) is a team
decision to make then.

## Open items (team decisions still pending)

- ~~Exact retry backoff schedule (TTL values) and maximum attempt
  count~~ — decided 2026-09-19 (10 s / 3 attempts, env-overridable);
  implemented with the DLQ under issue #65 (2026-09-20, with the
  D20/D21 implementation choices).
- ~~Exchange/queue naming convention, and how the topology is provisioned
  (declared by the application at startup via Spring AMQP vs loaded as
  broker configuration/definitions)~~ — decided 2026-09-19 (D12, D13);
  the `order-events` exchange type was settled as fanout (D14), then
  superseded by topic the same day (D16).
- ~~Exchange topology once a second producer or consumer appears~~ —
  decided 2026-09-19 (D16): per-domain topic exchanges with pattern
  bindings.
- ArchUnit test enforcing the D11 package boundary (write alongside the
  service implementation).
- ~~WebSocket session mechanics: how the JWT authenticates the STOMP
  handshake/upgrade, and the client reconnect/backoff policy —
  implementation decisions for when the push gateway is built~~ —
  decided 2026-09-19 (issue #67; see "Session mechanics" under
  Connection topology).
- Scale-out (team decision 2026-09-15): the current design is
  single-instance and includes **no Redis**. If the service is later
  scaled to multiple instances (nice-to-have N5.4, Kubernetes
  autoscaling), the team will then choose between Redis pub/sub and a
  RabbitMQ STOMP broker relay for cross-instance WebSocket fan-out.
- ~~Fold the broker/transport/engine decisions into the system-level
  diagram~~ — done 2026-09-18: [`architecture.md`](architecture.md) /
  `architecture.mmd` now show RabbitMQ, WebSocket/STOMP push, and the
  PostgreSQL notification DB (remaining engines still TBD there).
