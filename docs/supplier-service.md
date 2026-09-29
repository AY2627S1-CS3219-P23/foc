<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude, 2026-09-21; revised 2026-09-27 (Claude Code, Sonnet 5).
  Scope: Drafted from the team's D1 backlog — FR/NFR numbers, 
  wording and priorities below are transcribed directly from that document.
  Implementation-level choices are still pending for
  owner/team sign-off before being treated as final.
  2026-09-27: team decision to drop the zone feature — F2.2's wording
  amended to drop "campus location/zone" (diverges from the original D1
  backlog transcription as a result), D4 marked retired, and the `Zone`
  entity/zone references removed from D7, Components, and Entities
  below (PR #134 Copilot review flagged the code/doc mismatch after the
  feature was dropped in code without updating this doc).
  2026-09-27: team decision to reduce NFR2.1's capacity target from
  100,000 to 1,000 suppliers (diverges from the original D1 backlog
  transcription). D7's justification rewritten accordingly — at 1,000
  rows a plain sequential scan for the `LIKE '%...%'` search is fast
  enough on its own, so the trigram/GIN indexing a 100,000-row target
  would have required is no longer warranted.
  Reviewed by: XXX
-->

# FoC Supplier Service — Architecture Design

**Scope:** component-level design of `supplier-service/`, covering the
finalized Supplier Service requirements (F1 supplier management, F2
search and filtering, NFR1 performance, NFR2 capacity), and the
requirements it serves for other services: pickup-location validation
(Order F1.1.1) and supplier-distance lookups for the courier
proximity rule (Order F8.1). \
Companion diagram: [`supplier-service.mmd`](supplier-service.mmd) \
High-level system architecture: [`architecture.md`](architecture.md)

## Requirements this service owns

| ID | Requirement | Priority | Sprint |
| --- | --- | --- | --- |
| F1 | Support supplier management | Very High | Week 5 |
| F1.1 | CRUD operations on locations/suppliers **by admin users** | Very High | Week 6 |
| F1.1.1 | Create a supplier with name, location, **categories**, opening times, description | Very High | Week 6 |
| F1.1.2 | Update existing supplier details | Very High | Week 6 |
| F1.1.3 | Delete suppliers | Very High | Week 6 |
| F1.1.4 | View all suppliers | Very High | Week 5 |
| F1.2 | List suppliers with name and category, for browsing | Very High | Week 5 |
| F1.2.1 | Show supplier details (name, location, opening time, etc.) on selection | Very High | Week 6 |
| F2 | Support supplier search and filtering | High | Week 6 |
| F2.1 | Search suppliers by name | High | Week 6 |
| F2.2 | Filter suppliers by category | High | Week 6 |
| F2.2.1 | Empty-state message when no supplier matches | Medium | Week 6 |
| NFR1 | Performance | High | Week 6 |
| NFR1.1 | Supplier listings retrieved within 5 seconds | High | Week 6 |
| NFR1.1.1 | Caching on frequently queried fields (e.g. supplier name, location) | Medium | Week 11 |
| NFR2 | Capacity | High | Week 6 |
| NFR2.1 | Store up to 1,000 suppliers | High | Week 6 |

## Requirements from other services this service serves

| ID | Requirement | Owning service |
| --- | --- | --- |
| Order F1.1.1 | Pickup location must be a supplier/location from the Supplier service | Order Service |
| Order F8.1 | A courier cannot hold two requests whose suppliers are more than 1 km (straight-line) apart | Order Service |

`Order F8.1` is why supplier records need real, precise latitude/longitude — it's not just a display detail, it's the input to a distance calculation Order Service depends on.

## Design decisions

| # | Concern | Proposal | Status |
| --- | --- | --- | --- |
| D1 | Persistence | PostgreSQL via Spring Data JPA | Proposal — engine not specified by the brief |
| D2 | Authorization | CRUD endpoints admin-gated via JWT role claim; browse/search open to any authenticated user | Grounded directly in F1.1 ("by admin users") |
| D3 | Categories | Many-to-many: a `supplier_categories` join table, not a single field, since F1.1.1 says "categories" (plural) | Grounded in F1.1.1 wording |
| D4 | Zone modeling | ~~Zone as a lookup table, not a hardcoded enum, for configurability~~ | **Removed (2026-09-27):** team dropped the zone feature |
| D5 | Deletion | **Team decision (2026-09-29):** hard delete — `DELETE /suppliers/{id}` removes the row. F1.1.3's "delete suppliers" is implemented literally; the team accepted that a supplier referenced by past/active orders loses its detail in order history as a consequence, rather than adding a `status: ACTIVE/INACTIVE` soft-delete column. | **Decided** |
| D6 | Distance support | Supplier records store `latitude`/`longitude`; Supplier Service exposes an endpoint (or the raw coordinates) Order Service can use to compute the F8.1 distance check | Grounded in Order F8.1 |
| D7 | Indexing | Not required at NFR2.1's revised 1,000-supplier target — a sequential scan comfortably clears NFR1.1's 5-second bound at that size, including for the search endpoint's leading-wildcard `LIKE '%...%'` query, which a standard btree index can't accelerate regardless (would need a trigram/GIN index, only worth the added complexity at a much larger row count). Revisit if NFR2.1 is raised again. | Revised 2026-09-27 (was: index on `name`/`category` to hit the original 100,000-row NFR2.1 target) |
| D8 | Seeding | Initial data load is an implementation task (tracked as issue #102 — CSV loader), not a numbered FR in the finalized brief | Confirmed: not in scope as an FR |

## Components

| Component | Responsibility |
| --- | --- |
| **Browse/search REST API** | List (F1.2), details (F1.2.1), search by name (F2.1), filter by category (F2.2), empty-state (F2.2.1) |
| **Admin CRUD REST API** (JWT role-gated, D2) | Create/read/update/delete on suppliers (F1.1–F1.1.4) |
| **Internal validation/lookup API** | For Order Service: confirm a supplier exists (Order F1.1.1) and return its coordinates for distance checks (Order F8.1, D6) |
| **Seed loader** (issue #102) | Parses the supplier seed CSV and populates the database on first startup |
| **Supplier Database** (PostgreSQL, D1) | Owns supplier and category data exclusively |

## Entities

| Entity | Key fields | Grounded in |
| --- | --- | --- |
| `Supplier` | `id`, `name`, `location` (building/description), `latitude`, `longitude`, `openingTime`, `closingTime`, `description`, `status` (pending D5) | F1.1.1, F1.2.1, Order F8.1 |
| `SupplierCategory` (join) | `supplier_id` (FK), `category` | F1.1.1 ("categories"), F2.2 |

## Diagram legend

Same notation as [`architecture.md`](architecture.md):

| Notation | Meaning |
| --- | --- |
| Rectangle | A software component (Spring Boot beans inside the service, or a neighbouring service) |
| Cylinder | The PostgreSQL database owned by the service |
| Thin arrow `-->` | Synchronous call (REST/JSON over HTTP), caller --> callee |
| Plain line `---` | Database access via Spring Data JPA |
| Label on a line | The requirement ID the relationship implements |
