<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20 (header added on PR #79
  Copilot review; the file predates it).
  Scope: this log's entries are appended by whichever tool made the
  change they describe, following the template below; each entry names
  its own tool, author, and review.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# AI Usage Log

Shared log of AI tool usage, required by the CS3219 AI Usage Policy
(project document, Appendix 2). Every AI-assisted change must be logged
here with a timestamp, the prompts used, and the usage scenario.

Entry template:

```markdown
## YYYY-MM-DD — <member name>
- **Tool:** <e.g., Claude Code (Fable 5), GitHub Copilot>
- **Mode:** generate | refactor | debug | explain | docs
- **Scope:** <files/feature affected>
- **Prompt(s):** <exact prompt or summary + key responses>
- **Author review:** <how the output was validated/edited/tested>
```

---
## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** docs, boilerplate (developer tooling)
- **Scope:** Project-scoped MCP server configuration for agent tooling:
  `context7` (library documentation lookup), `postgres`
  (`@microsoft/postgres-mcp`, for inspecting a service-owned DB),
  and `playwright` (browser automation for the future `web/` frontend)
  added to the root `.mcp.json` alongside the existing `figma` entry;
  `notification-db` published on a host port in `compose.yaml` with a
  new `NOTIFICATION_DB_HOST_PORT` variable in `.env.example` so the
  postgres server can reach it; new "MCP Servers" section in
  `AGENTS.md` documenting each server and its setup, including a
  per-service database port table — we run database-per-service, so
  nothing service-specific is committed: each teammate registers a
  connection profile per service DB, with the password held in their OS
  keyring rather than in `.env` or any committed file.
- **Prompt(s):** Asked which MCP tools would help productivity on the
  project, then asked to set up context7, the postgres MCP and the
  playwright MCP as project MCP servers and raise a pull request. The
  tool checked the current npm packages (the reference
  `@modelcontextprotocol/server-postgres` and `server-github` are
  deprecated) and recommended against a GitHub MCP server since the
  `gh` CLI already covers that workflow. Whether to publish the
  database port in `compose.yaml` was put back to the author as an
  explicit choice and decided by the author. The author then raised
  that database-per-service means more than one DB; the tool verified
  against the running server that 17 of its 18 tools accept a per-call
  `connectionString`, and the author chose to document that override
  plus an empty port table rather than reserve host ports for the four
  services owned by other teammates. The author then pointed out that
  the committed connection string was still notification-specific, so
  it was replaced with a per-developer environment variable; the author
  then asked whether `@microsoft/postgres-mcp` could be used in place
  of the little-known `@henkey/...` package. The tool compared the two
  on published facts (Microsoft: MIT, official npm org, ~17k weekly
  downloads, but Preview 0.1.0-rc.x; henkey: AGPL-3.0, single
  maintainer, ~1k weekly downloads), tested the Microsoft server
  against the running notification DB, and switched to it — the
  decision was the author's.
- **Author review:** Developer tooling only — no product, requirements,
  or architecture decision is involved, and no service code changed.
  Note: this session ran on Opus 5, not the Fable 5 used for the
  earlier entries; the disclosure headers on `compose.yaml`,
  `.env.example` and `README.md` were corrected to say so after the
  author spotted the wrong model in them.
  Verified the `.mcp.json` is valid JSON, that no credentials are
  committed (credentials live in the OS keyring, not in `.mcp.json`
  or `.env`), and that the compose change adds only a host
  port publication, leaving the in-network wiring untouched. Reviewed
  via pull request.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** ArchUnit test enforcing the D11 broker-isolation boundary
  (issue #69, the last "Open items" entry in
  `docs/notification-service.md`): `archunit-junit5` added as a
  test-scope dependency (`notification-service/pom.xml`, version
  pinned via a new `archunit.version` property, matching the existing
  `jjwt.version` pattern for deps outside the Boot BOM); new
  `foc.notification.ArchitectureTest` (plain JUnit 5, no Spring
  context, matching the `DomainEventMessageConverterTest`/
  `JwtVerifierTest` precedent) asserting no package outside
  `messaging.rabbitmq` depends on `org.springframework.amqp` or
  `com.rabbitmq` types — exactly the rule the design doc's "Broker
  decoupling" section already prescribed. No production code changed:
  a repo-wide grep confirmed only the four existing
  `messaging.rabbitmq` classes import broker types today, so the rule
  is green from the moment it's added. Design doc's Open items list
  and "Broker decoupling" enforcement bullet updated to mark this
  done; service and root READMEs updated.
- **Prompt(s):** "create a new plan to fix issue #69" (following the
  same plan-then-approve flow as issue #68), then plan approval. No
  open design decisions to raise — the design doc already specifies
  the exact rule and package boundary; the only implementation detail
  (which `archunit-junit5` version to pin) is a routine dependency
  pick, not a design trade-off, so it wasn't raised as a Q&A.
- **Author review:** No design decisions made in this change beyond
  the already-recorded D11. Verified via `./mvnw test` (36/36 green:
  35 pre-existing + 1 new) and a manual sanity check — a throwaway
  `org.springframework.amqp.core.Message`-typed field added to
  `IdempotentEventProcessor` made the new test fail with a clear
  ArchUnit violation message, then was reverted — confirming the rule
  actually catches a real bytecode-level violation, not just imports.
  Reviewed via pull request.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** retention purge scheduler (issue #68, team decision D9,
  `docs/notification-service.md`): `NotificationRepository.deleteByCreatedAtBefore`
  (a Spring Data derived bulk-delete query, matching the design
  diagram's "Spring Data JPA: delete rows..." label); new
  `RetentionPurgeScheduler` (`service` package, plain business logic
  per D11 — no broker imports) reading `notification.retention.days`
  and running on a `notification.retention.purge-cron` schedule;
  `@EnableScheduling` added to `NotificationServiceApplication` (runs
  on the sole `TaskScheduler` bean already declared in
  `WebSocketStompConfig` for the STOMP heartbeat — Boot backs off its
  own default scheduler once a user-defined one exists, as a comment
  left on that class during issue #67 anticipated); `NOTIF_RETENTION_DAYS`
  (default 30, D9) and a new `NOTIF_PURGE_CRON` (default daily at
  03:00) wired through `application.yaml` (+ test yaml), `.env.example`,
  `compose.yaml`; new tests — `NotificationRepositoryTest` (`@DataJpaTest`)
  and `RetentionPurgeSchedulerTest` (`@SpringBootTest`, `@Transactional`),
  both backdating rows via a JPQL bulk update through the raw
  `EntityManager` since `created_at` is `updatable = false`; service
  README and root README AI Use Summary updated.
- **Prompt(s):** "create a plan to fix issue #68 on github", then plan
  approval. The design doc left two implementation details open (D9
  states the env var and default but not the cutoff timestamp field or
  schedule cadence); the tool surfaced both as neutral options Q&As.
  The author decided: `created_at` (row storage time) over `occurred_at`
  (event time) as the purge cutoff, matching D9's "stored notifications"
  wording; and a second env var (`NOTIF_PURGE_CRON`) controlling how
  often the sweep runs, kept independent of the retention-window size,
  over a hardcoded daily cron or a from-startup `fixedDelay`. Batch
  size and hard-vs-soft delete were not raised as decisions — the
  design doc's "delete" wording and the entity's lack of a soft-delete
  column already settle hard delete, and no batching is warranted at
  this project's scale.
- **Author review:** Both open decisions above made by the author from
  neutral options before implementation. Verified via `./mvnw test`:
  35/35 green (32 pre-existing + 3 new, including the Testcontainers
  broker path, unaffected) and pull-request review. PR #81 Copilot
  review addressed (same day): `deleteByCreatedAtBefore` changed from
  a derived `deleteBy...` method (loads and removes matching rows one
  at a time, not a bulk SQL DELETE) to an explicit
  `@Modifying @Query("delete from Notification n where n.createdAt <
  :cutoff")`, with `@Transactional` added directly on it since a
  custom `@Modifying` query is not transactional by default and the
  scheduled caller provides no surrounding transaction; return type
  changed `long` → `int` to match `Query.executeUpdate()`. No new
  design decision — both fixes are Spring Data JPA correctness/
  efficiency requirements the author had not weighed in on. Re-verified
  with `./mvnw test` (still 35/35) and replied inline to both review
  comments with the fix commit.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (+ docs)
- **Scope:** order→request event-vocabulary rename and foc-contracts
  package split, both author decisions recorded as D22
  (`docs/notification-service.md`): Java types (`RequestEvent` + the
  seven records), identities/routing keys (`request.created` …
  `request.courier-arrived`), the exchange (`request-events`,
  constant `REQUEST_EVENTS_EXCHANGE`), the notification queues
  (`notification-service.request-events` + `.retry`/`.dlq`), the
  `requestId` body field, and the fixtures; `foc.contracts.events`
  split into `events.core` (DomainEvent, EventTypeRegistry,
  EventContracts, Nullable) and `events.request` (marker + records),
  tests mirrored; notification-service imports, topology, converter,
  and all tests updated; docs (design doc D22 row + current-name
  updates, diagram, foc-contracts and service READMEs, migration
  notes for orphaned old broker entities). Historical notes (D14,
  struck Open items, past header/scope lines) deliberately keep the
  old names.
- **Prompt(s):** "Can you rename order to request in events defined
  in foc-contracts, also make sure the files in
  foc-contracts/src/main/java/foc/contracts/events are neatly
  arranged in their folders. make a new pr for this." The tool asked
  two neutral options Q&As; the author chose the full wire rename
  (over full-wire-keeping-orderId and Java-names-only) and the
  core+domain folder split (over a single domain subfolder).
- **Author review:** decisions made by the author (2026-09-20);
  verified via both module test suites (foc-contracts install +
  notification-service `./mvnw test`, 32 tests incl. the
  Testcontainers broker path against the renamed topology) and
  pull-request review.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (+ docs)
- **Scope:** issue #65 retry/dead-letter topology in
  `notification-service` per team decisions D6/D7 (10 s TTL, 3
  attempts, env-overridable; unknown types dead-letter): retry queue
  and DLQ added to `RabbitMqTopology` (work queue gains dead-letter
  arguments), `DomainEventsListener` reworked — republish-to-retry
  while attempts remain (counted via a listener-stamped
  `x-retry-attempts` header after the integration test surfaced that
  RabbitMQ 4 resets `x-death` counts on client republish; forced
  PERSISTENT on republish), nack-without-requeue to the DLQ once
  exhausted; new `notification.rabbitmq.retry.*` properties
  (`NOTIFICATION_RETRY_TTL_MS`, `NOTIFICATION_RETRY_MAX_ATTEMPTS`)
  wired through `application.yaml`, `.env.example`, `compose.yaml`;
  integration test extended (failure-injecting processor decorator,
  transient-recovery and poison-to-DLQ scenarios, unknown-type
  assertions updated from dropped to dead-lettered); design doc
  (D20/D21 recorded, D6/D7/F2.2/F2.3 rows updated), diagram, and
  service README migration note updated. PR #79 Copilot review
  addressed: the retry republish became a confirmed publish
  (`publisher-confirm-type: simple` + `waitForConfirmsOrDie` before
  acking the original, so a lost publish can never lose the event);
  explicit empty-body guard in the converter (fatal conversion →
  DLQ) with a unit test; disclosure header added to this log file;
  root README AI Use Summary wording extended. The review's
  crash-window duplicate-retry observation is the already-documented
  at-least-once posture (absorbed by event-ID dedupe) — no change.
- **Prompt(s):** "Make a plan to resolve issue #65 on github", then
  plan approval. The tool surfaced the two implementation choices the
  D6/D7 design left open as neutral options Q&As; the author decided
  (2026-09-20): the default exchange serves as the dead-letter
  exchange on both legs (vs a named DLX), and unconvertible messages
  dead-letter on first rejection without retry cycles (vs custom
  error-handler machinery to retry them). The listener-republish
  mechanics follow from the team's diagrammed topology (a queue has
  one dead-letter target), not a tool decision.
- **Author review:** decisions made by the author and recorded as
  D20/D21 in the design doc; verified via `./mvnw test` including the
  Testcontainers broker path (retry recovery, DLQ after max attempts
  with attempt-header + `x-death` evidence, unknown-type
  dead-lettering) and pull-request review.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor
- **Scope:** removal of the `schemaVersion` mechanism across
  `foc-contracts` and `notification-service` (amends D18, merged in
  PR #75 the day before): field dropped from `DomainEvent`, the 7
  records, all fixtures (renamed back to
  `<identity>.example.json`) and the wire; `EventTypeRegistry` keyed
  by identity alone again; the converter's version gate deleted
  (unknown-type rejection remains); conventions/design docs updated —
  breaking contract changes now ship as new event types (e.g.
  `order.accepted.v2`). Required-field validation and `@Nullable`
  are unchanged.
- **Prompt(s):** After merging PR #75 the author reconsidered the
  versioning machinery as overly complex and asked whether it could
  be removed, also querying whether concurrent v1+v2 acceptance was
  desirable. The tool explained the current behavior factually (only
  registered versions are accepted; concurrency exists only during a
  deliberate migration window) and presented three neutral options
  (remove entirely / keep versioned registry / keep a single-version
  gate); the author chose removal, with the new-event-type convention
  covering future breaking changes via the existing unknown-type
  rejection.
- **Author review:** Decision made by the author (2026-09-20) and
  recorded in D18; verified via both test suites and pull-request
  review.

## 2026-09-19 — Ryan Ang
- **Tool:** Claude Sonnet 5
- **Mode:** docs
- **Scope:** `docs/credit-service.md` and `docs/credit-service.mmd`
- **Prompt(s):** Convert the given credit-service.md file into a draft mermaid diagram. Skip heavy implementation details.
- **Author review:** Architecture was decided beforehand. Mermaid diagram was manually verified to ensure that it represents architectural details.

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (+ docs)
- **Scope:** Method-B event-contract refactor across `foc-contracts`
  and `notification-service` (decisions D16–D19 in
  `docs/notification-service.md`): deleted `EventEnvelope` + its
  fixture; added `DomainEvent`/`OrderEvent` interfaces, the 7 typed
  order-lifecycle records, `EventTypeRegistry`, 7 per-event fixtures,
  and registry/record tests (test-scoped JUnit added to the contracts
  pom; runtime stays dependency-free); consumer reworked —
  `DomainEventMessageConverter` (dispatch on the body's canonical
  `eventType` via the registry), topic exchange + `order.#` binding
  (replacing fanout), `EventProcessor`/`IdempotentEventProcessor` on
  `DomainEvent`, `EntitySequence(+Id)`/repository deleted,
  `entity_type`/`entity_id` dropped from `Notification` and
  `NotificationDto`; all 4 test classes updated + new converter unit
  test (27 tests green incl. Testcontainers); docs updated
  (design doc decisions table/sections, both .mmd diagrams, service
  README with migration steps, foc-contracts README with the new
  "Event conventions" section, AGENTS.md, root README AI summary).
  Follow-up cleanup of the `messaging.rabbitmq` package (author
  decisions from neutral options): `OrderEventsListener` renamed to
  `DomainEventsListener` (the class was already event-type-agnostic),
  and the per-domain topology declarations grouped into one
  `Declarables` bean with the initializer generically declaring all
  groups (exchanges, then queues, then bindings) — topology additions
  now touch only `RabbitMqTopology`. PR #75 Copilot review addressed:
  fixture null-check in the converter test; converter now enforces the
  registry's supported `schemaVersion` and rejects missing required
  fields (nullability declared via the contracts' new plain-Java
  `@Nullable` marker — only `OrderCancelled.courierId`, per the
  author's D17 field decision); producer conventions in the
  foc-contracts README now spell out the eventType-injecting
  converter a producer must own. These enforce rules the author had
  already decided (version-bump-is-breaking, injected eventType,
  courierId nullability); constant rename WORK_QUEUE →
  ORDER_EVENTS_QUEUE was an author naming decision from neutral
  options. After the tool explained the single-supported-version
  limitation of the first schemaVersion gate (a bump would reject all
  in-flight traffic on the lagging side), the author chose the
  registry-keyed-by-(eventType, schemaVersion) model — one record
  class per version, concurrent version support, fixtures renamed
  `<identity>-v<version>.example.json` — over strict big-bang and
  upcaster alternatives presented factually; the tool implemented it
  (registry `entriesFor`/`entryFor(type, version)` API, converter
  lookup + clearer unsupported-version errors, docs and the
  add-a-new-schema-version checklist in the conventions).
- **Prompt(s):** Asked to redesign the RabbitMQ architecture from the
  generic envelope (Method A) to explicit business-specific contracts
  (Method B). The tool surfaced the AI policy and ran neutral options
  Q&As (with JSON/code previews); the author made every decision:
  flat typed records with no envelope wrapper (after the tool
  explained that the flawed part of Method A was the untyped Map
  payload, not the envelope concept); the 7-event order-lifecycle
  catalog; a plain-Java registry in foc-contracts for type
  resolution; consumer+contracts scope only (no producer service
  yet); topic exchange with `order.#` binding; dropping the
  entity-identity/sequence stale-discard mechanism and the `sequence`
  field; deriving `eventType` instead of storing it per record (the
  author asked about repetitive validation; the tool laid out
  field+guard / derived / unguarded options); after consulting an
  external LLM, keeping `eventType` in the body as the canonical
  identity with the routing key carrying the same registry string for
  transport; retry values (3 attempts / 10 s) and unknown-type
  dead-lettering decided for issue #65 but explicitly kept out of
  this refactor-only change. The tool implemented the decisions.
- **Author review:** All design decisions made by the author across
  the Q&A rounds (2026-09-19) and recorded with attribution in the
  design doc's Decisions table (D16–D19). Verified by the author via
  the test suites (`foc-contracts` 9 tests, `notification-service`
  27 tests incl. the Testcontainers broker path) and pull-request
  review.

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** STOMP push gateway, backend half (issue #67):
  `messaging.stomp` adapter package — `WebSocketStompConfig` (`/ws`
  endpoint with SockJS fallback, simple broker on `/queue`, 10 s
  heartbeats with a dedicated TaskScheduler, Jackson 3 STOMP
  converter wrapping Boot's mapper per D15), `JwtChannelInterceptor`
  (Bearer JWT verified at STOMP CONNECT; session principal = token
  `sub`), `StompPrincipal`, `NotificationPushListener`
  (`@TransactionalEventListener(AFTER_COMMIT)` →
  `convertAndSendToUser`, best-effort with the catch documented);
  `security.JwtVerifier` (jjwt 0.13.0 + jjwt-gson, HS256 shared
  `JWT_SECRET`, fail-fast on short secret); processor publishes
  `NotificationStoredEvent` per saved row; `NotificationDto` (payload
  parsed; shape intended for reuse by #66); yaml/compose/.env.example
  wiring; docs ("Session mechanics" section, architecture.md sub
  convention); 6 JwtVerifier unit tests + 7 STOMP integration tests
  (real sessions on RANDOM_PORT, no Docker).
- **Prompt(s):** Asked to pick another notification issue and plan
  it; the tool identified #67 (last piece of the very-high F1
  umbrella) and surfaced that web/ has no SPA and no JWT
  code/User Service exists. The author decided, from neutral
  options: backend-only scope (frontend client deferred); CONNECT
  frame Bearer auth over query-param/cookie; jjwt over Spring
  Security resource server and hand-rolled HMAC; `sub` = platform
  user ID as the provisional cross-service claim convention;
  recording the provisional client reconnect policy now
  (exponential 1 s→30 s, resubscribe, 10 s heartbeats); and — on
  plan review — SockJS fallback enabled rather than plain WebSocket
  only. The tool implemented those decisions (jjwt-gson serializer
  chosen because no Jackson 3 jjwt module exists — implementation
  detail, disclosed in the pom header).
- **Author review:** All scope/security/interface decisions made by
  the author before implementation. Verified with `./mvnw test`:
  25/25 green (JWT unit tests; STOMP integration: push after commit
  within the 5 s budget, per-user isolation, silence for
  duplicate/stale events, CONNECT rejection for missing/invalid/
  expired tokens, SockJS fallback path; existing suites unaffected)
  and `dependency:tree` confirming no Jackson 2 databind was pulled
  in. Reviewed via pull request. Copilot review fixes on the same PR
  (author-directed): the SockJS test now uses an XHR-only transport
  list so the fallback path is actually exercised (WebSocket-first
  always won locally); allowed-origin patterns are trimmed after the
  comma split; the verifier explicitly rejects non-HS256 algorithms
  (defense in depth, with a unit test minting an HS384 token).

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** AMQP listener and idempotent event processor (issue
  #64): `OrderEventsListener` + `RabbitMqMessageConverterConfig`
  (Jackson 3 `JacksonJsonMessageConverter` wrapping Boot's
  auto-configured mapper, D15) in the `messaging.rabbitmq` adapter
  package; `EventProcessor` port + `IdempotentEventProcessor`
  (@Transactional: event-ID dedupe D4/F2.1, per-entity stale-sequence
  discard D5/F2.4, one notification row per party F1.2, generic over
  event/entity types F1.3, envelope-only data F1.1) in a new
  `service` package; entities/repositories restructured into
  `entity`/`repository` packages; `Notification` columns renamed to
  entity vocabulary; `OrderSequence` replaced by `EntitySequence`
  with composite `(entityType, entityId)` key; manual acknowledge
  mode in both yamls (test yaml also disables listener auto-startup);
  Testcontainers test deps; `IdempotentEventProcessorTest` (6 port
  tests on H2) and `OrderEventsRabbitMqIntegrationTest` (real broker
  end-to-end, auto-skips without Docker); design doc D5/component
  wording generalized to per-entity; READMEs updated.
- **Prompt(s):** Asked to pick another notification issue and plan
  it; the tool identified #64 as the only unblocked critical-path
  candidate under the author's earlier criterion and presented the
  open implementation decisions as neutral options. The author
  decided: schema renamed to entity vocabulary, composite
  (entityType, entityId) sequence key, literal manual ack (matching
  the doc's D11/NFR1.1 wording), interim failure handling as Spring's
  default split (conversion failures dropped, processing failures
  requeued; retry limits deliberately left to #65's pending team
  decision), a classic controller-service-repository package layout
  (author request during plan review), and the `EventProcessor` name
  over a Service suffix (after asking the tool's view — the tool
  noted D11 already prescribes `eventProcessor.process(...)`).
- **Author review:** All six decisions above made by the author from
  neutral options before implementation; the tool implemented them
  per the finalized design (D4, D5, D11, D15). Verified with
  `./mvnw test`: 12/12 green including the Testcontainers
  integration test against a real RabbitMQ container (fixture
  consumed end-to-end, duplicate absorbed, stale discarded, queue
  drained). Reviewed via pull request. Copilot review fixes on the
  same PR (author-directed): the per-entity sequence read now takes a
  pessimistic write lock (`findWithLockById`, SELECT ... FOR UPDATE)
  so concurrent deliveries cannot regress `last_applied_sequence`;
  the listener's nack-then-rethrow was kept after verifying against
  the spring-rabbit 4.1.1 sources that MANUAL mode never
  double-settles a rethrown delivery (documented in the listener
  Javadoc; explained in the review reply rather than changed).

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** Event envelope contract and shared fixture (issue #63):
  `EventEnvelope` record (plain, annotation-free) and the canonical
  contract fixture `contracts/order-event.example.json` added to
  `foc-contracts/`; consumer-side contract test
  (`EventEnvelopeContractTest`, `@JsonTest` with
  `spring-boot-starter-jackson-test` added to the service pom)
  asserting the fixture deserializes and tolerant-reader rules hold
  (unknown fields/types ignored, F1.3).
  D15 amendment, the Event envelope section, and READMEs updated.
  Same-PR revisions: fixture renamed to `order-event.example.json`
  (author request); envelope fields restricted to event-handling
  semantics (author decision) — the doc's platform-wide shape,
  finally named `entityType` + `entityId` + `parties[]`, adopted in
  place of `orderId` / `requesterId` / `courierId`, with domain
  identity and roles moved into `payload`; `type` renamed to
  `eventType` (author suggestion, symmetry with the entity-type
  field); on the grouping pair's name the author asked for
  alternatives to "aggregate" and chose entity over the
  DDD-conventional aggregate* and CloudEvents-style subject*,
  preferring plain English over pattern jargon; record, fixture,
  contract test, and design doc updated to match.
- **Prompt(s):** The author directed the tool to pick an open
  notification issue that is not blocked by other issues and plan it;
  applying that author-set criterion, the tool identified #63 as the
  only unblocked critical-path candidate (issue selection followed
  the team's existing backlog priorities — no requirements or
  prioritization work was done). The open design decisions were
  presented as neutral options first: DTO location (per-service
  copies + shared fixture vs. shared record in foc-contracts — the
  question team decision D15 had explicitly deferred to #63), fixture
  location, and payload Java type. The author chose the
  fixture-in-foc-contracts and `Map<String, Object>` payload options;
  on DTO location the author asked for the tool's view, and the tool
  restated constraints already recorded in the repo (the fixture, per
  the author's own prior choice, ships in the jar both services
  depend on; the library's documented framework-free rule; the design
  doc's additive-only evolution rule). The author weighed those and
  made the placement decision, recorded as the D15 amendment in
  `docs/notification-service.md`. Follow-up rounds: the author
  questioned why the test exists and where it lives (the tool
  explained the existing documented behavior: the contract is the
  JSON wire format, tolerant reading is consumer-side mapper
  behavior, and foc-contracts is deliberately dependency-free), asked
  why the fixture lives in foc-contracts, then directed that envelope
  fields relate only to event handling; the tool presented
  grouping-key and parties-shape options neutrally and the author
  chose the two-field grouping pair (finally named
  `entityType`/`entityId`) and a plain user-ID list. Per the AI Usage
  Policy, decision authority remained with the author throughout:
  every design choice in this entry was made by the author, and the
  author owns the recorded decisions and their rationale.
- **Author review:** All three contract decisions made and confirmed
  by the author before implementation (DTO location after weighing
  the documented constraints the tool restated). Verified with
  `./mvnw install` (foc-contracts) and `./mvnw test`
  (notification-service, 5/5 green including the 4 new contract
  tests); reviewed via pull request.

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation)
- **Scope:** RabbitMQ broker infrastructure and core topology (issue
  #62): `rabbitmq` container + named volume in `compose.yaml`, broker
  env vars in `.env.example`, `spring-boot-starter-amqp` and
  `spring.rabbitmq` config in `notification-service`, and the
  `foc.notification.messaging.rabbitmq` adapter package declaring the
  durable `order-events` fanout exchange, durable
  `notification-service.order-events` queue, and binding, provisioned
  at startup. Same PR, follow-up decision: the shared `foc-contracts/`
  Maven library (plain contract constants, no framework code) holding
  the `order-events` exchange name, consumed by the notification
  service; service Docker build context moved to the repo root (with a
  root `.dockerignore`) so the library builds inside the image.
  Decisions D12–D15 recorded in `docs/notification-service.md`; the
  D15 shared-code exception recorded in `AGENTS.md` and the root
  README.
- **Prompt(s):** Asked to pick one open GitHub issue and resolve it via
  a PR; the tool picked #62 and presented the issue's open decisions
  (topology provisioning, naming convention, exchange type) as neutral
  options. Follow-up Q&A: the author asked how the exchange name should
  be carried (config vs constant — an initial env-overridable config
  choice was made and then reverted by the author), asked for factual
  industry conventions, then decided on a shared constants library; the
  tool presented forms (config file vs Java library vs contract folder
  + tests) neutrally, including the conflict with the existing
  no-shared-code convention. Naming rounds (module name kept as
  foc-contracts; constants class named EventContracts over
  MessagingContracts to avoid colliding with a possible future N6 chat
  feature) were likewise author decisions from neutral options.
- **Author review:** All four open design decisions were made by the
  author (D12 app-declared provisioning via Spring AMQP, D13
  `order-events` / `notification-service.order-events` naming, D14
  fanout exchange, D15 shared contracts library amending the AGENTS.md
  no-shared-code convention); the tool implemented them per the
  already-finalized design (D1, D8, D11). Verified with `./mvnw test`
  and the issue's acceptance test (publish persistent message, restart
  broker, message survives). Copilot review fixes on the same PR:
  pinned `hostname: rabbitmq` so the persisted node data survives
  container recreation, documented broker credential rotation in
  `.env.example`, `@Qualifier`s in the topology initializer to stay
  unambiguous when #65 adds retry/DLQ beans, attribution headers added
  to `AGENTS.md`, the root `README.md`, and `foc-contracts/.gitignore`,
  and the PR description's stale class name corrected. Reviewed via
  pull request.

## 2026-09-19 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (review fixes)
- **Scope:** PR #70 follow-up addressing Copilot review comments:
  `NOTIFICATION_DB_HOST`/`NOTIFICATION_DB_PORT` placeholders added to
  `.env.example`; `payload` column marked non-null in
  `Notification.java` (the design doc treats every envelope field as
  required); per-file attribution headers added to `.env.example` and
  the five generator-emitted files (`.gitignore`, `.gitattributes`,
  `maven-wrapper.properties`, `mvnw`, `mvnw.cmd` — Spring
  Initializr/Apache Maven Wrapper boilerplate, otherwise unmodified).
- **Prompt(s):** Asked to resolve the Copilot review comments on PR #70.
- **Author review:** No new design decisions — the non-null constraint
  enforces the documented envelope contract. `./mvnw test` re-run to
  confirm the build and the edited wrapper script still work. Reviewed
  via pull request.

## 2026-09-18 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (scaffolding)
- **Scope:** `notification-service/` Spring Boot scaffold (issue #61):
  Maven project via Spring Initializr (Boot 4.1.1, Java 21), JPA
  entities + repositories for the three team-decided tables
  (notifications with read/unread flag, processed event IDs,
  last-applied sequence per order — D3/D4/D5), Actuator health
  endpoint, Dockerfile, `compose.yaml` entries (service + PostgreSQL
  with named volume), `.env.example` variables, context-load test on
  in-memory H2.
- **Prompt(s):** Asked to pick one open GitHub issue and resolve it via
  a PR; the tool selected #61. Build tool (Maven) and Java version (21)
  were put to the author as explicit choices and decided by the author.
  Table-level schema comes from the issue text and the design doc's
  Decisions table; column-level detail was derived from the documented
  event envelope and is subject to author review.
- **Author review:** All design decisions (PostgreSQL, JPA, the three
  tables, envelope fields) were made by the team beforehand
  (`docs/notification-service.md`); the tool implemented them.
  Verified locally: `./mvnw test` passes; `docker compose up
  notification-service` starts against its own PostgreSQL; health
  endpoint returns UP; the three tables are present. Reviewed via pull
  request.

## 2026-09-18 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs
- **Scope:** `docs/notification-service.md` and
  `docs/notification-service.mmd` (also `docs/architecture.md` /
  `docs/architecture.mmd`, merged earlier via PR #57).
- **Prompt(s):** Asked to re-align the architecture diagram and the
  notification-service design with the latest D1 backlog (Template-7).
  Later the same day: asked how the broker pipeline works, how to keep
  it loosely coupled to RabbitMQ, and how it extends to more
  producers/consumers; the tool explained the options neutrally, then
  ran a Q&A in which the author decided: broker isolation via ports and
  adapters (new decision D11), keep broker-native retry/DLQ (D6/D7
  unchanged, trade-off noted), record multi-producer/consumer mechanics
  as extension notes with exchange topology left an open decision, and
  document it all in `docs/notification-service.md`. Finally, asked to
  fold the decided broker/transport/DB choices into
  `docs/architecture.md` / `.mmd`, replacing the corresponding TBD
  markers (transcription of decisions already recorded in the
  notification-service Decisions table). Also documented the
  connection topology (single Notification→Web WebSocket, all other
  frontend traffic stateless REST) as rationale following from D2 and
  the existing architecture edges, with WebSocket handshake/reconnect
  mechanics and the N6 chat transport recorded as open decisions.
- **Author review:** Backlog realignment was transcription only —
  requirement references renumbered (e.g. Order F11.1 → F0.2, Credit
  NFR3.2.1 → NFR2.2.1), items the backlog reclassified as nice-to-haves
  (chat, admin credit adjustment) moved out of committed scope, and the
  courier collection/arrival updates (Order F4.1.1–F4.1.2) traced to
  the team's existing generic-envelope design. The decoupling/
  extensibility decisions were made by the author in the Q&A and are
  recorded in the document's Decisions table and Extensibility section.
  Reviewed via pull request.

## 2026-09-15 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs, explain
- **Scope:** `docs/notification-service.md` and
  `docs/notification-service.mmd` (notification-service design document
  and component diagram).
- **Prompt(s):** Asked to design the notification-service architecture;
  the tool declined to design per the AI policy and instead ran a Q&A in
  which the author made every design decision (RabbitMQ; WebSocket/STOMP
  push; PostgreSQL; unique-event-ID dedupe; per-order sequence numbers;
  TTL-backoff broker redelivery; dead-letter exchange/queue; durable
  queues + persistent messages; configurable 30-day retention; no
  Redis). The tool also gave neutral factual explanations (Kafka vs
  RabbitMQ properties, DB vs broker dead-letter, when Redis becomes
  relevant, what STOMP is) to inform those decisions.
- **Author review:** All decisions made by the author during the Q&A and
  recorded in the document's Decisions table; document and diagram
  reviewed via pull request.

## 2026-09-14 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs
- **Scope:** `docs/architecture.md` — high-level service architecture
  diagram (Mermaid) plus an exported SVG/PNG for the D1 document.
- **Prompt(s):** Asked to draw the high-level architecture diagram
  connecting the dependencies between services, based on the team's D1
  functional/non-functional requirements and following the CS3219 L4
  diagram guidelines (title, legend, labelled lines, explicit elements).
- **Author review:** The architecture itself (microservices, service
  boundaries, REST + async event workflow, JWT auth) was decided by the
  team beforehand (AGENTS.md, D1 document); the tool transcribed those
  decisions into a diagram. Open decisions (broker technology, database
  engines, client update transport) were kept as explicit "TBD" markers
  for the team — the tool made no design choices. Reviewed via pull
  request.

## 2026-09-10 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs, boilerplate
- **Scope:** GitHub issues for the product backlog (from the team's D1
  requirements document), labels, README team-allocation table,
  `notification-service/` skeleton folder, `AGENTS.md`, this log file.
- **Prompt(s):** Asked to create GitHub issues from the team-written D1
  requirements with priority/sprint/service labels; update README with the
  team's allocation; document the team's chosen tech stack and the course
  guidelines in AGENTS.md.
- **Author review:** Requirements, priorities, allocation, and tech stack
  were decided by the team beforehand (D1 document/presentation); the tool
  transcribed and formatted them. Output reviewed via pull requests.
