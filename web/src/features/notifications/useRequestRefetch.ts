// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-27, issue #233.
// Scope: push-triggered refetch for the order pages — when a STOMP
// notification arrives whose payload.requestId matches a request the
// page is showing, the page's existing REST fetch is re-run. The push
// is only the trigger; REST stays the source of truth (D32's read-side
// pattern). Scope (all four order pages), the fixed debounce window and
// reusing the provider's stream instead of a second subscription are
// the author's decisions via options Q&A.
// Author review: [pending]

import { useContext, useEffect, useRef } from 'react'

import type { NotificationDto } from './types'
import { NotificationsContext } from './useNotifications'

// One refetch per window, however many frames a burst delivers.
export const REQUEST_REFETCH_DEBOUNCE_MS = 400

const NO_PUSHES: NotificationDto[] = []

// Calls onMatch (debounced) when a live-pushed request event concerns
// one of the given ids. Pages pass the ids they currently display and
// their existing refetch trigger; both are read through refs, so
// callers need not memoize them.
//
// Outside a NotificationProvider (bare renders in tests) this is a
// permanent no-op: no push channel, no push-driven refresh. Frames
// pushed before mount never trigger — the page's mount fetch is
// fresher. One accepted gap: a frame arriving during the page's first
// fetch, while ids are still empty, cannot match; the in-flight
// response almost always reflects that change anyway.
export function useRequestRefetch(ids: number[], onMatch: () => void): void {
  const context = useContext(NotificationsContext)
  const pushedNotifications = context?.pushedNotifications ?? NO_PUSHES

  // Always the committed page's rows: an id that just paged out no
  // longer matches, one that just paged in does. Synced every commit
  // (and before the consume effect below, which is declared later).
  const idsRef = useRef(ids)
  const onMatchRef = useRef(onMatch)
  useEffect(() => {
    idsRef.current = ids
    onMatchRef.current = onMatch
  })

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Seeded with whatever was already in the queue at mount, then diffed
  // per arrival: the provider only changes the array's reference when a
  // frame arrives, and only ever appends (and caps), so fresh frames
  // are exactly the new object references.
  const consumed = useRef(pushedNotifications)
  useEffect(() => {
    if (pushedNotifications === consumed.current) return
    const fresh = pushedNotifications.filter(
      (n) => !consumed.current.includes(n),
    )
    consumed.current = pushedNotifications

    // payload.requestId arrives as a decimal string while page ids are
    // numbers, so both sides are compared as strings.
    const wanted = new Set(idsRef.current.map(String))
    const matched = fresh.some(
      (n) =>
        n.eventType.startsWith('request.') &&
        wanted.has(String(n.payload.requestId ?? '')),
    )
    if (!matched) return

    // Fixed window, not a resetting debounce: a pending timer swallows
    // later matches, so a burst means one refetch and a continuous
    // stream can never starve it. Not cleared here — a second frame
    // mid-window must not cancel the pending refetch.
    if (timer.current === null) {
      timer.current = setTimeout(() => {
        timer.current = null
        onMatchRef.current()
      }, REQUEST_REFETCH_DEBOUNCE_MS)
    }
  }, [pushedNotifications])

  useEffect(
    () => () => {
      if (timer.current !== null) clearTimeout(timer.current)
    },
    [],
  )
}
