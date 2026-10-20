<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-10-20, Leong Wei Zhi.
  Scope: the collection, environment, and this README transcribe the
  wiki's D3-Demo-Script page into runnable Postman requests, with
  bodies, paths, and status codes taken from the merged contracts
  (order-service README, docs/credit-service.md "Read API contract",
  docs/notification-service.md). Deviations from the wiki page are
  mechanical and listed below; no design decisions by the tool. The
  environment JSON cannot carry a comment, so this header covers all
  three files (the collection also repeats it in its description).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# D3 demo as a Postman collection

Runs the [wiki's D3 Demo Script](https://github.com/AY2627S1-CS3219-P23/foc/wiki/D3-Demo-Script)
— Order + Credit + the async pipeline, backend-only, through the gateway —
as one Postman collection where every step asserts its own status codes
and bodies.

## Prerequisites

Same as the wiki script:

```bash
# .env set (JWT_SECRET etc.), and USER_SEED_DEMO=true so the demo
# accounts exist (demo_user_001..100, demo_admin_1..3 — all pre-verified)
docker compose up -d --build
docker compose stop web        # backends keep running; the UI is not needed
```

## Run it

Scripted (no Postman account needed):

```bash
npx newman run docs/postman/foc-d3-demo.postman_collection.json \
  -e docs/postman/foc-local.postman_environment.json
```

Or in the Postman app: import both JSON files, select the
**FoC local (gateway :8090)** environment, and run the **FoC D3 Demo**
collection with the Collection Runner (keep the default top-to-bottom
order — later steps use ids and tokens captured by earlier ones, and the
saga polls jump back to themselves via `setNextRequest`, which only
works in a runner, not when clicking requests one by one).

## What it covers

Folders mirror the wiki's step numbers: 0 gateway-only access,
1 provisioning, 2 create → saga reserve, 3 validation 400,
4 async insufficient-balance rejection, 5 self-accept 409,
6 single-winner accept, 7 404 masking vs admin read, 8 happy path with
the credit transfer/expiries/history, 9 duplicate completion 409,
10 cancel + release, 11 courier drop (reward stays held),
12 notification feed + unread count, 14 role-gated admin views.
Step 13 (expiry) and the manual halves of step 12 (live WS push,
RabbitMQ dedupe/DLQ) stay manual; the folder descriptions say what to
show.

Deviations from the wiki page, all mechanical:

- The wiki's `sleep 2` waits are polls (up to 15 × 1 s) on the request
  status or balance, so slow saga round-trips don't flake the run.
- Request ids are captured from the `"Request <id> created"` replies
  instead of assuming a clean stack's 1/3/4.
- Balance assertions are deltas against the step 1 baseline.
- Step 0's direct-backend check stays a terminal curl (noted in the
  folder description): a refused connection cannot be asserted in a
  script without newman counting it as a run error.
- The parallel-accept race (step 6) runs as two sequential accepts —
  first 200, second 409 — since a collection run is single-threaded;
  the true `& wait` race stays a wiki/terminal demo.
- The wiki logs in as `demo_requester`/`demo_courier`/`demo_admin`;
  the actual seeded accounts are `demo_user_001`, `demo_user_002`, and
  `demo_admin_1` (see `user-service/.../seed/DemoAccountsSeeder.java`).
- The cancel and drop steps use 2-credit requests: after step 8
  transfers 3 to the courier, only 2 of the provisioned 5 remain
  available, so the wiki's "create as in step 2" (3 credits) could not
  reserve.

## Re-running

A full run spends the requester down to 0 available (3 transferred,
2 still held by the dropped request D). To run again on the same stack,
point `requesterUser`/`courierUser` at a fresh seeded pair
(`demo_user_003`/`demo_user_004`, …) — or `docker compose down -v` for a
clean stack. The seeded passwords in the environment file are the
committed constants from `DemoAccountsSeeder.java`, not secrets.
