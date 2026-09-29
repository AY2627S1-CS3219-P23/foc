// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-29, PR #142 (JWT wiring into
// apiFetch). Scope: token is the stored LoginResponse session (or null
// when logged out) instead of jose's JWTPayload, which login() was
// typed as but never actually received. The hook itself comes from
// PR #139.
// 2026-09-29, Claude Code (Sonnet 5): added `role`, decoded from the
// token by AuthProvider — lets pages hide admin-only controls (e.g.
// routes/suppliers.tsx's IS_ADMIN) for the caller's actual role.
// 2026-09-29, Claude Code (Opus 5.5), issue #147: Prettier formatting;
// `me`, the signed-in
// user's account from GET /users/me, shared by the /admin guard and the
// nav bar's Admin Dashboard link (one request per login).
// Reviewed by: Leong Wei Zhi (via pull request).

import { createContext, useContext } from 'react'
import type { AdminUser, LoginResponse, UserRole } from './types'

// GET /users/me for the current session: null while logged out
export type CurrentUser =
  | { status: 'loading' }
  | { status: 'ready'; user: AdminUser }
  | { status: 'error'; message: string }

// roles that may use the admin dashboard (user-service's
// hasAnyRole("ADMIN", "OWNER") on the admin endpoints)
export const ADMIN_ROLES: readonly UserRole[] = ['ADMIN', 'OWNER']

export function isAdmin(me: CurrentUser | null): boolean {
  return me?.status === 'ready' && ADMIN_ROLES.includes(me.user.role)
}

export interface AuthData {
  token: LoginResponse | null
  // decoded from the token (display-only, see jwt.ts)
  role: UserRole | null
  // GET /users/me for this session
  me: CurrentUser | null
  login(session: LoginResponse): Promise<void>
  logout(): void
}

export const AuthContext = createContext<AuthData | null>(null)

export const useAuth = () => {
  const context = useContext(AuthContext)

  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider')
  }

  return context
}
