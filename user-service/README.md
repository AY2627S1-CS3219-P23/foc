<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20.
  Scope: service README written while scaffolding for issue #84.
  2026-09-23, Claude Code (Opus 5.5): status and test instructions updated
  for the first-owner bootstrap (issue #97, PR #126 review).
  2026-09-23, Claude Code (Opus 5.5): status updated for the profile
  endpoints (issue #95).
  2026-09-25, Claude Code (Opus 5.5): stale "no DB wiring" sentence
  replaced (PR #131 review).
  2026-09-25, Claude Code (Opus 5.5): setup-owner no longer limited to
  the first owner.
  2026-09-27, Claude Code (Fable 5), issue #93: DELETE /users/me and the
  soft-delete/purge section added.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# User Service

Will own accounts, auth, and profiles (User F1–F8): sign-up/OTP, login,
JWT issuance, account update/delete/recover, roles, admin management.
Backlog: issues #84–#98.

Spring Boot 4 · Java 21 · Maven.

Currently: actuator health endpoint, `POST /auth/setup-owner` (creates an
OWNER for any caller with the setup token, #97), `GET /users/me` /
`GET /users/{id}` (own and public profile, #95), and `DELETE /users/me`
(self-deletion, #93), backed by Postgres via
Spring Data JPA. The root `compose.yaml` runs it with its own `user-db` (host port
`${USER_SERVICE_PORT:-8087}`); `spring-boot:run` needs that database
reachable (`USER_DB_*` env vars). Tests supply their own database.

## Soft delete & day-31 purge (#93)

`DELETE /users/me` (bearer only, no body) stamps `deleted_at` — the row
stays, so the account's unique email/username remain reserved and the
user can recover within 30 days (#94). Repeat deletion, like every other
`/users/me` call on a deleted account, returns 404. A daily scheduler
(`AccountPurgeScheduler`, default 03:00) hard-deletes accounts whose
`deleted_at` is older than the retention window, together with their
`otps`/`account_tokens` rows — that purge is what frees the identifiers.
Configure via `USER_RETENTION_DAYS` (default 30) and `USER_PURGE_CRON`
(Spring 6-field cron).

## Run

```sh
# Tests (need a running Docker daemon: Testcontainers starts Postgres)
./mvnw test

# Run locally
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Container (from the repo root — build context is the repo root)
docker build -f user-service/Dockerfile .
```
