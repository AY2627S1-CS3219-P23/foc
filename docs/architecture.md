<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-14; revised 2026-09-15.
  Scope: transcribed the team-decided architecture (AGENTS.md service
  boundaries + D1 requirements interactions) into this diagram/document,
  and re-aligned relationships and requirement references with the
  latest D1 backlog. No architecture or design decisions were made by
  the tool; open decisions are marked "TBD" for the team.
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
APIs. The order-event workflow is **event-driven** through the message
broker (course milestone M6), decoupling the Notification Service from
the services that produce events.

**Scope:** committed functional and non-functional requirements (F/NFR)
only. The selected nice-to-haves (N1 ratings, N2 order history, N3
disputes, N4 centralized logging, N5 cloud deployment/CI-CD/Kubernetes)
are intentionally excluded from this runtime diagram; N5 concerns
deployment, not runtime structure.

**Diagram source:** [`architecture.mmd`](architecture.mmd) — GitHub
renders it directly in the file view; the legend and dependency table
below accompany it.

## Legend (notation)

| Notation | Meaning |
| --- | --- |
| Rectangle | A deployable service — its own Spring Boot app in its own Docker container (M7), one per top-level folder |
| Rounded/stadium shape | People using the system through a browser |
| Parallelogram | Message broker (infrastructure component, technology TBD) |
| Double-bordered rectangle | External system outside the platform (the email provider) |
| Cylinder | A database owned by **exactly one** service; no service reads another service's database |
| Thin arrow `-->` | **Synchronous** REST/JSON call over HTTP; the arrow points from caller to callee (request direction; the response returns along the same call) |
| Thin double arrow `<-->` | **Bidirectional real-time channel** (the requester–courier chat); messages flow both ways, transport TBD |
| Thick arrow `==>` | **Asynchronous** event/message flow; the arrow points in the direction the data (event/email) travels |
| Plain line `---` | Database access via Spring Data JPA (only ever from the owning service) |
| "scheduler: …" inside a service node | A timer-driven behavior internal to that service (no cross-service call involved) |
| `F… / NFR… / M…` on a line | The D1 requirement or course-mandated item that this relationship implements |
| **TBD** | A decision the team has not made yet (kept open on purpose) |

**Authentication note:** every Web → service call carries a JWT issued by
the User Service on login (User F7.1); each service rejects requests the
caller's role does not permit (User F6.1.5). Token-validation mechanics
(shared `JWT_SECRET` per `.env.example`) are configuration, not a runtime
call, so no arrow is drawn for it.

**Abbreviations:** REST = HTTP/JSON web APIs · JWT = JSON Web Token ·
OTP = one-time password · JPA = Java Persistence API (Spring Data JPA) ·
SPA = single-page application · CRUD = create/read/update/delete ·
TBD = to be decided.

## Dependency summary (traceable to the D1 backlog)

| From → To | Style | Purpose | Requirement |
| --- | --- | --- | --- |
| Web → User Service | sync REST | sign-up, login/logout, OTP flows, account update/delete/recover, roles, admin user management, profiles; JWT issued here | User F1–F8 |
| Web → Supplier Service | sync REST | browse/search/filter vendors; admin vendor CRUD | Supplier F1–F2 |
| Web → Order Service | sync REST | create/list/accept/pickup–dropoff/cancel/complete requests | Order F1–F7, F9, F10 |
| Web ↔ Order Service | real-time channel (transport TBD) | requester–courier chat: text + photo media for verification | Order F8.1, F8.1.1 |
| Web → Credit Service | sync REST | available + reserved balances, filtered transaction history; admin credit adjustment | Credit F4, F6 |
| Web → Notification Service | sync REST | list recent notifications in-app, mark read/unread, retention window | Notif F3.1, F3.2, F3.4 |
| Notification Service → Web | async (transport TBD) | order-status updates to each party within 5 seconds | Notif F1.2; Order NFR1.1–1.2 |
| User Service → Email Provider | async email (provider TBD) | OTP for sign-up verification, email-change confirmation, password reset | User F1.1.3, F2.1.1, F2.1.3, F4.2 |
| User Service → Credit Service | sync REST | allocate 5 starting credits (reserved balance 0) on sign-up | Credit F1.1 |
| Order Service → Supplier Service | sync REST | validate pickup location is a known supplier/landmark | Order F1.1.1 |
| Order Service → Credit Service | sync REST | reserve on create, release on cancel/expiry, atomic transfer on completion; duplicate transfer requests for the same order processed once | Credit F2.1, F2.1.1, F3.1, F5.1, NFR3.2.1; Order F13 |
| Order Service → Broker → Notification Service | **async events** | event on every order-status change (created/accepted/collected/completed/cancelled/expired); at-least-once, persisted across restarts; consumer deduplicates, retries, records exhausted retries, discards stale out-of-order events; delivery failure never affects the producing operation | Order F11.1; Notif F1.1, F1.4, F2.1–F2.4, NFR1.1–1.2; **M6** |

Timer-driven behaviors stay **inside** the owning service (no arrow):
order expiry with refund via the existing Order → Credit release call
(Order F6.1, F6.1.1), auto-completion after 1 hour (Order F7.2),
deleted-account purge after 30 days (User F3.1.3), and monthly credit
redistribution (Credit F7).

## Decisions still open (team, not AI)

- **Message broker technology** for the M6 async workflow.
- **Database engine(s)** — one database per service is decided; engines are not.
- **Client update transport** for Notification → Web (how order-status
  updates reach the browser).
- **Chat transport** for the Web ↔ Order Service real-time channel
  (Order F8.1).
- **Email provider** for OTP delivery (User F1.1.3).

An exported image of this diagram (SVG/PNG) is kept with the D1
document; this Mermaid source is the version of record.
