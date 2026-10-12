// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #178.
// Scope: one-shot "session ended" flag between AuthProvider's forced
// sign-out and the login page's notice. Router navigation state (the
// sign-up notice's channel) loses this race: clearing the token makes
// ProtectedRoute bounce to /login statelessly while the handler's own
// stateful navigation sits in the router's startTransition, so the login
// page can mount after the state was already wiped. A module flag has no
// such ordering: set before the token is cleared, read on the login
// page's first render, cleared after it mounts. It dies on reload, which
// matches the notice's shows-on-arrival-only intent.
// Author review: Leong Wei Zhi to review via the PR.

let sessionEnded = false

// AuthProvider: a 401 on a token-bearing request ended the session
export function flagSessionEnded() {
  sessionEnded = true
}

// login page, in render: should the notice show?
export function sessionEndedFlag() {
  return sessionEnded
}

// login page, after mount: the notice was delivered
export function clearSessionEnded() {
  sessionEnded = false
}
