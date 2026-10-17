<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-18 – 2026-09-21.
  Scope: wrote and maintained this README alongside the service
  implementation (issues #61–#69); all design and implementation
  decisions were made by the team (recorded in
  docs/notification-service.md). Restructured 2026-09-21 from an
  issue-by-issue changelog into topic sections; the history lives in
  the GitHub issues and git log.
  2026-10-11, Claude Code (Fable 5), PR #176 Copilot review: the health
  check URL updated for the gateway cutover (#151) — the host port is
  unpublished; the gateway prefix (or the in-network name) reaches it.
  2026-10-12, Claude Code (Fable 5), issue #67: REDIS_HOST/REDIS_PORT
  rows added for the D23 denylist read at STOMP CONNECT (author
  decision via options Q&A).
  2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): the
  Redis row names the per-user reset cutoff the check also reads.
  2026-10-17, Claude Code (Fable 5), issue #66: REST API section added,
  WEB_ALLOWED_ORIGIN row added, JWT_SECRET row widened to the REST
  routes, Status brought current.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Notification Service

Consumes request events from the broker, stores one notification per
affected user, pushes them to the browser in real time over STOMP, and
serves the Notification Center's REST API (list, unread count, mark
read/unread).
Spring Boot 4 · Java 21 · Maven · PostgreSQL (service-owned).

Design and decisions:
[`docs/notification-service.md`](../docs/notification-service.md) ·
diagram: [`docs/notification-service.mmd`](../docs/notification-service.mmd) ·
event contracts: [`foc-contracts/README.md`](../foc-contracts/README.md#event-conventions).

## Run

```sh
# One-time: install the shared contracts library to the local Maven
# repository — needed before the first build/test
(cd ../foc-contracts && ./mvnw install)

# Tests (in-memory H2, no broker needed; the one Testcontainers test
# auto-skips when Docker is unavailable)
./mvnw test

# Full stack (from the repo root; copy .env.example to .env first and
# set NOTIFICATION_DB_PASSWORD and RABBITMQ_PASSWORD)
docker compose up --build notification-service
```

Health check: `GET http://localhost:${GATEWAY_PORT:-8090}/api/notification/actuator/health`
— since the gateway cutover (#151) the service has no published host
port of its own (`docs/gateway.md`); inside the compose network it
stays `notification-service:8080`.

## Configuration

| Env var | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | — (required) | HS256 secret for verifying the STOMP `CONNECT` JWT and the REST routes' bearer tokens |
| `NOTIFICATION_WS_ALLOWED_ORIGINS` | `http://localhost:*` | Allowed WebSocket handshake origins for `/ws` |
| `WEB_ALLOWED_ORIGIN` | `http://localhost:5173` | SPA origin the REST API's CORS headers allow (the gateway passes them through unchanged) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / 6379 | Platform Redis for the denylist reads at STOMP `CONNECT` (D23) — a revoked jti, or a token issued before the account's password-reset cutoff, rejects the CONNECT; unreachable Redis fails open |
| `NOTIFICATION_RETRY_TTL_MS` | 10 000 | Retry backoff (baked into the retry queue at declaration) |
| `NOTIFICATION_RETRY_MAX_ATTEMPTS` | 3 | Total delivery attempts before dead-lettering |
| `NOTIF_RETENTION_DAYS` | 30 | Notification retention window |
| `NOTIF_PURGE_CRON` | daily 03:00 | Purge job schedule |

Failed events land in the dead-letter queue — inspect and re-publish
via the RabbitMQ management UI (`RABBITMQ_MANAGEMENT_PORT`).

## REST API

JWT-authenticated routes under the gateway's `/api/notification/`
prefix, every one scoped to the token's user — the full contract
(shapes, bounds, errors, decisions D24–D28) is in the
[design doc's "REST API contract"](../docs/notification-service.md#rest-api-contract-d24d28):

- `GET /notifications/me?page=&size=[&unread=true]` — paged list,
  newest stored first
- `GET /notifications/me/unread-count` — `{"count": n}` for the bell
- `PATCH /notifications/me/{id}` `{"read": true|false}` — 204 / 404
- `POST /notifications/me/read-all` — 204

## Troubleshooting

Dev volumes created before the typed-contracts refactor or the
retry/DLQ topology (#65) fail at startup: the old fanout exchange and
the pre-#65 work queue carry incompatible declarations
(`PRECONDITION_FAILED`), and a DB schema with the removed NOT NULL
`entity_type`/`entity_id` columns rejects inserts (`ddl-auto: update`
never drops columns). Reset both volumes once:

```sh
docker compose down rabbitmq notification-db
docker volume rm foc_rabbitmq-data foc_notification-db-data
docker compose up -d rabbitmq notification-db
```

Separately, after the order→request rename the old `order-events`
exchange and `notification-service.order-events*` queues are harmless
orphans — the new names declare cleanly, so no reset is needed; delete
the orphans in the management UI (any messages still parked in them
are stranded).

## Status

Event pipeline, retry/dead-lettering, STOMP push gateway, retention
purge job, and the REST API
([issue #66](https://github.com/AY2627S1-CS3219-P23/foc/issues/66)) are
live. The SPA's Notification Center (the web half of #66) consumes the
REST API; the frontend STOMP client shipped with #67.
