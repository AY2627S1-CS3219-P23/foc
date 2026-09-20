<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-10; revised 2026-09-19. The
  2026-09-20 MCP server configuration entry was made with Claude Code
  (Opus 5); the other 2026-09-20 entries are Fable 5.
  Scope: team allocation table and repository-structure notes
  transcribed from team decisions (2026-09-10); the AI Use Summary
  section is maintained as the policy's consolidated disclosure.
  2026-09-19: foc-contracts structure note added per team decision D15
  (docs/notification-service.md).
  2026-09-20: AI Use Summary wording extended for the notification
  retry/DLQ implementation (issue #65), then again for the retention
  purge scheduler (issue #68), the ArchUnit broker-isolation test
  (issue #69), and the project MCP server configuration (developer
  tooling only); and again for the D2 design document
  (docs/d2-design.md, briefly the progress-check readiness doc
  before a same-day pivot); and again for the web/ SPA scaffold
  (issue #108).
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# CS3219 — Software Design and Architecture (AY2627 Sem 1)

## Favours on Campus (FoC)

**Favours on Campus (FoC)** is a peer-to-peer campus errand platform where
students can request items to be collected from stores or facilities on
campus, and other students can fulfil (and deliver) those requests. The
platform runs on a closed credit economy — credits cannot be bought,
withdrawn, or exchanged for money, and only circulate within the platform.

---

## Team Members

| Name | Service In-Charge | Nice-to-have |
| ----- | ----- | ----- |
| Kwey Xiu Xi | User Service | Cloud Deployment and DevOps |
| Alastair Tan Choon Wei | Supplier Service | — |
| Aung Ko Khant | Order Service | Centralized Logging |
| Ryan Ang Jun Wen | Credit Service | Order History |
| Leong Wei Zhi | Notification Service | Rating System |

---

## Repository Structure

This repository follows a **one-service-per-folder** structure: each
microservice (`user-service/`, `supplier-service/`, `order-service/`,
`credit-service/`, `notification-service/`) lives in its own top-level
folder. The frontend lives in `web/`.

```text
.
├── web/
├── user-service/
├── supplier-service/
├── order-service/
├── credit-service/
├── notification-service/
├── foc-contracts/
├── <n2h-service>/
└── README.md
```

- Any **nice-to-have (N2H)** feature that warrants its own service should
  be added as an **additional folder** at the same level, following the
  same per-service structure.
- `foc-contracts/` is a shared library (not a service) holding
  cross-service contract constants such as broker exchange names and
  the typed event contracts (event records + registry + fixtures) —
  team decision D15 (`docs/notification-service.md`).
- Files for agentic coding tools (e.g. agent configs, prompts, skills)
  may be added as needed, but must still **respect the
  one-service-per-folder skeleton** for core implementation.

---

## AI Use Summary

This section is the consolidated AI-use disclosure required by the CS3219
AI Usage Policy (project document, Appendix 2). The detailed log with
prompts and timestamps lives in [`ai/usage-log.md`](ai/usage-log.md).

**Tools:** Claude Code (Fable 5, Opus 5)

**Prohibited phases avoided:** requirements elicitation and
prioritization; architecture and design decisions. Requirements, service
boundaries, allocation, and the tech stack were decided by the team; AI
tools were used only afterwards.

**Used for:** transcribing the team-written D1 backlog into labelled
GitHub issues; repository documentation (README, AGENTS.md); project
scaffolding (service folder skeletons); drawing the high-level
architecture diagram (`docs/architecture.md`) from the team-decided
service boundaries and D1 requirements — open design decisions are
marked TBD in the diagram and remain with the team; implementation
scaffolding code (Spring Boot service skeletons, Docker/compose
wiring, JPA entities, message-broker topology declarations, the
shared `foc-contracts` library — contract constants and the typed
event records/registry with their canonical JSON fixtures and contract
tests) and the notification event pipeline (AMQP listener, idempotent
event processor, broker-native retry/dead-letter failure handling,
STOMP push gateway with JWT-authenticated sessions, retention purge
scheduler, an ArchUnit test enforcing the broker-isolation boundary,
unit and integration tests) written strictly from the team's
finalized design docs and issue specifications, with per-file
attribution headers; developer tooling configuration (the
project-scoped MCP servers declared in `.mcp.json`, documented in
`AGENTS.md`); and the D2 design document (`docs/d2-design.md`) —
status inventory and D1-backlog traceability compiled from the
repository and issue state, schema and endpoint content derived
mechanically from the requirements plus decisions the team made via
neutral-options Q&A, and the remaining open choices presented in
place as factual option comparisons, without tool-authored
recommendations or justifications; and the `web/` SPA scaffold
(issue #108) — Vite/React/TypeScript project skeleton, file-based
router with the shared app shell and 404, shared API/query utilities,
tooling configuration, and Docker/compose wiring, with the stack,
tooling, scope, and port choices made by the author via
neutral-options Q&A and recorded in `ai/usage-log.md`.

**Verification:** all AI-assisted output is reviewed by the team through
pull requests before merging.
