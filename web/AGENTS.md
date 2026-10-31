<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Sonnet 5), 2026-09-27.
  Scope: updated the suppliers.png wireframe description — team
  decision to drop the zone feature means the image (still showing a
  campus-zone filter) no longer matches what's being built; noted as
  stale/directional rather than edited, since the underlying PNG itself
  wasn't changed.
  2026-09-30, Claude Code (Opus 5), issue #109 (PR #150): the signup.png
  row records where the shipped OTP dialog goes beyond the wireframe (the
  resend cooldown label and the expiry line), same convention as the
  suppliers.png note above.
  2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): the profile.png
  row records the two deviations #92's shipped contract (PR #157) forces
  on the profile page — no Current Password field, and a second code step
  for a new email address. Both were the author's call via options Q&A.
  Author review: Leong Wei Zhi to review via PR #150 — which also closes
  the pending marker the 2026-09-27 entry above was left with.
  2026-10-17, Claude Code (Opus 5.5), issue #174: the credit-history.png
  row records where the credits page departs from the wireframe; the
  deviations are team decisions via options Q&A.
  2026-10-17, Claude Code (Opus 5.5), issue #196: the same row notes the
  upcoming-expiry line and notice, which the wireframe doesn't draw
  (team decisions).
  Author review: Ryan Ang, pending pull request review.
  2026-10-17, Claude Code (Fable 5), issue #66: the
  notification-center.png row records the shipped Center and its three
  beyond-the-wireframe controls (author decisions via options Q&A).
  Author review: Leong Wei Zhi to review via PR #199.
  2026-10-18, Claude Code (Opus 5.5), Aung Ko Khant: the my-requests,
  open-requests, order-history and dispute-flow rows record where the
  order pages depart from the wireframes now that they run on
  order-service's real API (adapt-to-the-backend, and the filter /
  username choices, are the author's decisions via options Q&A).
  Author review: Aung Ko Khant, pending pull request review.
  2026-10-18, Claude Code (Fable 5), notification sound (follow-up to
  issue #66, no issue yet): the notification-center.png row gains the
  header's sound toggle and the profile.png row the Preferences card
  with the same switch — neither is drawn; both surfaces were the
  author's call via options Q&A.
  Author review: Leong Wei Zhi to review via the PR.
  2026-10-19, Claude Code (Fable 5), issue #221: the
  notification-center.png row notes that courier names now hydrate
  client-side (D32, read-time lookup) — a row can render actor-less for
  a beat and then name its courier in place (title and aria-label
  update together). Author decision via options Q&A.
  Author review: Leong Wei Zhi to review via the PR.
  2026-10-26, Claude Code (Sonnet 5.5), issues #225/#227: the suppliers.png
  and create-request.png rows record the bookmark flag, the Create Request
  button, the dropdown's star and the supplier prefill (author decisions via
  options Q&A).
  Author review: Alastair Tan Choon Wei to review via the PR.
  2026-10-27, Claude Code (Opus 5.5), issue #242: the admin-dashboard.png
  row records the Stuck Orders and Failed Orders sections, which the
  wireframe doesn't draw (team decisions).
  Author review: Ryan Ang, pending pull request review.
  2026-10-27, Claude Code (Fable 5), issue #232: the
  notification-center.png row records that clicking a request row now
  opens the request at the delivery-detail page (unread rows mark read
  on the way; request-less rows keep the mark-read-only click) — author
  decisions via options Q&A.
  Author review: Leong Wei Zhi to review via the PR.
  2026-10-29, Claude Code (Opus 5.5), issue #241: the my-requests,
  admin-dashboard and dispute-flow rows record the report-a-problem
  dialog, the confirm-delivery dialog, the auto-complete countdown and the
  admin Disputes section (team decisions, docs/disputes.md); the
  order-history row records resolved requests and their amount (team
  decision).
  Author review: Ryan Ang, pending pull request review.
-->

# web/ — Frontend Agent Guide

TypeScript + React frontend for Favours on Campus (FoC). Read the root
`AGENTS.md` first for the overall architecture, course constraints, and
AI usage policy.

## Design Reference

Wireframes (Figma):
https://www.figma.com/design/HZ25RDhbcsBycXG7K7xrFf/Backlog-Mockup?m=auto&t=jQwFXEoz5ucD66x4-6

This is an **initial low-fidelity wireframe as of Milestone D1** — it
defines the intended screen organization and workflows, not final visual
design. Expect it to be revised as the project progresses; treat it as
directional, and confirm with the team before building anything that
deviates from it.

Agents can access the Figma file through the **Figma remote MCP server**,
configured repo-wide in the root `.mcp.json`. Each person authenticates
once via `/mcp` → figma → Allow Access (OAuth, own Figma account).
Note: on Figma's free tier the server is rate-limited to roughly 20 tool
calls per month, so use it sparingly — prefer any exported wireframe
images in the repo when they exist, and reserve MCP calls for details the
exports don't show.

Snapshots of the core screens are committed in `docs/wireframes/` — each
PNG shows the desktop and mobile variants side by side. **Look at these
first**; they cost nothing to read, unlike Figma MCP calls:

| File | Screen |
| --- | --- |
| `docs/wireframes/signup.png` | Sign Up — NUS email, username, password (live strength checklist), OTP verification step (built in PR #150: the six code boxes and "Resend code" are as drawn; the dialog adds a cooldown label on that link and one line stating when the code expires, neither of which the wireframe shows — the API refuses a resend inside `OTP_RESEND_COOLDOWN`, so the button has to say why it is disabled) |
| `docs/wireframes/login.png` | Login — username/email + password |
| `docs/wireframes/dashboard.png` | Dashboard — credit balance, requester/courier mode toggle, quick actions, active requests with status chips, recent activity |
| `docs/wireframes/suppliers.png` | Suppliers — search and category filters, supplier detail panel, empty state (wireframe image still shows a campus-zone filter; team dropped the zone feature, see `docs/supplier-service.md` D4 — the image is directional, not a spec, per this file's own note above). Beyond the wireframe (issues #225/#227): a bookmark flag on every supplier card (yellow = bookmarked, hollow = not; clicking it only toggles the bookmark), and a black "Create Request" button at the top of the detail panel — full width, above Edit and Delete for admins — that opens the create-request form with that supplier preselected (`?supplierId=`) |
| `docs/wireframes/profile.png` | Profile — account details, edit flows, delete-account modal (built in PR #149; two deviations the shipped #92 endpoints force, same convention as the signup.png note above: **Change Password has no Current Password field** — `POST /users/me/password` takes `{newPassword, confirmPassword, otp}` and the code emailed to the current address *is* the authentication, so the card ends in a code step instead; and a **second code step** the wireframe doesn't draw confirms a new email address from that address before it replaces the old one, F2.1.3; plus a **Preferences card** with the notification-sound switch — the same device-wide preference as the Center header's toggle, not drawn in the wireframe, author decision via options Q&A) |
| `docs/wireframes/credit-history.png` | Credit history — balances and transaction list (built for issue #174 at `/credits`, reached from the top-bar credits badge and the profile card rather than a nav item; three deviations the read API forces or the team chose, same convention as the signup.png note above: the **"Total lifetime earned" card shows Total** (available + reserved) and there is **no running-balance column**, since `GET /credits/me` and the history rows carry neither; and the **"Amount: All" dropdown is two fields, minimum and maximum**, mapping straight to the API's `minAmount`/`maxAmount`. Not drawn, added for issue #196: the **Available card says when the soonest credits expire** ("5 credits expire by 12 Jan 2027", Singapore dates) and a **notice above the transactions** warns when they expire within 7 days; neither appears on the badge or the profile card) |
| `docs/wireframes/order-history.png` | Order history — past/current orders (built at `/history` on order-service's `GET /requests/mine`; holds only **finished** requests (completed, cancelled, expired, rejected) with a **state filter** the wireframe doesn't draw — All finished by default, one backend query per chip — and rows show the **creation date**, since the API has no completion or cancellation time. Issue #241: **Resolved** is a finished state on both sides; its amount is the courier's share — − for the requester, + for the courier, 0 for a full refund — with a "Resolved: refunded / paid / split" label under it) |
| `docs/wireframes/create-request.png` | Create request — new errand form. Beyond the wireframe (issue #227): the pickup supplier dropdown lists bookmarked suppliers first, each with a leading ★, and `?supplierId=` preselects one |
| `docs/wireframes/my-requests.png` | My requests — requester's view of own requests (built at `/my-requests`; lists only **active** requests — finished ones are in History — with a **state filter** (All active by default) and the state the wireframe doesn't draw, **Reserving credits** (PENDING); there is **no Re-release** button — order-service has none; the courier's name comes from user-service's public profile. Issue #241: **Disputed** is a state and a chip here too; an ARRIVED card **counts down to order-service's `autoCompleteAt`** and offers **Report a problem** until then, then says "Completing automatically…" until the sweep runs; a COLLECTED card past its deadline offers Report a problem as well; **Confirm delivery asks first** in a dialog that states the reward paid, says it can't be undone and links to Report a problem) |
| `docs/wireframes/open-requests.png` | Open requests — courier's browse/accept view (built at `/requests`, nearest deadline first; the API's list items carry no requester, so there is **no "Requested by" line and no own-request state** on the card — order-service refuses a self-accept with a 409, shown as a banner; the "Too far" state is not built) |
| `docs/wireframes/active-delivery.png` | Active delivery — courier's in-progress order view |
| `docs/wireframes/admin-dashboard.png` | Admin dashboard — user/vendor management (not drawn, added for issue #242 between the Common Pool box and the Users section: **Stuck Orders**, finished orders whose credits credit-service still holds, with a "Held longer than" preset and a **Retry settlement** button per order; and **Failed Orders**, every user's rejected, cancelled and expired requests with their close reason and close time, filtered by user, reason and a created or closed date range; both table on desktop, cards on mobile. Issue #241 adds **Disputes** first among the order sections: the DISPUTED orders with a count badge, each with its category, note and an Auto-escalated tag, and a Resolve dialog — pay the courier, refund the requester, or a split whose two shares add up to the reward; cards at every width) |
| `docs/wireframes/forgot-password.png` | Forgot/reset password — email step + reset form |
| `docs/wireframes/account-recovery.png` | Account recovery — restore a deleted account within 30 days |
| `docs/wireframes/notification-center.png` | Notification center — bell with unread badge, read/unread list, empty state (built in the web half of issue #66: rows, blue unread dot, grayed read rows, "Mark all as read", empty-state copy as drawn; three additions the wireframe doesn't show, each an author decision via options Q&A — an **All/Unread filter toggle**, a **"Load more"** button (the wireframe draws no paging control; the REST list is paged), and **clicking an unread row marks it read** (no per-row control is drawn; mark-unread exists in the API but has no UI affordance); a fourth addition from the notification-sound follow-up — a **header sound toggle** muting the arrival chime, mirrored by the profile page's Preferences switch (author decision via options Q&A); a fifth from issue #232 — **clicking a row that references a request opens it** at the delivery-detail page (`/deliveries/{requestId}`): an unread request row is also marked read on the way, read request rows stay clickable to navigate, and only rows without a `payload.requestId` keep the mark-read-only click (read ones stay inert), all author decisions via options Q&A; and one rendering note from issue #221 — **courier names hydrate client-side** (D32 read-time lookup through the gateway), so a freshly loaded or pushed row can show the actor-less copy briefly before the name resolves in place) |
| `docs/wireframes/add-edit-supplier.png` | Add/edit supplier (admin) — form modal + delete confirmation |
| `docs/wireframes/public-profile.png` | Public profile — another user's minimal profile view (built in issue #257 at `/users/:id`, reached by link — the assigned courier's name on My Requests and the requester's name on Active Delivery — not from the nav. Shows username, the star row with the rounded average and review count, one **Avg rating received** tile, paged **Recent Reviews** (5 per page) whose rater names hydrate client-side (D32, so they can appear a beat after the cards), and **⚑ Report User** posting rating-service's fixed reason list, hidden on the viewer's own profile. Two tiles the wireframe draws are **not built**: "Joined {date}" and "{n} deliveries completed" — no API carries either (public profile is username-only per F8.1.1); follow-up issue filed instead, author decision via options Q&A. No entry links from notification rows (the whole row already navigates on click) or History/Open Requests rows (their list items carry no counterpart id) — author decisions via options Q&A) |
| `docs/wireframes/rating-review.png` | Rating & review — post-completion star rating, write-up, report user (the **Rate Your Experience** dialog is built in issue #256, launched from an **Actions column on Request History's COMPLETED rows** on both tabs — not My Requests, which lists only active states, so a completed request never appears there; the entry point shows **Rated ★n** instead once `GET /ratings/mine` says the viewer rated it, author decisions via options Q&A. The dialog is as drawn — required stars, "Write a review (optional)" with a 1000-char counter matching rating-service's cap, Cancel / Submit Review — plus the subtitle flips to **"Requested by {name}"** on the courier tab, and the wireframe's struck fifth star is styling, all five render alike. The **Report Review** dialog is issue #254's scope, not built here) |
| `docs/wireframes/dispute-flow.png` | Dispute flow — dispute form + admin resolution panel (built in issue #241 as the **Report a problem** dialog on My Requests — a required category of Not delivered, Wrong item, Damaged item or Other, and a note of at most 500 characters, required for Other — and the admin dashboard's **Disputes** section with its Resolve dialog; team decisions DSP-3, DSP-5, DSP-7, DSP-8 in `docs/disputes.md`) |
| `docs/wireframes/flow-requester.png` | Requester flow diagram — end-to-end journey map |
| `docs/wireframes/flow-courier.png` | Courier flow diagram — end-to-end journey map |

## Planned Frontend Workflow

The frontend is **one single-page application** (one React SPA, one
router, one shared app shell — auth/session handling, navigation, and
common UI primitives live in shared code, not per-feature copies).

Work is divided by **service domain**: each team member develops the UI
pages that correspond to the backend service they own. Indicative
mapping of screens to domains:

| Service domain | Screens |
| --- | --- |
| User service | Sign Up, Login, Forgot Password, Account Recovery, Profile, Public Profile, Admin Dashboard (user management) |
| Supplier service | Suppliers, Add/Edit Supplier (admin) |
| Order service | Create Request, My Requests, Open Requests, Active Delivery, Order History, Dispute Flow, Rating & Review |
| Credit service | Credit History, credit balance widgets |
| Notification service | Notification Center, in-app toasts/badges |

**Overlaps are expected** — e.g. the Dashboard composes widgets from
several domains, and the nav bar shows credits and notifications
everywhere. For overlapping pieces: whoever needs a shared component
first builds it in the shared layer, and cross-domain changes should be
flagged to the affected owner in the PR rather than silently edited.
Ownership means "primary developer/reviewer", not exclusive access.

## Requirements to keep in mind

- The UI must be **responsive across mobile and wide-screen widths**
  (course must-have M1; NFR "Overall UI" — Very High priority). Every
  screen needs to work at both.
- Complete workflows for **both requester and courier** roles, including
  clear user feedback and handling of invalid or unsuccessful actions.
