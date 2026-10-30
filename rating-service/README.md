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
-->

# rating-service

The FoC rating service (nice-to-have N1, issue #44): ratings for
completed orders. It ingests `request.completed` into per-request
eligibility rows (#251), takes each party's one immutable rating of the
other (#252), and publishes `rating.submitted` to the `rating-events`
exchange through a transactional outbox so the ratee can be notified
(#255). Built on JWT security, a service-owned PostgreSQL (`rating-db`,
schema owned by Flyway), the gateway route `/api/rating/**`, and
compose wiring (#250). The remaining features land with the issues
under #44 (#253–#258).

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
Flyway disabled (the migrations are PostgreSQL-flavored).

## Endpoints

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/actuator/health` | none — open through the gateway too (G5: anonymous requests pass through; a token is only verified if present) | compose healthcheck / probes |
| POST | `/ratings` | bearer | `{requestId, stars (1–5), text? (≤1000 chars)}`; the caller rates the other party of a completed request, once. 201 with the stored rating; problem+json 400 invalid stars/text, 403 not a party, 404 unknown request, 409 already rated |
| GET | `/ratings/mine?requestId=` | bearer | the caller's own rating of that request: 200 `{"rating": {...}}`, or `{"rating": null}` until they rate (same 400/403/404 refusals as POST) |

Everything else is answered 401 by the service itself without a valid
bearer token (the gateway does not require one, per G5) and 404 with
one.
