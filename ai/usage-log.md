<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-20 (header added on PR #79
  Copilot review; the file predates it).
  Scope: this log's entries are appended by whichever tool made the
  change they describe, following the template below; each entry names
  its own tool, author, and review.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-10-17, Codex (GPT-6): recorded PR #180 review work and the
  missing main-sync entry from its PR description and file headers.
  Author review: Leong Wei Zhi to review via PR #180.
  2026-10-17, Codex (GPT-6.1 Sol): recorded implementation and verification
  of the Docker-unavailable test skips for issue #98.
  PR #197 review: recorded the CI skipped-test check and its verification.
  2026-10-17, Claude Code (Opus 5): appended the issue #203 entry below.
  PR #209 main merge: this file was the only conflict — both sides had
  appended fresh entries under the same `---`, so both were kept (the
  #203 entry first, then main's three order-service ones) and the stray
  second `---` the #203 entry had introduced was dropped; nothing
  reworded or removed.
  Author review: Leong Wei Zhi to review via pull request.
  2026-10-20, Copilot SDK in VS Code (model not exposed): added the
  AuthServiceTest matcher-misuse fix entry below.
  Author review: Kwey Xiu Xi to review via pull request.
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
## 2026-10-27 — Leong Wei Zhi (feat: clicking a notification opens the request it is about, issue #232)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate, docs
- **Scope:** `web/src/features/notifications/eventMessage.ts`
  (`requestIdOf` exported, `requestLabel` refactored onto it),
  `web/src/features/notifications/NotificationCenter.tsx` (a row whose
  payload carries `requestId` navigates to `/deliveries/{requestId}`;
  an unread one is also marked read, fire-and-forget, before
  navigating; read request rows become clickable; request-less rows
  keep the mark-read-only click), `web/src/test/notification-center.test.tsx`
  (three new click-path cases, labels swapped to "select to open the
  request", the mark-read-in-place regression tests moved to
  request-less fixtures, and the mocked order base made real so the
  landing page's fetch reaches the stub), `web/AGENTS.md`
  (notification-center.png row), `README.md` (AI Use Summary).
- **Prompt(s):** Plan and resolve issue #232. The landing target
  (always the delivery-detail page) and the click-marks-read behavior
  were the author's decisions via options Q&A; the issue had left both
  open.
- **Author review:** `npm test` (308/308), lint, format and build
  green. Leong Wei Zhi to review via the PR.

## 2026-10-26 — Ryan Ang (feat: Notification accepts the N3 dispute events, issue #237)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** notification-service tests:
  `RequestEventsRabbitMqIntegrationTest` (both dispute fixtures
  published raw under their routing keys, one row per party, nothing
  dead-lettered, redelivery deduped behind a sentinel; the fixture
  loader now takes the event type), `DomainEventContractTest` (the
  fields each dispute fixture adds, and both parties named). No main
  code change: the consumer dispatches through `EventTypeRegistry`, so
  rebuilding against the current `foc-contracts` is what makes it
  accept the two types. `README.md` (AI Use Summary and header line).
- **Prompt(s):** The tool wrote the two tests from issue #237's
  implementation checklist (part of #46, DSP-15) and confirmed that the
  consumer needs no main-code change, since it dispatches through the
  registry.
- **Author review:** `./mvnw test` green with Docker, including the
  Testcontainers broker integration test. Ryan Ang to review via pull
  request.
- **PR #260 review follow-ups:** the review's four
  suggestions applied. The broker test checks each dispute field in
  both stored payloads, and the accepted-event test uses `rowsOfType`.
  `DomainEventContractTest`'s two registry tests read their fixture
  through `readFixture`, so the path rule and its not-null check live
  in one place. The `request-dispute-resolved` fixture in
  `foc-contracts` now has distinct amounts, courier 0 and requester 2,
  still adding up to the fixture request's reward of 2, with a
  matching `resolutionNote`, so a swapped split fails the tests.
  `foc-contracts` install (with its tests) and `./mvnw test` green
  with Docker.
  Second round: the contract test checks the split adds up to the
  `request-submitted` fixture's reward (DSP-8), and the README summary
  names the fixture change.

## 2026-10-25 — Ryan Ang (feat: Credit admin list of long-held reservations, issue #236; PR #247 and PR #210 follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** credit-service (`AdminCreditController.java`:
  `GET /credits/admin/reservations/held`; `ReservationQueryService.java`
  and `HeldReservationsResponse.java` new; `ReservedRepository.java`:
  held at or before a cutoff, paged;
  `db/migration/V5__reservation_share_check_and_held_index.sql` new;
  `CreditController.java`: comment only; `AGENTS.md`; tests:
  `AdminCreditControllerTest`, `CreditOperationsServiceTest`,
  `CreditServiceApplicationTests`). `docs/credit-service.md` (the admin
  route and its 400s, D9, the listener, handler, slice and
  `credit_reservation` rows, the diagram and the constraints list).
  `ai/usage-log.md` (the #235 entry's test count and trailing spaces).
  `README.md` (AI Use Summary and header line).
- **Prompt(s):** Summary: implement issue #236 as written there (team
  decisions DSP-5, DSP-11), and in the same PR the outstanding PR #247
  review follow-ups and the stale comment left from the PR #210 review.
  Before coding, the tool listed what the issue left open, and the
  decisions made were: the body is an object around the list, as on
  `/credits/me/expiries`, so fields can be added later; a negative
  `olderThan` is a 400 naming the problem, and `PT0S` is allowed and
  lists every held reservation; a reservation made exactly at the
  cutoff is included. The team also took both optional PR #247 items:
  the D9 pointer to DSP-4, and the review's suggested CHECK, added in
  V5 since V4 is applied. The tool chose a request ID tiebreak so the
  capped list is stable, and a cutoff clamped to the epoch so a very
  long duration cannot leave the range of `Instant` or PostgreSQL (it
  changes no result, as no reservation predates it). Two PR #248 review
  suggestions were done: the list says whether it was cut off (101 rows 
  are read, 100 returned, and `truncated` is true when there were more), 
  and a partial index on the HELD rows by `created_at` and `request_ref` 
  serves it. 
- **Author review:**
  credit-service 321 tests pass locally with Docker, none skipped.

## 2026-10-25 — Ryan Ang (feat: Credit split settlement for request.dispute-resolved, issue #235)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** credit-service (`db/migration/V4__reservation_resolved.sql`
  new; `ReservationStatus.java`: `RESOLVED`; `Reserved.java`:
  `courierShare`; `CreditReservationSliceRepository.java`: slices latest
  expiry first; `CreditOperations.java`, `CreditOperationsService.java`:
  `resolve`, and a reserve / release on a RESOLVED record;
  `RequestEventHandler.java`: the `RequestDisputeResolved` arm;
  `RabbitMqTopology.java`: the fifth consumed event;
  `RequestEventsListener.java`: comment only; `README.md`, `AGENTS.md`;
  tests: `CreditOperationsServiceTest`, `RequestEventHandlerTest`,
  `EntityMappingTest`, `RabbitMqTopologyTest`,
  `RabbitMqTopologyIntegrationTest`). `docs/credit-service.md` (schema,
  constraints, the event table with a RESOLVED column and a
  `request.dispute-resolved` row, lots, operation answers, failure
  table). `README.md` (AI Use Summary and header line).
- **Prompt(s):** Implement issue #235 as written there (team decisions
  DSP-4, DSP-8, DSP-13). Before coding, the tool listed what the
  decisions left open, and the decisions were: on a RESOLVED record a
  `request.cancelled` / `request.expired` or a `request.completed` is
  dead-lettered and a redelivered `request.submitted` is a duplicate.
  The team also gave the rule behind the whole event table (an event
  matching what already happened is a duplicate, one contradicting it is
  INVALID_STATE), transcribed into `docs/credit-service.md` in the
  team's words. A courier share of 0 creates no lot (as it writes
  no history row); a slice is refilled in part where the requester's
  share runs out inside it; "latest expiry first" puts the never-expiring
  sign-up credits first, from DSP-4's "the credits that last longest"; a
  missing courier or a null or negative share is INVALID_STATE before the
  record is looked up, as a transfer checks its courier; the shares are
  `Integer` so a missing one reaches that check.
- **Author review:**
  credit-service 296 tests pass locally with Docker, none skipped.

## 2026-10-23 — Ryan Ang (feat: Order failed-order management, issue #240)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** order-service (`db/migration/V2__close_reason_outbox_request_id.sql`
  new; `CloseReason.java` new; `Requests.java`: `closeReason`;
  `OutboxEvent.java`, `EventOutbox.java`, `OutboxEventRepository.java`:
  `request_id`, the newest settlement event and its reset;
  `RequestsService.java`: close reasons on cancel / timeout / reject /
  expire, `retrySettlement`; `CreditReplyHandler.java`: `reasonCode` →
  `CloseReason`; `RequestsQueryService.java`, `RequestSpecifications.java`:
  admin list and batch statuses; `RequestSummary.java`,
  `RequestDetail.java`: `closeReason`; `RequestsController.java`,
  `SecurityConfig.java`: `GET /requests/admin`, `POST
  /requests/admin/statuses`, `POST /retry-settlement`, ADMIN / OWNER;
  `README.md`, `endpoints.txt`; tests: `RequestsServiceTests`,
  `CreditReplyHandlerTests`, `RequestsReadEndpointsTests`,
  `OutboxPublisherTests`, `FlywayMigrationTests` new).
  `docs/disputes.md` (DSP-1, DSP-5, DSP-17 amended; #240 revision entry).
  `README.md` (AI Use Summary and header line).
- **Prompt(s):** Implement issue #240 as written there (team decisions
  DSP-1, DSP-5, DSP-10, DSP-17). Before coding, the tool listed four
  points the issue left open; the team decided them: `closeReason` as a
  nullable field in both `RequestSummary` and `RequestDetail`; the admin
  list takes `state`, `closeReason` (comma-separated, unknown value 400)
  and the shared `RequestFilter`; batch statuses answer an unknown id
  with `null`, an empty list with `{}`, and more than 100 or a
  non-numeric id with 400, a repeated id once; `close_reason` as text
  with a named CHECK, `request_id` as `bigint` with no foreign key,
  passed to `EventOutbox.enqueue` by the caller rather than parsed from
  `correlationId`. These were transcribed into `docs/disputes.md`. The
  `reasonCode` → `close_reason` mapping sits in one place in
  `CreditReplyHandler` (the PR #243 review follow-up left for this
  issue). The tool chose, as implementation details: "finished" for
  retry settlement means a terminal state in `OrderStateMachine`, so a
  REJECTED request gets the "no settlement event" 409; the settlement
  event types come from `EventTypeRegistry`; the reset is a `@Modifying`
  update; the batch-status ids bind as strings so a non-numeric id gets
  the service's 400 message.
- **Author review:** 
  order-service 126 tests pass locally with Docker, none skipped
  (V2 back-fill tested on a separate schema migrated V1 → rows → V2).

## 2026-10-23 — Ryan Ang (fix: PR #244 review findings)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** order-service (`V1__baseline.sql`: `outbox_event.payload`
  as `text` and the partial index `idx_outbox_event_unsent`;
  `OutboxEvent.java`: `payload` declared `text`; `RequestsServiceTests`:
  `databaseAcceptsEveryOrderState` restored; `README.md`: the reset
  recreates every volume, with the reason). `.github/workflows/ci.yml`:
  the skipped-tests guard runs on every Java leg. notification-service
  (`DomainEventContractTest`: the fixed value is read from a named
  component per record). `README.md` (AI Use Summary and header line).
- **Prompt(s):** Review and explain the six PR #244 review findings. 
  Team decisions: add the outbox index now, partial on `created_at` 
  where `sent_at is null` (Order's sent rows are kept and the poll runs 
  every second), without credit-service's `idx_outbox_event_sent`, since 
  Order has no purge; make `payload` `text` so both outbox copies behave 
  the same; edit V1 directly rather than adding a V2, as no deployment has
  applied it yet. The other four fixes followed the reviewer's
  suggestions.
- **Author review:** 
  order-service 104 tests (the restored test is the 104th) and
  notification-service 89 pass locally with Docker, none skipped. A
  fixture with a misspelt category and the correct value in another
  component now fails the fixture test.

## 2026-10-22 — Ryan Ang (feat: Order Flyway baseline, issue #238; PR #243 follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, refactor, docs
- **Scope:** order-service (`pom.xml`: Flyway, Testcontainers Postgres,
  H2 removed; `db/migration/V1__baseline.sql` new; `application.yaml`
  main and test: `ddl-auto: validate`; `PostgresTestContainer` new, and
  the ten `@SpringBootTest` classes extend it; `Requests.java`: the
  hand-written status CHECK removed from `columnDefinition`;
  `RequestsServiceTests`: the state-list test removed with it;
  `CreditReplyHandlerTests`: the rejection helper takes the reasonCode;
  `README.md`: Schema section and the one-off `order-db` reset).
  PR #243 review follow-ups: credit-service (`RequestEventHandler` reason
  text constants renamed `*_TEXT`; `RequestEventHandlerTest`),
  foc-contracts (`README.md` record list gains `RequestDropped`;
  `RequestEventRecordsTest` javadoc), notification-service
  (`DomainEventContractTest`: a fixture's fixed value must be in its
  record's value set). `README.md` (AI Use Summary and header line).
- **Prompt(s):** Implement issue #238 as written there (team decision
  DSP-16), and address the PR #243 review follow-ups in the same branch.
  V1 was taken from Hibernate's PostgreSQL DDL export of the current
  entities and checked against the schema `ddl-auto: update` had built
  in an existing `order-db` volume: the same columns and types, with
  the status CHECK written once and named `requests_status_check`
  (Hibernate had emitted it twice). The test base class follows
  credit-service's and user-service's (skips without Docker). For the
  follow-ups the tool took the reviewer's suggested fixes; where two
  were offered for the constant names it took the rename, the smaller
  one. The follow-up about mapping `reasonCode` to `close_reason` in one
  place is left for issue #240, where `close_reason` is added.
- **Author review:** 
  All suites pass locally with Docker: foc-contracts 12, order-service
  103 (the removed state-list test was the 104th), credit-service 268,
  notification-service 89 tests, none skipped. A misspelt fixture
  category made the new fixture test fail.

## 2026-10-21 — Ryan Ang (feat: N3 dispute event contracts, issue #234)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, docs
- **Scope:** `foc-contracts` (`RequestDisputed` new, with its
  `CATEGORIES` value set; `RequestDisputeResolved` new;
  `CreditReservationRejected.reasonCode` and its `REASON_CODES` value
  set; `EventTypeRegistry`; two new fixtures and the rejection fixture;
  `EventTypeRegistryTest`; `RequestEventRecordsTest` (value-set check);
  `README.md`, `AGENTS.md`), credit-service (`RequestEventHandler` sets
  `reasonCode`; `RequestEventHandlerTest`, `ReplyOutboxServiceTest`,
  `OutboxPublisherIntegrationTest`), order-service tests
  (`CreditReplyHandlerTests`, `DomainEventMessageConverterTests`),
  `docs/credit-service.md`, `docs/notification-service.md`,
  `docs/disputes.md`, `README.md` (AI Use Summary and header line).
- **Prompt(s):** The author asked the tool to review issue #234. 
  Before writing code the tool reported two conflicts with standing 
  team rules: a required `reasonCode` breaks D18's additive-only rule, 
  and DSP-12's dispute `note` would repurpose the request `note` that 
  the PR #166 rule puts on every request record. The team decided: 
  `reasonCode` is `@Nullable` (always set by Credit; Order leaves
  `close_reason` null when it is missing), and both new records keep 
  the standard `note` and carry `pickupLocation`/`dropoffLocation`, 
  with `disputeNote` and `resolutionNote` as separate nullable fields. 
  The tool implemented those decisions. It had also held the fixed values
  as `String` constants on the records and reported this as its own
  choice; the author confirmed it over plain strings and enums and
  decided the rules around it: a `Set` of all values on each record
  (checked by a test), services converting to their own enums at the
  edge with a fallback for unknown or null values, API input checked
  against the set (400), and a logging default branch in every consumer
  `switch`.
- **Author review:** Ryan to review before opening the PR. All
  suites pass locally with Docker: foc-contracts 12, order-service 104,
  credit-service 268, notification-service 88 tests, none skipped.

## 2026-10-20 — Kwey Xiu Xi (user-service: AuthServiceTest stubbing)
- **Tool:** Copilot SDK in VS Code (model not exposed)
- **Mode:** debug
- **Scope:** `user-service/src/test/java/foc/user/service/AuthServiceTest.java`;
  replaced `eq(false)` matcher values in `PendingSignup` constructors with
  boolean literals to prevent matcher leakage into repository stubs.
- **Prompt(s):** "Help resolve test errors
  org.mockito.exceptions.misusing.PotentialStubbingProblem: Strict stubbing
  argument mismatch"
- **Author review:** Targeted `AuthServiceTest` run to verify; review via PR.

## 2026-10-18 — Aung Ko Khant (web: order pages on the real order API)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, refactor
- **Scope:** `web/src/features/order` — `types.ts` and `api.ts` replaced
  the placeholder contract with order-service's real one (paths, the
  request states incl. PENDING / REJECTED, RequestSummary / RequestDetail,
  the /create body, {"message"} replies and error bodies); `status.ts`,
  `StatusChip`, `OrderProgress` retyped; new `StateFilter` and
  `useUsername` (user-service's GET /users/{id}, cached); `orderApiMock.ts`
  rewritten to the real contract and rules; `DisputeModal` removed. Pages
  `my-requests`, `open-requests`, `active-delivery`, `request-history`,
  `create-request` adapted; tests `orders.test.tsx` and
  `order-api.test.ts` rewritten (255 web tests passing); `web/AGENTS.md`
  wireframe rows.
- **Prompt(s):** "Can we merge the frontend and backend tgt for order
  service? Adapt frontend to match backend when a discrepancy occurs".
  The tool listed the mismatches (from its PR #192 review) and asked the
  points the rule left open as neutral options; the author chose:
  usernames looked up from user-service; a state filter (one backend query
  per chip) for My Requests and History; keep and rewrite the dev mock; a
  new branch stacked on #213. Following the rule, dispute and re-release
  were removed, actions reload instead of patching, and the open feed
  uses the backend's sort=deadline.
- **Author review:** `tsc`, `eslint`, `prettier` and `vitest` (255
  tests) pass. Run in a browser against the compose stack with the real
  user-, order-, credit- and notification-services: create (PENDING ->
  reserved -> Open), accept, pick up, arrive, confirm; balances moved
  5 -> 2 (requester) and 5 -> 8 (courier); History checked at 390 px. To
  be reviewed via pull request.
## 2026-10-19 — Leong Wei Zhi (issue #221: D32 read-time name hydration, supersedes D30/D31)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor
- **Scope:** removed the system's only synchronous inter-service call —
  notification-service's ingest-time username enrichment (D30) and its
  `X-Internal-Token` credential (D31). notification-service: `UserDirectory`
  port, `RestUserDirectory` adapter (+ test) deleted; `IdempotentEventProcessor`
  stores IDs-only payloads and reverted to `@Transactional` (the
  lookup-before-transaction ordering was `TransactionTemplate`'s only
  reason); `notification.user-service.*` yaml keys and
  `spring-boot-starter-restclient` dropped. user-service:
  `InternalServiceTokenFilter`, its SecurityConfig wiring,
  `user.internal.service-token` and the four token tests deleted
  (`GET /users/{id}` stays authenticated — now the web's hydration source).
  web: new `features/user/usernames.ts` (per-id `GET /api/user/users/{id}`
  via the gateway, 10 m/30 s TTL cache, in-flight dedupe, `useUsernames`
  hook + unit tests); `eventMessage.ts` reads `courierId` against a
  resolved-name map (stored name keys of old rows ignored);
  `NotificationCenter` hydrates REST rows and STOMP frames at one seam.
  compose.yaml/.env.example: `USER_SERVICE_BASE_URL` +
  `INTERNAL_SERVICE_TOKEN` plumbing removed. docs/notification-service.md:
  D32 row, "Display names (D32)" section, catalog/config notes.
- **Prompt(s):** "check if synchronous communication between microservices
  exist", "is this anti pattern? what are the alternatives", "what would
  industry do?", then "/plan make a plan to remove this synchronous call
  using [read-time hydration industry pattern]. Create a new github issue
  for the created pr to resolve." Design decided by the author via neutral
  options Q&A: per-id lookups + SPA cache (no batch endpoint), internal-token
  machinery removed in the same PR, web always hydrates (ignores stored
  name keys, no migration), Claude opens the PR closing #221.
- **Author review:** Leong Wei Zhi to review via the PR. Verified:
  notification-service + user-service `./mvnw clean test`, web `npm test`
  (279) + `npm run build`, compose config parse, dead-reference grep.

## 2026-10-19 — Ryan Ang (PR #224 review: outbox retention purge off in tests)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + docs)
- **Prompt(s):** asked to review the PR #224 comments, then to apply the
  fix for the one inline finding.
- **Scope:** `credit-service/`. PR review found the purge's
  cron live in every test context, unlike the other two scheduled
  components. Following the reviewer's suggestion and the existing
  pattern: `OutboxRetentionScheduler` gains
  `@ConditionalOnProperty(credit.outbox.retention.enabled, matchIfMissing
  = true)` and its "off in tests" javadoc line; `application-test.yaml`
  sets it to `false`; the service's `AGENTS.md` and README note it.
  Deployed behaviour is unchanged (the property is unset there).
- **Verification:** full credit-service suite (`./mvnw test`): 268
  tests, 0 failures, 0 errors, 0 skipped.
- **Author review:** Ryan Ang, pending pull request review.

## 2026-10-19 — Ryan Ang (credit-service: outbox retention purge, issue #218)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Source:** the team's decisions on issue #218: only sent rows are
  purged, the window counts from `sent_at`, 30 days and 03:00
  Asia/Singapore as `CREDIT_OUTBOX_RETENTION_DAYS` /
  `CREDIT_OUTBOX_PURGE_CRON`, a partial index on `sent_at`, a new
  migration, the window refused below 1 at startup, and nothing but
  `outbox_event` purged.
- **Scope:** `credit-service/`, `docs/credit-service.md`, `compose.yaml`,
  `.env.example`.
  - `V3__outbox_event_sent_index.sql`: partial index on
    `outbox_event (sent_at) where sent_at is not null`.
  - `OutboxEventRepository.deleteSentBefore`: one bulk delete of the
    sent rows before a cutoff.
  - `service/OutboxRetentionScheduler`: daily cron in Singapore time,
    cutoff = now minus the window; a window below 1 fails construction.
    Follows notification-service's `RetentionPurgeScheduler`.
  - `application.yaml`, `compose.yaml`, `.env.example`: the two settings.
  - Tests: `OutboxEventRepositoryTest` (3 new, real Postgres: sent before
    the cutoff deleted, at or after it kept, unsent never deleted, the
    V3 index present) and `OutboxRetentionSchedulerTest` (3: the cutoff,
    0 and -1 refused).
  - Docs: D13 in the design doc (with the team's reasons, including why
    the ledger tables are kept unlike Notification's `processed_events`),
    the components, the `outbox_event` row and a failure-table row; the
    service's `AGENTS.md` and README; the README AI Use Summary.
- **Prompt(s):** Implement credit outbox purge, following schedule of Notification 
    Service.
- **Verification:** full credit-service suite (`./mvnw test`,
  Testcontainers Postgres and RabbitMQ): 268 tests, 0 failures, 0
  errors, 0 skipped. `docker compose config` renders both settings;
  `git diff --check` clean.
- **Author review:** Ryan Ang, pending pull request review.

## 2026-10-18 — Aung Ko Khant (order-service: hide past-deadline requests)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** `GET /requests` lists only CREATED requests whose deadline
  is still ahead (`RequestSpecifications.after`); `RequestsService.accept`
  refuses a request past its deadline with 409 "The request's deadline
  has passed"; tests (97 passing); README, `endpoints.txt`.
- **Prompt(s):** "For read endpoints, can we hide requests that are
  already past their deadline from couriers?" The expiry sweep runs
  every 5 min, so such requests stay CREATED until it does. The tool
  asked as neutral options; the author chose: the open-errands list only
  (a courier's own deliveries unchanged), and accept refused with 409.
- **Author review:** `mvn test` in a Maven container: 97 tests, 0
  failures. To be reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: list filters and sort)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** new `RequestFilter` (query-string record) and
  `repository.RequestSpecifications`; `RequestsRepository` now a
  `JpaSpecificationExecutor` (the four JPQL list queries removed);
  `RequestsQueryService` combines the base query with the filters,
  checks the ranges and the sort; `RequestsController` binds the filter
  on all three lists and answers a wrong-typed value with 400; tests
  (95 passing); README reads section; `endpoints.txt`.
- **Prompt(s):** "for our list endpoints, could we add filtering?" The
  tool listed candidate filters as neutral options; the author chose
  supplier, reward range, deadline and created ranges, a sort parameter
  (createdAt / deadline), on all three lists (not a multi-state filter).
  Range rules (lower bound inclusive, upper exclusive, amounts at least
  1, an offset on instants) follow credit-service's history filters.
- **Author review:** `mvn test` in a Maven container: 95 tests, 0
  failures. Through the gateway: minCredit, sort=deadline and a +08:00
  createdFrom filtered as expected; minCredit=abc was a 400. To be
  reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: read endpoints)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** `GET /requests`, `/requests/{id}`, `/requests/mine`,
  `/users/{id}/requests` in `RequestsController`; new
  `RequestsQueryService` (read-only), `dto.RequestSummary`,
  `dto.RequestDetail`, `dto.PageResponse` (copied from credit-service),
  four paged repository queries; `SecurityConfig` limits the admin view
  to ADMIN / OWNER with `RestAccessDeniedHandler` (copied from
  credit-service) for the 403; tests `RequestsReadEndpointsTests` (90
  passing in all); README reads section; `endpoints.txt`.
- **Prompt(s):** "How should we set up read endpoints?" — the tool
  declined to design the interface, listed the requirements, the other
  services' conventions and the open decisions. The author then gave the
  design (paths, what each returns, who may call it: open errands for
  any user, detail for the requester / assigned courier / ADMIN / OWNER,
  /mine by role, the admin view for ADMIN / OWNER). The tool asked the
  remaining details as neutral options; the author chose: GET /requests
  is CREATED only (any other state 400); lists trimmed, the detail full;
  newest first, page size 20; the admin view covers both roles. The
  page / size limits (0+, 1-100) follow credit-service's history.
- **Author review:** `mvn test` in a Maven container: 90 tests, 0
  failures. Through the gateway with the rebuilt container: open
  errands, /mine with a state, the requester's detail 200, a
  non-party's detail 404, the admin view 403 for an ordinary user. To be
  reviewed via pull request.
## 2026-10-18 — Leong Wei Zhi (PR #215 bot-review round)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor + docs
- **Source:** the three @claude bot comments on PR #215.
- **Scope:** `useLocalStorage`'s cross-tab `storage` listener became
  opt-in (`{ sync: true }`, used only by the sound preference) — the
  hook-wide listener had silently made the `'user'` session key swap a
  tab's account live when another tab logged in, a behaviour change
  beyond this PR with no test; the listener's `defaultValue` fallback
  now reads through a ref so an object-literal default cannot
  re-subscribe every render; a new `notifications.test.tsx` case pins
  that another tab's write of `'user'` is NOT adopted. The PR
  description no longer promises background-tab chimes (the visibility
  gate is deliberate: the visible tab speaks for all open tabs).
- **Prompt(s):** "address the latest pr comments from claude bot".
- **Author review:** Leong Wei Zhi to review via the PR; `npm test`
  (273 passing), lint, format:check, build all clean.

## 2026-10-18 — Leong Wei Zhi (PR #215 review fixes)
- **Tool:** Claude Code (Fable 5)
- **Mode:** debug + refactor
- **Source:** all six items are @Sinnez1's PR #215 review comments.
- **Scope:**
  - **`chime.ts`:** a policy-blocked `resume()` is no longer awaited —
    its promise stays pending until a user gesture, so stacked awaits
    all released and chimed together on the first click (the blocking
    item); a context that reached `closed` is replaced, not reused; the
    context suspends itself ~0.6 s after a chime so the audio thread
    idles, with an `idled` flag so that self-suspension is woken by the
    next chime rather than mistaken for an autoplay block; new
    `unlockChime()` creates/resumes the context inside a gesture for
    iOS Safari.
  - **`NotificationProvider.tsx`:** the chime keys on an arrival
    counter bumped before the body parse (a frame that fails
    `JSON.parse` bumps the badge, so it chimes too); only the visible
    tab chimes; the first `pointerdown` hands its gesture to
    `unlockChime`.
  - **`useLocalStorage.tsx`:** a `storage` listener adopts another
    tab's write of the key, so a mute in one tab lands everywhere
    (noted: this also syncs the `'user'` session across tabs).
  - Tests follow in `chime.test.ts` (never-settling fake `resume`,
    no-stack-on-unlock, closed-context replacement, idle/wake,
    unlockChime) and `notifications.test.tsx` (malformed frame chimes,
    hidden tab quiet, cross-tab mute, pointerdown unlock).
- **Author review:** Leong Wei Zhi to review via the PR; `npm test`
  (271 passing), lint, format:check, build all clean.

## 2026-10-18 — Leong Wei Zhi (notification sound, follow-up to issue #66)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `web/` — a bell chime on live STOMP notification arrivals
  with a client-stored mute: new `features/notifications/chime.ts` (Web
  Audio two-tone synth, every blocked/muted path silent);
  `NotificationProvider` keys the chime on the pushed queue's identity
  (live arrivals only — REST seeds, reconnect catch-ups and the logout
  reset stay silent; a burst batched into one render chimes once) and
  holds the localStorage-backed toggle (`notificationSoundEnabled`,
  default on) in the context; toggle surfaces in the Notification
  Center header (`aria-pressed` icon button) and a new profile
  Preferences card (`role="switch"`); `useLocalStorage.setValue`
  memoized so the context `useMemo` holds; tests in `chime.test.ts`
  plus new cases in `notifications.test.tsx`,
  `notification-center.test.tsx`, `profile.test.tsx`; wireframe
  deviation rows in `web/AGENTS.md`.
- **Prompt(s):** "i want to have a bell sound on the web everytime a
  notification is arrived, the sound can be disabled by the client (the
  setting should be stored on the client), also if the external system
  like os or web browser tab disables sound, then the notification
  should be silent" — surface (both toggles) and sound source (Web
  Audio synthesis, no binary asset) chosen by the author via options
  Q&A.
- **Author review:** Leong Wei Zhi to review via the PR; `npm test`
  (263 passing), `npm run lint`, `npm run format:check`, `npm run
  build` all clean. Known limit flagged in the PR: a push arriving
  before the first user gesture after a cold page load may be silent
  (browser autoplay policy; accepted, no workaround added).

## 2026-10-18 — Leong Wei Zhi (PR #214 review round)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor + docs
- **Scope:** all three findings of @Sinnez1's PR #214 review:
  `order-service/README.md`'s "DROP publishes nothing" line updated to
  `request.dropped` (blocking); the `RequestsService` header's older
  "DROP still publishes nothing" line marked "(until request.dropped,
  below)"; `RequestEvents.dropped` now takes the dropping courier as a
  parameter (the reviewer's snippet), so the enqueue no longer depends
  on running before the courier fields are cleared.
- **Prompt(s):** "address pr comments and reply to each comment
  individually".
- **Author review:** to review via PR #214. `RequestsServiceTests`
  rerun 36/36.

---
## 2026-10-18 — Leong Wei Zhi (request.dropped notification)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** a courier dropping an accepted request now notifies the
  requester. New `request.dropped` contract (`RequestDropped` record,
  registry entry, `request-dropped.example.json` fixture, count test
  bumped to 12); `RequestsService.drop` enqueues
  `RequestEvents.dropped` before clearing the courier fields;
  order-service tests (`everyStepWritesItsEvent` renamed and extended,
  new `dropWritesRequestDroppedForTheRequester`); web template in
  `eventMessage.ts` + Notification Center test cases; event-catalog
  paragraph in `docs/notification-service.md` and the F5.2 row in
  `docs/credit-service.md`. Notification- and credit-service code
  untouched (`request.#` binding and field-driven name enrichment pick
  the type up; credit-service binds explicit keys and never sees it).
- **Prompt(s):** "we dont have a notif if courier drops a request
  after accepting. make one"; decisions via options Q&A: event name
  `request.dropped`, parties = the requester only, one PR across
  contracts + order-service + web.
- **Author review:** to review via pull request. Verified:
  foc-contracts 11/11, order-service `RequestsServiceTests` 36/36,
  notification-service 94 tests with `DomainEventContractTest`
  validating the new fixture, web vitest suite. The 5 order-service /
  1 notification-service `internalRabbitListenerEndpointRegistry`
  context-restart errors reproduce on a clean `origin/main` worktree
  under local Java 26 (known environmental issue, not this change).

---
## 2026-10-18 — Ryan Ang (PR #212 review follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** docs + code comments
- **Source:** every item below is a suggestion from 
  PR #212 review.
- **Scope:**
  - **Credit F2.1.3** (`docs/credit-service.md`): the traceability
    row's left column restored to the requirement as stated ("reject
    creation when available < reward"), keeping the reworded
    implementation column; Credit F2.1.3 put back next to Order F1.1.4
    in "Requirements to revise", and the "has been revised" sentence
    removed. The requirement itself is left for the team.
  - **Resent reply wording:** comments in `ReserveResult`,
    `RequestEventHandler`, `CreditOperationsService` and two test
    headers, the `request.submitted` handler row and the README now say
    the reply is rebuilt from the event with the record's decision,
    not "the recorded reply"; the rejection reason is described as
    worked out from the recorded reward.
  - **Redelivery failure row:** names the two cases in which the
    resend comes into play (a crash between commit and ack, an operator
    replaying the submission), since the outbox never drops a reply.
  - **Comments:** `CreditOperations.reserve` javadoc states its result
    per existing record status; the `REJECTED` case in
    `applyReserve` points at the `V1__baseline.sql` invariant;
    `DomainEvent.parties()` (foc-contracts) states that only credit
    events may name no one.
  - README AI Use Summary paragraph updated and its grammar fixed.
  - Not done: storing the rejection reason on the row (a migration the
    reviewer left optional; a team decision), and the PR body's
    "Closes" line (author's choice).
- **Prompt(s):** Asked to read the PR #212 review comments, then to
  review the blocking item and all notes except the PR-body one.
- **Verification:** comment and doc changes only. foc-contracts
  `./mvnw install` (with its tests), credit-service main and test
  compile, and notification-service `DomainEventContractTest` all pass;
  `git diff --check` clean.
- **Author review:** Ryan Ang, pending pull request review.

## 2026-10-18 — Ryan Ang (issue #165 follow-ups from Leong Wei Zhi's PR #163 re-review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** implementation + tests + docs
- **Source:** every item below was found by Leong Wei Zhi in the
  PR #163 re-review and tracked in issue #165; the fixes are the
  reviewer's suggestions, taken as written by team decision.
- **Scope:**
  - **Duplicate `request.submitted` on `HELD` / `REJECTED`:** it
    answered nothing, so a reply lost after the commit (for example a
    dead-lettered `credit.reserved`) could never be recovered and the
    Order Service's give-up timer would cancel a request whose credits
    had reserved fine. `ReserveResult` gains `ALREADY_RESERVED`,
    `ALREADY_REJECTED_INSUFFICIENT_CREDITS` and
    `ALREADY_REJECTED_INVALID_AMOUNT` (the reason rebuilt from the
    recorded reward, since only a reward below 1 is refused as
    invalid); `RequestEventHandler` resends `credit.reserved` or
    `credit.reservation-rejected` for them. `DUPLICATE` now means a
    settled record (`TRANSFERRED` / `RELEASED`), which owes no reply.
    Tests: operations, handler and listener integration tests updated
    or added. `docs/credit-service.md`: the `request.submitted` row of
    "How each event is handled" and two failure rows.
  - **`DomainEventContractTest`** (notification-service): `parties`
    must be non-empty for request events again; only credit events may
    have none.
  - **Diagram legend** in `docs/credit-service.md`: no longer claims
    the same notation as `architecture.md`, and states the two
    differences (`-->` covers JDBC, `---` unused).
  - **Credit F2.1.3** (the credit half of #165's "Requirements to
    revise" item): the traceability row in `docs/credit-service.md`
    reworded to describe what is implemented (refused as `REJECTED`,
    rejection published, request moves `pending` → `rejected`, same
    rejection on redelivery), and Credit F2.1.3 dropped from
    "Requirements to revise". Transcription of the existing saga
    design; no new behaviour chosen. The Order requirements (F0.1,
    F1.1, F1.1.4, NFR1.1) left for the Order owner.
- **Prompt(s):** Asked to fix the duplicate-reply item using the
  reviewer's suggestion and to explain the problem, then to continue
  with the remaining #165 follow-ups, then to update the credit-side
  requirement to match the implementation and leave the Order ones.
- **Verification:** credit-service `./mvnw test` with Docker running:
  262 tests, 0 failures, 0 skipped. notification-service
  `DomainEventContractTest`: 2 tests, 0 failures.
- **Author review:** Ryan Ang, pending pull request review.

## 2026-10-16 — Alastair Tan (order-service frontend pages)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** generate
- **Scope:** web/ only — `src/features/order/` (types, status helpers, API client, dev-only in-memory mock, components), `src/features/credit/api.ts` (balance read), and the pages `my-requests`, `create-request`, `open-requests`, `active-delivery`, `request-history` plus their route entries in `src/routes/index.tsx`, built from `web/docs/wireframes/`. No backend files touched. The order-service endpoint paths and field names in `features/order/api.ts` and `types.ts` are placeholders pending the order-service owner's contract; the mock (`VITE_MOCK_ORDER_API=true`, dev server only) is temporary.
- **Prompt(s):** "build the frontend pages for this service and run the UI to let me take a look at it; backend no need to be touched, we only work on the frontend."
- **Author review:** Pending — author to review the diff via the PR, and the API contract with the order-service owner.
## 2026-10-18 — Aung Ko Khant (order-service: accepted/collected events, expiry and auto-complete)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** `RequestsService.accept` / `.collect` write
  `request.accepted` / `request.collected` (`RequestEvents`, parties =
  requester and courier); new `DeadlineExpirySweeper` (CREATED / ACCEPTED
  past the deadline -> EXPIRE, F6.1-F6.2) and `AutoCompleteSweeper`
  (ARRIVED longer than the delay -> `RequestsService.completeAutomatically`,
  F7.2), modelled on `PendingTimeoutSweeper`; two repository queries;
  `order.expiry.*` / `order.auto-complete.*` settings, compose and
  `.env.example` variables, scheduler pool 2 -> 4; `endpoints.txt`
  brought up to date with the API; README saga section; tests (82
  passing). Also committed the earlier `/create` field-check tests.
- **Prompt(s):** "Can you perform the ones that only require small
  decisions" (from the remaining-tasks list). The tool asked as neutral
  options; the author chose: both sweeps every 5 min; auto-complete after
  1 h, configurable; accepted / collected notify requester and courier;
  update `endpoints.txt` and push.
- **Author review:** `mvn test` in a Maven container: 82 tests, 0
  failures. Live run with the intervals shortened through environment
  variables: a request past its deadline became EXPIRED and an ARRIVED
  one COMPLETED within 25 s, with request.accepted / collected / expired /
  completed all sent; the container was then restarted on the defaults.
  To be reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: create field checks)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** `RequestsService.create` checks a new request before storing
  it: the NOT NULL columns present (blank text counts as missing;
  `supplierName` optional), `creditAmount` at least 1 (F1.1.3), `deadline`
  in the future (F1.1.2); every problem in one `InvalidRequestException`
  (new), which `RequestsController` answers 400 with the message. Tests
  (78 passing); README endpoints paragraph. Then `supplierName` made
  required in the check too ("Can we have suppliername field be required
  for now?"); its column stays nullable.
- **Prompt(s):** "When creating a request, can we check that all required
  fields are there and raise an error explicitly if not instead of
  silently failing" (a missing field was a bare 500). The tool asked as
  neutral options; the author chose: required = the database's NOT NULL
  columns (supplierName optional); 400 listing every missing field; also
  check the reward and deadline values in the same 400.
- **Author review:** `mvn test` in a Maven container: 78 tests, 0
  failures. Rebuilt container through the gateway: missing fields 400 with
  the list, reward 0 and a past deadline 400, a valid create 200. To be
  reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: order-e2e.ps1)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** new `order-service/order-e2e.ps1`: logs in a requester and
  a courier (real user-service accounts, passwords prompted hidden),
  creates requests through the gateway, gets them reserved (credit-service
  within 5 s, else `credit-stub.ps1`), and checks 11 steps against the
  ownership rules (409 self-accept, 404 other party, 401 no token, 200
  owner); README section.
- **Prompt(s):** "How can I manually test user verified order service",
  then "Could we write this as a script".
- **Author review:** run by the tool against the compose stack with two
  throwaway accounts it created in the local user-db through sign-up and
  Mailpit (e2e_requester, e2e_courier): 11 of 11 checks passed. The
  first run showed this branch's credit-service never reserves (no
  request-event listener before main's), hence the stub fallback. To be
  reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: scripts call as users)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** split from the 2026-10-18 ownership-checks entry:
  `ManualRequestsHarness` acts as each step's party; `order-curl.ps1`
  signs a bearer token like user-service's (HS256 with `.env`'s
  `JWT_SECRET`; sub, role, jti, iat, exp) for the step's party, or for
  `-As`, and no longer sends `requesterId` / `courierId`.
- **Prompt(s):** part of "I would like to add owner ship checks ..."
  (the endpoints now need a token).
- **Author review:** `order-curl.ps1` run through the gateway against the
  rebuilt container (owners 200, other party 404, self-accept 409). To be
  reviewed via pull request.

## 2026-10-17 — Aung Ko Khant (order-service: credit and user stubs)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** new `order-service/credit-stub.ps1` (sends `credit.reserved`
  / `credit.reservation-rejected` for one request to `credit-events`
  via the RabbitMQ management API, values read from order-db; warns if
  credit-service is running or the request is not PENDING; prints the
  resulting status), new `order-service/stub-users.psd1` (alice 1, bob
  2, carol 3, dave 4), `order-curl.ps1` `-Requester` / `-Courier` take a
  name or an id (courier default now bob instead of 7), README section.
- **Prompt(s):** "can we create a stub for credit service and user
  service?" The tool asked as neutral options; the author chose: for
  testing order-service alone; the credit stub replies only when told
  (reserve/reject per call); the user stub is a fixed user list.
- **Author review:** run against the compose stack with credit-service
  stopped: reserve moved a request PENDING -> CREATED and accept then
  succeeded; reject moved one to REJECTED and a later reserve was
  ignored. A first run failed with RabbitMQ `not_json` (Windows
  PowerShell piped a BOM); fixed by sending the body from a BOM-less
  file. To be reviewed via pull request.

---

## 2026-10-17 — Aung Ko Khant (gateway: /api/order route)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** debug
- **Scope:** `gateway/nginx.conf` — the reserved `/api/order/` 503 stub
  replaced with a proxied location identical in form to credit's (auth
  check, prefix stripped, `order-service:8080`); `docs/gateway.md`
  routing-table row; `order-service/order-curl.ps1` default `BaseUrl`
  now `http://localhost:8090/api/order`.
- **Prompt(s):** `./order-curl create` failed with "Failed to connect to
  localhost:8088". Cause: order-service lost its host port in the
  gateway cutover (G3), and the gateway still answered the stub. The
  stub's own comment and G3 reserved the prefix for this replacement,
  so no new decision was made.
- **Author review:** gateway rebuilt with compose; `./order-curl.ps1
  create` returned 200 "Request 1 created" through the gateway. To be
  reviewed via pull request.

---

## 2026-10-13 — Aung Ko Khant (order-service: manual-testing scripts)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** `order-service/order-event.ps1` and `order-curl.ps1`
  (new), `ManualRequestsHarness` (interactive mode),
  `order-service/README.md` manual-testing sections. Later extended:
  reserve / reject / timeout (2026-10-15) and arrive (2026-10-16).
- **Prompt(s):** split from the 2026-10-13 RequestsController entry.
  After asking how to test the transitions against the real database
  and view the rows in DBeaver, "Can you create a script?": the tool
  wrote `order-event.ps1`, which runs the existing
  `ManualRequestsHarness` in a Maven container against the compose
  `order-db`. Then "is it possible for it to stay on until I am done":
  the tool added an interactive mode to the harness
  (`-Dinteractive=true`) and made the script start it when run with no
  event. Then "Can I test the endpoints using a curl script?": the
  tool wrote `order-curl.ps1`, which POSTs to one endpoint with curl
  and prints the status and body.
- **Author review:** the interactive mode was run in the Maven
  container against `order-db` with piped commands; `order-curl.ps1`
  was run against the compose order-service (create, accept, collect,
  complete, plus a 409 and a 404). To be reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: ownership checks)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service bearer-token auth copied from credit-service
  (`security.JwtVerifier`, `JwtAuthenticationFilter`,
  `RestAuthEntryPoint`, `config.SecurityConfig` with per-service CORS
  allowing POST; `spring-boot-starter-security`, jjwt 0.13.0;
  `order.jwt.secret` / `order.web-allowed-origin`; `JWT_SECRET` and
  `WEB_ALLOWED_ORIGIN` passed in compose). `RequestsController` takes
  the caller from the token: `/create` makes them the requester and
  `/accept` the courier (`requesterId` / `courierId` removed from the
  bodies). `RequestsService`: collect, arrive and drop only by the
  assigned courier; cancel and complete only by the requester; checked
  before the state, anyone else gets the unknown-request 404. Tests
  (73 passing) sign tokens with the test secret; README endpoints
  section. The manual-testing scripts' token support follows in a later
  commit.
- **Prompt(s):** "I would like to add owner ship checks. Users should
  only be able to add requests for themselves. All steps in the
  lifecycle should only be able to be performed by a single person."
  The token mechanism follows the existing services (credit-service,
  supplier-service: shared `JWT_SECRET`, `sub` = user id). The tool
  asked as neutral options; the author chose: requester = create (for
  self), cancel, complete; assigned courier = collect, arrive, drop;
  accept = any user but the requester, no admin override; ids taken
  from the token instead of the body; a step that is not the caller's
  answers 404.
- **Author review:** `mvn test` in a Maven container: 73 tests, 0
  failures. Run through the gateway against the rebuilt container:
  self-accept 409, other party's collect / complete 404, the owners'
  steps 200, no token 401. To be reviewed via pull request.

## 2026-10-18 — Aung Ko Khant (order-service: no self-accept)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `RequestsService.accept` refuses a courier id
  equal to the request's requester id (IllegalStateException, so the
  controller answers 409 and the transaction rolls back); tests in
  `RequestsServiceTests` and `RequestsControllerTests` (67 passing);
  README endpoints paragraph.
- **Prompt(s):** "Is there a check for whether courier and requester
  are the same person?" (there was none). The tool asked how a refusal
  should answer (409 / 403 / 400) as neutral options; the author chose
  409 Conflict.
- **Author review:** `mvn test` in a Maven container: 67 tests, 0
  failures. To be reviewed via pull request.

## 2026-10-16 — Aung Ko Khant (order-service: ARRIVED state)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `RequestsService
  .arrive` (sets `arrivedAt`, writes `request.courier-arrived` to the
  outbox), `RequestEvents.courierArrived`, `POST /arrive` in
  `RequestsController`, the `Requests` status comment, `endpoints.txt`,
  the README, and tests (57 passing). The ARRIVED constant in
  `OrderStates` was the author's own edit; the state-machine part
  (ARRIVE, COLLECTED -> ARRIVED -> COMPLETED) was committed on its own
  earlier, and the harness, `order-curl.ps1` and `order-event.ps1`
  changes follow in a later commit.
- **Prompt(s):** "I would like to add an arrived state between
  collected and completed, the point is to inform requester that
  courier has arrived". The tool asked the open choices as neutral
  options; the author chose: COMPLETE only from ARRIVED (no direct
  COLLECTED -> COMPLETED); ARRIVE publishes `request.courier-arrived`
  (the existing foc-contracts record) now; `parties` = the requester
  only. `/arrive` takes the same `{requestId}` body as `/collect`.
- **Author review:** `mvn test` run in a Maven container: 57 tests,
  0 failures. Not yet run against a live broker or notification-service.
  To be reviewed via pull request.

## 2026-10-13 — Aung Ko Khant (order-service: RequestsController)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, explain
- **Scope:** order-service `foc.order.controller.RequestsController`
  (new; six POST endpoints) and `RequestsControllerTests` (new);
  `order-service/README.md`; README AI Use Summary. The same session's
  `order-event.ps1`, `order-curl.ps1` and harness interactive mode are
  logged with the manual-testing tools in a later commit.
- **Prompt(s):** "How can I start adding endpoints", "Treat order
  service as its own isolated microservice for now" (explanations
  only; the tool declined to pick routes or request/response shapes
  and listed them as the author's decisions), then "Can you add a
  method for each event for me to fill out?". The tool wrote a
  `@RestController` with one unmapped, empty stub method per
  `OrderEvents` constant, each commented with the `RequestsService`
  method it corresponds to. No routes, HTTP methods, parameters,
  DTOs or status codes were chosen by the tool. Then "For the create
  event, I would like a row to be added to db based on the
  parameters supplied to create method, status should be created":
  the tool filled in `create()` to take the entity's caller-supplied
  columns as parameters, build a `Requests` and store it through the
  existing `RequestsService.create` (which sets status `CREATED`).
  The method is still unmapped. Then "fill out stub methods with
  corresponding method from request service. Modify method
  signatures as needed": the tool made `accept`, `collect`,
  `cancel`, `drop`, `expire` and `complete` each delegate to the
  `RequestsService` method of the same name, taking that method's
  parameters and returning its `Requests` result, and fixed the
  compile errors in the author's in-progress edits (`void` methods
  returning a value, a `requestService` typo, and a
  `setStatus('CREATED')` line, removed because
  `RequestsService.create` already sets the status). Still unmapped.
  Not compiled or run by the tool (no JDK or Docker available in the
  session). Then "Can you help me add the endpoints": the
  tool declined to pick the interface and listed what it needed; the
  author wrote `endpoints.txt` (paths and parameters for create,
  accept, collect, drop, cancel, complete) and asked "Can we create
  these endpoints?". Four points the file left open were put to the
  author as neutral-options Q&A, and the author chose: POST with a
  JSON body; `creditAmount` and `deadline` added to `/create` (both
  are NOT NULL columns); a JSON `{"message": ...}` response with 200
  / 409 (illegal transition) / 404 (unknown id); endpoints only, no
  event publishing in this change. The tool mapped the six
  controller methods accordingly (`expire` left unmapped), added
  `RequestsControllerTests`, and ran the full order-service suite in
  a Maven container (25 tests, all passing).
- **Author review:** Stubs are to be filled out by the author, who
  decides the interface; reviewed via pull request.

## 2026-10-17 — Aung Ko Khant (order-service: request.cancelled / request.expired)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `RequestsService.cancel` and `.expire` write
  `request.cancelled` / `request.expired` to the outbox with the state
  change; `RequestEvents.expired` (new) and the existing `cancelled`
  builder; a comment on `drop` that it must not publish (Order F5.2);
  README saga section; tests (65 passing): each event's fields and
  parties, and no event from a refused cancel.
- **Prompt(s):** "Can we add events for expire and cancel, these should
  release credits back to requester". The Credit Service already
  releases on both (docs/credit-service.md D9). The tool noted that the
  foc-contracts `RequestExpired` record has no `courierId` and
  describes expiry before acceptance, while the state machine also
  expires ACCEPTED requests, and asked who goes in `parties`. The
  author chose: expired = requester plus the courier when one had
  accepted; cancelled = requester only.
- **Author review:** `mvn test` in a Maven container: 65 tests, 0
  failures. Not yet run end to end against credit-service. To be
  reviewed via pull request.

## 2026-10-17 — Aung Ko Khant (order-service: request.completed)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `RequestsService.complete` writes
  `request.completed` to the outbox in the same transaction as the
  COMPLETED status; `RequestEvents.completed` builds it from the
  existing foc-contracts `RequestCompleted` record; README saga
  section; tests (62 passing): the event's fields, the event sequence,
  and no event when COMPLETE is refused.
- **Prompt(s):** "I would like to publish an event when an order is
  completed successfully, to signal to credit service to release
  credits to courier". The Credit Service already transfers on
  `request.completed` using `requestId` and `courierId`
  (docs/credit-service.md D9). The tool asked as neutral options who
  goes in `parties`; the author chose requester and courier.
- **Author review:** `mvn test` in a Maven container: 62 tests, 0
  failures. Not yet run end to end against a live broker and
  credit-service. To be reviewed via pull request.

## 2026-10-16 — Aung Ko Khant (order-service: status enum)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `Requests` (`status` is now `OrderStates` with
  `@Enumerated(EnumType.STRING)` and a hand-written
  `check (status in (...))` in its text column definition),
  `RequestsService`, `CreditReplyHandler`, `PendingTimeoutSweeper`,
  `RequestsRepository` (enum instead of names), and tests (60 passing):
  a save without `createdAt`, a bad status rejected by the database,
  and every `OrderStates` constant accepted by it. The `createdAt`
  default from the same session was committed on its own earlier.
- **Prompt(s):** "Is it enforced that status must be one
  of the enums we defined" (it was not) and "I would like the states to
  be an enum". `columnDefinition = "text"` stopped Hibernate generating
  its check constraint; the tool asked as neutral options (drop the
  column definition / write the check into it / Java-only) and the
  author chose the hand-written check.
- **Author review:** `mvn test` in a Maven container: 60 tests, 0
  failures. Under `ddl-auto: update` an existing order-db `requests`
  table does not gain the check until it is recreated. To be reviewed
  via pull request.

## 2026-10-15 — Aung Ko Khant (order-service: saga with the Credit Service)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `RequestsService` (create starts PENDING; reserve/reject/timeout),
  new `CreditReplyHandler`, `PendingTimeoutSweeper`, `RequestEvents`,
  `EventOutbox`, `EventPublisher`, `OutboxPublisher`, `OutboxEvent` +
  repository, `messaging.rabbitmq` (converter, topology, initializer,
  `RabbitEventPublisher`, `CreditEventsListener`), configuration,
  compose/`.env.example` variables, the order-service README, and
  tests (54 passing). The state-machine part (PENDING, REJECTED and
  their events) was committed on its own earlier; the harness and
  `order-event.ps1` changes follow in a later commit.
- **Prompt(s):** "I think we need to add 2 additional stages pending
  and rejected to coordinate with credit service. Pending is to check
  if there are enough credits for requester and to reserve it, rejected
  is in the event that there are not enough". The saga itself was
  already the team's design (`docs/credit-service.md` D7–D8, D10, and
  "What the other services must build"). The tool asked the open
  order-side choices as neutral options; the author chose: event names
  RESERVE / REJECT / TIMEOUT; only those leave PENDING; scope including
  the broker wiring; only the four saga transitions publish; copy
  credit-service's outbox and retry layout with its values; field
  mapping pickupLocation = supplierName, note = item; correlationId =
  the request id; 30 s pending timeout swept every 15 s.
- **Tool's readings, for the author to confirm:** a reply naming no
  known request is dead-lettered at once (credit-service D11's rule
  for events that conflict with the recorded state); queue names
  `order-service.credit-events[.retry|.dlq]` follow credit-service's
  consumer-prefixed convention; the listener's retry republish waits
  on a correlated confirm (the outbox needs correlated confirms on the
  same connection, where notification-service uses simple ones).
- **Author review:** `./mvnw test` run in a Maven container: 54 tests,
  0 failures. Not yet run against a live broker or credit-service. To
  be reviewed via pull request.

## 2026-10-12 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, explain
- **Scope:** order-service `foc.order.RequestsService` (body of the
  author's empty `@Service` stub) and `RequestsServiceTests`;
  order-service README status paragraph; README AI Use Summary.
- **Prompt(s):** "So how do we add transitions to this file?", "How do
  I wire this to db?", "Which existing module's responsibility is it
  to change the db?" (explanations only), then "Can you populate
  requestservice". The tool wrote one method per `OrderEvents`
  constant: load via `RequestsRepository`, ask `OrderStateMachine`
  for the next state, set the status and the entity's existing
  courier/timestamp columns. The class was named `RequestsService` to
  match the author's file name (the stub's class was `RequestService`,
  which does not compile in `RequestsService.java`). The `status`
  field was left as a `String`; the tool raised String-vs-enum as the
  author's choice and changed nothing in the entity. No validation,
  ownership checks, error mapping or event publishing were added.
- **Author review:** Integration tests against in-memory H2 (each
  event's stored status and columns; illegal event leaves the row
  unchanged); `mvn test` passes (19 tests). Reviewed via pull request.
## 2026-10-17 — Leong Wei Zhi (issue #137: purge processed_events past retention)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** notification-service — `ProcessedEventRepository`
  (`deleteByProcessedAtBefore`, bulk `@Modifying @Query` mirroring the
  PR #81 notification purge pattern), `ProcessedEvent`
  (`idx_processed_events_processed_at`), `RetentionPurgeScheduler`
  (same run/cutoff also sweeps the dedupe table, one combined log
  line), tests (`ProcessedEventRepositoryTest` new,
  `RetentionPurgeSchedulerTest` extended), and the doc set
  (design doc D4/D9/components/trace/config, service README, AGENTS.md).
- **Prompt(s):** Asked to plan and resolve issue #137. Two
  implementation details were author-decided via neutral options Q&A:
  purge cutoff reuses the 30-day `NOTIF_RETENTION_DAYS` window (vs 2×
  retention or a separate property), and `processed_at` gets an index
  (vs matching the unindexed notifications purge).
- **Author review:** `./mvnw clean test` — the new tests pass; the two
  `ApplicationContext` restart errors (AMQP listener bean under local
  Java 26) reproduce identically on unmodified main. Pending pull
  request review.
- **PR #201 review round (Codex, 2026-10-17):** two findings assessed,
  no code change. The queued-redelivery dedupe gap is the same accepted
  ≥30-day-outage class as the issue's DLQ-replay path — now named
  explicitly in the scheduler javadoc and the design doc's D4 note. The
  run-off-the-heartbeat-scheduler suggestion was rejected as
  pre-existing #68 design (documented in `WebSocketStompConfig`), with
  the indexed daily delete far below the 10 s heartbeat interval at
  this scale.
- **main merge (2026-10-17):** merged `origin/main` (through PR #195)
  into the branch to clear the PR #201 conflict. Three docs conflicted,
  all against #66's notification work: `ai/usage-log.md` (both sides
  appended at the top — kept every entry), the design doc and the
  service README (disclosure headers kept both lines; the F3.4 trace
  row and the Status line take #66's newer REST wording with the
  `processed_events` sweep folded in). No code conflict. `./mvnw clean
  test`: 94 run, 1 error — the `RetentionPurgeSchedulerTest`
  `ApplicationContext` AMQP failure, which unmodified main reproduces
  identically (92 run, same error); both purge tests pass 2/2 in
  isolation.
- **main merge, round 2 (2026-10-17):** merged `origin/main` (now
  through PR #199, #209 and #210) to clear the PR #201 conflict again.
  One conflict this time, `ai/usage-log.md` — both sides had appended
  at the top of the entry list, so the resolution keeps every entry
  from each side with this branch's entry first, matching the earlier
  merge. Verified by heading count: 144 on the branch + 151 on main
  with 143 shared gives the 152 the merged file has, and no heading
  from either parent is missing. No code conflict and no doc conflict:
  the design doc and the service README now carry only this branch's
  own edits over main. `./mvnw clean test`: 94 run, 1 error — the same
  `RetentionPurgeSchedulerTest` `ApplicationContext` AMQP failure that
  unmodified main reproduces under local Java 26; the four purge tests
  pass 4/4 in isolation.

## 2026-10-17 — Ryan Ang (PR #210 review: upcoming credit expiries)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor + tests + docs
- **Scope:** Leong Wei Zhi's review of PR #210 (issue #196), and a merge
  of `main` (#202's order-service code) whose only conflict, this log,
  was resolved by keeping both sides.
  - `web/src/features/credit/CreditHistoryPage.tsx` and `api.ts`: the
    expiries are read on page load and on every balance poll, keyed on
    the poll's `version` instead of `available`/`reserved`. The test
    that pinned the old trigger is replaced by one with the reviewer's
    case: the same balance, a different lot set, and the line and
    notice update on the next poll.
  - `CreditControllerTest`: a first expiries read that commits outside
    the test transaction (`NOT_SUPPORTED`), like the balance and
    history routes; "two first-read cases" made three there and in
    `credit-service/AGENTS.md`.
  - `docs/credit-service.md`: the contract's cadence sentence for
    `GET /credits/me/expiries`, and the "No window" bullet's upper
    bound (hundreds of lots for a busy courier, about 450 at five
    errands a day) in place of the earlier understatement.
- **Prompt(s):** Asked to go through the review's three findings. For
  the blocker (the expiry line going stale when two balance changes
  cancel out between polls), the tool laid out four options neutrally
  (re-read daily, on a new history row, on every poll, or a marker on
  the balance route), and the team chose re-reading on every poll,
  reversing its earlier "not on every poll" decision. The two
  non-blocking items were applied as the reviewer described: add the
  first-read test, and correct the doc's stated list size rather than
  add a limit.
- **Author review:** `./mvnw test` in `credit-service` with Docker
  (259/259) and the `web` suite (199/199) with `tsc -b`, eslint and
  Prettier; the new web test fails against the old trigger. Pending
  pull request review.

## 2026-10-17 — Ryan Ang (upcoming credit expiries: route and credits page, issue #196)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate + tests + docs
- **Scope:** `credit-service/` (`GET /credits/me/expiries` in
  `CreditController`, `CreditQueryService.upcomingExpiries`,
  `ExpiriesResponse`, and the route's cases in `CreditControllerTest`),
  the route in `docs/credit-service.md`'s Read API contract and REST API
  component row, and the service's README and AGENTS.md. Then `web/`:
  `getExpiries()`, `expiry.ts` (items grouped by Singapore date), the
  Available card's line, `ExpiryNotice`, and their tests in
  `credits.test.tsx`.
- **Prompt(s):** Asked to implement #196, backend then web, to the
  team's decisions, given in full: a new route
  `GET /credits/me/expiries` returning `{"items": [{amount,
  expiresAt}]}`, one item per lot still holding credits, ordered by
  `expiresAt` then lot ID, with no lot IDs, no window, sign-up, drained
  and reserved credits left out, past dates kept until the expiry run,
  an empty list rather than 404, get-or-create first, and the web app
  reading it on page load and balance changes rather than on the poll.
  The web half to the same decisions: the soonest credits on the
  Available card ("5 credits expire by 12 Jan"), a notice above the
  transactions only when they expire within 7 days ("3 credits expire
  on 12 Jan. Use them on a request before then."), dates in Singapore,
  no "never expire" line, and nothing on the badge or profile card.
  The tool's own choices: reusing the spend-order query, whose order is
  the contract's with the sign-up lot last, and dropping that lot;
  counting anything dated today or earlier in Singapore as "tonight",
  since tonight's 00:00 run moves it; the soonest Singapore date's
  items added up for the card and the notice; and an expiries read
  that fails showing nothing rather than an error.
- **Author review:** `./mvnw test` in `credit-service` with Docker, and
  the `web` suite with `tsc -b` and eslint. Pending pull request
  review.

## 2026-10-17 — Leong Wei Zhi (self-deletion keeps the admin role, issue #203)
- **Tool:** Claude Code (Opus 5)
- **Mode:** docs (+ one test)
- **Scope:** `user-service/README.md`, `docs/gateway.md`,
  `user-service/src/main/java/foc/user/service/ProfileService.java`
  (comment only) and
  `user-service/src/test/java/foc/user/security/JwtAuthenticationTest.java`.
  - The asymmetry #203 reported: `DELETE /users/{id}` demotes the
    target to USER and stamps `denylist:user:<id>` (#179, #178);
    `DELETE /users/me` does neither, so a self-deleting ADMIN keeps the
    role for the 30-day window — the admin routes authorize on the
    token's claim alone — and restores as ADMIN.
  - `README.md`'s login section claimed "no admin powers ride along"
    for every deleted account; it was true only of admin removal. Now
    both paths are stated there and in the soft-delete section;
    `docs/gateway.md`'s G12 note says self-deletion writes no stamp.
  - `JwtAuthenticationTest.selfDeletedAdminKeepsAdminRoutes`: an ADMIN
    self-deletes, logs in inside the window (200 with
    `deletionScheduledFor`), the fresh token carries ADMIN, `GET /users`
    answers 200 and `GET /users/me` 404 — the acceptance in #203.
  - No behaviour changed.
- **Prompt(s):** "resolve #203" — the tool read the cited files, then
  put the issue's three options (A demote on self-delete, B active-
  caller check on the admin routes, C accept and document) as a neutral
  question.
- **Author review:** Decision by Leong Wei Zhi: **option C** — the
  state is self-inflicted and nobody but the account's owner can open
  or close it, so the window is accepted and only the docs were wrong.
  Verified by `./mvnw clean test` in `user-service` (367/367 green,
  one more than before).

## 2026-10-17 — Aung Ko Khant (order-service: createdAt default, pushed first)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** debug
- **Scope:** order-service `Requests.createdAt` defaults to
  `Instant.now()`, so a `save()` that never calls `setCreatedAt` still
  passes the not-null check; the column stays NOT NULL.
- **Prompt(s):** 2026-10-16, pasted PR review comment (blocking:
  `createdAt` is `nullable = false` but nothing sets it). The reviewer
  offered a field default (as Notification and User do),
  `@CreationTimestamp` or `@PrePersist`; the tool applied the field
  default, compared it with `@CreationTimestamp` when asked, and the
  author kept the default.
- **Author review:** a test saving a request without `createdAt` comes
  with the service code in a later commit; this commit's tests run in a
  Maven container on a clean checkout. To be reviewed via pull request.

## 2026-10-17 — Aung Ko Khant (order-service: state machine states, pushed first)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service `OrderStates` (PENDING and REJECTED added by
  the tool on 2026-10-15; ARRIVED added by the author on 2026-10-16),
  `OrderEvents` (RESERVE, REJECT, TIMEOUT on 2026-10-15; ARRIVE on
  2026-10-16), `OrderStateMachine` (PENDING -RESERVE-> CREATED,
  -REJECT-> REJECTED, -TIMEOUT-> CANCELLED; COLLECTED -ARRIVE->
  ARRIVED -COMPLETE-> COMPLETED) and `OrderStateMachineTests`.
  Committed ahead of the rest of the 2026-10-15 to 10-17 order-service
  work at the author's request ("Can we push state machine changes
  first?"); the service, messaging and endpoint code that fires these
  events follows in later commits with their own entries.
- **Prompt(s):** 2026-10-15: "I think we need to add 2 additional
  stages pending and rejected to coordinate with credit service"; the
  saga is the team's design (`docs/credit-service.md` D7-D8), and the
  author chose the event names RESERVE / REJECT / TIMEOUT and that only
  they leave PENDING. 2026-10-16: "I would like to add an arrived state
  between collected and completed, the point is to inform requester
  that courier has arrived"; the author chose COMPLETE only from
  ARRIVED (no direct COLLECTED -> COMPLETED).
- **Author review:** this commit's tests run in a Maven container on a
  clean checkout of it: 15 tests, 0 failures. To be reviewed via pull
  request.

## 2026-10-11 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service request state machine skeleton —
  `foc.order.OrderStateMachine` (transition table) and
  `OrderStateMachineTests`; `package foc.order;` line added to the
  author's `OrderStates` / `OrderEvents` enums; order-service README
  status paragraph; README AI Use Summary.
- **Prompt(s):** "Help me implement skeleton of state mchine", with
  the author's hand-written `OrderStates` and `OrderEvents` enums
  (including the guard comments on CANCEL, DROP and EXPIRE) as input.
  The tool asked two neutral-options questions and the author chose:
  DROP leads ACCEPTED -> CREATED (not CANCELLED), and a plain-Java
  transition table (not the Spring Statemachine library). The
  remaining transitions were transcribed from the enums and their
  comments.
- **Author review:** Transition table pinned by unit tests (every
  permitted transition, every other state/event pair rejected);
  `mvn test` passes (11 tests). Reviewed via pull request.

## 2026-10-17 — Ryan Ang (admin dashboard: common pool box, issue #174 branch)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `credit-service/`, `web/` and `docs/credit-service.md`.
  - Credit service: `GET /credits/admin/pool` (pool balance, the
    accounts a redistribution shares among, a preview of each share,
    the last run and when the next is due) and
    `GET /credits/admin/redistributions` (recorded months, newest
    first, paged), in `AdminCreditController` and `PoolQueryService`,
    with two response records; `SecurityConfig` gates
    `/credits/admin/**` to `ADMIN` and `OWNER`. Tests:
    `PoolQueryServiceTest` (6) and `AdminCreditControllerTest` (10).
  - Design doc: an "Admin routes" part of the Read API contract and a
    403 row. The service's README and AGENTS.md (package map, and the
    "only the caller's own data" invariant's one exception).
  - Web: `CommonPoolCard` on the admin dashboard above the Users
    section, two `creditApi` calls and their types; times shown in
    Singapore time. Tests in `credits.test.tsx` (3); `admin.test.tsx`
    fakes the new call and expects the second section.
- **Prompt(s):** Asked for a box on the admin dashboard showing the
  common pool, how many users it would be shared among, and the last
  and next distribution.
- **Author review:** Team decisions, via options Q&A: a summary route
  plus a paged run history (the server computing the next run and the
  share preview); ADMIN and OWNER only; the account count with a share
  preview; built on the #174 branch. Choices the tool made for the
  author to review: the route paths and field names; the next run
  following PR #193's routine (the 1st at 00:00 Singapore time once the
  month is recorded, otherwise the next midnight), although this branch
  does not have #193's scheduler yet; the box loading once when the
  dashboard opens. Checked through the gateway as an ADMIN (summary)
  and a USER (403), and in the browser. Then, at the author's request:
  the dashboard subtitle removed, and a past-distributions table under
  the figures (`PastDistributions`, 12 months a page, its "Run at"
  column right-aligned) on the runs route, with the formatting shared
  in `poolFormat.ts`; 3 more tests in `credits.test.tsx`.
  Pending pull request review.

## 2026-10-17 — Ryan Ang (web: credit balance and history, issue #174)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `web/`.
  - `src/features/credit/`: `api.ts` (`GET /credits/me` and
    `/credits/me/history` through `apiFetch`), `types.ts`,
    `history.ts` (each history type's label, sign and description; the
    filter inputs turned into the query, local days to UTC instants
    with the end exclusive, and the range checks),
    `CreditBalanceProvider` + `useCreditBalance` (one balance poll for
    the app), `CreditHistoryPage` and its `BalanceCards`,
    `HistoryFilters` and `HistoryList` (a table on desktop, cards on
    mobile).
  - `routes/credits.tsx` and `/credits` behind `ProtectedRoute`; the
    provider as a layout route below `NotificationProvider`.
  - `shared/shell/CreditsBadge.tsx` shows the live available balance as
    a link to `/credits`; `ProfileDetailsCard`'s Credit Status shows the
    live balance with a link (a user-domain file, flagged in the PR).
  - Tests: `credits.test.tsx` (19); `profile.test.tsx` fakes the
    balance call, since a running gateway answers the test token with a
    401 that signs the session out; `app.test.tsx` checks the new badge.
  - `web/AGENTS.md`'s wireframe row and the README's AI Use Summary.
- **Prompt(s):** Review and scaffold issue #174 on the current branch.
- **Author review:** Team decisions, via options Q&A: the lifetime-earned
  card replaced by Total and no running-balance column (the API has
  neither); `/credits` as its own page reached from the badge and the
  profile card, with no nav item; minimum and maximum amount fields;
  and polling the balance every 30 s while the tab is visible, paused
  while it is hidden and refetched when it is shown. Choices the tool
  made for the author to review: the type labels and descriptions; a
  row's sign follows its change to the available balance, with
  `TRANSFER_OUT` shown as a loss; a failed poll keeps the last balance;
  the page reloads its rows when a poll finds the balance changed; the
  badge shows a dash while logged out. Checked in the browser against
  the running stack at 1280 and 390 px wide, including a date filter
  the server accepted. Then, at the author's request, a loss's amount
  shown in red, as a gain's is in green. Pending pull request review.

## 2026-10-17 — Leong Wei Zhi (issue #66: D29 reversed — ingest-time name enrichment, D30/D31)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor + generate
- **Scope:** `foc-contracts` (requesterName/courierName removed from
  all nine request records, example JSONs, README/AGENTS, record-shape
  test inverted to a no-display-names guard), `user-service`
  (`InternalServiceTokenFilter` + SecurityConfig wiring + yaml binding
  + ProfileControllerTest cases), `notification-service`
  (`UserDirectory` port, `RestUserDirectory` adapter + unit test,
  processor enrichment, both yamls, pom: spring-boot-starter-restclient),
  `compose.yaml`, `.env.example`, `docs/notification-service.md`
  (D29 removed as superseded; D30/D31 + "Display-name enrichment"
  section added).
- **Prompt(s):** The author questioned coupling display names into the
  shared event contracts ("do we really want to couple name in events
  since requester id is already present?") and decided the reversal
  through neutral options Q&A rounds: IDs-only events with
  notification-service enrichment (D30); enrichment at ingest, stored
  in the payload, after establishing the live STOMP push would
  otherwise prepend actor-less rows; names injected into the payload
  map so the web templates are untouched; lookups via the existing
  GET /users/{id} behind a TTL cache; and a zero-trust static internal
  token (D31) after explicitly rejecting the AI-suggested permitAll
  ("we want zero trust even though gateway has it"). AI also explained
  why borrowing the STOMP CONNECT JWT cannot work (offline recipients,
  1 h token lifetime vs CONNECT-only checks, credential replay).
- **Author review:** foc-contracts suite green; user-service
  ProfileControllerTest 20/20; notification-service 91/92 with only the
  known Java-26-local context-restart AMQP error on
  `RetentionPurgeSchedulerTest` (passes alone; exists on main). To be
  reviewed via pull request (PR #198).
- **PR #205 review (2026-10-17):** both reviewer bots' inline note
  confirmed valid — the username lookup ran inside the @Transactional
  ingest, so a user-service stall could hold a pooled DB connection for
  seconds per event. Fixed as the reviewers suggested: the lookup moved
  before the transaction (programmatic TransactionTemplate replaces
  @Transactional); duplicates now pay a cache-absorbed lookup. Suite
  re-run green (same 91/92).

## 2026-10-17 — Leong Wei Zhi (Notification Center, web half of issue #66)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `web/src/features/notifications/` (new: types, api module,
  eventMessage templates + relativeTime, NotificationCenter page;
  modified: NotificationProvider — badge seeds from
  GET /notifications/me/unread-count on login/reconnect and re-syncs
  after writes, pushed frame body exposed for the open Center to
  prepend; useNotifications context widened), `routes/notifications.tsx`
  + the route table, the bell (button → Link to /notifications), tests
  (`notification-center.test.tsx` new; `notifications.test.tsx` and
  `app.test.tsx` updated for the link bell and a URL-dispatching fetch
  stub), `web/AGENTS.md` wireframe row.
- **Prompt(s):** The web half of issue #66's plan. UI decisions were the
  author's via neutral options Q&A (no recommendations): clicking an
  unread row marks it read (no mark-unread affordance), an All/Unread
  filter toggle, a "Load more" append pager, display names rendered
  from the event payloads (backend D29; superseded the same day by D30 —
  the names now arrive via ingest-time enrichment, same payload
  shape, web untouched), pushed frames prepend into the
  open Center, and the badge re-fetches the server count after writes.
  Layout follows web/docs/wireframes/notification-center.png; the three
  controls the wireframe doesn't draw are recorded in web/AGENTS.md.
- **Author review:** `npm run lint`, `prettier --check`, all 168 vitest
  tests and `npm run build` pass. To be reviewed via pull request.
- **PR #199 review (2026-10-17), Claude Code (Opus 5):** eleven inline
  findings from the two reviewer bots (@claude, @codex) triaged — the
  two backend ones were already fixed on main by the #205 review and
  are not in this diff; the rest were valid and are fixed, each with a
  regression test that fails against the pre-review code. A failed
  "Load more" is retryable rather than skipping its page (`loaded` /
  `attempt` split from `page`); a filter switch clears the previous
  filter's page count; a page-0 answer merges rather than discards
  frames pushed while it was in flight; "Mark all as read" only touches
  rows that existed when it started; the empty state hides behind an
  error banner; the bulk control falls back to the loaded rows when the
  best-effort badge count is unavailable; a count sync can no longer
  erase a pushed increment (newest-sync-wins guard, plus a push starting
  its own sync); pushes queue instead of a single replaceable value, so
  two frames batched into one render both land; and the relative times
  tick on a 60 s clock. One finding was a UX fork, not a defect, so the
  author decided it via neutral options Q&A: under the Unread filter a
  row marked read now leaves the list (and read-all empties it), while
  the All filter keeps graying in place. Re-verified: 179 vitest tests,
  `tsc -b`, eslint and Prettier clean.
- **Browser run (2026-10-17), Claude Code (Opus 5):** drove the branch
  against the live compose stack with Playwright (Vite dev server on
  5173, gateway 8090, 14 then 13 seeded notifications published to the
  `request-events` exchange as demo_user_001). Confirmed working: the
  REST-seeded badge, all nine event templates with D30 display names,
  D25's created_at ordering, "Load more", the Unread filter, a row
  marked read leaving the Unread list, a live STOMP push prepending and
  re-syncing the badge, and the ticking relative times. It also found
  one defect the unit tests had missed: after "Mark all as read"
  emptied the Unread view, the page count from the last fetch outlived
  its rows and the dashed empty state rendered with a "Load more"
  button under it. Fixed (the bulk write clears the Unread page count,
  and the pager is gated on having rows) with a regression test that
  fails against the pre-fix code; 180 vitest tests green.
- **PR #199 review, round 2 (2026-10-17), Claude Code (Opus 5):** after
  the #195 main sync, two threads were still open. @claude found that
  "Load more" skips rows under the Unread filter: the list is
  offset-paged over a set that shrinks as rows are read, so asking for
  `loaded + 1` steps past the rows that moved up into the slice just
  vacated. Confirmed against the live API — with 13 unread, marking 3
  read makes `page=1` return 0 rows while `page=0` returns the 10
  remaining, 3 of them never seen. Fixed by deriving the next offset
  from the rows held (`floor(items.length / PAGE_SIZE)`, the held rows
  being a prefix of the server's current set) and testing "is there
  more" against `totalElements` rather than a page count computed from
  an older, larger set; the fetch request became one value carrying its
  own replace/append mode, so a page number and the merge it implies
  cannot disagree. The reviewer's secondary claim that a push also
  loses a row does not hold — a push shifts every offset by exactly one
  and the one-row overlap is absorbed by the id dedupe. The test stub
  gained a small mutating server model for the unread set, which is
  what makes "mark read, then Load more" honest; two paging fixtures
  that used one-row pages against a `size=10` request were corrected.
  @codex's standing P1 (the README's consolidated AI Use Summary had
  the backend half of #66 but not the web half) was valid against
  AGENTS.md duty 4 and is now addressed. 213 vitest tests, `tsc -b`,
  eslint and Prettier clean.
- **PR #199 review, round 3 (2026-10-17), Claude Code (Opus 5):** the
  approving review (@Sinnez1) left one actionable nit: "Load more" can
  disappear before everything is loaded, because `totalElements` only
  moves on a fetch while live pushes add rows to the list — 15 on the
  server with 10 held, then 5 pushed, and the rows held reach a total
  those rows postdate, hiding the pager over 5 unreachable rows. Valid
  and fixed by keeping the two in step: a push raises the total by the
  rows it actually prepends. The same defect runs the other way under
  the Unread filter, which the nit did not mention — a row marked read
  leaves the list but not the total, so one page of unread rows grew a
  pager that offered a page already on screen — so the read path lowers
  the total too. Two regression tests, both failing against the
  pre-review code. The review's second point (credit-service's
  rejection reasons are lowercase machine strings, so a row reads
  "…rejected: insufficient available credits") was a copy fork rather
  than a defect and the reviewer called it fine as it is; put to the
  author via neutral options Q&A against leaving it, capitalising the
  reason, and mapping the two known reasons — the author chose to leave
  the service's wording unchanged, so no code change. 215 vitest tests,
  `tsc -b`, eslint and Prettier clean.

---
## 2026-10-17 — Leong Wei Zhi (notification REST API, backend half of issue #66)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `notification-service/` REST API (new `config/`,
  `controller/`, `dto/` packages; `security/` filter + handlers;
  repository queries; `NotificationQueryService`; both application
  yamls; pom), `foc-contracts` request-event records
  (requesterName/courierName + record-shape test), the nine test files
  constructing those records (notification- and credit-service),
  `compose.yaml`, `.env.example`, `docs/notification-service.md` (+
  `.mmd`), service README/AGENTS.md. New tests: `SecurityConfigTest`
  (chain 401s + the `/ws/info` stays-open canary for #67),
  `NotificationControllerTest` (order, paging 400s, ownership 404s,
  idempotent writes, unread count/filter).
- **Prompt(s):** Asked to plan and resolve issue #66. Every API/UX
  decision was made by the author via neutral options Q&A rounds
  (no recommendations): scope split (backend + web PRs), PATCH-with-body
  shape, `/notifications/me` base path, created_at list order,
  204 + ownership-scoped bulk UPDATE (count 0 → 404, no existence
  oracle), empty filter authorities, `POST /read-all`,
  `GET /unread-count` → `{"count": n}`, `?unread=true` filter, and
  display names carried in the event payloads rather than rendered from
  IDs or looked up cross-service. Recorded as D24–D29 in
  `docs/notification-service.md`; implementation mechanics transcribe
  credit-service's precedent (its SecurityConfig/controller/test style).
- **Author review:** full suites run — foc-contracts and credit-service
  (217/217) green; notification-service 83 tests with the known
  Java-26-local context-restart AMQP error on `RetentionPurgeSchedulerTest`
  (passes alone; same environmental failure exists on main). To be
  reviewed via pull request.
- **PR #198 Codex review (2026-10-17):** both findings reproduced in
  tests before fixing. (1) Jackson's default coercion bound
  {"read":"true"}/{"read":1} → 204 where the contract promises 400 —
  fixed with a field-scoped strict Boolean deserializer (the AMQP
  mapper is untouched). (2) The /** CORS registration policed the /ws
  paths, 403ing a handshake Origin the WS config allows — narrowed to
  /notifications/**. Both pinned by new tests.
- **PR #198 follow-up (2026-10-17):** reverted the strict-deserializer
  fix above — author decided the PATCH `read` field should keep
  Jackson's default scalar coercion rather than reject it, so
  `StrictBooleanDeserializer` and its pinning test are removed and
  `MarkReadRequest` is back to a plain `Boolean`. The CORS fix is
  unaffected and stays. `docs/notification-service.md`'s REST API
  contract (D24) is reworded to document the PATCH body's coercion as
  deliberate rather than promising 400 for every non-boolean. Also
  addressed Sinnez1's non-blocking review nit on the `unread` query
  param: rather than hand-validating it like `page`/`size`, D27 and
  the REST API table now document that `unread` uses Spring's
  standard boolean conversion (accepts `1`/`yes`/`on` and
  `0`/`no`/`off` alongside `true`/`false`) — both are the author's
  decisions via options Q&A, not this tool's.
- **Author review:** `mvn -pl notification-service test` after the
  revert; reviewed via pull request.

## 2026-10-16 — Ryan Ang (PR #193 review: expiry and redistribution scheduler)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor + tests + docs
- **Scope:** `credit-service/` and `docs/credit-service.md`, for Leong
  Wei Zhi's review.
  - `ExpiryAndRedistributionScheduler`: one routine, `run`, called by
    the 00:00 daily job and at startup: the sweep, then the current
    Singapore month's redistribution, which the service skips if the
    month is recorded. The 00:10 job and the catch-up's time check are
    gone. Review findings 1 (startup sweep), 2 (no retry of a failed
    run) and 3 (order not enforced) are answered by it.
  - `EarnedCreditLifetime`: the earned-credit expiry and its `>= 1`
    check, moved from `CreditOperationsService` and used by both it
    and `RedistributionService` (finding 4).
  - Tests: `ExpiryAndRedistributionSchedulerTest` rewritten (8: the
    order inside the routine, the Singapore month on the 1st, an
    ordinary day and a startup with the month recorded, a missed
    month at startup, a failed redistribution completed the next day,
    and neither step throwing at startup);
    `aPoolSmallerThanTheAccountsGivesOneEachAndSkipsTheRest` makes its
    own accounts (finding 5); `CreditOperationsConfigurationTest`
    builds the new component.
  - Design doc: D12 and the failure table (the down-at-00:00 row, and
    new rows for a failed run, a month that never completes, and a
    startup run overlapping the 00:00 one). The service's README and
    AGENTS.md, and the README's AI Use Summary, updated.
- **Prompt(s):** Asked whether a proposed single daily-and-startup
  routine answers the review's first two findings, then to implement
  it with the review's smaller items.
- **Author review:** The single routine, dropping the 00:10 time, and
  
  ing that a month failing every day until its end is skipped
  (its credits stay in the pool) are team decisions; the tool wrote
  the code, tests and doc text to them. The shared component is the
  reviewer's suggested option. Pending pull request review.

## 2026-10-16 — Ryan Ang (credit-service: expiry and redistribution scheduler, issue #173)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `credit-service/` and `docs/credit-service.md`.
  - `service/LotExpiryService`: moves what remains in each lot past its
    expiry to the common pool, one transaction per account (the
    owner's account lock, their lots read again, then the pool), with
    one `EXPIRY` history row per account.
  - `service/RedistributionService`: in one transaction, locks every
    account in ascending user ID order and then the pool, gives each
    `floor(pool / accounts)` and the remainder 1 each to accounts
    chosen at random, each share a new lot that expires the
    earned-credit lifetime after it is received, with a
    `REDISTRIBUTION` history row; records the month in
    `redistribution_run`, checked before and after the locks.
  - `service/ExpiryAndRedistributionScheduler`: expiry daily at 00:00
    and redistribution on the 1st at 00:10, Asia/Singapore; a missed
    month caught up at startup, after an expiry run; one account's
    failed expiry logged
    without stopping the others. Off in the `test` profile
    (`credit.scheduler.enabled`).
  - Repositories: the expired lots and their owners, the pool's row
    lock, every account locked by `user_id collate "C"`, and the run
    row inserted rather than merged.
  - Tests: `ExpiryAndRedistributionTest` (9, real Postgres, committing,
    with the design doc's invariants checked after each; one covers a
    pool smaller than the number of accounts, where a share of 0 writes
    no lot or history row) and
    `ExpiryAndRedistributionSchedulerTest` (7, mocked, fixed clock).
  - Design doc: D12, the scheduler's component row and two failure
    table rows record the team's decisions. The service's README and
    AGENTS.md, and the README's AI Use Summary, updated.
- **Prompt(s):** Implement issue #173 on
  `feat/credit-expiry-redistribution`.
- **Author review:** The behaviour is the team's, from
  `docs/credit-service.md` (D2, D4, D12, "How lots are spent, returned
  and expired") and issue #173. Where the doc was silent, the team
  decided: both runs in Singapore time (expiry 00:00 daily,
  redistribution 00:10 on the 1st, the Singapore month as the period),
  one transaction per account for expiry, a month with an empty pool
  still recorded, and a missed redistribution caught up at startup.
  On review, a further team decision: the catch-up runs an expiry
  first, the order of a normal night, so credits that expired while the
  service was down are in that month's share (the tool's first draft
  ran the redistribution alone). The tool also made the catch-up never
  throw, since a failing ready-event listener stops the service from
  starting. One `EXPIRY` row per account per run (the doc's "one row per 
  affected account per operation"); a lot past its expiry when its `expires_at` 
  is before the run's instant; the remainder given to distinct accounts 
  by a shuffle; `distributed` in `redistribution_run` counting every 
  credit taken from the pool, the remainder included; the "C" collation 
  on the all-accounts lock so its order matches the transfer's Java
  string order.
  Pending pull request review.

## 2026-10-16 — Leong Wei Zhi (CI pipeline + Claude PR review)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `.github/workflows/ci.yml` and
  `.github/workflows/claude-review.yml` (first CI setup — the repo had
  no `.github/` directory).
  - `ci.yml`: one always-running workflow; `dorny/paths-filter` picks
    the changed Maven modules (contracts-dependent services also rebuild
    on `foc-contracts/**`), a web job (lint, format, vitest, build), a
    path-filtered Docker build check for all seven images (no push), and
    a `ci-ok` gate job intended as the sole required check so skipped
    path-filtered jobs can't block merges.
  - `claude-review.yml`: `anthropics/claude-code-action@v1` reviews
    non-draft PRs on open/ready/push, posting inline comments plus a
    summary; authenticated by the `CLAUDE_CODE_OAUTH_TOKEN` secret
    (subscription OAuth) running claude-sonnet-5-5; docs-only changes
    are skipped and superseded runs cancelled.
  - Two pre-existing issues the first CI run surfaced, fixed on the same
    PR: `supplier-service/mvnw` was committed non-executable (mode
    100644; exit 126 on a fresh checkout), and 10 `web/src` files had
    drifted from Prettier style — reformatted mechanically with
    `prettier --write` (lint and all 125 vitest tests pass unchanged).
- **Prompt(s):** Asked to plan and implement a CI/CD GitHub pipeline
  with a cost-efficient Claude Code review in CI; chose Docker
  build-check-only CD, subscription OAuth auth, auto-trigger on
  opened/ready/synchronize, and the Sonnet 5.5 model in follow-ups.
- **Author review:** Workflow YAML checked against the
  claude-code-action docs ("Automatic PR Code Review" recipe) and
  validated locally; path filtering and the review to be exercised on
  this PR and follow-up test PRs before marking `ci-ok` required.
  Pending pull request review.

## 2026-10-16 — Ryan Ang (PR #190 re-review: credit request event listener)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** docs + config
- **Scope:** the four items in Leong Wei Zhi's approving re-review,
  applied as the reviewer suggested:
  - `credit.rabbitmq.retry.confirm-timeout-ms` takes
    `CREDIT_RETRY_CONFIRM_TIMEOUT_MS` (default 5000), passed through
    `compose.yaml` and listed in `.env.example`, whose stale "attempts
    are not read yet" note is dropped.
  - The test profile's broker host is `broker.invalid`, so a reused
    test context cannot reach a developer's own broker on 5672.
  - `credit-service/AGENTS.md`'s "never dropped" narrowed to a missing
    dead-letter queue, and the design doc's returned-copy row notes it.
  - The `credit-service/README.md` listener paragraph reflowed, and the
    2026-10-14 entry's superseded "left unacked" clause replaced with
    the reviewer's wording.
- **Prompt(s):** Asked to explain and apply the reviewer's re-review suggestions.
- **Author review:** The changes are the reviewer's suggestions, taken
  as written by team decision. Pending pull request review.

## 2026-10-16 — Ryan Ang (PR #190 review: credit request event listener)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor + docs
- **Scope:** `credit-service/` and `docs/credit-service.md`.
  - `RequestEventsListener`: a retry copy returned as unroutable now
    dead-letters the original; a nacked, unconfirmed or unsendable copy
    requeues it after a pause of the confirm timeout, which moved from
    a constant to `credit.rabbitmq.retry.confirm-timeout-ms` (5000).
    The catch around the republish widened from `AmqpException` to
    `RuntimeException`, so a `CancellationException` also settles the
    delivery. The event's own failure is logged and attached as a
    suppressed exception on the requeue and returned paths. The outcome
    `switch` became a switch expression, so a new `Outcome` no longer
    compiles unsettled.
  - `RequestEventsListenerTest`: the returned-copy test now expects a
    dead-letter; the nacked, unconfirmed and unsendable tests expect the
    requeue after the (mocked) pause; new tests for a cancelled confirm
    and an interrupted pause. The confirm timeout is passed in (50 ms),
    so no test waits 5 s.
  - `RequestEventsListenerIntegrationTest`: a new test deletes the
    retry queue on the real broker and checks that a failed event is
    dead-lettered on its first delivery, then redeclares the topology.
  - Design doc failure table: the refused-copy row split into a
    returned row and a nacked/timeout row (noting a timeout waits up to
    10 s in total: the confirm wait, then the pause), and a row for a
    timed-out confirm leaving two copies. Service README, AGENTS.md and
    `application.yaml` updated to match.
- **Prompt(s):** Review the
  review comments on PR #190, then to implement the team's decisions
  on the two that needed one, with the fixes for the rest.
- **Author review:** Team decisions, transcribed: a returned copy
  dead-letters immediately; a nack or timeout requeues after a 5 s
  pause; a timed-out confirm keeps the requeue and is documented as
  possibly running the event twice. The tool's triage of the Copilot
  comments (the README environment variable name and the message
  mutation found not to be bugs) was checked against the code.
  Pending pull request review.

## 2026-10-14 — Ryan Ang (credit-service: request event listener, issue #171)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `credit-service/`.
  - `messaging/rabbitmq/RequestEventsListener`: consumes
    `credit-service.request-events` with manual acknowledgement and
    hands each event to `RequestEventHandler`. `HANDLED` is acked after
    the handler's transaction commits; `INVALID_STATE` is nacked
    without requeue, so it is dead-lettered at once; any exception
    (including `ReservationNotFoundException`) is republished to the
    retry queue until `credit.rabbitmq.retry.max-attempts`, then
    dead-lettered. Each settled delivery logs its event type, event
    ID, request and outcome.
  - Tests: `RequestEventsListenerTest` (mocked: each outcome, the
    attempt header, the cap, and how a retry copy the broker does not
    accept settles the original; see the 2026-10-16 entry for the
    final behaviour) and
    `RequestEventsListenerIntegrationTest` (Testcontainers RabbitMQ and
    Postgres: a reservation with its reply in the outbox, a duplicate
    delivery changing nothing, a completion that overtook its
    reservation transferred on a retry, a completion with no
    reservation retried then dead-lettered, a conflict dead-lettered
    without a retry, and a fractional reward dead-lettered on first
    delivery).
  - The service's README and AGENTS.md, and the README's AI Use
    Summary updated.
- **Prompt(s):** The scope of issue #171, on
  `feat/credit-event-listener`.
- **Author review:** The settlement rules, the retry delay and the
  attempt cap are the team's, from `docs/credit-service.md` D11 and
  issue #171; the design doc is unchanged. Choices the tool made for
  the author to review, following notification-service's
  `DomainEventsListener`: attempts counted in an `x-retry-attempts`
  header the listener stamps, since RabbitMQ 4 resets `x-death` on a
  client republish; the original acked only after the retry copy is
  confirmed; what the listener does when the copy is nacked, returned
  or not confirmed is the 2026-10-16 entry's answer, not this one's.
  One difference from notification-service: the
  retry copy is confirmed through a `CorrelationData` future, because
  this service uses correlated publisher confirms (PR #188) and
  notification-service's `waitForConfirmsOrDie` needs simple ones. The
  listener container keeps Boot's defaults (one consumer). A retry copy
  the broker does not accept requeues the original at once (it was
  left unacknowledged, so stuck until the channel closed), recorded as
  a row in the design doc's failure table, and that the two
  dead-letters the listener decides are logged at ERROR. The cap is
  now asserted as total attempts, and a `request.submitted` with no
  requester is shown to be dead-lettered on first delivery by the
  converter. Four unit tests now cover the requeue (a nacked, returned,
  unconfirmed or unsendable retry copy).
  Pending pull request review.

## 2026-10-15 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** docs
- **Scope:** PR #187 review items 4 and 5 (Leong Wei Zhi):
  `order-service/mvnw` committed executable (`100755`);
  `docs/architecture.md` "Database engine for the Order Service" bullet
  struck from the open decisions and the Order DB node in
  `docs/architecture.mmd` labelled PostgreSQL.
- **Prompt(s):** "You can address 4 and 5". The engine (PostgreSQL 17)
  was the author's choice when order-db was wired into compose.yaml
  (2026-10-10); the tool only recorded it.
- **Author review:** Reviewed via pull request.

## 2026-10-14 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, docs
- **Scope:** `order-db` host port default 5436 -> 5437 in
  `compose.yaml`, `.env.example`, `AGENTS.md` (service DB table) and
  `order-service/README.md`.
- **Prompt(s):** "I need to use port 5437 instead as 5436 is used by
  credit service". Port chosen by the author; the tool only applied it.
- **Author review:** Reviewed via pull request.
- **Same day:** rebased `feat/order-service-scaffold` onto main after
  the gateway cutover (#151) and, per the PR review, removed
  order-service's `ports:` block and `ORDER_SERVICE_PORT` (compose,
  `.env.example`, order-service README). Conflicts with the
  credit-service work resolved by keeping both sides (credit-db on
  5436, order-db on 5437; main's gateway `VITE_*` URLs kept).
  Prompt: "Please carry out 1" (rebase and drop the ports block, as
  the reviewer asked). `docker compose config` validated; the service
  build and tests were not re-run.

## 2026-10-14 — Ryan Ang (PR #189 review follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, docs
- **Scope:** `credit-service/`, following review
  comments on PR #189.
  - `service/CreditOperationsService.getOrCreate` reads an existing
    account first and only attempts the insert when there is none.
  - `controller/ApiExceptionHandler`: a type mismatch reads "must be an
    integer" only for an integer parameter; any other non-instant type
    reads "must be a valid value".
  - `config/SecurityConfig`: the CORS comment updated for the gateway
    cutover (issue #151).
  - `AGENTS.md`: the `CreditControllerTest` invariant now states the
    two `NOT_SUPPORTED` first-read cases that commit.
  - Second review round: the `main` merge resolution redone for
    `ai/usage-log.md`, the root README, and the service's README and
    AGENTS.md. Each was rebuilt from `main`'s copy with only #172's
    and #189's additions on top, so the #170 and PR #188 text the
    first resolution dropped is back, and the conflict markers left in
    the service README are gone.
- **Prompt(s):** Review the four review comments on PR #189 as
  the reviewer suggested; then fix the merge resolution as the
  reviewer's second round suggested.
- **Author review:** Each change follows the reviewer's comment.
  Pending pull request review.

## 2026-10-13 — Ryan Ang (credit-service: read API, issue #172)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `credit-service/`.
  - `controller/CreditController`: `GET /credits/me` and
    `GET /credits/me/history`, for the token's user only. `page` and
    `size` are plain integers and `from` / `to` bind as
    `OffsetDateTime`, as the design doc's "Implementation notes" say;
    the checks behind the contract's 400s are in the controller.
  - `controller/ApiExceptionHandler` (`@RestControllerAdvice`) and
    `InvalidQueryParameterException`: an out-of-range or contradictory
    parameter, or one of the wrong type, is a 400 problem+json whose
    detail names the parameter.
  - `service/CreditQueryService` and `HistoryFilter`: both reads get
    or create the account first (D6); the history is a JPA
    Specification over the caller's rows with the optional filters, in
    the fixed order `occurredAt` descending then `id` descending.
  - `dto/`: `BalanceResponse`, `HistoryEntryResponse`, and
    `PageResponse`, a copy of supplier-service's shape.
  - `CreditHistoryEntryRepository` now extends
    `JpaSpecificationExecutor`.
  - Tests: `CreditControllerTest` (42 cases through the security chain
    against a real Postgres: the balance on first read and with held
    credits, the history's fields, order, filters and paging, an empty
    page, a page past the last one, both instant formats, and each row
    of the contract's "Errors" table). `SecurityConfigTest`'s
    valid-token case now expects the balance, not a 404.
  - The service's README and AGENTS.md, and the README's AI Use
    Summary updated.
- **Prompt(s):** The scope of issue #172, on `feat/credit-read-api`.
- **Author review:** The routes, fields, order, parameter bounds,
  errors and the two implementation notes are the team's, from
  `docs/credit-service.md` "Read API contract"; the tool transcribed
  them and the design doc is unchanged. Choices the tool made where the
  contract is silent, for the author to review: the wording of each
  400's `detail`; the checks placed in the controller rather than bean
  validation, so each has its own message; a refused request creating
  no account, since validation runs before the read; an unknown
  parameter such as `sort` being ignored; and the test class being
  `@Transactional`, so its made-up history rows are never committed.
  On the author's review, two first-read cases (balance and history)
  were added that run outside the test transaction and commit, so a
  read that could not write on its own would fail a test; no read-only
  transaction was found in the code.
  `./mvnw test` on JDK 21 with Docker: 169 tests, 0 failures. Pending
  pull request review.

## 2026-10-13 — Ryan Ang (PR #188 review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** review assistance, refactoring, tests, docs
- **Scope:** `credit-service/`, `docs/credit-service.md`.
  - `V2__outbox_next_attempt_at.sql` and `OutboxEvent.nextAttemptAt`: a
    nullable column, null meaning due at once.
  - `OutboxEventRepository`: `findDue` replaces the unsent-rows query
    (unsent and due, oldest first, limited), and an `insert` that never
    overwrites, as `ReservedRepository` has.
  - `OutboxPublisher`: a returned reply gets its next attempt time and
    is saved; the poll reads only the replies that are due.
  - `ReplyOutboxService`: the row is inserted, not saved.
  - `RabbitReplyPublisher`: the no-op returns callback is removed, and
    the class comment says a return is read from the `CorrelationData`
    without one.
  - `application.yaml`: `unroutable-retry-delay-ms` (30 000) and
    `spring.task.scheduling.pool.size` (2).
  - Tests: `OutboxEventRepositoryTest` (new: which replies are due,
    their order, the batch counted over the due ones only), a repeated
    event ID refused in `ReplyOutboxServiceTest`, the next attempt time
    in `OutboxPublisherTest`, no callback registered in
    `RabbitReplyPublisherTest`, the new column in `EntityMappingTest`,
    and in `OutboxPublisherIntegrationTest` two unroutable replies
    filling a batch of two while the routable reply behind them is
    still delivered.
  - `docs/credit-service.md` (D10, the component and failure rows, the
    `outbox_event` table), the service's README and AGENTS.md, two
    wording fixes from the Copilot review, and the README's AI Use
    Summary.
- **Prompt(s):** Summary: asked to assess the review comments on
  PR #188. The tool checked
  each against the code and the spring-rabbit jar, reported all six as
  accurate, and left the approach to the two that needed a decision to
  the team. It was then asked to implement the decisions below and the
  four items that needed none.
- **Author review:** Team decisions, transcribed into
  `docs/credit-service.md`. (1) Head-of-line blocking: a
  `next_attempt_at` column, in this PR; on a return it is set to now
  plus 30 s, and the poll reads only rows that are unsent and due. No
  cap on attempts, as before. (2) The returns callback is removed
  rather than moved to a template of its own: the return is still read
  from the `CorrelationData`, and the cost is the template's own
  warning beside ours for each returned reply, a few lines at the 30 s
  spacing. A private template inside `RabbitReplyPublisher` is the
  fallback if that warning is a nuisance in the demo logs; a template
  bean is ruled out. The reviewer's other two points (a second
  scheduler thread; insert, not save) and Copilot's two wording points
  were accepted as raised. Choices the tool made within those
  decisions, for the author to review: the column is nullable with
  null meaning due at once, so existing rows need no backfill; the
  delay is a `credit.outbox.publisher` setting so the integration test
  can shorten it; the unsent index is unchanged; a stopped poll
  (`BROKER_UNAVAILABLE`) sets no delay. `./mvnw test` on JDK 21 with
  Docker: 156 tests, 0 failures, including the existing
  unbound-then-bound integration test after the callback's removal.
  Pending pull request review.

## 2026-10-12 — Ryan Ang (credit-service: reply outbox and outbox publisher, issue #170)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs)
- **Scope:** `credit-service/`, `docs/credit-service.md`.
  - `ReplyOutboxService`: the real `ReplyOutbox` bean, replacing
    `NotImplementedReplyOutbox`, which is deleted with
    `NotImplementedStubsTest`. One `outbox_event` row per reply, in the
    caller's transaction (`MANDATORY`), holding the message body as it
    goes on the wire.
  - `OutboxPublisher` (`@Scheduled`, fixed delay) and the
    `ReplyPublisher` interface it sends through, with
    `RabbitReplyPublisher` in `messaging/rabbitmq/`: mandatory publish
    to `credit-events`, a correlated confirm, and a reply marked sent
    only when it was confirmed and not returned.
  - `OutboxEventRepository`: the poll query (unsent, oldest first,
    limited).
  - `application.yaml`: `credit.outbox.publisher` (poll interval,
    batch size, confirm wait); the publisher is off in the test
    profile.
  - Tests: `ReplyOutboxServiceTest` (stored with its transaction, not
    stored on rollback, refused outside one), `OutboxPublisherTest`
    and `RabbitReplyPublisherTest` (each outcome, mocked), and
    `OutboxPublisherIntegrationTest` (Testcontainers RabbitMQ and
    Postgres: published and marked sent, unsent while no queue is
    bound and delivered once one is, an unsent row from an earlier run
    published, a sent reply not published again).
  - `docs/credit-service.md`, the service's README and AGENTS.md, and
    the README's AI Use Summary updated.
- **Prompt(s):** The scope of issue #170, on
  `feat/reply-outbox-publisher`. The tool put three behaviours the
  design doc does not state to the team as questions with the options
  described factually and no recommendation: the poll interval, what a
  failed publish does to the rest of a poll, and how long to wait for
  a confirm.
- **Author review:** Team decisions, transcribed into
  `docs/credit-service.md`: a 1 s poll as a fixed delay, so a slow
  publish cannot overlap the next poll; at most 100 replies in a poll;
  a 5 s confirm wait; no cap on attempts for now. What a failed publish
  does to the rest of the poll was decided twice. First: an unreachable
  broker or a lost connection stops the poll, and a nack, a missing
  confirm or a returned reply is skipped. The tool's first version
  followed that, and told a lost connection from a broker nack by
  whether the nack carried a reason. **Revised team decision**, on the
  author's review of that version: only a returned reply is skipped,
  with a warning naming its event ID; a nack or a missing confirm stops
  the poll like an unreachable broker, because they mean the broker is
  in trouble, not that one reply is bad, so going on only multiplies
  the waits. The reason check is removed with its test, and two mocked
  tests run a poll of three replies through a confirm timeout and
  through a nack, with the replies after it left unsent. The author
  also reviewed and kept two choices the tool had raised: the publisher
  split into a scheduled class in `service/` and a `ReplyPublisher`
  interface implemented in `messaging/rabbitmq/`, which keeps broker
  types out of `service/`; and "published after a restart" tested as an
  unsent row already in the table when a poll runs, not by restarting
  the application. Each reply is marked sent in its own transaction. The first run of the integration test
  failed on the tool's own test queue (RabbitMQ 4 refuses a transient
  non-exclusive queue); it is now durable. A run in the local compose
  stack (rows written to the outbox by hand, a temporary queue bound
  to `credit-events`, RabbitMQ stopped and started) showed the template
  logging its own warning for every returned reply, so the publisher
  now registers a returns callback that does nothing. `./mvnw test`:
  147 tests, 0 failures, with Docker. Pending pull request review.

## 2026-10-11 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate, debug
- **Scope:** order-service JPA skeleton — `foc.order.model.Requests`
  (table `requests`) and
  `foc.order.repository.RequestsRepository`; order-service README
  status paragraph; README AI Use Summary. Also diagnosed a failing
  `docker compose up` build (no file changes).
- **Prompt(s):** "Can you add basic jpa skeleton for order? In
  particular I would like a table named requests". The tool mirrored
  the supplier-service `Suppliers` / `SuppliersRepository` pattern,
  including its auto-increment `Long` id. Then "I would like to add
  these columns:" with the author's ER diagram of the `requests`
  table — the tool transcribed those columns, types and nullability
  into the entity (bigint -> `Long`, text -> `String`, int ->
  `Integer`, timestamptz -> `Instant`, version -> `@Version`) and
  added or changed none.
  Earlier in the session: "I am getting this error when running docker
  compose up" — traced to antivirus HTTPS scanning replacing Maven
  Central's certificate inside the build container.
- **Author review:** Table name and schema given by the author.
  `./mvnw test` and the image build were not run by the
  tool (no JDK on the machine; Maven Central unreachable from
  containers), so both remain for the author to run. To be reviewed
  via pull request.

## 2026-10-10 — Aung Ko Khant
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** order-service Spring Boot scaffold — `pom.xml`,
  `OrderServiceApplication`, context-load test, main/test
  `application.yaml`, `Dockerfile` (replacing the earlier placeholder),
  Maven wrapper and `.gitignore`/`.gitattributes` copied from
  supplier-service, service README; `compose.yaml`
  order-service/order-db wiring; `.env.example` Order Service section
  and `VITE_ORDER_SERVICE_URL`; `AGENTS.md` port-table row; README AI
  Use Summary.
- **Prompt(s):** "Help me scaffold order service". The tool mirrored
  the supplier-service / notification-service skeletons. Open points
  were decided by the author via neutral options Q&A: PostgreSQL 17 for
  the order DB (`docs/architecture.md` listed the engine as undecided);
  host ports 8088 (service) / 5436 (DB); scope = skeleton plus broker
  wiring (`spring-boot-starter-amqp`, the `foc-contracts` dependency
  and Dockerfile stage, RabbitMQ env/depends_on in compose) with no
  entities, endpoints or publisher code.
- **Author review:** All decisions made by the author during the
  options Q&A; `docker compose config` validated by the tool. The
  image build and `./mvnw test` were not run by the tool (no JDK on the
  machine, and Maven Central was unreachable from containers), so both
  remain for the author to run. To be reviewed via pull request.

## 2026-10-11 — Ryan Ang (PR #175 review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** review assistance, refactoring, tests, docs
- **Scope:** `credit-service/`, `docs/credit-service.md`, GitHub issues
  #171, #173 and #174.
  - `SettleResult.NO_RESERVATION`, returned by a transfer that finds no
    record; `INVALID_STATE` stays for a record in the wrong status. A
    release with no record is unchanged.
  - `RequestEventHandler.settle` is a switch with no default;
    `NO_RESERVATION` throws the new `ReservationNotFoundException`, so
    the event rolls back and takes the retry path.
  - Tests: the no-record transfer split from the released-or-rejected
    one in `CreditOperationsServiceTest`, and the transfer-against-release
    race removed earlier the same day restored; a mocked and a
    real-database handler test that a completion with no record throws,
    the second also paying the courier once the reservation exists.
  - `transfer` answers `INVALID_STATE` for a null or blank courier,
    checked before the record is looked up; `release` answers it for a
    null or blank requester when the request has no record.
  - The four log lines (reserve, transfer, release, provision) are
    written from an after-commit hook; the class comment, the service
    README and the PR body say "one line per operation that changes
    credits".
  - `application-test.yaml` pins the sign-up amount and the lifetime;
    the new `CreditOperationsConfigurationTest` covers non-default
    values and the below-1 refusals; the handler's real-database test
    checks that a committed reservation logs and a rolled-back one does
    not.
  - `V1__baseline.sql`: the `credit_reservation` comment only (what
    `amount` and `settled_at` hold on a `REJECTED` row). No statement
    changed, though the file's Flyway checksum does.
  - `docs/credit-service.md`: the event table, "What each operation
    answers" and a failure-table row for the early completion; the
    expiry window and the expiry instant moved from "Open items" into
    D4 and the lots section, with the daily expiry run (D12) and the
    Asia/Singapore display; the `REJECTED` row's `amount` and
    `settled_at` in the schema section; the lot table and expiry
    paragraph reworded for the configurable lifetime.
  - Issues: #171 gains the acceptance criterion for a completion with
    no record; #173 says the expiry run is daily; #174 says the expiry
    is displayed in Asia/Singapore.
- **Prompt(s):** Asked the tool to go through the PR's review comments;
  it checked each against the branch and listed six that needed a
  decision. These decisions were given to implement: keep the
  amended V1; restore the race test; retry a completion that finds no
  record, with the result name, the exception name, the tests and the
  doc and issue wording specified; and no code change for expired lots
  awaiting the run, the expiry instant, or `REJECTED` rows, each to be
  written into the doc as decided. A second set of team answers
  followed for three more comments: guard a missing courier before the
  lookup and a missing requester on release, returning `INVALID_STATE`,
  with a missing requester on reserve left to #171; keep `getOrCreate`
  unlogged and soften the logging claim; and log after commit, with a
  sketch of the hook.
- **Author review:** The tool verified the
  reviewers' findings, implemented the decisions and transcribed them.
  The after-commit hook is the team's sketch, used as given. The tool
  chose the code-comment and exception-message wording, which doc
  paragraphs carry each decision, and the shape of the configuration
  and log tests. The release guard applies only when the request has no
  record, so a release naming no requester still returns the credits
  of a `HELD` record to the requester on it: the tool's reading of the
  decision, since confirmed by the team as the one meant. The three readings the
  entry below leaves "for the author to confirm" are now decided as
  implemented. `./mvnw test` on JDK 21: 127 tests, 0 failures, with
  Docker. Pending pull request review.

## 2026-10-11 — Ryan Ang (credit operations: team review fixes, issue #169)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** review assistance, refactoring, tests, docs
- **Scope:** `credit-service/`, `docs/credit-service.md`, `compose.yaml`,
  `.env.example`, GitHub issue #173.
  - Flyway `V1__baseline.sql` amended: `lot_id` on a slice (foreign key
    to its lot), a lot allowed at `remaining = 0`, and both lot indexes
    made partial on `remaining > 0`. First written as a V2 migration,
    then folded into V1 on the author's instruction. `CreditReservationSlice`
    gains `lotId`; the schema and mapping tests cover the change.
  - `CreditOperationsService`: a release adds each slice back to the lot
    it came from instead of creating lots; a drained lot is kept and the
    spend-order query skips it; the sign-up amount and the earned-credit
    lifetime read from `credit.sign-up-credits` and
    `credit.earned-credit-lifetime-months` (`CREDIT_SIGN_UP_CREDITS`,
    `CREDIT_EARNED_CREDIT_LIFETIME_MONTHS`), refused below 1 at startup;
    one log line per operation (request, users, amount, outcome) and
    one per account created.
  - `ReservedRepository.insert`: the insert that never overwrites, moved
    out of the service so it can be called twice in a test.
  - `CreditOperationsServiceTest`: a release refilling the same lot rows,
    repeated reserve and release adding no lots, a release into a lot
    past its expiry, a transfer to the requester themselves, the record
    inserted twice, and the five races repeated five times each. A
    sixth race (a transfer against a release of the same request) was
    added from a further review and then removed at the author's
    instruction: the Order Service's state machine publishes only one
    ending per request, so the scenario is not reachable in normal use.
  - `RequestEventHandlerCreditOperationsTest`: the handler over the real
    operations with only the outbox mocked (a reservation commits with
    its reply, or rolls back entirely when the reply cannot be stored;
    completion, cancellation, expiry-first). Added on the tool's own
    re-review of the change, which found no defect in the code.
  - `docs/credit-service.md` (D4, D6, Schema, the lot and slice rows, the
    lock rule, the release and same-user transfer rules, the
    lot-expires-while-held row), the service's README and AGENTS.md.
    Then, at the author's instruction, a section describing how lots
    are spent, returned and expired as the code does it now, with the
    expiry granularity and the expired-lot window added to "Open
    items" as undecided.
  - Issue #173 (scheduler): the account-lock rule and the
    `remaining > 0` filter added to its scope.
  - The team wiki's D3 design page (outside this repo): Part 2 §1 to §3
    and Part 3 §6 brought in line with the design doc as changed here
    (the `lot_id` column and kept lots in the schema diagram, release
    to the original lot, the two configuration values, the operation
    log line), cited to issue #169 and marked as on this branch until
    it merges. Pushed at the author's request.
  - Merge-conflict markers left in this file and in the root README by
    the PR #168 merge removed, keeping both sides (the first commit on
    this branch).
- **Prompt(s):** Make
  the two constants properties, log each operation, pin a same-user
  transfer, add a collision test that needs no threads, and return
  released credits to their original lots; then chose, of the two ways
  the tool offered to find the original lot, the foreign key with
  drained lots kept; then, since only the author had run the service,
  asked to fold the schema change into V1 instead of keeping a V2.
- **Author review:** All five changes and the choice of the foreign key
  over matching lots by their dates are the team's decisions, and
  amending V1 instead of adding a V2 is the author's, made knowing that
  a `credit-db` volume created from the earlier V1 fails Flyway's
  checksum and must be recreated (the service README says how); the tool
  implemented them and transcribed them into the design doc. The tool
  chose the property and environment variable names, the constraint and
  index names, the log wording, and making the expiry-run index partial
  as well as the spend-order one. Still **for the author to confirm**
  from the first draft: "3 months" is three calendar months in UTC; a
  `REJECTED` record stores the reward that was asked for and has
  `settled_at` set; a lot past its expiry but not yet moved to the pool
  is still spendable. A slice still carries `earned_at` and
  `expires_at`, which its lot now also gives. `./mvnw test` on JDK 21:
  113 tests, 0 failures, with Docker. The image was built and the service started once with `docker compose`: Flyway applied the amended V1, health answered UP and an unauthenticated request got 401; the containers and the database volume were then removed. The record-insert test was seen
  failing with `insert` swapped back to `save`, and passing once
  restored. Pending pull request review.

## 2026-10-11 — Ryan Ang (credit-service: credit operations, issue #169)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs), explain
- **Scope:** `credit-service/`, GitHub issues.
  - GitHub issues #169 to #174: one task issue per unbuilt component
    of `docs/credit-service.md` (credit operations, reply outbox and
    publisher, event listener, read API, scheduler, web balance and
    history), each citing the requirement issues it serves. Only the
    `service:` labels were set; priority, sprint and assignee are left
    to the team.
  - `CreditOperationsService`: the real `CreditOperations` bean
    (get-or-create, reserve, transfer, release), replacing
    `NotImplementedCreditOperations`, which is deleted with its case in
    `NotImplementedStubsTest`.
  - Repository queries: an insert-once account (`on conflict do
    nothing`), locking reads of an account and a reservation, a user's
    lots in spend order, a reservation's slices.
  - `CreditOperationsServiceTest` (22 tests, Testcontainers Postgres):
    every row of "What each operation answers", the lot rules, four
    races, and after each test the checks of "How their consistency is
    maintained".
  - The service's README and AGENTS.md, and the `CreditOperations`
    header, updated for the implementation.
- **Prompt(s):** Asked to open issues for the pieces citing the requirements;
  then to implement the first issue on `feat/credit-operations`.
- **Author review:** The outcomes per reservation status, the lock
  order, the lot spend order, the history rows and the sign-up amount
  are the team's design (`docs/credit-service.md`); the tool wrote the
  code to it. Where the doc is silent the tool took the plainest
  reading, and each is **for the author to confirm**: the sign-up
  amount (5) and the earned-credit lifetime (3 months) are constants in
  the class, not configuration; "3 months" is three calendar months in
  UTC; a `REJECTED` record stores the reward that was asked for and has
  `settled_at` set; slices are kept after a transfer or release, not
  deleted; a lot already past its expiry but not yet moved to the pool
  is still spendable. The first test run found a defect in the tool's
  own first draft (the repository's `save` merged a new record over one
  a concurrent delivery had just committed); records are now inserted
  with `persist`, and the two-first-deliveries test covers it.
  `./mvnw test`: 80 tests, 0 failures, with Docker; the new class run
  three more times with the same result. Pending pull request review.

## 2026-10-10 — Ryan Ang (PR #168 review fixes)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** review assistance, refactoring, tests, docs
- **Scope:** `credit-service/`, `docs/credit-service.md`, `compose.yaml`,
  `.env.example`.
  - `ReserveResult`: `REJECTED` split into
    `REJECTED_INSUFFICIENT_CREDITS` and `REJECTED_INVALID_AMOUNT`;
    `CreditOperations.reserve`'s comment states the reward-below-1 rule.
  - `RequestEventHandler`: the reply chosen by a switch expression over
    the result, the reason taken from the result, a warning log (event
    type and `eventId`) on a request event with no handler, and the
    duplicate comment corrected.
  - Tests: `RequestEventHandlerTest` (a relative time bound, the two
    refusals); new `ConsumedEventsHandledTest` (every bound event
    reaches an operation) and `RequestEventHandlerTransactionTest` (the
    handler through Spring's proxy); `RabbitMqTopologyIntegrationTest`
    (unbound keys published first on one channel, fixture read as
    bytes); `DomainEventMessageConverterTest` (a named fixture guard).
  - `application.yaml`, `.env.example`: the attempts setting described
    as not read yet; the below-5 s bound on the retry delay.
  - `docs/credit-service.md`: the reserve results, the duplicate-reply
    wording, the unhandled-event rule, the NFR1.1 row.
  - Disclosure headers on `compose.yaml`, `credit-service/AGENTS.md`
    and `credit-service/README.md` brought up to this PR's scope.
- **Prompt(s):** Summary: asked for the review comments on PR #168 to be
  checked against the code. The tool confirmed all eleven and put two
  back as decisions (how the rejection reason is carried, what an
  unhandled event does). The decisions were then given in writing, with
  where the tying test goes and what the log names, and the tool was
  asked to apply them with the remaining fixes.
- **Author review:** The two decisions are the team's, from the
  reviewer's options: the result carries the rejection reason (and the
  rule is also stated on `reserve`), and an unhandled event stays
  acknowledged, with a warning log and a build-time test instead of
  dead-lettering. The database keeps its single `REJECTED` status. The
  tool made no design choice; it chose the two enum names' comments,
  the log wording and the test mechanics. The Copilot finding (the
  transaction never exercised) was not among the reviewer's blocking
  items; its test was added on the tool's suggestion. `./mvnw clean
  test`: 59 tests, 0 failures, with Docker, the broker test run. The
  three guarding tests were each seen failing with the code broken on
  purpose (no `@Transactional`, a missing handler arm, a fifth
  binding) and passing once restored. Pending pull request review.

## 2026-10-10 — Ryan Ang (credit-service: request event handler and broker topology)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (code + tests + docs), built against the
  `CreditOperations` / `ReplyOutbox` stubs
- **Scope:** `credit-service/`, `docs/credit-service.md`, `compose.yaml`,
  `.env.example`, and the D3 wiki page.
  - `service/RequestEventHandler` (+ `Outcome`): one transaction per
    request event; reserve, transfer or release, and the reply enqueued
    for a reservation. `RequestEventHandlerTest` with both interfaces
    mocked.
  - `messaging/rabbitmq/`: `DomainEventMessageConverter` and
    `RabbitMqTopologyInitializer` copied from notification-service
    (package, and the initializer's property name, changed);
    `RabbitMqTopology` (work, retry and dead-letter queues, four
    bindings from `EventTypeRegistry`, both exchanges); converter
    wiring. `RabbitMqTopologyTest`, `DomainEventMessageConverterTest`,
    and `RabbitMqTopologyIntegrationTest` against a Testcontainers
    RabbitMQ (new test dependency), following notification-service's
    integration test: the four keys reach the work queue and no other,
    the retry queue returns an event, a rejected one lands in the DLQ.
  - `CreditServiceApplication`: `@EnableScheduling`.
  - `application.yaml`: manual acknowledgement, correlated confirms
    with returns and the mandatory flag, retry delay and attempts,
    `accept-float-as-int: false`. Test profile: listener auto-startup
    and topology provisioning off.
  - `docs/credit-service.md`: D10, D11, the handler row, "What each
    operation answers", three failure rows, F1.2.1 and NFR1.1; and a
    "Read API contract" section (`GET /credits/me`,
    `GET /credits/me/history`, the fields, filters, paging, fixed
    order, errors and implementation notes).
  - `compose.yaml`, `.env.example`: `CREDIT_RETRY_TTL_MS`,
    `CREDIT_RETRY_MAX_ATTEMPTS`. Service README and AGENTS.md.
  - The team wiki's D3 design page (outside this repo): its Credit
    content brought in line with `docs/credit-service.md`.
- **Prompt(s):** Gave a four-part written plan (transaction ownership,
  copying the messaging code, queues and bindings, what each operation
  answers per record status) and asked for it to be checked for issues
  before building. Then answered the issues raised and set the scope:
  this branch is the handler, converter copy, topology, config and doc
  edits; the real operations, listener and outbox publisher are later
  slices. Asked next for `@EnableScheduling`, a real-broker topology
  test and the read API's REST contract in the design doc, checked
  against the D3 wiki; then to bring the wiki's Credit content in line
  with the design doc.
- **Author review:** Every decision here is the author's, from the
  written plan and the answers to the check: the handler's shape, copy
  rather than share, the queue names and four bindings, declaring both
  exchanges, the retry values (2000 ms, 5 attempts), the results table,
  the mandatory flag for replies, refusing a fractional reward, and the
  history rules for refusals. The check raised four points the plan did
  not cover (an unroutable reply confirmed and lost, where the reply is
  serialised, the `PROVISION` row on a refusal, a test switch for the
  publisher); the author decided each. The tool chose only the wording
  of the reward-below-1 reason. The fractional-reward test was run
  failing before the Jackson setting was added and passing after.
  `@EnableScheduling` and the real-broker test were added on the
  author's follow-up instruction. `./mvnw clean test`: 53 tests, 0
  failures, the broker test run, not skipped. The read API contract is
  the author's, supplied in writing
  after the tool checked the D3 wiki page and found it gave no paths,
  fields or parameters; the tool transcribed it, raised two gaps (the
  format of `from` and `to`, a negative `page`), and the author decided
  both. The wiki edits follow the author's list of the sections that
  contradicted the design doc: decided items are cited to
  `docs/credit-service.md`, #163, #166 and #168, and Order-side
  consequences are flagged for the Order owner rather than decided. The
  author approved the push to the wiki. Pending pull request review.

## 2026-10-10 — Leong Wei Zhi (PR #149 re-review @Sinnez1: the empty-code regression and three nits)
- **Tool:** Claude Code (Opus 5)
- **Mode:** debug (review fixes)
- **Scope:** `web`: `features/user/otp.ts`,
  `components/{EditAccountCard,ChangePasswordCard,EmailChangeModal}.tsx`,
  `test/profile.test.tsx`.
- **Prompt(s):** Asked to address @Sinnez1's re-review of the #167
  refactor. The regression he found: sharing the code made `gate.live`
  mean "the account holds a code", not "this card has one typed", so the
  Edit Account card's Save could send `otp: ""` — after cancelling its
  own code step, or when the Change Password card had requested the code
  — and the untyped 400 that answers a blank otp routes to `amend`,
  leaving the card resending the same empty code with no boxes to type
  into. The hook answers `complete` now and the card opens its code step
  unless the digits are filled, as the password card already did; its
  hint and button follow (`Continue`, not `Save`, when there is nothing
  to send). Three nits: a refused resend kept blanking an expiry already
  known, so it is preserved; the expiry countdown's last tick now lands
  on the expiry instead of up to 15s past it, which had `live` outliving
  the code; and `resendWait` is actually used by EmailChangeModal, as
  problemTypes.ts claimed — with `deadline` replacing its local copy of
  the same helper. He also asked for a merge with main (conflicts since
  #162), logged separately above.
- **Author review:** One half of his expiry nit is deliberately not
  fixed: a code learnt of only through a cooldown 429 has no quoted
  life, so `live` cannot time it out without either assuming
  user-service's TTL in the web (duplicating server config, which would
  lie silently if the server's `otp.ttl` changed) or the 429 carrying
  the remaining life — a user-service contract change, and so the
  team's call, not an agent's. What the author took instead is the
  user-visible half: with no quoted life the cards say a code was sent
  without calling it valid, and the path self-heals (the server answers
  `otp-expired`, which restarts the card). Raised in the PR reply for
  @Sinnez1 to decide. Two regression tests cover the empty-code bug in
  both shapes (requested from the other card, and cancelled here), and
  both were checked to fail against the pre-fix submit path. Vitest
  (97 tests), eslint, tsc and prettier clean; reviewed via pull request.

---
## 2026-10-10 — Leong Wei Zhi (merging main into feat/profile-screens, second time)
- **Tool:** Claude Code (Opus 5)
- **Mode:** refactor (merge conflict resolution)
- **Scope:** `README.md`, `ai/usage-log.md`, `web/src/test/auth.test.tsx`
  — the same three ledgers as the previous merge, conflicting only
  because each side appended at the same place. No code conflicted.
  (1) `auth.test.tsx`: both sides added header disclosure lines; all four
  kept, in date order. (2) `README.md` AI Use Summary: both continued the
  same sentence, so the shared "neutral-options Q&A and recorded in
  `ai/usage-log.md`" prefix is kept once and the clauses chained — this
  branch's profile screens (#112/#149/#167) first, since they follow the
  #92 account-update clause they build on, then main's #154/#138
  follow-ups (PR #162), then the Credit Service contracts clause (PR
  #163) both sides already shared; the changelog comment keeps all of
  both sides' lines. (3) `ai/usage-log.md`: every entry from both parents
  kept with each side's internal order untouched, this branch's block
  first at each joint; two `---` separators are the only lines authored
  here.
- **Prompt(s):** Asked to address @Sinnez1's PR #149 re-review, which
  also asked for a merge with main (conflicts since #162 went in).
- **Author review:** Checked mechanically, not by eye: a script re-parsed
  the merged log and compared each `## ` entry against both parents —
  59 entries from this branch and 58 from main, 0 missing and 0 altered,
  65 in the union. `web` suite 95 tests, eslint and tsc clean after the
  merge. Reviewed via pull request.

---
## 2026-10-10 — Leong Wei Zhi (merging main into feat/profile-screens)
- **Tool:** Claude Code (Opus 5)
- **Mode:** refactor (merge conflict resolution)
- **Scope:** Two documentation ledgers, both conflicting only because each
  side appended at the same place; no code conflicted. `README.md` AI Use
  Summary: both sides continued the same sentence from "made by the author
  via", so the shared "neutral-options Q&A and recorded in
  `ai/usage-log.md`" prefix is kept once and the two clauses chained — the
  `web/` profile screens (#112, PR #149) first, since they read directly
  off the #92 account-update endpoints clause above them, then main's
  Credit Service saga contracts (PR #163), whose closing text is
  unchanged. `ai/usage-log.md`: all five new entries kept and ordered
  newest-first, which interleaves the two sides rather than stacking them
  — this branch's 2026-10-07 #112 entry sorts below main's 2026-10-08
  foc-contracts entry. Nothing was dropped or reworded on either side; the
  only line authored here is one `---` separator at the new joint.
- **Prompt(s):** Asked to fix the merge conflict on PR #149.
- **Author review:** Verified mechanically rather than by eye: every
  `## ` entry from both parents is present in the merge (55 = the union of
  the branch's 53 and main's 51), each entry's body is byte-identical to
  its parent, no entry appears that is in neither parent, and the five
  date inversions remaining in the file are all pre-existing in both
  parents (none introduced). `foc-contracts` 9 tests and `web` 87 pass
  after the merge. `notification-service` has a pre-existing flaky
  failure unrelated to this merge, which touched no code: with Docker
  running, `RetentionPurgeSchedulerTest` (and sometimes
  `IdempotentEventProcessorTest`) fail to start the Rabbit listener
  registry, and `origin/main` alone fails the same way — 33 run/2 errors
  and 36 run/1 error on two runs of the same commit. With Docker stopped
  the suite is 36/36 with the 3 Testcontainers tests skipped. Reviewed
  via pull request.

---
## 2026-10-10 — Leong Wei Zhi (issue #167: one gate code for both profile cards)
- **Tool:** Claude Code (Opus 5)
- **Mode:** refactor
- **Scope:** `web`: `features/user/otp.ts` (new `useGateCode` hook and a
  shared `deadline` helper), `problemTypes.ts` (`resendWait`),
  `components/{ProfileSection,EditAccountCard,ChangePasswordCard}.tsx`,
  `test/profile.test.tsx`.
- **Prompt(s):** Asked to do issue #167 — @Sinnez1's two remaining PR
  #149 follow-ups — inside the same PR. user-service keeps ONE
  `account_update_otps` row per account, but each profile card held its
  own copy of it, so a code one card spent or replaced left the other
  promising a code that was gone and spending attempts on one that had
  been replaced; the two cards' `POST /users/me/otp` paths had also
  drifted apart. `useGateCode()` now owns the row, the digits typed for
  it, the request (one call for the first send and the resend, since
  user-service treats a repeat as the resend), the cooldown-429 "a code
  already exists" answer, and the `GateFailure` routing; ProfileSection
  runs it once and hands it to both cards, which keep only what is
  theirs — their fields, whether the code step is showing, and their
  messages. Confirming an email change spends it too, because
  user-service deletes the gate row along with the old address. The
  shape (hook in `otp.ts`, instantiated once above the cards,
  EmailChangeModal keeping its own `pending_email_changes` row) is the
  reviewer's, recorded in #167 before the work started.
- **Author review:** The refactor landed with the 91 existing tests
  unchanged and passing, which is the regression evidence for "behaviour
  preserved"; two cases were added for the shared code — one card using
  a code the other requested without a second `POST /users/me/otp`, and
  one card asking for its own once the other spent it. Both were
  mutation-checked rather than just run: the first fails if each card is
  given its own hook (the pre-#167 split), the second if `spent()` stops
  clearing the shared row. The email-change resend still has its own
  request path — a different row, endpoint, response and failure rules —
  and shares only the cooldown detection (`resendWait`) and the
  countdown. Vitest (93 tests), eslint, tsc and prettier clean; reviewed
  via pull request.

---
## 2026-10-10 — Leong Wei Zhi (PR #149 review @Sinnez1: shared /users/me, delete dialog, gate-code failures)
- **Tool:** Claude Code (Opus 5)
- **Mode:** debug (review fixes)
- **Scope:** `web`: `features/user/AuthProvider.tsx`, `useAuth.tsx`,
  `profileApi.ts`, `problemTypes.ts`,
  `components/{ProfileSection,DeleteAccountModal,EditAccountCard,ChangePasswordCard,CodeStep,OtpVerificationModal}.tsx`;
  `test/profile.test.tsx`, `test/auth.test.tsx`.
- **Prompt(s):** Asked to address @Sinnez1's review of PR #149. The two
  flagged items: (1) the delete dialog could be dismissed mid-request —
  Cancel (and the ✕ / backdrop it shares a handler with) closed it while
  the DELETE was away, so `logout()` signed the user out with no warning
  and a failure's message landed on a dialog that was gone; both are
  disabled while it is pending. (2) the profile page asked
  `GET /users/me` for itself even though AuthProvider already holds that
  answer for the session, and its private copy meant an edit here left
  the nav bar and the `/admin` guard on pre-edit values; the page reads
  `useAuth().me` and hands each save back through a new `updateMe`, and
  the duplicate `profileApi.getCurrentUser` wrapper is gone. Also four of
  his follow-ups: a resend-cooldown 429 on save is classified `wait`
  (its own `GateFailure`) so the code stays in the boxes and `CodeStep`
  counts the quoted wait down on the submit button, instead of being read
  as a wrong guess; a code kept across a refusal is only offered while
  its own expiry stands; an untyped 429 ends a pending sign-up on its
  status alone; and the page's two email banners clear each other.
- **Author review:** The two remaining follow-ups — one gate code tracked
  separately by both cards, and the duplicated code-request logic — need
  a shared owner (@Sinnez1's suggested `useGateCode()` hook, instantiated
  once in ProfileSection); the author chose to track them as a follow-up
  issue rather than widen an approved PR, since today's behaviour
  degrades gracefully (a stale "still valid" hint costs one refused call,
  then the card asks for a new code). Four regression tests added — the
  dialog that cannot be dismissed mid-delete, the 429 countdown, a kept
  code that has expired, and the untyped 429 — each checked to fail with
  only its own fix reverted; the shared-`me` change is pinned by the
  existing username-save case, which now also asserts one
  `GET /users/me` per session. Vitest (91 tests), eslint, tsc and
  prettier clean; reviewed via pull request.

---
## 2026-10-09 — Leong Wei Zhi (PR #149 Copilot re-review: expired email change, test strength, disclosures)
- **Tool:** Claude Code (Opus 5)
- **Mode:** debug (review fixes) + docs (disclosures)
- **Scope:** `web`: `features/user/components/EmailChangeModal.tsx`,
  `register.tsx`, `test/profile.test.tsx`, and the author-review note
  filled in on the thirteen files added for #112; `README.md` AI Use
  Summary.
- **Prompt(s):** Asked to address the second round of Copilot findings on
  PR #149. (1) The confirm dialog offered "Resend code" once the parked
  change expired, but `AccountUpdateService.resendEmailChange` deletes an
  expired `pending_email_changes` row and answers `OTP_EXPIRED` — unlike a
  gate code, a dead row cannot be renewed — so the CTA could only fail;
  the dialog now says so and offers "Start over", which drops the
  snapshot (where the doomed call ended up anyway, a round trip later).
  (2) Two retained-code tests asserted `toHaveBeenLastCalledWith` with
  arguments the previous failed save had already produced, so they passed
  whether or not the second Save ran; they assert the call count now, and
  the same gap was fixed in the pre-existing taken-name test.
  (3) The `PasswordChecklist` import in `register.tsx` used double quotes
  and a semicolon, which `format:check` rewrites. (4) The disclosure
  duties in AGENTS.md were incomplete: the thirteen files added for #112
  still carried `Reviewed by: [pending]`, and the README AI Use Summary
  had no #112 entry at all.
- **Author review:** The review-note wording was the author's choice via
  an options round (2026-10-09) — `Author review: Leong Wei Zhi (via
  PR #149).`, the form already used for author-reviewed files in this
  repo, rather than a claim of completed teammate review (the PR's
  2026-09-29 approval predates the endpoint rewrite). The expiry case has
  its own test, rendering the dialog on a dead snapshot — the state it
  really meets, since the page's sweep clears expired snapshots on mount
  — checked to fail before the fix. The `about:blank` finding Copilot
  still lists as open was answered in the previous round inside
  `problemType()` and is covered by the `auth.test.tsx` case using that
  response shape. Vitest (87 tests), eslint, tsc and prettier clean;
  reviewed via pull request.

---
## 2026-10-09 — Leong Wei Zhi (PR #149 Copilot review: snapshot ownership + failure recovery)
- **Tool:** Claude Code (Opus 5)
- **Mode:** debug (review fixes) + generate (regression tests)
- **Scope:** `web`: `features/user/problemTypes.ts`, `types.ts`,
  `components/ProfileSection.tsx`, `EditAccountCard.tsx`,
  `ChangePasswordCard.tsx`, `EmailChangeModal.tsx`,
  `OtpVerificationModal.tsx`; `test/profile.test.tsx`,
  `test/auth.test.tsx`.
- **Prompt(s):** Asked to address Copilot's four findings on PR #149.
  (1) The pending-email snapshot used one browser-wide key with no
  account on it and outlived both logout and account deletion, so the
  next account to sign in was shown — and could confirm against its own
  account — an address somebody else had parked: `PendingEmailChange`
  carries `userId` now, the page reads only the signed-in account's own
  (another account's is left alone, an ownerless or expired one is
  dropped), the resend keeps the owner, and deleting the account clears
  its snapshot. (2) After a name-taken refusal the Edit card sits at its
  fields holding a live code; a retryable failure there cleared the
  digits without reopening the code step, so the next Save submitted an
  empty code with no boxes to type into — the retry branch now always
  returns to the step. (3) `problemType()` read Spring's placeholder
  `type: "about:blank"` as a real type, which made the sign-up dialog
  answer for AuthService's untyped post-flush refusal and stay open over
  a sign-up only the form could fix; `about:blank` is reported as untyped
  now, in the one helper. (4) An untyped 400 is the request body's own
  validation (`ProblemDetailAdvice.handleInvalidBody`), which runs before
  the service reads the code, so it is classified `amend` rather than a
  code retry: both cards return to their fields — the part that was
  refused — keeping the unspent code, where before a rejected password or
  address left the fields disabled behind a code step and cost the user
  their edits.
- **Author review:** Six regression tests added (account switch, an
  ownerless snapshot, amend-then-transient-failure, an untyped 400 on
  each card, and the `about:blank` sign-up refusal); each was checked to
  fail against the pre-fix code and pass after. Vitest (86 tests),
  eslint, tsc and prettier clean; reviewed via pull request.

---
## 2026-10-10 — Ryan Ang (credit-service scaffold: entities, repositories, interfaces)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (scaffold + tests + docs)
- **Scope:** `credit-service/` only, no business logic.
  - `entity/`: one JPA entity per Flyway V1 table (`CreditAccount`,
    `Reserved`, `CreditReservationSlice`, `CreditLot`,
    `CreditHistoryEntry`, `CommonPool`, `RedistributionRun`,
    `OutboxEvent`) and the `ReservationStatus` / `CreditHistoryType`
    enums, transcribed from `V1__baseline.sql`. IDs of other rows are
    plain columns, not JPA associations.
  - `repository/`: an empty `JpaRepository` per entity.
  - `service/`: `CreditOperations` and `ReplyOutbox` with the supplied
    signatures, `ReserveResult`, and `NotImplemented*` stub beans whose
    methods throw `UnsupportedOperationException("not implemented")`.
  - Tests: `EntityMappingTest` (each entity saved and read back against
    V1) and `NotImplementedStubsTest`.
  - `credit-service/README.md` and `AGENTS.md`; root `README.md` AI Use
    Summary clause.
- **Prompt(s):** Pasted the planned scaffold contents (skeleton, Flyway
  V1, entities, empty repositories, the JWT filter, and the two
  interfaces with their method signatures) and asked whether the
  scaffold PR had them, then to add the four missing pieces.
- **Author review:** The interface signatures are the author's, as
  pasted. `ReserveResult` was undefined; the author chose the
  three-value enum (`RESERVED`, `REJECTED`, `DUPLICATE`) from options.
  Entity class names follow `docs/credit-service.md` where it names
  them; `CreditReservationSlice` and `CommonPool` are not named there
  and follow their tables. `./mvnw clean test`: 21 tests, 0 failures.
- **2026-10-10, PR #166 Copilot review (schema):** asked to fix the
  second review's three findings, with the team decision for the first
  supplied as written text. `V1__baseline.sql`:
  `CHECK (amount > 0)` on `credit_reservation_slice` and an index on its
  `request_ref`, with two cases in `CreditServiceApplicationTests`.
  Tombstone finding (also the requester-provisioning item of #165):
  team decision to keep the foreign key and make get-or-create part of
  the release path, stated as one rule under D6. The tool transcribed
  it into `docs/credit-service.md` (D6, the event table's cancelled /
  expired "no record" cell, the paragraph under it, the F1.1 row, the
  slice row and ER diagram) and the `CreditOperations` javadoc. No
  implementation: `release` is still the stub, so the cancel-then-submit
  test waits for the real bean. `./mvnw clean test`: 23 tests, 0
  failures.
- **2026-10-10, PR #166 review (Leong Wei Zhi):** asked to look at the
  five findings and fix them. `CreditOperations.transfer` / `release`
  return a new `SettleResult` (`SETTLED`, `DUPLICATE`, `INVALID_STATE`)
  instead of `void`: the reviewer offered that enum or a documented
  exception, and the author chose the enum. `V1__baseline.sql`: the
  reviewer's partial index on unsent `outbox_event` rows, with a test.
  `docs/credit-service.md`: that index, and the two existing
  reservation CHECKs marked on the ER diagram.
  `CreditServiceApplicationTests`: the two negative cases made
  `@Transactional`. `foc-contracts` `RequestEventRecordsTest`: every
  request record must declare `note`, not only annotate it where
  present. Also removed three merge-conflict marker lines left in this
  file by the merge of `main`; both sides' entries are kept. Tests:
  foc-contracts 10, credit-service 24, 0 failures.
  

## 2026-10-09 — Ryan Ang (credit-service scaffold)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (scaffold + tests + docs)
- **Scope:** new `credit-service/` skeleton, plus its wiring:
  - Maven wrapper, `.gitignore`, `.gitattributes` copied from
    user-service; `pom.xml` (Boot 4.1.1, Java 21, web, JPA, Flyway,
    Postgres, security, validation, actuator, AMQP, foc-contracts,
    jjwt, Testcontainers); `Dockerfile` with notification-service's
    foc-contracts build stage.
  - `application.yaml` (credit-db datasource, Flyway with
    `ddl-auto: none`, RabbitMQ connection, JWT secret, CORS origin).
  - Flyway `V1__baseline.sql`: the eight tables from
    `docs/credit-service.md`'s "Database" section, with its keys and
    CHECK constraints and the common pool's single row.
  - JWT verification copied from supplier-service (verifier, filter,
    401/403 problem+json); `SecurityConfig` leaves only health open.
  - Tests (Testcontainers Postgres, `test` profile): context and V1
    schema checks, and the filter chain's 401/pass-through cases.
  - `compose.yaml` (credit-service + credit-db + volume),
    `.env.example` (Credit Service section, `VITE_CREDIT_SERVICE_URL`),
    root `AGENTS.md` port table, the service's README and AGENTS.md.
- **Prompt(s):** Asked whether a credit-service scaffold existed, then
  to build one (app, credit-db in compose, Flyway V1, Dockerfile, JWT
  filter) on a new branch.
- **Author review:** The author chose, via options Q&A: host ports
  8088/5436 and postgres:17; including the messaging dependencies now
  (no listener or outbox code); the full schema in V1; Testcontainers
  for tests. The schema itself is the team's design, transcribed; the
  tool chose only SQL details the doc leaves open (varchar lengths,
  NOT NULL where the doc marks nothing nullable, constraint/index
  names, and indexes for the queries the doc lists). The RabbitMQ
  health check is off in the test profile only, since tests run
  without a broker. `./mvnw test`: 10 tests, 0 failures;
  `docker compose config` and `docker compose build credit-service`
  succeed. Pending pull request review.
- **issue #165 item 1:** `note` marked `@Nullable` on all nine
  request records (author's decision: a request may have no note, and a
  missing display field must not dead-letter a reservation, transfer or
  release). `request-submitted.example.json` now carries `"note": null`
  so the contract tests bind the tolerant path; a new
  `RequestEventRecordsTest` case keeps every `note` `@Nullable`. The
  foc-contracts README/AGENTS and `docs/notification-service.md`
  updated to match. Tests: foc-contracts 10, notification-service 36
  (Docker running, none skipped), credit-service 10 — 0 failures.
- **2026-10-10, PR #166 Copilot review:** credit-service security, as
  user-service already does it: `SessionCreationPolicy.STATELESS`; the
  JWT filter built in `SecurityConfig` instead of being a `@Component`,
  so it runs only in the security chain; and the 401 carries
  `WWW-Authenticate: Bearer`. `SecurityConfigTest` now asserts the
  header, that no request creates a session (checked to fail without
  the stateless policy), and that the filter is not a bean. 11 tests,
  0 failures.

## 2026-10-10 — Ryan Ang (PR #162 re-review fixes, Leong Wei Zhi's re-review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (review fixes: code + tests + docs)
- **Scope:** `user-service/` and `web/`.
  - `PostgresTestContainer`: `user.retention.purge-cron` and
    `owner.setup.token` added to the `@TestPropertySource` pins; the
    token moved out of `application-test.yaml`.
  - `DemoAccountsSeeder`: a run that loses an insert race seeds once
    more; a second loss is logged as a warning that no longer says the
    other instance finished. `DemoAccountsSeederTest`: one case for the
    retry, one for losing twice.
  - `OwnerSetupService`: the unconfigured-token 503 is logged
    server-side, naming the setting; the response stays generic.
  - `web` `login.tsx`: clearing the sign-up state keeps the URL's query
    and hash; `auth.test.tsx` has a case for it.
  - Root `README.md`: AI Use Summary clause updated.
- **Prompt(s):** Asked to look at the comments on PR #162, then to fix
  them, with the seeder re-running its seed after a conflict.
- **Author review:** For the seeder the reviewer offered three options
  (per-account transaction or `ON CONFLICT DO NOTHING`, re-running the
  seed once, or only rewording the log line); the author chose the
  re-run. The other fixes follow the reviewer's own suggestions.
  `./mvnw clean test`: 306 tests, 0 failures; `OwnerSetupControllerTest`
  and `AccountPurgeSchedulerTest` also pass with `USER_PURGE_CRON`
  exported empty and a wrong `OWNER_SETUP_TOKEN`. Web: `npx vitest run`,
  65 tests pass. Pending author review of the diff.
- **Second re-review (same day, head `d5e5734`):** asked to look at the
  new findings and fix them. `OwnerSetupService`: the unconfigured-token
  line is logged once per process, since the endpoint takes no
  credentials (the reviewer's one-shot flag suggestion);
  `OwnerSetupServiceTest` checks three calls write one line.
  `ProblemDetailAdvice`: a 405 handler shaped like the 415 one, keeping
  Spring's `Allow` header; `AuthControllerTest` has a case for it. The
  406 is left alone: the reviewer found it returns an empty body and
  called it a separate question. `./mvnw clean test`: 308 tests, 0
  failures.

## 2026-10-09 — Ryan Ang (PR #162 review fixes, Leong Wei Zhi's review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (review fixes: code + tests + docs)
- **Scope:** `user-service/` only.
  - `PostgresTestContainer`: the values the tests assert on
    (`user.retention.days`, `user.jwt.access-token-ttl`,
    `user.web-allowed-origin`, `user.mail.from`, `user.otp.ttl`,
    `user.otp.max-attempts`, `spring.mail.host` / `port`) pinned with
    `@TestPropertySource`, so exported or empty environment variables
    can't change them or stop the context starting.
  - `ProblemDetailAdvice`: the 415 returns Spring's headers with the
    body, keeping `Accept`; `AuthControllerTest` asserts it.
  - `LoginAttempts.record`: back to `find` then
    `refresh(PESSIMISTIC_WRITE)`, so the read is fresh even when the
    `User` is already in the persistence context; `AuthServiceTest`'s
    stub follows.
  - Root `README.md`: AI Use Summary clause updated.
- **Prompt(s):** Asked to look at the review on PR #162, then to fix
  all of it on the PR branch.
- **Author review:** The login read was a performance-versus-
  robustness trade-off; the author chose restoring `refresh` (one
  extra query per attempt) over a guard test or no change, via
  options Q&A. The other two fixes follow the reviewer's own
  suggestions. `./mvnw clean test`: 305 tests, 0 failures. The four
  environment-sensitive classes (`AuthControllerTest`,
  `AccountPurgeSchedulerTest`, `AccountUpdateControllerTest`,
  `DemoAccountsSeederTest`) also pass with `OTP_TTL`,
  `OTP_MAX_ATTEMPTS`, `JWT_ACCESS_TOKEN_TTL`, `MAIL_PORT` and
  `MAIL_FROM` exported empty and non-default `WEB_ALLOWED_ORIGIN`,
  `USER_RETENTION_DAYS` and `USER_SEED_DEMO`.

## 2026-10-09 — Ryan Ang (PR #163 review fixes)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (review fixes: one test + docs)
- **Scope:**
  - `notification-service/.../DomainEventContractTest.java`: producer
    and `requestId` checked through each event's domain marker
    (`RequestEvent` / `CreditEvent`); `parties` required present, not
    non-empty.
  - `foc-contracts`: `request-submitted.example.json` now names the
    requester in `parties`; README convention 1 reworded for saga
    reply events.
  - `docs/credit-service.md` / `.mmd`: pre-saga residue removed
    (ownership note, error-mapping row and label, lot spend order,
    closed-economy rule, legend), the two invariants stated, the
    Notification row updated.
  - `docs/architecture.md` / `.mmd`: Order states include `pending`
    and `rejected`, Notification row lists the saga events, Credit DB
    is PostgreSQL.
  - `docs/notification-service.md`: catalog description, the
    events-are-facts paragraph and the exchange list.
- **Prompt(s):** Asked to read the review comments on PR #163 and fix
  them.
- **Author review:** Three choices were the author's, made via
  options Q&A before any edit: `request.submitted` notifies the
  requester (rather than documenting it as notification-free), item 4
  states the reviewer's two proposed invariants (rather than being
  dropped), and the Order states in the diagram include `pending` and
  `rejected`. The convention rewording follows the team's saga
  decision (`docs/credit-service.md` D7-D8) and was requested by the
  convention's owner in the review. All other edits follow the
  reviewer's own suggestions. `./mvnw install` in `foc-contracts` (9 tests)
  and `./mvnw test` in `notification-service` (36 tests, 0 failures,
  0 skipped, with Docker running for the RabbitMQ Testcontainers
  tests) both pass.

## 2026-10-08 — Ryan Ang (foc-contracts: saga event contracts)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (records, fixtures, tests, docs)
- **Scope:** `foc-contracts/` only; no service code changed.
  - New records `RequestSubmitted`, `RequestRejected`
    (`events/request/`) and `CreditReserved`,
    `CreditReservationRejected` with the `CreditEvent` marker
    (`events/credit/`), one fixture each under `contracts/`.
  - `EventContracts.CREDIT_EVENTS_EXCHANGE` and four
    `EventTypeRegistry` entries.
  - `EventTypeRegistryTest` and `RequestEventRecordsTest`: the entry
    count, the identity-prefix check and the domain-marker check now
    cover the credit domain.
  - `README.md` and `AGENTS.md` of the module: record, exchange and
    package lists.
- **Prompt(s):** Asked to read the credit-service design doc and
  diagram, check a list of proposed contract additions against the
  current library, and then add them.
- **Author review:** The saga, the four identity strings and the
  exchange name are the team's design (`docs/credit-service.md` D7-D8);
  the field lists and the empty `parties` on the credit events are team
  decisions, confirmed by the author before the tool wrote anything.
  The tool made no contract decisions. `./mvnw test` passes in
  `foc-contracts`.
- **2026-10-09, PR #163 Copilot review:** `docs/credit-service.md`:
  typo and punctuation fixes, and D3 reworded to the author's own text
  (duplicates are detected by the one held-credits record per request,
  not by event ID). The schema is unchanged. The tool wrote no design
  content.
  `foc-contracts`: `reward` and both `amount` components changed
  from `int` to `Integer` (team decision) so a missing value is
  rejected. `docs/architecture.md` and `.mmd`: Credit Service rows and
  edges transcribed from the team's saga design (D6-D9). Root
  `README.md`: AI Use Summary clause for this PR.

---
## 2026-10-07 — Leong Wei Zhi (#112 profile screens on #92's real endpoints)
- **Tool:** Claude Code (Opus 5)
- **Mode:** refactor (contract swap) + generate (the two new code steps)
- **Scope:** `web`: `features/user/profileApi.ts` rewritten on the
  endpoints PR #157 shipped and `profileApiMock.ts` + the
  `VITE_MOCK_PROFILE_API` switch deleted; new `problemTypes.ts`,
  `otpCountdown.ts`, `components/CodeStep.tsx` and
  `components/EmailChangeModal.tsx`; `EditAccountCard`,
  `ChangePasswordCard`, `ProfileDetailsCard`, `ProfileSection` and
  `types.ts` updated; `OtpVerificationModal` now matches problem+json
  type URIs instead of detail sentences; `routes/index.tsx` AdminRoute
  wrapper restored; `test/profile.test.tsx` rewritten,
  `test/auth.test.tsx` problem helper extended; `web/AGENTS.md`.
- **Prompt(s):** Asked to plan and execute the swap of PR #149's
  provisional contract for the one #92 (PR #157) actually shipped.
  Design decided by the author via an options Q&A round (2026-10-07):
  (1) Change Password drops the wireframe's Current Password field and
  gains an OTP step, because the shipped `POST /users/me/password` takes
  `{newPassword, confirmPassword, otp}` and has no `currentPassword`;
  (2) the new-email confirmation is a modal, matching the sign-up
  dialog, rather than a third phase inside the Edit card;
  (3) the parked email change is snapshotted in localStorage, since
  user-service has no GET for the row and a reload would otherwise cost
  the user a fresh gate code; (4) all four ride-along follow-ups PR #157
  listed were taken in this PR.
- **Author review:** Deviations from `docs/wireframes/profile.png` are
  recorded in `web/AGENTS.md` next to the wireframe. The tool flagged two
  things found while swapping: this branch had flattened `/admin`'s
  `AdminRoute` wrapper (restored — main's role gate from issue #147), and
  one sign-up uniqueness refusal is still thrown untyped in
  `AuthService`, so the dialog keeps a single sentence fallback and the
  one-line user-service fix is left as a follow-up. Vitest (80 tests),
  eslint and build clean; walked end to end against docker compose +
  Mailpit; reviewed via pull request.

---
## 2026-10-05 — Ryan Ang (user-service + web: remaining #154 and #138 follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor + debug + tests + docs
- **Scope:** `user-service/` and `web/`; the seven items still open on
  issues #154 and #138, as listed in those issues (review follow-ups from
  PR #152, PR #155 and PR #135).
  - Test config: `src/test/resources/application.yaml` renamed to
    `application-test.yaml` and `PostgresTestContainer` activates the
    `test` profile, so tests load the main `application.yaml` (open-in-view
    off, the main Flyway settings) and the test file keeps only the setup
    token, the JWT secret and the 0s resend cooldown.
  - `FlywayMigrationTest` runs with the Spring-configured Flyway pointed
    at its own schema, so its baseline settings come from
    `application.yaml` instead of a hand-typed copy.
  - `PostgresTestContainer` pins `user.seed.demo=false` for every test
    class; `DemoAccountsSeederTest` turns it on with `@TestPropertySource`;
    the pin on `UserServiceApplicationTests` is gone.
  - `ProblemDetailAdvice`: 415 problem+json for an unsupported
    `Content-Type` (#138), with a test in `AuthControllerTest` and a line
    in `user-service/README.md`.
  - `LoginAttempts.record`: one locked `find` instead of `find` plus
    `refresh`; `AuthServiceTest`'s stub follows.
  - `DemoAccountsSeeder`: a run that loses an insert race to another
    instance is rolled back and logged as a warning instead of stopping
    startup; test in `DemoAccountsSeederTest`.
  - `web/src/features/user/login.tsx`: the "account created" navigation
    state is cleared once read, so a reload doesn't show the notice again;
    test in `auth.test.tsx`.
  - `web/src/shared/shell/NavBar.tsx`: the broken `border-gray-5=800`
    class on the Log In / Logout buttons replaced with `border-gray-300`,
    the credits badge's border.
- **Prompt(s):** Asked which #154, #138 and #147 items were still open,
  then to fix the seven open code items (the `jwt.ts` clean-up left out).
- **Author review:** Full user-service suite (241 tests, also with
  `USER_SEED_DEMO=true` exported) and the web suite (64 tests) run green;
  to be reviewed via PR.

## 2026-10-05 — Leong Wei Zhi (PR #157 Copilot review: Retry-After on exhaustion + spent-row docs)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (review fixes: contract + docs)
- **Scope:** `user-service/` — the Copilot findings on the spent-row
  follow-up. (1) The attempts-exceeded 429 now carries `Retry-After`:
  the exhausted row survives as spent and its replacement waits out the
  resend cooldown, so there is a real wait to quote (0 = request now).
  `OtpAttemptsExceededException` carries the remaining cooldown
  (`OtpResendTooSoonException`'s pattern), set at every throw site from
  the row's `lastSentAt`; `ProblemDetailAdvice` emits the header.
  (2) Docs caught up with the spent lifecycle: `ProblemTypes`'
  attempts-exceeded description no longer claims the operation was
  discarded, and the `AccountUpdateService` javadoc/comments that said a
  spent row "stays deleted" now describe the persisted-spent,
  cooldown-gated state. README's type table mentions the header.
- **Prompt(s):** (summary) Asked to address the latest Copilot comments
  on the PR (three "previously missed" findings; the two threads the
  overview lists as open were fixed in earlier commits and await
  resolution on GitHub).
- **Author review:** Full suite green (299 tests); the exhaustion 429s
  now pin Retry-After in both cooldown regimes (0 in the shared yaml,
  present at the real 60s), and the service tests pin the quoted
  seconds. Reviewed via pull request.

## 2026-10-05 — Leong Wei Zhi (PR #157 Copilot re-review: spent-row takeover + stale-email send)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (review fixes + tests)
- **Scope:** `user-service/` — the Copilot review of the spent-row
  change, two findings. (1) Treating a spent pending sign-up as
  dead-and-takeable resurrected the PR #150 hijack: `/auth/signup/verify`
  is anonymous, so anyone could burn a stranger's pending row with 5
  wrong guesses, wait out the cooldown, and replace it with their own
  password. A spent row now revives only for a repeat of its own request
  (`PendingSignup.reviveSpent`: fresh code and attempt budget, original
  expiry kept so the row's life stays bounded — no forever-hold); anyone
  else keeps the 409 until expiry. (2) `requestOtp` read the user row
  unlocked, so a concurrent email-change verify could commit between the
  read and the SMTP send, mailing a live gate code to the address the
  account just left (F2.1.1). All five account-update flows now take a
  `PESSIMISTIC_WRITE` lock on the caller's user row first
  (`getActiveUserWithLock`), one consistent lock order (user, then
  gate/pending rows) so the flows serialize without deadlock.
- **Prompt(s):** (summary) Asked to address the latest Copilot comments
  on the PR. The sign-up spent-row shape (same-request revival with the
  original expiry, over freezing the row until expiry) decided by
  Leong Wei Zhi via options Q&A; the tool flagged that Copilot's literal
  suggestion (spent rows take the expired branch for the same requester)
  would have reintroduced the PR #150 forever-hold, hence the kept expiry.
- **Author review:** Full suite green (299 tests, 1 new: a spent row
  with other details stays 409; the takeover test became the
  same-request revival case). Reviewed via pull request.

## 2026-10-05 — Leong Wei Zhi (PR #157 review: spent OTP rows + combined-PATCH race)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (review fixes + tests + docs)
- **Scope:** `user-service/` — @Sinnez1's PR #157 review, two fixes.
  (1) The guess that exhausts `OTP_MAX_ATTEMPTS` keeps its row as
  **spent** instead of deleting it, in all three OTP tables (gate codes,
  pending sign-ups, pending email changes): deleted, the next request
  found no `lastSentAt`, skipped the resend cooldown, and minted a fresh
  code and attempt budget at once — unlimited guesses and inbox
  flooding on a stolen JWT. A spent row refuses everything (sign-up's
  verify answers it like no row at all, same message and BCrypt cost, so
  nothing new is revealed) and is taken over like an expired one once
  the cooldown passes. (2) `updateAccount` flushes a username change
  through its typed catch before the email branch's uniqueness query
  auto-flushes the dirty row, so losing a username race in a combined
  username+email PATCH answers the documented `username-taken` 400
  instead of an unhandled 500. README's OTP sections updated; the two
  account-update test classes now clear their FK'd OTP tables after each
  test since spent rows survive. Also merged `main` into the branch
  (conflict in this log: both sides' entries kept).
- **Prompt(s):** (summary) Asked to resolve the latest PR #157 review
  comments and fix the merge conflict. The exhaustion shape
  (spent-until-cooldown over spent-until-TTL or delete-plus-separate-
  timestamp) and the scope (all three OTP flows, not just the two the
  review named) decided by Leong Wei Zhi via options Q&A.
- **Author review:** Full suite green (298 tests, 11 new — including
  end-to-end regressions in both real-cooldown classes pinning that
  exhaustion no longer bypasses the cooldown). Reviewed via pull request.

## 2026-10-02 — Ryan Ang (user-service: #154 review follow-ups)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor + tests + docs
- **Scope:** `user-service/` only, four items from issue #154 (PR #152 and
  PR #155 review follow-ups); no behaviour change beyond error bodies.
  - `ProblemDetailAdvice`: the `ResponseStatusException` and
    `MethodArgumentTypeMismatchException` handlers moved here from
    `AuthController` and `AdminController`, so `OwnerSetupController` and
    `ProfileController` answer those in problem+json too (e.g. a
    non-numeric `GET /users/{id}`, a wrong setup token, `CallerId`'s 401).
  - `DemoAccountsSeeder`: `Locale.ROOT` on the generated emails and
    usernames; comment on the `ADMINS`/`USERS` limits.
  - Tests: non-numeric id (400 problem+json) and a problem+json check on
    the non-numeric-principal 401 in `ProfileControllerTest`; a
    problem+json check on the wrong-token 403 in
    `OwnerSetupControllerTest`; a soft-deleted demo account is not
    re-seeded in `DemoAccountsSeederTest`.
  - `user-service/README.md`: "Old local databases" note on
    `baseline-on-migrate`; the wrong-type parameter 400 now mentions path
    parameters.
  - `OwnerSetupService`: the 503 for an unconfigured setup token now says
    only "Owner setup is unavailable", since the shared handler shows the
    reason to unauthenticated callers; `OwnerSetupServiceTest` checks it.
  - `SignupIdentifierTakenException`: header comment now names
    `ProblemDetailAdvice` as the handler that maps it.
- **Prompt(s):** Review #138 and #154 to determine fixes.
- **Author review:** Reviewed via PR.

## 2026-09-30 — Leong Wei Zhi (#92 account update flows)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `user-service/` — the F2 account-update flows:
  `POST /users/me/otp` (gate code to the current email, F2.1.1),
  `PATCH /users/me` (allow-list DTO — username applies, email parks in
  the new `pending_email_changes` table with a code to the new address,
  F2.1.2/F2.1.3), `POST /users/me/email/verify` + `/email/resend`,
  `POST /users/me/password` (server-side double-entry + policy re-check,
  F2.1.4/F2.1.5). New `AccountUpdateOtp`/`PendingEmailChange` entities +
  repos, `AccountUpdateService`, V4/V5 migrations, SecurityConfig route
  lines, purge sweeps, and problem+json `type` URIs on the OTP errors
  (the three OTP handlers moved from `AuthController` to
  `ProblemDetailAdvice`). Tests: `AccountUpdateControllerTest` (29),
  `AccountUpdateResendCooldownTest` (2), `AccountUpdateServiceTest` (10),
  plus purge/Flyway/auth/admin suites updated. README section.
- **Prompt(s):** Asked to plan and resolve #92. Contract decisions made
  by the author via two AskUserQuestion options rounds (options
  presented neutrally, no recommendations): backend-only scope; password
  change on its own OTP-gated endpoint with no `currentPassword`;
  double-entry checked server-side too; the gate code carried in the
  mutating request (verify-and-apply in one call); username+email may
  combine in one PATCH; a parked email change answers 202 with the OTP
  timings; machine-readable problem `type` URIs in scope including the
  existing sign-up errors. The tool flagged `POST /users/me/email/resend`
  as the one route beyond the decided list (mechanically required: the
  gate code is consumed when the change parks) for author veto at
  review. OTP semantics reuse the PR #150 decisions (single table per
  operation, resend keeps expiry and attempts, discard-if-taken at
  verify) rather than re-deciding them.
- **Author review:** Full suite green (280 tests, 43 new); manual
  Mailpit run of all five routes. Reviewed via pull request.

## 2026-09-29 — Leong Wei Zhi (#112 profile screens)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (implementation + tests)
- **Scope:** `web`: `routes/profile.tsx` + route-table entry (the
  already-linked /profile nav destination); `features/user/profileApi.ts`
  and `profileApiMock.ts`; `ProfileSection`, `ProfileDetailsCard`,
  `EditAccountCard` (OTP flow), `ChangePasswordCard`,
  `DeleteAccountModal` components; `PasswordChecklist.tsx` extracted
  from register.tsx; `errorMessage` hoisted from its three page-local
  copies into `lib/api/http.ts`; `test/profile.test.tsx`.
- **Prompt(s):** Asked to plan and resolve issue #112 (profile.png
  wireframe). Scope decided by the author via question rounds: full edit
  UI now against a dev-only mock (VITE_MOCK_PROFILE_API, adminApiMock
  pattern) since #92's endpoints don't exist yet; credits shown as
  placeholder dashes (credit-service has no API); no My Settings sidebar
  (single card stack) until a second settings page exists.
- **Author review:** Key deviations the tool flagged: the OTP/update/
  password API contract is PROVISIONAL, confined to profileApi.ts, to be
  renegotiated with #92's owner (F2.1.4's second OTP to the new email
  deferred to #92); the email helper text follows AccountRules
  (eXXXXXXX@u.nus.edu), not the wireframe's looser copy; deleting the
  account lands on the login page, not home — ProtectedRoute's redirect
  wins over logout()'s navigation from any protected page (pre-existing
  app-wide logout behavior). Vitest suite (39 tests incl. 14 new),
  eslint, build + mock-absent-from-bundle check; reviewed via pull
  request.
## 2026-09-30 — Ryan Ang (admin dashboard: Users section only)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (removal + tests + docs)
- **Scope:** `web/` only; no backend changes.
  - `routes/admin.tsx`: the Suppliers section is no longer rendered, so
    the Admin Dashboard shows just the Users section. Admins still add,
    edit and delete suppliers on the Suppliers page.
  - `features/supplier/components/SuppliersAdminSection.tsx` deleted, as
    nothing else used it (it stays in git history).
  - `test/admin.test.tsx`: the Suppliers section's cases and its faked
    supplier API removed; one case added checking the dashboard renders
    only the Users section.
  - GitHub wiki, *D2 Design — User and Supplier Services* Part 2 §4: the
    note that supplier management also appears as an admin-dashboard
    section updated to match.
- **Prompt(s):** (summary) Asked for a PR removing the supplier listing
  from the admin dashboard, keeping only users for now, and for the wiki
  to be updated.
- **Author review:** Removing the section is the author's decision; the
  tool made the edits. Verified with the web test suite (63 passing),
  `tsc -b` and `eslint .`. Ryan to review via the PR.

## 2026-09-29 — Leong Wei Zhi (#88 OTP email sending)
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `user-service/` — sign-up reworked to the design doc's
  insert-after-verify shape: `pending_signups` entity/repository (the
  generic `otps` table removed with it), `OtpService` (code policy),
  `OtpEmailSender`/`SmtpOtpEmailSender` (SMTP via
  spring-boot-starter-mail), `POST /auth/signup` → 202 + emailed code,
  new `POST /auth/signup/verify` → 201, purge scheduler sweep, unit +
  integration tests; `compose.yaml` mailpit container + MAIL_*/OTP_*
  wiring; `.env.example`, `AGENTS.md` port row.
- **Prompt(s):** Asked to read the D2 design doc and plan/resolve #88.
  The open design decisions were made by the author via options Q&A:
  Gmail SMTP as the provider (over a transactional API and AWS SES),
  scope including OTP issuance and the sign-up wiring, Mailpit as the
  local SMTP default, and the pending-tables design (drop `otps`;
  `pending_email_changes` to follow with #92).
- **Author review:** Full test suite (213 tests) green; end-to-end
  verified against the compose stack — sign-up 202, code read from the
  Mailpit inbox, verify 201, login token issued. Reviewed via pull
  request.

## 2026-09-29 — Leong Wei Zhi (PR #150 review: repeat sign-ups can't hijack a pending sign-up)
- **Tool:** Claude Code (Opus 5)
- **Mode:** fix
- **Scope:** `user-service/` — the review of #88 found that a repeat
  `POST /auth/signup` overwrote the pending row's username and password
  hash, so whoever received the code verified it into an account holding
  someone else's password. A live `pending_signups` row is now only
  advanced by a repeat of its own details (same username, password
  matching the stored hash) — `renewCode`; anything else is a 409; an
  expired row is dead and any sign-up may take it over
  (`replaceExpired`). Added with it: a resend cooldown
  (`OTP_RESEND_COOLDOWN`, default 60s → 429 + `Retry-After` via the new
  `OtpResendTooSoonException`), wrong-code attempts that survive a
  resend (so the limit caps guesses per pending sign-up, not per code),
  a `last_sent_at` column, and the insert-race loser answering with the
  same 409 as the up-front check instead of a near-identically worded
  400. Tests: reworked sign-up cases in `AuthServiceTest`/
  `AuthControllerTest` (including the hijack as a regression case) and a
  new `SignupResendCooldownTest` at the real 60s default.
- **Prompt(s):** Asked to resolve the review comments on PR #150. The
  first Q&A round picked "first pending sign-up wins"; checking that
  shape against the threat model showed it only reversed who had to move
  first (a planted pending row would be what the real student's sign-up
  completed into), which was reported back, and the author then chose
  the match-to-resend rule above via a second options round, along with
  carrying attempts over plus the 60s cooldown. The author also chose to
  leave the web code-entry screen to issue #109 rather than build it in
  this PR.
- **Author review:** Full suite green (222 tests, up from 213). Reviewed
  via pull request.

## 2026-09-30 — Leong Wei Zhi (PR #150 re-review: bounded pending sign-ups + the web OTP step)
- **Tool:** Claude Code (Opus 5)
- **Mode:** fix + generate
- **Scope:** `user-service/` — the re-review's two pending-row findings.
  (1) `verifySignup` now discards a pending sign-up whose email or
  username was taken while the code was in flight (new
  `SignupIdentifierTakenException`, a `ResponseStatusException` subclass
  named in `noRollbackFor` so the delete commits): such a row can never
  complete, and leaving it locked whoever merely lost a username race out
  of their own address for the rest of the TTL. (2) `renewCode` no longer
  moves `expires_at`, so a row lives at most `OTP_TTL` from creation and
  always becomes takeable — previously whoever pended an address first
  could resend once per cooldown for ever; the email and the 202 now quote
  the seconds actually left. Also `SignupResponse.resendInSeconds` and
  `Retry-After` in the CORS `exposedHeaders`.
  `web/` — the code-entry step the review's High finding demanded (the
  sign-up/OTP half of #109): new `CodeInput` (six boxes per
  `docs/wireframes/signup.png`) and `OtpVerificationModal` under
  `features/user/components/`, `register.tsx` opening that dialog on the
  202 instead of navigating to `/login`, `ApiError.retryAfter`, and ten
  new cases in `src/test/auth.test.tsx`.
  Docs: `user-service/README.md`, `web/AGENTS.md`, the wiki D2 design page
  (§3 and its route table), this log and the README summary.
- **Prompt(s):** Asked to resolve the PR comments, with the web dead-end
  called out as needing a fix in this branch. Decisions by the author via
  options Q&A: patch the two findings narrowly rather than restructuring
  to one pending row per request; the code step as a modal over the
  register page with a six-box input (wireframe fidelity); and the
  cooldown carried in the 202 body. The tool's own finding, reported and
  folded in: `Retry-After` is not CORS-safelisted, so the SPA could not
  have read it without the `exposedHeaders` line.
- **Author review:** user-service 230 tests pass; web 62 pass, build,
  lint and Prettier clean. Reviewed via pull request.

## 2026-09-30 — Leong Wei Zhi (merging main into feat/otp-email-sending)
- **Tool:** Claude Code (Opus 5)
- **Mode:** refactor (merge conflict resolution)
- **Scope:** `user-service`: `AuthService` keeps both sides — login is
  #147's (no transaction, `LoginAttempts` records the attempt under the
  row lock, `pastRetention` replacing the branch's own retention check),
  while #88's sign-up and verify stay `@Transactional` because the
  pending row and its email must commit or roll back together (the
  finite SMTP timeouts from PR #150's Copilot review answer #147's
  reason for dropping the transaction). New
  `V3__pending_signups.sql`, needed because #147 made Flyway the schema
  owner: it drops the always-empty `otps` table #88 removed and creates
  `pending_signups` (with `last_sent_at`). `FlywayMigrationTest`'s
  migration counts, `EntityMappingTest`'s new table-emptying sweep
  (`pending_signups` instead of `otps`), and #147's
  `login_parallelFailuresLock` (which signed up expecting 201) updated
  for the 202-plus-verify flow. `pom.xml`, both `application.yaml`s,
  `.env.example` and `ai/usage-log.md` keep both sides. Docs corrected
  where the merge made them wrong: `user-service/README.md` now
  documents the insert-after-verify routes, the V3 migration and the
  pending-sign-up sweep, and the purge's dependent-rows note (in the
  README and `UserRepository`) no longer names the dropped `otps` table.
- **Prompt(s):** Asked to pull and merge `origin/main` into the branch.
  Nothing was dropped from either side.
- **Author review:** user-service 228 tests pass; web 52 pass. Reviewed
  via pull request.

## 2026-09-29 — Leong Wei Zhi (architecture docs: resolved TBDs struck)
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs
- **Scope:** `docs/architecture.md`, `docs/architecture.mmd` — the
  follow-up the D2 design doc lists: strike resolved TBDs (same PR as
  the #88 implementation above).
- **Prompt(s):** Asked to do the docs follow-up for issue #88.
  Email provider recorded as Gmail SMTP (Mailpit locally) at the
  User Service → Email Provider edge and moved out of "Decisions still
  open"; User/Supplier DB engine TBDs replaced with PostgreSQL, matching
  what compose.yaml has run since issue #85 and the supplier
  scaffolding.
- **Author review:** Transcription only — every decision recorded here
  was made earlier by its owner (provider via options Q&A in the #88
  PR; engines with the DB wiring PRs). Reviewed via pull request.
## 2026-09-29 — Ryan Ang (demo-account seeder)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `user-service`: new `seed/DemoAccountsSeeder.java`
  (CommandLineRunner, only when `user.seed.demo=true`: 1 OWNER, 3
  ADMINs, 100 USERs, skipping accounts already present); the
  `user.seed.demo` setting in `application.yaml`; `USER_SEED_DEMO` in
  `compose.yaml` and `.env.example`; new `DemoAccountsSeederTest` (roles
  and counts, each role's login, rerun, partial reseed, sign-up rules)
  and an off-by-default check in `UserServiceApplicationTests`; README
  "Demo accounts" section.
- **Prompt(s):** Summary: Asked for a database seed of mock users in
  user-service: at least one owner, 2-3 admins and 100 users, on a new
  branch `feat/seed-users`. Team decisions: a startup seeder in
  user-service (following supplier-service's `SuppliersSeeder`), off
  unless a flag is set, and one password per role. Implementation
  choices by the tool, to confirm in review: 3 admins; the passwords
  `OwnerPass123` / `AdminPass123` / `StudentPass123`; `demo_*` usernames
  and `e9…` emails; each role's password hashed once and the hash reused;
  accounts matched by email or username when skipping.
- **Author review:** Ryan to review via the PR. user-service: 204 tests
  pass.

## 2026-09-29 — Ryan Ang (PR #152 Copilot review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor
- **Scope:** `web/`: `UsersSection.tsx` takes the signed-in admin's id
  from `useAuth().me` (AuthProvider's shared `GET /users/me`) instead of
  its own request; `admin.test.tsx` replaces the now-impossible
  "unknown signed-in admin" case with one checking a single `/users/me`
  request per page; the `Reviewed by` lines in
  `features/supplier/components/DeleteSupplierModal.tsx` and
  `routes/suppliers.tsx` record Ryan's review of the #147 changes and
  leave the original supplier code's review with its author.
- **Prompt(s):** Summary: Asked to fix PR #152's Copilot comments (a
  duplicate current-user request, and two files still marked
  `[pending]`).
- **Author review:** Ryan to review via the PR. Web: 52 tests pass;
  type-check and lint clean.

## 2026-09-29 — Ryan Ang (merging main into fix/user-issues)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (merge conflict resolution)
- **Scope:** `user-service`: `AuthService` / `LoginAttempts` combine
  issue #146's per-cause login messages and earlier retention check (PR
  #148) with #147's row-locked counter update — `LoginAttempts.Result`
  now carries the attempts left and the lock's remaining time, and
  `pastRetention` runs before the lock and password checks;
  `AuthController`, `AuthControllerTest`, `AuthServiceTest` and the
  user-service README keep both sides. `web/`: `AuthProvider` and
  `useAuth` expose both PR #143's token-decoded `role` (used by the
  Suppliers page) and #147's `me` from `GET /users/me` (used by the
  `/admin` guard and the nav link); `routes/suppliers.tsx` keeps both
  sides' imports.
- **Prompt(s):** Summary: Asked to merge `main` and fix the conflicts.
  Nothing was dropped from either side; the two role sources in the web
  app are left side by side for the team to decide whether to unify.
- **Author review:** Ryan to review via the PR. user-service: 198 tests
  pass; web: 52 tests pass, type-check and lint clean.

## 2026-09-29 — Ryan Ang (#147 admin Suppliers list)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `web/`: new
  `src/features/supplier/components/SuppliersAdminSection.tsx` (read-only
  supplier list from `listSuppliers`: name, categories, location, hours;
  table at md+, cards on mobile; shared `Pagination`, 20 per page;
  loading, empty and error states) replaces the placeholder in
  `src/routes/admin.tsx`; `src/test/admin.test.tsx` fakes
  `listSuppliers` and replaces the placeholder test with five cases.
- **Prompt(s):** Summary: Team decision to list suppliers on the admin
  dashboard first and add the create/edit/delete buttons once
  supplier-service's CRUD endpoints (#104) exist. Implementation choices
  by the tool, to confirm in review: the wireframe's Zone column left out
  (zones were dropped, supplier-service D4); 20 suppliers per page; hours
  shown as "08:00–18:00" like the Suppliers page's cards.
- **Author review:** Ryan to review via the PR. Web: 48 tests pass;
  type-check and lint clean.

## 2026-09-29 — Ryan Ang (#147 admin Users section bugs)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor (bug fixes), generate (tests)
- **Scope:** `web/`: `UsersSection.tsx` no longer patches rows locally
  before reloading after a role change or removal; row actions
  (`UserTable.tsx` `actionsDisabled`) and the confirm buttons
  (`ConfirmModal.tsx`, `RemoveUserModal.tsx`, `ChangeRoleModal.tsx`
  `disabled`) are off while the list reloads; removing the only row of
  a later page goes straight to the previous page. Three
  `admin.test.tsx` cases (the emptied page is never fetched, a role
  change shows through one reload, actions and confirm off during a
  reload).
- **Prompt(s):** Summary: Asked to fix the three admin Users section
  bugs on #147 (from the PR #140 review): the last-row check reading
  outdated rows with confirm still enabled during a reload, the local
  patch plus reload after each action, and the extra fetch when the last
  row of the last page is removed. Implementation choices by the tool,
  to confirm in review: rely on the reload instead of the local patch
  (old rows stay on screen, actions disabled, until it lands) and
  disable actions rather than read the latest rows through a ref.
- **Author review:** Ryan to review via the PR. Web: 44 tests pass;
  type-check and lint clean.

## 2026-09-29 — Ryan Ang (#147 Admin Dashboard nav link)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests), refactor
- **Scope:** `web/`: `AuthProvider.tsx` asks `GET /users/me` once per
  session and shares it as `me` (`useAuth.tsx` gains the `CurrentUser`
  type, `ADMIN_ROLES` and `isAdmin`); `AdminRoute.tsx` uses `me` instead
  of its own request; `shared/shell/navigation.ts` gains `adminNavItem`
  (label "Admin Dashboard", mobile tab "Admin"); `NavBar.tsx` and
  `TabBar.tsx` show it after Profile for ADMIN and OWNER only (the tab
  bar widens to six columns); `NavBar.tsx` Prettier-formatted; tests in
  `admin.test.tsx` (link shown to an ADMIN, hidden from a USER),
  `app.test.tsx` (hidden when logged out) and `auth.test.tsx` (login now
  also fetches `/users/me` with the new token).
- **Prompt(s):** Summary: Asked for an Admin Dashboard button beside
  Profile in the nav bar, routing to `/admin` and shown only to ADMIN and
  OWNER. Implementation choices by the tool, to confirm in review: the
  role comes from the same `GET /users/me` answer the `/admin` guard
  uses, fetched once in `AuthProvider`; the link also appears in the
  mobile tab bar (as "Admin") so admins on phones can reach the page.
- **Author review:** Ryan to review via the PR. Web: 42 tests pass;
  type-check and lint clean.

## 2026-09-29 — Ryan Ang (#147 admin route guard)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `web/`: new `src/features/user/AdminRoute.tsx` (asks
  `GET /users/me` for the signed-in user's role; ADMIN and OWNER see the
  page, anyone else is redirected to the home page; a failed check shows
  an error message); `src/routes/index.tsx` puts `/admin` behind it;
  `src/test/admin.test.tsx` waits for the guarded page and gains three
  guard cases (USER redirected, OWNER allowed, failed check).
- **Prompt(s):** Summary: Asked how to keep normal users off `/admin`;
  the tool noted user-service already rejects them (403) and listed
  where the web app could get the role from. Team decision: a route
  guard that asks `GET /users/me`. Implementation choices by the tool, to
  confirm in review: non-admins are redirected to the home page; the
  "Checking access..." and "Could not check your access. Try again."
  texts; the guard re-checks when the session token changes.
- **Author review:** Ryan to review via the PR. Web: 39 tests pass;
  type-check and lint clean.

## 2026-09-29 — Ryan Ang (#147 team-decision items)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests), refactor
- **Scope:** `user-service`: Flyway (`pom.xml`, `db/migration/V1__baseline.sql`
  dumped from the entities' generated schema, `V2__username_unique_ignoring_case.sql`,
  `ddl-auto: none` and baseline settings in both `application.yaml`s,
  `User` index annotation); `UserRepository` username lookups on
  `lower(username)`; `GET /users?includeDeleted` (`AdminController`,
  `AdminService`) and `deletedAt` on `UserResponse` (omitted while null);
  the invalid-body and unreadable-body handlers moved from
  `AuthController` to `ProblemDetailAdvice`, `AdminController` keeping
  only the query-parameter one, and a message on `UpdateRoleRequest`;
  `AuthService.signup`/`login` no longer `@Transactional`, with the
  attempt's counter update in new `LoginAttempts` (row locked with
  `refresh(..., PESSIMISTIC_WRITE)`, which also closes the parallel-login
  lockout bypass); tests: `FlywayMigrationTest` (new), parallel-login and
  database-uniqueness cases in `AuthControllerTest`, `includeDeleted`
  and invalid-body reasons in `AdminControllerTest`, the owner-setup
  reason, `EntityMappingTest` table cleanup, `AuthServiceTest`/
  `AdminServiceTest`/`ProfileServiceTest` updated; README "Errors" and
  "Schema (Flyway)" sections. `web/`: `types.ts`, `adminApi.ts`,
  `adminApiMock.ts` (soft-deleting), `UsersSection.tsx` ("Show removed
  accounts" toggle), `UserTable.tsx` (removed rows greyed with their
  removal date, no actions), `register.tsx`/`login.tsx` ("Account
  created" notice via navigation state); `admin.test.tsx` and
  `auth.test.tsx` cases.
- **Prompt(s):** Summary: Asked to implement team decisions: an
  `includeDeleted` filter with a toggle in the Users table instead of a
  separate deleted-users table; one validation error format by moving
  `AuthController`'s handler into `ProblemDetailAdvice` (#138 contract);
  Flyway with a `lower(username)` unique index and clean-up of existing
  case-duplicates; bcrypt outside the transaction; an "account created"
  message; token expiry left to #109. The tool asked about the points
  those left open, and the team decided: duplicates are renamed (the
  oldest keeps the name, later ones get a numbered suffix); `ddl-auto`
  becomes `none`; removed rows are view-only (restore stays with #94);
  token expiry stays with #109. Implementation choices by the tool, to
  confirm in review: fixing the lockout race with a row lock taken after
  the password check; the notice text "Account created. Log in to
  continue."; "Role is required" and "Invalid request parameter";
  `deletedAt` left out of the JSON while null; dates as "29 September
  2026"; with removed accounts shown, a removed row stays and greys
  instead of disappearing.
- **Author review:** Ryan to review via the PR. user-service: 192 tests
  pass (the parallel-login test also run 3 more times); web: 36 tests
  pass, type-check, lint and build clean.

## 2026-09-29 — Ryan Ang (#147 housekeeping and duplicated code)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, generate (tests)
- **Scope:** `web/`: `errorMessage` moved into `src/lib/api/http.ts`
  and used by `UsersSection.tsx`, `login.tsx`, `register.tsx` and
  `routes/suppliers.tsx`; new `src/shared/components/ConfirmModal.tsx`,
  with `RemoveUserModal`, `ChangeRoleModal` and `DeleteSupplierModal`
  built on it; `routes/suppliers.tsx`'s Previous/Next pager replaced by
  the shared `Pagination`; `UserTable.tsx`'s `UserActionsProps` spelled
  out and its handlers passed by name; `axios` removed from
  `package.json`; Prettier run on `login.tsx`, `register.tsx`,
  `AuthProvider.tsx`, `useAuth.tsx` and `useLocalStorage.tsx`; review
  headers filled in on `adminApiMock.ts` and `UserTable.tsx`; the
  `VITE_MOCK_ADMIN_API` comment in `.env.example` updated; new
  `src/test/auth.test.tsx` (7 login and sign-up page tests).
- **Prompt(s):** Summary: Asked to do the housekeeping and duplicated-code
  items of issue #147 (open review items from PRs #140 and #141), leaving
  the items that need a team decision. Implementation choices by the
  tool, to confirm in review: the shared `errorMessage` shows the
  fallback text for a network failure (a `TypeError`), so the admin Users
  section no longer shows "Failed to fetch"; `ConfirmModal`'s props;
  the supplier pager gains page numbers and keeps its "Page X of Y · N
  suppliers" label.
- **Author review:** Ryan to review via the PR. Web: 32 tests pass;
  type-check, lint and build clean.

## 2026-09-29 — Alastair Tan (#106 JWT verification + role gate in supplier-service)
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** generate (implementation + tests)
- **Scope:** `supplier-service`: JWT verification (`security/JwtVerifier`,
  jjwt 0.13.0, mirroring `notification-service`'s `JwtVerifier`), the
  authentication filter (`security/JwtAuthenticationFilter`), problem+json
  401/403 handlers (`security/RestAuthEntryPoint`,
  `security/RestAccessDeniedHandler`), and the Spring Security filter
  chain (`config/SecurityConfig`, replacing the old `CorsConfig` per
  design doc D2: GET open to any authenticated user, everything else
  under `/suppliers` needs `ROLE_ADMIN`); `spring-boot-starter-security`
  and jjwt added to `pom.xml`; `supplier.jwt.secret` in both
  `application.yaml`s; `compose.yaml` passes `JWT_SECRET`;
  `JwtVerifierTest`, `SecurityConfigTest`.
- **Prompt(s):** Asked to implement issue #106 ("mirror JwtVerifier"),
  scoped to supplier-service only. The JWT `role` claim's name/shape was
  confirmed against user-service's already-merged `JwtIssuer` (PR #141)
  rather than invented, per AGENTS.md's restriction on agents making
  interface/schema decisions; the GET-endpoint auth requirement (design
  doc D2) was confirmed with the author before implementing, since it
  temporarily breaks browser access to supplier browsing until the
  frontend's separate auth-wiring task lands.
- **Author review:** Tested live end-to-end against running
  user-service + supplier-service containers (signup, login, then
  GET/POST /suppliers with no/invalid/valid tokens and non-admin/admin
  roles); `./mvnw test` passes (21/21). Reviewed by: [pending].

## 2026-09-29 — Leong Wei Zhi (#146 login errors that name their cause)
- **Tool:** Claude Code (Opus 5)
- **Mode:** generate (implementation + tests + docs)
- **Scope:** `user-service`: `exception/LoginFailedException` (one
  message per cause, via `unknownAccount()` / `wrongPassword(remaining)`
  / `wrongPassword()`), `exception/AccountLockedException` (the wait
  computed from the lockout's end instead of a hard-coded "15 minutes",
  and exposed for `Retry-After`), `service/AuthService` (each failure
  throws its own message; `recordFailure` returns the running count; the
  timing-equalisation hash removed), `controller/AuthController`
  (`Retry-After` on the 429; the lost-race path answers with the
  wrong-password message); `AuthServiceTest`, `AuthControllerTest`,
  `ConflictResponseTest` updated; `web/src/test/login.test.tsx` (new —
  the login page had no tests); user-service and root READMEs.
- **Prompt(s):** Asked to plan and resolve issue #146 (split the generic
  login failure into unknown-account and incorrect-password), then to
  consider adding an attempts-remaining countdown and a lockout message
  that counts down the minutes left. The author chose, via
  neutral-options Q&A: the exact wording of all three messages; the
  countdown shown on every wrong-password failure; the lockout time
  computed dynamically with a `Retry-After` header; both 401s keeping
  their status (no 404, no problem `type` URI), so the web page needed
  no source change; an account deleted past the retention window
  answering as unknown and the concurrent-login race loser answering as
  a wrong password; and docs-only handling of the wireframe, which still
  shows the old single error box. The accepted trade-off — login now
  reveals whether an account exists, reversing #89's non-revealing
  failures — is the author's call, noted because sign-up's "already
  taken" 400s already reveal it and #145 already accepted revealing the
  lock state. Because the messages state which failure happened, the
  decoy password hash that made an unknown account answer as slowly as a
  wrong password no longer hid anything and was removed.
  PR #148 Copilot review: the lockout's seconds are rounded up before its
  minutes are (a part-second could otherwise be dropped twice and quote a
  wait shorter than the lock, with `Retry-After` a second early), and the
  retention-window check moved ahead of the lock and password checks, so
  an account past the window answers as gone whatever password is typed
  instead of only when the password happened to be right — a wrong one
  used to get the countdown and count towards a lock.
- **Author review:** Leong Wei Zhi to review via the PR.
  user-service 191/191 tests pass; web 29/29 tests pass, lint clean,
  build succeeds; the three messages verified live against a running
  stack and in-browser on `/login`.

---
## 2026-09-29 — Ryan Ang (#91 Spring Security filter chain in user-service)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `user-service`: `security/JwtVerifier`,
  `security/JwtAuthenticationFilter`, `security/SecurityProblemResponses`
  (new); `SecurityConfig` (filter wiring, stateless sessions, 401/403
  handlers); `CallerId` comment; `JwtVerifierTest`,
  `JwtAuthenticationTest` (new); the provisional-403 cases in
  `ProfileControllerTest` and `AdminControllerTest` changed to 401;
  user-service README "Checking tokens" section.
- **Prompt(s):** Summary: Asked whether #91 could be picked up, then to
  implement it on `feat/spring-security-filter-chain` following the D2
  design doc (§3: the filter chain validates the JWT, maps `role` to
  authorities, violations are problem+json 401/403; §4: verify locally
  like notification-service's `JwtVerifier`). The
  `token_denylist` check is deferred; a bad token on a public route was
  discussed (ignore vs reject, a client-side exclude, `web.ignoring()`,
  a path allowlist), and the author chose to skip the filter on
  `/auth/**` except `/auth/logout`, and on `/actuator/health`, and reject
  bad tokens with 401 everywhere else. Implementation choices by the
  tool, to confirm in review: the `detail` texts ("Authentication
  required", "Token has expired", "Invalid token", "You do not have
  permission to access this resource"); `WWW-Authenticate: Bearer`
  (with `error="invalid_token"` for a bad token); rejecting tokens with
  a non-numeric `sub`, no `exp`, or an unknown `role`; the `Bearer`
  scheme matched case-insensitively; another scheme treated as no token.
- **Author review:** Ryan to review via the PR; the full user-service
  suite (185 tests) passes locally.

## 2026-09-29 — Leong Wei Zhi (PR #142 login/sign-up wireframe restyle)
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactor (UI restyle) + generate (password checklist)
- **Scope:** `web/src/features/user/login.tsx` and `register.tsx`
  restyled to the wireframes (`web/docs/wireframes/login.png`,
  `signup.png`) using the app's existing Tailwind conventions — centered
  card, stacked labels, dark full-width button, error alert box — plus a
  live password checklist on sign-up mirroring user-service's
  `AccountRules` (display-only; the server stays the validator);
  `web/src/routes/home.tsx` Outlet import switched from
  `react-router-dom` to `react-router` and the stray v6
  `react-router-dom` dependency removed from `web/package.json`;
  `.idea/` added to `.gitignore`. PR #142 Copilot review fixes:
  `role="alert"` on both error messages, the checklist moved outside the
  password `<label>`, and these disclosure updates. Follow-up fix
  reported by the author: `home.tsx` kept showing the logged-in view
  after logout because it read `localStorage` directly (a raw read
  React never re-renders on); it now takes the logged-in state from
  `useAuth()`, the same source the NavBar uses — reproduced and
  verified fixed in-browser (login → logout resets the page). Also on
  this PR, at the author's request: JWT wired into `apiFetch` —
  `AuthProvider` now feeds the stored session's `accessToken` to
  `setTokenSource` (a layout effect, so it is set before any page's
  mount-time fetch), the session is typed as the `LoginResponse` it
  actually is (shared via `types.ts`; `useAuth`/`useLocalStorage` were
  typed as jose's `JWTPayload` but never held one), and the
  Authorization header was verified present on a live `/users` request
  in-browser. Server-side JWT validation does not exist yet, so
  authenticated endpoints still 403 — tracked separately. Also on this
  PR, deciding issue #145 as "wireframe wins" (author's call, accepting
  that a locked account is revealed to exist): user-service's lockout
  now raises `AccountLockedException` — thrown when a login hits a
  locked account and on the attempt that trips the lock — mapped in
  `AuthController` to a 429 problem+json with the wireframe's "Too many
  failed attempts. Login disabled for 15 minutes."; the login page
  displays it with no frontend change. Unit/integration tests updated
  (156/156 pass) and the flow verified live in-browser.
- **Prompt(s):** Asked to fix the UI discrepancy in the login and
  sign-up pages; the author chose "match the wireframes" and "include
  the password checklist" (omitting forgot-password and the OTP modal,
  which have no backend yet) via neutral-options Q&A; then asked to
  switch back from react-router-dom to react-router and to resolve the
  Copilot review comments.
- **Author review:** Wireframe target and checklist scope chosen by the
  author via options Q&A; verified in-browser against the wireframe
  PNGs (checklist flips live while typing, error box renders on a
  failed login), with lint, tests, and build green; reviewed via pull
  request.

## 2026-09-29 — Ryan Ang (#87/#89/#90 sign-up, login, JWT in user-service)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `user-service`: `POST /auth/signup` and `POST /auth/login`
  (`AuthController`, `AuthService`, `SignupRequest`, `LoginRequest`,
  `LoginResponse`, `LoginFailedException`, `UserRepository.findByUsername`),
  JWT minting (`security/JwtIssuer`, jjwt 0.13.0 in `pom.xml`), CORS in
  `SecurityConfig`, `user.jwt.*` / `user.web-allowed-origin` in both
  `application.yaml`s; `compose.yaml` and `.env.example` wiring
  (`JWT_SECRET`, `JWT_ACCESS_TOKEN_TTL`, `WEB_ALLOWED_ORIGIN`,
  `VITE_USER_SERVICE_URL`); `AuthServiceTest`, `JwtIssuerTest`,
  `AuthControllerTest`; user-service README. PR #139's `user-auth/`
  server deleted; `web/src/features/user/login.tsx` and `register.tsx`
  pointed at user-service (email field added, server error shown).
- **Prompt(s):** Summary: Asked to convert PR #139's Node `user-auth`
  server to Spring Boot with the RDBMS instead of `users.json`. The tool
  noted AGENTS.md and issues #87/#89/#90 already place auth in
  user-service. Team decisions: auth goes into user-service, not a separate
  service; both login and register; the account is inserted only after
  OTP verification, but OTP is deferred, so sign-up inserts directly for
  now; JWT minting (#90) built now; sign-up returns the account (201) and
  the page sends the user to log in; camelCase login response; CORS in
  this PR; keep #139's frontend, changing it only where it broke. Rules
  otherwise follow design doc §3 (routes, claims, 1 h expiry, 5-failure
  15-minute lockout, non-revealing failures, exact 400 reasons) and the
  earlier team decision that logging in within 30 days recovers a
  soft-deleted account. Implementation choices by the tool, to confirm
  in review: request field `usernameOrEmail`; a locked account gets the
  same 401 as a wrong password; validation reasons joined in one
  `detail`; CORS reuses `WEB_ALLOWED_ORIGIN`. 
  PR #141 Copilot review: `login.tsx` and `register.tsx` no longer log the
  response (the login one now holds the access token); the #90 logout
  denylist stays deferred, per the design doc's "defer for now" on logout
  revocation, with #90 kept open.
  Second PR #141 review (after merging `main`, which brought in PR #140):
  the tool sorted a reviewer's findings into implementation fixes and
  ones needing a team decision. Team decision: usernames are matched
  ignoring case (stored as typed), so `UserRepository` gains
  `existsByUsernameIgnoreCase` / `findByUsernameIgnoreCase` in place of
  the exact-match methods. Fixed: `SignupRequest` and `SetupOwnerRequest`
  share their rules through new `dto/AccountRules.java`, and sign-up and
  owner setup share normalising and uniqueness checks through new
  `service/NewAccountDetails.java`; `JwtIssuer` reads a unit-less TTL as
  seconds (`@DurationUnit`), documented in `.env.example` and the
  README; `AuthController` maps a lost optimistic-locking race to 401 for
  login only, so other `/auth` routes keep the 409; `login.tsx` and
  `register.tsx` call `apiFetch` instead of axios and drop their success
  logs; `AuthProvider.tsx` navigates to `/` (not the missing `/home`)
  and is the only navigation after login. Tests: case-insensitive
  sign-up and login, the login/sign-up race responses in
  `ConflictResponseTest`, and the TTL unit in `JwtIssuerTest`. Left for
  the team: holding a DB connection through bcrypt, the service-wide
  validation error format, token storage and expiry on the web side,
  and the post-sign-up message.
- **Author review:** Full `./mvnw test`
  suite passes, including the Docker-backed `AuthControllerTest`
  (156 tests after the second review). Web: 25 tests pass, lint and
  type-check clean.

## 2026-09-29 — Ryan Ang (#113 PR #140 second review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, generate (tests)
- **Scope:** PR #140 review fixes. `user-service`: `AdminService.listUsers`
  leaves out soft-deleted accounts, with `AdminControllerTest` list
  expectations updated and a hidden-when-searched case added. `web`:
  `UsersSection.tsx` reloads after a removal, moves to the last page when
  a reload lands past the end (role change or removal), clears the error
  banner when the search, filter or page changes, trims search before
  debouncing, and lists the fetch effect's inputs directly;
  `ChangeRoleModal.tsx` shows a general self-demotion warning when the
  signed-in admin is unknown (`GET /users/me` failure is now logged);
  `adminApi.ts` honours `VITE_MOCK_ADMIN_API` only on the dev server and
  imports the mock lazily, so production bundles exclude it (checked in
  the built bundle); `ProtectedRoute.tsx` and `routes/index.tsx`
  formatted with the repo's Prettier config; `.env.example` mock comment
  corrected; four `admin.test.tsx` cases added.
- **Prompt(s):** Summary: Asked to recheck new PR #140 comments (a
  reviewer's change request and Copilot) against PR #141. The tool noted
  #141 already sets `VITE_USER_SERVICE_URL` and adds user-service CORS
  with preflights allowed on the admin routes. The duplicate supplier pager is
  left for a follow-up issue, as `routes/suppliers.tsx` is the supplier
  domain's.
- **Author review:** Reviewed by Ryan before PR. `./mvnw test` passes
  (0 failures); web: 25 tests pass, lint and type-check clean.

## 2026-09-28 — Ryan Ang (#113 admin user management)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, generate (implementation + tests)
- **Scope:** `web/` Admin Dashboard (issue #113):
  - Suppliers section reduced to a "coming soon" placeholder inside
    `src/routes/admin.tsx`; `src/features/supplier/components/`
    `SuppliersAdminSection.tsx` and `SupplierTable.tsx` deleted.
  - Credit mock removed: `src/features/credit/` (`adminCreditApi.ts`,
    `types.ts`, `components/AddCreditsModal.tsx`) deleted; the Credits
    column and Add Credits action dropped from `UserTable.tsx` and
    `UsersSection.tsx`.
  - `src/features/user/adminApi.ts` in-memory mock replaced by
    `apiFetch('user', ...)` calls to `GET /users`, `PATCH /users/{id}`
    and `DELETE /users/{id}`; `types.ts` gains `AdminUserPage` (Spring
    PagedModel body) and `ListUsersParams`.
  - `UsersSection.tsx` sends search (debounced, `SEARCH_DEBOUNCE_MS` =
    300) and a new role filter to the server, pages with
    `USERS_PAGE_SIZE` = 100, and resets to page 1 when either changes;
    new shared `src/shared/components/Pagination.tsx` (Previous / page
    numbers / Next).
  - New `src/features/user/adminApiMock.ts` (in-memory stand-in with the
    same signatures, 30 demo users, filters and pages like `GET /users`);
    `adminApi.ts` uses it only when `VITE_MOCK_ADMIN_API=true`, set in
    the git-ignored `web/.env.local`.
  - `src/test/admin.test.tsx` rewritten against a fake `adminUserApi`:
    supplier and credit tests replaced by one placeholder test, owner-row
    test changed to "owner has no actions", search, role filter, paging
    and page-reset cases added.
- **Prompt(s):** Summary: Asked whether the admin page could show
  suppliers once PR #134 merges. The tool noted that #134 removes
  `listZones`/`Zone` and changes `listSuppliers` to a paged response,
  which would break the section, and that no PR yet adds the supplier
  create/update/delete endpoints. Per team decision, the section becomes
  a placeholder for now; who builds the admin supplier display is left
  to the team. Then asked to remove the credit mock as well. Then asked
  which files change now that #96 is merged, and to connect the Users
  section to it: search goes to the server with a ~300 ms debounce kept
  as an editable constant; paging with Previous/Next and page numbers;
  typing a search or choosing a role filter resets to page 1 and reloads
  from the server; page size 100, also a constant. Finally asked for a
  mock to view the page in the browser before #91 (login) and
  user-service CORS exist, keeping the real calls in place for when
  those land; the flag name was the tool's choice. On review, the tool
  made the tests follow the exported `USERS_PAGE_SIZE` instead of a
  hard-coded 20, then merged `main` (#139) into the branch: `/admin`
  moved inside #139's `ProtectedRoute` in `src/routes/index.tsx`, and
  the admin tests store a fake session under `"user"` in localStorage.
  Then asked to fix what the merge brought in: #139's
  `src/features/user/ProtectedRoute.tsx` now redirects with
  `<Navigate>` during render instead of a `useEffect` + loading state
  (fixes the `react-hooks/set-state-in-effect` lint error), and the `*`
  404 route moved back outside `ProtectedRoute` so the existing
  `app.test.tsx` 404 test passes; formatting-only changes to four
  supplier files reverted to `main`.
  PR #140 review: the tool checked Copilot's comments against the code.
  Fixed: `UsersSection.tsx` reloads the current query after a role
  change (filter and counts stay right) and goes back a page when a
  later page's last row is removed; the test double's search matches
  ID and email too; `VITE_MOCK_ADMIN_API` documented in root
  `.env.example`; README AI Use Summary updated. Per team decision
  (admins cannot remove their own account through the admin endpoint,
  which user-service already rejects), `adminApi.ts` gains
  `getCurrentUser()` (`GET /users/me`, mocked as nus_courier_99) and
  `UserTable.tsx` hides Remove on the signed-in admin's own row. Three
  tests added. The unauthenticated-requests comment is left for #91.
  Then asked to show the ID in the users list since search matches it:
  `UserTable.tsx` gains an ID column (desktop) and "ID:" on the mobile
  cards, with an assertion in the list test.
  Then asked for a confirmation step on promote/demote: new
  `components/ChangeRoleModal.tsx` (following `RemoveUserModal`), shown
  before `UsersSection.tsx` calls `changeRole`; it warns when an admin
  demotes their own account. Tests now confirm through the dialog, with
  new cases for cancelling and for the self-demotion warning.
- **Author review:** Ryan reviewed the placeholder, credit removal and
  API wiring before the PR. Mock website reviewed by Ryan before PR.
  PR fixes reviewed by Ryan.

## 2026-09-28 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** debug
- **Scope:** `supplier-service` — `SupplierService.normalizeImageUrl`
  and its tests in `SupplierServiceTest`.
- **Prompt(s):** Asked why some supplier images didn't render in the
  UI (broken-image icon) while others (with no image at all) showed
  nothing. Diagnosed that the seed CSV's `ImageURL` column points at
  GitHub's file-*viewer* page (`github.com/.../blob/<ref>/<path>`),
  which serves `text/html`, not image bytes — only
  `raw.githubusercontent.com/.../<ref>/<path>` (no `blob`) serves the
  actual `image/jpeg`. Confirmed via `curl -I` on both URL forms. The
  author said the seed CSV is course-provided data and can't be edited,
  so asked for the fix to happen in code instead.
- **Author review:** The constraint (CSV must stay untouched) and the
  general fix location (normalize at the API response boundary, in
  `SupplierService.toResponse`, so it also covers any future
  admin-CRUD-created supplier with the same URL shape, not just the
  CSV-seeded rows) were the author's; the tool implemented the
  GitHub-blob-to-raw-URL regex and its tests. Verified live: rebuilt
  and restarted the `supplier-service` container, confirmed
  `GET /suppliers?search=Anna` now returns the raw-content URL, and
  confirmed in a real (Playwright-driven) browser that the image
  actually loads (200, non-zero `naturalWidth`) where it previously
  showed a broken-image icon. `mvn test` passes (7/7). Reviewed via
  pull request.

## 2026-09-28 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** debug
- **Scope:** `supplier-service` — `GET /suppliers` sort validation
  (`SupplierController`, `SupplierService`, new
  `exception/InvalidSortException`, new `SupplierServiceTest`).
- **Prompt(s):** Asked to explain a PR #134 review comment (LeongWZ)
  showing that an unrecognized `sort` value (e.g. `location`/
  `openingTime`, which match the response DTO's field names but not the
  `Suppliers` entity's) throws an unhandled `PropertyReferenceException`
  since Spring Data resolves Sort against the entity, and the service
  has no `@ControllerAdvice` to turn that into a clean error; the
  frontend's own `'distance'` sentinel leaking through unconverted
  would hit the same path. Then asked to fix it and add tests.
- **Author review:** The fix approach (an explicit allow-list of
  sortable properties, rejected with a 400 problem+json body via an
  `@ExceptionHandler`, following `user-service`'s existing
  `ProfileController` pattern) was the reviewer's, not the tool's; the
  tool implemented it and added `SupplierServiceTest` covering the
  allow/reject cases. Verified by running the full supplier-service
  test suite (`mvn test`, 4/4 passing) and a live smoke test against
  the running container confirming `sort=name,asc` still returns 200
  while `sort=location`/`sort=distance` now return 400 instead of 500.
  Reviewed via pull request.

## 2026-09-28 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** debug
- **Scope:** `web/src/routes/suppliers.tsx` — "Nearest to Me" sort
  fallback.
- **Prompt(s):** Asked to explain a PR #134 review comment (LeongWZ)
  identifying that the render-phase guard falling back to name-sort in a
  browser without geolocation support only fires once (it self-gates on
  `locationNotice`, which it also sets), so re-selecting "Nearest to Me"
  leaves `sort` stuck at `'distance'` with `fetchCurrentPage` resolving
  `null` forever — search/category changes then silently stop updating
  the list. Then asked to apply the reviewer's suggested fix: reject the
  selection in the sort-change handler itself instead of correcting
  `sort` back after the fact during render.
- **Author review:** The fix approach (handle in `onSortChange`, never
  commit `sort = 'distance'` when unsupported) was the reviewer's, not
  the tool's; the tool implemented it. Reviewed via pull request.

## 2026-09-26 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** generate (implementation), debug
- **Scope:** issue #133 — `GET /suppliers` list endpoint: search by
  name, filter by category, paging + sorting. New files
  `supplier-service/src/main/java/foc/supplier/{controller/SupplierController,
  service/SupplierService, dto/SupplierResponse, dto/PageResponse,
  config/CorsConfig}.java`; extended `SuppliersRepository` (paginated
  search/filter query) and `SupplierCategoriesRepository` (batch
  category lookup); `application.yaml`/`compose.yaml`/`.env.example`
  (CORS-allowed origin for the browser). Frontend: `web/src/features/supplier/api.ts`
  and `types.ts` updated to the new paginated response shape;
  `routes/suppliers.tsx` given page state + Prev/Next controls. Also
  removed the zone feature's remaining frontend wiring (`Zone` type,
  `SupplierFilterBar`'s zone dropdown, `SupplierFormModal`'s required
  Campus Zone field, `SupplierDetailPanel`'s zone line, and the
  `listZones()` call to a nonexistent `/zones` endpoint) — the team
  dropped the zone feature; the required, unpopulatable Campus Zone
  `<select>` was blocking every supplier edit.
- **Prompt(s):** Asked to implement task #7 (pagination first, per
  author's own build-order decision) end-to-end and run the app to see
  it working. Bugs found and fixed while verifying live: (1) the JPQL
  search query threw `function lower(bytea) does not exist` on Postgres
  when `search`/`category` were null — fixed with explicit
  `CAST(:param AS string)`; (2) Vite's dev server doesn't read the
  repo-root `.env` (only `web/`'s own env files or the shell
  environment) — `VITE_SUPPLIER_SERVICE_URL` had to be exported before
  `npm run dev`; (3) a `react-hooks/set-state-in-effect` lint error from
  resetting `page` in a plain effect — fixed via React's documented
  "adjust state during render" pattern instead; (4) discovered mid-task
  that the suppliers page was already broken independent of this work —
  it called a `/zones` endpoint the backend never implemented, throwing
  inside `Promise.all` and failing the page's entire initial load —
  removed as part of the zone-feature drop.
- **Author review:** Verified live end-to-end: `docker compose up`
  supplier-db + supplier-service, `curl` against `GET /suppliers` with
  search/category/page/sort params, and the actual browser UI via
  Playwright (21 seeded suppliers paginate across 3 pages, category
  dropdown populated from live data, multi-category suppliers e.g.
  "Food, Coffee" display correctly, empty-state renders on no match).
  `tsc -b`, `eslint .`, and `vitest run` all pass on `web/`. Reviewed by
  author via pull request.

---
## 2026-09-27 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** generate (implementation)
- **Scope:** "Nearest to Me" distance-sort feature, PR #134 — backend:
  `SuppliersRepository.searchOrderedByDistance` (native Haversine
  query), `SupplierController`'s optional `lat`/`lng` params,
  `SupplierService.listSuppliers`'s lat/lng branch. Frontend:
  `web/src/features/supplier/distance.ts` (client-side display-only
  distance formatting), `SupplierFilterBar`'s `LabeledSelect` sort
  control, `SupplierCard`'s `distanceLabel`, `suppliers.tsx`'s
  geolocation-request effect and fallback-to-name-sort notice,
  `api.ts`'s `sort`/lat/lng query params. Also added a required-field
  asterisk to `SupplierFormModal` (Name, Location, Opening/Close).
- **Prompt(s):** Asked to implement "sort suppliers by distance from
  the user's current location" (team decision) end-to-end: request
  browser geolocation, pass it to the backend, order results by
  distance there (not client-side), and show a distance label per
  card. Backend query approach (native SQL for the Haversine trig
  functions JPQL doesn't expose) and the geolocation-denied/unavailable
  fallback (drop to name-sort with a visible notice) were author
  decisions.
- **Author review:** Verified via `curl` with real lat/lng against the
  seeded suppliers and manually in-browser (geolocation grant, deny,
  and unavailable paths). Reviewed via pull request — see PR #134's
  later review comments (2026-09-28 entries above) for the gaps this
  first pass missed (geolocation timeout, LIKE-wildcard escaping,
  sort-value validation, and this entry itself, added after review
  flagged the missing disclosure).

## 2026-09-27 — Ryan Ang (PR #135 second review)
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, review
- **Scope:** `SecurityConfig` /users rules rewritten to fail closed
  (only `GET /users/*` and `DELETE /users/me` open to any signed-in
  user; every other method on `/users` and `/users/*` needs
  ADMIN/OWNER, replacing the per-method admin lines and the HEAD rule),
  with an `AdminControllerTest` case; `UserRepository.getActiveUser`
  shared by `AdminService` and `ProfileService` (their service tests'
  repository mocks now call real default methods); `AdminService` role
  rank taken from the `Role` enum's declaration order, noted on `Role`.
- **Prompt(s):** Summary: Asked to go through the new PR #135 review
  comments. The tool checked each against the code and noted that the
  reviewer's suggested method-less `/users/*` admin rule would also
  block the public profile (`GET /users/{id}`), and offered a rule set
  that keeps it open. Per team decision: apply that rule set, share the
  active-user lookup, and derive the role rank from the enum; the
  problem+json consistency comments are handled separately.
- **Author review:** The tool ran the full `./mvnw test` suite:
  117/117 passed; the new security test failed before the rule change
  (a USER's `PATCH /users/me` returned 400). Ryan to review via the PR.

## 2026-09-27 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, review
- **Scope:** PR #136 (issue #93) reviewed for gaps; after it merged,
  `main` merged into the #96 branch (conflicts in `README.md`,
  `ai/usage-log.md`, `user-service/README.md`, `UserRepository` and
  `OwnerSetupControllerTest` resolved by keeping both sides), and
  `AdminService.removeUser` switched to the shared `User.softDelete`.
  Then, from the PR #135 review comments: `HEAD /users` given the same
  ADMIN/OWNER rule as `GET /users` in `SecurityConfig`, with a test in
  `AdminControllerTest`. The `callerId` parsing and the
  `UserNotFoundException` handler, copied in `ProfileController` and
  `AdminController`, moved to the new `CallerId` and
  `ProblemDetailAdvice` in the controller package. The literal-`_`
  search test switched from `"o_u"` to `"o_n"`, since a wildcard `_`
  never matched `"o_u"` against the seeded users. Optimistic locking
  on `users`: a `@Version` column on `User` (default 0 so `ddl-auto`
  can add it to existing rows), a 409 problem+json handler in
  `ProblemDetailAdvice`, `UserOptimisticLockTest` and
  `ConflictResponseTest`. A `ProfileControllerTest` case pins that an
  OWNER can delete their own account via `DELETE /users/me`.
- **Prompt(s):** Summary: Asked for a review of PR #136 and which of
  #135/#136 to merge first. The tool raised owner self-deletion via
  `DELETE /users/me` and the wording of the #89 login note; both were
  answered by the team. The tool suggested merging #136 first, then
  asked to do the follow-up merge on the #96 branch. Then asked to go
  through the PR #135 comments; the tool confirmed that a USER could
  call `HEAD /users` and offered two fixes. Per team decision: add a
  separate HEAD rule. Then asked to fix the duplicated caller-id and
  404 handling that the review flagged now instead of with #91, and
  to fix the underscore test. For the race between a role change and
  a delete, the tool listed row locking, a version column,
  changed-columns-only updates, or deferring. Per team decision:
  optimistic locking with a version column, and a lost race returns
  409 problem+json. The tool noted that `DELETE /users/me` lets an
  OWNER delete themselves, against the earlier ownership-transfer
  decision. Per team decision: owners may delete themselves via
  `DELETE /users/me`, like any user.
- **Author review:** The tool ran the full `./mvnw test` suite:
  116/116 passed. The new HEAD test failed before the fix, the
  underscore test failed with `_` escaping temporarily removed, and
  the 5 locking/409 tests failed before `@Version` and the handler. Ryan
  to review via the PR.

## 2026-09-27 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** user-service issue #93 — soft delete (`DELETE /users/me`,
  `User.softDelete`), the day-31 purge (`AccountPurgeScheduler`,
  `OtpRepository`/`AccountTokenRepository` purge queries,
  `user.retention.*` config, `@EnableScheduling`), reuse-block and
  purge tests, README/.env.example updates.
- **Prompt(s):** Asked to plan and implement issue #93 from the D2
  design doc (§2: soft delete = reuse block, purge on day 31). Open
  design points were decided by the author via neutral options Q&A:
  `DELETE /users/me` needs the bearer token only (no password/OTP
  re-check); the shared soft-delete path is an entity method
  (`User.softDelete`), which admin removal (#96/PR #135) switches to
  after merge; purge FK cleanup is bulk deletes inside the purge
  transaction (over DB-level ON DELETE CASCADE). The scheduler follows
  notification-service's retention purge pattern (cadence/window
  env-overridable, defaults matching: 30 days, daily 03:00). The
  30-day reuse block needed no new code — uniqueness checks already
  include soft-deleted rows — so it was pinned with tests instead.
- **Author review:** All design decisions made by the author during the
  options Q&A; suite run with `./mvnw test` (49/49 green); code, tests,
  and disclosures reviewed via pull request (#136).

## 2026-09-26 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, generate
- **Scope:** issue #96 review follow-up: `UserRepository.countByRole`
  removed (`OwnerSetupControllerTest` and `OwnerSetupServiceTest`
  updated to match); the admin role check moved from `@PreAuthorize`
  to URL rules in `SecurityConfig`; 12 test cases added to
  `AdminControllerTest`.
- **Prompt(s):** Summary: Asked whether `countByRole` was still used
  and for a review of the #96 code for missing tests or anything else
  missed. The tool reported that only tests used `countByRole`, listed
  untested cases, and noted that non-admins got 400 instead of 403 for
  bad input. Per team decision: remove `countByRole`, add the missing
  tests plus out-of-range page sizes, and give non-admins 403 before
  their input is checked. A removed user's token staying valid was
  added to the deferred items.
- **Author review:** The tool ran the full `./mvnw test` suite:
  100/100 passed. Ryan to review via the PR.

## 2026-09-26 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** user-service admin endpoints (issue #96): `AdminController`
  (`GET /users`, `PATCH /users/{id}`, `DELETE /users/{id}`),
  `AdminService`, `UpdateRoleRequest`; `UserRepository` gains
  `JpaSpecificationExecutor`; `SecurityConfig` restricts the admin
  routes to ADMIN/OWNER; `AdminServiceTest` (unit) and
  `AdminControllerTest` (Testcontainers); user-service README status.
- **Prompt(s):** Summary: Asked to start issue #96. The tool listed the
  open questions, pointed to the relevant parts of the D2 design doc
  (Part 1 §1, §3, §4, §6), and implemented the answers. Per team
  decisions: build before #91 (JWT filter) and #93 (soft delete) land,
  setting `deleted_at` directly for now; the caller's role comes from
  the ADMIN/OWNER authorities that #91 maps from the token; admins and
  owners can promote to ADMIN; admins can demote or remove other admins
  and demote themselves (to be raised again in team discussion at PR
  time); nobody can change or remove an OWNER, and OWNER is never
  granted here; admins remove themselves only through `DELETE
  /users/me`; PATCH for role change, 200 unchanged on a no-op, 403 for
  blocked actions, 404 for missing or deleted users; the list includes
  soft-deleted accounts, has partial case-insensitive search on
  id/username/email, a role filter, sorting on any field in either
  direction (role by rank), and page sizes of 20, 50 or 100 (default
  100). Ownership transfer was deferred to #7. Double confirmation
  stays in the frontend. A `deletedAt` field, an active/deleted filter
  and stale-role handling were deferred.
- **Author review:** The tool ran the full `./mvnw test` suite: 89/89
  passed. Ryan to review via the PR.

## 2026-09-25 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor
- **Scope:** user-service owner setup (PR #132): removed the setup
  token expiry and the setup log line, leaving only the multi-owner
  change. `OwnerSetupService`, `OwnerSetupController`, both test
  classes, both `application.yaml` files, `.env.example`,
  `compose.yaml` and the user-service README returned to their state in
  the multi-owner commit (21a86fb).
- **Prompt(s):** Summary: Token expiry is to be deferred to another issue in the future. The tool removed the
  expiry code, config and tests (including the lock-wait recheck added
  earlier the same day) and the log line with its test. The tool
  moved password hashing before the setup lock in `OwnerSetupService`,
  added a concurrent different-email test (two 201s, two OWNER rows) to
  `OwnerSetupControllerTest` with the concurrent-request code shared
  between both concurrency tests.
- **Author review:** The tool ran the full `./mvnw test` suite: 40/40
  passed. Ryan to review via the PR.

## 2026-09-25 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** PR #132 review fixes in user-service owner setup:
  `OwnerSetupService` re-checks the token expiry after taking the setup
  lock; `OwnerSetupServiceTest` and `OwnerSetupControllerTest` gain a
  lock-wait expiry case and a log-line assertion.
- **Prompt(s):** Summary: Ryan asked the tool to review the PR #132
  comments. The tool explained each one: the expiry could be passed
  while a request waits on the lock; the removed zero-owner guard
  conflicts with the text of issue #97 (a requirements update for the
  team, not code); the setup log line had no test; and LeongWZ's
  question about what happens after the expiry. Ryan chose to fix the
  first and third. The tool implemented them.
- **Author review:** The tool ran the full `./mvnw test` suite: 46/46
  passed. Ryan to review via the PR.

## 2026-09-25 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** user-service owner setup (issue #97): setup token expiry
  and a setup log line. `OwnerSetupService` reads
  `OWNER_SETUP_TOKEN_EXPIRES_AT`; `OwnerSetupController` logs each
  successful setup. Tests added to `OwnerSetupServiceTest` and
  `OwnerSetupControllerTest`; the variable added to `application.yaml`,
  the test `application.yaml`, `.env.example` and `compose.yaml`; the
  user-service README updated.
- **Prompt(s):** Summary: Asked to add expiry to tokens and remove 1 owner restriction. Token still
  set by the operator in `.env`; expiry only (no single use), given as
  an ISO-8601 timestamp; setup disabled (503) when no expiry is set; an
  expired token gets 403; and a log line on success with the new
  owner's id, email and caller IP, never the token. Asked to add log lines for creating owners for audit.
- **Author review:** The tool ran the full `./mvnw test` suite: 44/44
  passed. Ryan reviewed before PR.

## 2026-09-25 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor
- **Scope:** user-service owner setup (`POST /auth/setup-owner`, issue
  #97): removed the existing-owner check from `OwnerSetupService` and
  deleted the now-unused `OwnerAlreadySetException`; updated
  `OwnerSetupServiceTest` and `OwnerSetupControllerTest`; updated
  comments in `UserRepository`, `application.yaml`, `.env.example` and
  the user-service README.
- **Prompt(s):** Summary: Ryan asked whether the one-owner rule could be
  dropped, so an owner who loses both their password and email access
  doesn't leave the system without a recoverable owner. The tool listed
  the facts only: the backlog items and design-doc sections the change
  conflicts with (F6.2.3, design doc §6, the course's bootstrap guide),
  what it means for the setup token, and the questions left for the
  team. It made no recommendation. Ryan then decided to drop the
  "owner already exists" (409) check first. The tool removed it, kept
  the advisory lock (it still serialises the email/username uniqueness
  checks), changed the second-call test to expect another OWNER, and
  changed the concurrency test to use the same email (one 201, one
  400). Ryan then asked to rename `setupFirstOwner` to `setupOwner`; the
  tool renamed the service and controller methods and the test method
  prefixes.
- **Author review:** The tool ran the full `./mvnw test` suite: 39/39
  passed. Reviewed by Ryan before PR.

## 2026-09-23 — Ko-Khan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** debug
- **Scope:** `supplier-service/src/main/java/foc/supplier/seed/SuppliersSeeder.java`,
  `supplier-service/src/main/java/foc/supplier/model/Suppliers.java` —
  fixed a Maven `MojoFailureException` blocking both local build and the
  Docker image build, then two further runtime bugs found while
  verifying the seeder actually populates `supplier-db`.
- **Prompt(s):** Author reported "I am getting a mojofailure" (no other
  detail), then pasted the Docker build failure log. Asked to diagnose
  and fix. Findings: (1) `SuppliersSeeder.java` used `FileReader`
  without importing `java.io.FileReader` — compile error; (2) the CSV
  path pointed at `resources/data/...` but the seed file lives at
  `resources/csv/supplier-seed-data.csv` — confirmed with author before
  changing; (3) after fixing (1), `docker build` still failed:
  `CsvToBean.setType(Class)` does not exist in opencsv 5.9 (verified by
  extracting and `javap`-ing the jar inside a throwaway Maven
  container) — only `CsvToBeanBuilder.withType(...)` does, so the
  seeder was rebuilt to use `CsvToBeanBuilder`; (4) while in there,
  noticed the CSV header `Location Description` (has a space) would
  silently fail to auto-map to the `locationDescription` field under
  opencsv's default case-insensitive matching, so added an explicit
  `@CsvBindByName` for that one column.
  Separately, author reported a DBeaver "connection has been closed"
  error; traced to `supplier-db`'s container having been recreated
  (fresh `initdb`), invalidating DBeaver's open session — a client-side
  reconnect, not a code issue. While checking, found the containerized
  service was still failing to seed: (5) `FileNotFoundException` at
  runtime, because the Dockerfile's final stage only copies the built
  jar — `src/main/resources/csv/...` is packaged as a classpath
  resource inside the jar, not a file on disk, so a relative filesystem
  path can't resolve in the container even though it worked when run
  locally from the source tree; switched to `ClassPathResource`. (6)
  After that fix, seeding then failed with a `NOT NULL` violation on
  `name`: opencsv's `HeaderColumnNameMappingStrategy` stops
  auto-matching fields by name as soon as any field carries a
  `@CsvBindByName` annotation, so the earlier fix (4), which annotated
  only `locationDescription`, silently unbound every other field;
  fixed by annotating all CSV-seeded fields explicitly.
- **Author review:** No architecture, schema, or interface changes —
  same fields, same CSV, same DB columns; only the CSV-parsing
  implementation and file paths were corrected. Verified end-to-end by
  running `docker compose build supplier-service` and
  `docker compose up -d supplier-service` against the real
  `supplier-db` and querying the resulting table (21 rows, all columns
  including `location_description` populated correctly). Reviewed by
  author via pull request.

## 2026-09-23 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate
- **Scope:** issue #95 (user-service profile endpoints):
  `ProfileController` (`GET /users/me`, `GET /users/{id}`),
  `ProfileService`, `PublicProfileResponse`, `UserNotFoundException`,
  `UserRepository.findByIdAndDeletedAtIsNull`, `ProfileServiceTest`,
  `ProfileControllerTest`, and the `spring-boot-starter-security-test`
  test dependency in `user-service/pom.xml`.
- **Prompt(s):** Summary: The tool summarised F8.1/F8.1.1 from issue #9, the architecture
  doc and the wireframes, and listed the open interface decisions
  without choosing them. The implementation was based on these decisions:
  "GET /users/me for own profile, GET /users/{id} for others profile, which should show name, joined
  date for now, deleted users and unknown users should show user not
  found. caller identity should carry id for now." The tool then
  implemented exactly those decisions, reusing the existing
  `UserResponse` for the own profile. Follow-up on the PR #131 Copilot
  review: Ryan limited the public profile to the username (F8.1.1); the
  tool dropped `createdAt` from `PublicProfileResponse` and updated the
  service mapping and both test classes. Follow-up on the PR #131 review
  by Leong Wei Zhi: the tool explained each finding and Ryan chose the
  fix for each. Ryan decided that soft-deleted accounts keep their email
  and username reserved during the 30-day recovery window, so the
  repository's differing soft-delete filtering stays and is documented in
  `UserRepository`. The tool then restored the PR #126 entry below with a
  dated correction, guarded the principal-id parse in
  `ProfileController` (non-numeric → 401), marked the unauthenticated
  403 assertion as provisional until #91, added a controller-scoped
  `ProblemDetail` handler for the 404, fixed a stale README sentence,
  moved the `User` → `UserResponse` mapping into `UserResponse.from`
  (also used by `OwnerSetupService`), moved the four Testcontainers
  classes onto a shared `PostgresTestContainer` base, and added
  `id`/`createdAt` assertions.
- **Author review:** Ryan reviewed the code and ran the full
  `./mvnw test` suite to ensure it all passed (38/38). After the PR #131
  review fixes the tool ran the full suite: 39/39 passed, with one
  shared Postgres container.

## 2026-09-23 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate, refactor
- **Scope:** issues #85 + #86 in one PR. Infra (#85): `compose.yaml`
  user-service + user-db entries and volume, `.env.example` User
  Service port/DB variables, `AGENTS.md` user-db port-table row,
  `user-service/src/main/resources/application.yaml` datasource +
  `ddl-auto: update` — all following the supplier-service rows.
  Schema (#86): new entities `Otp`, `AccountToken`,
  `TokenDenylistEntry` from the design doc's §2 erDiagram; `User.role`
  converted from String to a new `Role` enum (USER/ADMIN/OWNER, stored
  as text) with the design doc's CHECK constraint, updating PR #126's
  usages (`UserRepository.countByRole`, `OwnerSetupService`, both test
  classes; `UserResponse` JSON shape unchanged); new
  `EntityMappingTest` round-trip tests (Testcontainers pattern).
  README AI Use Summary extended for this work (PR #130 Copilot
  review).
- **Prompt(s):** Asked to read the D2 design doc's Task Allocation and
  plan/implement user-service tasks #2 and #3 (issues #85/#86) in one
  PR. Decisions were made by Leong Wei Zhi via neutral options Q&As:
  postgres:17 image (matching the merged Testcontainers tests), host
  ports 8087/5435 (8085/5433 reserved for the notification re-add),
  Role as a Java enum with the CHECK added now, @ManyToOne FK
  representation for `otps`/`account_tokens`, and round-trip test
  scope. The OWNER value follows the merged owner-bootstrap feature
  (issue #97): treated as admin-equivalent.
- **Author review:** full suite green locally (28/28 incl. Ryan's
  OwnerSetup tests, BUILD SUCCESS); `docker compose up --build
  user-service user-db` from a fresh volume verified: health UP on
  8087, all four tables present with the role CHECK, unique
  email/username indexes and both FKs (inspected via psql), and
  `POST /auth/setup-owner` exercised end-to-end (201 with role
  "OWNER", 409 on the second call, BCrypt hash stored). An explicit
  @Check was dropped during review: Hibernate 7 already generates the
  role CHECK from the STRING enum mapping, and the duplicate showed up
  in psql. Reviewed via pull request.

## 2026-09-23 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** generate (implementation + tests)
- **Scope:** `web/` Admin Dashboard (issue #113), built to match
  `web/docs/wireframes/admin-dashboard.png`: `src/routes/admin.tsx` (+
  one `/admin` entry in `src/routes/index.tsx`); Users section in
  `src/features/user/` (`types.ts`, `adminApi.ts` in-memory mock,
  `components/UsersSection.tsx`, `UserTable.tsx`, `RemoveUserModal.tsx`);
  Credits column + Add Credits in `src/features/credit/` (`types.ts`,
  `adminCreditApi.ts` in-memory mock, `components/AddCreditsModal.tsx`);
  Suppliers section as two new files in `src/features/supplier/components/`
  (`SuppliersAdminSection.tsx`, `SupplierTable.tsx`) reusing the existing
  supplier `api.ts` and form/delete modals, with no existing supplier code
  changed; `src/test/admin.test.tsx` (15 cases).
- **Prompt(s):** "create a new branch and lets work on the UI", "cant i
  just create the ui and wire it up later", then "can you make it so that
  it matches wireframe?". Users and credits run on in-memory mocks because
  #96 and credit-service have not defined their APIs; no endpoint paths
  were chosen. The Suppliers section calls the real supplier api and shows
  its error state until supplier-service serves data. Defaults applied and
  stated to the author: route without a nav item, USER/ADMIN/OWNER roles
  matching the PR #126 entity, owner row limited to Add Credits.
- **Author review:** Vitest 18/18, tsc, eslint
  and prettier clean on the new files; checked at desktop (1280/1920px)
  and mobile (390px) widths in the browser.

---
## 2026-09-23 — Ryan Ang
- **Tool:** Claude Code (Opus 5.5)
- **Mode:** refactor, debug, generate (tests), docs
- **Scope:** `user-service/` fixes from the PR #126 review (issue #97):
  renamed `dto/userResponse.java` → `UserResponse.java`,
  `repository/userRepository.java` → `UserRepository.java`, and
  `OwnerSetUpControllerTest.java` → `OwnerSetupControllerTest.java`;
  `SecurityConfig.java` (permit `/actuator/health`);
  `SetupOwnerRequest.java` (case-insensitive, whitespace-tolerant email
  pattern; redundant `@Email` removed; password size message covers both
  bounds); `User.java` (dropped `unique = true` duplicated by the named
  unique indexes); `pom.xml` (removed duplicate `spring-boot-starter-web`
  and explicit `jackson-databind`, dropped the `testcontainers-bom` import,
  switched to Testcontainers 2.x artifact names); tests moved to Jackson 3
  / Testcontainers 2.x, with new controller cases for mixed-case/padded
  email, over-long password, concurrent setup requests, and public health
  endpoint; missing AI disclosure headers added to `OwnerSetupService`,
  `OwnerSetupController`, `UserRepository`, `UserResponse`,
  `OwnerAlreadySetException`, and `src/test/resources/application.yaml`;
  existing headers extended in the other touched files; README AI Use
  Summary updated.
- **Prompt(s):** Asked the tool to review the PR #126 comments, then to
  apply every review finding except the two left for a team
  decision (durable bootstrap-used marker; production datasource/schema
  strategy and compose wiring).
- **Author review:**  `OwnerSetupServiceTest`
  (9/9) passed locally, and all test sources compile; the Testcontainers
  controller/context tests were not run because Docker was not running.
  (Corrected 2026-09-25: Ryan re-ran the full `./mvnw test` suite with
  Docker running on the `feat/profile-endpoints` branch; all tests
  passed.)

## 2026-09-22 — Ryan Ang
- **Tool:** Claude Code (Sonnet 4.6)
- **Mode:** debug, generate (tests)
- **Scope:** `user-service/` test suite for issue #86 / #91 scope:
  `src/test/java/foc/user/controller/OwnerSetUpControllerTest.java` (bug
  fixes + 4 additional cases), `src/test/java/foc/user/service/OwnerSetupServiceTest.java`
  (new file), `src/test/java/foc/user/UserServiceApplicationTests.java`
  (Testcontainers wiring), `src/test/resources/application.yaml` (new file).
- **Prompt(s):** Asked to diagnose why `OwnerSetUpControllerTest` was not
  running. Issues found and fixed: (1) `@AutoConfigureMockMvc` import was
  wrong for Spring Boot 4.x — corrected to
  `org.springframework.boot.webmvc.test.autoconfigure`; (2) `pom.xml` had
  two non-existent test artifacts (`spring-boot-starter-actuator-test`,
  `spring-boot-starter-webmvc-test`) causing Maven to fail dependency
  resolution — `spring-boot-starter-webmvc-test` restored (Spring Boot 4.x
  artifact), bogus actuator-test artifact removed; (3) `ObjectMapper` was
  `@Autowired` but not registered as a bean in the test context — replaced
  with `new ObjectMapper()`; (4) `UserServiceApplicationTests` had no
  datasource, causing context load failure — wired up the same
  Testcontainers `@ServiceConnection` pattern; (5) `users` table did not
  exist in the Testcontainers Postgres DB — created
  `src/test/resources/application.yaml` with `ddl-auto: create-drop`.
  After tests passed, asked whether more test coverage was warranted; the
  tool generated `OwnerSetupServiceTest` (6 Mockito unit tests: owner guard,
  duplicate email/username, email normalisation, username trim, happy path)
  and added 4 controller test cases (blank email, password missing uppercase,
  password missing digit, invalid username characters). A subsequent
  `UnnecessaryStubbingException` from Mockito strict mode was fixed by
  changing `@BeforeEach` stubs to `lenient()`.
- **Author review:** Ryan ran `mvn test` after each fix and confirmed
  15/15 tests green at that point. Confirmed tests cover the self-disabling owner
  bootstrap feature end-to-end (happy path, idempotency guard, all
  Bean Validation constraints, service-layer business rules).
  (Corrected 2026-09-23: after the setup-token gate the suite grew to
  20 tests — 10 controller, 9 service, `contextLoads` — and the PR #126
  review fixes below add 4 more controller cases.)

## 2026-09-22 — Ryan Ang
- **Tool:** Claude (Sonnet 5)
- **Mode:** generate (implementation)
- **Scope:** `user-service/` first-owner bootstrap feature (issue #86 /
  #91 scope): `src/main/java/foc/user/dto/SetupOwnerRequest.java`,
  `src/main/java/foc/user/dto/UserResponse.java`,
  `src/main/java/foc/user/entity/User.java`,
  `src/main/java/foc/user/repository/UserRepository.java`,
  `src/main/java/foc/user/service/OwnerSetupService.java`,
  `src/main/java/foc/user/controller/OwnerSetupController.java`,
  `src/main/java/foc/user/exception/OwnerAlreadySetException.java`,
  and an initial `OwnerSetUpControllerTest.java` (4 cases).
- **Prompt(s):** Generated the `SetupOwnerRequest` DTO with Bean
  Validation annotations (NUS email regex, password strength rules,
  username format). Converted the agreed user schema into the `User`
  JPA entity. Generated initial controller integration tests with Testcontainers Postgres setup.
- **Author review:** Ryan validated that the entity matches the agreed
  schema, that the email/password/username regex rules are correct, and
  that the endpoint logic (advisory lock, owner guard, uniqueness
  checks, BCrypt hashing) matches the feature design. 

## 2026-09-22 — Ko-Khan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** generate (scaffolding/boilerplate)
- **Scope:** `supplier-service/` Spring Boot scaffold: completed
  `pom.xml` into a full POM (kept the author's existing postgres +
  opencsv dependencies, added the standard actuator/webmvc/data-jpa
  starters and matching test starters, following the user-service /
  notification-service pattern), `application.yaml`, `Dockerfile`,
  application + contextLoads test classes (`foc.supplier`), Maven
  wrapper and Initializr dotfiles copied from `user-service/`, and a
  short service README. Also fixed `compose.yaml`, which had the
  notification-service/rabbitmq/notification-db block and `name: foc`
  accidentally commented out, and a malformed `supplier-db` block (not
  nested under `services:`, no matching `volumes:` entry) in the
  working tree before this session started; restored the former and
  properly wired `supplier-db` + a new `supplier-service` app service
  (host ports 8086 / 5434), following the notification-service
  pattern. Added the Supplier Service section to `.env.example` and
  filled in the `supplier-db` row of the port table in `AGENTS.md`.
- **Prompt(s):** Asked to set up Spring Boot for supplier-service.
  Flagged the compose.yaml anomaly and asked the author to confirm
  scope before proceeding (options Q&A): restore + fix compose.yaml
  vs. leave as-is, and full runnable skeleton vs. pom.xml only. Author
  chose restore + fix and the full skeleton.
- **Author review:** No architecture or dependency decisions were
  made — the dependency set, package layout, and file shapes mirror
  the already-team-reviewed user-service/notification-service scaffolds
  exactly; the pre-existing `postgres`/`opencsv` dependency choice in
  the partial `pom.xml` was kept unchanged. `docker compose config`
  verified the fixed compose.yaml resolves cleanly. Reviewed via pull
  request.

---
## 2026-09-21 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate
- **Scope:** `web/` SPA scaffold, redone from scratch after PR #118 was
  closed unmerged (issue #108): Vite/React/TS project skeleton
  (`package.json`, `vite.config.ts`, tsconfigs, `index.html`,
  `src/index.css`, `.nvmrc`, `.prettierrc`/`.prettierignore`,
  `eslint.config.js`, `.gitignore`/`.dockerignore`); react-router (v8)
  route table (`src/routes/index.tsx`, `src/routes/home.tsx`,
  `src/main.tsx`); shared app shell rebuilt to the committed wireframes
  (`src/shared/shell/` — top bar, mobile bottom tab bar, credits +
  notification placeholders, 404); shared API layer
  (`src/lib/api/config.ts`, `http.ts`); convention READMEs
  (`web/README.md`, `src/features/`, `src/shared/{auth,components}/`);
  tests (`src/test/`); Docker/nginx/compose wiring and the root
  `.env.example`, `AGENTS.md` port-table row, `.gitignore`, and README
  AI Use Summary updates. Files that cannot carry a header comment
  (`package.json`, `package-lock.json`, the tsconfigs, `.gitignore`,
  `.nvmrc`, `.prettierrc`, `public/favicon.svg`) are covered by this
  entry instead.
- **Prompt(s):** Author directed a fresh, leaner redo of the PR #118
  scaffold and made each decision via neutral-options Q&A: drop
  TanStack Router and TanStack Query (too much lock-in for the other
  owners); routing via react-router (v8) in data-router mode
  (`createBrowserRouter` with a plain route table, no codegen); no
  data-fetching library at all (only the shared `apiFetch` wrapper —
  each owner picks their own tools); keep Tailwind CSS, the
  features/shared/lib folder structure and API config, Docker +
  compose, and the Vitest/ESLint/Prettier tooling; include the shared
  app shell but follow the committed wireframes (top nav bar with
  credits badge and notification bell; mobile bottom tab bar instead
  of PR #118's hamburger menu). Two issues flagged on the old PR were
  fixed in the port: the compose default for the notification URL no
  longer hardcodes a port (values live in `.env`), and `apiFetch`
  merges headers via `Headers` so all `RequestInit.headers` forms
  work.
- **Author review:** `npm run lint`, `npm run format:check`,
  `npm run test`, `npm run build` all green; dev-server and
  `docker compose up --build web` smoke tests including the
  deep-link 404 fallback; Playwright viewport checks at 1280×800
  (top nav, no tab bar) and 375×812 (bottom tab bar, credits and
  bell visible) against the wireframes; reviewed via pull request.

## 2026-09-21 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs
- **Scope:** Notification docs cleanup — `docs/notification-service.md`
  / `.mmd`, `docs/architecture.md` / `.mmd` (notification-related
  parts), `notification-service/README.md`, `notification-service/AGENTS.md`
  (previously empty). Same treatment for `foc-contracts/README.md` and
  the new `foc-contracts/AGENTS.md` (checklists and producer
  conventions moved there from the README, per the author's choice
  from neutral options). No code changes.
- **Prompt(s):** Asked to make the notification docs human-readable:
  restructure the README from an issue-by-issue changelog into topic
  sections, split human-facing (README) from agent-facing (AGENTS.md)
  documentation, and clean up the Mermaid diagrams. Cleanup choices
  (compress disclosure changelogs to static summaries, delete
  superseded decision rows and closed open-items, delete the README
  state narrative, strip requirement IDs from diagram labels) were made
  by the author via neutral options Q&As.
- **Author review:** No design decisions were made or changed — content
  was reorganized, condensed, or deleted, and stale facts corrected
  against the code (REST API marked planned per issue #66; the D19
  stale-discard removal and the D22 order→request rename propagated to
  `docs/architecture.md`/`.mmd`; leftover pre-D17 "envelope" wording
  replaced). Reviewed via pull request.

## 2026-09-20 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (scaffolding/boilerplate)
- **Scope:** `user-service/` Spring Boot scaffold for issue #84: `pom.xml`,
  `application.yaml`, `Dockerfile`, application + contextLoads test
  classes (`foc.user`), Maven wrapper and Initializr dotfiles copied
  from `notification-service/`, and a short service README. No compose
  row, `.env.example` vars, or port-table entry (issue #85's scope);
  no datasource, security, or endpoints beyond actuator health
  (issues #86+).
- **Prompt(s):** Asked to plan and resolve issue #84. The tool explored
  the notification-service pattern and put the non-mechanical scaffold
  choices to the author as neutral options; the author chose a minimal
  boot-only dependency set (actuator + webmvc, deferring data-jpa and
  security to their issues), a root-build-context Dockerfile without
  the foc-contracts stage, and a name-plus-health-only
  `application.yaml`.
- **Author review:** `./mvnw test` (contextLoads), local boot with a
  health-endpoint check, and a Docker image build; reviewed via pull
  request.

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
- **Follow-up (PR #83 Copilot review):** two findings addressed.
  (1) High — the published database port used the short `5433:5432`
  form, which binds every host interface and would have exposed the DB
  to the LAN; it is now `127.0.0.1:${NOTIFICATION_DB_HOST_PORT:-5433}`.
  Verified behaviourally: loopback and `localhost` connect, the
  machine's LAN address is refused. (2) Medium — all three stdio
  servers ran from floating specs, so teammates could get a different
  build from the one verified here; each is now pinned
  (`@upstash/context7-mcp@4.1.1`,
  `@microsoft/postgres-mcp@0.1.0-rc.11`, `@playwright/mcp@0.0.82`) and
  re-verified at those versions, with the bump policy written into
  AGENTS.md.
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

## 2026-10-05 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5)
- **Mode:** debugging assistance, implementation (unit test)
- **Prompt(s):** Asked to fix supplier category storage: sync
  `Suppliers.category` on create/update (categories joined with `/`),
  capitalise category names on save (`Food`/`food`/`fOOD` -> `Food`), and
  refresh the category filter after a save. Issue #160, branch
  `fix/supplier-category-sync`.
- **Scope:** `supplier-service` `SupplierService` (save/create/update),
  `SupplierCategories` (new `normalizeCategory` helper), `SuppliersSeeder`
  (same normalisation), new `SupplierCategoriesTest`; `web`
  `suppliers.tsx` (category effect re-runs on `refreshKey`).
- **Author review:** Required the team-decided format (`/`-joined names,
  first-letter capitalisation) beforehand; the schema of
  `SupplierCategories` and whether `Suppliers.category` stays were left to
  * the team and are not changed here. Backend tests pass (42, Java 21 via
  Docker); web `tsc` passes. Pending author review of the diff.

## 2026-10-07 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** refactoring, implementation
- **Prompt(s):** Asked to apply PR #161 review feedback (LeongWZ): write
  `null` rather than `""` to `Suppliers.category` when a supplier has no
  categories, reject `/` in category names, and set the flat column before
  `save()` to avoid a second UPDATE per create.
- **Scope:** `supplier-service` `SupplierService` (categories normalised and
  validated before save; flat column set in `applyRequest`), new
  `InvalidCategoryException`, `SupplierController` (400 handler).
- **Author review:** Rejecting `/` in category names is a validation-rule
  choice made by the author. Pending author review of the diff.

## 2026-10-07 — Alastair Tan
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** debugging assistance
- **Prompt(s):** Asked to apply PR #161 review feedback (LeongWZ): guard
  `SuppliersSeeder` against a null `Suppliers.category` (empty CSV `Type`
  cell) so seeding doesn't throw an NPE and abort startup.
- **Scope:** `supplier-service` `SuppliersSeeder` (null check around the
  category split loop).
- **Author review:** Pending author review of the diff.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** implementation, unit tests (design decided by the team first)
- **Prompt(s):** Asked to plan and implement issue #151's logout denylist
  with an nginx API gateway and Redis (both named in the prompt). All
  design choices were made by Leong Wei Zhi through ten neutral
  AskUserQuestion option rounds (gateway tech = OpenResty/Lua; Redis-only
  TTL-key denylist replacing the token_denylist table; full routing
  cutover behind the gateway; gateway-only enforcement; anonymous
  pass-through; fail-open on Redis outage; AOF persistence; logout in
  user-service; best-effort 204 on a failed Redis write; "Token has been
  revoked" 401 detail); the tool presented factual comparisons only.
- **Scope (this entry: the user-service/Redis half of the single
  feat/api-gateway PR):** `user-service` POST /auth/logout
  (`AuthController`), `TokenDenylist` Redis writer, `JwtVerifier`
  VerifiedToken +jti/+expiresAt, `JwtAuthenticationFilter` details,
  `SecurityConfig` authenticated() rule, `V6__drop_token_denylist.sql`,
  `TokenDenylistEntry` deleted, data-redis starter + application.yaml;
  tests: `LogoutTest`, `TokenDenylistTest`, `JwtVerifierTest`,
  `FlywayMigrationTest`, `EntityMappingTest`; `compose.yaml` redis
  container + user-service env; `.env.example` Redis section;
  user-service README.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** implementation, documentation (design decided by the team
  first; same session as the entry above — this is the gateway half,
  shipped with it in the one feat/api-gateway PR at the author's
  request)
- **Prompt(s):** Continuation of the issue #151 plan: build the OpenResty
  gateway and cut the frontend over to it, per the ten decisions made by
  Leong Wei Zhi via options Q&A (see the entry above and docs/gateway.md
  G1–G10).
- **Scope:** new `gateway/` (Dockerfile pinned to
  openresty/openresty:1.31.1.1-alpine-fat + opm lua-resty-jwt,
  `nginx.conf` path-prefix routing + WS proxy, `lua/auth.lua` bearer
  verify + Redis denylist check, fail-open, problem+json 401s);
  `compose.yaml` gateway service + backend host ports unpublished;
  `web` AuthProvider logout POST (best-effort) + auth.test.tsx logout
  cases + config.ts comments; `.env.example` GATEWAY_PORT/VITE cutover;
  `AGENTS.md` port rows; `docs/gateway.md` (new, decisions table),
  `docs/architecture.md`/`.mmd` gateway + Redis, D10 note in
  `docs/notification-service.md`; README AI Use Summary.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** debugging assistance, refactoring (PR #176 Copilot review)
- **Prompt(s):** Asked to address the five Copilot findings on PR #176.
- **Scope:** `gateway/lua/auth.lua` skips user-service's public
  `/auth/**` routes except logout (mirrors JwtAuthenticationFilter's
  issue-#91 skip — a stale SPA token no longer blocks login at the
  gateway); `gateway/nginx.conf` order stub now runs the same auth
  check and answers CORS-readably (incl. its own preflight);
  `compose.yaml` drops the redis health gates on gateway and
  user-service (fail-open G6 / best-effort G9 must hold at startup
  too); `docs/architecture.md` auth note qualified (anonymous routes,
  the /ws limit); `user-service/README.md` old-databases warning no
  longer lists the dropped token_denylist; `docs/gateway.md` updated to
  match. Behavior-preserving fixes to recorded decisions; no new
  design decisions.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** documentation (PR #176 Copilot re-review)
- **Prompt(s):** Asked to address the re-review's remaining finding: the
  service READMEs still pointed at the backend host ports the cutover
  unpublished.
- **Scope:** `user-service/README.md`, `credit-service/README.md`,
  `notification-service/README.md` — run/health-check URLs now go via
  the gateway prefix (`${GATEWAY_PORT:-8090}/api/<service>/**`), with
  the in-network name noted where relevant. The gateway health path was
  verified against the running stack (200).
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** debugging assistance, refactoring (PR #176 review, @Sinnez1)
- **Prompt(s):** Asked to address the team review's eight findings (two
  blocking).
- **Scope:** `gateway/nginx.conf` — the public-auth skip rebuilt as
  location blocks (the raw request_uri match was bypassable with
  dot-segment/percent-encoded paths; locations match the normalized
  URI, closing the revoked-token bypass the reviewer reproduced);
  `/actuator/health` mirrored; the three proxy headers repeated per
  location (location-level proxy_set_header cancels inheritance);
  gateway-origin 404/502/504 now problem+json with the CORS origin
  header; the order stub builds its body with cjson. `gateway/lua/auth.lua`
  — path-skip removed (locations own it). `gateway/Dockerfile` — opm
  package pinned to 0.3.2. `compose.yaml` — the five VITE_* defaults
  track the gateway so an unset value no longer builds a dead bundle;
  stale values documented as needing a .env update (also added to the
  PR description). `docs/gateway.md` — G5, routing table, error-shape
  note and the encoded-slash known limit. All reviewer reproductions
  re-run against the rebuilt stack: bypass paths 401, skips intact,
  JSON valid, CORS headers present. Behavior-preserving fixes; no new
  design decisions.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** code generation with options Q&A (issue #94)
- **Prompt(s):** Asked to plan and implement issue #94 (account recovery
  + password reset via token table). Five flow decisions made by the
  author via options Q&A before coding: reset credential is an opaque
  token in an emailed link (not an OTP code); recovery is login +
  explicit restore (login stops silently recovering — reverses the
  #93-era behavior; POST /users/me/recover restores); a reset on a
  soft-deleted account inside the window works but leaves it deleted;
  the request endpoint answers 202 whatever the email maps to (with the
  silent-202 cooldown corollary, since a 429 would reveal account
  existence); scope covers backend and the web screens together.
- **Scope:** user-service — `V7__account_tokens_reset.sql` (created_at,
  unique token_hash, unique user_id+kind), `AccountToken` (createdAt,
  replace(), RECOVERY dropped from the enum), `AccountTokenRepository` +
  `UserRepository.findWithLockById` (one lock order: user then token),
  new `PasswordResetService`/`PasswordResetController`/DTOs/
  `ResetTokenInvalidException` (+ ProblemTypes URI and advice handler),
  `OtpEmailSender.sendPasswordResetLink` (+ SMTP impl), SHA-256 token
  hashing (lookup-by-hash needs a deterministic hash; 256-bit entropy
  needs no slow one — documented as the inverse of the OTP BCrypt
  rationale), `LoginAttempts` no longer clears deletedAt (+ purgeAt),
  `LoginResponse.deletionScheduledFor`, `ProfileService/Controller`
  recover route + SecurityConfig line, purge scheduler sweep,
  application.yaml/.env.example `PASSWORD_RESET_TTL`. web — login's
  forgot-password link and reset notice, `forgot-password.tsx`,
  `reset-password.tsx` (URL token strip), `account-recovery.tsx`
  (wireframe's restore/keep-deleted), AuthProvider/useAuth `restored()`
  + deleted-session guard in ProtectedRoute, `apiFetch` tolerates the
  bodyless 202. Tests: new PasswordResetService/Controller tests, web
  password-reset and account-recovery suites; existing login/profile/
  purge/Flyway/entity tests updated (user-service 350 green, web 112
  green). Docs: README #94 section + corrections to the two sections
  that said login recovers.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** code generation with options Q&A (issue #94, revision)
- **Prompt(s):** Asked whether the reset flow could use an OTP instead;
  the author chose "reset via OTP code" via options Q&A, revisiting the
  same day's link-token decision. Follow-on shapes applied and
  documented: confirm carries email + code (a salted BCrypt code hash
  cannot be looked up); the codes ride the user.otp.* knobs whole (the
  PASSWORD_RESET_TTL knob went); and every confirm failure is the one
  reset-code-invalid 400 — stricter than sign-up's verify on purpose,
  since a 429 or an expired-specific answer on this public route would
  reveal which addresses hold accounts.
- **Scope:** user-service — V8 (attempts column, V7's unique hash index
  dropped), AccountToken (codeHash naming, attempts, replace() resets
  the budget), AccountTokenRepository (by-hash finders removed),
  PasswordResetService rewritten for the code flow (dummy-hash cost for
  missing rows, spent-until-replaced attempts), DTO gains email + code,
  ResetTokenInvalidException → ResetCodeInvalidException with
  urn:foc:user:reset-code-invalid, sendPasswordResetCode replaces the
  link mail, yaml/.env/test pins drop the reset TTL knob. web — the
  /reset-password route went; forgot-password.tsx is the wireframe's
  two steps on one page (email → code + new password, resend/change
  affordances). Tests reworked on both sides (user-service 353 green,
  web 112 green); README #94 section and Flyway paragraph rewritten.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactoring with options Q&A (issue #94, PR #179 review)
- **Prompt(s):** Asked to rename account_tokens to something
  reset-specific and remove the kind column, now that recovery carries
  no emailed credential. Names chosen by the author via options Q&A:
  table password_reset_otps / entity PasswordResetOtp (the
  account_update_otps parallel), and the token_hash column follows as
  code_hash (revisiting the earlier keep-tokenHash call now that the
  table itself says OTP).
- **Scope:** `V9__password_reset_otps.sql` (rename table and column,
  drop kind — V7's composite index and V1's CHECK go with it —
  per-user unique index, FK renamed to the siblings' convention; a new
  migration, not an edit, since V7/V8 are applied on dev DBs).
  AccountToken/AccountTokenRepository → PasswordResetOtp/
  PasswordResetOtpRepository (Kind enum gone, finders lose the kind
  parameter); PasswordResetService and AccountPurgeScheduler follow;
  stale comments updated (UserRepository's "login recovers" note
  included). Tests track the rename (user-service suite green);
  README/application.yaml/.env.example wording updated.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** refactoring (issue #94, PR #179 review)
- **Prompt(s):** Asked to collapse the branch's three stacked
  migrations into one, since they all belong to one PR.
- **Scope:** V7/V8/V9 squashed into a single `V7__password_reset_otps.sql`
  (rename + code_hash + drop kind + created_at + attempts + per-user
  unique index + FK rename, with the design iterations noted in its
  header); FlywayMigrationTest counts back to 6/7 and its proof block
  rewritten; the V8/V9 references in headers, comments and the README's
  Flyway paragraph updated. Dev databases that ran the branch's stacked
  migrations must be reset (README's "Old local databases" steps) —
  noted in the migration header and the PR.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-11 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** debugging assistance, refactoring (PR #179 Copilot review)
- **Prompt(s):** Asked to address the Copilot review's findings.
- **Scope:** (1) stale-credential login race — LoginAttempts.record now
  receives the hash the bcrypt check ran against and answers
  STALE_CREDENTIAL when the locked re-read shows a different one (a
  concurrent reset could otherwise turn an old-password match into a
  fresh JWT); AuthService redoes the whole check, three stale rounds
  answer as the existing concurrent-update 409. (2) Both reset flows'
  FIRST read is the locked one (UserRepository.findWithLockByEmail) —
  the plain-read-then-lock-by-id shape kept stale entity state, so a
  code could be mailed to a just-replaced address; the purged-between-
  lookups case now falls through the same dummy-BCrypt branch as an
  unknown email. (3) The purge locks its condemned user rows before
  touching child tables (findWithLockByDeletedBefore), matching the
  flows' user-then-child lock order instead of deadlocking against a
  reset request. (4) forgot-password.tsx: "Change email" clears the
  typed passwords, and the step-2 resend/change buttons are disabled
  while a request is in flight. (5) The README's consolidated AI Use
  Summary extended for issue #94 (it ended at #151). Tests added or
  updated for (1)–(4); the review's AI-policy finding is left to the
  team, not resolved by wording changes.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** debugging assistance, refactoring, documentation (PR #179
  review by @Sinnez1)
- **Prompt(s):** Asked to address the latest PR #179 review comments.
  The review's four security/design questions (OTP brute-force cap,
  deleted-account access scope, admin-removal recoverability, fix vs
  document for the lock-across-send and surviving-sessions findings)
  were put to Leong Wei Zhi as neutral options Q&A; the tool
  implemented the choices.
- **Scope:** (1) Windows test fix — the deleted-login test truncates
  its Instant to microseconds, matching what Postgres stores. (2) OTP
  brute-force cap (chosen: carry attempts across renewals) —
  PasswordResetOtp.replace split into renew (live row, count carried)
  and replaceExpired (dead row, fresh budget); a spent live row absorbs
  requests silently, so the budget is OTP_MAX_ATTEMPTS guesses per
  OTP_TTL, not per cooldown. (3) Lock-across-send (chosen: fix) —
  requestReset parks the row in a TransactionTemplate transaction that
  commits before the SMTP send; a failed send retracts the undelivered
  code best-effort and still answers 502. (4) Sessions surviving a
  reset (chosen: fix) — gateway decision G11: confirmReset stamps
  denylist:user:<id> via TokenDenylist.revokeUserSessions, and
  gateway/lua/auth.lua rejects the subject's tokens whose iat predates
  the stamp (fail-open, best-effort, docs/gateway.md updated).
  (5) Deleted-account access (chosen: accept) — README and PR body now
  say the full 30-day window, not ≤1 h. (6) Admin removal (chosen:
  same as self-delete) — recorded in AdminService and the README.
  Tests: suite 354 → 359 (renewal/absorb/retract/eviction pins).
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** debugging assistance, refactoring, documentation (PR #179
  approval follow-ups by @Sinnez1)
- **Prompt(s):** Asked to address the latest PR #179 comments — the
  seven non-blocking follow-ups on @Sinnez1's approval. The two that
  were design/security choices (what admin removal does to the ADMIN
  role; how retract handles a failed send on a renewal) were put to
  Leong Wei Zhi as neutral options Q&A; the tool implemented the
  choices and the mechanical corrections.
- **Scope:** (1) Admin removal (chosen: demote to USER; alternatives
  were an active-check in the admin routes or documenting as
  accepted) — AdminService.removeUser sets the role before the
  soft-delete, so a removed admin's later logins and a self-restored
  account carry no admin powers; a pre-removal ADMIN token lasts its
  ≤1 h exp. Pinned in AdminServiceTest. (2) Failed renewal send
  (chosen: restore the pre-renewal row; alternatives were leaving the
  renewed row in place or documenting the budget-reset exception) —
  PreparedReset snapshots the live row before renew, retract restores
  code/clock via PasswordResetOtp.restore instead of deleting (new or
  expired-takeover rows still delete), closing the second
  budget-renewal path and keeping the delivered code working. Pinned
  in PasswordResetServiceTest. (3) user-service README: the stale
  pre-G11 "old JWT stays valid" sentence replaced with the
  Redis-failure/same-second exceptions; the retract and soft-delete
  passages updated for (1)/(2). (4) Root README AI Use Summary: web
  clauses moved out (the web half is PR #180's), scope corrected to
  backend-only, this round appended. (5) TokenDenylist javadoc records
  the reviewer-reproduced same-second survivor and the
  login-racing-the-reset gap as the same accepted leak. (6) Test
  isolation: PasswordResetControllerTest and AccountPurgeSchedulerTest
  clear password_reset_otps in @AfterEach (AccountUpdateControllerTest's
  rule) so a leftover row cannot break other classes' deleteAll setup.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (issue #178, web half — PR #182), then refactor
  (PR #182 Copilot review)
- **Prompt(s):** Asked to plan and resolve issue #178 (a demoted admin
  keeps ADMIN until their token expires); the revocation mechanism
  (option A, per-user epoch stamp), the scope (the issue's two paths
  only), the web reaction (force sign-out on any token-bearing 401)
  and the sign-out UX (/login with a notice) were decided by the
  author via neutral options Q&A. Then asked to address the PR's
  review comments.
- **Scope:** `web/src/lib/api/http.ts` — a swappable unauthorized
  handler, invoked before the throw when a 401 answers a request that
  carried the bearer token; review round: `auth: false` opts a call
  out of the token injection. `web/src/features/user/AuthProvider.tsx`
  — registers the handler: clears the stored session and navigates to
  /login; guarded on a live session so logout's own best-effort 401
  doesn't re-navigate. `web/src/features/user/sessionNotice.ts` (new)
  — one-shot flag carrying the "session ended" notice to the login
  page; chosen over router navigation state because ProtectedRoute's
  stateless bounce races the handler's startTransition-deferred
  navigation and can wipe the state before the login page mounts (the
  test caught this; the file documents it).
  `web/src/features/user/login.tsx` — renders the notice; review
  round: the login call sends no bearer token, so a stale stored
  session can't make a wrong-credentials 401 read as a revoked
  session. `web/src/test/auth.test.tsx` — revoked-session cases, plus
  the review round's stale-session login regression.
- **Author review:** web 102/102 (vitest), eslint, tsc, prettier; the
  diff reviewed via the PR.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** generate (issue #178, server half — PR #183)
- **Prompt(s):** Same issue #178 session as the web half above, for the
  server side: which revocation mechanism (the author chose option A of
  the issue's table — a per-user epoch stamp in Redis, enforced at the
  gateway — via neutral options Q&A, accepting its documented trade-off
  that one demotion signs the target out on every device) and how wide
  to scope it (issue scope only: the two paths the issue names, no
  further stamp points).
- **Scope:** `user-service` — `AdminService` takes `TokenDenylist` and
  calls `revokeUserSessions(targetId)` after the save in `changeRole`
  (any actual change, promotion and self-demotion included; the
  same-role early return and every refusal path stamp nothing) and in
  `removeUser`, best-effort at the same call-site pattern as
  `PasswordResetService.confirmReset`. `AdminServiceTest` pins the
  eviction on each success path and its absence on the no-op and the
  refusals; `AdminControllerTest` pins it end-to-end with
  `TokenDenylist` as a `@MockitoBean` (no Redis in that test stack,
  like `LogoutTest`). `docs/gateway.md` — decision G12 and its
  request-flow note; **no gateway code changed**, since G11's
  `iat`-versus-stamp check already covers the new stamp writer.
- **Author review:** user-service suite via Testcontainers, and the
  diff reviewed via PR #183.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** implementation + documentation (issue #67, remaining scope)
- **Prompt(s):** Asked to plan and resolve issue #67. Exploration found
  the server half already merged (PR #74); the remaining scope was
  decided by the author via neutral options Q&A: badge-only UI (no
  dropdown/toasts — issue #66 owns the Notification Center), native
  WebSocket only (no sockjs-client), the verifier's missing-exp fix
  folded in, and D23 — notification-service reads the Redis denylist at
  STOMP CONNECT itself, fail-open, as a narrow exception to gateway
  decision G4 (the CONNECT-frame token never reaches the gateway).
- **Scope:** `notification-service` — `JwtVerifier.verify()` now
  requires `exp` and exposes `jti` (parity with the other services);
  new `TokenDenylist` port + `security.redis.RedisTokenDenylist`
  adapter (`EXISTS denylist:<jti>`, fails open on `DataAccessException`,
  finite 2 s client timeouts); `JwtChannelInterceptor` rejects revoked
  jtis at CONNECT; ArchUnit rule confining Redis types; yaml/compose/
  .env.example/README wiring (no `depends_on: redis`, mirroring the
  gateway's stance). Tests: no-expiry/jti verifier cases, adapter unit
  tests, revoked/not-revoked/jti-less CONNECT cases against a fake
  denylist (the STOMP integration test stays Docker- and Redis-free).
  `web` — `@stomp/stompjs` added; `features/notifications/`
  (`NotificationProvider` layout route owning the app's single STOMP
  client: gateway `/ws/websocket` URL, bearer CONNECT header,
  exponential 1 s→30 s reconnect, resubscribe in onConnect, stop on
  STOMP ERROR; `useNotifications`; `stompUrl`); `NotificationBell`
  badge wired to the session count; test suite for URL derivation,
  connect config, badge counting, logout teardown and ERROR handling.
  Docs: D23 recorded in `docs/notification-service.md` (plus the stale
  "no API gateway" wording fixed and the client policy marked
  implemented); `docs/gateway.md` known limit retired + G4 exception
  noted; user-service `TokenDenylist` javadoc updated.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-12 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** review fixes (PR #185 Copilot review)
- **Prompt(s):** Asked to check and address the Copilot review's two
  findings.
- **Scope:** `web/src/shared/shell/NotificationBell.tsx` — the unread
  count joins the button's accessible name (`Notifications (N unread)`)
  since `aria-label` overrides the badge's descendant text for screen
  readers; the test suite asserts the name. `README.md` — the AI Use
  Summary extended for the #67 remaining scope, which the PR had missed
  (policy: the summary stays current).
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-13 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** debugging assistance, refactoring (PR #182 review by
  @Sinnez1)
- **Prompt(s):** Asked to address the PR #182 review comments — the
  blocking session-identity finding and the lower-priority notice-timing
  one. Both were defects with a stated fix direction, so no new design
  decisions were put to the author.
- **Scope:** (1) A late 401 for an old token was signing out a new
  session: `apiFetch` now hands the unauthorized handler the bearer
  token the refused request actually carried (read back off the request
  headers, so a caller-supplied Authorization counts the same), and
  `AuthProvider`'s guard compares it against the live session's token
  instead of merely checking that some session exists — a reply
  outlived by a fresh login is ignored. (2) The "session ended" notice
  could show at the wrong time: `sessionNotice` publishes the flag to
  listeners as well as holding it, and the login page subscribes, so a
  401 landing while the page is already open shows the notice then and
  consumes the flag — it can no longer surface on a later visit, e.g.
  after a plain manual logout. (3) `web/src/test/auth.test.tsx` — a case
  per finding (both verified to fail without the fix), and the flag is
  reset between tests since it is module state. Also merged `main`
  (PR #179), resolving the README and usage-log conflicts by keeping
  both sides in date order.
- **Author review:** web 104/104 (vitest), `tsc -b`, eslint, prettier
  clean on the touched files; the diff reviewed via the PR.

## 2026-10-14 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** debugging assistance, refactoring (PR #182 second review
  round by @Sinnez1)
- **Prompt(s):** Asked to resolve the PR #182 review comments. Eight of
  the nine findings were defects with a stated fix direction, so no
  decision was put to the author. The ninth asked a behaviour question
  — whether a forced sign-out should redirect a visitor off a public
  page, and whether to return them to the page afterwards — which was
  put to Leong Wei Zhi as neutral options and answered: clear the
  session in place on public pages (no redirect, no notice), and leave
  return-to-location alone, as no flow in the app has it today.
- **Scope:** (1) `AuthProvider` compares the refused token with what
  `localStorage` holds at call time instead of the token of the last
  committed render: the rendered value lags `login()`'s `setToken` by a
  render, and another tab can have stored a newer session under the
  shared key — the handler was clearing both. (2) The notice and the
  redirect are limited to the case where the user ends up at the login
  form (a route behind `ProtectedRoute`, which carries a new
  `handle.requiresSession` in `src/routes/index.tsx`, or `/login`
  itself); the handler no longer navigates at all, since clearing the
  token makes `ProtectedRoute` bounce a gated page on its own. (3) The
  layout effect unregisters the handler and the token source on
  unmount. (4) `web/src/lib/api/http.ts` — a handler that throws is
  logged rather than raised in place of the `ApiError` callers match
  on, and the bearer scheme is matched case-insensitively, as the
  gateway matches it. (5) `web/src/features/user/useLocalStorage.tsx` —
  `readStoredValue` added for (1); the hook is unchanged.
  (6) `web/src/test/auth.test.tsx` — a case per blocking finding, each
  verified to fail without its fix, and the double-wrapped `me` fixture
  the reviewer spotted is unwrapped; `web/src/test/http.test.ts` (new)
  — the two apiFetch contracts above. (7) File-header review notes
  split per entry, as the reviewer asked.
- **Not changed:** the login page still reads the notice flag in a
  `useState` initializer and clears it in an effect rather than in one
  read-and-clear call — StrictMode invokes an initializer twice, which
  would spend the flag on a call whose result React may discard. The
  reviewer left this one optional.
- **Author review:** web 113/113 (vitest), `tsc -b`, eslint, prettier
  clean on the touched files; the diff reviewed via the PR.

## 2026-10-14 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** review fixes + documentation (PR #185 review by @Sinnez1)
- **Prompt(s):** Asked to resolve the PR #185 review comments. The
  blocking finding offered two routes (implement the check or correct
  the docs); the review's own wording asked for the check, which is
  what the gateway already does for REST — implemented rather than put
  back to the author as a new decision, since G11 was already the
  author's.
- **Scope:** `origin/main` merged into the branch first (the branch's
  `docs/gateway.md` edits predated #179, so the merge keeps main's G11
  rows and request-flow note). (1) Reset cutoff at CONNECT —
  `JwtVerifier.VerifiedToken` exposes `iat`; `TokenDenylist` gains
  `isUserRevokedSince`; `RedisTokenDenylist` reads
  `denylist:user:<id>` and compares strictly, like `auth.lua`
  (unparseable stamp and Redis error both fail open);
  `JwtChannelInterceptor` runs both lookups in the gateway's order.
  (2) Docs: D23 extended to both key shapes, and the CONNECT-only
  timing recorded as an accepted limit in `docs/notification-service.md`
  (Session mechanics) with the matching entry in `docs/gateway.md`'s
  known limits — the reviewer's second finding. (3)
  `spring.data.redis.repositories.enabled: false` in both yamls, which
  stops the starter scanning the JPA repositories for a Redis store
  (the reviewer's startup-log nit). Tests: 5 adapter cases, 2 verifier
  cases, 3 CONNECT cases (pre-cutoff rejected, post-cutoff and
  iat-less accepted) — notification-service 53 run, the 2 pre-existing
  Java-26 AMQP context-restart errors unchanged.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-16 — Leong Wei Zhi
- **Tool:** Claude Code (Fable 5)
- **Mode:** review fixes (PR #185 re-review by @Sinnez1)
- **Prompt(s):** Asked to resolve the PR #185 re-review comments —
  two blocking STOMP auth gaps the reviewer demonstrated and six
  non-blocking items.
- **Scope:** (1) `JwtChannelInterceptor` — STOMP 1.2's `STOMP` frame
  is authenticated like `CONNECT` (it used to open an unauthenticated
  session), every client SEND is refused (a SEND to
  `/user/{id}/queue/notifications` was delivered to that user), and
  SUBSCRIBE is allowed only to `/user/queue/notifications`. Both gaps
  predate this PR but combined allowed tokenless spoofed
  notifications, so they were fixed where the PR already works.
  (2) Non-blocking: `RedisTokenDenylist` fails open on any
  `RuntimeException` (the port's contract), the Redis-outage cost
  comment corrected to ~4 s (two lookups, each with its own 2 s
  timeout), the no-jti gateway-parity comment reworded (this check is
  marginally stricter than `auth.lua`), the ERROR-frame comment
  softened in `NotificationProvider.tsx` and
  `docs/notification-service.md`, the unused `NotificationDto` export
  dropped until issue #66 reads the body, and the redundant
  `mockito-core` dependency removed. (3) Docs: Session mechanics
  gained a Frame policing bullet. Tests: raw-frame STOMP cases (with
  and without token), spoofed-SEND and stray-SUBSCRIBE rejection, two
  untranslated-exception fail-open adapter cases.
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-16 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** review response with options Q&A (PR #185 re-review)
- **Prompt(s):** Asked to rebase the branch onto `origin/main` and then
  address the PR #185 comments. The one open thread was @Sinnez1's
  blocking note that `onStompError` permanently deactivates the app's
  only STOMP client: with no token refresh in the SPA, a session that
  outlives the 1 h token TTL and then loses its socket reconnects with
  a dead token, gets an ERROR frame, and push stops silently behind a
  frozen badge. Exploration established that neither direction the
  reviewer suggested exists on this branch — notification-service has
  no REST controllers, so there is no unread count to re-fetch (issue
  #66 owns it), and `apiFetch` has no 401/expired-session path to route
  the ERROR frame through (PR #182 is adding one for #178). The author
  was given four options — sign out via `useAuth().logout()`, report it
  in the bell only, both, or bounded retry then report — with the
  trade-offs stated factually, and chose **bounded retry, then
  reported stop**.
- **Scope:** web — `NotificationProvider` retries a rejected CONNECT
  three times 30 s apart (the reconnect cap, so a dead token is not
  re-sent every second), resets the budget on a successful CONNECT, and
  then marks the session stopped instead of dying quietly; the stop is
  derived per token, so a re-login starts live, and an unset
  `VITE_NOTIFICATION_SERVICE_URL` derives the same state without a
  setState in the effect. `useNotifications` exposes `pushStopped`;
  `NotificationBell` greys the badge and appends "updates paused, sign
  in again" to its accessible name, keeping the count visible (those
  notifications did arrive) without presenting it as current. Tests:
  retry-then-reactivate, budget exhaustion with the bell's paused name,
  a reconnect earning a fresh budget, and the unset-URL case (web 111
  green; `tsc -b` and eslint clean — eslint's
  `react-hooks/set-state-in-effect` is what pushed the unset-URL case
  to a derived value). Docs: an ERROR-frame policy bullet in
  `docs/notification-service.md` Session mechanics, and the root README
  AI Use Summary extended for this round plus the frame-policing round
  its 2026-10-14 disclosure line claimed but never wrote into the
  clause. Also in this session: the branch rebased onto `origin/main`
  (the flattened merge put the pre-#179 commits against post-#189 main;
  `README.md`, `ai/usage-log.md`, `docs/gateway.md` and user-service's
  `TokenDenylist` javadoc resolved by keeping both sides, in date order
  where the file keeps one).
- **Author review:** Pending author review of the diff via the PR.

## 2026-10-16 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** repository maintenance (PR #185 main sync)
- **Prompt(s):** Asked to fix PR #185's merge conflict. GitHub reported
  the PR as conflicting after PR #187 (order-service scaffold) landed
  on `main`; no review comment was open and no decision was put to the
  author.
- **Scope:** No source change. The branch was rebased onto `main`
  (PR #187 landed) and the seven #67 commits replayed. Conflicts in
  `README.md` (the header disclosure log and the AI Use Summary),
  `.env.example` and `compose.yaml` (both disclosure headers) were
  resolved by keeping both sides — `main`'s order-service block in the
  position `main` appends it, then this branch's #67 entry — with no
  clause reworded and nothing removed; the AI Use Summary's paragraph
  break that the conflict seam swallowed was restored.
  `ai/usage-log.md` merged without a conflict. The replayed diff was
  diffed line-for-line against the pre-rebase diff to confirm it is
  unchanged, and `main`'s own lines in the four files confirmed intact.
  `main` since the old base changes no code outside `order-service/`,
  so nothing the branch changes overlaps it; the rest of its diff is
  the shared docs and compose/`.env` wiring resolved above. PR #190 (credit request event
  listener) merged while this was in flight, so the branch was rebased
  a second time the same day for a fresh `README.md` conflict: `main`'s
  #171/#190 header lines kept, the two order-service header lines left
  to `main`'s copy (its #190 merge had relocated them into date order,
  so re-adding this branch's copy would have duplicated them), and this
  branch's two #67 lines placed in their date positions, which is now
  how the upstream header reads. The replayed diff was again confirmed
  line-for-line identical to the pre-rebase diff.
- **Author review:** web 111/111 (vitest), `tsc -b`, eslint, and
  prettier clean on the touched files; notification-service
  `./mvnw test` 59 tests with the two known Java 26 AMQP
  context-restart errors that also fail on `main` locally
  (`IdempotentEventProcessorTest`, `RetentionPurgeSchedulerTest`).
  Author review of the resolution pending via the PR.

## 2026-10-16 — Leong Wei Zhi
- **Tool:** Claude Code (Opus 5)
- **Mode:** repository maintenance (PR #182 main sync)
- **Prompt(s):** Asked to address the PR #182 review comments and to
  rebase onto `origin/main` before pushing. The three open review
  rounds were checked against the branch first: every finding already
  has a fix in the code and a reply on the thread, so no code change
  was needed and no decision was put to the author.
- **Scope:** No source change. The branch was rebased onto `main`
  (PRs #187/#188/#189 landed), which dropped the earlier
  `main`-into-branch merge commit and replayed the four #178 commits.
  Conflicts in `README.md` (the header disclosure log and the AI Use
  Summary) and in `ai/usage-log.md` were resolved by keeping both
  sides in date order — `main`'s entries first, then this branch's —
  with no clause reworded and nothing removed; the two paragraph
  breaks the conflict seams swallowed were restored. The README header
  also gained the 2026-10-14 line the second review round had left out.
  Later the same day PRs #190 and #185 landed and the branch was
  rebased again, this time with a code conflict: `web/src/routes/index.tsx`
  — #185 had added a `NotificationProvider` layout route between
  `AuthProvider` and the shell, while this branch adds the
  `RouteHandle` type and the `handle` on the session-gated branch. The
  seam paired the wrong layers (main's `element: <AppShell />` against
  this branch's `Component: ProtectedRoute`), so it was resolved by
  hand: #185's layout route and import kept, `RouteHandle` kept, and
  `handle: { requiresSession: true } satisfies RouteHandle` placed on
  the nested `ProtectedRoute` that actually gates
  /suppliers, /profile and /admin. The `README.md` and
  `ai/usage-log.md` conflicts were resolved by keeping both sides in
  date order, with the order-service header lines left to `main`'s copy
  (its #190 merge had moved them into date order, so re-adding this
  branch's copy would have duplicated them). Checked afterwards: the
  branch's added and removed lines are identical to the pre-rebase set
  apart from the `handle` line's new indentation under the extra route
  layer, and both docs are additive against `main` (36 and 122 lines,
  zero deletions).
- **Author review:** web 125/125 (vitest — this branch's cases and
  #185's notification suite together), `tsc -b`, eslint and
  `vite build` clean; prettier clean on the touched files (the one
  warning, `src/features/user/jwt.ts`, is pre-existing on `main` and
  untouched here). The diff is reviewed via the PR.

## 2026-10-16 — Leong Wei Zhi (PR #180 main sync; recorded 2026-10-17)
- **Tool:** Claude Code (Opus 5); entry transcribed by Codex (GPT-6).
- **Mode:** repository maintenance + debugging (issue #94, PR #180).
- **Prompt(s):** Retrospective entry from PR #180's description and
  commit `0ba4212` file headers: sync the frontend half of #94 with
  main, including the merged #182 session handling and #185 provider.
  The original prompt text is not available in this session.
- **Scope:** Reconciled the route table so /forgot-password stays
  inside AppShell and /account-recovery stays inside ProtectedRoute's
  requiresSession branch under NotificationProvider. Preserved both
  sides of header conflicts in AuthProvider, login, and http. Both
  reset calls now pass auth: false; a successful reset clears the
  local session with endSession(), without calling the logout endpoint.
  Added password-reset regression tests and HTTP cases for empty/body
  202 responses and the auth opt-out. The original #94 screen generation
  is covered by the 2026-10-11 entries above (Claude Code, Fable 5).
- **Author review:** PR #180 reports 145 web tests, lint, TypeScript,
  build, touched-file formatting and browser reset/recovery walkthroughs
  passing at that commit. These historical results are transcribed,
  not newly executed by Codex. This retrospective disclosure is pending
  Leong Wei Zhi's review via PR #180.

## 2026-10-16 — Leong Wei Zhi (PR #183 main sync)
- **Tool:** Claude Code (Opus 5)
- **Mode:** repository maintenance (PR #183 main sync) + docs
- **Prompt(s):** Asked to bring PR #183 up to date with the merged
  PR #179, its base. The scope of that update was put to the author as
  neutral options: fix the claims #179 left stale and add the owed
  disclosures (chosen), conflicts only, or conflicts plus claims
  without the disclosures; likewise retargeting to `main` and marking
  the PR ready (chosen), and running the full user-service suite
  (chosen). No design decision was made by AI.
- **Scope:** The branch was rebased onto `main` (its base branch
  `feat/account-recovery-password-reset` merged as #179, and #182 and
  #185 landed since; #180 was still open at that point). Three files
  conflicted and all three were resolved by keeping both sides:
  `docs/gateway.md`'s header log (this branch's G12 line placed in date
  order among #185's two entries), `AdminService`'s header log (#179's
  approval follow-up entry, then this branch's), and
  `AdminServiceTest`'s header plus the remove-another-admin case, where
  #179's demotion assertion and this branch's eviction `verify` now both
  stand.
  One semantic change beyond the seams: #179's approval follow-up had
  written, in `AdminService.removeUser`'s comment and in
  `user-service/README.md`, that a token minted before a removal keeps
  ADMIN until its `exp` — true of `main`, false once this branch's
  stamp lands. Both copies now describe the revocation, with the
  demotion to USER kept and re-framed as the fallback for a failed
  best-effort write (G9) against a gateway failing open (G6).
  `user-service/README.md` also gains the stamp in its admin-endpoint
  list and in the token section's account of what this service writes
  to the denylist, and `docs/gateway.md` records the two facts #185's
  D23 content creates for G12: notification-service's CONNECT lookup
  reads the same stamp, so a new STOMP session is refused too, while
  an already-open session still is not (that accepted known limit now
  names a demote/remove among its triggers).
- **Author review:** user-service suite via Testcontainers after the
  rebase; the resolution and the doc corrections reviewed via PR #183.

## 2026-10-17 — Leong Wei Zhi (PR #180 review)
- **Tool:** Codex (GPT-6).
- **Mode:** debug + refactor + documentation (issue #94).
- **Prompt(s):** "Address pr comments on
  https://github.com/AY2627S1-CS3219-P23/foc/pull/180".
- **Scope:** Applied @Sinnez1's recovery hold to all routes through the
  shared AuthProvider; ProtectedRoute keeps the sign-in gate. Parsed
  202 response text so only an empty body resolves undefined and
  malformed JSON rejects. Moved the recovery request to profileApi
  and removed its duplicate imperative home navigation. Reused the
  existing CodeInput/emptyCode for six-digit reset entry and required
  the email field. Added public-route and Home-link recovery regression
  cases, malformed-202 cases using real Responses, and code-entry
  integration coverage; updated existing response mocks and code-box
  selectors. Added the retrospective main-sync entry above and the
  README summary for PR #180. The reset API supplies no timing data,
  so no cooldown/expiry values were invented or API changes made.
  Unconditional local sign-out after reset retains the existing PR
  behavior; the reviewer left changing it optional.
- **Author review:** 157/157 frontend tests pass; lint, TypeScript and
  production build pass. Prettier is clean on every touched source/test
  file, and git diff --check passes. The nine new public-route/Home-link
  and malformed-202 regression cases fail against the previous source
  implementation. Recovery tests were rerun after correcting a test
  query option caught by TypeScript. No live browser walkthrough was
  performed in this review round. Leong Wei Zhi to review via PR #180.

## 2026-10-17 — Leong Wei Zhi (PR #183 review)
- **Tool:** Claude Code (Opus 5)
- **Mode:** debug + docs (issue #178, PR #183)
- **Prompt(s):** "address mr comments on <PR #183> and rebase from
  origin/main also before pushing". The review's one code finding names
  two mechanisms, so both open choices were put to the author as
  neutral options: the mechanism (chosen: the `afterCommit` sketch
  credit-service already uses, over a
  `@TransactionalEventListener(AFTER_COMMIT)` event) and the scope
  (chosen: fix `PasswordResetService.confirmReset` too, which carries
  the identical pre-commit write from the merged #179, rather than only
  the two sites the review names). No design decision was made by AI;
  the options were factual.
- **Scope:** @Sinnez1's two non-blocking comments.
  - The reviewer reproduced the revocation stamp being written inside
    the caller's `@Transactional`, where the committed role is still
    `ADMIN`, so a login in the gap between the Redis write and the
    commit could mint an ADMIN token with an `iat` the stamp accepts.
    New `foc.user.service.AfterCommit` runs the write on
    `TransactionSynchronization.afterCommit`, or inline when no
    transaction is open; `AdminService.changeRole`, `removeUser` and
    `PasswordResetService.confirmReset` all stamp through it.
    `TokenDenylist`'s javadoc states the requirement, and
    `user-service/README.md` records it.
  - The 2026-10-16 PR #183 main-sync entry claimed #180 had landed; it
    was still open then, so the entry now says so. (#180 has merged
    since — this round's rebase is onto `main` at `668b538`, which
    includes it.)
  - Also in this round: the branch rebased onto `main` for #180's
    merge. `README.md` and this log conflicted and were resolved by
    keeping both sides, with this branch's #178 server-half summary
    paragraph kept next to the #182 web-half paragraph it refers back
    to and main's #180 paragraph left after it.
- **Author review:** user-service **362/362** via Testcontainers (the
  two new `AdminControllerTest` cases). All three new ordering
  assertions were confirmed to fail against the pre-fix code: each
  reads the target's row over a second JDBC connection — committed
  state only — at the moment the stamp is written, and before the fix
  saw the old role, a null `deleted_at` and the old password hash. The
  manual gateway walkthrough the PR description owes is still owed.
  Leong Wei Zhi to review via PR #183.

## 2026-10-17 — Leong Wei Zhi (PR #183 Codex review)
- **Tool:** Claude Code (Opus 5)
- **Mode:** docs + analysis (issue #178, PR #183)
- **Prompt(s):** "address latest pr comments", then — on the second
  finding — "is this even valid?" and, after the analysis below,
  "revert changes and reply to codex comments". The author's call on
  both findings; no design decision was made by AI.
- **Scope:** Codex's two P1 findings on `fb0f270`.
  - **Accepted:** `AfterCommit.run` had no `// AI-generated (edited by
    <name>)` block marker (AGENTS.md disclosure duty 2). Added in the
    form the repo already uses, matching
    `CreditOperationsService.afterCommit`, the sketch it follows.
  - **Declined:** the claim that an older post-commit cutoff can
    overwrite a newer one. Codex's mechanism does not hold — the cutoff
    is read from the clock as the first statement of
    `revokeUserSessions`, not captured at commit time and carried into
    the callback, so a delayed callback writes a *later* second, never
    the stale one in Codex's example. The genuine residue is narrower
    than stated: the writes are indeed no longer ordered by the row
    lock (confirmed in `AbstractPlatformTransactionManager.processCommit`,
    spring-tx 7.0.9 — `doCommit` precedes `triggerAfterCommit`), but an
    out-of-order *value* needs a ≥1 s stall inside the few instructions
    between the clock read and the Redis command, with the other
    request completing its whole transaction and stamp in that stall,
    plus a login in between to mint a token worth resurrecting. A
    monotonic Lua cutoff was drafted and then reverted on the author's
    call. Noted for whoever revisits it: the case that would actually
    bite is **clock skew between user-service replicas** (each stamps
    from its own `Clock.systemUTC()`), which needs no stall at all —
    latent today because compose runs one instance, and already a
    property of G11's `iat`-versus-stamp comparison rather than
    something this PR introduced.
- **Author review:** comment-only change to `AfterCommit`; the
  user-service suite is unchanged at 362/362 from the previous entry.
  Both Codex threads answered on the PR. Leong Wei Zhi to review via
  PR #183.

## 2026-10-17T11:17:00+08:00 — Leong Wei Zhi (issue #98)
- **Tool:** Codex (GPT-6.1 Sol).
- **Mode:** debugging + implementation + documentation.
- **Prompt(s):** "can i close https://github.com/AY2627S1-CS3219-P23/foc/issues/98";
  "what is meant by auto-skip docker?"; "create a plan to resolve this issue";
  "Implement the plan."; "create the pr";
  "wait are you sure you are opus 5? you are gpt 6.1 sol".
- **Scope:** Implemented the approved automatic Docker-unavailable skip plan
  in the shared user-service PostgreSQL test base: inherited
  `@Testcontainers(disabledWithoutDocker = true)` and a Docker availability
  guard around static startup. Preserved the singleton container and
  `@ServiceConnection`, without adding `@Container` or catching startup
  failures. Updated the service test instructions and consolidated README
  AI Use Summary. No production code, API, schema, dependency, or CI changes.
- **Verification:** Full user-service suite with Docker: 360 tests, zero
  failures/errors/skips, one PostgreSQL container start. With Docker socket
  access denied by the sandbox: 360 tests discovered, 157 unit tests executed,
  203 integration tests skipped, zero failures/errors. Surefire reports
  confirmed that exactly the 17 PostgreSQL test subclasses were skipped.
  The unmodified base failed in the same Docker-inaccessible environment
  with `ExceptionInInitializerError` from unconditional container startup.
  Both successful runs used `./mvnw -o test` with Mockito's existing 5.23.0
  jar passed as `-javaagent` through `-DargLine` to avoid this machine's
  Java 26 self-attachment restriction; no build configuration was changed.
  `git diff --check` passed.
- **Attribution correction:** Model label refined from GPT-6 to GPT-6.1 Sol
  using the model identification supplied by Leong Wei Zhi. Historical
  Claude/Opus attributions from earlier work remain unchanged.
- **Author review:** Leong Wei Zhi to review via pull request.

## 2026-10-17T11:33:22+08:00 — Leong Wei Zhi (PR #197 review, issue #98)
- **Tool:** Codex (GPT-6.1 Sol).
- **Mode:** debugging + implementation + documentation.
- **Prompt(s):** "address latest pr comments".
- **Scope:** Addressed both inline reviews about CI succeeding when Docker
  is unavailable. Added a user-service-only CI step after Maven verify to
  reject any skipped Surefire suites and missing reports. Local automatic
  skipping remains unchanged. Updated service instructions, workflow file
  attribution, and the consolidated AI Use Summary.
- **Verification:** Parsed the workflow YAML and executed its extracted
  shell step against actual Surefire reports. The previous Docker-available
  full run's reports (360 passed, zero skipped) passed the new check. A
  fresh sandboxed Docker-unavailable run passed locally (157 tests executed,
  203 skipped), then failed the new CI check with all 17 skipped integration
  classes named in the error. Missing reports also failed the check.
  The Maven rerun used the same existing Mockito jar as a Java agent for
  this machine's Java 26 restriction. No Java code changed in this review
  round; the Docker-available suite was not rerun. `git diff --check` passed.
- **Author review:** Leong Wei Zhi to review via PR #197.

## 2026-10-17 — Leong Wei Zhi (PR #197 comment cleanup)
- **Tool:** Codex (GPT-6.1 Sol).
- **Prompt(s):** "remove // AI-generated (edited by Codex; human review pending)."
- **Scope:** Removed the specified inline Java comment at the user's
  explicit request. File-header attribution remains accurate.
- **Verification:** Comment-only change; `git diff --check` passed.
- **Author review:** Leong Wei Zhi to review via PR #197.

## 2026-10-20 — Aung Ko Khant (order lists: active / finished state groups)
- **Tool:** Claude Code (Opus 5.5).
- **Scope:** order-service's `state` parameter on the request lists takes
  several states, comma-separated (`RequestsQueryService.parseStates`,
  `RequestSpecifications.statusIn`, two endpoint tests; the open list
  still takes only CREATED). In `web/`, My Requests lists only active
  requests (All active by default) and History only finished ones (All
  finished by default), each sent as one state list; `StateFilter` chips
  stand for a set of states; the mock, types, tests and `web/AGENTS.md`
  wireframe rows updated. The grouping and the comma-separated form were
  the author's decisions.
- **Verification:** web vitest (282 passed), order-service `mvn test` in a
  maven:3.9-eclipse-temurin-21 container (100 passed).
- **Author review:** Aung Ko Khant, pending pull request review.

## 2026-10-20 — Aung Ko Khant (My Requests PENDING polling)
- **Tool:** Claude Code (Opus 5.5).
- **Prompt(s):** "For order service I would like to refetch order list every
  2 seconds when an order is in pending state."
- **Scope:** `web/src/routes/my-requests.tsx` refetches the list 2 seconds
  after each response while any shown request is PENDING, and stops once
  none is; a test in `web/src/test/orders.test.tsx` covers the refetch and
  the stop. File headers updated.
- **Verification:** `vitest run src/test/orders.test.tsx` (23 passed),
  `tsc -b`, eslint and prettier clean.
- **Author review:** Aung Ko Khant, pending pull request review.

## 2026-10-20 — Aung Ko Khant (order-service: lost races answered 409)
- **Tool:** Claude Code (Opus 5.5).
- **Prompt(s):** "map errors to 409, say these request's state has changed?"
  (after asking how order-service handles two couriers accepting at once,
  and a cancel racing an accept).
- **Scope:** `RequestsController` answers an
  `OptimisticLockingFailureException` (a step that lost a race, caught by
  the `@Version` check) with 409 "This request's state has changed"
  instead of a 500. New `RequestsControllerConflictTests` (the mapping,
  service mocked); two `RequestsServiceTests` staging the real races —
  accept vs accept and accept vs cancel — where the loser is refused and
  leaves no state change or outbox event. The status code and message
  were the author's decision.
- **Verification:** order-service `mvn test` in a
  maven:3.9-eclipse-temurin-21 container (103 passed).
- **Author review:** Aung Ko Khant, pending pull request review.
## 2026-10-19 — Alastair Tan Choon Wei (docs: supplier bookmarks N8)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** docs
- **Scope:** `README.md` (team table, AI Use Summary),
  `docs/architecture.md` (scope paragraph, open decision),
  `docs/supplier-service.md` (N8 requirements, D9-D12, component, entity)
  and `docs/supplier-service.mmd` (bookmark API and table). Issue #226.
- **Prompt(s):** "help me add the new N2H feature to the required docs,
  in a separate branch." Only the author's decisions from #225 were
  transcribed (supplier-service owns the table; 409 on a sixth
  bookmark, cap enforced in frontend and backend; bookmark removed when
  its supplier is deleted; no gateway change). Endpoint paths, column
  names and the account-purge cleanup mechanism were not decided, so
  they are marked TBD.
- **Author review:** Alastair Tan Choon Wei to review before opening
  the PR.

## 2026-10-19 — Alastair Tan Choon Wei (docs: supplier bookmarks D13)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** docs
- **Scope:** `docs/supplier-service.md` (D13, N8.3/N8.4, browse/search
  component, bookmark entity), `docs/supplier-service.mmd` (browse/search
  label), `README.md` (AI Use Summary and header line). Issue #226.
- **Prompt(s):** "ill stick to option 1. help me update the relevant
  docs." The tool compared the two options and the changes option 1
  needs in `GET /suppliers`; the author chose option 1 and decided the
  ordering: bookmarked first by default, most recently bookmarked first,
  and no bookmark precedence once a filter such as nearest-to-me is
  applied. Only those decisions were transcribed.
- **Author review:** Alastair Tan Choon Wei to review before opening
  the PR.

## 2026-10-20 — Leong Wei Zhi (docs: D3 demo as a Postman collection)
- **Tool:** Claude Code (Fable 5)
- **Mode:** docs, generate
- **Scope:** `docs/postman/foc-d3-demo.postman_collection.json`,
  `docs/postman/foc-local.postman_environment.json`,
  `docs/postman/README.md` (new), `README.md` (AI Use Summary).
- **Prompt(s):** "create postman script for d3 demo, you can find it in
  github wiki." The tool transcribed the wiki D3-Demo-Script page into a
  Collection v2.1 with per-step assertions, taking bodies, paths, and
  status codes from the merged contracts (order-service README,
  docs/credit-service.md "Read API contract",
  docs/notification-service.md). Format (v2.1 JSON + newman), scope
  (all automatable steps; expiry and the RabbitMQ/live-push parts stay
  manual), and location (`docs/postman/`) chosen by the author via
  options Q&A. Mechanical deviations from the wiki page (polls for the
  sleeps, captured ids, delta balance checks, sequential accept pair,
  the real seeded account names, 2-credit cancel/drop requests so the
  run fits the 5-credit provision) are listed in `docs/postman/README.md`;
  the wiki page's own stale bits (nonexistent `demo_requester` accounts,
  3-credit steps 10–11 the balance cannot afford) were reported to the
  author for a separate wiki fix.
- **Author review:** Leong Wei Zhi verified the newman run against the
  compose stack and reviews via pull request.

## 2026-10-26 — Alastair Tan Choon Wei (feat: supplier bookmark table, issues #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** implementation
- **Scope:** `supplier-service` `SupplierBookmarks` entity,
  `SupplierBookmarkId`, `SupplierBookmarksRepository` (first step of N8:
  the table only; endpoints, purge endpoint and web follow in later
  entries), `README.md` (AI Use Summary).
- **Prompt(s):** "lets do it step by step, starting with DB first".
  Composite primary key (user_id, supplier_id), DB-level ON DELETE
  CASCADE on supplier_id, `Long` ids and the 5-bookmark cap were decided
  by the author; table and column names were left to the tool. Checked
  against the compose DB: the first run produced a duplicate supplier
  column (join column name), fixed; a `user_id` index was added because
  the primary key leads with `supplier_id`.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (feat: supplier bookmark flag and Create Request button, issues #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** implementation
- **Scope:** `web/` — `SupplierCard` (bookmark flag), `SupplierDetailPanel`
  (Create Request button), `routes/suppliers.tsx` (toggle, reload and cap
  logic), `routes/create-request.tsx` (`?supplierId=` prefill),
  `features/supplier/api.ts` and `types.ts` (bookmark calls and types),
  `test/supplier-bookmarks.test.tsx` (new), `README.md` (AI Use
  Summary). The backend endpoints these call are still to come.
- **Prompt(s):** "lets move on to next step of adding flag icon and create
  request button (frontend stuff)". Behaviour decided by the author:
  yellow/hollow flag on every card; flag click only toggles the bookmark;
  bookmarking in the default view jumps to page 1, un-bookmarking reloads
  the same page, a search/filter flips the flag in place; client-side cap
  plus the server's 409; Create Request for every user, full width, above
  Edit/Delete for admins, linking to the create-request form with
  `?supplierId=`. The form's route is `/my-requests/new`.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (feat: supplier bookmark endpoints, issues #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** implementation
- **Scope:** `supplier-service` — `BookmarkController`, `BookmarkService`,
  `BookmarkLock`, the bookmark DTOs and exceptions, the bookmark repository
  queries, the `bookmarked` flag and bookmarks-first default order in
  `SupplierService`/`SuppliersRepository`/`SupplierController`/
  `SupplierResponse`, the `SecurityConfig` rule for the user's own
  bookmark add/remove, and their tests (`BookmarkServiceTest`,
  `BookmarkControllerTest`, additions to `SupplierServiceTest`);
  `README.md` (AI Use Summary).
- **Prompt(s):** "lets move on to backend now". Decided by the author:
  `GET /bookmarks`, `PUT`/`DELETE /suppliers/{id}/bookmark`, the cap of 5
  answering 409 with the limit message, a simultaneous request from the
  same user refused with "You are bookmarking too fast, please try again."
  and nothing saved, bookmarks first (newest first) only in the default
  view. The tool chose how to guard the race: a transaction-scoped
  PostgreSQL advisory lock tried per user, instead of the serializable
  transaction the author first named, because serializable can also abort
  two different users' requests that share index pages; the author is to
  confirm this. **Update 2026-10-26 (PR #259 review):** the approach was
  suggested during implementation and reviewed and confirmed by the
  author/team as the accepted design (D14, `docs/supplier-service.md`).
  The purge endpoint (D12) is not in this entry.
  Checked on the compose Postgres: ordering, flag, the cap under 8
  simultaneous requests, the lock refusal, supplier-delete cascade.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (feat: star bookmarked suppliers in the create-request dropdown, issue #227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** implementation
- **Scope:** `web/` — `features/order/types.ts` and `api.ts` (the supplier
  option keeps the `bookmarked` flag), `routes/create-request.tsx` (leading
  ★ on bookmarked options), `test/supplier-bookmarks.test.tsx` (one test).
- **Prompt(s):** "B. Text prefix is good, help me add star". The author
  chose a text prefix over group headings, a suffix or a custom dropdown;
  the bookmarks-first order itself comes from supplier-service's default
  list order.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (feat: purge a deleted account's bookmarks, issues #225/#227, N8.5/D12)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** implementation
- **Scope:** `supplier-service` — `PurgeController` (`POST /purge/bookmarks`),
  `PurgeBookmarksRequest`/`Response`, `BookmarkService.purgeUsers`, the
  `/purge/**` SERVICE-role rule in `SecurityConfig`, and tests
  (`PurgeControllerTest`); `user-service` — `JwtIssuer.issueServiceToken`,
  `SupplierBookmarkCleanupClient`, `BookmarkCleanupException`, the call in
  `AccountPurgeScheduler`, `user.supplier.base-url` in `application.yaml`,
  and tests (`SupplierBookmarkCleanupClientTest`, additions to
  `AccountPurgeSchedulerTest` and `JwtIssuerTest`); `gateway/nginx.conf`
  (404 for `/api/supplier/purge`); `compose.yaml` (`SUPPLIER_SERVICE_URL`);
  `README.md` (AI Use Summary).
- **Prompt(s):** "ok implement the C+B purge". Decided by the author: a
  direct API call from user-service's purge job (D12), `/purge/bookmarks`,
  a SERVICE-role service token (option C) plus a gateway block (option B),
  bookmarks removed when the account is purged (day 31), not when it is
  soft-deleted, and the supplier call made before the accounts are
  deleted. The tool chose: POST with a JSON body (the author was asked and
  did not object), all-or-nothing skip of the account delete when the call
  fails (retried on the next daily run), 500-id batches, a 1-minute token,
  and the client timeouts. Checked on the compose stack: the gateway answers
  404 for the purge path in every spelling tried; with supplier-service
  stopped the condemned account was kept and the failure logged; once it was
  back the account and its bookmarks were removed while an in-window
  account's and another user's bookmarks were untouched. Test rows removed.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (docs: supplier bookmarks N8, issues #225-#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** docs, transcription
- **Scope:** `docs/supplier-service.md` (D12 decided, D13 default-order note,
  D14 added, Bookmark API section, component and entity rows),
  `docs/supplier-service.mmd` (internal purge API and the User Service
  call), `docs/architecture.md` (the open bookmark-cleanup decision moved to
  resolved), `docs/gateway.md` (the supplier route's purge exception),
  `web/AGENTS.md` (the suppliers and create-request wireframe rows),
  `README.md` (AI Use Summary).
- **Prompt(s):** "all is good, lets add to the docs now". Every decision
  recorded was made by the author in this session or read from the code as
  built; no rationale text was drafted, and the D13 default-order note
  describes what was built for the author to confirm.
- **Author review:** Alastair Tan Choon Wei to review before opening the PR.

## 2026-10-26 — Alastair Tan Choon Wei (fix: PR review of the supplier bookmarks PR, issues #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** fix
- **Scope:** `.env.bak-pre-pr217` removed from the index and `.env.bak*` added
  to `.gitignore` (the file was swept into the first commit by a stage-all
  and holds local secrets); `BookmarkService.add` (a supplier deleted
  between lookup and insert is a 404, not a 500) and its test;
  `AccountPurgeScheduler` and `UserRepository` (the supplier-service call
  now runs before the locking transaction opens, not inside it).
- **Prompt(s):** "need to fix?" then "FIX IT NOW", answering an automated
  review's comments. The two code changes follow the reviewer's suggestions;
  an earlier note that the held locks stopped an account being recovered in
  the gap was wrong (recovery already refuses accounts past the window), so
  the call no longer sits inside the transaction. Rotating the secrets in the
  committed backup and rewriting the branch history were left to the author.
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (fix: Copilot review on PR #259, issue #225)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** fix
- **Scope:** `SupplierController` gains a `NumberFormatException` handler
  returning 401 (copied from `BookmarkController`), so `GET /suppliers` with a
  non-numeric JWT subject no longer 500s; test added in
  `BookmarkControllerTest`.
- **Prompt(s):** "fix with option 1" after asking whether to fix a Copilot
  review comment. The handler duplicates `BookmarkController`'s rather than
  sharing a helper; that choice was the author's.
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (fix: Copilot review on PR #259, issue #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** fix
- **Scope:** `web/` — `SupplierCard.tsx` (the bookmark flag keeps one constant
  accessible label and conveys its state only through `aria-pressed`) and
  `test/supplier-bookmarks.test.tsx` (finds the flag by the constant label and
  asserts `aria-pressed` instead of the "Remove bookmark" label).
- **Prompt(s):** "fix it", answering Copilot's accessibility review comment.
  The change follows the reviewer's suggestion.
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (docs: Copilot review on PR #259, issue #225)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** docs
- **Scope:** `UserRepository.java` — dated entry added to the file header for
  `findIdsDeletedBefore`; the method's comment moved so the
  `deleteByDeletedBefore` comment sits above that method again (a comment
  edit only, no code change).
- **Prompt(s):** "need fix?" then the go-ahead, answering Copilot's review comment.
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (docs: Copilot review on PR #259, issue #225)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** docs
- **Scope:** `.env.example` — `SUPPLIER_SERVICE_URL` added to the User Service
  section with its compose default and a file-header entry, as the file's
  rule requires for every variable a service reads. The value matches
  `application.yaml` and `compose.yaml`.
- **Prompt(s):** "what is this? need fix?" then the go-ahead, answering
  Copilot's review comment.
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (fix: reviewer comment on PR #259, issues #225/#227)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** fix
- **Scope:** `web/` — `features/supplier/api.ts` and `types.ts` (`addBookmark`
  returns PUT's `BookmarkResult`), `routes/suppliers.tsx` (the bookmark count
  is taken from the server's `count` after an add and re-read after a remove,
  instead of a local +1/-1), `test/supplier-bookmarks.test.tsx` (add mock
  answers like PUT; two tests for the count).
- **Prompt(s):** "need fix?" answering Sinnez1's review comment. The change
  follows the reviewer's suggestion and leaves the DELETE endpoint as
  documented (the reviewer's note on a DELETE body is for the team).
- **Author review:** Alastair Tan Choon Wei to review before pushing.

---

## 2026-10-26 — Alastair Tan Choon Wei (fix: reviewer comment on PR #259, issue #225)
- **Tool:** Claude Code (Sonnet 5.5)
- **Mode:** fix
- **Scope:** `AccountPurgeScheduler` (the bookmark-cleanup step treats any
  `RuntimeException` as a failed cleanup and keeps the accounts, so an
  unexpected exception no longer skips the expired-code sweeps) and
  `AccountPurgeSchedulerTest` (a case where the client throws
  `IllegalStateException`).
- **Prompt(s):** "need fix?" answering Sinnez1's review comment. The change
  follows the reviewer's suggestion.
- **Author review:** Alastair Tan Choon Wei to review before pushing.
