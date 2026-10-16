// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Sonnet 5.5), 2026-10-16.
// Scope: the signed-in user's id from the session's GET /users/me, which
// the order pages compare with an order's requester/courier to decide what
// to show. Display logic only — order-service enforces the same rules.
// Author review: [pending]

import { useAuth } from '@/features/user/useAuth'

export function useCurrentUserId(): number | null {
  const { me } = useAuth()
  return me?.status === 'ready' ? me.user.id : null
}
