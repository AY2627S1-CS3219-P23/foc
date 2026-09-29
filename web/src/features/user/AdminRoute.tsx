// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-29, issue #147.
// Scope: admin-only route guard — uses the signed-in user's role from
// GET /users/me (team decision; AuthProvider asks once per session and
// shares it as `me`) and shows the page only to ADMIN and OWNER;
// everyone else goes to the home page. Nests inside ProtectedRoute,
// which has already sent logged-out visitors to /login. Like
// ProtectedRoute, this only shapes the UI: user-service's own role rules
// are what protect the admin endpoints.
// Author review: Ryan to review via the PR.

import { Navigate, Outlet } from 'react-router'

import { isAdmin, useAuth } from './useAuth'

export function AdminRoute() {
  const { me } = useAuth()

  if (!me || me.status === 'loading') {
    return <p className="text-sm text-gray-500">Checking access...</p>
  }

  if (me.status === 'error') {
    return (
      <p
        role="alert"
        className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
      >
        Could not check your access. Try again.
      </p>
    )
  }

  if (!isAdmin(me)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
