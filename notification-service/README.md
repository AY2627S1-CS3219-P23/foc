<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-18 – 2026-09-21.
  Scope: wrote and maintained this README alongside the service
  implementation (issues #61–#69); all design and implementation
  decisions were made by the team (recorded in
  docs/notification-service.md). Restructured 2026-09-21 from an
  issue-by-issue changelog into topic sections; the history lives in
  the GitHub issues and git log.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# Notification Service

Consumes request events from the broker, stores one notification per
affected user, and pushes them to the browser in real time over STOMP.
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

Health check: `GET http://localhost:${NOTIFICATION_SERVICE_PORT}/actuator/health`.

## Configuration

| Env var | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | — (required) | HS256 secret for verifying the STOMP `CONNECT` JWT |
| `NOTIFICATION_WS_ALLOWED_ORIGINS` | `http://localhost:*` | Allowed WebSocket handshake origins for `/ws` |
| `NOTIFICATION_RETRY_TTL_MS` | 10 000 | Retry backoff (baked into the retry queue at declaration) |
| `NOTIFICATION_RETRY_MAX_ATTEMPTS` | 3 | Total delivery attempts before dead-lettering |
| `NOTIF_RETENTION_DAYS` | 30 | Notification retention window |
| `NOTIF_PURGE_CRON` | daily 03:00 | Purge job schedule |

Failed events land in the dead-letter queue — inspect and re-publish
via the RabbitMQ management UI (`RABBITMQ_MANAGEMENT_PORT`).

## Troubleshooting

Dev broker/DB volumes created before the typed-contracts refactor, the
retry/DLQ topology (#65), or the order→request rename carry
incompatible declarations (`PRECONDITION_FAILED` on startup) or
removed NOT NULL columns that reject inserts. Reset both volumes once:

```sh
docker compose down rabbitmq notification-db
docker volume rm foc_rabbitmq-data foc_notification-db-data
docker compose up -d rabbitmq notification-db
```

## Status

Event pipeline, retry/dead-lettering, STOMP push gateway, and the
retention purge job are live. The REST API (list notifications, mark
read/unread) is pending
[issue #66](https://github.com/AY2627S1-CS3219-P23/foc/issues/66); the
frontend STOMP client waits on the SPA.
