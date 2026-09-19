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
