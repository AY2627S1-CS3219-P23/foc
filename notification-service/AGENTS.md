<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21.
  Scope: wrote this agent guide from the existing code and design doc
  (docs/notification-service.md); it records team-made decisions and
  invariants, and makes no design decisions of its own.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-17, Claude Code (Fable 5), issue #66: package map, invariants
  and test list updated for the REST API (controller/config/dto
  packages, security chain, D24-D29); stale D1-D22 range fixed.
  2026-10-17, Claude Code (Fable 5), PR #198 Codex review: the config/
  row records the CORS registration's /notifications/** scope.
-->

# notification-service/ — Agent Guide

Java 21 + Spring Boot 4 service that consumes request events from
RabbitMQ, stores notifications, and pushes them over STOMP. Read the
root `AGENTS.md` first (architecture, course constraints, AI usage
policy). The design authority is
[`docs/notification-service.md`](../docs/notification-service.md) —
every architectural choice is a numbered decision (D1–D29) there; cite
the decision ID when touching related code.

## Package map (`src/main/java/foc/notification/`)

| Package | Contents |
| --- | --- |
| `entity/` | `Notification` (one row per recipient, read/unread flag), `ProcessedEvent` (dedupe inbox) |
| `repository/` | Spring Data JPA repositories for both entities |
| `service/` | `EventProcessor` (port), `IdempotentEventProcessor` (dedupe + insert + publishes `NotificationStoredEvent`), `NotificationDto`, `RetentionPurgeScheduler` |
| `messaging/rabbitmq/` | The only package allowed to touch broker types: `DomainEventsListener` (manual ack, retry republish), `DomainEventMessageConverter` (dispatch on body `eventType` via `EventTypeRegistry`), `RabbitMqTopology` + `RabbitMqTopologyInitializer` (startup declaration), `RabbitMqMessageConverterConfig` |
| `messaging/stomp/` | `WebSocketStompConfig` (`/ws`, SockJS, heartbeats — also owns the app's sole `TaskScheduler`), `JwtChannelInterceptor` (CONNECT auth), `NotificationPushListener` (`AFTER_COMMIT` push), `StompPrincipal` |
| `security/` | `JwtVerifier` (HS256, jjwt; fails startup on missing/short secret), `JwtAuthenticationFilter` (REST bearer → security context, empty authorities per D28; built inline by `SecurityConfig`, not a bean), `RestAuthEntryPoint`/`RestAccessDeniedHandler` (401/403 problem+json), `redis/RedisTokenDenylist` (D23 — STOMP CONNECT only, never the REST path, G4) |
| `config/` | `SecurityConfig` — stateless chain: `/error`, `/actuator/health` and the `/ws` SockJS paths open (the CONNECT frame authenticates, not the handshake), everything else bearer-authenticated; CORS from `WEB_ALLOWED_ORIGIN`, registered for `/notifications/**` only — never `/**`, which would put the REST origin policy in front of the WS handshake/SockJS paths (PR #198 review) |
| `controller/` | `NotificationController` (`/notifications/me` routes, D24–D27), `ApiExceptionHandler` (problem+json 400/404), the contract's exceptions |
| `dto/` | `PageResponse` (the platform page envelope — a copy, not an import, D15), `MarkReadRequest`, `UnreadCountResponse` |

## Invariants — do not break

- **Manual ack after DB commit** (NFR1.1): the listener acks only after
  the processor's transaction commits; a retry republish must be
  broker-confirmed before the original delivery is acked.
- **Idempotency by event ID** (D4/F2.1): processed-event insert and
  notification insert commit in one transaction.
- **Broker isolation** (D11): only `messaging.rabbitmq` may import
  `org.springframework.amqp` / `com.rabbitmq`. `ArchitectureTest`
  (ArchUnit) fails the build otherwise.
- **Dispatch on the body `eventType` only** (D18): no `__TypeId__` or
  other broker headers; `EventTypeRegistry` in `foc-contracts` is the
  single class↔identity source.
- **JWT verified at STOMP CONNECT** (issue #67): principal = token
  `sub` = platform user ID = `parties[]` entry = `recipientId`. The
  REST API verifies the same token per request (issue #66) but must
  **not** read the denylist — the gateway does that for HTTP (G4);
  `ArchitectureTest` confines Redis types to `security.redis`.
- **Every REST route is the caller's own data** (D24/D26): recipient
  scoping lives in the repository query's `WHERE`, never a Java-side
  compare, and a foreign ID answers the same 404 as a missing one.
- **No shared code across services except `foc-contracts`** (D15).
  Event contracts evolve additively; follow the
  [foc-contracts README's Event conventions](../foc-contracts/README.md#event-conventions)
  and the add-a-new-event checklist in
  [`foc-contracts/AGENTS.md`](../foc-contracts/AGENTS.md).
- Retry attempts are counted in the listener-stamped `x-retry-attempts`
  header — RabbitMQ 4 resets `x-death` on client republish; don't
  "simplify" back to `x-death`.

## Build & test

```sh
(cd ../foc-contracts && ./mvnw install)   # once, before first build
./mvnw test
```

Tests: `IdempotentEventProcessorTest`, `NotificationRepositoryTest`,
`RetentionPurgeSchedulerTest` (H2), `DomainEventContractTest` (fixture
tolerant-reader lock-in), `DomainEventMessageConverterTest`,
`JwtVerifierTest`, `SecurityConfigTest` (REST chain 401s + the
`/ws/info` stays-open canary for #67), `NotificationControllerTest`
(REST contract: order, paging 400s, ownership 404s, idempotent
writes), `StompPushIntegrationTest` (real STOMP session,
random port, no Docker), `RequestEventsRabbitMqIntegrationTest`
(Testcontainers — auto-skips without Docker), `ArchitectureTest`
(D11 boundary).

Schema is JPA-managed (`ddl-auto: update`) — it never drops columns;
stale dev volumes need the reset in the
[README's Troubleshooting section](README.md#troubleshooting).
