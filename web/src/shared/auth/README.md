<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
  Scope: placeholder note for where session/auth code will live.
  Reviewed by: Leong Wei Zhi (via pull request).
-->

# `src/shared/auth/` — session & JWT handling (not built yet)

Auth/session code lives here in the shared layer (one copy for the
whole SPA), owned by the user-service domain: login state, the JWT
session token, and route guards. Token and claim shapes (roles,
expiry, algorithm) are the User Service's contract — nothing is fixed
here until that service defines them (the repo's role model is
requester/courier/admin; see root `AGENTS.md`).

`src/lib/api/http.ts` already exposes the hook point: call
`setTokenSource(...)` from here once session handling exists, and every
domain's `apiFetch` calls pick up the `Authorization` header.
