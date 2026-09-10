# Friend on Campus (FoC) — Agent Guide

FoC is a peer-to-peer campus errand platform (CS3219 Team 23): students post
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
