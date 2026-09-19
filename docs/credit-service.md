# FoC Credit Service — Architecture Design

**Scope:** component-level design of `credit-service/`, covering
Credit Service requirements (F1 closed credit economy, F2 credit reservation, F3 credit transfer, F4 credit balance and history, F5 transfer atomicity, F6 redistribution, NFR1 responsiveness, NFR2 integrity), and the other requirements that it serves: rejecting request creation when the reward exceeds the available balance (Order F1.1.4) and viewing credit balance on the user profile (User Service F8.1). \
Companion diagram:
[`credit-service.mmd`](credit-service.mmd) \
High-level system architecture: [`architecture.md`](architecture.md)

## Design decisions
| # | Concern | Decision | Serves |
| --- | --- | --- | --- |
| D1 | Persistence | **PostgreSQL** accessed via Spring Data JPA and use of `@Transactional`| F5, NFR2.1 |
| D2 | Concurrency control | **Optimistic locking** using `@Version`| F2.1.2, NFR2.1 |
| D3 | Duplicate detection | Duplicates are identified by **unique event ID** | NFR2.2, NFR2.2.1 |
| D4 | Expiry model | Earned credits live in lots that expire 3 months after they are earned into a common pool. Lots are spent in first-in, first-out order.| F6.1–F6.1.2 |
| D5 | Credit history | **Append-only**: each row has type, amount, timestamp and request reference. Filters will use JPA. | F4.1.1, F4.1.2, F4.1.3 |
| D6 | Account provisioning | **User Service calls the Credit Service synchronously at sign-up** (as in `architecture.md`). Call is made idempotent by a unique user ID | F1.1, F1.1.1 F1.1.2 |
| D7 | Credit transfer and reservation | **Synchronous REST** | F2.1, F2.1.1, F3.1, NFR1.1 | Decided |
| D8 | Scheduler | Done by a **cron job** through use of `@Scheduled` | F6.1.1, F6.1.2 |

## Components

Ownership note: the Credit Service is a request/response
service. Its state lives only in its own PostgreSQL database (database-per-service). The only timer-driven work is the scheduler.

| Component | Responsibility |
| --- | --- |
| **Credit account REST API** (Spring Boot) | For the Web App: returns the caller's available and reserved balance (F4.1) and their transaction history with amount and date-range filters (F4.1.2, F4.1.3).|
| **Internal credit operations API** (Spring Boot) | For Order and User Service only: provision account with credits (F1.1), reserve (F2.1), release (F2.1.1), transfer (F3.1). |
| **Amount validation and error mapping** (Spring Boot, `@RestControllerAdvice`) | Rejects non-integer and non-positive amounts (F1.2, F1.2.1) and maps validation and domain failures (insufficient balance, unknown request, already released) to HTTP error responses. |
| **Idempotent credit operations service** (Spring Boot, `@Transactional`) | Core logic: provisions accounts with 5 available / 0 reserved, once per user (F1.1.1, F1.1.2). It reserves only if available credit >= amount needed (F2.1, F2.1.2, F2.1.3) and releases or transfers each request's held credits at most once (F2.1.1, F3.1.1, F3.1.2, NFR2.2.1). It spends the oldest credit lots first and commits balance change, held credits state and credit histories in one transaction (F5.1, NFR2.1) |
| **Credit history** (Spring Boot) | Appends one immutable row per transaction such as account provision, reservation, release, transfer, expiry, redistribution, with timestamps and request reference (F4.1.1). It will also serve the filtered history queries. (F4.1.2, F4.1.3). |
| **Expiry and redistribution scheduler** (Spring Boot, `@Scheduled`) | Move earned credits older than 3 months into the common pool (F6.1). At the start of each month, it shares the pool equally in integer amounts (F6.1.1) and gives the remainder to random users. (F6.1.2). |
| **Credit Database** (PostgreSQL) | Holds credit accounts, held credits records, credit lots, common pool and history. |

## Entities (fields required by the decisions above)

Each field exists because a decision or requirement demands it:

| Entity | Key fields | Why it must be present |
| --- | --- | --- |
| `CreditAccount` | `userId` (unique), `available`, `reserved`, `version` | (F4.1, F1.1.1–F1.1.2) Unique user ID allows for idempotent provisioning |
| `Reserved` | `requestRef` (unique), `requesterId`, `courierId` (set at transfer), `amount`, `status` (`HELD` / `TRANSFERRED` / `RELEASED`), lot slices the amount came from, `version` | One record per request makes duplicates and release-vs-transfer conflicts detectable (D8, NFR2.2.1); the lot slices let a release return credits to the lots they came from (D6) |
| `CreditLot` | `lotId`, `ownerId`, `remaining`, `earnedAt`, `expiresAt` (nullable) | FIFO spending and 3-month expiry (F6.1). Nullable expiry lets a non-expiring lot exist (eg. for sign up credits that should not expire) |
| Common pool | single system row: `balance` | Holds expired credits until redistribution (F6.1–F6.1.2) |
| `CreditHistoryEntry` | `id`, `userId`, `type`, `amount`, `requestRef` (null for non-request events), `timestamp` | F4.1.1 (record), F4.1.2–F4.1.3 (history and filters); a transfer writes one row per affected account |
| `RedistributionRun` | `period` (unique, e.g. `2026-10`), `distributed`, `remainder` | Monthly redistribution of credits |

## Diagram legend

Same notation as [`architecture.md`](architecture.md):

| Notation | Meaning |
| --- | --- |
| Rectangle | A software component (Spring Boot beans inside the service, or a neighbouring service) |
| Cylinder | The PostgreSQL database owned by the service |
| Thin arrow `-->` | Synchronous call (REST/JSON over HTTP, or a process call), caller --> callee |
| Plain line `---` | Database access via Spring Data JPA |
| `FR… / NFR…` on a line | The requirement the relationship implements |


**Glossary:** FIFO = first-in, first-out, JPA = Java Persistence API,  REST = HTTP/JSON web APIs, held = credits held for one request between reservation and
transfer or release.

## Requirement traceability

| Requirement | Satisfied by |
| --- | --- |
| Credit F1.1 - allocate 5 credits at sign-up | User Service provisions the account through the internal API |
| Credit F1.1.1 - reserved balance 0 at sign-up | Account is created with `reserved = 0` |
| Credit F1.1.2 - available balance 5 at sign-up | Account is created with `available = 5` |
| Credit F1.2 - credits spent only in integer amounts | Amounts are integers ≥ 1 and stored as integers |
| Credit F1.2.1 - reject non-integer reservation and transfer | Reserve rejects fractional values  |
| Credit F2.1 - reserve from available when a request is created | Internal API `reserve` moves the amount from `available` to held (`reserved`)|
| Credit F2.1.1 - release on cancellation or expiry | Internal API `release`: status change from `HELD` → `RELEASED`, amount returns to `available` (to the lots it came from) |
| Credit F2.1.2 - available never below zero | Balance check before reserving |
| Credit F2.1.3 - reject creation when available < reward | `reserve` fails with insufficient balance |
| Credit F3.1 - transfer reserved credits on completion | Internal API `transfer`: status change from `HELD` → `TRANSFERRED` |
| Credit F3.1.1 - deduct from requester's reserved balance | Requester's `reserved` decreases by the held credit amount |
| Credit F3.1.2 - add to courier's available balance | Courier's `available` increases by the same amount, as a new credit lot earned at transfer time |
| Credit F4.1 - view available and reserved balances | Credit account REST API returns both fields of the caller's account |
| Credit F4.1.1 - record reservation, release, transfer | Row per transaction with timestamp and request reference |
| Credit F4.1.2 - view transaction history | Credit account REST API that returns history query |
| Credit F4.1.3 - filter history by amount or date range | JPA Specification queries on the credit history |
| Credit F5.1 - atomic transfers | One atomic transaction for the whole transfer |
| Credit F5.1.1 - revert on partial failure such as a crash | Uncommitted changes are rolled back by PostgreSQL. The caller's retry re-runs the whole transfer |
| Credit F6.1 - expire earned credits after 3 months into a pool | Scheduler moves lots older than 3 months into the common pool |
| Credit F6.1.1 - redistribute equally, in integers, monthly | Scheduler gives each user `floor(pool / users)` at the start of each month |
| Credit F6.1.2 - redistribute the remainder randomly | Remainder (always fewer credits than users) goes 1 credit each to randomly chosen users |
| Credit NFR1.1 - balances updated within 5 s of delivery | Order Service calls `transfer` synchronously when delivery is confirmed |
| Credit NFR2.1 - revert to pre-transaction balance on transfer failure | Single transaction rolls back |
| Credit NFR2.1.1 - both balances match their pre-transaction values | Same rollback as NFR2.1 above |
| Credit NFR2.2 - prevent duplicate payments | Unique request reference on the record, checked in the same transaction |
| Credit NFR2.2.1 - identical transfer requests for one confirmation processed once | First request moves the status to `TRANSFERRED`. A repeat returns the recorded outcome and changes nothing |
| Order F1.1.4 - reject creation when reward > available | Same mechanism as Credit F2.1.3 |
| User F8.1 - credit balance on the own profile | Web App reads the balance from the Credit account REST API (F4.1) |

## Atomicity, idempotency and concurrency

1. The Order Service calls `transfer(requestRef, courierId)` when the
   requester confirms delivery (or if request is auto-completed).
2. In **one transaction**, the operations service loads the held credits by
   `requestRef`:
   - `TRANSFERRED` → return the recorded outcome; nothing changes.
   - `RELEASED` → reject; a released request cannot be paid.
   - `HELD` → reduce the requester's `reserved`, add the amount to the
     courier's `available` as a new lot, set the status to `TRANSFERRED`,
     append the credit history rows.
3. Commit the changes.

**Failure and retry behaviour:**

| Scenario | Outcome |
| --- | --- |
| Credit Service crashes mid-transfer | PostgreSQL discards the uncommitted transaction (F5.1.1, NFR2.1) |
| Order Service times out after Credit already committed, then retries | Same request reference so the original outcome is returned, with no second transfer (NFR2.2.1) |
| Requester double-clicks confirm and two identical transfers arrive together | Both load `HELD` so the `@Version` lets one commit while the other is retried, and sees `TRANSFERRED` and returns the recorded outcome |
| Two reservations by the same requester race past the balance | `@Version` conflict so one attempt is retried and rejected if balance is insufficient (F2.1.2) |
| Credit Service is down when delivery is confirmed | The Order Service must keep retrying with the same request reference |
| Scheduler runs twice for one month (due to restarts, or overlap) | A unique key prevents the second run from happening |

## Closed-economy

`Sum over all users (available + reserved) + common pool = number of credits ever created`

Credits are created only by the sign-up allocation (F1.1). The credit operation moves credits and destroys none: reserve (available → held), release (held → available), transfer (held → courier), expiry (lot → common pool), redistribution (pool → users).


## Open items

- **Account closure**: when a user account is deleted (User F3.1.3) or
  removed by an admin (User F6.1.3), the user's balance must move to the
  common pool, otherwise credits vanish. 
