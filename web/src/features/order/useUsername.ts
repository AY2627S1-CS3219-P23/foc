// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-18.
// Scope: a user's display name for the order pages. order-service returns
// user ids only, so the name comes from user-service's GET /users/{id}
// (orderApi.getUsername, cached) — the author's choice over showing ids.
// Shows "User #id" until the lookup answers, and if it fails.
// Author review: [pending]

import { useEffect, useState } from 'react'

import { orderApi } from './api'

export function useUsername(userId: number | null): string | null {
  const [name, setName] = useState<{ id: number; username: string } | null>(
    null,
  )

  useEffect(() => {
    if (userId === null) return
    let cancelled = false
    orderApi.getUsername(userId).then(
      (username) => {
        if (!cancelled) setName({ id: userId, username })
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [userId])

  if (userId === null) return null
  return name?.id === userId ? name.username : `User #${userId}`
}
