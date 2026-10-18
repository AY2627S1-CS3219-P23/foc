// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-28, while merging main into
// the #113 branch. Scope: the redirect moved from a useEffect + loading
// state to <Navigate> during render, fixing the react-hooks
// set-state-in-effect lint error; behaviour otherwise unchanged. The
// component itself comes from PR #139.
// 2026-10-11, Claude Code (Fable 5), issue #94: a session whose account
// is still soft-deleted (deletionScheduledFor on the stored token) is
// held on /account-recovery — every other protected page's data calls
// answer 404 for a deleted account, so a reloaded deleted session must
// not wander into them.
// Reviewed by: Ryan Ang
// 2026-10-17, Codex (GPT-6), PR #180 review, issue #94:
// AI-generated (edited by Codex; Leong Wei Zhi review pending).
// Recovery hold now lives in AuthProvider so it also covers public pages.
// Author review: Leong Wei Zhi to review via PR #180.

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
