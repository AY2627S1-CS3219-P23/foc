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
// 2026-10-13, Claude Code (Opus 5), PR #182 review (@Sinnez1): the flag
// is also published to listeners, because reading it at mount alone
// missed the 401 that lands while the login page is already open — and
// the flag then survived to a later visit, where the notice appeared
// after a plain manual logout. The page subscribes and consumes it
// whichever way it arrives.
// Author review: Leong Wei Zhi to review via the PR.

let sessionEnded = false
const listeners = new Set<() => void>()

// AuthProvider: a 401 on a token-bearing request ended the session
export function flagSessionEnded() {
  sessionEnded = true
  // the login page may already be open, in which case nothing is about
  // to read the flag on a first render — tell it instead
  for (const listener of [...listeners]) listener()
}

// login page, in render: should the notice show?
export function sessionEndedFlag() {
  return sessionEnded
}

// login page: the notice has been taken, so it can't surface again on
// some later visit. Deliberately silent — nothing reacts to a clear.
export function clearSessionEnded() {
  sessionEnded = false
}

// login page: be told about a sign-out that happens while it is open.
// Returns the unsubscribe function.
export function subscribeSessionEnded(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
