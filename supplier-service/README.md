<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Sonnet 5), 2026-09-22.
  Scope: service README written while scaffolding the Spring Boot
  skeleton.
  Reviewed by: Ko-Khan (via pull request).
  2026-11-10, Claude Code (Sonnet 5.5), issue #326: rewritten from the
  scaffold-only text to describe the implemented service (endpoints read
  from the controllers and SecurityConfig). Docs only; no decision made
  by the tool. Author review: Alastair Tan Choon Wei to review via pull
  request.
-->

# Supplier Service

Owns vendor/landmark data: admin CRUD, browsing, search and filtering,
and per-user supplier bookmarks (N8). Design record:
[`docs/supplier-service.md`](../docs/supplier-service.md); component
diagram: [`docs/supplier-service.mmd`](../docs/supplier-service.mmd).

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned, schema from
JPA). The database is seeded from the course CSV
(`src/main/resources/csv/supplier-seed-data.csv`) on first startup.

## Endpoints

Reached through the gateway as `/api/supplier/**`. Every route needs a
bearer JWT (HS256, the shared `JWT_SECRET`); errors are problem+json.

| Endpoint | Who | Purpose |
| --- | --- | --- |
| `GET /suppliers` | any signed-in user | List with `search`, `category`, paging and `sort`; optional `lat` + `lng` sort nearest-first; each item carries the caller's `bookmarked` flag |
| `GET /suppliers/categories` | any signed-in user | Every category, for the filter dropdown |
| `POST /suppliers`, `PUT /suppliers/{id}`, `DELETE /suppliers/{id}` | `ADMIN` or `OWNER` | Supplier CRUD (hard delete) |
| `GET /bookmarks` | any signed-in user | The caller's bookmarks, newest first (cap of 5) |
| `PUT /suppliers/{id}/bookmark`, `DELETE /suppliers/{id}/bookmark` | any signed-in user | Add or remove the caller's bookmark (`409` at the cap or on a parallel add) |
| `POST /purge/bookmarks` | `SERVICE` role only | Remove purged accounts' bookmarks; called by user-service, `404` through the gateway |

## Configuration

`SUPPLIER_DB_*` (database), `JWT_SECRET` (shared with the other
services) and `WEB_ALLOWED_ORIGIN` (CORS); see `.env.example`.

## Run

```sh
# Tests (the NFR tests need Docker for Testcontainers and are skipped
# without it)
./mvnw test

# Run locally
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Full stack (from the repo root; copy .env.example to .env first and
# set SUPPLIER_DB_PASSWORD)
docker compose up --build supplier-service
```
