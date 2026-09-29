// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5), 2026-09-29.
// Scope: decodes the role claim out of the stored JWT for display-only
// purposes (hiding admin controls a non-admin can't use) — replaces the
// hardcoded IS_ADMIN in routes/suppliers.tsx now that AuthProvider (PR
// #142) actually stores the access token. Not a trust boundary: the
// signature is never verified here, since every enforcement decision
// already happens server-side (supplier-service's role gate, issue
// #106) — this only decides what the UI shows.
// PR #143 review (LeongWZ): `token.split('.')` sat outside the try, so
// a non-string token (e.g. a session stored by a pre-#142 build, or
// any other shape AuthProvider hands in unvalidated from localStorage)
// threw a TypeError that escaped this function entirely — uncaught in
// render, white-screening the app past react-router's default error
// boundary. Fixed with a typeof guard before any string method runs,
// so a malformed token now fails safe (null) like every other bad
// input this function already handled.
// Reviewed by: [pending]

import type { UserRole } from './types'

// Decodes a JWT's payload (the middle, base64url-encoded segment) without
// verifying its signature. Returns null for anything that isn't a
// well-formed token so callers can fail safe (treat as "no role").
export function decodeJwtRole(token: string): UserRole | null {
  if (typeof token !== 'string') return null

  const [, payload] = token.split('.')
  if (!payload) return null

  try {
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const claims = JSON.parse(json) as { role?: unknown }
    return claims.role === 'USER' || claims.role === 'ADMIN' || claims.role === 'OWNER'
      ? claims.role
      : null
  } catch {
    return null
  }
}
