# Favours on Campus (FoC) — Agent Guide

Favours on Campus (FoC) is a peer-to-peer campus errand platform
(CS3219 Team 23): students post
item-delivery requests, other students fulfil them, settled via a closed
credit economy (credits cannot be bought or cashed out).

## Tech Stack

- **Frontend:** TypeScript + React
- **Backend:** Java + Spring Boot, with Spring Data JPA for database access
- **Deployment:** Docker per service (`Dockerfile` in each service folder),
  orchestrated via root `compose.yaml`

## Architecture

Microservices, **one service per top-level folder**. Each backend service is
an independent Spring Boot application:

| Folder | Responsibility |
| --- | --- |
| `user-service/` | Accounts, auth (tokens, OTP), roles (requester/courier/admin), profiles |
| `supplier-service/` | Vendor/landmark CRUD (admin), browsing, search and filtering |
| `order-service/` | Request creation, listing, acceptance, courier updates, completion, expiry |
| `credit-service/` | Credit balances, reservation, atomic transfer, transaction history |
| `notification-service/` | Order event updates, delivery retries, idempotent event processing |

Any nice-to-have feature that warrants its own service gets an additional
top-level folder following the same skeleton. A service folder may contain
its own `AGENTS.md` with service-specific instructions — read it before
working in that service.

## Conventions

- Keep changes scoped to the relevant service folder; cross-service
  communication happens via APIs/events, never by importing another
  service's code.
- Requirements are tracked as GitHub issues labelled by `service:`,
  `priority:`, and `sprint:` — reference the issue when implementing one.

## Workflow

- `main` is protected: no direct pushes, no force pushes. All changes go
  through a pull request with 1 approval.
- Branch naming: `feat/...`, `fix/...`, `docs/...`.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, ...).

## Course Constraints (from the CS3219 project document)

Mandatory scope (Mx labels):

- **M1** Responsive UI (desktop + mobile) with complete requester and
  courier workflows.
- **M2–M5** User, Supplier, Order, and Credit services are mandatory
  microservices (our Notification service is an approved addition).
- **M6** At least one *meaningful* asynchronous/event-driven workflow that
  serves a genuine product requirement — not a message broker for its own
  sake.
- **M7** Everything containerized (Docker); local deployment is the
  must-have, cloud is encouraged.
- Seed the Supplier service from the initial data in `data/` and extend it
  for a rich listing. Use meaningful users/suppliers for demo data.

Out of scope — do not build: payment to vendors, supplier menus/product
catalogs/inventory/checkout, live courier location tracking. Suppliers and
campus locations may be predefined. Document any additional assumptions.

Key deadlines: D2 progress check Week 7 (User/Supplier + supplier-listing
page demo), D3 Week 10 (Order/Credit, async workflow, containerization,
N2H plan), D4 final commit **Nov 11, 2026, 10:00** + Week 13 demo. Every
team member must contribute at least one nice-to-have feature's worth of
effort.

## AI Usage Policy (binding for all agents in this repo)

CS3219 imposes an AI usage policy (project document, Appendix 2). Agents
working in this repo MUST follow it:

**Allowed** (only after the team has finalized the relevant requirements
and architecture): writing implementation code and unit tests, boilerplate
and scaffolding, debugging assistance, refactoring, documentation, and
explanations.

**Prohibited — never do these, even if asked casually:**

- Requirements work: eliciting, prioritizing, or consolidating the backlog;
  sprint planning.
- Architecture & design: proposing or changing system architecture,
  component boundaries, design patterns, data schemas, or interfaces;
  making performance/security trade-offs.
- Drafting decision rationales, trade-off analyses, or justifications.

These decisions belong to the team; agents implement what the team has
decided. If a task requires such a decision, stop and ask the team member
to make it.

**Disclosure duties for every AI-assisted change:**

1. Add a file-header attribution comment to each AI-influenced file
   (tool + model, date, scope of assistance, author review note).
2. Mark pasted AI code blocks: `// AI-generated (edited by <name>)`.
3. Append an entry to `ai/usage-log.md` (timestamp, author, tool, prompt,
   scope of the change).
4. The README's final section is the consolidated AI Use Summary — keep it
   current.

Missing or misleading disclosure can cost the team grades or be treated as
an academic integrity violation; AI use in prohibited phases can zero the
project.
