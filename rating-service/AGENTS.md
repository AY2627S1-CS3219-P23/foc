<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-11-02, issue #258.
  Scope: wrote this agent guide from the merged code (issues #250-#254)
  and the design doc (docs/rating-service.md); it records team-made
  decisions and invariants, and makes no design decisions of its own.
  Author review: Leong Wei Zhi, pending pull request review.
  2026-11-02, Claude Code (Fable 5), PR #285 review (@Sinnez1):
  RateableRequest listed as the deliberately updatable projection
  rather than with the immutable D9 entities; the review order located
  in RatingService.reviewsOf, not the repository; the dto/ row's
  NUL/length caps scoped to SubmitReportRequest. Transcription only.
  Author review: Leong Wei Zhi, pending pull request review.
-->

# rating-service/ — Agent Guide

Java 21 + Spring Boot 4 service that consumes `request.completed` from
RabbitMQ into rating-eligibility rows, takes immutable ratings and user
reports, serves profile read APIs, and publishes `rating.submitted`
through a transactional outbox. Read the root `AGENTS.md` first
(architecture, course constraints, AI usage policy). The design
authority is [`docs/rating-service.md`](../docs/rating-service.md) —
every architectural choice is a numbered decision (D1–D17) there; cite
the decision ID when touching related code.

## Package map (`src/main/java/foc/rating/`)

| Package | Contents |
| --- | --- |
| `entity/` | `Rating` + `UserReport` (no setters, columns `updatable = false` — D9); `RateableRequest` (the eligibility projection — no setters, but its columns deliberately stay updatable: the D5 upsert `save()`-merges a fresh instance over the stored row, and adding `updatable = false` would silently stop replayed completions from updating it); `ReportReason`; `ProcessedEvent` (the dedupe inbox; `Persistable` with a lifecycle-flipped `isNew` so `save()` persists, never merges — D4); `OutboxEvent` (PK = the event's own id) |
| `repository/` | Spring Data JPA repositories; `RatingRepository` holds the null-average summary JPQL, but its `findByRateeId` is unsorted — D11's `createdAt desc, id desc` order is built into the `PageRequest` in `RatingService.reviewsOf`, so don't call the repository expecting it; `OutboxEventRepository` inserts natively — `save()` would merge a sent event back to unsent |
| `service/` | `RatingService` (one eligibility gate shared by submit and `/mine` — D10), `ReportService` (trim-then-self-check, no existence checks — D14/D15), `RatingEligibilityProcessor` (inbox + upsert-with-WARN — D4/D5), `RatingEvents`, `EventOutbox` (`@Transactional(MANDATORY)` enqueue — D7), `OutboxPublisher` (1 s fixed delay), the `EventProcessor`/`EventPublisher` ports, and the contract exceptions (`AlreadyRatedException`, `NotAPartyException`, `UnknownRequestException`, `SelfReportException`) |
| `messaging/rabbitmq/` | The only package allowed to touch broker types: `RabbitMqTopology` (work/retry/DLQ trio bound on the exact `request.completed` key; declares the `rating-events` exchange only — consumers bring their own queues, D3/D7) + `RabbitMqTopologyInitializer`, `RequestEventsListener` + `RetryingListener` (manual ack after commit, client-stamped attempt header), `RabbitEventPublisher`, `DomainEventMessageConverter`, `RabbitMqMessageConverterConfig` |
| `controller/` | `RatingController` (D9/D10), `UserRatingsController` + `ReportController` (hand-validated `page`/`size`, int-offset-overflow 400 — D13), `ProblemDetailAdvice` (problem+json; 409 only for the `rating_once_per_rater` constraint, every other integrity violation an ERROR-logged 500 — D9), `InvalidQueryParameterException` |
| `dto/` | Request/response records; validation is the contract edge: `stars` is `Integer` (missing ≠ 0); the NUL-byte and 255-char `@Pattern` caps live on `SubmitReportRequest` only — the rating DTOs lack them, D15's recorded gap; `PageResponse` (a copy, not an import — D6) |
| `security/` | `JwtVerifier` (HS256, jjwt; fails startup on missing/short secret), `JwtAuthenticationFilter` (built inline by `SecurityConfig`, not a bean — a Filter bean would also run outside the chain), `RestAuthEntryPoint`/`RestAccessDeniedHandler` (401/403 problem+json) |
| `config/` | `SecurityConfig` — stateless chain: `/error` + `/actuator/health` open, `GET`/`HEAD /reports` role-gated in the chain itself (`@GetMapping` serves HEAD — D14), everything else bearer-authenticated; per-service CORS from `WEB_ALLOWED_ORIGIN`, `GET`/`POST`/`OPTIONS` only |
| `db/migration/` (resources) | Flyway owns the schema (D16): `V1__baseline`, `V2__rating_eligibility`, `V3__rating_and_outbox`, `V4__rating_read_index`, `V5__user_report`. `ddl-auto: none` — schema changes are new migrations, never entity-driven |

## Invariants — do not break

- **Ratings and reports are immutable** (D9, the #44 scope decision):
  no setters, `updatable = false`, no PUT/PATCH/DELETE routes. Don't
  add any of them without a new team decision.
- **Idempotency by event ID** (D4): the `ProcessedEvent` insert and the
  business write commit in one transaction; the listener acks after
  commit. Keep `Persistable`'s lifecycle-callback `isNew` — a constant
  `true` breaks `SimpleJpaRepository.delete` (PR #271 review).
- **The outbox is the only publish path** (D7): `EventOutbox.enqueue`
  is `MANDATORY` on purpose — a publish outside the state-changing
  transaction is a bug, not a convenience.
- **One eligibility gate** (D10): submit and `/ratings/mine` share
  `rateeOf()`; if the 403/404 behaviour needs changing, change it
  there, never by forking the check.
- **409 stays narrow** (D9): only the named `rating_once_per_rater`
  constraint maps to 409; everything else must remain a logged 500 —
  a handled exception never reaches Spring's default error logging.
- **Broker isolation**: only `messaging.rabbitmq` may import
  `org.springframework.amqp` / `com.rabbitmq`. No ArchUnit test
  enforces it here (unlike notification-service) — keep the discipline
  by convention.
- **Retry attempts live in the client-stamped header** (D3) — RabbitMQ
  4 resets `x-death` on client republish; don't "simplify" back.
- **No shared code across services except `foc-contracts`** (D6).
  Event contracts evolve additively; follow the
  [foc-contracts README's Event conventions](../foc-contracts/README.md#event-conventions)
  and the add-a-new-event checklist in
  [`foc-contracts/AGENTS.md`](../foc-contracts/AGENTS.md).

## Build & test

```sh
(cd ../foc-contracts && ./mvnw install)   # once, before first build
./mvnw test
```

Tests: `RatingControllerTest` (mine-null, shared gate, independent
directions, text cap, 409 race + narrow mapping),
`UserRatingsControllerTest` (average math, empty history, leakage,
offset overflow), `ReportControllerTest` (self-report incl. padding,
HEAD gate, NUL 400s, role gate), `RatingServiceTests` (rollback leaves
neither row), `RatingEligibilityProcessorTest` (duplicate no-op,
fresh-id upsert), `OutboxPublisherTests`, `RequestEventsListenerTest`
(settlement matrix), `RabbitMqTopologyTest`,
`DomainEventMessageConverterTest`, `SecurityConfigTest`,
`JwtVerifierTest` (H2, Flyway off — the migrations are
PostgreSQL-flavored); `FlywayMigrationTests` + `SchemaValidationTest`
(Testcontainers PostgreSQL via `PostgresTestContainer` — auto-skip
without Docker), `RequestEventsRabbitMqIntegrationTest` (Testcontainers
RabbitMQ: retry recovery, dead-lettering, unknown types).

Test config points RabbitMQ at `broker.invalid` so a cached-context
restart can never reach a real local broker, and disables the outbox
poller; the retry TTL is 500 ms for fast integration cycles.
