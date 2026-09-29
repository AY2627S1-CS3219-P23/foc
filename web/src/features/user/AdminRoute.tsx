// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: admin-only route guard — asks GET /users/me for the signed-in
// user's role (team decision) and shows the page only to ADMIN and
// OWNER; everyone else goes to the home page. Nests inside
// ProtectedRoute, which has already sent logged-out visitors to /login.
// Like ProtectedRoute, this only shapes the UI: user-service's own role
// rules are what protect the admin endpoints.
// Author review: Ryan to review via the PR.

import { useEffect, useState } from 'react'
import { Navigate, Outlet } from 'react-router'

import { errorMessage } from '@/lib/api/http'
import { adminUserApi } from './adminApi'
import type { UserRole } from './types'
import { useAuth } from './useAuth'

const ADMIN_ROLES: readonly UserRole[] = ['ADMIN', 'OWNER']

// the answer for one session token, so a new login is checked afresh
interface RoleCheck {
  token: string | undefined
  role: UserRole | null
  error: string | null
}

export function AdminRoute() {
  const token = useAuth().token?.accessToken
  const [check, setCheck] = useState<RoleCheck | null>(null)

  useEffect(() => {
    let cancelled = false
    adminUserApi.getCurrentUser().then(
      (me) => {
        if (!cancelled) setCheck({ token, role: me.role, error: null })
      },
      (err: unknown) => {
        if (!cancelled)
          setCheck({
            token,
            role: null,
            error: errorMessage(err, 'Could not check your access. Try again.'),
          })
      },
    )
    return () => {
      cancelled = true
    }
  }, [token])

  // still asking (or asking again after a new login)
  if (!check || check.token !== token) {
    return <p className="text-sm text-gray-500">Checking access...</p>
  }

  if (check.error) {
    return (
      <p
        role="alert"
        className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
      >
        {check.error}
      </p>
    )
  }

  if (!check.role || !ADMIN_ROLES.includes(check.role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
