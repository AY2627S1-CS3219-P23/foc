<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-14; revised 2026-09-15, 2026-09-18.
  Scope: transcribed the team-decided architecture (AGENTS.md service
  boundaries + D1 requirements interactions) into this diagram/document,
  and re-aligned relationships and requirement references with the
  latest D1 backlog (Template-7): chat and admin credit adjustment moved
  to nice-to-haves (out of the committed scope this diagram covers),
  Order F0/F8 renumbering, Credit F6 redistribution, Supplier caching,
  capacity/performance targets. Also on 2026-09-18: folded in the
  team's notification-design decisions (RabbitMQ broker, WebSocket/STOMP
  client push, PostgreSQL notification DB — decided by Leong Wei Zhi,
  recorded in docs/notification-service.md), replacing the corresponding
  TBD markers. No architecture or design decisions were made by the
  tool; remaining open decisions are marked "TBD" for the team.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Favours on Campus (FoC) — High-Level Service Architecture

**Diagram type:** free-form service-level (container-level) architecture
diagram. It shows every deployable component, the databases they own,
the external systems the platform depends on, and all runtime
relationships between them, traced back to the D1 requirements
(`F`/`NFR` references). All technology choices shown were decided by the
team; anything marked **TBD** ("to be decided") is a pending team
decision, deliberately left open.

**Architectural style:** distributed deployment × domain partitioning —
i.e. **microservices** (per the L4 categorization of architecture
styles). Each service owns exactly one business domain (users,
suppliers, orders, credits, notifications) and its own database
(database-per-service); services interact only through their published
APIs. The request-event workflow is **event-driven** through the message
broker (course milestone M6), decoupling the Notification Service from
the services that produce events.

**Scope:** committed functional and non-functional requirements (F/NFR)
only. The selected nice-to-haves (N1 ratings, N2 request history, N3
disputes, N4 centralized logging, N5 cloud deployment/CI-CD/Kubernetes,
N6 requester–courier chat, N7 admin dashboard incl. admin credit
adjustments) are intentionally excluded from this runtime diagram; N5
concerns deployment, not runtime structure. (In the latest backlog, chat
and admin credit adjustment were reclassified from committed
requirements to nice-to-haves N6/N7, so they no longer appear here.)

**Diagram source:** [`architecture.mmd`](architecture.mmd) — GitHub
renders it directly in the file view; the legend and dependency table
below accompany it.

## Legend (notation)

| Notation | Meaning |
| --- | --- |
| Rectangle | A deployable service — its own Spring Boot app in its own Docker container (M7), one per top-level folder |
| Rounded/stadium shape | People using the system through a browser |
| Parallelogram | Message broker (RabbitMQ) — shared platform infrastructure in its own container; the exchange is the publishing service's surface; each queue is dedicated to one consuming service (see [`notification-service.md`](notification-service.md)) |
| Double-bordered rectangle | External system outside the platform (the email provider) |
| Cylinder | A database owned by **exactly one** service; no service reads another service's database |
| Thin arrow `-->` | **Synchronous** REST/JSON call over HTTP; the arrow points from caller to callee (request direction; the response returns along the same call) |
| Thick arrow `==>` | **Asynchronous** event/message flow; the arrow points in the direction the data (event/email) travels |
| Plain line `---` | Database access via Spring Data JPA (only ever from the owning service) |
| "scheduler: …" inside a service node | A timer-driven behavior internal to that service (no cross-service call involved) |
| `F… / NFR… / M…` on a line | The D1 requirement or course-mandated item that this relationship implements |
| **TBD** | A decision the team has not made yet (kept open on purpose) |

**Authentication note:** every Web → service call carries a JWT issued by
the User Service on login (User F7.1); tokens expire after one hour and
logout invalidates the current token (User F7.1.1–F7.1.2). Each service
rejects requests the caller's role does not permit (User F6.1.5).
Token-validation mechanics (shared `JWT_SECRET` per `.env.example`) are
configuration, not a runtime call, so no arrow is drawn for it.

**Abbreviations:** REST = HTTP/JSON web APIs · JWT = JSON Web Token ·
OTP = one-time password · JPA = Java Persistence API (Spring Data JPA) ·
SPA = single-page application · CRUD = create/read/update/delete ·
TBD = to be decided.

## Dependency summary (traceable to the D1 backlog)

| From → To | Style | Purpose | Requirement |
| --- | --- | --- | --- |
| Web → User Service | sync REST | sign-up, login/logout, OTP flows, account update/delete/recover, roles (user/admin/owner), admin user management, profiles; JWT issued here | User F1–F8 |
| Web → Supplier Service | sync REST | browse/search/filter vendors; admin vendor CRUD | Supplier F1–F2 |
| Web → Order Service | sync REST | create/list/accept/collect–arrive/cancel/complete requests; re-release expired requests | Order F1–F8 (re-release: F6.4) |
| Web → Credit Service | sync REST | available + reserved balances (shown in the user's profile), filtered transaction history | Credit F4; User F8.1 |
| Web → Notification Service | sync REST | list recent notifications in-app, mark read/unread, retention window | Notif F3.1, F3.2, F3.4 |
| Notification Service → Web | async WebSocket (STOMP) push, per-user destinations | request state-change updates to requester and assigned courier within 5 seconds; the system's **only standing connection** — all other Web ↔ service traffic is stateless REST (see [`notification-service.md`](notification-service.md), "Connection topology") | Notif F1.2; Order NFR1.1–1.2 |
| User Service → Email Provider | async email (provider TBD) | OTP for sign-up verification, email-change confirmation, password reset | User F1.1.3, F2.1.1, F2.1.3, F4.2 |
| User Service → Credit Service | sync REST | allocate 5 starting credits (reserved balance 0) on sign-up | Credit F1.1 |
| Order Service → Supplier Service | sync REST | validate pickup location is a known supplier/landmark; fetch supplier locations for the 1 km acceptance-proximity check | Order F1.1.1, F8.1 |
| Order Service → Credit Service | sync REST | reserve on create, release on cancel/expiry, atomic transfer on completion; identical transfer requests for the same confirmation processed once | Credit F2.1, F2.1.1, F3.1, F5.1, NFR2.2, NFR2.2.1 |
| Order Service → Broker (RabbitMQ) → Notification Service | **async events** | event on every request state transition (created/accepted/collected/completed/cancelled/expired); at-least-once, persisted across restarts; consumer deduplicates, retries, records exhausted retries, discards stale out-of-order events; delivery failure never affects the producing operation; new event types need no publisher changes | Order F0.2; Notif F1.1, F1.3, F1.4, F2.1–F2.4, NFR1.1–1.2; **M6** |

Timer-driven behaviors stay **inside** the owning service (no arrow):
request expiry from the created or accepted state at the deadline, with
credits released via the existing Order → Credit release call (Order
F6.1–F6.2; Credit F2.1.1), auto-completion 1 hour after the courier
marks arrival (Order F7.2, which triggers the same Order → Credit
transfer as a manual confirmation), deleted-account purge on the 31st
day (User F3.1.3), and earned-credit expiry after 3 months into a common
pool with monthly redistribution (Credit F6.1–F6.1.2).

## Capacity and performance targets (NFRs)

These shape sizing and implementation rather than adding components:

- **User Service:** 60 000 accounts (User NFR2); account operations and
  permission checks within 2 s, excluding OTP email delivery (User
  NFR3); passwords hashed with SHA256 or equivalent (User NFR1.1);
  uniqueness checks complete before other asynchronous operations can
  access the records (User NFR4).
- **Supplier Service:** 100 000 suppliers (Supplier NFR2); listings
  within 5 s, with caching on frequently queried fields such as name
  and location (Supplier NFR1.1, NFR1.1.1).
- **Order Service:** courier listings reflect requests entering/leaving
  the created state within 5 s; every state change visible to requester
  and assigned courier within 5 s (Order NFR1.1–1.2) — via the
  broker → Notification → Web path.
- **Credit Service:** balances updated within 5 s of confirmed delivery
  (Credit NFR1.1); failed transfers revert to pre-transaction balances
  and duplicate payments are prevented (Credit NFR2).
- **Notification Service:** at-least-once event delivery, undelivered
  events persisted across restarts (Notif NFR1).
- **Web App:** responsive across mobile and wide-screen widths
  (UI NFR1).

## Decisions still open (team, not AI)

- **Database engines** for the User, Supplier, Order and Credit
  services — one database per service is decided; the Notification DB
  is decided (PostgreSQL), the rest are not.
- **Email provider** for OTP delivery (User F1.1.3).
- **Supplier caching mechanism** (Supplier NFR1.1.1) — in-process vs. a
  shared cache; drawn inside the Supplier Service until decided.

An exported image of this diagram (SVG/PNG) is kept with the D1
document; this Mermaid source is the version of record.
