<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21.
  Scope: wrote this agent guide from the existing code and design doc
  (docs/notification-service.md); it records team-made decisions and
  invariants, and makes no design decisions of its own.
  2026-10-17, issue #137: scheduler line, D4 invariant and test list
  updated for the processed_events retention sweep.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-17, Claude Code (Fable 5), issue #66: package map, invariants
  and test list updated for the REST API (controller/config/dto
  packages, security chain, D24-D29); stale D1-D22 range fixed.
  2026-10-17, Claude Code (Fable 5), PR #198 Codex review: the config/
  row records the CORS registration's /notifications/** scope.
  2026-11-08, Claude Code (Fable 5.1), issue #305: package map, invariants
  and test list for the D35 push fanout, the duplicate-key ack and the D36
  Flyway schema. Author review: Leong Wei Zhi, pending pull request review.
  Same day, PR #306 review: V2 converges legacy volumes. Author review:
  Leong Wei Zhi, pending pull request review.
  2026-11-08, Claude Code (Opus 5.5), Credit NFR1.1 (PR #313): the credit-change
  events (D37) in the summary line.
  Author review: Ryan Ang, pending pull request review.
-->

# notification-service/ — Agent Guide

Java 21 + Spring Boot 4 service that consumes request events (and the
rating and credit-change events, issue #255 and D37) from
RabbitMQ, stores notifications, and pushes them over STOMP. Read the
root `AGENTS.md` first (architecture, course constraints, AI usage
policy). The design authority is
[`docs/notification-service.md`](../docs/notification-service.md) —
every architectural choice is a numbered decision (D1–D34) there; cite
the decision ID when touching related code.

## Package map (`src/main/java/foc/notification/`)

| Package | Contents |
| --- | --- |
| `entity/` | `Notification` (one row per recipient, read/unread flag), `ProcessedEvent` (dedupe inbox) |
| `repository/` | Spring Data JPA repositories for both entities |
| `service/` | `EventProcessor` (port), `IdempotentEventProcessor` (dedupe + insert + publishes `NotificationStoredEvent`), `RecipientResolver` (who an event notifies, per event type from its id fields — D34), `NotificationDto`, `RetentionPurgeScheduler` (purges `notifications` + `processed_events` past the retention window, #137) |
| `messaging/rabbitmq/` | The only package allowed to touch broker types: `DomainEventsListener` (manual ack, retry republish, duplicate-key ack), `DomainEventMessageConverter` (dispatch on body `eventType` via `EventTypeRegistry`), `RabbitMqTopology` + `RabbitMqTopologyInitializer` (startup declaration), `RabbitMqMessageConverterConfig`, `RabbitMqUserPushFanout` (the `notification.push` fanout adapter of `UserPushFanout`, D35) |
| `messaging/stomp/` | `WebSocketStompConfig` (`/ws`, SockJS, heartbeats — also owns the app's sole `TaskScheduler`), `JwtChannelInterceptor` (CONNECT auth), `NotificationPushListener` (`AFTER_COMMIT` → `UserPushFanout.publish`), `UserPush` + `UserPushFanout` (the push envelope and its outbound port), `LocalUserPushDelivery` (the per-user STOMP send every replica makes for a fanned-out push), `StompPrincipal` |
| `security/` | `JwtVerifier` (HS256, jjwt; fails startup on missing/short secret), `JwtAuthenticationFilter` (REST bearer → security context, empty authorities per D28; built inline by `SecurityConfig`, not a bean), `RestAuthEntryPoint`/`RestAccessDeniedHandler` (401/403 problem+json), `redis/RedisTokenDenylist` (D23 — STOMP CONNECT only, never the REST path, G4) |
| `config/` | `SecurityConfig` — stateless chain: `/error`, `/actuator/health` and the `/ws` SockJS paths open (the CONNECT frame authenticates, not the handshake), everything else bearer-authenticated; CORS from `WEB_ALLOWED_ORIGIN`, registered for `/notifications/**` only — never `/**`, which would put the REST origin policy in front of the WS handshake/SockJS paths (PR #198 review) |
| `controller/` | `NotificationController` (`/notifications/me` routes, D24–D27), `ApiExceptionHandler` (problem+json 400/404), the contract's exceptions |
| `dto/` | `PageResponse` (the platform page envelope — a copy, not an import, D15), `MarkReadRequest`, `UnreadCountResponse` |

## Invariants — do not break

- **Manual ack after DB commit** (NFR1.1): the listener acks only after
  the processor's transaction commits; a retry republish must be
  broker-confirmed before the original delivery is acked.
- **Idempotency by event ID** (D4/F2.1): processed-event insert and
  notification insert commit in one transaction. The dedupe window is
  bounded by the retention purge (#137): rows older than
  `NOTIF_RETENTION_DAYS` are swept — an accepted trade-off, since
  redelivery ends at the DLQ within seconds. At two replicas (#305) a
  concurrent duplicate loses on the `processed_events` primary key; the
  listener acks that failure as a duplicate only when the violated
  constraint is `processed_events_pkey`; any other unique violation
  retries and dead-letters as usual.
- **Pushes go through the fanout, never straight to the local broker**
  (D35, #305): `NotificationPushListener` publishes to `UserPushFanout`;
  `LocalUserPushDelivery` is called only by the fanout adapter, once per
  replica, so a socket gets each frame exactly once.
- **Broker isolation** (D11): only `messaging.rabbitmq` may import
  `org.springframework.amqp` / `com.rabbitmq`. `ArchitectureTest`
  (ArchUnit) fails the build otherwise.
- **Dispatch on the body `eventType` only** (D18): no `__TypeId__` or
  other broker headers; `EventTypeRegistry` in `foc-contracts` is the
  single class↔identity source.
- **JWT verified at STOMP CONNECT** (issue #67): principal = token
  `sub` = platform user ID = the recipient `RecipientResolver` names from the
  event's `requesterId`/`courierId`/`rateeId` (D34) = `recipientId`. The
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

Tests: `IdempotentEventProcessorTest`, `RecipientResolverTest` (the D34
rule per event type, registry-driven), `NotificationRepositoryTest`,
`ProcessedEventRepositoryTest`,
`RetentionPurgeSchedulerTest` (H2), `DomainEventContractTest` (fixture
tolerant-reader lock-in), `DomainEventMessageConverterTest`,
`JwtVerifierTest`, `SecurityConfigTest` (REST chain 401s + the
`/ws/info` stays-open canary for #67), `NotificationControllerTest`
(REST contract: order, paging 400s, ownership 404s, idempotent
writes), `StompPushIntegrationTest` (real STOMP session,
random port, Testcontainers RabbitMQ for the push fanout — auto-skips
without Docker), `NotificationFlywayMigrationTest` (Testcontainers
Postgres: V1 and V2 apply, a ddl-auto database baselines, a legacy
entity-columns database converges), `ConcurrentDuplicateIntegrationTest`
(Testcontainers Postgres: the losing delivery of a raced event id fails
on `processed_events_pkey`),
`RabbitMqUserPushFanoutTest` / `LocalUserPushDeliveryTest` /
`NotificationPushListenerTest` (the D35 fanout halves),
`RequestEventsRabbitMqIntegrationTest`
(Testcontainers — auto-skips without Docker), `ArchitectureTest`
(D11 boundary).

Flyway owns the schema (`src/main/resources/db/migration`, D36, #305);
Hibernate only validates it. Add a new migration, never edit an applied
one. A volume built by the old `ddl-auto: update` is baselined as V1 on
first start, and V2 drops the pre-typed-contracts columns such a volume
may still carry, so no reset is needed for the schema; broker-volume resets
are in the [README's Troubleshooting section](README.md#troubleshooting).
