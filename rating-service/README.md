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
-->

# rating-service

The FoC rating service (nice-to-have N1, issue #44): ratings for
completed orders. **Walking skeleton only for now** — JWT security, a
service-owned PostgreSQL (`rating-db`, schema owned by Flyway), the
gateway route `/api/rating/**`, and compose wiring. The rating features
land with the issues under #44 (#251–#258).

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

Everything else is answered 401 by the service itself without a valid
bearer token (the gateway does not require one, per G5) and 404 with
one — no rating routes exist yet.
