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
  2026-10-19 (Claude Code, Sonnet 5.5), N8 (#225-#227): nice-to-have
  requirements, D9-D12, bookmark component and entity added from the
  decisions Alastair Tan Choon Wei recorded on #225; undecided
  details are marked TBD. Transcription only.
  2026-10-19 (Claude Code, Sonnet 5.5): D13 added, with N8.3/N8.4, the
  browse/search component and the bookmark entity aligned to it, from
  Alastair Tan Choon Wei's decision on how the list orders bookmarks.
  Transcription only.
  2026-10-26 (Claude Code, Sonnet 5.5), N8 (#225-#227): D12 recorded as
  decided and D14 added, the Bookmark API section and the bookmark
  entity's names filled in, and D13's default-order note added, from
  Alastair Tan Choon Wei's decisions and what was built. Transcription
  only; no rationale drafted - the team writes that.
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

### Nice-to-have N8 — Supplier bookmarks (issue #227)

| ID | Requirement | Priority | Sprint |
| --- | --- | --- | --- |
| N8.1 | Users can bookmark and un-bookmark suppliers as favourites, saved to their account so they persist across devices | Medium | Week 10 |
| N8.2 | At most 5 bookmarks per user, enforced in both the frontend and the backend; a sixth attempt is rejected with a message stating the limit of 5 has been reached | Medium | Week 10 |
| N8.3 | Bookmarked suppliers are shown first by default in the Suppliers tab, most recently bookmarked first; when any search or filter is applied (e.g. a search term, a category, nearest to me), the list follows that search or filter's order instead | Medium | Week 10 |
| N8.4 | Bookmarked suppliers are listed first in the pickup supplier dropdown on the create-request form, most recently bookmarked first | Medium | Week 10 |
| N8.5 | A bookmark is removed when its supplier is deleted, and all of a user's bookmarks are removed when their account is purged | Medium | Week 10 |

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
| D9 | Bookmark ownership | **Decided (2026-10-19, Alastair Tan Choon Wei):** the Supplier Service owns the bookmark table, so a supplier's deletion (D5) can remove its bookmarks in the same service. No gateway change: endpoints sit under the existing `/api/supplier/**` route. | **Decided** |
| D10 | Bookmark cap | **Decided (2026-10-19, Alastair Tan Choon Wei):** at most 5 per user, enforced in both frontend and backend. A sixth is rejected with `409` and a message stating the limit of 5 has been reached. | **Decided** |
| D11 | Supplier deletion | **Decided (2026-10-19, Alastair Tan Choon Wei):** deleting a supplier removes its bookmarks; affected users simply have one fewer. | **Decided** |
| D12 | Account-purge cleanup | **Decided (2026-10-26, Alastair Tan Choon Wei):** a direct API call. The User Service's account-purge job (the day-31 hard delete) calls `POST /purge/bookmarks` with `{"userIds":[…]}` on the Supplier Service, before it deletes the accounts, in batches of 500; so a soft-deleted account keeps its bookmarks through the 30-day recovery window and they go with the purge. The endpoint needs the `SERVICE` role: a one-minute JWT the User Service mints with the shared `JWT_SECRET` (login never issues that role), and the gateway answers `404` for `/api/supplier/purge` from outside (options C + B). If the call fails, that run skips deleting the accounts (all or nothing) and tries again on its next daily run. Batch size, token lifetime and the call timeouts (5 s connect, 15 s read) were set at implementation. | **Decided** |
| D13 | Bookmarks in the supplier list | **Decided (2026-10-19, Alastair Tan Choon Wei):** `GET /suppliers` returns a `bookmarked` flag on each supplier for the calling user (user id from the JWT), and the server orders the list before paging. By default, bookmarked suppliers come first, most recently bookmarked first, followed by the rest in the default order (name). When any search or filter is applied (e.g. a search term, a category, nearest to me), bookmarked suppliers get no precedence and the list follows that search or filter's order, so bookmarks-first holds only for the default, unfiltered view; the `bookmarked` flag is still returned. As built: name ascending (or no sort) is the default order; a descending name sort or any other sort the user picks gets no bookmark precedence. | **Decided** |
| D14 | Concurrent bookmark adds | **Decided (2026-10-26, Alastair Tan Choon Wei):** the implementation uses a PostgreSQL per-user advisory transaction lock to prevent simultaneous bookmark additions. This approach was suggested during implementation and reviewed and confirmed by the author/team as the accepted design. The lock failure returns a conflict response (`409`, "You are bookmarking too fast, please try again.") and does not persist the bookmark; it is not retried. It guards against parallel requests (e.g. scripts); the web client does not trigger it in normal use. | **Decided** |

## Components

| Component | Responsibility |
| --- | --- |
| **Browse/search REST API** | List (F1.2), details (F1.2.1), search by name (F2.1), filter by category (F2.2), empty-state (F2.2.1); also returns each supplier's `bookmarked` flag for the caller and applies the bookmarks-first default order (D13, N8.3-N8.4) |
| **Admin CRUD REST API** (JWT role-gated, D2) | Create/read/update/delete on suppliers (F1.1–F1.1.4) |
| **Internal validation/lookup API** | For Order Service: confirm a supplier exists (Order F1.1.1) and return its coordinates for distance checks (Order F8.1, D6) |
| **Bookmark REST API** (JWT, per-user; N8) | List, add and remove the caller's own bookmarks and enforce the cap (D10) and the parallel-add guard (D14). The caller's id comes from the verified JWT, never from the request. Endpoints in the Bookmark API section below |
| **Internal purge API** (`SERVICE` role only; N8.5) | Removes every bookmark of the accounts the User Service has purged (D12). Not a user endpoint: no login token can call it, and the gateway returns `404` for it |
| **Seed loader** (issue #102) | Parses the supplier seed CSV and populates the database on first startup |
| **Supplier Database** (PostgreSQL, D1) | Owns supplier and category data exclusively |

## Bookmark API (N8)

All endpoints take the caller from the verified JWT. Errors are problem+json.

| Endpoint | Who | Success | Errors |
| --- | --- | --- | --- |
| `GET /bookmarks` | any signed-in user | `200` `{count, limit, bookmarks:[{supplierId, name, bookmarkedAt}]}`, newest first; `limit` is 5 | `401` |
| `PUT /suppliers/{id}/bookmark` | any signed-in user | `200` `{supplierId, bookmarked, bookmarkedAt, count}`; adding an existing bookmark is a no-op that does not count against the cap | `404` unknown supplier; `409` limit reached ("You have reached the limit of 5 bookmarks.", D10); `409` parallel add ("You are bookmarking too fast, please try again.", D14) |
| `DELETE /suppliers/{id}/bookmark` | any signed-in user | `204`, also when there is no such bookmark | `401` |
| `POST /purge/bookmarks` | `SERVICE` role only (D12) | `200` `{deleted}`; body `{"userIds":[…]}` of up to 1000 ids; safe to repeat | `400` bad body; `403` any login token; `401` |

`GET /suppliers` also returns `bookmarked` on each supplier and orders bookmarks first in the default view (D13). The `PUT`/`DELETE` bookmark routes are open to any signed-in user, unlike the other writes under `/suppliers/**`, which are admin-only (D2).

## Entities

| Entity | Key fields | Grounded in |
| --- | --- | --- |
| `Supplier` | `id`, `name`, `location` (building/description), `latitude`, `longitude`, `openingTime`, `closingTime`, `description`, `status` (pending D5) | F1.1.1, F1.2.1, Order F8.1 |
| `SupplierBookmark` (N8) | Table `supplier_bookmarks`: `user_id` (no foreign key — users live in the User Service), `supplier_id` (foreign key to `suppliers` with `ON DELETE CASCADE`, D11), `bookmarked_at` (orders the list, D13). Primary key (`supplier_id`, `user_id`), so each pair is unique, plus an index on `user_id` for the per-user list and count. Names were left to the tool by the author | N8.1, N8.5 |
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
