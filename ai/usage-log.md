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
<<<<<<< HEAD
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

=======
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
>>>>>>> 9028a1bcf8978bc561d674aeb3cef8ba4fb23407
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
