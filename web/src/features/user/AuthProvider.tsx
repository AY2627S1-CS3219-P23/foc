// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, PR #141 review.
// Scope: login navigates to the home page ("/") instead of "/home", which
// isn't a route, and is the only navigation after login. The component
// itself comes from PR #139.
// 2026-09-29, Claude Code (Fable 5), PR #142: the stored session's
// accessToken is now fed to apiFetch via setTokenSource, so every API
// call carries the Authorization header (getToken was hardcoded to
// null); the session is typed as the LoginResponse it actually is.
// 2026-09-29, Claude Code (Sonnet 5): exposes `role`, decoded from the
// token's claims (display-only — see jwt.ts), so pages can hide
// admin-only controls for non-admin callers instead of showing them
// and letting the server's role gate (supplier-service #106) reject
// the action after the fact.
// 2026-10-10, Claude Code (Opus 5), PR #149 review (@Sinnez1): updateMe
// writes a saved account back into this one answer, which the profile
// page calls after an edit — it reads `me` instead of asking
// GET /users/me a second time, so the request happens once per session
// and no consumer is left on stale values.
// 2026-09-29, Claude Code (Opus 5.5), issue #147: Prettier formatting;
// also asks GET /users/me once per session and shares the answer as
// `me` (the /admin guard and the nav bar's Admin Dashboard link use it,
// team decision). Merged with `role` above: both are exposed.
// 2026-10-11, Claude Code (Fable 5), issue #151: logout() now also calls
// POST /auth/logout (best-effort, team decision) so the server denylists
// the token; the local session is cleared either way.
// 2026-10-11, Claude Code (Fable 5), issue #94: a login whose response
// carries deletionScheduledFor (a soft-deleted account — login no longer
// recovers it) lands on /account-recovery instead of home, and the
// GET /users/me fetch is skipped while the flag stands (the active-only
// lookup would 404 and poison `me`). restored() is the recovery screen's
// exit: it strips the flag from the stored session and seeds `me` with
// the account POST /users/me/recover returned.
// Reviewed by: Ryan Ang

import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import { useNavigate, Outlet } from 'react-router'
import { AuthContext } from './useAuth'
import { useLocalStorage } from './useLocalStorage'
import { apiFetch, errorMessage, setTokenSource } from '@/lib/api/http'
import { adminUserApi } from './adminApi'
import { decodeJwtRole } from './jwt'
import type { AdminUser, LoginResponse } from './types'
import type { CurrentUser } from './useAuth'

// one shared object, so the context value stays stable while loading
const LOADING: CurrentUser = { status: 'loading' }

// the GET /users/me answer for one session, so a new login asks afresh
interface MeCheck {
  token: LoginResponse
  me: CurrentUser
}

export const AuthProvider = () => {
  const [token, setToken] = useLocalStorage<LoginResponse>('user')
  const navigate = useNavigate()

  // apiFetch's swappable token source; refreshed whenever the session
  // changes so requests carry the current token (or none after logout).
  // Layout effect, not useEffect: passive effects run children-first,
  // so a page's mount-time fetch would fire before this provider's
  // effect set the source; layout effects all run before any of them.
  useLayoutEffect(() => {
    setTokenSource(() => token?.accessToken ?? null)
  }, [token])

  const [meCheck, setMeCheck] = useState<MeCheck | null>(null)
  useEffect(() => {
    if (!token) return
    // a still-deleted session (issue #94): GET /users/me filters deleted
    // accounts and would 404. The recovery screen reads the token, not
    // `me`; restored() seeds `me` once the account is back
    if (token.deletionScheduledFor) return
    let cancelled = false
    adminUserApi.getCurrentUser().then(
      (user) => {
        if (!cancelled) setMeCheck({ token, me: { status: 'ready', user } })
      },
      (err: unknown) => {
        if (!cancelled)
          setMeCheck({
            token,
            me: {
              status: 'error',
              message: errorMessage(err, 'Could not load your account.'),
            },
          })
      },
    )
    return () => {
      cancelled = true
    }
  }, [token])
  const me: CurrentUser | null = !token
    ? null
    : meCheck?.token === token
      ? meCheck.me
      : LOADING

  const role = useMemo(
    () => (token ? decodeJwtRole(token.accessToken) : null),
    [token],
  )

  const value = useMemo(() => {
    // call this function to set login values
    const login = async (session: LoginResponse) => {
      setToken(session)
      // a deleted account logs in without being recovered (issue #94):
      // offer the restore-or-keep-deleted choice before anything else
      navigate(session.deletionScheduledFor ? '/account-recovery' : '/')
    }

    // call this function to sign out logged in user
    const logout = () => {
      // denylist the token server-side (issue #151). Best-effort by team
      // decision: signing out locally must work even when the server
      // doesn't — an unrevoked token just lives out its remaining expiry.
      // Fired before the token is cleared (apiFetch reads the token
      // source synchronously while building the request).
      void apiFetch<void>('user', '/auth/logout', { method: 'POST' }).catch(
        () => {},
      )
      setToken(null)
      navigate('/', { replace: true })
    }

    // an account saved elsewhere in the app, written back into the one
    // GET /users/me answer this session holds. Stamped with the current
    // token so it is dropped by the same rule the fetch is: a new login
    // asks afresh.
    const updateMe = (user: AdminUser) => {
      if (token) setMeCheck({ token, me: { status: 'ready', user } })
    }

    // the recovery screen's exit (issue #94): the account is active
    // again, so the session sheds its deletion flag — kept, the guard
    // would bounce every page back to /account-recovery — and `me` is
    // seeded from the restore's response, stamped with the NEW session
    // object so the identity check above keeps it
    const restored = (user: AdminUser) => {
      if (!token) return
      const recovered: LoginResponse = { ...token }
      delete recovered.deletionScheduledFor
      setToken(recovered)
      setMeCheck({ token: recovered, me: { status: 'ready', user } })
    }

    return { token, role, me, login, logout, updateMe, restored }
  }, [token, role, me, navigate, setToken])

  return (
    <AuthContext.Provider value={value}>
      <Outlet />
    </AuthContext.Provider>
  )
}
