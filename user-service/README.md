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
  2026-09-26, Claude Code (Opus 5.5): status updated for the admin
  endpoints (issue #96).
  2026-09-27, Claude Code (Fable 5), issue #93: DELETE /users/me and the
  soft-delete/purge section added.
  2026-09-29, Claude Code (Opus 5.5), issues #87/#89/#90: sign-up, login,
  JWT issuance and CORS added to the status; new "Sign-up & login"
  section.
  2026-09-29, Claude Code (Opus 5.5), issue #91: "Checking tokens" section
  for the JWT filter chain.
  2026-09-29, Claude Code (Opus 5.5), issue #147: Flyway schema, the
  includeDeleted list filter, the shared 400 format, and the login
  section's lockout (429) and row-locked counters.
  2026-09-30, Claude Code (Opus 5), issue #88 (PR #150), written when
  #147's Flyway switch was merged into that branch: sign-up is the
  insert-after-verify flow (202 + emailed code, POST /auth/signup/verify
  creates the account), with its repeat/resend rules, the V3 migration
  and the purge's pending_signups sweep.
  2026-09-30, Claude Code (Opus 5), PR #150 re-review: the two pending-row
  rules that came out of it (a resend keeps the original expiry; verify
  discards a row whose identifiers were taken) and the 202's
  resendInSeconds, each with the reason it exists.
  2026-09-29, Claude Code (Opus 5.5): "Demo accounts" section for the
  USER_SEED_DEMO seeder
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-09-30, Claude Code (Fable 5), issue #92: the "Account updates"
  section (gate code, PATCH, new-email confirm, password change), the
  problem+json type-URI table, V4/V5 in the Flyway paragraph, and the
  purge's two new sweeps. Contract decisions by Leong Wei Zhi via
  options Q&A.
-->

# User Service

Will own accounts, auth, and profiles (User F1–F8): sign-up/OTP, login,
JWT issuance, account update/delete/recover, roles, admin management.
Backlog: issues #84–#98.

Spring Boot 4 · Java 21 · Maven.

Currently: actuator health endpoint, `POST /auth/signup` /
`POST /auth/signup/verify` and
`POST /auth/login` (#87/#89/#88, JWT issuance #90 — see below),
`POST /auth/setup-owner` (creates an
OWNER for any caller with the setup token, #97), `GET /users/me` /
`GET /users/{id}` (own and public profile, #95), `DELETE /users/me`
(self-deletion, #93), the account-update flows `POST /users/me/otp` /
`PATCH /users/me` / `POST /users/me/email/verify` /
`POST /users/me/email/resend` / `POST /users/me/password` (#92 — see
below), and the admin endpoints
`GET /users` (list, search, role filter, sort, 20/50/100 page sizes,
`includeDeleted=true` to list removed accounts with their `deletedAt`),
`PATCH /users/{id}` (promote/demote) and `DELETE /users/{id}` (soft
delete) for ADMIN/OWNER callers (#96), backed by Postgres via
Spring Data JPA, with the schema managed by Flyway (see below). The root `compose.yaml` runs it with its own `user-db` (host port
`${USER_SERVICE_PORT:-8087}`); `spring-boot:run` needs that database
reachable (`USER_DB_*` env vars) and `JWT_SECRET` set. Tests supply
their own database. CORS allows the web origin in `WEB_ALLOWED_ORIGIN`
(default `http://localhost:5173`).

## Sign-up & login (#87, #88, #89, #90)

These replace PR #139's separate `user-auth` server; auth lives here.

Sign-up is the design doc's insert-after-verify flow (#88): the request
is parked in `pending_signups` with a hashed 6-digit code, and the
`users` row appears only once the code is verified — so an unverified
sign-up never reserves an email or username.

- `POST /auth/signup` `{ email, username, password }` → **202**
  `{ email, expiresInSeconds, resendInSeconds }`; no account yet, a code
  has been emailed. `expiresInSeconds` is the time the code has **left**
  (less than `OTP_TTL` on a resend, see below), and `resendInSeconds` is
  the cooldown the SPA disables its resend button for. The cooldown is in
  the body rather than read from the 429's `Retry-After` because the SPA
  is cross-origin and that header is not CORS-safelisted (it *is* named in
  `exposedHeaders`, so a 429 can still correct the countdown).
  Same rules as owner setup: `eXXXXXXX@u.nus.edu`, username 3–30 of
  `[A-Za-z0-9_]`, password 10–50 with upper, lower and a digit; email and
  username unique (soft-deleted accounts included). Usernames keep the
  case they were typed in but are unique, and match at login, ignoring
  case. Failures are 400 problem+json with the exact reason.
  Repeating the call is the resend — there is no separate route — but
  only for a repeat of the same request: the same username and a password
  matching the one stored. Anything else gets 409 (a live pending sign-up
  is never rewritten; PR #150 review found that overwriting it let anyone
  who knew the address hijack the sign-up). A resend inside
  `OTP_RESEND_COOLDOWN` (default 60s) gets 429 with `Retry-After`; an
  expired pending sign-up is dead, so any sign-up takes its place. A mail
  failure is 502 and leaves nothing behind.
  A resend replaces the code but **not** the expiry, so a pending sign-up
  lives at most `OTP_TTL` from the moment it was created and always
  reaches the expired-and-takeable state. Without that, whoever pended an
  address first could resend once per cooldown for ever and hold it while
  its real owner kept getting the 409 telling them to wait for an expiry
  that never came (PR #150 review). The consequence is deliberate: a
  resend late in the window hands out a short-lived code, and both the
  email and the 202 quote the seconds actually left.
- `POST /auth/signup/verify` `{ email, code }` → **201** with the account
  (no token). A wrong code, or an email with no pending sign-up, is 400
  "Invalid verification code" — deliberately the same answer, at the same
  bcrypt cost; an expired code is 400 "sign up again". Wrong codes are
  counted per pending sign-up, resends included (`OTP_MAX_ATTEMPTS`,
  default 5): the attempt that exhausts them is 429, and the row stays
  behind as **spent** — to a verify it answers like no row at all, and
  only a repeat of its own request (same username, matching password)
  may revive it for a fresh code once the resend cooldown passes,
  keeping the original expiry so its life stays bounded. Anyone else
  keeps getting the 409 until it expires: verify is anonymous, so a
  spent row that were up for grabs would let 5 wrong guesses burn a
  stranger's pending sign-up and swap in the guesser's password.
  Deleting it instead (the pre-review behavior) let 5 wrong guesses buy
  an immediate fresh code, cycling attempt budgets and flooding the
  inbox (PR #157 reviews).
  If the email or username was taken while the code was in flight, verify
  answers 400 with that reason **and discards the pending row** — a
  sign-up that can never complete must not keep holding its email, which
  used to lock whoever lost a username race out of their own address for
  the rest of the TTL (PR #150 review).
  Codes are BCrypt-hashed at rest, never logged, and live for `OTP_TTL`
  (default 10 min). Locally they land in Mailpit
  (<http://localhost:8025>); real delivery goes over Gmail SMTP with the
  `MAIL_*` values in `.env.example`.
- `POST /auth/login` `{ usernameOrEmail, password }` → 200
  `{ accessToken, tokenType: "Bearer", expiresIn }`. A failure names its
  cause (#146): 401 problem+json "No account found for that username or
  email." — also for an account deleted past the window, which is only
  waiting for the purge — or 401 "Incorrect password. N attempts
  remaining before your account is temporarily locked.". Five failures
  in a row lock the account for 15 minutes; from the attempt that trips
  the lock onwards it is 429 problem+json "Your account is locked due to
  too many failed login attempts. Try again in M minutes." (rounded up,
  repeated in `Retry-After` seconds). A success clears the counters, and
  logging in within the 30-day window recovers a soft-deleted account.
  Login therefore tells a caller whether an account exists — the author
  chose that over the non-revealing failures of #89, as sign-up's
  "already taken" 400s reveal the same thing.
  Wrong passwords sent at once each count: the counter is updated with
  the row locked, after the password check (which holds no database
  connection).
- Tokens are HS256 with the shared `JWT_SECRET` (≥ 32 bytes, checked at
  startup): `sub` = user id, `role`, `jti`, `exp` = 1 h
  (`JWT_ACCESS_TOKEN_TTL`, e.g. `1h`; a bare number is seconds). Checking tokens on incoming requests is #91 (below).

## Checking tokens (#91)

- Every route outside `/auth/**` and `/actuator/health` reads
  `Authorization: Bearer <token>`. The token must be HS256 with
  `JWT_SECRET`, unexpired, with a numeric `sub` and a `role` of `USER`,
  `ADMIN` or `OWNER`. `sub` becomes the caller's id and `role` the
  `ROLE_*` authority the `/users` rules check. The role is read from the
  token, not the database.
- No token (or another scheme) on a protected route → 401
  `"Authentication required"`. An expired token → 401
  `"Token has expired"`; any other bad token → 401 `"Invalid token"`,
  on every filtered route. The wrong role → 403 `"You do not have
  permission to access this resource"`. All are problem+json with
  `type`, `title`, `status`, `detail` and `instance`.
- `/auth/**` and `/actuator/health` never read the header, so a stale
  token in the browser can't block login or sign-up. `/auth/logout` is
  the exception: it is filtered, and #90 adds its `authenticated()` rule
  in `SecurityConfig`.
- No sessions: each request stands on its own token. The logout
  denylist (`token_denylist`) isn't checked yet (deferred).

## Account updates (#92)

Editing an account is gated the way the design doc's §5 asks: a code to
the **current** email proves the inbox before anything changes (F2.1.1),
new values are re-validated like sign-up — uniqueness ignoring the
caller's own row, password policy (F2.1.2/F2.1.5) — and a new email only
applies once a second code sent to the **new** address is verified
(F2.1.3), so a typo'd address can never lock the account out. All routes
take the caller from the bearer token; the request DTOs are
**allow-lists** (`PATCH /users/me` binds only `username`, `email`,
`otp`), so a `role`, `id` or `deletedAt` in the body has no field to
land in.

The gate code is **single-use** — the mutating call that matches it
consumes it, so each operation requests its own — and consumption rides
the write transaction: a refusal that rolls the change back (mail 502,
name taken) un-consumes the code, and only wrong guesses and discards
commit. Codes share sign-up's knobs and storage rules: 6 digits,
BCrypt-hashed, never logged, `OTP_TTL` (10 min) / `OTP_MAX_ATTEMPTS`
(5, resends included) / `OTP_RESEND_COOLDOWN` (60s, 429 + `Retry-After`
inside it), resends replace the code but never the expiry. Sign-up's
spent rule is shared too: the guess that exhausts the attempts leaves
the row behind, refusing everything, so the next code still waits out
the cooldown instead of being minted at once (PR #157 review). Every
flow locks the caller's user row first (one lock order: user, then
gate/pending rows), so concurrent operations on one account serialize —
without it, a gate code could be mailed to an address a concurrent
email-change verify had just replaced (PR #157 review).

- `POST /users/me/otp` (no body) → **202**
  `{ expiresInSeconds, resendInSeconds }`; a code went to the account's
  current email. Repeating the call is the resend.
- `PATCH /users/me` `{ username?, email?, otp }` → **200** with the
  account when everything applied (username changes apply at once;
  asking for the email you already have is a no-op), or **202**
  `{ user, email, expiresInSeconds, resendInSeconds }` when an email
  change parked in `pending_email_changes` and a confirmation code went
  to the new address. One pending change per account: repeating the
  PATCH with the same address resends its code, a different address
  replaces the change outright (the bearer token proves the row is the
  caller's own — sign-up's stricter same-request rule guards anonymous
  rows, which these are not), both behind the send cooldown.
- `POST /users/me/email/verify` `{ code }` → **200** with the account,
  new email applied. An address taken while the code was in flight is
  400 "Email is already registered" **and discards the pending change**
  (sign-up's rule, same reason). No pending change / expired code get
  honest, distinct 400s — the caller is signed in, so there is no
  existence to hide and no dummy-hash timing game to play.
- `POST /users/me/email/resend` (no body) → **202**, same shape as the
  PATCH's; it exists because the gate code was consumed when the change
  parked, and re-sending to an address the gated PATCH already chose
  needs no second gate.
- `POST /users/me/password` `{ newPassword, confirmPassword, otp }` →
  **204**. Both entries must match (checked server-side, F2.1.4) and the
  new password passes sign-up's policy (F2.1.5); there is no
  `currentPassword` — the gate code is the authentication.

OTP-flavoured errors carry a stable problem+json `type` URI, so clients
can match failures by machine instead of by `detail` wording (the SPA's
OTP dialog used to string-match sentences; match `type`, the sentences
may change). The sign-up OTP errors carry them too:

| `type` | Meaning (status) |
| --- | --- |
| `urn:foc:user:otp-invalid` | wrong code (400) |
| `urn:foc:user:otp-expired` | code/operation expired; request anew (400) |
| `urn:foc:user:otp-attempts-exceeded` | limit hit; the row is spent — request anew once the cooldown passes (429) |
| `urn:foc:user:otp-resend-cooldown` | resend too soon; `Retry-After` says when (429) |
| `urn:foc:user:otp-required` | no gate code requested yet (400) |
| `urn:foc:user:email-change-none` | nothing pending to verify/resend (400) |
| `urn:foc:user:email-taken` | email already registered (400) |
| `urn:foc:user:username-taken` | username already taken (400) |

## Errors

Every error body is RFC 9457 problem+json. A request body that breaks
validation rules gets 400 with one sentence per broken rule, sorted by
field (e.g. `"Email is required. Password is required."`), from
`ProblemDetailAdvice`, on every endpoint. A missing or unreadable body
(bad JSON, an unknown role) gets 400 `"Request body is missing or
malformed"`; a query parameter of the wrong type gets 400 `"Invalid
request parameter"`.

## Schema (Flyway)

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate's
`ddl-auto` is `none`. `V1__baseline` is the schema `ddl-auto` used to
generate; a database built that way is recorded as V1 on first start
(`baseline-on-migrate`) and only gets the later migrations.
`V2__username_unique_ignoring_case` renames usernames that differ only
in case (the oldest keeps its name, later ones get `_2`, `_3`, …) and
replaces the username constraint with a unique index on
`lower(username)`. `V3__pending_signups` drops the generic (always
empty) `otps` table and creates `pending_signups` — #88 replaced one
OTP table with a pending table per operation.
`V4__pending_email_changes` and `V5__account_update_otps` are #92's two
tables in that shape: the parked email change awaiting the new address's
code, and the account-update gate codes (one row per account each, FK to
`users`). Schema changes go in a new `V<n>__<name>.sql`, never
by editing an applied one.

## Demo accounts (local only)

With `USER_SEED_DEMO=true` (in `.env`; off by default), the service
inserts demo accounts at startup (`DemoAccountsSeeder`), skipping any
that already exist, so restarts are safe:

| Accounts | Role | Password |
| --- | --- | --- |
| `demo_owner` (`e9000001@u.nus.edu`) | OWNER | `OwnerPass123` |
| `demo_admin_1` … `demo_admin_3` (`e9000101`–`e9000103@u.nus.edu`) | ADMIN | `AdminPass123` |
| `demo_user_001` … `demo_user_100` (`e9100001`–`e9100100@u.nus.edu`) | USER | `StudentPass123` |

The passwords are public, so never set the flag on a real deployment.

The seeder only inserts missing accounts, and soft-deleted rows count as
present: an account that is changed (e.g. a demoted admin) or removed
(soft-deleted, kept until the day-31 purge below) is not restored on
restart. To get the originals back, reset the user database — remove the
`user-db` container and its data volume, then start again:

```sh
docker compose rm -sf user-db
docker volume rm foc_user-db-data   # "<project>_user-db-data"; foc = the repo folder name
docker compose up -d user-db user-service
```

(`docker compose down -v` also works, but wipes every service's
database.)

## Soft delete & day-31 purge (#93)

`DELETE /users/me` (bearer only, no body) stamps `deleted_at` — the row
stays, so the account's unique email/username remain reserved and the
user can recover within 30 days (#94). Repeat deletion, like every other
`/users/me` call on a deleted account, returns 404. A daily scheduler
(`AccountPurgeScheduler`, default 03:00) hard-deletes accounts whose
`deleted_at` is older than the retention window, together with their
`account_tokens` rows — that purge is what frees the identifiers. The
same daily run sweeps expired `pending_signups`, `account_update_otps`
and `pending_email_changes` rows (hygiene only: every flow rejects an
expired row on sight; the two #92 sweeps run before the account delete
so a purged user's rows never trip their FKs).
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
