// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5), 2026-09-29.
// Scope: decodes the role claim out of the stored JWT for display-only
// purposes (hiding admin controls a non-admin can't use) — replaces the
// hardcoded IS_ADMIN in routes/suppliers.tsx now that AuthProvider (PR
// #142) actually stores the access token. Not a trust boundary: the
// signature is never verified here, since every enforcement decision
// already happens server-side (supplier-service's role gate, issue
// #106) — this only decides what the UI shows.
// Reviewed by: [pending]

import type { UserRole } from './types'

// Decodes a JWT's payload (the middle, base64url-encoded segment) without
// verifying its signature. Returns null for anything that isn't a
// well-formed token so callers can fail safe (treat as "no role").
export function decodeJwtRole(token: string): UserRole | null {
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
