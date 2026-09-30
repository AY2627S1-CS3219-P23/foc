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
  Reviewed by: Leong Wei Zhi (via pull request).
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

**Tools:** Claude Code (Fable 5, Opus 5, Opus 5.5, Sonnet 4.6, Sonnet 5), Claude (Sonnet 5)

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
behaviour decided by the team and recorded in `ai/usage-log.md`; and
the login errors that name their
cause (issue #146) — an unknown account, a wrong password with the
attempts left before the lock, and a lockout that counts down the
minutes left and repeats them in `Retry-After`, with their tests and the
first tests for the web login page, the wording and the trade-off (login
now reveals whether an account exists, reversing #89's non-revealing
failures) chosen by the author via neutral-options Q&A and recorded in
`ai/usage-log.md`; and supplier
Add / Edit / Delete on the admin dashboard (reusing the Suppliers page's
form and delete dialogs, with a retry after a failed load, and its
failed saves and deletes shown where the admin can see them after the
PR #156 review, the same fix applied to the Suppliers page and the two
pages' supplier messages and auto-dismiss shared after the re-review),
recorded in `ai/usage-log.md`;
and user-service OTP email sending (issue #88, PR
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
`ai/usage-log.md`.

**Verification:** all AI-assisted output is reviewed by the team through
pull requests before merging.
