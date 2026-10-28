// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-27, issue #233.
// Scope: push-triggered refetch for the order pages — when a STOMP
// notification arrives whose payload.requestId matches a request the
// page is showing, the page's existing REST fetch is re-run. The push
// is only the trigger; REST stays the source of truth (D32's read-side
// pattern). Scope (all four order pages), the fixed debounce window and
// reusing the provider's stream instead of a second subscription are
// the author's decisions via options Q&A.
// 2026-10-28, Claude Code (Opus 5), PR #264 review (@Sinnez1): onMatch
// is handed the ids that matched, so a page need not refetch rows no
// push touched; frame fields are read defensively; the cleanup resets
// the timer ref; and a push-channel reconnect is a trigger too, so
// frames missed while the socket was down no longer leave a page stale.
// Author review: [pending]

import { useCallback, useContext, useEffect, useRef } from 'react'

import type { NotificationDto } from './types'
import { NotificationsContext } from './useNotifications'

// One refetch per window, however many frames a burst delivers.
export const REQUEST_REFETCH_DEBOUNCE_MS = 400

const NO_PUSHES: NotificationDto[] = []

// Calls onMatch (debounced) when a live-pushed request event concerns
// one of the given ids, passing the ids the window's frames touched —
// a page with per-row fetches can refresh just those. Pages pass the
// ids they currently display and their existing refetch trigger; both
// are read through refs, so callers need not memoize them.
//
// Outside a NotificationProvider (bare renders in tests) this is a
// permanent no-op: no push channel, no push-driven refresh. Frames
// pushed before mount never trigger — the page's mount fetch is
// fresher. One accepted gap: a frame arriving during the page's first
// fetch, while ids are still empty, cannot match; the in-flight
// response almost always reflects that change anyway.
export function useRequestRefetch(
  ids: number[],
  onMatch: (matchedIds: number[]) => void,
): void {
  const context = useContext(NotificationsContext)
  const pushedNotifications = context?.pushedNotifications ?? NO_PUSHES
  const pushReconnects = context?.pushReconnects ?? 0

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
  // Ids matched since the pending window opened: a later frame in the
  // window must still reach onMatch's argument even though it does not
  // get its own refetch.
  const pendingIds = useRef(new Set<number>())

  // Opens the window, or joins the one already open. Reads only refs,
  // so it is stable for the lifetime of the hook.
  const schedule = useCallback((matched: Set<number>) => {
    for (const id of matched) pendingIds.current.add(id)

    // Fixed window, not a resetting debounce: a pending timer swallows
    // later matches, so a burst means one refetch and a continuous
    // stream can never starve it. Not cleared here — a second frame
    // mid-window must not cancel the pending refetch.
    if (timer.current !== null) return
    timer.current = setTimeout(() => {
      timer.current = null
      const touched = [...pendingIds.current]
      pendingIds.current.clear()
      onMatchRef.current(touched)
    }, REQUEST_REFETCH_DEBOUNCE_MS)
  }, [])

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
    // numbers, so both sides are compared as strings — and mapped back,
    // so onMatch is given the page's own ids. Every frame field is read
    // defensively (as eventMessage.ts does): a frame that parses but
    // carries no eventType or a null payload is ignored, not a throw
    // inside the effect that would take the page down (PR #264 review).
    const byString = new Map(idsRef.current.map((id) => [String(id), id]))
    const matched = new Set<number>()
    for (const n of fresh) {
      if (!n.eventType?.startsWith('request.')) continue
      const id = byString.get(String(n.payload?.requestId ?? ''))
      if (id !== undefined) matched.add(id)
    }
    if (matched.size > 0) schedule(matched)
  }, [pushedNotifications, schedule])

  // A reconnect means frames were delivered to nobody while the socket
  // was down, so every shown row is suspect: refetch them all. The
  // provider counts reconnects only, never a session's first connect —
  // that one is covered by the page's own mount fetch (PR #264 review).
  const seenReconnects = useRef(pushReconnects)
  useEffect(() => {
    if (pushReconnects === seenReconnects.current) return
    seenReconnects.current = pushReconnects
    if (idsRef.current.length > 0) schedule(new Set(idsRef.current))
  }, [pushReconnects, schedule])

  useEffect(
    () => () => {
      if (timer.current !== null) clearTimeout(timer.current)
      // reset too: an effect teardown without a remount (StrictMode,
      // Fast Refresh) would otherwise leave a non-null ref and swallow
      // every later match (PR #264 review)
      timer.current = null
      pendingIds.current.clear()
    },
    [],
  )
}
