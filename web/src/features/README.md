<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
  Scope: convention note for the per-domain feature folders.
  Reviewed by: Leong Wei Zhi (via pull request).
  2026-11-02, Claude Code (Fable 5), issue #258: the folder list caught
  up with main — rating/ added (N1, issues #256/#257) and notification/
  corrected to notifications/, the folder's actual name. Transcription
  only.
  Author review: Leong Wei Zhi, pending pull request review.
-->

# `src/features/` — per-domain code

Each team member owns one subfolder matching their backend service
domain (see `web/AGENTS.md` for the screen mapping):

```
features/
├── user/
├── supplier/
├── order/
├── credit/
├── notifications/
└── rating/        # N1: rate-experience modal, star displays, report
                   # user, my-rating cache (issues #256/#257)
```

Conventions:

- Components, hooks, and API calls for a domain live in its folder.
- Pages are added as files under `src/routes/` plus one child entry in
  the route table (`src/routes/index.tsx`), so parallel page PRs
  conflict on at most one line. Route files stay thin and import from
  the feature folder.
- Cross-domain/shared pieces go in `src/shared/` — whoever needs one
  first builds it there, and flags cross-domain changes to the owner
  in the PR (`web/AGENTS.md`).
- REST calls go through `src/lib/api/http.ts` (`apiFetch`) so auth
  headers and error handling stay in one place. There is deliberately
  no data-fetching library in the scaffold — each owner picks their
  own tools (or none) for their domain.
