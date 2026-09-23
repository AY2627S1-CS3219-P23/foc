<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude, 2026-09-21.
  Scope: Drafted from the team's D1 backlog — FR/NFR numbers, 
  wording and priorities below are transcribed directly from that document.
  Implementation-level choices are still pending for
  owner/team sign-off before being treated as final.
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
| F2.2 | Filter suppliers by category and by campus location/zone | High | Week 6 |
| F2.2.1 | Empty-state message when no supplier matches | Medium | Week 6 |
| NFR1 | Performance | High | Week 6 |
| NFR1.1 | Supplier listings retrieved within 5 seconds | High | Week 6 |
| NFR1.1.1 | Caching on frequently queried fields (e.g. supplier name, location) | Medium | Week 11 |
| NFR2 | Capacity | High | Week 6 |
| NFR2.1 | Store up to 100,000 suppliers | High | Week 6 |

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
| D4 | Zone modeling | Zone as a lookup table, not a hardcoded enum, for configurability | Proposal — not specified by the brief |
| D5 | Deletion | **Open question for the team:** F1.1.3 literally says "delete suppliers," which reads as hard delete. Hard-deleting a supplier referenced by past/active orders will break order history. Recommend the team explicitly decide between (a) implementing F1.1.3 as a genuine hard delete and accepting that order history loses supplier detail, or (b) implementing it as a soft delete (`status: ACTIVE/INACTIVE`) that satisfies "delete" from the admin's point of view while preserving referential integrity. | **Needs team decision — do not default silently** |
| D6 | Distance support | Supplier records store `latitude`/`longitude`; Supplier Service exposes an endpoint (or the raw coordinates) Order Service can use to compute the F8.1 distance check | Grounded in Order F8.1 |
| D7 | Indexing | Index on `name` (F2.1 search), `category`/`zone_code` (F2.2 filter) to keep NFR1.1's 5-second bound achievable at NFR2.1's 100,000-row scale | Grounded in NFR1.1, NFR2.1 |
| D8 | Seeding | Initial data load is an implementation task (tracked as issue #102 — CSV loader), not a numbered FR in the finalized brief | Confirmed: not in scope as an FR |

## Components

| Component | Responsibility |
| --- | --- |
| **Browse/search REST API** | List (F1.2), details (F1.2.1), search by name (F2.1), filter by category/zone (F2.2), empty-state (F2.2.1) |
| **Admin CRUD REST API** (JWT role-gated, D2) | Create/read/update/delete on suppliers (F1.1–F1.1.4) |
| **Internal validation/lookup API** | For Order Service: confirm a supplier exists (Order F1.1.1) and return its coordinates for distance checks (Order F8.1, D6) |
| **Seed loader** (issue #102) | Parses the supplier seed CSV and populates the database on first startup |
| **Supplier Database** (PostgreSQL, D1) | Owns supplier, category, and zone data exclusively |

## Entities

| Entity | Key fields | Grounded in |
| --- | --- | --- |
| `Supplier` | `id`, `name`, `location` (building/description), `latitude`, `longitude`, `openingTime`, `closingTime`, `description`, `status` (pending D5) | F1.1.1, F1.2.1, Order F8.1 |
| `SupplierCategory` (join) | `supplier_id` (FK), `category` | F1.1.1 ("categories"), F2.2 |
| `Zone` | `code` (PK), `name` | F2.2 |

## Diagram legend

Same notation as [`architecture.md`](architecture.md):

| Notation | Meaning |
| --- | --- |
| Rectangle | A software component (Spring Boot beans inside the service, or a neighbouring service) |
| Cylinder | The PostgreSQL database owned by the service |
| Thin arrow `-->` | Synchronous call (REST/JSON over HTTP), caller --> callee |
| Plain line `---` | Database access via Spring Data JPA |
| Label on a line | The requirement ID the relationship implements |
