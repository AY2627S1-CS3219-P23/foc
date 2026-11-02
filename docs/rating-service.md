<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-11-02, issue #258.
  Scope: transcribed the team-decided rating-service design into this
  document and the companion diagram. Every design decision (D1-D17)
  was made by Leong Wei Zhi — on the N1 issues (#44, #249-#255), in
  the PR review rounds cited per row, or via neutral options Q&As
  recorded in ai/usage-log.md — and was already implemented and merged
  before this document existed. The tool collected the decisions from
  the merged code's disclosure headers, the issues and the PR reviews,
  derived the traceability mapping, and formatted the result; it made
  no design decisions.
  Author review: Leong Wei Zhi, pending pull request review.
-->

# FoC Rating Service — Architecture Design

**Scope:** component-level design of `rating-service/`, the
nice-to-have N1 rating system (issue #44): requesters and couriers
rating each other after a completed request (N1.1, N1.2), public
review history (N1.3), and reporting irresponsible behaviour (N1.4).
Unlike the committed services, every requirement here is a
nice-to-have; the service still follows the platform's committed-scope
conventions (database-per-service, gateway routing, typed broker
events, problem+json errors). \
Companion diagram: [`rating-service.mmd`](rating-service.mmd)
(GitHub renders it in the file view). \
High-level system architecture: [`architecture.md`](architecture.md) —
N1 is drawn in the runtime diagram since this service merged.

## Requirements this service owns

| ID | Requirement | Priority | Sprint |
| --- | --- | --- | --- |
| N1 | Rating system (issue #44) | Medium | Week 10 |
| N1.1 | Allow requesters and couriers to review and rate each other after the transaction is completed | Medium | Week 10 |
| N1.2 | Allow parties to give the other party a star review and a short write-up based on factors such as timeliness and friendliness | Medium | Week 10 |
| N1.3 | Allow users to check review history of other users | Medium | Week 10 |
| N1.4 | Allow users to report parties who engage in irresponsible behaviour using a report button/form | Medium | Week 10 |

Issue-author decisions scoping N1 (2026-10-25, recorded on #44): N1 is
its own microservice; N1.4 reports target a **user**, not a review
(review moderation is out of scope); the ratee gets an in-app
notification; stars (1–5) required, write-up optional; ratings are
immutable, at most one per direction per completed request.

## Requirements from other services this service serves

| ID | Requirement | Owning service |
| --- | --- | --- |
| User F8.1 | Public profiles — the profile's star row and Recent Reviews feed from this service's summary and history APIs (web issue #257) | User Service |
| Notif F1.1–F1.3 | The ratee's in-app notification rides the existing event pipeline: this service publishes `rating.submitted`, the Notification Service consumes it (issue #255) | Notification Service |

## Design decisions (made by the team)

All decisions were made by Leong Wei Zhi: the N1 scope decisions on
2026-10-25 (issue #44), D1 and D16 with the issue breakdown and the
#250 scaffold (2026-10-25 – 2026-10-28), D2–D15 on the feature issues
#249 and #251–#254 (2026-10-26 – 2026-10-30, several via neutral
options Q&As recorded in `ai/usage-log.md`, others in the cited PR
review rounds), and D17 on 2026-10-30 (comment on issue #258). They
count as the team's finalized design for this service.

| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Service boundary | **A separate `rating-service` with its own PostgreSQL (`rating-db`)** — not tables inside order-service (the D3-era sketch): ratings are a distinct domain with its own lifecycle, and the database-per-service rule holds. Routed at `/api/rating/**` through the gateway with no published host port, as [`gateway.md`](gateway.md) G3 anticipated for new services (issue #250) | N1; M7 |
| D2 | Rating eligibility | **A projection, not a call**: the service consumes `request.completed` from the `request-events` exchange into its own `rateable_request` rows (request id, requester id, courier id, completed at) and answers eligibility from them — no synchronous call to order-service, mirroring how [`notification-service.md`](notification-service.md) events carry all needed data. A just-completed request can 404 here until the event is consumed; the web retries (issue #251) | N1.1; M6 |
| D3 | Ingest failure handling | **The platform's queue trio** (work/retry/DLQ, notification-service D6/D7/D13/D20): `rating-service.request-events` bound on the exact `request.completed` key — not `request.#`; no other request event reaches the queue — with retry attempts counted in the client-stamped header (RabbitMQ 4 resets `x-death` on client republish). TTL/attempts env-tunable (issue #251) | N1.1 reliability |
| D4 | Ingest idempotency | **`processed_events` inbox in the same transaction as the business write**, ack after commit. `ProcessedEvent` implements `Persistable` with an `isNew` flag flipped by lifecycle callbacks, so `save()` issues a `persist` (INSERT — trips the PK under a concurrent duplicate) rather than a `merge` (whose lookup would silently adopt the other transaction's row). A constant `isNew()=true` was tried and reverted: it breaks `SimpleJpaRepository.delete`, which skips entities it believes new (issue #251, PR #271 review) | N1.1 correctness |
| D5 | Replayed completions | **A repeat `request.completed` under a fresh event id upserts the eligibility row** (last write wins) rather than dead-lettering; an upsert that changes the parties is logged at WARN, since submitted ratings read that row. `completed_at` carries the event's `occurredAt` — the contract has no separate completion timestamp (PR #271 review) | N1.1 |
| D6 | Code reuse stance | **Copy, not share**: the problem+json advice (user-service), `PageResponse` and the transactional outbox classes (order-service), the queue trio and `RetryingListener` (credit-service), and the inbox/converter/topology-initializer (notification-service) are per-service copies. Services share nothing but `foc-contracts` (notification-service D15) | independent evolution |
| D7 | Publishing `rating.submitted` | **Transactional outbox**: the rating row and its outbox row commit in one transaction (`enqueue` is `MANDATORY` — a call outside a transaction fails instead of committing an event with no state change behind it); a fixed-delay publisher drains it, at-least-once, consumers dedupe on `eventId`. The topology declares the **exchange only** — consumers declare their own queues and bindings (notification-service D13), so an event published before any consumer exists is simply unroutable and retried (issues #249/#252) | Notif F1.4; M6 |
| D8 | Event payload | **`rating.submitted` carries ids and stars only** — `ratingId`, `requestId`, `raterId`, `rateeId`, `stars`; `parties` = **the ratee alone** (the rater knows what they did — #44) and the write-up is deliberately absent: payloads keep party ids, the web hydrates names at read time (notification-service D32), and a consumer that wants the text reads it from this service. `stars` is `Integer`, not `int`, so a missing value fails `@NotNull` instead of reading 0 (PR #163 convention) (issue #249) | Notif F1.1; N1 notification |
| D9 | Immutability | **Ratings and reports cannot change**: entities have no setters and every column is `updatable = false`; no PUT/PATCH/DELETE route exists; one rating per direction per request is the DB's named unique constraint `rating_once_per_rater`. The 409 is **narrowed to that constraint name** — any other `DataIntegrityViolationException` stays a 500 and is logged at ERROR, because a handled exception never reaches Spring's default error logging (issue #252 + PR #272 review). The web encodes it too: a 409 locks the modal into close-only | N1 scope decision (#44) |
| D10 | "Have I rated this?" | **`GET /ratings/mine?requestId=` answers 200 with `{"rating": null}`** until the caller rates — not-yet-rated is the normal pre-rating state the UI renders, not an error; 404/403 are reserved for the same ineligibility gate as POST (unknown request / not a party), which the lookup shares so the two routes can never disagree (issue #252) | N1.1 UX |
| D11 | Aggregate semantics | **`average` is `null`, never 0, when `count` is 0** — 0 reads as a terminal score — and the mean is **raw (unrounded)**; the UI rounds for display. Review pages sort `createdAt desc, id desc` — the id breaks ties so pages never overlap (order-service precedent) (issue #253) | N1.3 |
| D12 | Review projection | **`ReviewResponse` is deliberately narrower than the full rating**: `raterId`, `stars`, `text`, `createdAt` only — no rating id, request id or ratee id leaves this service's door; the web hydrates rater usernames at read time (notification-service D32) (issue #253) | N1.3; least exposure |
| D13 | Paging | **Hand-validated `page`/`size` ints, not Spring `Pageable`** (credit-service stance): `Pageable` turns a negative page into 0, caps an oversized size silently and admits a sort parameter. Bounds: page ≥ 0, 1 ≤ size ≤ 100, defaults 0/20; a page past `Integer.MAX_VALUE / size` is a 400, because past that spring-data refuses the query deep inside as a 500, not the contract's 400 (issue #253 + PR #273 review) | N1.3 contract |
| D14 | Reports | **A report is a moderation flag, not an N3 dispute**: stored and listed for admins (GET and HEAD both `ADMIN`/`OWNER`-gated — `@GetMapping` serves HEAD, which would otherwise fall through to `authenticated()`), with **no event published, no credit consequence, and no existence check** on `reportedUserId`/`requestId` (this service has no user table; ids are opaque). `user_report.request_id` has **no FK** to `rateable_request` — that table holds completed requests only, and reports about cancelled/dropped requests are the most report-worthy; `rating.request_id` keeps its FK as defense-in-depth (nothing deletes eligibility rows). Known gap, accepted for N1's store-and-list scope: no duplicate/rate limit on POST (issue #254 + PR #274 reviews) | N1.4 |
| D15 | Input hardening | **Reject at the contract edge what the DB would 500 on**: NUL bytes in any string field are a 400 (PostgreSQL rejects zero bytes in text parameters), id fields are capped at the columns' 255 chars, and the write-up/details cap of **1000 chars lives in the DTO over an unbounded `text` column** — the wireframe gives no number, so the cap can change without a migration. Blank optional text normalizes to `null`. A padded `reportedUserId` is trimmed before the self-report check (`@NotBlank` admits padding) (issues #252/#254 + PR #274 review) | robustness |
| D16 | Schema ownership | **Flyway migrations, `ddl-auto: none`** (`V1__baseline` … `V5__user_report`) — the first service to adopt it; tests run H2 from the entities with Flyway off (the migrations are PostgreSQL-flavored) plus a Testcontainers `SchemaValidationTest` proving entities match the real migrated schema (issue #250) | schema history |
| D17 | Account purge (`user.purged`) | **No `user-events` listener — purged users' ratings and reports stay** (decided 2026-10-30, recorded on issue #258). The service stores opaque user ids only — no names, no FKs to users; the web already degrades to actor-less copy when a purged rater 404s (notification-service D32). The day-31 purge frees the email/username (User F3.1.3) and is not an erasure requirement — none exists — and user ids are never reused. Deleting a purged rater's rows would retroactively change active users' averages (D9: ratings are immutable). Precedent: notification- and order-service keep purged users' rows; credit-service listens only because redistribution had a real requirement. **Revisit trigger:** an erasure/privacy requirement would add a `user-events` listener plus a one-time backfill — the contract and a consumer precedent are on `main`, so nothing is lost by waiting | User F3.1.3 boundary |

## Components

| Component | Responsibility |
| --- | --- |
| **Rating REST API** (Spring Boot) | `POST /ratings` — one immutable rating of the other party of a completed request (N1.1/N1.2, D9); `GET /ratings/mine?requestId=` — the caller's own rating or null (D10). Caller identity from the verified JWT |
| **Profile read API** (Spring Boot) | `GET /users/{id}/ratings/summary` (null-average aggregate, D11) and `GET /users/{id}/ratings` (paged review history, D12/D13) for the public profile (N1.3, web issue #257) |
| **Report API** (Spring Boot) | `POST /reports` (N1.4, any signed-in user, self-report refused) and the admin-gated `GET /reports` list (D14) |
| **AMQP listener + eligibility processor** (Spring Boot) | Consumes `request.completed` from the service's queue trio with manual ack after commit; idempotent via the `processed_events` inbox (D2–D5). The broker adapter is confined to `messaging/rabbitmq/` (notification-service D11's ports-and-adapters) |
| **Outbox publisher** (Spring Boot) | Fixed-delay poller draining `outbox_event` to the `rating-events` topic exchange, at-least-once (D7/D8) |
| **rating-events exchange** (RabbitMQ) | Durable topic exchange, this service's publishing surface; consumers bring their own queue trios (the Notification Service's second trio, issue #255) |
| **Rating DB** (PostgreSQL, `rating-db`) | Owned exclusively by this service; schema owned by Flyway (D16) |

## Entities

| Entity | Key fields | Grounded in |
| --- | --- | --- |
| `RateableRequest` | Table `rateable_request`: `request_id` (PK, opaque), `requester_id`, `courier_id`, `completed_at` — the D2 projection of `request.completed` | N1.1, D2/D5 |
| `Rating` | Table `rating`: `id`, `request_id` (FK to `rateable_request`), `rater_id`, `ratee_id`, `stars` (1–5, DB CHECK), `text` (nullable, unbounded; 1000-char cap in the DTO, D15), `created_at`; unique `rating_once_per_rater (request_id, rater_id)`; all columns `updatable = false` (D9) | N1.1, N1.2 |
| `UserReport` | Table `user_report`: `id`, `reporter_id`, `reported_user_id`, `reason` (enum: `NO_SHOW`, `ABUSIVE_BEHAVIOUR`, `UNSAFE_HANDLING`, `OTHER`), `details` (nullable), `request_id` (nullable, **no FK**, D14), `created_at` | N1.4 |
| `ProcessedEvent` | Table `processed_events`: consumed event ids + `processed_at` (indexed for a future retention purge) — the D4 inbox | N1.1 correctness |
| `OutboxEvent` | Table `outbox_event`: PK = the event's own `eventId`, payload, `created_at`, `next_attempt_at`, `sent_at`; partial index on unsent rows so it stays small however many sent rows accumulate (D7) | Notif F1.4 |

## The ingest → rate → notify pipeline (D2–D5, D7/D8)

One request's happy path crosses the broker twice:

1. order-service publishes `request.completed` (its normal F0.2 event
   stream — no producer change was needed for N1).
2. This service's listener consumes it from its own trio, records the
   event id and upserts the `rateable_request` projection in one
   transaction, and acks after commit (D2–D5). From that moment both
   parties are eligible to rate, and the web's Request History offers
   the **Rate** action (issue #256).
3. `POST /ratings` resolves the ratee from the projection — 404 for an
   unknown request, 403 for a non-party — and inserts the rating and
   its `rating.submitted` outbox row in one transaction (D7). The
   eligibility gate lives in one private method shared with
   `GET /ratings/mine`, so submit and lookup can never disagree (D10).
4. The outbox publisher drains the row to the `rating-events` exchange;
   the Notification Service's second queue trio (bound on the exact
   `rating.submitted` key) stores and pushes the ratee's notification
   ([`notification-service.md`](notification-service.md), issue #255).

Failure legs: a failed ingest parks in the retry queue
(`RATING_RETRY_TTL_MS`, default 10 s) for up to
`RATING_RETRY_MAX_ATTEMPTS` deliveries (default 3), then dead-letters
intact (D3). An unroutable or failed publish stays in the outbox and is
retried 30 s later; broker-unavailable stops the poll until the next
tick. Both sides are at-least-once by construction — the inbox (D4) and
the consumers' own dedupe absorb the duplicates.

## REST contract summary

The service README's [Endpoints table](../rating-service/README.md#endpoints)
is the authoritative route list (methods, auth, bodies, refusals).
Contract decisions worth naming here: every error is problem+json
(user-service's #138 shape, copied per D6); `POST /ratings` answers 201
with the stored rating and **no `Location` header** — no per-rating GET
exists to point at; 409 means "already rated" and nothing else (D9);
the profile reads are open to any signed-in user because profiles are
public (N1.3), while `/reports` reads are role-gated in the security
chain itself, ahead of parameter parsing (D14).

## Diagram legend

Same notation as [`architecture.md`](architecture.md#legend-notation):
rectangles are components, cylinders service-owned databases, `-->`
synchronous REST, `==>` asynchronous events, `---` JPA access.
Requirement/decision IDs are not drawn on the diagram — the
traceability table below maps them.

## Requirement traceability

| Requirement | Satisfied by |
| --- | --- |
| N1.1 — rate each other after completion | eligibility projection from `request.completed` (D2); `POST /ratings` gated to the request's two parties, one rating per direction (D9), independent directions |
| N1.2 — star review + short write-up | `stars` 1–5 required (`@NotNull` + DB CHECK), `text` optional ≤ 1000 chars (D15); factors like timeliness/friendliness are the write-up's free text |
| N1.3 — check other users' review history | `GET /users/{id}/ratings/summary` (null-average, raw mean — D11) + paged `GET /users/{id}/ratings` (D12/D13); rendered on the public profile (web issue #257) |
| N1.4 — report irresponsible behaviour | `POST /reports` with the fixed reason list; admin-only `GET /reports`, newest first (D14); the web's Report User dialog (issue #257) |
| Ratee notified (#44 decision) | `rating.submitted` through the outbox (D7/D8) → Notification Service (issue #255) |
| User F8.1 — public profile | the profile's star row, tiles and Recent Reviews consume N1.3's APIs |
| M6-style async workflow | both broker hops: `request-events` in (D2/D3), `rating-events` out (D7) |

## Configuration notes

- `RATING_DB_HOST` / `RATING_DB_PORT` / `RATING_DB_NAME` /
  `RATING_DB_USER` / `RATING_DB_PASSWORD` — the service-owned
  PostgreSQL; compose points them at `rating-db`
  (host port `RATING_DB_HOST_PORT`, loopback-only, default **5438**).
- `RATING_RETRY_TTL_MS` / `RATING_RETRY_MAX_ATTEMPTS` /
  `RATING_RETRY_CONFIRM_TIMEOUT_MS` — ingest retry backoff, attempt
  cap and republish-confirm wait (D3), defaults **10 s / 3 / 5 s**.
- `JWT_SECRET` (shared platform secret, 32+ bytes — startup fails
  without it) and `WEB_ALLOWED_ORIGIN` (per-service CORS, `GET`/`POST`/
  `OPTIONS` only — [`gateway.md`](gateway.md): CORS stays per-service).
- The outbox poll (1 s fixed delay, batch 100, 30 s unroutable
  backoff) is tuned by `rating.outbox.publisher.*` application
  properties — deliberately not surfaced as env vars; tests disable
  the poller.

> 🔴 Like the Notification Service's, the retry TTL is baked into the
> retry queue's arguments at declaration: changing it against a broker
> that already holds the queue fails (`PRECONDITION_FAILED`) until the
> queue is deleted or the broker volume reset.

## Open items (team decisions still pending)

- **Report handling workflow** — reports are stored and listed only
  (D14); whatever an admin does about one happens outside the system.
  A status field / resolution flow is unscoped.
- **Report dedupe / rate limiting** — the accepted D14 gap: one user
  can file repeat reports against another. Revisit if report volume
  ever matters.
- **Retention** — `processed_events` and sent outbox rows accumulate;
  `processed_at` is indexed for a future purge job
  (notification-service's issue #137 pattern), none is scheduled yet.
- **Erasure** — D17's revisit trigger: an erasure/privacy requirement
  would add a `user-events` listener and a one-time backfill.
