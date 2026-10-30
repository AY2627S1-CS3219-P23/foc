// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-17, issue #66.
// Scope: the Notification Center page per
// web/docs/wireframes/notification-center.png (heading + subtitle,
// "Mark all as read", rows with unread dot / icon tile / bold title /
// relative time, grayed read rows, dashed empty state). Interactions
// are the author's decisions via options Q&A: clicking an unread row
// marks it read (no mark-unread control — the API supports it, the UI
// doesn't surface it), an All/Unread filter toggle, a "Load more"
// append button for paging, pushed frames prepend live, and the badge
// re-fetches the unread count after every successful write. The two
// controls absent from the wireframe (toggle, Load more) are recorded
// as deviations in web/AGENTS.md. Loading/error/banner styling follows
// routes/suppliers.tsx.
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-17, Claude Code (Opus 5), PR #199 review (@claude, @codex):
// a failed "Load more" is retryable instead of skipping its page; the
// filter switch clears the page count too; a page-0 answer no longer
// discards frames pushed while it was in flight; under the Unread
// filter a row that is marked read leaves the list (author decision via
// options Q&A, superseding the gray-in-place behaviour there only);
// "Mark all as read" only touches rows that existed when it started;
// the empty state is hidden behind an error; the bulk control falls
// back to the loaded rows when the best-effort badge is unavailable;
// pushes are consumed from the provider's queue; and the relative times
// tick while the page sits open.
// 2026-10-17, same tool, driving the branch in a browser: "Mark all as
// read" left the Unread view's page count behind, so the empty state
// rendered with a "Load more" under it.
// 2026-10-17, same tool, PR #199 review round 3 (@Sinnez1): the rows
// held and the total they are measured against now move together — a
// pushed row raises the total, and a row read out of the Unread view
// lowers it — so the pager neither disappears with older rows still
// unloaded nor offers a page that is already on screen.
// 2026-10-19, Claude Code (Fable 5), issue #221: courier names hydrate
// at read time per the author's D32 decision (options Q&A, supersedes
// D30/D31) — one useUsernames call over the held rows covers REST pages
// and pushed frames alike; a row renders actor-less copy until (and
// unless) its courierId resolves.
// 2026-10-19, Claude Code (Fable 5), PR #222 review (@Sinnez1): the id
// array passed to useUsernames is memoized over the held rows — a fresh
// array every render (clock ticks, pushes, filter toggles) defeated the
// hook's own memo and re-ran its dedupe/sort/join each time.
// 2026-10-27, Claude Code (Fable 5), issue #232: clicking a row that
// references a request navigates to /deliveries/{requestId} (author
// decisions via options Q&A: always the delivery-detail page; an unread
// row is also marked read, fire-and-forget, before navigating; read
// rows with a request become clickable; rows without a request keep the
// mark-read-only behavior).
// 2026-10-27, same tool, PR #263 review (@claude): the payload's id is
// URL-encoded before it is interpolated into the navigate path.
// 2026-10-30, Claude Code (Fable 5), issue #255: the hydrated id list
// goes through actorIdOf (raterId ?? courierId), so a rating.submitted
// row names its rater the same way request rows name their courier
// (D32). Author review: Leong Wei Zhi, pending pull request review.

import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'

import { listNotifications, markAllRead, setNotificationRead } from './api'
import {
  actorIdOf,
  notificationMessage,
  relativeTime,
  requestIdOf,
} from './eventMessage'
import { useNotifications } from './useNotifications'
import type { NotificationDto } from './types'
import { useUsernames } from '@/features/user/usernames'
import { errorMessage } from '@/lib/api/http'

const PAGE_SIZE = 10

// "just now" / "10 min ago" are computed at render, and an open page
// has no other reason to re-render: tick a shared clock at the smallest
// unit the labels show so they cannot sit frozen (PR #199 review).
const CLOCK_TICK_MS = 60_000

type Filter = 'all' | 'unread'

interface ListRequest {
  page: number
  append: boolean
  // bumped to re-run the effect when `page` does not change
  attempt: number
}

const FIRST_PAGE: ListRequest = { page: 0, append: false, attempt: 0 }

// re-read the current filter's first page, replacing what is held
const refill = (r: ListRequest): ListRequest => ({
  page: 0,
  append: false,
  attempt: r.attempt + 1,
})

export function NotificationCenter() {
  const {
    unreadCount,
    pushedNotifications,
    refreshUnreadCount,
    soundEnabled,
    setSoundEnabled,
  } = useNotifications()

  const navigate = useNavigate()

  const [items, setItems] = useState<NotificationDto[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  // What the effect should fetch, as one value so a page number and the
  // merge it implies can never disagree. `append` false replaces the
  // list (first load, filter switch); true adds to it ("Load more").
  // `attempt` is what makes the effect re-run when the page number does
  // not change, so a failed page can be retried rather than skipped
  // (PR #199 review).
  const [request, setRequest] = useState<ListRequest>(FIRST_PAGE)
  // true once a fetch has answered for the current filter
  const [loaded, setLoaded] = useState(false)
  // of the current filter, from the last answer — not `totalPages`: the
  // unread set shrinks as rows are read, so a page count computed
  // against an older, larger set is the thing that goes stale
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [marking, setMarking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Switching the filter restarts the list from page 0 — reset during
  // render (the suppliers page's prop-change pattern) so a stale page
  // of the other filter never flashes. The page count goes with it: left
  // behind, a failed page-0 fetch for the new filter would leave
  // `hasMore` true against the old filter's total and let "Load more"
  // skip the new filter's first page (PR #199 review).
  const [prevFilter, setPrevFilter] = useState<Filter>(filter)
  const switching = filter !== prevFilter
  if (switching) {
    setPrevFilter(filter)
    setItems([])
    setRequest(FIRST_PAGE)
    setLoaded(false)
    setTotalElements(0)
  }

  // the clock the relative labels read, ticking while the page is open
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), CLOCK_TICK_MS)
    return () => clearInterval(timer)
  }, [])

  // One effect owns every list fetch. Both merges dedupe by id — a
  // pushed row prepended below may already be in what the server
  // returns, and an adjusted offset can re-read rows already held.
  useEffect(() => {
    let cancelled = false
    const { page, append } = request

    async function load() {
      if (append) setLoadingMore(true)
      else setLoading(true)
      setError(null)
      try {
        const result = await listNotifications({
          page,
          size: PAGE_SIZE,
          unread: filter === 'unread',
        })
        if (cancelled) return
        setTotalElements(result.totalElements)
        setLoaded(true)
        setItems((prev) => {
          if (!append) {
            // Rows pushed while this request was out are newer than the
            // server's snapshot, so they stay on top rather than being
            // replaced away (PR #199 review). Only pushes can be here:
            // the reset above empties the list before this runs, on a
            // filter switch as on the first load.
            const returned = new Set(result.content.map((n) => n.id))
            const pushedSince = prev.filter((n) => !returned.has(n.id))
            return [...pushedSince, ...result.content]
          }
          const seen = new Set(prev.map((n) => n.id))
          return [...prev, ...result.content.filter((n) => !seen.has(n.id))]
        })
      } catch (err) {
        if (cancelled) return
        setError(errorMessage(err, 'Could not load notifications. Try again.'))
      } finally {
        if (!cancelled) {
          setLoading(false)
          setLoadingMore(false)
        }
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [request, filter])

  // Frames pushed while this page is open join the top of the list (the
  // newest stored row is exactly where D25's order puts it). Render-phase
  // adjust-on-prop-change, the suppliers page's pattern, rather than an
  // effect. The provider hands over a queue, so a burst delivered inside
  // one render arrives whole; `consumed` starts at whatever had already
  // been pushed before mount, since those rows belong wherever the
  // server's own ordering puts them, not on top. Dedupe by id: the same
  // row may already be here from a fetch that raced the push.
  const [consumed, setConsumed] =
    useState<NotificationDto[]>(pushedNotifications)
  if (pushedNotifications !== consumed) {
    const fresh = pushedNotifications.filter((n) => !consumed.includes(n))
    setConsumed(pushedNotifications)
    // what the list holds once this render commits: a filter switch
    // above has already emptied it
    const held = switching ? [] : items
    const seen = new Set(held.map((n) => n.id))
    // newest first, matching the list's order
    const add = fresh.filter((n) => !seen.has(n.id)).reverse()
    if (add.length > 0) {
      setItems((prev) => [...add, ...prev])
      // Each of these is a new member of the server's set, which the
      // last answer's total predates. Left alone, the rows held catch
      // up to a stale total and the pager disappears while older rows
      // are still unloaded (PR #199 review).
      setTotalElements((t) => t + add.length)
    }
  }

  async function handleRowClick(notification: NotificationDto) {
    const requestId = requestIdOf(notification.payload)
    if (requestId !== null) {
      if (!notification.read && !marking) {
        // Fire-and-forget: navigation unmounts this page, so no list
        // surgery — a failure just leaves the row unread server-side.
        // The provider outlives the route, so the badge refresh lands.
        // During mark-all (`marking`) the bulk call covers this row, so
        // the per-row PATCH is skipped but the click still navigates
        // (PR #263 review).
        setNotificationRead(notification.id, true)
          .then(() => refreshUnreadCount())
          .catch(() => {})
      }
      // the id is an arbitrary payload string — encoded so a `/`, `?`
      // or `#` in it cannot route somewhere unintended (PR #263 review)
      navigate(`/deliveries/${encodeURIComponent(requestId)}`)
      return
    }
    if (notification.read || marking) return
    try {
      await setNotificationRead(notification.id, true)
      setItems((prev) =>
        // The Unread view must not show read rows, so there the row
        // leaves the list instead of graying in place (author decision
        // via options Q&A, PR #199 review).
        filter === 'unread'
          ? prev.filter((n) => n.id !== notification.id)
          : prev.map((n) =>
              n.id === notification.id ? { ...n, read: true } : n,
            ),
      )
      // It left the unread set on the server as well, so the total
      // comes down with it — otherwise the rows held fall one short of
      // a total nothing will reach and the pager offers a page that is
      // already on screen.
      if (filter === 'unread') setTotalElements((t) => Math.max(0, t - 1))
      setError(null)
      // server-authoritative badge (options Q&A decision): re-fetch
      // rather than decrement locally
      refreshUnreadCount()
    } catch (err) {
      setError(errorMessage(err, 'Could not mark the notification read.'))
    }
  }

  async function handleMarkAllRead() {
    setMarking(true)
    // Only the rows this request covers. A frame pushed after the server
    // ran the bulk update is still unread there, so marking it read here
    // would leave the badge counting an unread row the list shows as
    // inert — with no way to click it (PR #199 review).
    const covered = new Set(items.map((n) => n.id))
    try {
      await markAllRead()
      setItems((prev) =>
        filter === 'unread'
          ? prev.filter((n) => !covered.has(n.id))
          : prev.map((n) =>
              n.read || !covered.has(n.id) ? n : { ...n, read: true },
            ),
      )
      if (filter === 'unread') {
        // Every row this covered has left the unread set, so the view's
        // page-0 slice is whatever is left: ask the server rather than
        // reason about it. Without this the totals from the last fetch
        // outlive the rows they described and the empty state renders
        // with a "Load more" under it (browser run).
        setRequest(refill)
      }
      setError(null)
      refreshUnreadCount()
    } catch (err) {
      setError(errorMessage(err, 'Could not mark all notifications read.'))
    } finally {
      setMarking(false)
    }
  }

  // Against `totalElements`, not a page count: under the Unread filter
  // the set shrinks as rows are read, so "is there more" has to be asked
  // of how many rows exist now versus how many are held (PR #199
  // review). `loaded` keeps the pager off screen until a fetch answers.
  const hasMore = loaded && items.length < totalElements
  // The offset to read from, derived from the rows held rather than
  // from the last page number. Under the Unread filter the set shrinks
  // as rows are read, so `loaded + 1` would step past the rows that
  // moved up into the slice just vacated and lose them (PR #199
  // review). The rows held are a prefix of the server's current set —
  // a pushed row is its newest member, and a row marked read has left
  // it — so the next unread row sits at exactly that offset. Re-read
  // overlap is deduped by the append merge.
  const nextPage = (r: ListRequest): ListRequest => ({
    page: Math.floor(items.length / PAGE_SIZE),
    append: true,
    attempt: r.attempt + 1,
  })
  // The badge is best-effort — the provider swallows a failed count sync
  // and keeps the current value, which is 0 before the first one lands.
  // An unread row on screen is proof enough that there is something to
  // mark, so it must not be gated on the badge alone (PR #199 review).
  const canMarkAll = unreadCount > 0 || items.some((n) => !n.read)

  // Read-time hydration (D32): resolve the actors (courier or rater)
  // named by the held rows — REST pages and pushed frames alike converge
  // on `items`. Memoized over the rows so the hook's own memo on the
  // array can hit; a fresh array every render (clock tick, filter
  // toggle) would re-run its dedupe/sort/join for nothing (PR #222
  // review).
  const actorIds = useMemo(
    () => items.map((n) => actorIdOf(n.payload)),
    [items],
  )
  const names = useUsernames(actorIds)

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Notifications</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage your courier transactions and credit activities
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* sound toggle (beyond the wireframe, like the filter below).
              Fixed label + aria-pressed carries on/off to AT, matching
              the filter buttons' pattern. */}
          <button
            type="button"
            onClick={() => setSoundEnabled(!soundEnabled)}
            aria-pressed={soundEnabled}
            aria-label="Notification sound"
            title={
              soundEnabled ? 'Notification sound on' : 'Notification sound off'
            }
            className="rounded-md border border-gray-300 p-2 text-gray-700 hover:bg-gray-50"
          >
            <svg
              className="h-5 w-5"
              viewBox="0 0 20 20"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M3.5 7.5h2L10 4v12l-4.5-3.5h-2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1Z" />
              {soundEnabled ? (
                <path d="M13 7.5a3.5 3.5 0 0 1 0 5M15 5.5a6 6 0 0 1 0 9" />
              ) : (
                <path d="M13 8l4 4m0-4l-4 4" />
              )}
            </svg>
          </button>
          <button
            type="button"
            onClick={handleMarkAllRead}
            disabled={marking || !canMarkAll}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Mark all as read
          </button>
        </div>
      </div>

      {/* All/Unread filter (options Q&A decision; not in the wireframe) */}
      <div
        role="group"
        aria-label="Filter notifications"
        className="inline-flex rounded-md border border-gray-300 text-sm"
      >
        {(['all', 'unread'] as const).map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => setFilter(value)}
            aria-pressed={filter === value}
            className={`px-4 py-1.5 first:rounded-l-md last:rounded-r-md ${
              filter === value
                ? 'bg-gray-900 font-medium text-white'
                : 'text-gray-600 hover:bg-gray-50'
            }`}
          >
            {value === 'all' ? 'All' : 'Unread'}
          </button>
        ))}
      </div>

      {error && (
        <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {loading && (
        <p className="text-sm text-gray-500">Loading notifications...</p>
      )}

      {/* An empty list only means "all caught up" when the load worked;
          behind an error banner it would be a reassuring lie (PR #199
          review). */}
      {!loading && items.length === 0 && !error && (
        <div className="rounded-lg border-2 border-dashed border-gray-200 bg-white py-12 text-center">
          <p className="font-medium text-gray-900">No new notifications</p>
          <p className="mt-1 text-sm text-gray-500">
            You&apos;re all caught up for today.
          </p>
        </div>
      )}

      {!loading && items.length > 0 && (
        <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
          {items.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              names={names}
              now={now}
              onClick={() => handleRowClick(notification)}
            />
          ))}
        </ul>
      )}

      {!loading && hasMore && (
        <div className="text-center">
          <button
            type="button"
            onClick={() => setRequest(nextPage)}
            disabled={loadingMore}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {loadingMore ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  )
}

function NotificationRow({
  notification,
  names,
  now,
  onClick,
}: {
  notification: NotificationDto
  names: ReadonlyMap<string, string>
  now: Date
  onClick: () => void
}) {
  const { icon, title } = notificationMessage(notification, names)
  const time = relativeTime(notification.occurredAt, now)
  const requestId = requestIdOf(notification.payload)

  // A row that references a request navigates to it, read or not (an
  // unread one is also marked read on the way). A row without a request
  // keeps the original behavior: unread marks read, read is inert —
  // there is deliberately no mark-unread control (options Q&A).
  const label =
    requestId !== null
      ? notification.read
        ? `${title} — select to open the request`
        : `${title} — unread, select to open the request`
      : notification.read
        ? title
        : `${title} — unread, select to mark read`
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        disabled={notification.read && requestId === null}
        aria-label={label}
        className="flex w-full items-center gap-3 px-4 py-3 text-left enabled:cursor-pointer enabled:hover:bg-gray-50"
      >
        <span
          aria-hidden="true"
          className={`h-2 w-2 shrink-0 rounded-full ${
            notification.read ? 'bg-transparent' : 'bg-blue-500'
          }`}
        />
        <span
          aria-hidden="true"
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md border text-base ${
            notification.read
              ? 'border-gray-100 bg-gray-50 opacity-50'
              : 'border-gray-200 bg-white'
          }`}
        >
          {icon}
        </span>
        <span className="min-w-0">
          <span
            className={`block truncate text-sm font-semibold ${
              notification.read ? 'text-gray-400' : 'text-gray-900'
            }`}
          >
            {title}
          </span>
          <span
            className={`block text-xs ${
              notification.read ? 'text-gray-300' : 'text-gray-500'
            }`}
          >
            {time}
          </span>
        </span>
      </button>
    </li>
  )
}
