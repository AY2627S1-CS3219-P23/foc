<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
  Scope: service agent guide written while building chat-service.
  Author review: Ko-Khan, pending pull request review.
-->

# chat-service — Agent Guide

Read the root `AGENTS.md` first (architecture, conventions, AI usage
policy). The team's decisions for this service are C1–C15 in
[`docs/chat-service.md`](../docs/chat-service.md); implement against
them, and ask the owner (Ko-Khan) before changing any of them.

- Build and test with this folder's wrapper: `./mvnw test` (Docker must
  be running for the database tests).
- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate
  only validates it. Add a new migration, never edit an applied one.
- Who may read and send is decided in `ChatService` only; the REST
  controller and the STOMP interceptor ask it.
- Pushes go out after the transaction commits (`ChatPublisher`), so a
  frame never announces a rolled-back message.
- `src/test/resources/application.yaml` replaces the main one in tests:
  a new `chat.*` property must be added to both.
