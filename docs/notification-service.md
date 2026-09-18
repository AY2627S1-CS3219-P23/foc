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
  to the existing generic-envelope design. No architecture, technology,
  or trade-off decisions were made by the tool.
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

All decisions below were made by Leong Wei Zhi on 2026-09-15 and count
as the team's finalized design for this service.

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

## Components

| Component | Responsibility |
| --- | --- |
| **order-events exchange** (RabbitMQ) | Durable exchange the Order Service publishes order-status events to; publishing is fire-and-forget, so a delivery failure never affects the producing operation (F1.4). |
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
because a decision or requirement demands it:

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

## Open items (team decisions still pending)

- Exact retry backoff schedule (TTL values) and maximum attempt count.
- Exchange/queue naming convention.
- Scale-out (team decision 2026-09-15): the current design is
  single-instance and includes **no Redis**. If the service is later
  scaled to multiple instances (nice-to-have N5.4, Kubernetes
  autoscaling), the team will then choose between Redis pub/sub and a
  RabbitMQ STOMP broker relay for cross-instance WebSocket fan-out.
- The system-level diagram ([`architecture.md`](architecture.md)) still
  marks broker/transport/engine as TBD on `main`; fold these decisions
  into it once the outstanding docs branches merge.
