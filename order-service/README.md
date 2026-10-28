<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-10.
  Scope: service README written while scaffolding the Spring Boot
  skeleton. 2026-10-11: status paragraph updated for the Requests
  entity/repository skeleton, then for the state machine skeleton.
  2026-10-12: status paragraph updated for RequestsService.
  2026-10-13: manual testing section for order-event.ps1; endpoints
  section for the RequestsController mappings; order-curl.ps1 section.
  2026-10-15: status paragraph and a "Saga with the Credit Service"
  section for PENDING/REJECTED, the outbox and the reply listener.
  2026-10-16: the ARRIVED state and /arrive.
  2026-10-17: request.completed on COMPLETE; request.cancelled and
  request.expired on CANCEL and EXPIRE. Same day: section on testing
  without the Credit and User Services (credit-stub.ps1, stub-users.psd1).
  2026-10-18: 409 when the requester accepts their own request; then
  bearer-token auth and the ownership rules in the endpoints section;
  then /create's 400 field checks;
  order-e2e.ps1 section. Then request.accepted / request.collected and
  the expiry and auto-complete timers. Then the read endpoints, and their
  filters and sort; past-deadline requests hidden from the open list.
  Reviewed by: Ko-Khan (via pull request).
  2026-10-22, Claude Code (Opus 5.5), issue #238: the Schema section
  (Flyway, the one-off order-db reset) and the tests on Testcontainers
  Postgres. Author review: Ryan Ang, pending pull request review.
  2026-10-23, Claude Code (Opus 5.5), PR #244 review: the reset now
  recreates every volume, since Credit's reservations are keyed by
  order ids; V1 gains the outbox index. Author review: Ryan Ang,
  pending pull request review.
  2026-10-23, Claude Code (Opus 5.5), issue #240: Flyway V2, closeReason
  in the reads, and the admin endpoints (team decisions DSP-1, DSP-5,
  DSP-10, DSP-17). Author review: Ryan Ang, pending pull request review.
  2026-10-26, Claude Code (Opus 5.5), issue #239: Flyway V3, the dispute
  states, /dispute, /resolve, the sweep's escalation and the dispute
  object in the reads (team decisions DSP-2, DSP-5 to DSP-8, and the
  #239 answers in docs/disputes.md). Author review: Ryan Ang, pending
  pull request review.
-->

# Order Service

Will own request creation, listing, acceptance, courier updates,
completion and expiry.

Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned) · RabbitMQ
(shared broker).

Scaffold only for now — one actuator health endpoint, database and
broker connection wired, and the `Requests` entity (table `requests`)
with its repository, the request state machine
(`OrderStateMachine`, a transition table over `OrderStates` /
`OrderEvents`), `RequestsService`, which applies those transitions
to stored requests, the HTTP endpoints below, and the saga with the
Credit Service (next section); no authentication or input validation
yet.

## Schema

Flyway owns the schema (`src/main/resources/db/migration`), as in
credit-service; Hibernate only validates the entities against it at
startup (`ddl-auto: validate`). A schema change is a new `V<n>__*.sql`
file; an applied migration is never edited.

- **Flyway V1** (`V1__baseline.sql`, issue #238): the `requests` and
  `outbox_event` tables as `ddl-auto: update` built them, with the
  status CHECK (`requests_status_check`) listing every `OrderStates`
  name, `payload` as `text` (as in credit-service), and the partial
  index `idx_outbox_event_unsent` for the publisher's poll. A new state
  needs a migration that replaces that CHECK
  (`databaseAcceptsEveryOrderState` fails until it does).
- **Flyway V2** (`V2__close_reason_outbox_request_id.sql`, issue #240):
  `requests.close_reason` (nullable text, why a request ended without
  completing, DSP-1) with the CHECK `requests_close_reason_check` over
  the `CloseReason` names (`databaseAcceptsEveryCloseReason` fails until
  a new reason has a migration); `outbox_event.request_id` (nullable
  bigint, indexed, no foreign key, DSP-17), back-filled from each
  existing row's payload `correlationId`.
- **Flyway V3** (`V3__disputes.sql`, issue #239): `DISPUTED` and
  `RESOLVED` added to `requests_status_check`; the `request_dispute`
  table (DSP-2), one row per disputed request, `request_id` a foreign
  key to `requests`. `raised_by` is null when the sweep escalated it.
  Named CHECKs cover the category, that a hand-raised dispute names who
  raised it, that the resolution fields are set together, and that the
  shares are non-negative.

`close_reason` is set when a request ends abnormally: `/cancel` →
`REQUESTER_CANCELLED`, the pending timeout → `RESERVATION_TIMEOUT`, the
expiry sweep → `DEADLINE_PASSED`, and a Credit rejection → the reply's
`reasonCode` mapped in `CreditReplyHandler` (`INSUFFICIENT_CREDITS` →
`INSUFFICIENT_CREDITS`, `INVALID_AMOUNT` → `INVALID_REWARD`; a null or
unknown code leaves it null). `/resolve` sets `DISPUTE_RESOLVED`.

**If the service fails to start after pulling V1** (Flyway reports a
non-empty schema with no history table, or Hibernate validation fails):
your `order-db` volume was built by `ddl-auto: update` before Flyway.
It held no real data, so recreate it once, **together with the other
services' volumes**:

```sh
docker compose down -v
docker compose up -d
```

Resetting `order-db` alone is not enough. A fresh `order-db` numbers
requests from 1 again, but `credit-db` still holds the old run's
reservations keyed by those ids, and Credit treats a repeated id as the
same request: the new request either gets no reply (cancelled by the
PENDING sweep after 30 s) or is confirmed against the old run's hold.
`down -v` also clears the demo accounts; they are seeded again on start
when `USER_SEED_DEMO=true` is set, as
[`docs/postman/README.md`](../docs/postman/README.md) describes.

## Saga with the Credit Service

The team's design is in [`docs/credit-service.md`](../docs/credit-service.md)
(D7–D8, "The saga").

- `/create` stores the request as **PENDING** and writes
  `request.submitted` to the outbox. A PENDING request cannot be
  accepted, cancelled or expired.
- `credit.reserved` moves it to **CREATED** (`request.created`);
  `credit.reservation-rejected` moves it to **REJECTED**, which is final
  (`request.rejected`, with the Credit Service's reason).
- A request still PENDING after `ORDER_PENDING_TIMEOUT_MS` (30 s) is
  cancelled by a sweep every `ORDER_PENDING_SWEEP_INTERVAL_MS` (15 s)
  and `request.cancelled` is written, so a late reservation is
  released. A reply for a request that is no longer PENDING is ignored.
- Besides these four transitions, ARRIVE publishes
  `request.courier-arrived` (tells the requester the courier is at the
  drop-off) and COMPLETE publishes `request.completed`, on which the
  Credit Service transfers the held reward to the courier (`parties` =
  requester and courier). COMPLETE is only allowed from ARRIVED.
  CANCEL publishes `request.cancelled` and EXPIRE `request.expired`,
  on which the Credit Service releases the held reward to the requester
  (`parties` = requester, plus the courier on an expiry after
  acceptance). ACCEPT and COLLECT publish `request.accepted` and
  `request.collected` (`parties` = requester and courier). DROP publishes
  `request.dropped` (`parties` = requester), so the requester is told;
  the reward stays held for the next courier.
- **Timers** (every `ORDER_EXPIRY_SWEEP_INTERVAL_MS` /
  `ORDER_AUTO_COMPLETE_SWEEP_INTERVAL_MS`, default 5 min): a CREATED or
  ACCEPTED request past its deadline is EXPIRED (F6.1); a request
  ARRIVED for `ORDER_AUTO_COMPLETE_AFTER_MS` (default 1 h) without the
  requester's confirmation is COMPLETED for them, with the same
  `request.completed` (F7.2). The expiry sweep also escalates a request
  still COLLECTED `ORDER_DISPUTE_ESCALATION_GRACE_MS` (default 1 h)
  after its deadline to DISPUTED, as `NOT_DELIVERED` with the note
  "auto-escalated: not delivered by deadline", publishing
  `request.disputed` (DSP-6). A DISPUTED request is never
  auto-completed (DSP-14).
- **Disputes:** DISPUTE (ARRIVED or COLLECTED → DISPUTED) and ESCALATE
  publish `request.disputed`; RESOLVE (DISPUTED → RESOLVED) publishes
  `request.dispute-resolved`, on which the Credit Service divides the
  held reward. Both have `parties` = requester and courier (DSP-9).
- **Publishing:** a transactional outbox (`outbox_event` table), sent to
  the `request-events` exchange by a poller with publisher confirms —
  a copy of credit-service's (D10).
- **Consuming:** queue `order-service.credit-events` on `credit-events`,
  bound to the two replies, with `.retry` (2 s, 5 attempts:
  `ORDER_RETRY_*`) and `.dlq` queues — credit-service's D11 layout. A
  reply naming no known request goes straight to the DLQ.
- Event fields: `pickupLocation` = supplier name, `note` = item,
  `reward` = credit amount, ids as decimal strings, `correlationId` =
  the request id, `parties` = the requester (plus the courier where
  noted above).

Without the Credit Service running, a new request stays PENDING and
is cancelled after 30 s. To move one on by hand, use
`./order-event.ps1 reserve <id>` (or `reject`, `timeout`) — the
harness runs with the broker off, so the events it writes stay unsent
in the outbox.

## Endpoints

All are `POST` with a JSON body and need a bearer token (the platform
JWT from user-service's login, verified with the shared `JWT_SECRET`);
the caller is the token's user. They answer `{"message": "..."}`: 200
on success; 409 if the event is not allowed from the request's current
state (including a courier action on a still-PENDING request), the
requester tries to accept their own request, or `/accept` comes after
the deadline (before the expiry sweep has run); 404 if no request has that
id **or the step is not the caller's** (the same answer, so a stranger
cannot tell the request exists). A missing or invalid token is a 401
problem+json. `/create` answers 400 listing every problem at once:
missing fields (all six body fields are required; blank text counts as
missing), `creditAmount` below 1, or a `deadline` not in the future —
e.g. `{"message": "Missing required fields: item, deadline; creditAmount
must be at least 1"}`.

| Path | Body | Who may call it |
| --- | --- | --- |
| `/create` | `supplierId`, `supplierName`, `dropoffLocation`, `item`, `creditAmount`, `deadline` (ISO-8601 instant) | anyone; the caller becomes the requester |
| `/accept` | `requestId` | anyone but the requester; the caller becomes the courier |
| `/collect` | `requestId` | the assigned courier |
| `/arrive` | `requestId` | the assigned courier |
| `/drop` | `requestId` | the assigned courier |
| `/cancel` | `requestId` | the requester |
| `/complete` | `requestId` | the requester |
| `/dispute` | `requestId`, `category`, `note` | the requester |
| `/resolve` | `requestId`, `courierAmount`, `requesterAmount`, `note` | ADMIN / OWNER who is not the requester or the courier; anyone else 403 |
| `/retry-settlement` | `requestId` | ADMIN / OWNER; anyone else 403 |

`/dispute` (DSP-5, DSP-7) needs `category`: `NOT_DELIVERED`,
`WRONG_ITEM`, `DAMAGED` or `OTHER`. `note` is optional, at most 500
characters, and required for `OTHER`. Either is a 400 otherwise. An
ARRIVED request can be disputed until `arrivedAt` + the auto-complete
window (409 "The dispute window closed at ..." after it, even before
the sweep has run). A COLLECTED request can be disputed once its
deadline has passed (409 before). A second dispute is a 409.

`/resolve` (DSP-5, DSP-8) divides the reward: both amounts 0 or more and
adding up to the reward, else 400. `note` is optional, at most 500
characters. 409 if the request is not DISPUTED or the admin is a party
to it.

`/retry-settlement` (DSP-10) makes the request's newest settlement event
(`request.completed`, `request.cancelled`, `request.expired` or
`request.dispute-resolved`) unsent again, keeping its event id, so the
outbox publisher sends the same message once more and consumers
deduplicate it. 409 if the request is still active (`is not finished`)
or finished without such an event, e.g. REJECTED (`has no settlement
event`); 404 for an unknown id.

### Reads

All need a bearer token. Lists are newest first and paged like the
supplier listing: `page` (from 0, default 0) and `size` (1-100, default
20), answered as `{"content": [...], "page", "size", "totalElements",
"totalPages"}`, each item trimmed to `id`, `status`, `closeReason`
(null unless the request ended without completing), `supplierName`,
`dropoffLocation`, `item`, `creditAmount`, `deadline`, `createdAt`, and
`dispute`. A bad `state`, `role`, `page` or `size` is a 400 with a
`message`.

`dispute` (on the lists and the full request) is null unless the request
was disputed: `{category, disputeNote, raisedAt, autoEscalated,
resolvedAt, resolvedBy, courierAmount, requesterAmount, resolutionNote}`,
the resolution fields null until it is resolved. A page's disputes are
loaded in one query.

Every list also takes these optional filters, combined with AND:

| Parameter | Matches |
| --- | --- |
| `supplierId` | requests from that supplier |
| `minCredit` / `maxCredit` | reward at least / at most (both inclusive, each at least 1) |
| `deadlineAfter` / `deadlineBefore` | deadline at or after / before (ISO-8601 instant with an offset, e.g. `Z`; write `+08:00` as `%2B08:00`) |
| `createdFrom` / `createdTo` | created at or after / before (same format) |
| `sort` | `createdAt` (newest first, the default) or `deadline` (nearest first) |

A range whose lower bound is not below its upper (`minCredit` above
`maxCredit`, `deadlineAfter` not before `deadlineBefore`, ...), an unknown
`sort`, or a value of the wrong type is a 400 naming the parameter.

| Path | Returns | Who may call it |
| --- | --- | --- |
| `GET /requests?state=CREATED&page=&size=` | open errands for couriers (F2.1): CREATED requests whose deadline is still ahead; `state` may only be `CREATED` (the default) | any logged-in user |
| `GET /requests/{id}` | the request in full (every column, incl. `requesterId`, `courierId`, `closeReason` and the step timestamps) | its requester, its assigned courier, or ADMIN / OWNER; anyone else 404 |
| `GET /requests/mine?role=requester&state=` | the caller's requests as requester, optionally in one state | the caller |
| `GET /requests/mine?role=courier&state=` | the caller's assigned deliveries | the caller |
| `GET /users/{id}/requests?state=` | that user's requests as requester or courier (admin view) | ADMIN / OWNER; anyone else 403 |
| `GET /requests/admin?state=&closeReason=` | every user's requests; `closeReason` takes one or more `CloseReason` names, comma-separated like `state` (the dispute queue is `state=DISPUTED`) | ADMIN / OWNER; anyone else 403 |
| `POST /requests/admin/statuses` `{"ids": [42, 99]}` | each id's current state, `{"42": "COMPLETED", "99": null}`: an unknown id is `null`, a repeated one answered once, `[]` gives `{}`; more than 100 ids or a non-numeric id is a 400 | ADMIN / OWNER; anyone else 403 |

## Run

```sh
# One-time (and after any foc-contracts change): install the shared
# contracts library into your local Maven repository
cd ../foc-contracts && ./mvnw install && cd ../order-service

# Tests (Postgres in a Testcontainers container, built by Flyway; need
# Docker, and are skipped without it. No RabbitMQ needed)
./mvnw test

# Run locally (needs PostgreSQL; health reports DOWN without RabbitMQ)
./mvnw spring-boot:run
curl localhost:8080/actuator/health

# Full stack (from the repo root; copy .env.example to .env first and
# set ORDER_DB_PASSWORD and RABBITMQ_PASSWORD)
docker compose up --build order-service
# No host port since the gateway cutover (issue #151): the service is
# reached through the gateway at /api/order/** (docs/gateway.md).
```

## Manual testing over HTTP

`order-curl.ps1` calls one endpoint of a running order-service with
curl and prints the HTTP status and response body. `create` sends a
sample request.

```powershell
docker compose up --build -d order-service   # from the repo root
./order-curl.ps1 create                      # -> Request 1 created (PENDING)
# wait for credit-service to reserve the reward (request becomes CREATED)
./order-curl.ps1 accept 1 -Courier bob
./order-curl.ps1 collect 1
./order-curl.ps1 arrive 1
./order-curl.ps1 complete 1
```

## End-to-end check with real accounts

`order-e2e.ps1` logs in two verified user-service accounts through the
gateway and walks requests through the lifecycle with their tokens,
trying each step as the wrong person first: it prints PASS / FAIL per
step (11 checks). Passwords are prompted for, hidden. The reward is
reserved by credit-service if it does so within 5 s, otherwise by
`credit-stub.ps1`.

```powershell
docker compose up --build -d order-service gateway user-service   # from the repo root
./order-e2e.ps1 -Requester <username or email> -Courier <another account>
```

## Testing without the Credit and User Services

`credit-stub.ps1` stands in for the Credit Service: it sends the reply
you choose for one request to the `credit-events` exchange (through the
RabbitMQ management API), with the requester and reward read from
`order-db`. `stub-users.psd1` is a fixed user list (alice = 1, bob = 2,
carol = 3, dave = 4) standing in for the User Service; `order-curl.ps1`
takes those names, or ids, for `-Requester` and `-Courier`.

```powershell
docker compose stop credit-service           # from the repo root, or it answers first
./order-curl.ps1 create -Requester carol     # -> Request 1 created (PENDING)
./credit-stub.ps1 reserve 1                  # -> request 1 is now CREATED
./order-curl.ps1 accept 1 -Courier dave
./credit-stub.ps1 reject 2 -Reason 'insufficient available credits'   # -> REJECTED
```

Needs `rabbitmq`, `order-db`, `order-service` and `gateway` running.

## Manual testing against order-db

`order-event.ps1` fires order events at one request in the compose
`order-db` by running `ManualRequestsHarness` in a Maven container (no
local JDK needed; needs Docker and a root `.env`). It prints the stored
row after each event; inspect the `requests` table with any client on
`localhost:${ORDER_DB_HOST_PORT:-5437}`.

```powershell
./order-event.ps1 create               # inserts a sample request (PENDING), prints its id
./order-event.ps1 reserve 1            # PENDING -> CREATED, standing in for credit.reserved
./order-event.ps1 accept 1 -Courier 7  # fires accept at request 1
./order-event.ps1 collect,arrive,complete 1   # several events in order
```

Run it with no event to start a session that stays up, so each event
takes effect immediately instead of restarting the container:

```text
./order-event.ps1          # or ./order-event.ps1 -Id 1 to continue request 1
create
reserve
accept
courier 9                  # courier id used by later accepts
id 3                       # switch to request 3
collect,complete
quit
```

A rejected event is reported and the session carries on.
