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
// Reviewed by: Ryan Ang

import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import { useNavigate, Outlet } from 'react-router'
import { AuthContext } from './useAuth'
import { useLocalStorage } from './useLocalStorage'
import { errorMessage, setTokenSource } from '@/lib/api/http'
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
      navigate('/')
    }

    // call this function to sign out logged in user
    const logout = () => {
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

    return { token, role, me, login, logout, updateMe }
  }, [token, role, me, navigate, setToken])

  return (
    <AuthContext.Provider value={value}>
      <Outlet />
    </AuthContext.Provider>
  )
}
