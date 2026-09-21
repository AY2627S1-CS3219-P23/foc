<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-15 – 2026-09-21.
  Scope: transcribed and maintained the team-decided notification-service
  design in this document and the companion diagram. Every design decision
  (D1–D22) was made by a team member — recorded per decision in the
  Decisions table, chosen via neutral options Q&As where AI assisted — and
  the decision-by-decision history lives in ai/usage-log.md and git
  history. The tool documented decisions, derived the traceability
  mapping, and formatted the result; it made no design decisions.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# FoC Notification Service — Architecture Design

**Scope:** component-level design of `notification-service/`, covering
every D1 Notification requirement (F1 event delivery, F2 delivery
reliability, F3 notification retrieval, NFR1 reliability) plus the
Order-side requirements it serves: events on every request state
transition (Order F0.2), requester updates on collection and courier
arrival (Order F4.1.1–F4.1.2), the state-change latency budget
(Order NFR1.1–1.2), and the course's mandatory async workflow (M6).

Companion diagram: [`notification-service.mmd`](notification-service.mmd)
(GitHub renders it in the file view). System-wide context:
[`architecture.md`](architecture.md).

## Design decisions (made by the team)

All decisions were made by Leong Wei Zhi (D1–D10 on 2026-09-15, D11 on
2026-09-18, D12–D19 on 2026-09-19, D20–D22 on 2026-09-20) and count as
the team's finalized design for this service. Superseded decisions
(D5, D14) have been removed; gaps in the numbering are intentional.

| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Message broker | **RabbitMQ** | M6; Notif F1.1, NFR1 |
| D2 | Client push transport | **WebSocket with STOMP** (Spring simple broker, per-user destinations) | Notif F1.2; Order NFR1.1–1.2 |
| D3 | Notification DB engine | **PostgreSQL**, accessed via Spring Data JPA | Notif F2.1, F3 |
| D4 | Duplicate detection | **Unique event ID** recorded in the DB, in the *same transaction* as the notification insert | Notif F2.1 |
| D6 | Retry policy | **Broker redelivery with TTL retry queue**: 3 total attempts, 10 s backoff, env-overridable — see [Failure handling](#failure-handling-d6d7-d20d21) | Notif F2.2 |
| D7 | Dead-letter handling | **Durable dead-letter queue**; unknown event types dead-letter (replayable after a consumer upgrade) rather than being dropped | Notif F2.3 |
| D8 | Broker persistence | **Durable exchanges/queues + persistent messages** (durability is opt-in in RabbitMQ) | Notif NFR1.2 |
| D9 | Retention window | `NOTIF_RETENTION_DAYS` env var, **default 30 days**; scheduled purge job | Notif F3.4 |
| D10 | Redis / scale-out | **No Redis** in the single-instance design (see Open items) | — |
| D11 | Broker isolation | **Ports and adapters**: all RabbitMQ code confined to one adapter package — see [Broker decoupling](#broker-decoupling-d11) | broker swap surface |
| D12 | Topology provisioning | **App-declared at startup** via Spring AMQP beans (no broker definitions file) | topology lives beside its owner |
| D13 | Naming convention | Exchange named after the producing domain (`request-events`); queues prefixed with the consuming service (`notification-service.request-events` + `.retry`/`.dlq`) | ownership visible in the management UI |
| D15 | Cross-service contract names | **Shared Java library** `foc-contracts/` (framework-free Maven module) — the sole exception to the no-shared-code convention. Holds the typed event contracts and canonical fixtures; see [Event contracts](#event-contracts-d17d18) | drift caught at compile time / by contract tests |
| D16 | Exchange type | **Topic exchange**, one per producing domain; canonical routing keys, this service binds `request.#` | Notif F1.1–F1.3; Extensibility |
| D17 | Event contract shape | **Flat typed event records, no envelope** — one record per request-lifecycle fact; see [Event contracts](#event-contracts-d17d18) | schema per event |
| D18 | Event identity & dispatch | **One canonical identity string per event** (e.g. `request.accepted`), registered once in `EventTypeRegistry`; consumers dispatch on the body `eventType`. A breaking contract change ships as a new event type | Notif F1.3; portability |
| D19 | Idempotency scope | **Event-ID dedupe only** — the per-entity sequence stale-discard mechanism was removed and requirement F2.4 retired; out-of-order deliveries each notify | Notif F2.1 |
| D20 | Dead-letter exchange form | **The default exchange (`""`)** serves as the dead-letter exchange on both legs — see [Failure handling](#failure-handling-d6d7-d20d21) | minimal topology |
| D21 | Unconvertible-message path | **Dead-letter on first rejection**, skipping the retry queue — retrying a message that cannot convert could never succeed | Notif F2.3 |
| D22 | Event vocabulary | **`request` replaces `order`** across classes, routing keys, exchange and queue names, and fixtures; `foc-contracts` split into `events.core` / `events.request` packages. A one-time pre-production break (no producer existed yet) | consistency with the D1 backlog |

## Components

> ℹ️ Ownership note — the first four rows are **broker infrastructure,
> not part of the Notification Service process**. RabbitMQ is shared
> platform infrastructure in its own container; the `request-events`
> exchange is conceptually the *Order Service's* publishing surface,
> while the work/retry/dead-letter queues are broker-hosted resources
> dedicated to this service as their sole consumer. The service's
> durable state lives only in its PostgreSQL database — the broker holds
> in-flight messages, not service state, so the database-per-service
> rule is untouched.

| Component | Responsibility |
| --- | --- |
| **request-events exchange** (RabbitMQ) | Durable **topic** exchange (D16) the Order Service publishes request events to under canonical routing keys; publishing is fire-and-forget (F1.4). |
| **Work queue** (RabbitMQ) | Durable queue bound with `request.#`; holds undelivered events across restarts (NFR1.2), delivers at-least-once (NFR1.1). |
| **Retry queue** (RabbitMQ) | Durable TTL queue; parks a failed event while attempts remain, returns it to the work queue on TTL expiry (F2.2). |
| **Dead-letter queue** (RabbitMQ) | Durable queue for events that exhausted max attempts — or couldn't convert (D21); retained for inspection and manual re-publish (F2.3). |
| **AMQP listener** (Spring Boot) | Consumes with manual acknowledgement; acks only after the processor's DB transaction commits (NFR1.1). |
| **Idempotent event processor** (Spring Boot) | Core logic: rejects already-seen event IDs (F2.1), creates one notification row per party (F1.2) from the typed event alone — never querying another service (F1.1). Works against the `DomainEvent` interface, so every cataloged event type flows through generically. |
| **Notification REST API** (Spring Boot) | *Planned — issue #66.* Will let the Web App list a user's recent notifications and mark them read/unread (F3.1, F3.2). |
| **STOMP push gateway** (Spring Boot) | WebSocket endpoint with Spring's STOMP simple broker; pushes each stored notification to the affected users' `/user/...` destinations within the 5-second budget (F1.2; Order NFR1.1–1.2). |
| **Retention purge scheduler** (Spring Boot) | Scheduled job deleting notifications older than the configured window (F3.4). |
| **Notification DB** (PostgreSQL) | Owned exclusively by this service: notification rows with read/unread state plus processed event IDs — one schema so dedupe and notification insert commit atomically. |

## Event contracts (D17/D18)

The Order Service publishes **explicit, business-specific typed
events** — one flat record per fact, no envelope wrapper. The contracts
are code in `foc-contracts/`: the `DomainEvent`/`RequestEvent`
interfaces, the seven records, the `EventTypeRegistry`, and one
canonical fixture per event under
[`foc-contracts/src/main/resources/contracts/`](../foc-contracts/src/main/resources/contracts/),
which both producer and consumer contract-test against (D15). The full
conventions — naming, wire shape, evolution rules — live in the
["Event conventions" section of the foc-contracts README](../foc-contracts/README.md#event-conventions);
the add-a-new-event checklist and producer conventions are in
[`foc-contracts/AGENTS.md`](../foc-contracts/AGENTS.md).

Wire metadata carried by every event (top-level, alongside the event's
business fields):

| Field | Why it must be present |
| --- | --- |
| `eventId` (unique) | duplicate detection (D4, F2.1) |
| `eventType` (canonical identity, e.g. `request.accepted`) | consumer dispatch to the record class; self-describing, transport-portable bodies (D18) |
| `occurredAt` timestamp | notification display and audit |
| `producer` (e.g. `order-service`) | provenance/audit |
| `correlationId` | correlates the event with the request/flow that caused it |
| `parties` (user IDs to notify) | one notification per entry, without querying other services (F1.1, F1.2) |

Identity mechanics (D18): the identity string travels twice by design —
as the body `eventType`, which consumers **dispatch on** (D11's
body-only rule; no `__TypeId__` headers), and as the RabbitMQ routing
key (transport metadata). `eventType` is *not* a record component:
`DomainEvent.eventType()` derives it from the registry and the message
converter injects it on publish, so an instance can never carry a
mismatched type. Consumers reject unknown types and events missing
required fields (components not marked `@Nullable`); a **breaking
contract change ships as a new event type** (e.g.
`request.accepted.v2`).

The seven cataloged events (Order F0.2 state transitions plus the
courier-arrival update, F4.1.1–F4.1.2): `request.created`,
`request.accepted`, `request.collected`, `request.completed`,
`request.cancelled`, `request.expired`, `request.courier-arrived`. All
carry `requestId`, `requesterId`, `pickupLocation`, `dropoffLocation`,
`note`; the post-acceptance events add `courierId` (nullable on
`request.cancelled` — a pre-acceptance cancel has no courier). Adding
an event type is a contracts release (record + registry entry +
fixture) plus a consumer jar bump — the `request.#` binding never
changes (D16).

## Failure handling (D6/D7, D20/D21)

- **Retry (D6):** on a processing failure the listener republishes the
  event to the TTL retry queue while attempts remain; the queue's
  dead-letter leg returns it to the work queue when the TTL expires.
  **3 total attempts, 10 s backoff**, overridable via
  `NOTIFICATION_RETRY_MAX_ATTEMPTS` / `NOTIFICATION_RETRY_TTL_MS`.
  Attempts ride in a listener-stamped `x-retry-attempts` header —
  RabbitMQ 4 resets its own `x-death` count on client republish
  (verified against a real broker).
- **Dead-lettering (D7/D20):** after max attempts, the work queue's
  dead-letter leg routes the event to the durable DLQ; inspect and
  re-publish via the management UI. Both legs use the **default
  exchange (`""`)** as their dead-letter exchange with
  `x-dead-letter-routing-key` = the target queue name — zero extra
  exchanges to declare. The routing key is rewritten in transit, but
  nothing is lost: the body `eventType` *is* the original key (D18) and
  `x-death` records it too.
- **Unconvertible messages (D21):** a message the converter rejects
  (unknown `eventType`, malformed body, missing required field)
  dead-letters intact on first rejection, skipping the retry queue;
  replay happens from the DLQ after a consumer upgrade. Falls out of
  the container's rejection plus the dead-letter leg — no custom
  error-handler code.

## Diagram legend

The companion diagram uses the same notation as
[`architecture.md`](architecture.md#legend-notation): rectangles for
components, hexagon/double-bordered shapes for broker
exchange/queues, cylinders for service-owned databases, `-->` for
synchronous calls, `==>` for async message flow, `---` for JPA access.
A dashed node is planned, not yet built. Requirement/decision IDs are
not drawn on the diagram — the traceability table below maps them.

## Requirement traceability

| Requirement | Satisfied by |
| --- | --- |
| Notif F1.1 — notify on events without querying the producing service | each typed event carries all needed data; processor reads only the event + own DB |
| Notif F1.2 — notify each party of an event | one notification per entry in the event's `parties`; push gateway targets each party's per-user destination |
| Notif F1.3 — new event types without publisher changes | a new event type is a contracts release consumed via a jar bump (registry-driven converter, `DomainEvent`-generic processor, unchanged `request.#` binding). Unknown *fields* are tolerated; an unknown *type* dead-letters intact for replay after a consumer upgrade (D7/D21) |
| Notif F1.4 — delivery failure never affects the producing operation | fire-and-forget publish; all retry/failure handling stays on the consumer side of the broker |
| Notif F2.1 — no duplicate notification on redelivery | unique event-ID constraint checked in the same DB transaction as the notification insert (D4) |
| Notif F2.2 — retry failed deliveries | listener republish → TTL retry queue → redelivery with backoff, up to max attempts (D6/D20) |
| Notif F2.3 — record events that exhaust retries | durable DLQ for inspection/re-publish (D7/D20); unconvertible messages arrive on first rejection (D21) |
| Notif F3.1 — view recent notifications in-app | Notification REST API (planned, issue #66) + stored rows |
| Notif F3.2 — mark read/unread | read/unread flag on the notification row, toggled via the REST API (planned, issue #66) |
| Notif F3.4 — retention window | purge scheduler, `NOTIF_RETENTION_DAYS` (default 30) |
| Notif NFR1.1 — at-least-once delivery | durable queue + manual ack after transaction commit |
| Notif NFR1.2 — persist undelivered events across restarts | durable exchanges/queues + persistent messages (D8) |
| Order F0.2 — event on every request state transition | Order Service publishes all six state-transition events to the exchange |
| Order F4.1.1–F4.1.2 — requester updated on collection and courier arrival | `collected` state event plus the `courier-arrived` event, pushed to the requester through the same pipeline |
| Order NFR1.1–1.2 — state changes visible within 5 s | broker push path end-to-end: consume → process → STOMP push, no polling |
| M6 — meaningful async workflow | the entire request-events → broker → notification pipeline |

> ℹ️ F2.4 (stale-event discard) was retired by D19 and no longer
> appears in the backlog trace.

## Configuration notes

- `NOTIF_RETENTION_DAYS` — retention window, default **30** (D9).
- `NOTIF_PURGE_CRON` — purge schedule, default daily at 03:00.
- `NOTIFICATION_RETRY_TTL_MS` / `NOTIFICATION_RETRY_MAX_ATTEMPTS` —
  retry backoff and attempt cap (D6), defaults **10 s / 3**.

> 🔴 The retry TTL is baked into the retry queue's arguments at
> declaration: changing it against a broker that already holds the
> queue fails (`PRECONDITION_FAILED`) until the queue is deleted or the
> volume reset. Max attempts is read by the listener and only needs a
> service restart.

- RabbitMQ durability is **opt-in**: exchanges and queues must be
  declared durable and messages published persistent, or NFR1.2 is
  silently violated.
- RabbitMQ runs as its own container in `compose.yaml` with a named
  volume (M7).
- Dev volumes created before the Method-B refactor or issue #65 need a
  one-time reset; the pre-D22 `order-events` broker entities are merely
  orphans to delete — see the
  [service README's Troubleshooting section](../notification-service/README.md#troubleshooting).

## Broker decoupling (D11)

Goal: a broker swap (e.g. RabbitMQ → Kafka) touches one adapter package
and `compose.yaml`, never the business logic.

- **Ports (service-owned interfaces):** inbound, the AMQP listener is a
  thin adapter — the message converter dispatches on the body
  `eventType` via the registry (D18), the listener calls
  `eventProcessor.process(DomainEvent)` and acks/nacks on the result;
  outbound (Order Service side), publishing goes through an
  `EventPublisher.publish(DomainEvent)` interface with the RabbitMQ
  implementation as one class (see the producer conventions in
  `foc-contracts/AGENTS.md`). The processor, REST API, and purge job import
  nothing from `org.springframework.amqp` / `com.rabbitmq`.
- **Enforcement:** broker code lives in `messaging.rabbitmq`; the
  ArchUnit test `ArchitectureTest` (issue #69) asserts no other package
  depends on broker types, and runs with the normal test suite.
- **Events stay broker-agnostic:** every domain-event field — metadata
  and business fields, including the canonical `eventType` — rides in
  the JSON body, never in AMQP headers (D11/D18). The routing key
  duplicates the identity for RabbitMQ delivery only; the body alone is
  self-describing. The one deliberate AMQP header, `x-retry-attempts`
  (see Failure handling), is transport-side retry accounting stamped by
  the adapter, not event data — it belongs to the RabbitMQ retry
  mechanism and would be re-implemented along with it in a broker swap.
- **Already portable by construction:** duplicate detection (D4) lives
  in this service's own database, not in broker features. The design
  assumes only the weakest common guarantee — events may arrive twice
  or late — which every mainstream broker meets.
- **Known non-portable surface (accepted trade-off):** the retry and
  dead-letter topology (D6/D7) and per-message ack/nack are RabbitMQ
  mechanisms, treated as part of the adapter. The team considered an
  application-level alternative (a `failed_events` table plus a retry
  scheduler, fully broker-portable) and kept the broker-native design:
  battle-tested, no custom code, at the cost that a broker swap must
  re-implement retry/dead-lettering in the new broker's idiom.
- **Testing follows the port:** the processor is unit-tested by passing
  typed events to the interface directly (no broker); the
  `RequestEventsRabbitMqIntegrationTest` Testcontainers test exercises
  the full RabbitMQ adapter path including retry recovery and
  dead-lettering.

## Extensibility: future producers and consumers

Nothing in the committed D1 scope needs more than one producer (Order)
and one consumer (this service); these notes record how the design
extends without rework. The most plausible next consumer in the backlog
is centralized logging (nice-to-have N4).

- **New consumers are free for the publisher.** A new consuming service
  gets **its own durable queue** bound to the existing exchange;
  RabbitMQ delivers a copy of each event to every bound queue.
- **One queue per consuming service.** Consumers on the same queue
  *compete* for messages, so two services must never share a queue.
  Multiple instances of the *same* service do share its queue — that
  competition is the desired load balancing for scale-out.
- **Each consumer brings its own reliability machinery:** its own
  retry queue and DLQ, its own processed-event-ID inbox, its own
  backlog, and its own (possibly narrower) bindings.
- **The contracts library is already the public contract.** The typed
  records evolve additively only (add `@Nullable` fields, never rename
  or repurpose); a breaking change ships as a new event type — see the
  foc-contracts README. A new producing domain adds its own event
  records, registry entries and exchange.
- **Events are facts, not commands.** Calls whose caller needs the
  result — e.g. Order → Credit reserve/transfer — stay synchronous
  REST; the broker carries only "this happened" notifications.
- **Exchange topology is settled (D16):** one topic exchange per
  producing domain (`request-events` today), routing keys = the
  registry's canonical identities, pattern bindings per consumer. This
  also maps cleanly onto Kafka topics, should D11's swap scenario ever
  happen.

## Connection topology: one WebSocket, everything else REST

There is exactly **one standing connection** in the whole system: the
browser's WebSocket (STOMP) session to this service's push gateway
(D2), one per logged-in browser session. Every other frontend–service
interaction — including this service's own notification-list API — is
stateless request/response REST carrying the JWT per call.

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
  without new frontend connection handling; the browser keeps one STOMP
  client subscribed to its per-user destination.

### Session mechanics (decided under issue #67)

- **Endpoint:** `/ws`, STOMP over WebSocket **with SockJS fallback** —
  native WebSocket is SockJS's first transport, so capable browsers pay
  nothing; networks that block the upgrade degrade to XHR transports.
  Allowed handshake origins come from `NOTIFICATION_WS_ALLOWED_ORIGINS`
  (no API gateway exists, so the browser connects cross-origin).
- **Authentication:** the handshake/upgrade is unauthenticated; the
  STOMP `CONNECT` frame carries `Authorization: Bearer <JWT>` (HS256,
  shared `JWT_SECRET`, verified with jjwt). A channel interceptor
  validates it and sets the session principal to the token's `sub` —
  the platform user ID, the same ID in event `parties[]` and
  notification `recipientId`, which is what `/user/...` routing matches
  on. Invalid or missing tokens reject the CONNECT with a STOMP ERROR
  frame and the session closes.
- **Destination:** clients subscribe to `/user/queue/notifications`;
  the push listener sends each stored notification there after the
  processor's transaction commits (`AFTER_COMMIT`) — well inside the
  5 s budget. Push is best-effort: an offline user misses the frame and
  catches up via the REST list (F3.1).
- **Heartbeats:** 10 s / 10 s both directions.
- **Provisional client policy** (the SPA does not exist yet, so the
  client half of issue #67 is deferred): a single STOMP client in the
  shared app shell, auto-reconnect with exponential backoff 1 s
  doubling to a 30 s cap, resubscribe on reconnect.

> ✍️ Historical note: the earlier backlog's committed requester–courier
> chat implied a second real-time channel. The latest backlog moved
> chat to nice-to-have N6; if it is ever built, choosing its transport
> (own socket vs riding this service's channel) is a team decision to
> make then.

## Open items (team decisions still pending)

- **Scale-out** (team decision 2026-09-15): the current design is
  single-instance and includes **no Redis**. If the service is later
  scaled to multiple instances (nice-to-have N5.4), the team will then
  choose between Redis pub/sub and a RabbitMQ STOMP broker relay for
  cross-instance WebSocket fan-out.
