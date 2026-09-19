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
  in foc-contracts; the tool recommended that placement when asked and
  updated this document and the Event envelope section accordingly.
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
D11 on 2026-09-18, D12–D15 on 2026-09-19) and count as the team's
finalized design for this service.

| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Message broker | **RabbitMQ** | M6; Notif F1.1, NFR1 |
| D2 | Client push transport | **WebSocket with STOMP** (Spring simple broker, per-user destinations) | Notif F1.2; Order NFR1.1–1.2 |
| D3 | Notification DB engine | **PostgreSQL**, accessed via Spring Data JPA | Notif F2.1, F2.4, F3 |
| D4 | Duplicate detection | **Unique event ID** recorded in the DB (unique constraint), written in the *same transaction* as the notification insert | Notif F2.1 |
| D5 | Stale-event discard | **Per-order sequence number** stamped by the Order Service; the processor stores the last applied sequence per order and discards events with a sequence ≤ it | Notif F2.4 |
| D6 | Retry policy | **Broker redelivery with TTL/delay-queue backoff**, fixed maximum attempts | Notif F2.2 |
| D7 | Dead-letter handling | **RabbitMQ dead-letter exchange → durable dead-letter queue**; inspected via the management UI or a consumer, replayable by re-publishing | Notif F2.3 |
| D8 | Broker persistence | **Durable exchanges/queues + persistent messages** (must be explicitly configured — durability is opt-in in RabbitMQ) | Notif NFR1.2 |
| D9 | Retention window | **Configurable via environment variable** `NOTIF_RETENTION_DAYS`, **default 30 days**; scheduled purge job | Notif F3.4 |
| D10 | Redis / scale-out | **No Redis** in the current single-instance design (see Open items) | — |
| D11 | Broker isolation | **Ports and adapters**: business logic (event processor, REST API, purge job) has zero broker imports; all RabbitMQ-specific code is confined to a single messaging adapter package behind service-owned interfaces (see "Broker decoupling") | maintainability; broker swap surface |
| D12 | Topology provisioning | **App-declared at startup via Spring AMQP**: the consuming service declares its exchange/queue/binding beans and forces the declaration when it boots (no broker definitions file) | Notif NFR1.2; topology lives beside its owner |
| D13 | Naming convention | Exchange named after the producing domain (**`order-events`**); queues prefixed with the consuming service (**`notification-service.order-events`**, later `….retry` / `….dlq`) | ownership visible in the management UI; Extensibility |
| D14 | `order-events` exchange type | **Fanout** — every bound queue gets a copy of every event; consumers filter by `type` in their own code | Notif F1.3; Extensibility ("the exchange is the broadcast point") |
| D15 | Cross-service contract names | **Shared Java library** `foc-contracts/` (top-level Maven module, plain constants, zero framework dependencies): contract names that producer and consumer must agree on — e.g. the `order-events` exchange — are compile-time constants imported by each backend service. The sole exception to the no-shared-code convention (recorded in `AGENTS.md`). Service-private names (queues) stay in their service. Issue #63 (2026-09-19) extended the library's scope to the **event-envelope contract**: the `EventEnvelope` record (a plain annotation-free record, keeping the library framework-free) and the canonical fixture `contracts/order-event.json` on its classpath, which both producer and consumer contract-test against. Tolerant reading (unknown fields/types ignored, F1.3) is consumer-side `ObjectMapper` behavior, locked in by the Notification Service's contract test — so the AMQP message converter (issue #64) must use Boot's auto-configured mapper. | one definition per contract name and per envelope field; drift caught at compile time or by the contract tests |

## Components

Ownership note — the first four rows are **broker infrastructure, not
part of the Notification Service process**: RabbitMQ is shared platform
infrastructure running as its own container (like the databases), usable
by any service. Within it, the `order-events` exchange is conceptually
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
| **order-events exchange** (RabbitMQ) | Durable exchange the Order Service publishes request-state and courier-arrival events to; publishing is fire-and-forget, so a delivery failure never affects the producing operation (F1.4). |
| **Work queue** (RabbitMQ) | Durable queue bound to the exchange; holds undelivered events across restarts (NFR1.2) and delivers them at-least-once (NFR1.1). |
| **Retry queue** (RabbitMQ) | Durable TTL/delay queue; a nacked event parks here and is re-routed to the work queue when its TTL expires, giving backoff between attempts (F2.2). |
| **Dead-letter queue** (RabbitMQ) | Durable queue fed by the dead-letter exchange after an event exhausts its maximum attempts; retained for later inspection and manual re-publish (F2.3). |
| **AMQP listener** (Spring Boot) | Consumes events with manual acknowledgement; acks only after the processor's DB transaction commits, so a crash before commit leads to redelivery, never loss (NFR1.1). |
| **Idempotent event processor** (Spring Boot) | Core logic: rejects already-seen event IDs (F2.1), discards stale per-order sequences (F2.4), accepts any event type carried by the generic envelope without publisher changes (F1.3), and creates one notification row per associated party (F1.2) — all from envelope data alone, never querying another service (F1.1). |
| **Notification REST API** (Spring Boot) | Lets the Web App list a user's recent notifications and mark them read/unread (F3.1, F3.2). |
| **STOMP push gateway** (Spring Boot) | WebSocket endpoint with Spring's STOMP simple broker; pushes each stored notification to the affected users' `/user/...` destinations within the 5-second budget (F1.2; Order NFR1.1–1.2). |
| **Retention purge scheduler** (Spring Boot) | Scheduled job deleting notifications older than the configured window (F3.4). |
| **Notification DB** (PostgreSQL) | Owned exclusively by this service (database-per-service): notification rows with read/unread state, processed event IDs, and last-applied sequence per order — one schema so dedupe, sequence update, and notification insert commit atomically. |

## Event envelope (fields required by the decisions above)

The Order Service publishes a generic envelope; each field exists
because a decision or requirement demands it. The envelope is code:
`foc.contracts.events.EventEnvelope` in `foc-contracts/`, with the
canonical example checked in as
[`foc-contracts/src/main/resources/contracts/order-event.json`](../foc-contracts/src/main/resources/contracts/order-event.json)
— the contract artifact both producer (#55) and consumer (issue #63's
contract test) assert against (D15).

| Field | Why it must be present |
| --- | --- |
| `eventId` (unique) | duplicate detection (D4, F2.1) |
| `orderId` | groups events per order (F2.4) |
| `sequence` (per order, incrementing) | stale-event discard (D5, F2.4) |
| `type` (the six request states: created / accepted / collected / completed / cancelled / expired — plus `courier-arrived` for the dropoff-arrival update) | Order F0.2 (state transitions), Order F4.1.1–F4.1.2 (arrival); new types addable without publisher changes (F1.3) |
| `occurredAt` timestamp | notification display and audit |
| `requesterId`, `courierId` (party user IDs) | notify each party without querying other services (F1.1, F1.2) |
| `payload` (free-form details) | message text rendering; generic per F1.3 |

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
| Notif F1.1 — notify on events without querying the producing service | envelope carries all needed data (party IDs, type, payload); processor reads only the envelope + own DB |
| Notif F1.2 — notify each party of an event | processor creates one notification per party ID in the envelope; push gateway targets each party's per-user destination |
| Notif F1.3 — new event types without publisher changes | generic envelope (`type` + `payload`); processor handles unknown types generically |
| Notif F1.4 — delivery failure never affects the producing operation | fire-and-forget publish to the exchange; all retry/failure handling stays on the consumer side of the broker |
| Notif F2.1 — no duplicate notification on redelivery | unique event-ID constraint checked in the same DB transaction as the notification insert (D4) |
| Notif F2.2 — retry failed deliveries | nack → TTL retry queue → redelivery with backoff, up to max attempts (D6) |
| Notif F2.3 — record events that exhaust retries | dead-letter exchange routes them to the durable DLQ for inspection/re-publish (D7) |
| Notif F2.4 — discard events older than the last applied for the same order | per-order sequence check against the stored last-applied sequence (D5) |
| Notif F3.1 — view recent notifications in-app | Notification REST API + stored rows |
| Notif F3.2 — mark read/unread | read/unread flag on the notification row, toggled via the REST API |
| Notif F3.4 — retention window | purge scheduler, `NOTIF_RETENTION_DAYS` (default 30) |
| Notif NFR1.1 — at-least-once delivery | durable queue + manual ack after transaction commit; unacked events are redelivered |
| Notif NFR1.2 — persist undelivered events across restarts | durable exchanges/queues + persistent messages (D8) |
| Order F0.2 — event on every request state transition | Order Service publishes all six state-transition events to the exchange |
| Order F4.1.1–F4.1.2 — requester updated on collection and on courier arrival at the dropoff | `collected` state event plus the `courier-arrived` event, delivered through the same pipeline and pushed to the requester |
| Order NFR1.1–1.2 — requester and assigned courier see every state change within 5 s | broker push path end-to-end: consume → process → STOMP push, no polling |
| M6 — meaningful async workflow | the entire order-events → broker → notification pipeline |

## Configuration notes

- `NOTIF_RETENTION_DAYS` — retention window for stored notifications;
  default **30** (D9).
- Retry TTL values (backoff schedule) and the **maximum attempt count**
  are configuration values; exact numbers are still a team decision
  (see Open items).
- RabbitMQ durability is **opt-in**: exchanges and queues must be
  declared durable and messages published persistent, or NFR1.2 is
  silently violated.
- RabbitMQ runs as its own container in `compose.yaml` with a named
  volume for its data directory (M7).

## Broker decoupling (D11)

Goal: a broker swap (e.g. RabbitMQ → Kafka) touches one adapter package
and `compose.yaml`, never the business logic.

- **Ports (service-owned interfaces):** inbound, the AMQP listener is a
  thin adapter that deserializes, calls
  `eventProcessor.process(EventEnvelope)`, and acks/nacks on the result;
  outbound (Order Service side), publishing goes through an
  `EventPublisher.publish(EventEnvelope)` interface with the RabbitMQ
  implementation as one class. The processor, REST API, and purge job
  import nothing from `org.springframework.amqp` / `com.rabbitmq`.
- **Enforcement:** broker code lives in its own package (e.g.
  `messaging.rabbitmq`); an ArchUnit test can assert no other package
  imports broker types (not yet written — see Open items).
- **Envelope stays broker-agnostic:** all business data (event ID,
  sequence, parties, type, payload) rides in the JSON body — never in
  AMQP headers or other broker-specific message properties.
- **Already portable by construction:** duplicate detection (D4) and
  stale-event discard (D5) are enforced in this service's own database,
  not by broker features, so the logic satisfying F2.1/F2.4 is unchanged
  by any broker swap. The design assumes only the weakest common
  guarantee — events may arrive twice, late, or out of order — which
  every mainstream broker meets.
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
  consumer never blocks another), its own processed-event-ID inbox and
  sequence tracking (redelivery is per queue, so dedupe state cannot be
  shared), and its own backlog (a slow consumer affects nobody else).
- **The envelope becomes a public contract** once a second consumer
  exists: evolve it additively (add fields, never rename or repurpose),
  require consumers to ignore unknown fields and event types (this
  service already does, per F1.3), and publish domain facts rather than
  the producer's internal structures. A platform-wide shape would
  generalize the order-specific fields to `aggregateType` +
  `aggregateId` + per-aggregate `sequence` + `parties[]`.
- **Events are facts, not commands.** Calls whose caller needs the
  result — e.g. Order → Credit reserve/transfer (Credit F2.1.3, F3.1) —
  stay synchronous REST; the broker carries only "this happened"
  notifications. This is the existing system-level boundary in
  [`architecture.md`](architecture.md).
- **Exchange topology is deliberately open** until a second producer or
  consumer actually appears: one exchange per producing domain
  (`order-events`, `user-events`, …) vs a single topic exchange with
  routing keys (`order.request.accepted`) and pattern bindings. Both
  map cleanly onto the current design (and onto Kafka topics, should
  D11's swap scenario ever happen).

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

Historical note: the earlier backlog's committed requester–courier chat
implied a second real-time channel (Web ↔ Order Service). The latest
backlog moved chat to nice-to-have N6; if it is ever built, choosing
its transport (own socket vs riding this service's channel) is a team
decision to make then.

## Open items (team decisions still pending)

- Exact retry backoff schedule (TTL values) and maximum attempt count.
- ~~Exchange/queue naming convention, and how the topology is provisioned
  (declared by the application at startup via Spring AMQP vs loaded as
  broker configuration/definitions)~~ — decided 2026-09-19 (D12, D13);
  the `order-events` exchange type is also settled as fanout (D14).
- Exchange topology once a second producer or consumer appears:
  per-domain exchanges vs a single topic exchange with routing keys
  (see Extensibility notes).
- ArchUnit test enforcing the D11 package boundary (write alongside the
  service implementation).
- WebSocket session mechanics: how the JWT authenticates the STOMP
  handshake/upgrade, and the client reconnect/backoff policy —
  implementation decisions for when the push gateway is built.
- Scale-out (team decision 2026-09-15): the current design is
  single-instance and includes **no Redis**. If the service is later
  scaled to multiple instances (nice-to-have N5.4, Kubernetes
  autoscaling), the team will then choose between Redis pub/sub and a
  RabbitMQ STOMP broker relay for cross-instance WebSocket fan-out.
- ~~Fold the broker/transport/engine decisions into the system-level
  diagram~~ — done 2026-09-18: [`architecture.md`](architecture.md) /
  `architecture.mmd` now show RabbitMQ, WebSocket/STOMP push, and the
  PostgreSQL notification DB (remaining engines still TBD there).
