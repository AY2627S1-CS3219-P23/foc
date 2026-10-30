// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-30, issue #256.
// Scope: the viewer's rated/not-rated state for one completed request,
// cached per (viewer, request) for the session — ratings are immutable,
// so a cached answer never goes stale except through this tab's own
// submit, which writes the cache directly (recordMyRating). 403/404 are
// the service's "not rateable" answers and are cached too; other
// failures are dropped from the cache so a later render retries
// (the usernames-cache precedent in features/order/api.ts).
// Author review: [pending]

import { useEffect, useState } from 'react'

import { useCurrentUserId } from '@/features/order/useCurrentUserId'
import { ApiError } from '@/lib/api/http'
import { ratingApi } from './api'
import type { RatingResponse } from './types'

export type MyRatingState =
  | { kind: 'rated'; rating: RatingResponse }
  | { kind: 'not-rated' }
  // not a party (403), or the request is not completed/ingested (404).
  // A just-completed request can 404 until rating-service's event
  // consumer catches up; the cached answer then lasts the session, and a
  // reload recovers. Accepted as low-stakes (flagged in the PR).
  | { kind: 'not-rateable' }

const states = new Map<string, Promise<MyRatingState>>()

const key = (userId: number, requestId: number) => `${userId}:${requestId}`

function fetchState(userId: number, requestId: number): Promise<MyRatingState> {
  const k = key(userId, requestId)
  let state = states.get(k)
  if (!state) {
    state = ratingApi.getMyRating(requestId).then(
      ({ rating }): MyRatingState =>
        rating ? { kind: 'rated', rating } : { kind: 'not-rated' },
      (err) => {
        if (
          err instanceof ApiError &&
          (err.status === 403 || err.status === 404)
        )
          return { kind: 'not-rateable' } as MyRatingState
        states.delete(k)
        throw err
      },
    )
    states.set(k, state)
  }
  return state
}

// After this tab's own successful submit: flip the row without a refetch.
export function recordMyRating(
  userId: number,
  requestId: number,
  rating: RatingResponse,
) {
  states.set(key(userId, requestId), Promise.resolve({ kind: 'rated', rating }))
}

// After a 409 (rated elsewhere/earlier): drop the stale "not-rated" entry
// so the next render refetches and lands on the true rated state.
export function clearMyRating(userId: number, requestId: number) {
  states.delete(key(userId, requestId))
}

// test hook (the clearUsernameCache precedent)
export function clearMyRatingCache() {
  states.clear()
}

// null = still loading, disabled, or the viewer is unknown. `version`
// re-runs the lookup after the page pokes the cache (the detailVersion
// pattern in my-requests.tsx); a poked entry resolves instantly.
export function useMyRating(
  requestId: number,
  enabled: boolean,
  version = 0,
): MyRatingState | null {
  const userId = useCurrentUserId()
  const [state, setState] = useState<{
    key: string
    value: MyRatingState
  } | null>(null)

  useEffect(() => {
    if (!enabled || userId === null) return
    let cancelled = false
    fetchState(userId, requestId).then(
      (value) => {
        if (!cancelled) setState({ key: key(userId, requestId), value })
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [requestId, enabled, userId, version])

  if (!enabled || userId === null) return null
  return state?.key === key(userId, requestId) ? state.value : null
}
