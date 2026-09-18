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
| `docs/wireframes/signup.png` | Sign Up — NUS email, username, password (live strength checklist), OTP verification step |
| `docs/wireframes/login.png` | Login — username/email + password |
| `docs/wireframes/dashboard.png` | Dashboard — credit balance, requester/courier mode toggle, quick actions, active requests with status chips, recent activity |
| `docs/wireframes/suppliers.png` | Suppliers — search, category and campus-zone filters, supplier detail panel, empty state |
| `docs/wireframes/profile.png` | Profile — account details, edit flows, delete-account modal |
| `docs/wireframes/credit-history.png` | Credit history — balances and transaction list |
| `docs/wireframes/order-history.png` | Order history — past/current orders |
| `docs/wireframes/create-request.png` | Create request — new errand form |
| `docs/wireframes/my-requests.png` | My requests — requester's view of own requests |
| `docs/wireframes/open-requests.png` | Open requests — courier's browse/accept view |
| `docs/wireframes/active-delivery.png` | Active delivery — courier's in-progress order view |
| `docs/wireframes/admin-dashboard.png` | Admin dashboard — user/vendor management |
| `docs/wireframes/forgot-password.png` | Forgot/reset password — email step + reset form |
| `docs/wireframes/account-recovery.png` | Account recovery — restore a deleted account within 30 days |
| `docs/wireframes/notification-center.png` | Notification center — bell with unread badge, read/unread list, empty state |
| `docs/wireframes/add-edit-supplier.png` | Add/edit supplier (admin) — form modal + delete confirmation |
| `docs/wireframes/public-profile.png` | Public profile — another user's minimal profile view |
| `docs/wireframes/rating-review.png` | Rating & review — post-completion star rating, write-up, report user |
| `docs/wireframes/dispute-flow.png` | Dispute flow — dispute form + admin resolution panel |
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
