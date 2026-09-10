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

Screens covered so far (each in desktop and mobile variants):

- **Sign Up** — account creation with NUS email, username, password
  (live strength checklist), and OTP verification step
- **Dashboard** — credit balance, requester/courier mode toggle, quick
  actions, active requests with status chips, recent activity feed
- **Suppliers** — searchable/filterable supplier listing (category and
  campus zone filters), supplier detail panel, empty-state message

## Requirements to keep in mind

- The UI must be **responsive across mobile and wide-screen widths**
  (course must-have M1; NFR "Overall UI" — Very High priority). Every
  screen needs to work at both.
- Complete workflows for **both requester and courier** roles, including
  clear user feedback and handling of invalid or unsuccessful actions.
