<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
  Scope: service README written while scaffolding the Spring Boot
  skeleton (issue #250, the N1 walking skeleton under #44).
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
curl http://localhost:8090/api/rating/actuator/health   # 401 without a token
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
| GET | `/actuator/health` | none in-service (the gateway still requires a token on `/api/rating/**`) | compose healthcheck / probes |

Everything else is 401 without a valid bearer token and 404 with one —
no rating routes exist yet.
