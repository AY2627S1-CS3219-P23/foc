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

The Figma file additionally contains requester/courier flow diagrams not
snapshotted here — use the Figma MCP server for those, mindful of the
rate limit.

## Requirements to keep in mind

- The UI must be **responsive across mobile and wide-screen widths**
  (course must-have M1; NFR "Overall UI" — Very High priority). Every
  screen needs to work at both.
- Complete workflows for **both requester and courier** roles, including
  clear user feedback and handling of invalid or unsuccessful actions.
