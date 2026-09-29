// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-28, while merging main into
// the #113 branch. Scope: the redirect moved from a useEffect + loading
// state to <Navigate> during render, fixing the react-hooks
// set-state-in-effect lint error; behaviour otherwise unchanged. The
// component itself comes from PR #139.
// Reviewed by: Ryan Ang

import { Navigate, Outlet } from 'react-router'
import { useAuth } from './useAuth'

// Only blocks on client side, still need server side protection for API calls
export const ProtectedRoute = () => {
  const data = useAuth()

  if (!data?.token) {
    // user is not authenticated
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}
