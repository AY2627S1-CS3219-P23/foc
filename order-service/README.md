<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-10.
  Scope: service README written while scaffolding the Spring Boot
  skeleton. 2026-10-11: status paragraph updated for the Requests
  entity/repository skeleton, then for the state machine skeleton.
  2026-10-12: status paragraph updated for RequestsService.
  2026-10-13: endpoints section for the RequestsController mappings.
  2026-10-15: status paragraph and a "Saga with the Credit Service"
  section for PENDING/REJECTED, the outbox and the reply listener.
  2026-10-16: the ARRIVED state and /arrive.
  2026-10-17: request.completed on COMPLETE; request.cancelled and
  request.expired on CANCEL and EXPIRE.
  2026-10-18: 409 when the requester accepts their own request; then
  bearer-token auth and the ownership rules in the endpoints section.
  Reviewed by: Ko-Khan (via pull request).
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
  acceptance). DROP publishes nothing, so the reward stays held; ACCEPT
  and COLLECT publish nothing yet. Nothing fires EXPIRE yet (no
  deadline sweep).
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
is cancelled after 30 s.

## Endpoints

All are `POST` with a JSON body and need a bearer token (the platform
JWT from user-service's login, verified with the shared `JWT_SECRET`);
the caller is the token's user. They answer `{"message": "..."}`: 200
on success; 409 if the event is not allowed from the request's current
state (including a courier action on a still-PENDING request) or the
requester tries to accept their own request; 404 if no request has that
id **or the step is not the caller's** (the same answer, so a stranger
cannot tell the request exists). A missing or invalid token is a 401
problem+json.

| Path | Body | Who may call it |
| --- | --- | --- |
| `/create` | `supplierId`, `supplierName`, `dropoffLocation`, `item`, `creditAmount`, `deadline` (ISO-8601 instant) | anyone; the caller becomes the requester |
| `/accept` | `requestId` | anyone but the requester; the caller becomes the courier |
| `/collect` | `requestId` | the assigned courier |
| `/arrive` | `requestId` | the assigned courier |
| `/drop` | `requestId` | the assigned courier |
| `/cancel` | `requestId` | the requester |
| `/complete` | `requestId` | the requester |

## Run

```sh
# One-time (and after any foc-contracts change): install the shared
# contracts library into your local Maven repository
cd ../foc-contracts && ./mvnw install && cd ../order-service

# Tests (in-memory H2, no PostgreSQL or RabbitMQ needed)
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
