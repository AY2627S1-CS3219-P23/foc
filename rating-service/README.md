<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
  Scope: service README written while scaffolding the Spring Boot
  skeleton (issue #250, the N1 walking skeleton under #44).
  2026-10-27, Claude Code (Fable 5), PR #261 review (@Sinnez1): the
  health-curl comment and the Endpoints auth wording corrected — the
  gateway passes anonymous requests through (G5), so health is open
  and the 401 on other routes comes from the service itself.
  Reviewed by: [pending]
  2026-10-30, Claude Code (Fable 5), issue #252: the submit-rating
  routes in the Endpoints table, and the walking-skeleton framing
  retired (#251's ingest and #252's API exist now).
  Author review: [pending]
  2026-10-30, Claude Code (Fable 5), issue #253: the two profile read
  routes in the Endpoints table.
  Author review: [pending]
  2026-10-30, Claude Code (Fable 5), issue #254: the report routes in
  the Endpoints table and the intro's feature list.
  Author review: [pending]
  2026-10-30, Claude Code (Fable 5), PR #274 review (claude[bot]): the
  no-duplicate/rate-limit known gap noted on POST /reports.
  Author review: [pending]
  2026-11-02, Claude Code (Fable 5), issue #258: the intro's stale
  remaining-features line replaced now that the N1 issues are merged;
  Configuration, Events and Status sections added; pointer to the new
  design doc (docs/rating-service.md). Transcription only.
  Author review: Leong Wei Zhi, pending pull request review.
-->

# rating-service

The FoC rating service (nice-to-have N1, issue #44): ratings for
completed orders. It ingests `request.completed` into per-request
eligibility rows (#251), takes each party's one immutable rating of the
other (#252), serves any user's rating summary and review history for
the public profile (#253), takes reports of irresponsible behaviour for
admins to review (#254), and publishes `rating.submitted` to the
`rating-events` exchange through a transactional outbox so the ratee
can be notified (#255). Built on JWT security, a service-owned
PostgreSQL (`rating-db`, schema owned by Flyway), the gateway route
`/api/rating/**`, and compose wiring (#250). The web halves are the
Rate Your Experience modal on Request History (#256) and the public
profile with reviews and Report User (#257). The design record is
[`docs/rating-service.md`](../docs/rating-service.md) — every
architectural choice is a numbered decision (D1–D17) there.

## Running

Through the full stack (recommended):

```sh
docker compose up        # from the repo root
```

The service has no published host port; it is reachable only through
the API gateway (docs/gateway.md):

```sh
curl http://localhost:8090/api/rating/actuator/health   # {"status":"UP"} — health is open
```

Directly, against a local PostgreSQL (see `.env.example`, Rating
Service section, for the `RATING_DB_*` variables):

```sh
./mvnw spring-boot:run
```

`JWT_SECRET` (the shared platform secret, 32+ bytes) must be set either
way; the service fails at startup without it.

## Tests

```sh
./mvnw test
```

No external infrastructure needed: tests run against in-memory H2 with
Flyway disabled (the migrations are PostgreSQL-flavored). The
Testcontainers suites (Flyway migrations, schema validation, the real
RabbitMQ retry/DLQ path) auto-skip without Docker.

## Configuration

Set via `.env` (see `.env.example`, Rating Service section) or the
environment; compose overrides the DB host/port to the `rating-db`
container.

| Env var | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | — (required) | shared platform secret (32+ bytes); startup fails without it |
| `RATING_DB_HOST` / `RATING_DB_PORT` | `localhost` / `5432` | PostgreSQL as seen by the service |
| `RATING_DB_NAME` / `RATING_DB_USER` / `RATING_DB_PASSWORD` | `rating` / `rating` / — | credentials for the service-owned DB |
| `RATING_RETRY_TTL_MS` | `10000` | how long a failed `request.completed` parks in the retry queue (baked into the queue's arguments — changing it against an existing broker volume needs a queue delete/reset) |
| `RATING_RETRY_MAX_ATTEMPTS` | `3` | deliveries per event before it dead-letters |
| `RATING_RETRY_CONFIRM_TIMEOUT_MS` | `5000` | how long the retry republish waits for the broker's confirm |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | compose's broker | the shared RabbitMQ |
| `WEB_ALLOWED_ORIGIN` | `http://localhost:5173` | per-service CORS for the SPA (`GET`/`POST`/`OPTIONS` only) |

## Events

Consumes **`request.completed`** from the `request-events` exchange on
its own work/retry/dead-letter queue trio (bound on the exact key — no
other request event reaches the queue), idempotently via a
`processed_events` inbox, into the `rateable_request` eligibility
projection — no synchronous call to order-service.

Publishes **`rating.submitted`** (ids + stars, `parties` = the ratee
only) to the `rating-events` topic exchange through a transactional
outbox: the rating row and its event commit together, a fixed-delay
poller delivers at-least-once, and consumers dedupe on `eventId`. The
notification-service consumes it to notify the ratee (#255). Details
and the decision record:
[`docs/rating-service.md`](../docs/rating-service.md) D2–D8.

## Endpoints

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/actuator/health` | none — open through the gateway too (G5: anonymous requests pass through; a token is only verified if present) | compose healthcheck / probes |
| POST | `/ratings` | bearer | `{requestId, stars (1–5), text? (≤1000 chars)}`; the caller rates the other party of a completed request, once. 201 with the stored rating; problem+json 400 invalid stars/text, 403 not a party, 404 unknown request, 409 already rated |
| GET | `/ratings/mine?requestId=` | bearer | the caller's own rating of that request: 200 `{"rating": {...}}`, or `{"rating": null}` until they rate (same 400/403/404 refusals as POST) |
| GET | `/users/{id}/ratings/summary` | bearer | the user's aggregate for the profile's star row: 200 `{average, count}`; `average` is the raw mean (the UI rounds) and null — not 0 — while `count` is 0 |
| GET | `/users/{id}/ratings?page=&size=` | bearer | the user's review history, newest first: 200 paged `{content: [{raterId, stars, text, createdAt}], page, size, totalElements, totalPages}` (IDs only; the web hydrates usernames, D32). `page` ≥ 0 (default 0), 1 ≤ `size` ≤ 100 (default 20); problem+json 400 outside those bounds |
| POST | `/reports` | bearer | `{reportedUserId, reason (NO_SHOW \| ABUSIVE_BEHAVIOUR \| UNSAFE_HANDLING \| OTHER), details? (≤1000 chars), requestId?}`; report a user for irresponsible behaviour (N1.4). `requestId` is unverified context. 201 with the stored report; problem+json 400 self-report, unknown reason or invalid fields. Known gap (N1 scope: store and list only): no duplicate or rate limit, so one user can file repeat reports against another |
| GET | `/reports?page=&size=` | bearer, **ADMIN/OWNER** (403 otherwise) | all reports, newest first, paged like the review history (same page/size bounds and 400s) |

Everything else is answered 401 by the service itself without a valid
bearer token (the gateway does not require one, per G5) and 404 with
one.

## Status

**Complete for N1's scope.** All feature issues under #44 are merged:
contracts (#249), scaffold (#250), eligibility ingest (#251), submit
API (#252), profile reads (#253), reports (#254), the ratee
notification (#255) and the web halves (#256, #257). Open items —
report-handling workflow, report dedupe/rate limiting, inbox/outbox
retention — are listed in
[`docs/rating-service.md`](../docs/rating-service.md#open-items-team-decisions-still-pending).
