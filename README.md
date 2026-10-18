<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-10; revised 2026-09-19. The
  2026-09-20 MCP server configuration entry was made with Claude Code
  (Opus 5); the other 2026-09-20 entries are Fable 5.
  Scope: team allocation table and repository-structure notes
  transcribed from team decisions (2026-09-10); the AI Use Summary
  section is maintained as the policy's consolidated disclosure.
  2026-09-19: foc-contracts structure note added per team decision D15
  (docs/notification-service.md).
  2026-09-20: AI Use Summary wording extended for the notification
  retry/DLQ implementation (issue #65), then again for the retention
  purge scheduler (issue #68), the ArchUnit broker-isolation test
  (issue #69), and the project MCP server configuration (developer
  tooling only); and again for the user-service Spring Boot scaffold
  (issue #84).
  2026-09-22, Claude Code (Sonnet 5): extended again for the
  supplier-service Spring Boot scaffold and the compose.yaml/AGENTS.md/
  .env.example wiring that went with it.
  2026-09-21: AI Use Summary wording extended for the web/ SPA
  scaffold (issue #108).
  2026-09-23: AI Use Summary extended for the user-service first-owner
  bootstrap (issue #97) by Claude Code (Opus 5.5); and again for the
  user-db infrastructure and user-service JPA schema (issues #85/#86)
  by Claude Code (Fable 5); and again for the user-service profile
  endpoints (issue #95) by Claude Code (Opus 5.5).
  2026-09-25: AI Use Summary extended for the owner-setup change
  (setup no longer limited to one owner; a setup token expiry and a
  setup log line were added and then removed) by Claude Code (Opus 5.5).
  2026-09-26: AI Use Summary extended for the supplier-service list
  endpoint (issue #133 — search/filter/paging) and the accompanying
  removal of the zone feature's remaining frontend wiring, by Claude
  Code (Sonnet 5).
  2026-09-27: AI Use Summary extended for the user-service soft delete
  and day-31 purge (issue #93) by Claude Code (Fable 5), on PR #136
  Copilot review.
  2026-09-28: AI Use Summary extended for the "Nearest to Me" sort
  fallback fix on PR #134 review (LeongWZ), by Claude Code (Sonnet 5);
  and again for the supplier-service GET /suppliers sort-validation fix
  on the same PR's review, also by Claude Code (Sonnet 5); and again to
  add the 2026-09-27 "Nearest to Me" distance-sort feature itself
  (PR #134), which PR #134 review (LeongWZ) flagged as missing from
  both this summary and ai/usage-log.md — disclosed here retroactively,
  by Claude Code (Sonnet 5); and again for the supplier image-URL
  normalization fix (broken images from the seed CSV's GitHub blob
  links), also by Claude Code (Sonnet 5).
  2026-09-26: AI Use Summary extended for the user-service admin
  endpoints (issue #96) by Claude Code (Opus 5.5).
  2026-09-27: AI Use Summary extended for the user-service soft delete
  and day-31 purge (issue #93) by Claude Code (Fable 5), on PR #136
  Copilot review.
  2026-09-27: AI Use Summary extended for the PR #135 review fixes
  (issue #96) by Claude Code (Opus 5.5).
  2026-09-29: AI Use Summary extended for the user-service OTP email
  sending (issue #88 — insert-after-verify sign-up, Gmail SMTP/Mailpit,
  pending_signups replacing the otps table) by Claude Code (Fable 5),
  on PR #150 Copilot review (the summary had missed the change); and
  again for that PR's author-review fixes (repeat sign-ups can no longer
  hijack a pending sign-up; resend cooldown) by Claude Code (Opus 5); and
  again for its re-review fixes and the web sign-up OTP screen (issue
  #109) by Claude Code (Opus 5).
  2026-09-30: AI Use Summary extended for the user-service account-update
  flows (issue #92) by Claude Code (Fable 5).
  2026-10-07: AI Use Summary extended for the user-service and web review
  follow-ups (issues #154 and #138, PR #162) by Claude Code (Opus 5.5).
  2026-10-09: AI Use Summary extended for the web profile screens
  (issue #112, PR #149) and that PR's two Copilot-review rounds, by
  Claude Code (Opus 5) — the summary had no entry for #112.
  2026-10-10: that #162 clause extended for its re-review fixes by
  Claude Code (Opus 5.5); and the #149 clause for that PR's team review
  (@Sinnez1) and issue #167, the gate-code refactor that came out of it,
  by Claude Code (Opus 5).
  2026-10-10: AI Use Summary extended for the PR #168 review fixes by
  Claude Code (Opus 5.5).
  2026-10-10: AI Use Summary extended for the order-service Spring Boot
  scaffold and its compose.yaml/AGENTS.md/.env.example wiring by
  Claude Code (Opus 5.5).
  2026-10-11: AI Use Summary extended for the order-service Requests
  entity/repository skeleton by Claude Code (Opus 5.5).
  2026-10-11: AI Use Summary extended for the credit operations (issue
  #169) by Claude Code (Opus 5.5); the conflict markers the PR #168
  merge left in this header were removed, keeping both sides. Same day:
  that clause extended for the team-review fixes, and again for the
  PR #175 review fixes.
  2026-10-11: merge-conflict markers left in this header by an earlier
  merge resolved (both sides kept, in date order), and the AI Use
  Summary extended for the logout denylist + API gateway (issue #151),
  by Claude Code (Fable 5).
  2026-10-11: AI Use Summary extended for password reset + account
  recovery (issue #94, PR #179), by Claude Code (Fable 5) — the PR #179
  Copilot review flagged the summary as not covering #94.
  2026-10-12: the #94 clause extended for the PR #179 human-review
  round (@Sinnez1), by Claude Code (Fable 5).
  2026-10-12: AI Use Summary extended for the credit reply outbox and
  outbox publisher (issue #170) by Claude Code (Opus 5.5).
  2026-10-12: AI Use Summary extended for the issue #67 remaining scope
  (web STOMP client + CONNECT-time denylist, D23) by Claude Code
  (Fable 5), on PR #185 Copilot review (the summary had missed the
  change).
  2026-10-12: AI Use Summary extended for the web revoked-session
  sign-out (issue #178, PR #182) by Claude Code (Fable 5).
  2026-10-13: the #170 clause extended for the PR #188 review round
  (Leong Wei Zhi), by Claude Code (Opus 5.5).
  2026-10-13: AI Use Summary extended for the credit read API
  (issue #172) by Claude Code (Opus 5.5).
  2026-10-13: the #178 clause extended for the PR #182 human-review
  round (@Sinnez1), by Claude Code (Opus 5).
  2026-10-14: the #172 clause extended for the PR #189 review
  follow-ups, by Claude Code (Opus 5.5).
  2026-10-10: AI Use Summary extended for the order-service Spring Boot
  scaffold and its compose.yaml/AGENTS.md/.env.example wiring by
  Claude Code (Opus 5.5).
  2026-10-11: AI Use Summary extended for the order-service Requests
  entity/repository skeleton by Claude Code (Opus 5.5).
  2026-10-17: AI Use Summary extended for the order-service request
  state machine by Claude Code (Opus 5.5), then for the Requests
  createdAt default (PR review fix).
  2026-10-17: AI Use Summary extended for the order-service request
  events (RequestsService, outbox, credit reply listener) by Claude
  Code (Opus 5.5), then for the RequestsController endpoints, then
  for the order-service ownership checks, then for the manual-testing
  scripts, stubs and the gateway order route, then for the /create field
  checks, the accepted / collected events and the expiry and
  auto-complete timers, then for the read endpoints and their filters,
  then for the web order pages on the real order API.
  2026-10-14: AI Use Summary extended for the credit request event
  listener (issue #171) by Claude Code (Opus 5.5).
  2026-10-14: the #67 clause extended for the PR #185 human-review
  round (@Sinnez1), by Claude Code (Fable 5).
  2026-10-14: that #178 clause extended again for the PR #182 second
  review round, by Claude Code (Opus 5).
  2026-10-16: the #171 clause extended for the PR #190 review round
  (Leong Wei Zhi), by Claude Code (Opus 5.5); same day, again for its
  re-review.
  2026-10-16: the #67 clause extended for the PR #185 re-review rounds
  (@Sinnez1) by Claude Code (Opus 5) — the STOMP frame policing round
  the 2026-10-14 line claims but never wrote into the clause, and the
  client's ERROR-frame policy.
  2026-10-16: PR #185 rebased onto main twice the same day (PRs #187
  and #190 landed) — the conflicts this header, the AI Use Summary,
  `.env.example` and `compose.yaml` carried resolved by keeping both
  sides with nothing reworded or removed; in the second pass the two
  order-service lines main had meanwhile moved into date order were
  left to main's copy rather than re-added here, and this branch's #67
  lines take their own date positions, by Claude Code (Opus 5).
  2026-10-16: PR #182 rebased onto main twice the same day (first with
  PRs #187/#188/#189 landed, then with #190/#185) — the conflicts this
  header, the AI Use Summary and `ai/usage-log.md` carried resolved by
  keeping both sides in date order, no clause reworded; in the second
  pass `web/src/routes/index.tsx` also conflicted with #185's
  NotificationProvider layout route, resolved by keeping that route and
  moving this branch's `handle: { requiresSession: true }` onto the
  nested ProtectedRoute it gates, by Claude Code (Opus 5).
  2026-10-16: AI Use Summary extended for the credit expiry and
  redistribution scheduler (issue #173) by Claude Code (Opus 5.5);
  same day, extended for the PR #193 review fixes.
  2026-10-16: AI Use Summary extended for the server half of issue #178
  (PR #183) by Claude Code (Opus 5) — the summary covered the web half
  only.
  2026-10-16: PR #183 rebased onto main now that its base PR #179 has
  merged — the conflicts `docs/gateway.md`, `AdminService.java` and
  `AdminServiceTest.java` carried resolved by keeping both sides, and
  the one claim #179's approval follow-up left stale (a removed admin's
  token keeping ADMIN until its exp) corrected, by Claude Code
  (Opus 5).
  2026-10-17: AI Use Summary extended for the web credit balance and
  history (issue #174) by Claude Code (Opus 5.5); same day, extended
  for the admin dashboard's Common Pool box.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-17: the #178 clause extended for the PR #183 human-review
  round (@Sinnez1) and the branch rebased again for #180's merge, by
  Claude Code (Opus 5).
  2026-10-17, Codex (GPT-6), PR #180 review: added the web #94
  disclosure and the missing 2026-10-16 main-sync coverage.
  Author review: Leong Wei Zhi to review via PR #180.
  2026-10-17, Codex (GPT-6.1 Sol), issue #98: disclosed the user-service
  Docker-unavailable test skips and verification documentation.
  PR #197 review: disclosed CI's rejection of skipped user-service tests.
  2026-10-17, Claude Code (Opus 5), PR #199 review: AI Use Summary
  extended for the web half of issue #66 (the Notification Center) and
  that PR's review round — the summary had the backend half only.
  Author review: Leong Wei Zhi to review via pull request.
  2026-10-17: AI Use Summary extended for the upcoming credit expiries
  (issue #196: the route and the credits page) by Claude Code
  (Opus 5.5).
  2026-10-18: AI Use Summary extended for the issue #165 follow-ups
  (a duplicate reservation resends its reply, the contract test's
  parties check, the credit legend) by Claude Code (Opus 5.5).
  Author review: Ryan Ang, pending pull request review.
-->

# CS3219 — Software Design and Architecture (AY2627 Sem 1)

## Favours on Campus (FoC)

**Favours on Campus (FoC)** is a peer-to-peer campus errand platform where
students can request items to be collected from stores or facilities on
campus, and other students can fulfil (and deliver) those requests. The
platform runs on a closed credit economy — credits cannot be bought,
withdrawn, or exchanged for money, and only circulate within the platform.

---

## Team Members

| Name | Service In-Charge | Nice-to-have |
| ----- | ----- | ----- |
| Kwey Xiu Xi | User Service | Cloud Deployment and DevOps |
| Alastair Tan Choon Wei | Supplier Service | — |
| Aung Ko Khant | Order Service | Centralized Logging |
| Ryan Ang Jun Wen | Credit Service | Order History |
| Leong Wei Zhi | Notification Service | Rating System |

---

## Repository Structure

This repository follows a **one-service-per-folder** structure: each
microservice (`user-service/`, `supplier-service/`, `order-service/`,
`credit-service/`, `notification-service/`) lives in its own top-level
folder. The frontend lives in `web/`.

```text
.
├── web/
├── user-service/
├── supplier-service/
├── order-service/
├── credit-service/
├── notification-service/
├── foc-contracts/
├── <n2h-service>/
└── README.md
```

- Any **nice-to-have (N2H)** feature that warrants its own service should
  be added as an **additional folder** at the same level, following the
  same per-service structure.
- `foc-contracts/` is a shared library (not a service) holding
  cross-service contract constants such as broker exchange names and
  the typed event contracts (event records + registry + fixtures) —
  team decision D15 (`docs/notification-service.md`).
- Files for agentic coding tools (e.g. agent configs, prompts, skills)
  may be added as needed, but must still **respect the
  one-service-per-folder skeleton** for core implementation.

---

## AI Use Summary

This section is the consolidated AI-use disclosure required by the CS3219
AI Usage Policy (project document, Appendix 2). The detailed log with
prompts and timestamps lives in [`ai/usage-log.md`](ai/usage-log.md).

**Tools:** Claude Code (Fable 5, Opus 5, Opus 5.5, Sonnet 4.6, Sonnet 5, Sonnet 5.5), Claude (Sonnet 5)

**Prohibited phases avoided:** requirements elicitation and
prioritization; architecture and design decisions. Requirements, service
boundaries, allocation, and the tech stack were decided by the team; AI
tools were used only afterwards.

**Used for:** transcribing the team-written D1 backlog into labelled
GitHub issues; repository documentation (README, AGENTS.md); project
scaffolding (service folder skeletons); drawing the high-level
architecture diagram (`docs/architecture.md`) from the team-decided
service boundaries and D1 requirements — open design decisions are
marked TBD in the diagram and remain with the team; implementation
scaffolding code (Spring Boot service skeletons, Docker/compose
wiring, JPA entities, message-broker topology declarations, the
shared `foc-contracts` library — contract constants and the typed
event records/registry with their canonical JSON fixtures and contract
tests) and the notification event pipeline (AMQP listener, idempotent
event processor, broker-native retry/dead-letter failure handling,
STOMP push gateway with JWT-authenticated sessions, retention purge
scheduler, an ArchUnit test enforcing the broker-isolation boundary,
unit and integration tests) written strictly from the team's
finalized design docs and issue specifications, with per-file
attribution headers; developer tooling configuration (the
project-scoped MCP servers declared in `.mcp.json`, documented in
`AGENTS.md`); the user-service Spring Boot scaffold (issue #84) —
pom, application config, Dockerfile, and application/test classes
mirroring the notification-service pattern, with the dependency-set,
Dockerfile-shape, and config-scope choices made by the author via
neutral-options Q&A; and the supplier-service Spring Boot scaffold —
pom (completed from the author's own partial postgres/opencsv
dependency fragment), application config, Dockerfile, and
application/test classes mirroring the same pattern, plus the
compose.yaml supplier-db/supplier-service wiring, `.env.example`
section, and `AGENTS.md` port-table entry that went with it, with
scope confirmed by the author via options Q&A (including how to
resolve a pre-existing compose.yaml anomaly found in the working
tree); the `web/` SPA scaffold (issue #108) —
Vite/React/TypeScript project skeleton, react-router route table with
the shared app shell (wireframe top bar and mobile tab bar) and 404,
shared API fetch wrapper, tooling configuration, and Docker/compose
wiring, deliberately without a data-fetching library, with the stack,
tooling, scope, and port choices made by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`; and the
user-service first-owner bootstrap (issue #97) — request/response DTOs,
the `User` entity transcribed from the team's schema, repository,
service, controller, setup-token gate, and its unit and Testcontainers
integration tests, plus the fixes from the PR #126 review; and the
user-db infrastructure and user-service JPA schema (issues #85/#86) —
compose.yaml user-service/user-db wiring with the `.env.example`
section and `AGENTS.md` port-table entry that went with it, datasource
configuration, the `Otp`/`AccountToken`/`TokenDenylistEntry` entities
transcribed from the team's design-doc erDiagram, the `Role` enum
conversion of the merged String role column, and per-entity
persistence round-trip tests, with the image-version, port,
role-representation, FK-shape, and test-scope choices made by the
author via neutral-options Q&A and recorded in `ai/usage-log.md`; and
the user-service profile endpoints (issue #95) — `GET /users/me` and
`GET /users/{id}`, their service, DTO, not-found exception, repository
query, and unit and Testcontainers integration tests, with the paths,
response fields, not-found behaviour, and caller-identity choices made
by the author and recorded in `ai/usage-log.md`, plus the PR #131
review fixes (guarded caller-id parse, problem+json 404, shared
`UserResponse` mapping, shared Testcontainers base, and added test
assertions), each chosen by the author; and the removal of the
existing-owner check from user-service owner setup, decided by the
author, with the matching test, comment, and `.env.example` updates;
a setup token expiry and a setup log line were also built, then
removed by the author, with expiry deferred to a later issue; and the
supplier-service list endpoint (issue #133) — `GET /suppliers` with
search-by-name, category filtering (joined against the
`supplier_categories` table), paging, and sorting, plus the response
DTOs and CORS configuration, with the default page size, sort field,
and response-envelope shape chosen by the author; alongside this, the
`web/` suppliers page's remaining zone-feature wiring (the `Zone` type,
the filter bar's zone dropdown, the edit form's required Campus Zone
field, and a call to a `/zones` endpoint the backend never implemented)
was removed, per the team's decision to drop the zone feature, since
the unpopulatable required field was blocking every supplier edit.
user-service admin endpoints (issue #96) — user list with search, role
filter, sorting and fixed page sizes, promote/demote, and soft-delete
removal, with their service, request DTO, URL-based role rules,
and unit and Testcontainers integration tests, following rules decided
by the team and recorded in `ai/usage-log.md`, plus the PR #135 review
fixes (fail-closed /users security rules, shared caller-id,
active-user lookup and problem+json handling, optimistic locking on
users with a 409 on a lost race, per team decision); and the
user-service soft delete and day-31 purge (issue #93) — `DELETE
/users/me`, the shared `User.softDelete` entity method, the
`AccountPurgeScheduler` with its purge repositories and
config/compose/`.env.example` wiring (mirroring the notification
retention purge), and the unit, integration, and reuse-block tests,
with the endpoint-confirmation, shared-path, and FK-cleanup choices
made by the author via neutral-options Q&A and recorded in
`ai/usage-log.md` (the 30-day reuse block itself needed no new code
and was pinned with tests); and the 2026-09-27 "Nearest to Me" distance-sort feature (PR #134) —
`SuppliersRepository.searchOrderedByDistance` (a native query, since
the Haversine distance calculation needs trig functions JPQL doesn't
expose), the controller's optional `lat`/`lng` params, the client-side
`distance.ts` display formatting, the sort control UI, and a required-
field asterisk added to the supplier form, with the query approach and
the geolocation-denied/unavailable fallback (name-sort with a notice)
decided by the author; the "Nearest to Me" sort fallback fix
(PR #134 review, LeongWZ) — the render-phase guard falling back to
name-sort on an unsupported browser only fired once (it self-gated on
the notice it set), leaving the sort stuck and the supplier list frozen
on any later filter change; fixed by rejecting the sort selection in
the `onSortChange` handler instead, per the reviewer's suggested
approach; and the supplier-service `GET /suppliers` sort-validation fix
(same PR's review) — an unrecognized `sort` value threw an unhandled
`PropertyReferenceException` (500) because Spring Data resolves Sort
against the JPA entity, not the response DTO's field names, and the
service had no error handler for it; fixed with an explicit sortable-
property allow-list returning a 400 problem+json body instead, plus
unit tests, per the reviewer's suggested approach; and a supplier
image-URL fix — the seed CSV's `ImageURL` column points at GitHub's
file-viewer page (`github.com/.../blob/...`), which serves an HTML
page, not the image itself, so those supplier photos rendered as a
broken image; since the course-provided CSV can't be edited, the fix
rewrites the URL to its `raw.githubusercontent.com` equivalent at the
API response boundary instead, with tests, verified live in-browser; and
the web Admin Dashboard user management (issue #113) — the Users section wired to the #96
endpoints with debounced server-side search, a role filter and
paging, a shared `Pagination` component, a dev-only in-memory mock
behind `VITE_MOCK_ADMIN_API`, the Suppliers section reduced to a
placeholder and the credit mock removed, the merge with PR #139's auth
routes (with its `ProtectedRoute` lint fix and the 404 route moved
outside it), the PR #140 review fixes, and the admin page tests, with
the behaviour chosen by the team and recorded in `ai/usage-log.md`;
and user-service sign-up, login and JWT issuance (issues #87, #89, #90)
— `POST /auth/signup`, `POST /auth/login` with the 5-failure lockout,
HS256 token minting, CORS, their tests, the removal of PR #139's
separate `user-auth` server and the login/register pages pointed at
user-service, plus the PR #141 review fixes (shared sign-up and
owner-setup rules, case-insensitive usernames, the token lifetime unit,
login-only race handling, `apiFetch` on the login and register pages),
with auth placement, OTP deferral, username case and response shapes
decided by the team and recorded in `ai/usage-log.md`; and the
supplier-service JWT verification + role gate (issue #106) — a Spring
Security filter chain (`JwtVerifier` mirroring notification-service's,
a `JwtAuthenticationFilter`, and problem+json 401/403 handlers) gating
`GET /suppliers*` on any authenticated user and every other method on
ADMIN or OWNER, per the team's design doc D2, with the JWT `role`
claim's name/shape confirmed against user-service's already-merged
token issuer rather than invented, the GET-authentication requirement
(which breaks browser supplier-browsing until the frontend's separate
auth-wiring task lands) confirmed with the author before implementing,
and OWNER's inclusion in the gate (PR #143 review, LeongWZ) decided by
the author to match user-service's own admin-equivalent treatment of
OWNER — each per AGENTS.md's restriction on agents making
interface/design decisions; tested live against running containers and
recorded in `ai/usage-log.md`; and the login/sign-up page restyle to the
wireframes (PR #142) — card layout, stacked labels, error alert boxes,
a live password checklist mirroring the server's password rules, the
`react-router-dom`-to-`react-router` import cleanup, the JWT wiring
into the shared `apiFetch` (the stored session's access token now sent
as the Authorization header, with the session types corrected), the
distinct 429 lockout response in user-service (issue #145, decided by
the author: the wireframe's lockout message wins over fully
non-revealing login failures), and the PR's Copilot-review
accessibility fixes, with the wireframe target and checklist scope
chosen by the author via neutral-options Q&A and recorded in
`ai/usage-log.md`; and the
user-service Spring Security filter chain (issue #91) — bearer JWT
verification mirroring notification-service's `JwtVerifier`, role claim
to `ROLE_*` authorities, stateless sessions, problem+json 401/403
responses, and their unit and integration tests, following design doc
§3, with the skipped routes (`/auth/**` except `/auth/logout`, and
`/actuator/health`) and the denylist deferral decided by the author and
recorded in `ai/usage-log.md`; and the issue #147 clean-up — a shared
`errorMessage` helper and `ConfirmModal`, the suppliers page on the
shared `Pagination`, `axios` removed, Prettier on the auth pages,
review headers and the admin mock comment updated, and login/sign-up
page tests, recorded in `ai/usage-log.md`; and the issue #147
team-decision items — Flyway for user-service with a case-insensitive
username index and duplicate clean-up, an `includeDeleted` admin list
filter with a "Show removed accounts" toggle, one validation error
format across user-service, bcrypt outside the database transaction
with row-locked login counters, an "account created" notice, and an
admin-only route guard on `/admin` that checks the role with
`GET /users/me`, an Admin Dashboard nav link shown to admins and
owners only, fixes to the admin Users section's reload after an
action, and a read-only supplier list on the admin dashboard, with the
behaviour decided by the team and recorded in `ai/usage-log.md` (that
supplier list later removed, leaving the dashboard with only the Users
section for now, also recorded there); and
the login errors that name their
cause (issue #146) — an unknown account, a wrong password with the
attempts left before the lock, and a lockout that counts down the
minutes left and repeats them in `Retry-After`, with their tests and the
first tests for the web login page, the wording and the trade-off (login
now reveals whether an account exists, reversing #89's non-revealing
failures) chosen by the author via neutral-options Q&A and recorded in
`ai/usage-log.md`; and user-service OTP email sending (issue #88, PR
#150) — sign-up reworked to the design doc's insert-after-verify shape
(`POST /auth/signup` now parks a `pending_signups` row and emails a
6-digit code, new `POST /auth/signup/verify` creates the account), the
generic `otps` table removed for per-operation pending tables, an
`OtpService`/`OtpEmailSender` pair sending over SMTP (Gmail SMTP as the
provider, a pinned Mailpit compose container as the local target), the
purge scheduler's expired-pending sweep, their unit and integration
tests, and the PR #150 Copilot-review fixes (locked verify reads,
timing-equalized unknown emails, expiry boundary, sub-minute TTL
wording, finite SMTP timeouts), plus the PR #150 author-review fixes
(a repeat sign-up can no longer rewrite a live pending sign-up's
username and password — only a repeat of its own details resends, other
details are a 409, and an expired row may be taken over — with a resend
cooldown answering 429 + `Retry-After` and the wrong-code attempts now
surviving a resend), with the provider, scope, local-mail,
pending-tables and repeat-sign-up decisions made by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`; and that PR's
re-review fixes — a pending sign-up whose email or username was taken
meanwhile is discarded instead of blocking its address, a resend no longer
extends the code's expiry (so a pending sign-up can no longer be held
open indefinitely), the resend cooldown now travels in the 202 body with
`Retry-After` exposed through CORS, and the sign-up OTP screen in `web/`
(a six-box code dialog over the register page with resend and cooldown,
part of issue #109) without which registration through the site
dead-ended at a failed login — with the fix-versus-restructure choice,
the dialog's shape and the cooldown's route all decided by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`.
`ai/usage-log.md`; and an
opt-in demo-account seeder for user-service (`USER_SEED_DEMO`: 1
owner, 3 admins, 100 users), with the mechanism, the off-by-default
flag and per-role passwords decided by the team and recorded in
`ai/usage-log.md`; and the user-service account-update flows
(issue #92) — a gate code emailed to the account's current address
before any change, `PATCH /users/me` as an allow-list DTO (username
applies at once, an email change parks in `pending_email_changes`
until a second code sent to the new address is verified), an
OTP-gated password change with server-side double-entry and the
sign-up policy re-checked, machine-readable problem+json `type` URIs
on the OTP errors (sign-up's included), the V4/V5 migrations, purge
sweeps, and unit and integration tests, with the scope, endpoint
shapes, gating and response choices made by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`; and the `web/`
profile screens (issue #112, PR #149) — the protected `/profile` page
built on those endpoints: the details card, an OTP-gated Edit Account
card, an OTP-gated password change, the second code step a new address
confirms itself with, the delete-account confirmation, a shared
code-step component and countdown module, a problem+json `type`
classifier that replaced the sign-up dialog's matching on error
sentences, and the page's tests, with the deviations from
`docs/wireframes/profile.png` (no Current Password field, the new-email
confirmation as a modal, the parked change snapshotted in
localStorage) chosen by the author via neutral-options Q&A and
recorded in `web/AGENTS.md` and `ai/usage-log.md`; plus that PR's two
Copilot-review rounds — pending-email snapshots scoped to the account
that parked them, the code step restored after a retryable save,
Spring's `about:blank` read as "no type", body-validation refusals
returning to the editable fields with the unspent code, an expired
email change offering a restart instead of the resend user-service can
only refuse, and the retained-code tests asserting the call that was
meant to prove them; and that PR's team review (@Sinnez1) — the delete
dialog sealed while its request is in flight, the page reading the
session's one `GET /users/me` and writing saves back into it instead of
keeping a private copy that went stale for the nav bar and the `/admin`
guard, a resend-cooldown 429 counted down on the submit button rather
than read as a wrong code, a kept code offered only while it is still
alive, and an untyped 429 ending a pending sign-up on its status;
followed by issue #167 from the same review — the account's single gate
code moved out of the two cards into one `useGateCode` hook the page
owns, so a code either card requests, spends or replaces is the same
code for the other, and their drifted-apart request paths became one,
with the shape (hook, placement, what stays per card) decided by the
reviewer; and the user-service and web review follow-ups (issues #154 and #138, PR #162)
— problem+json on every user-service endpoint (the shared
`ResponseStatusException`, wrong-type-parameter and 415 handlers), a
generic owner-setup 503, the demo seeder's `Locale.ROOT` formatting and its handling of
a lost insert race (a duplicate key only; any other integrity failure
still stops startup), tests that load the main `application.yaml`
through a `test` profile, the "account created" notice on the login
page showing once, and the nav bar border class — with the items taken
from the team's review issues and recorded in `ai/usage-log.md`,
and that PR's review fixes (test values pinned against the
environment, the 415's `Accept` header kept, and the login attempt
counter's read-then-refresh restored by the author's choice) and
re-review fixes (the purge cron and owner setup token pinned as well,
a seeding run that loses the insert race seeding once more by the
author's choice among the reviewer's options, the unconfigured setup
token logged server-side, once per process, the login page keeping
the URL's query and hash when it clears the notice state, and a 405
problem+json handler); and the
Credit Service saga event contracts in `foc-contracts` (PR #163) —
four event records, the `credit-events` exchange name, their
fixtures and the registry tests — together with that PR's review
fixes to `docs/credit-service.md`, the high-level architecture doc
and diagram, the notification design doc and the foc-contracts
convention wording, which were brought in line with it, and the
notification-service contract test generalized to the two event
domains; the saga, the
event names and fields, and the database schema are the team's
decisions, recorded in `docs/credit-service.md` and
`ai/usage-log.md`; and the credit-service scaffold (Spring Boot app,
Dockerfile, credit-db in compose, the Flyway V1 schema transcribed
from the design doc, JWT verification copied from supplier-service,
and their tests), with the ports, image, dependency scope, schema
scope and test database chosen by the author via options Q&A and
recorded in `ai/usage-log.md`, plus `note` made `@Nullable` on the
request event records (the author's decision, issue #165), and the
scaffold's JPA entities (one per V1 table), empty repositories and the
`CreditOperations` / `ReplyOutbox` interfaces with stubs that throw —
the interface signatures and the `ReserveResult` values supplied by the
author, with no business logic; and that PR's review fixes (a slice
amount check and index in V1, the team's decision that the release
path get-or-creates the requester's account written into
`docs/credit-service.md`, a `SettleResult` returned by transfer and
release by the author's choice among the reviewer's options, an index
on unsent outbox rows, and tighter schema and contract tests), and its
request event handler and broker topology (the handler, a message
converter and topology initializer copied from notification-service,
the queues and bindings, their configuration and tests), written to
the author's plan, with the decisions recorded in
`docs/credit-service.md` and `ai/usage-log.md`; and that PR's review
fixes (#168: the reserve result split by refusal cause and an
unhandled event logged, both the team's decisions among the reviewer's
options, tests tying the bound events to the handler and exercising
its transaction, and the disclosure headers the review found
missing); and the credit operations (issue #169: get-or-create,
reserve, transfer and release, their repository queries and tests),
written to the team's design in `docs/credit-service.md`, with the
readings the tool took where the doc is silent listed in
`ai/usage-log.md` for the author to confirm, together with the task
issues #169 to #174 that split the remaining Credit Service work; and
that work's team-review fixes (released credits returned to the lots
they came from, with the V1 schema amended so a slice references its
lot, the sign-up amount and earned-credit lifetime made configuration,
a log line per operation, and further tests), all the team's decisions,
recorded in `docs/credit-service.md` and `ai/usage-log.md`; and that
PR's review fixes (#175: a completion that finds no reservation is
retried instead of dead-lettered at once, a removed race test restored,
and the expiry and rejected-record rules moved from open items into the
design doc), the team's decisions on the reviewers' findings; and the
reply outbox and outbox publisher (issue #170: the outbox row written
in the caller's transaction, the scheduled publisher with publisher
confirms and the mandatory flag, and their tests), written to D10 of
`docs/credit-service.md`, with the poll interval, the batch cap, the
confirm wait and what a failed publish does to the rest of a poll
decided by the team and recorded there and in `ai/usage-log.md`; and
that PR's review fixes (#188: a returned reply waits 30 s before its
next attempt, on a new `next_attempt_at` column, so that unroutable
replies cannot fill every batch; the reply row inserted, not saved; no
returns callback on the shared template; a second scheduler thread),
the approach to each decided by the team; the read API (issue #172:
the balance and history routes, the history filters as a JPA
Specification, the 400 problem+json mapping, and their tests),
transcribed from the "Read API contract" the team wrote in
`docs/credit-service.md`, with its PR #189 review follow-ups made as
the reviewer suggested; and the request event
listener (issue #171: manual acknowledgement after the handler's
commit, a conflicting event dead-lettered, failures retried through
the retry queue up to the attempt cap, and its unit and broker
integration tests), written to D11 of `docs/credit-service.md` and
following notification-service's listener, with the team's review
decisions (a retry copy the broker refuses requeues the original;
dead-letters logged at ERROR) recorded in that doc's failure table,
and its PR #190 review fixes (a returned retry copy dead-letters the
original, a nacked or unconfirmed one requeues it after a 5 s pause,
and a timed-out confirm documented as possibly running the event
twice), the approach to each decided by the team, and its re-review
follow-ups (the confirm timeout's environment variable, an
unresolvable broker host in the test profile, and doc wording);
and the expiry and redistribution scheduler
(issue #173: the daily expiry into the common pool and the monthly
redistribution, their locking queries, and their unit and Postgres
tests), written to D2, D4 and D12 of `docs/credit-service.md`, with the
run times and zone, the per-account expiry transaction, the empty
month recorded and the startup catch-up decided by the team and
recorded there, and its PR #193 review fixes (one routine, daily and
at startup, that sweeps then redistributes a missing month, so a
failed run is retried the next day; the earned-credit expiry shared;
a test made independent of test order), the approach decided by the
team; and the upcoming-expiry route (issue #196:
`GET /credits/me/expiries`, read from the lots in spend order, and its
controller tests), written to the design doc's Read API contract, with
the route, the response shape, the order, which credits count, no
window and how often the web app reads it decided by the team and
recorded there, and its web half (the Available card's line for the
soonest credits to expire and a notice when they expire within 7
days, in Singapore dates, with their tests), where they are shown and
the wording also decided by the team.

The logout denylist and API gateway (issue #151): the user-service
logout endpoint, Redis denylist writer and V6 migration with their
tests, the redis and gateway containers in compose, the OpenResty
gateway (`gateway/` — nginx config and the Lua auth check), the web
logout call and gateway base-URL cutover, and the documentation
(`docs/gateway.md`, architecture doc/diagram updates). Every design
decision (G1–G10: gateway technology, Redis-only store, full routing
cutover, gateway-only enforcement, anonymous pass-through, fail-open,
AOF persistence, logout placement, best-effort 204, revoked-token
wording) was made by Leong Wei Zhi via neutral options Q&A, recorded in
`docs/gateway.md` and `ai/usage-log.md`.

Password reset and account recovery (issue #94, PR #179): the
user-service reset endpoints, `PasswordResetService`, the
`password_reset_otps` entity/repository and V7 migration, the login
change (no silent recovery; `deletionScheduledFor`) with
`POST /users/me/recover`, and the purge-sweep addition (the web
screens moved to PR #180, which discloses them). Every
flow decision — OTP-code credential (revisited from an emailed link
the same day), login-plus-explicit-restore recovery, reset leaving a
deleted account deleted, the always-202/one-400 non-revealing error
contract, the storage's `password_reset_otps` shape without a kind
column, and the backend-only scope with the web half split to
PR #180 — was made by Leong Wei Zhi
via neutral options Q&A, recorded in `ai/usage-log.md` and the files'
disclosure headers; the PR #179 Copilot-review fixes (stale-credential
login recheck, lock-first email reads, purge lock ordering, form-state
clearing) followed the same pattern, as did the PR #179 human-review
round (@Sinnez1): the carried OTP guess budget, the post-commit email
send, the per-user session eviction on reset (gateway decision G11 —
`TokenDenylist`, `gateway/lua/auth.lua`), the accepted 30-day
deleted-account access, admin removal staying self-restorable, and the
Windows test-precision fix, each chosen by Leong Wei Zhi via neutral
options Q&A on 2026-10-12, and the approval round's follow-ups the
same day (admin removal demoting to USER, a failed renewal send
restoring the pre-renewal OTP row — both chosen by Leong Wei Zhi via
neutral options Q&A — plus the reviewer-flagged documentation and
test-isolation corrections).

The order-service frontend pages (Create Request, My Requests, Open
Requests, Active Delivery, Request History) were generated in `web/` from
the committed wireframes, against a placeholder API contract and a
dev-only in-memory mock; the backend was not touched.
The order-service Spring Boot
scaffold — pom, application config, Dockerfile, and application/test
classes mirroring the supplier-service and notification-service
pattern (including the broker connection settings and the
`foc-contracts` dependency, with no publisher code), plus the
compose.yaml order-db/order-service wiring, `.env.example` section,
and `AGENTS.md` port-table entry that went with it, with the database
engine and image version, host ports, and broker-wiring scope chosen
by the author via neutral-options Q&A and recorded in
`ai/usage-log.md`; and the order-service `Requests` entity and
repository (table name and columns given by the author as an ER
diagram, which the tool transcribed into the entity unchanged); and
the order-service request state machine (a plain-Java transition table
and its unit tests over the author's own state and event enums, with
the DROP target, the no-library form, the PENDING/REJECTED saga
transitions' names and the ARRIVED step chosen by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`), and a
`createdAt` default on the `Requests` entity (PR review fix); and
the order-service request events — `RequestsService` applying the state
machine, a transactional outbox and publisher and a credit reply
listener with retry and dead-letter queues copied from credit-service
and notification-service, the pending-timeout sweep, events on
submit / reserve / reject / timeout / arrive / complete / cancel /
expire, and `status` as the `OrderStates` enum with a database check —
with the event names, which transitions publish, the field mapping and
parties, the copy-not-share choice, the timeout values and the check's
form chosen by the author via neutral-options Q&A and recorded in
`ai/usage-log.md`; and the `RequestsController` (seven POST endpoints
delegating to `RequestsService`, with tests; paths, parameters and
request/response shapes specified by the author); and order-service's
bearer-token auth (copied from credit-service) with the ownership rules
for each step and the self-accept refusal, the rules, the ids-from-token
choice and the 409 / 404 answers chosen by the author; and the
`order-event.ps1` / `order-curl.ps1` / `order-e2e.ps1` manual-testing
helpers, the `credit-stub.ps1` / `stub-users.psd1` stand-ins for the
Credit and User Services, and the gateway's `/api/order` route
replacing the reserved stub; and `/create`'s field checks (400 listing
missing fields, a reward below 1 and a past deadline), the
`request.accepted` / `request.collected` events and the deadline-expiry
and auto-complete timers, with the required fields, the 400 answer, the
events' parties, the 5 min sweeps and the configurable 1 h delay chosen
by the author; and the read endpoints (open errands, a request's
detail, the caller's requests by role, the admin view), whose paths,
results and access rules were designed by the author and whose
remaining details (CREATED-only open list, trimmed lists, newest first,
page size 20, both roles in the admin view) the author chose; and the
lists' filters (supplier, reward, deadline and created ranges) and sort,
chosen by the author; and past-deadline requests hidden from the open list
and refused on accept (409), as the author chose; and the order pages in `web/` moved onto
order-service's real API (types, calls, states, the dev mock and tests
rewritten; dispute and re-release removed), with the username lookup, the
state filters and keeping the mock chosen by the author.

The issue #67 remaining scope (the server-side STOMP push gateway had
merged earlier in PR #74): the web notification client
(`web/src/features/notifications/` — the app's single STOMP client with
reconnect/backoff, and the bell's unread badge), the notification-service
JWT verifier's missing-expiry fix, and the D23 CONNECT-time Redis
denylist read (port + adapter, interceptor check, ArchUnit rule,
compose/.env wiring) with the matching doc updates
(`docs/notification-service.md` D23, `docs/gateway.md` known-limit
retirement).
The scope decisions (badge-only UI, native WebSocket without
sockjs-client, folding the expiry fix in, service-side denylist read as
a narrow G4 exception) were made by Leong Wei Zhi via neutral options
Q&A, recorded in `docs/notification-service.md` and `ai/usage-log.md`.
The PR #185 review round (@Sinnez1) extended that denylist read to the
G11 per-user reset cutoff (the jti lookup alone let a pre-reset token
still open a socket), recorded the CONNECT-only timing as an accepted
limit in both design docs, and switched off the Redis starter's
repository scan; the Copilot round before it fixed the bell's
accessible name. The re-review rounds that followed added the
interceptor's frame policing (a STOMP-command session authenticated
like CONNECT, every client SEND refused, SUBSCRIBE confined to the one
destination — together they had let an untokened client push a spoofed
notification to any user ID) and the client's ERROR-frame policy: three
retries 30 s apart, then a stop the bell reports as paused updates
rather than a frozen count. That policy was chosen by Leong Wei Zhi
from four options via neutral options Q&A; the rest were mechanical
fixes to reviewer-identified gaps.

The web revoked-session sign-out (issue #178, PR #182): the shared
fetch wrapper's unauthorized handler (a 401 on a token-bearing request
forces a sign-out to /login with a one-time notice; the login call
itself opts out of the token injection so its wrong-credentials 401
stays an inline error) and its tests. The mechanism (any token-bearing
401, no detail matching), the sign-out UX and the scope were decided
by Leong Wei Zhi via neutral options Q&A, recorded in
`ai/usage-log.md`. The PR #182 human-review round (@Sinnez1) added two
defect fixes with no new design decisions: the handler is given the
token the refused request carried, so a 401 answering a replaced token
no longer ends the session the user has just started; and the notice
is published to the login page rather than read at mount only, so a
401 arriving while that page is open shows it there instead of saving
it for a later visit. The second review round (@Sinnez1) added four
more defect fixes — the refused token is compared with what
localStorage holds rather than with the last rendered session (so
neither another tab's newer session nor one stored a render ago is
cleared), the handler and token source are unregistered when the
provider unmounts, a failing handler no longer replaces the ApiError
callers match on, and the bearer scheme is matched case-insensitively
as the gateway matches it — plus one behaviour question put to Leong
Wei Zhi via neutral options Q&A (2026-10-14): on a page that needs no
session the dead token is now dropped in place, with no redirect and
no notice, and the redirect for a session-gated page is
ProtectedRoute's own bounce.

The server half of the same issue (#178, PR #183): `AdminService`
writes the per-user revocation stamp after an admin role change (any
actual change) or an admin removal, so the target's outstanding tokens
are refused at the gateway on their next request instead of lasting to
their `exp`; its service and controller tests; and decision G12 in
`docs/gateway.md`, which needed no gateway code because PR #179's G11
check already enforces the stamp. The mechanism (option A of the
issue's options table, a Redis epoch stamp over a per-request role
lookup or refresh tokens) and the scope (those two paths only) were
decided by Leong Wei Zhi via neutral options Q&A, recorded in
`ai/usage-log.md`. Rebasing that branch onto the merged #179 also
corrected a sentence #179's own follow-up had added — that a token
minted before a removal keeps ADMIN until it expires — in
`user-service/README.md` and the `AdminService` comment. The PR #183
human-review round (@Sinnez1) added one defect fix with no new design
decision: the reviewer showed the stamp being written inside the
caller's transaction, before the role change or removal committed, so a
login in that gap could still mint a token carrying the old role —
every stamp now runs after the commit, including the identical call
site in the password reset from #179. Which of the two mechanisms the
review named to use, and whether to extend the fix to that reset call
site, were decided by Leong Wei Zhi via neutral options Q&A
(2026-10-17).

The web half of issue #94 (PR #180), generated with Claude Code
(Fable 5; main sync with Opus 5), adds the forgot-password and explicit
account-recovery screens, login link and arrival notice, recovery session
flag handling, and support for bodyless 202 reset responses. The
2026-10-16 sync reconciled the routes with #182/#185, opted both reset
calls out of bearer authentication, and added local session clearing on
successful reset with regression coverage. The PR #180 review fixes
(Codex, GPT-6, 2026-10-17) extend the recovery hold to public routes,
reject malformed nonempty 202 bodies, move the recovery request into
profileApi, remove the duplicate home redirect, reuse the six-box
CodeInput, and require the reset email field. The reset API returns no
cooldown or expiry timings; the screen does not display those values.

Issue #98's user-service test fix (Codex, GPT-6.1 Sol, 2026-10-17) adds
inherited automatic skips when Docker is unavailable and guards shared PostgreSQL
container startup. Unit tests still run without Docker; integration tests
retain their existing shared container when Docker is available. The
service README documents both modes and their coverage. The PR #197 review
follow-up adds a CI check that rejects skipped user-service tests and
missing test reports, enforcing the full suite on the runner.

The web credit balance and history (issue #174): the credit feature
folder (`web/src/features/credit/`: the read API calls, a
`CreditBalanceProvider` layout route that polls the balance for the
whole app, the `/credits` page with its balance cards, amount and
date-range filters and paged transaction list), the live top-bar badge
and the profile card's Credit Status, and their tests. Written to the
"Read API contract" in `docs/credit-service.md` and the
`credit-history.png` wireframe; the deviations from the wireframe (no
lifetime-earned card or running-balance column, which the API does not
provide, and min/max amount fields in place of the amount dropdown),
the page's route and how it is reached, and the 30 s poll that pauses
in a hidden tab were decided by the team via options Q&A, recorded in
`web/AGENTS.md` and `ai/usage-log.md`. On the same branch, the admin
dashboard's Common Pool box and the credit service's two admin routes
behind it (`/credits/admin/pool` and `/credits/admin/redistributions`,
`ADMIN` and `OWNER` only) with their tests, added to the design doc's
Read API contract; the routes' shape, access, the share preview and
the branch were decided by the team via options Q&A.

Claude Code (Fable 5 and Opus 5) wrote the web half of issue #66, the
Notification Center: the `/notifications` page and its feature folder
(`web/src/features/notifications/`: the REST calls, one display
template per cataloged request event, the paged list with its All and
Unread filters, click-to-mark-read and "Mark all as read"), the bell
badge seeded and re-synced from `GET /notifications/me/unread-count`,
and frames pushed over STOMP joining the open list live, with their
tests. Written to the `notification-center.png` wireframe and the REST
contract in `docs/notification-service.md`; the interactions the
wireframe does not draw (the filter toggle, the "Load more" pager, a
click marking a row read, and — after the PR #199 review — a read row
leaving the Unread view) were decided by the author via options Q&A and
recorded in `web/AGENTS.md` and `ai/usage-log.md`. The same tool
addressed that review's bot findings, including the paging and
count-sync races it and a live browser run surfaced.

Claude Code (Opus 5.5) applied follow-ups from Leong Wei Zhi's PR #163
re-review (issue #165), each the reviewer's suggestion taken by the
team: a redelivered `request.submitted` whose reservation is already
held or refused sends its reply again, rebuilt from the event with the
record's decision; the notification-service contract test again
requires request events to name their parties; the credit design doc's
diagram legend states where it differs from `architecture.md`; and, at
the author's instruction, the implementation column of that doc's
Credit F2.1.3 row was reworded. The same tool then applied Leong Wei
Zhi's PR #212 review suggestions: the Credit F2.1.3 requirement text
restored and left in "Requirements to revise" for the team, the resent
reply's comments and docs corrected, and the reserve and `parties()`
comments extended.

**Verification:** all AI-assisted output is reviewed by the team through
pull requests before merging.
