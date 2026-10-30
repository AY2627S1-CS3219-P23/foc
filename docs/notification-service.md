<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-15 – 2026-09-21.
  Scope: transcribed and maintained the team-decided notification-service
  design in this document and the companion diagram. Every design decision
  (D1–D32) was made by a team member — recorded per decision in the
  Decisions table, chosen via neutral options Q&As where AI assisted — and
  the decision-by-decision history lives in ai/usage-log.md and git
  history. The tool documented decisions, derived the traceability
  mapping, and formatted the result; it made no design decisions.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-09, Claude Code (Opus 5.5), PR #163 review (Leong Wei Zhi): the catalog
  description, the events-are-facts paragraph and the exchange list
  updated for the Credit saga events (team design,
  docs/credit-service.md D7-D8). Author review: Ryan Ang, pending pull
  request review.
  2026-10-09, Claude Code (Opus 5.5): note recorded as nullable on the
  request events (author's decision, issue #165).
  2026-10-11, Claude Code (Fable 5), issue #151: dated note on D10 — a
  platform Redis now exists for the logout denylist (docs/gateway.md);
  D10's scale-out question is unchanged and stays open.
  2026-10-12, Claude Code (Fable 5), issue #67: D23 added (CONNECT-time
  denylist read — author decision via options Q&A), the D10 note
  extended, the stale "no API gateway" wording in Session mechanics
  fixed, and the provisional client policy rewritten as implemented.
  2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): D23
  extended to the per-user reset cutoff (G11) and its CONNECT-only
  timing recorded as an accepted limit.
  2026-10-16, Claude Code (Fable 5), PR #185 re-review (@Sinnez1):
  Session mechanics updated for the interceptor's frame policing (STOMP
  frame authenticated like CONNECT, SEND rejected, SUBSCRIBE limited to
  the one destination) and the ERROR-frame wording softened.
  2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1): the
  client policy's ERROR-frame handling rewritten — bounded retry then a
  reported stop, chosen by Leong Wei Zhi from four options via neutral
  options Q&A.
  2026-10-17, Claude Code (Fable 5), issue #66: D24–D29 added (REST API
  contract, list order, write mechanism, paging/filter, REST filter
  authorities, event display names — all author decisions via neutral
  options Q&A), the REST API component/traceability rows flipped from
  planned to built, the "REST API contract" section added transcribing
  those decisions, and the event catalog updated for D29's name fields.
  2026-10-17, Claude Code (Fable 5), issue #66 (same day, later): the
  author reopened D29 via neutral options Q&A and superseded it with
  D30/D31 — IDs-only events, names resolved by this service at ingest
  with a zero-trust internal credential to user-service. D29 removed
  (superseded-decision convention, like D5/D14), the catalog and F1.1
  note updated, "Display-name enrichment" section added.
  2026-10-17, Claude Code (Fable 5), issue #137: D9/D4 rows, the
  retention-scheduler component row, the F3.4 trace and the
  configuration notes updated for the processed_events sweep (author
  decisions via neutral options Q&A: same 30-day window, processed_at
  indexed).
  2026-10-17, Claude Code (Fable 5), PR #201 Codex review: the D4
  trade-off note names the unacked work-queue redelivery path alongside
  DLQ replay (same accepted ≥30-day-outage class; no design change).
  2026-10-19, Claude Code (Fable 5), issue #221: the author reopened the
  display-name design via neutral options Q&A and superseded D30/D31
  with D32 — read-time hydration in the web (per-id lookups through the
  gateway with the caller's own JWT, SPA-side TTL cache); the ingest
  lookup, its adapter and the internal token are removed. D30/D31 rows
  removed (superseded-decision convention), the enrichment section
  rewritten as "Display names (D32)", catalog/config notes updated.
  2026-10-21, Claude Code (Opus 5.5), issue #234: event catalog notes
  extended with the two N3 dispute events and the rejection's nullable
  reasonCode (team decisions DSP-12, D18); author review: Ryan Ang,
  pending pull request review.
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
2026-09-18, D12–D19 on 2026-09-19, D20–D22 on 2026-09-20, D23 on
2026-10-12, D24–D31 on 2026-10-17, D32 on 2026-10-19) and count as
the team's finalized design for this service. Superseded decisions
(D5, D14, D29 — display names in events, reversed the same day in
favor of D30 — and D30/D31 themselves, the ingest-time lookup and its
internal token, superseded by D32's read-time hydration) have been
removed; gaps in the numbering are intentional.

| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Message broker | **RabbitMQ** | M6; Notif F1.1, NFR1 |
| D2 | Client push transport | **WebSocket with STOMP** (Spring simple broker, per-user destinations) | Notif F1.2; Order NFR1.1–1.2 |
| D3 | Notification DB engine | **PostgreSQL**, accessed via Spring Data JPA | Notif F2.1, F3 |
| D4 | Duplicate detection | **Unique event ID** recorded in the DB, in the *same transaction* as the notification insert. *2026-10-17 note (issue #137):* dedupe rows past the D9 retention window are purged, reopening the dedupe window for those event IDs — accepted: retries are 10 s TTL × 3 then DLQ, so re-processing needs a delivery resurfacing ≥ 30 days later (a manual DLQ replay, or a post-commit delivery left unacked on the work queue across an outage that long), and it would recreate notifications the purge already deleted | Notif F2.1 |
| D6 | Retry policy | **Broker redelivery with TTL retry queue**: 3 total attempts, 10 s backoff, env-overridable — see [Failure handling](#failure-handling-d6d7-d20d21) | Notif F2.2 |
| D7 | Dead-letter handling | **Durable dead-letter queue**; unknown event types dead-letter (replayable after a consumer upgrade) rather than being dropped | Notif F2.3 |
| D8 | Broker persistence | **Durable exchanges/queues + persistent messages** (durability is opt-in in RabbitMQ) | Notif NFR1.2 |
| D9 | Retention window | `NOTIF_RETENTION_DAYS` env var, **default 30 days**; scheduled purge job. *2026-10-17 (issue #137):* the same run and window also sweep the `processed_events` dedupe table | Notif F3.4 |
| D10 | Redis / scale-out | **No Redis** in the single-instance design (see Open items). *2026-10-11 note:* a platform Redis now exists for the **logout token denylist** ([`gateway.md`](gateway.md), issue #151) — unrelated to this service's scale-out question, which stays open. *2026-10-12 note:* this service now **reads** that denylist at STOMP CONNECT (D23); the scale-out question is still open | — |
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
| D23 | WebSocket revocation check | **This service makes the gateway's denylist lookups itself at STOMP CONNECT** — the token travels in the CONNECT frame, which never reaches the gateway's access phase. Both key shapes: `EXISTS denylist:<jti>` for a logged-out token (G10) and the account's `denylist:user:<id>` cutoff stamp, which refuses a token whose `iat` is strictly before it (G11, a password reset). A token missing the claim a lookup needs skips that lookup, and a Redis failure **fails open** behind finite 2 s timeouts (G6 semantics). The check runs at CONNECT only (see Session mechanics). A deliberate, narrow exception to gateway decision G4 | closes [`gateway.md`](gateway.md)'s logged-out-socket known limit |
| D24 | REST API surface | **`/notifications/me` base path** (caller = JWT `sub`, no user ID in any route, as credit-service's `/credits/me`): paged `GET` list, `PATCH /{id}` with body `{"read": true\|false}` → 204, `POST /read-all` → 204, `GET /unread-count` → `{"count": n}` — see [REST API contract](#rest-api-contract-d24d28) | Notif F3.1, F3.2 |
| D25 | List order | **`created_at` newest-first, ID descending as tiebreaker** — a late-delivered event surfaces at the top, where a catch-up reader expects it; no sort parameter | Notif F3.1 |
| D26 | Read-flag write mechanism | **One ownership-scoped bulk `UPDATE`, row count checked**: 0 rows → 404, so a foreign ID and a missing ID are indistinguishable (no existence oracle); counting matched rows makes repeats idempotent | Notif F3.2 |
| D27 | List paging & filter | **Hand-validated `page`/`size`** (defaults 0/20, cap 100, out-of-range → 400 — credit-service's anti-`Pageable` stance) plus an optional **`unread=true` filter**, bound as a primitive `boolean` — deliberately as lenient as Spring's standard conversion (`1`/`yes`/`on` and `0`/`no`/`off` alongside `true`/`false`), not hand-validated like `page`/`size` | Notif F3.1 |
| D28 | REST filter authorities | **Empty authorities**: the JWT filter authenticates the subject only — this service's verifier exposes no role claim and no route is role-gated; the denylist is NOT re-checked on REST (gateway decision G4; D23 stays the STOMP-only exception) | least change |
| D32 | Display names (supersedes D30/D31) | **Events and stored payloads carry party IDs only; the web resolves `courierId` → username at read time** (gateway `GET /api/user/users/{id}` with the caller's own JWT, SPA-side cache: 10 min positive / 30 s negative TTL, in-flight dedupe) — no service-to-service call remains, the internal-token machinery is gone, F1.1 holds unamended, renames show current names (D30's ingest snapshot went stale), and a failed lookup renders the actor-less copy — see [Display names](#display-names-d32) | Notif F3.1 UX |
| D33 | Dropped-courier prune (issue #266) | **Ingesting `request.dropped` deletes the dropping courier's stored rows for that request**, in the same transaction as the dedupe insert — the drop removes them as a party (order-service nulls `courierId`), so every row they held (`request.accepted`, `request.collected`, dispute events) could only dead-end on the detail 404. Chosen (options Q&A) over a past-party read-access record in order-service and over web-only 404 softening — see [Dropped-courier prune](#dropped-courier-prune-d33) | Notif F3.1 UX; issue #266 |

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
| **Notification REST API** (Spring Boot) | Lets the Web App list the caller's notifications (paged, newest first, optional unread filter), read the unread count, and mark rows read/unread (F3.1, F3.2) — JWT-authenticated, every route scoped to the token's user (D24–D28, issue #66). |
| **STOMP push gateway** (Spring Boot) | WebSocket endpoint with Spring's STOMP simple broker; pushes each stored notification to the affected users' `/user/...` destinations within the 5-second budget (F1.2; Order NFR1.1–1.2). |
| **Retention purge scheduler** (Spring Boot) | Scheduled job deleting notifications older than the configured window (F3.4), and `processed_events` dedupe rows past the same window (issue #137). |
| **Notification DB** (PostgreSQL) | Owned exclusively by this service: notification rows with read/unread state plus processed event IDs — one schema so dedupe and notification insert commit atomically. |

## Event contracts (D17/D18)

The Order and Credit Services publish **explicit, business-specific
typed events** — one flat record per fact, no envelope wrapper. The contracts
are code in `foc-contracts/`: the `DomainEvent` interface, the
`RequestEvent`/`CreditEvent` domain markers, the eleven records, the `EventTypeRegistry`, and one
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

The cataloged request events (Order F0.2 state transitions plus the
courier-arrival update, F4.1.1–F4.1.2): `request.created`,
`request.accepted`, `request.collected`, `request.completed`,
`request.cancelled`, `request.dropped`, `request.expired`,
`request.courier-arrived`, and the Credit saga's `request.submitted`
and `request.rejected`
([`credit-service.md`](credit-service.md) D7–D8). `request.dropped` is
the courier withdrawing after an accept (Order F5.2): the request
returns to the open feed and the reward stays held, so it is a
distinct type — never `request.cancelled`, which would release the
reward — with `parties` = the requester only and a non-null
`courierId` (the courier who dropped). All request events
carry `requestId`, `requesterId`, `pickupLocation`, `dropoffLocation`,
and `note` (nullable: a request may have none); the post-acceptance events add `courierId` (nullable on
`request.cancelled` — a pre-acceptance cancel has no courier),
`request.submitted` adds `reward` and `request.rejected` adds
`reason`. The N3 dispute events (issue #234) add, on
`request.disputed`, `category` and a nullable `disputeNote`, and on
`request.dispute-resolved`, `courierAmount`, `requesterAmount` and a
nullable `resolutionNote`; both carry `courierId` and `parties` =
requester and courier. Display names are deliberately **not** event fields — and not
stored payload fields either (D32, superseding D30): payloads keep the
party IDs as-is and the web resolves `courierId` to a username at read
time — see [Display names](#display-names-d32). The credit domain's `credit.reserved` and
`credit.reservation-rejected` carry `requestId`, `requesterId` and
`amount` (plus `reason` and a nullable `reasonCode` on the rejection) and go to the separate
`credit-events` exchange, which this service does not bind. Adding
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

## Display names (D32)

Stored payloads and push frames carry party IDs only — this service
makes **no outward calls** (F1.1 holds with no exceptions; D32
superseded D30's ingest-time lookup and D31's internal token, and the
ingest transaction is plain `@Transactional` again: the
lookup-before-transaction ordering was the only reason it had been
programmatic). The web resolves names where they are displayed:

- **Lookup:** the SPA calls `GET /api/user/users/{id}` through the
  gateway with the caller's own JWT (the route was already
  `authenticated()` — no new credential, no service-to-service trust).
  One request per unique `courierId` on screen; in-flight dedupe
  collapses concurrent rows naming the same courier.
- **Cache:** SPA-side, per id — 10 min positive / 30 s negative TTL
  (the retired ingest adapter's numbers, for the same reasons: bound
  request volume, don't mute recovery). Fan-out is bounded by unique
  couriers per page (10-row pages behind "Load more"); a batch
  endpoint (`GET /users/batch?ids=…`) is noted as future work if
  volume ever warrants it.
- **Degradation:** any failure — unknown id, 404 (deleted account),
  network error — renders the template's actor-less copy, the same
  strings that show while a lookup is still in flight. A row is never
  blocked on a name.
- **Renames:** copy always shows the *current* username — D30's
  ingest-time snapshot (stale after a rename) was a motivating defect.
  Pre-D32 rows still carry stored `requesterName`/`courierName` keys;
  the web ignores them entirely (no migration — dead keys age out with
  the 30-day retention purge).

## Dropped-courier prune (D33)

A drop returns the request to the open feed and removes the courier as
a party, so `RequestsQueryService.detail()` answers their old
notifications' clicks with 404 and `/requests/mine?role=courier` no
longer lists the request — no page can render it for them (issue
#266). Rather than restore read access or soften the dead end, the
ingest deletes the rows there would be nothing behind:

- **Mechanism:** on `request.dropped`, one bulk `DELETE` scoped to
  `recipient_id` = the event's `courierId` and a `LIKE` match on the
  payload's `"requestId":"<id>"` (both quotes in the pattern, wildcards
  escaped — request 12 never matches request 123). Textual on purpose:
  the request reference lives inside the JSON payload column, and JPQL
  `LIKE` runs on H2 and Postgres alike where `jsonb` would not.
- **Transactionality:** same transaction as the event-ID dedupe insert,
  and ordered **before** it — the bulk query's `clearAutomatically`
  discards the dedupe row's still-unflushed insert if it runs after
  (assigned PK, so the insert queues until flush, unlike the
  notifications' immediate IDENTITY inserts). A redelivery therefore
  replays both or neither; a duplicate prunes nothing.
- **Scope:** only the dropping courier's rows for that request. The
  requester's history is untouched and they still receive the
  `request.dropped` row; a courier who re-accepts gains fresh rows.
- **Accepted limitations:** a courier with the Center open keeps the
  deleted rows in client state until the next fetch (no STOMP deletion
  frame) — at worst one 404 click; and their Request History stays
  empty for that request, which is this decision's intended meaning,
  not a gap.

## REST API contract (D24–D28)

All routes sit behind the gateway prefix (`/api/notification/…`,
stripped in transit) and require `Authorization: Bearer <JWT>` — the
same HS256 platform token the STOMP CONNECT carries, verified by the
service itself (the gateway has already checked signature, expiry and
the denylist; the service does not re-check the denylist, per G4/D28).
The caller is always the token's `sub`: no route takes a user ID, so
there is no path to another user's rows.

| Route | What it does |
| --- | --- |
| `GET /notifications/me?page=0&size=20[&unread=true]` | One page of the caller's notifications, `created_at` newest-first with ID tiebreak (D25). `page`/`size` are hand-validated (D27): `page ≥ 0`, `1 ≤ size ≤ 100`, out of range → 400. `unread` is deliberately lenient (D27): Spring's standard boolean conversion, so `1`/`yes`/`on` and `0`/`no`/`off` also bind. Returns the platform page envelope `{content, page, size, totalElements, totalPages}`; each element is the same `NotificationDto` the STOMP push sends — `{id, eventType, payload (parsed object), occurredAt, read, createdAt}` — so the frontend has one type for both. |
| `GET /notifications/me/unread-count` | `{"count": n}` — what the bell badge shows; the SPA re-syncs it on load, on reconnect and after writes (the list's `totalElements` can't serve this). |
| `PATCH /notifications/me/{id}` body `{"read": true\|false}` | Sets one row's flag, both directions; 204 on success. A missing `read` → 400; a present `read` is otherwise deliberately as lenient as Jackson's default scalar coercion (e.g. `"true"`/`"false"` strings and `0`/`1` numbers also bind — D24). 404 when the caller has no such row — someone else's and nonexistent are deliberately the same answer (D26). Repeats are the same 204. |
| `POST /notifications/me/read-all` | Marks every unread row of the caller read in one statement; 204, also when nothing was unread. |

Errors are RFC 9457 problem+json throughout: 401 (`WWW-Authenticate:
Bearer`) is written by the security chain before any controller runs,
400/404 carry the contract's `detail` strings above. A page past the
last one is an empty page, not an error. A mark racing the retention
purge (D9) can 404 on a row the client just displayed — correct, the
row is gone.

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
| Notif F3.1 — view recent notifications in-app | REST list over the stored rows: `GET /notifications/me`, paged newest-first (D24/D25/D27) |
| Notif F3.2 — mark read/unread | `PATCH /notifications/me/{id}` flips the row's flag both ways; `POST /read-all` in bulk (D24/D26) |
| Notif F3.4 — retention window | purge scheduler, `NOTIF_RETENTION_DAYS` (default 30); same run sweeps `processed_events` (issue #137) |
| Notif NFR1.1 — at-least-once delivery | durable queue + manual ack after transaction commit |
| Notif NFR1.2 — persist undelivered events across restarts | durable exchanges/queues + persistent messages (D8) |
| Order F0.2 — event on every request state transition | Order Service publishes all six state-transition events to the exchange |
| Order F4.1.1–F4.1.2 — requester updated on collection and courier arrival | `collected` state event plus the `courier-arrived` event, pushed to the requester through the same pipeline |
| Order NFR1.1–1.2 — state changes visible within 5 s | broker push path end-to-end: consume → process → STOMP push, no polling |
| M6 — meaningful async workflow | the entire request-events → broker → notification pipeline |

> ℹ️ F2.4 (stale-event discard) was retired by D19 and no longer
> appears in the backlog trace.

## Configuration notes

- `NOTIF_RETENTION_DAYS` — retention window, default **30** (D9);
  covers both `notifications` and the `processed_events` dedupe table
  (issue #137, no separate knob).
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
  result stay synchronous REST, unless the exchange is a saga step:
  Order → Credit reservation is a choreographed saga in which
  `request.submitted` is answered by `credit.reserved` or
  `credit.reservation-rejected`, and transfer and release follow the
  request events ([`credit-service.md`](credit-service.md) D7–D9).
  Either way the broker carries only "this happened" facts.
- **Exchange topology is settled (D16):** one topic exchange per
  producing domain (`request-events` and `credit-events` today), routing keys = the
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
  Allowed handshake origins come from `NOTIFICATION_WS_ALLOWED_ORIGINS`.
  The browser connects through the API gateway at
  `/api/notification/ws` ([`gateway.md`](gateway.md), issue #151): the
  gateway proxies the upgrade and SockJS streaming and the `Origin`
  header passes through unchanged.
- **Authentication:** the handshake/upgrade is unauthenticated; the
  STOMP `CONNECT` frame carries `Authorization: Bearer <JWT>` (HS256,
  shared `JWT_SECRET`, verified with jjwt). A channel interceptor
  validates it and sets the session principal to the token's `sub` —
  the platform user ID, the same ID in event `parties[]` and
  notification `recipientId`, which is what `/user/...` routing matches
  on. The interceptor also rejects **revoked** tokens via the D23
  denylist lookups — the token's own key and the account's password-
  reset cutoff. Invalid, revoked or missing tokens reject the CONNECT
  with a STOMP ERROR frame and the session closes. STOMP 1.2's
  `STOMP` frame is an alias for `CONNECT` and passes the same checks
  (PR #185 review — it used to slip past them).
- **Frame policing** (PR #185 review): clients never SEND on this
  channel, and the interceptor enforces it — without an application
  destination prefix, a SEND to `/user/{id}/queue/notifications` would
  have been resolved and delivered to that user, a spoofed badge bump.
  Every SEND is refused, and SUBSCRIBE is allowed only to
  `/user/queue/notifications` (a direct subscription to another
  session's broker queue is refused the same way). A refused frame
  gets an ERROR frame and the session closes.
- **Destination:** clients subscribe to `/user/queue/notifications`;
  the push listener sends each stored notification there after the
  processor's transaction commits (`AFTER_COMMIT`) — well inside the
  5 s budget. Push is best-effort: an offline user misses the frame and
  catches up via the REST list (F3.1).
- **Heartbeats:** 10 s / 10 s both directions.
- **Accepted limit — revocation is checked at CONNECT only** (PR #185
  review): the denylist lookups run once, when the session opens. An
  already-open session keeps receiving pushes after that token is
  logged out elsewhere, after a password reset, and past the token's
  own `exp`, until the socket closes. The SPA closes its own socket on
  logout, so the exposure is a second session holding the same
  token — bounded by that session's lifetime. Re-checking on a timer
  or disconnecting on a revocation event is not implemented.
- **Client policy** (implemented under issue #67): a single STOMP
  client for the whole SPA, owned by a `NotificationProvider` layout
  route in the shared app shell. Native WebSocket only — no
  `sockjs-client` dependency; the client connects straight to the
  endpoint's raw-WebSocket transport (`/ws/websocket`) — auto-reconnect
  with exponential backoff 1 s doubling to a 30 s cap, resubscribe on
  reconnect (subscription made in `onConnect`). UI is badge-only until
  the Notification Center (issue #66).
- **ERROR-frame policy** (PR #185 re-review; chosen by Leong Wei Zhi
  from four options via neutral options Q&A): the server sends an ERROR
  frame for any frame-handling failure, and with this client's traffic
  (CONNECT plus one fixed subscribe) it is in practice a rejected
  CONNECT token — invalid, revoked, or expired. The client cannot tell
  those apart from the frame, so it **retries three times, 30 s apart**
  (the reconnect cap, not its 1 s floor — a dead token must not be
  re-sent every second), then stops for good. Each run is bounded
  because the SPA has no token refresh: a session that outlives the 1 h
  token TTL can never reconnect, and retrying forever would hammer the
  server with credentials that cannot work. Reaching CONNECTED resets
  the budget, so a later expiry gets its own three tries.
  A give-up is **reported, not silent**: the provider marks the session
  stopped and the bell greys its badge and says "updates paused, sign
  in again" in its accessible name — the count stays visible (those
  notifications did arrive) but stops being presented as current.
  Nothing is re-fetched by this policy; the REST API's
  `/notifications/me/unread-count` (issue #66) is what the SPA seeds and
  re-syncs the badge from once the Notification Center's web half lands
  (until then the count is session-local). An unset
  `VITE_NOTIFICATION_SERVICE_URL` reports the same paused state, since
  no socket is ever opened. Recovery is a re-login, which changes the
  token and re-runs the provider's effect. Transport drops with no
  ERROR frame are untouched and still auto-reconnect with the policy
  above.

> ✍️ Historical note: the earlier backlog's committed requester–courier
> chat implied a second real-time channel. The latest backlog moved
> chat to nice-to-have N6; if it is ever built, choosing its transport
> (own socket vs riding this service's channel) is a team decision to
> make then.

## Open items (team decisions still pending)

- **Scale-out** (team decision 2026-09-15): the current design is
  single-instance and uses Redis only for the D23 denylist read — not
  for connection state. If the service is later
  scaled to multiple instances (nice-to-have N5.4), the team will then
  choose between Redis pub/sub and a RabbitMQ STOMP broker relay for
  cross-instance WebSocket fan-out.
