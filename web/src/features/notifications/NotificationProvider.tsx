// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: the single STOMP client of the whole app (design decision D2 /
// "Session mechanics", docs/notification-service.md), per the author's
// decisions (options Q&A): native WebSocket only, exponential reconnect
// 1 s doubling to a 30 s cap, badge-only UI until issue #66. Layout
// route below AuthProvider, mirroring its provider pattern.
// 2026-10-16, PR #185 re-review (@Sinnez1): ERROR-frame comment
// softened (the server sends one for any frame-handling failure, not
// only CONNECT auth); unused NotificationDto export dropped until #66
// reads the body.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1): an
// ERROR frame no longer kills push silently — bounded retry, then the
// session is marked stopped so the bell can say so (chosen by Leong Wei
// Zhi from four options via neutral options Q&A).
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-17, Claude Code (Fable 5), issue #66: the badge count seeds
// from GET /notifications/me/unread-count on login and re-seeds on
// every (re)connect — a reconnect may have missed frames — and after
// the Center's writes via refreshUnreadCount (author decision via
// options Q&A: the server count is authoritative; pushes increment on
// top of the seeded baseline). The pushed frame's body is now parsed
// and exposed so the open Center can prepend the row (author decision
// via options Q&A). Count sync failures are
// non-fatal, like the missing-URL case: a broken badge must not break
// every page the shell renders.
// 2026-10-17, Claude Code (Opus 5), PR #199 review (@claude, @codex):
// a count sync no longer loses a push that raced it (newest-sync-wins
// guard, and a push triggers its own sync), and pushes queue instead
// of a single replaceable value, so two frames delivered in one
// WebSocket message both reach the open Center.
// 2026-10-18, Claude Code (Fable 5), notification sound (follow-up to
// issue #66, no issue yet): a live push now plays a chime, keyed on the
// pushed queue's identity — the one signal that moves only on live
// arrivals, never on the REST seeds/re-syncs — so a burst batched into
// one render chimes once and a reconnect catch-up stays silent. The
// localStorage-backed toggle (device preference, default on) lives here
// and is shared through the context.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@Sinnez1): the
// chime keys on an arrival counter bumped before the body parse, so a
// frame that fails JSON.parse still chimes like it still bumps the
// badge (same live-only guarantee: the counter moves nowhere else);
// only the visible tab chimes — every open tab holds its own
// subscription and the user destination delivers to all; the first
// pointerdown unlocks the audio context inside a gesture for iOS
// Safari.
// 2026-10-18, Claude Code (Fable 5), PR #215 review (@claude bot): the
// sound preference opts into useLocalStorage's now-opt-in cross-tab
// sync ({ sync: true }); the session key keeps its old per-tab
// behaviour.
// 2026-10-28, Claude Code (Opus 5), PR #264 review (@Sinnez1), issue
// #233: reconnects of this session's socket are counted and exposed as
// pushReconnects. The badge already re-seeded on every (re)connect, but
// the order pages had no signal, so frames sent while the socket was
// down left them stale until something else refreshed them. The first
// connect of a session is not counted — a page's own mount fetch
// already covers it.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Outlet } from 'react-router'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { serviceBaseUrls } from '@/lib/api/config'
import { useAuth } from '@/features/user/useAuth'
import { useLocalStorage } from '@/features/user/useLocalStorage'
import { getUnreadCount } from './api'
import { playChime, unlockChime } from './chime'
import { NotificationsContext } from './useNotifications'
import { brokerUrl } from './stompUrl'
import type { NotificationDto } from './types'

// An ERROR frame is in practice a rejected CONNECT token, which no
// retry can fix — but the server sends one for any frame-handling
// failure, so a transient one gets a bounded number of chances before
// push is declared over for this session. Spaced at the reconnect
// policy's cap rather than its 1 s floor: a dead token must not be
// re-sent every second.
const ERROR_RETRY_LIMIT = 3
const ERROR_RETRY_DELAY_MS = 30_000

// How many pushed frames to keep for the Center to pick up. Only frames
// that arrive while it is open are prepended, so this just has to cover
// a burst delivered inside one render.
const PUSH_BUFFER = 20

// one frozen empty array, so a session with no pushes keeps a stable
// reference and the Center's render-phase pickup does not re-run
const NO_PUSHES: NotificationDto[] = []

// whether a live push plays the chime — a device preference (the same
// browser profile regardless of account), unlike the per-account keys
// AuthProvider and ProfileSection keep
const SOUND_KEY = 'notificationSoundEnabled'

// the count stamped with the session that produced it, so a logout or
// re-login starts over without a reset write (the same derive-by-token
// rule AuthProvider's meCheck uses)
interface SessionCount {
  token: string
  count: number
}

export const NotificationProvider = () => {
  const { token } = useAuth()
  const accessToken = token?.accessToken ?? null
  const [sessionCount, setSessionCount] = useState<SessionCount | null>(null)
  const unreadCount =
    accessToken !== null && sessionCount?.token === accessToken
      ? sessionCount.count
      : 0

  // The frames pushed this session, oldest first, stamped like the
  // count so a re-login never shows the previous session's rows. A
  // queue rather than one replaceable value: two frames delivered in a
  // single WebSocket message run their callbacks in one task, so React
  // batches them into one render and a single-value field would expose
  // only the last (PR #199 review). Capped — the Center dedupes by id
  // and anything older is already in the list it fetched.
  const [pushed, setPushed] = useState<{
    token: string
    items: NotificationDto[]
  } | null>(null)
  const pushedNotifications =
    accessToken !== null && pushed?.token === accessToken
      ? pushed.items
      : NO_PUSHES

  // Null (unset) and corrupt values both land on the default: on.
  // sync: a mute written in another tab lands here live — a device-wide
  // preference, unlike the per-account keys that must not sync (PR #215
  // review).
  const [storedSound, setStoredSound] = useLocalStorage<boolean>(
    SOUND_KEY,
    true,
    { sync: true },
  )
  const soundEnabled = storedSound !== false

  // Live frames received, counted before the body parse — a frame that
  // fails JSON.parse bumps the badge, so it must chime too (PR #215
  // review). Bumped nowhere else, so REST count seeds/re-syncs cannot
  // move it, and it never resets — logout changes nothing, so nothing
  // fires.
  const [arrivalSeq, setArrivalSeq] = useState(0)

  // Reconnects of this session's socket, never its first connect
  // (`connected` below is per client, so a new session starts over).
  // A bump means the channel was down for a while and any frame sent
  // meanwhile reached nobody: the pages refetch what they show
  // (issue #233, PR #264 review). Monotonic — consumers only compare
  // it with what they last saw.
  const [pushReconnects, setPushReconnects] = useState(0)

  // A chime per committed render that delivered new frames: a burst
  // batched into one render moves the counter once per frame but
  // commits once, and chimes once (an effect because sound is a side
  // effect, not render output).
  const chimed = useRef(arrivalSeq)
  useEffect(() => {
    if (arrivalSeq === chimed.current) return
    // advance even when the chime is skipped, so toggling the sound on
    // (or focusing the tab) later never replays an old arrival
    chimed.current = arrivalSeq
    // only the tab being looked at chimes: every open tab holds its own
    // subscription and the user destination delivers to all of them
    // (PR #215 review)
    if (soundEnabled && document.visibilityState === 'visible') playChime()
  }, [arrivalSeq, soundEnabled])

  // iOS Safari only unlocks an audio context created or resumed inside
  // a user gesture — a resume from the push effect above stays
  // suspended forever. The first pointerdown anywhere is that gesture
  // (PR #215 review).
  useEffect(() => {
    window.addEventListener('pointerdown', unlockChime, { once: true })
    return () => window.removeEventListener('pointerdown', unlockChime)
  }, [])

  // Only the newest sync may land. Two syncs can overlap (login racing
  // a connect, a write racing either), and a push starts one of its own,
  // so without this an older snapshot could overwrite a newer count —
  // including erasing a pushed increment (PR #199 review).
  const syncSeq = useRef(0)

  // Seeds/re-syncs the badge from the REST count (issue #66). Replaces
  // the count wholesale: the server stores a notification before
  // pushing it, so a snapshot taken after that store is authoritative
  // over local increments. Failure keeps the current count — same
  // softer-than-apiFetch stance as the missing-URL branch below.
  const refreshUnreadCount = useCallback(() => {
    if (!accessToken) return
    const seq = ++syncSeq.current
    getUnreadCount()
      .then((body) => {
        // a later sync started while this one was out: its snapshot is
        // the newer one, so this answer must not land
        if (seq !== syncSeq.current) return
        // treat a wrong-shaped body like a failed sync: a NaN badge is
        // worse than a briefly stale one
        if (typeof body?.count === 'number' && Number.isFinite(body.count)) {
          setSessionCount({ token: accessToken, count: body.count })
        } else {
          console.warn(
            'unread-count sync returned no count; keeping the current badge',
          )
        }
      })
      .catch((err) => {
        console.warn('unread-count sync failed; keeping the current badge', err)
      })
  }, [accessToken])

  // seed on login (REST works even when the push socket cannot)
  useEffect(() => {
    refreshUnreadCount()
  }, [refreshUnreadCount])

  // the session whose push channel gave up, stamped by the same
  // derive-by-token rule: a re-login is a new session and starts live.
  // An unset base URL needs no state — no socket is ever created, so it
  // is derived rather than written from the effect (which would cascade
  // a render).
  const [stoppedToken, setStoppedToken] = useState<string | null>(null)
  const pushStopped =
    accessToken !== null &&
    (serviceBaseUrls.notification === '' || stoppedToken === accessToken)

  useEffect(() => {
    if (!accessToken) return

    const base = serviceBaseUrls.notification
    if (base === '') {
      // softer than apiFetch's throw on purpose: a missing push channel
      // must not break every page the shell renders
      console.warn(
        'VITE_NOTIFICATION_SERVICE_URL is not set; notification push disabled',
      )
      // the bell reports this as a paused channel (derived above): no
      // socket will ever open, so it must not imply a live one
      return
    }

    // Created per effect run and deactivated in cleanup — safe under
    // StrictMode's double-invoked dev effects. Never hoist to module
    // scope.
    let errorRetries = 0
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let disposed = false
    // this client has been CONNECTED before, so the next onConnect is a
    // reconnect and may have missed frames
    let connected = false

    const client = new Client({
      brokerURL: brokerUrl(base),
      // the handshake is open; this CONNECT header is what the server
      // authenticates ("Session mechanics", docs/notification-service.md)
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      // doc'd reconnect policy: 1 s doubling to a 30 s cap
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      // match the server's 10 s / 10 s heartbeats
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      // subscribing inside onConnect re-subscribes on every reconnect
      onConnect: () => {
        // a session that reached CONNECTED earns a fresh budget: the
        // next ERROR is a new failure, not a continuation of this one
        errorRetries = 0
        // a reconnect may have missed frames; the REST count catches
        // the badge up (issue #66), and the pages re-read what they
        // show off pushReconnects (issue #233, PR #264 review)
        if (connected) setPushReconnects((n) => n + 1)
        connected = true
        refreshUnreadCount()
        client.subscribe('/user/queue/notifications', (message) => {
          // before the parse: like the count below, the chime must not
          // depend on the body parsing (PR #215 review)
          setArrivalSeq((n) => n + 1)
          setSessionCount((prev) => ({
            token: accessToken,
            count: (prev?.token === accessToken ? prev.count : 0) + 1,
          }))
          // The local bump above is instant feedback; this sync is the
          // authority. Its snapshot is taken after the server stored
          // and pushed this row, so it cannot undercount it — and it
          // supersedes any sync still in flight, which might (PR #199
          // review).
          refreshUnreadCount()
          // the body is the same NotificationDto the REST list returns;
          // the open Center prepends it. The count above never depends
          // on the body parsing (a malformed frame still counts).
          try {
            const dto = JSON.parse(message.body) as NotificationDto
            setPushed((prev) => ({
              token: accessToken,
              items: [
                ...(prev?.token === accessToken ? prev.items : []),
                dto,
              ].slice(-PUSH_BUFFER),
            }))
          } catch {
            console.warn('unparseable notification push frame ignored')
          }
        })
      },
      // The server sends an ERROR frame for any frame-handling failure;
      // with this client's traffic (CONNECT + one fixed subscribe) that
      // is in practice a CONNECT auth failure (invalid/expired/revoked
      // token). The SPA has no token refresh, so an expired session
      // cannot recover on its own: retry a bounded number of times for
      // the transient case, then stop and let the bell report it rather
      // than leaving a frozen count looking live (PR #185 re-review).
      // Transport drops (no ERROR frame) still auto-reconnect with the
      // policy above.
      onStompError: () => {
        // the server closes the socket after an ERROR; stop the client's
        // own reconnect, which would re-send the rejected CONNECT within
        // a second
        void client.deactivate()
        if (disposed) return
        if (errorRetries >= ERROR_RETRY_LIMIT) {
          setStoppedToken(accessToken)
          return
        }
        errorRetries += 1
        retryTimer = setTimeout(() => {
          if (!disposed) void client.activate()
        }, ERROR_RETRY_DELAY_MS)
      },
    })
    client.activate()

    return () => {
      disposed = true
      clearTimeout(retryTimer)
      void client.deactivate()
    }
  }, [accessToken, refreshUnreadCount])

  const value = useMemo(
    () => ({
      unreadCount,
      pushStopped,
      pushedNotifications,
      pushReconnects,
      refreshUnreadCount,
      soundEnabled,
      setSoundEnabled: setStoredSound,
    }),
    [
      unreadCount,
      pushStopped,
      pushedNotifications,
      pushReconnects,
      refreshUnreadCount,
      soundEnabled,
      setStoredSound,
    ],
  )

  return (
    <NotificationsContext.Provider value={value}>
      <Outlet />
    </NotificationsContext.Provider>
  )
}
