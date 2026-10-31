<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
  Scope: service README written while building chat-service, following
  rating-service's README.
  2026-10-31: the read and unread-queue rows.
  Author review: Ko-Khan, pending pull request review.
-->

# chat-service

The FoC chat service (nice-to-have N6): the requester and the assigned
courier chat while a request is accepted, collected or arrived; admins
read the history. Text and images (JPEG/PNG/WebP, 2 MB), kept 30 days.
order-service opens and closes chats; the browser talks to this service
over REST and its own STOMP WebSocket. Decisions and interface:
[`docs/chat-service.md`](../docs/chat-service.md).

## Running

Through the full stack (recommended):

```sh
docker compose up        # from the repo root
```

It needs `CHAT_DB_PASSWORD` and `CHAT_INTERNAL_TOKEN` in `.env` (see
`.env.example`). The service has no published host port; it is reachable
only through the API gateway:

```sh
curl http://localhost:8090/api/chat/actuator/health
```

On its own, against a local chat-db:

```sh
./mvnw spring-boot:run
```

## Tests

```sh
./mvnw test
```

Every test that touches the database runs Flyway on a Testcontainers
Postgres (the schema uses a partial unique index and `bytea`), so Docker
must be running; without it those tests are skipped.

## Endpoints

| Path | Who | What |
| --- | --- | --- |
| `GET /chats?requestId=` | bearer token | the request's chats the caller may see |
| `GET /chats/{chatId}/messages?before=&size=` | parties of an open chat; admins | history, newest first |
| `GET /chats/{chatId}/messages/{messageId}/image` | same | the image bytes |
| `/ws` (STOMP, SockJS) | bearer token in CONNECT | subscribe `/topic/chats/{chatId}`, send `/app/chats/{chatId}/messages` |
| `POST /chats/{chatId}/read` | parties of an open chat | resets the caller's unread count |
| `/user/queue/chat` (STOMP) | the caller | their unread pushes |
| `POST /internal/chats/open`, `/close` | `X-Internal-Token` | order-service's calls |
| `GET /actuator/health` | open | health |
