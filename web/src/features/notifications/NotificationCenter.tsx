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

import { useEffect, useState } from 'react'

import { listNotifications, markAllRead, setNotificationRead } from './api'
import { notificationMessage, relativeTime } from './eventMessage'
import { useNotifications } from './useNotifications'
import type { NotificationDto } from './types'
import { errorMessage } from '@/lib/api/http'

const PAGE_SIZE = 10

// "just now" / "10 min ago" are computed at render, and an open page
// has no other reason to re-render: tick a shared clock at the smallest
// unit the labels show so they cannot sit frozen (PR #199 review).
const CLOCK_TICK_MS = 60_000

type Filter = 'all' | 'unread'

export function NotificationCenter() {
  const { unreadCount, pushedNotifications, refreshUnreadCount } =
    useNotifications()

  const [items, setItems] = useState<NotificationDto[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  // `page` is the page the effect requests; `loaded` the newest page a
  // fetch actually returned (-1 before the first one lands). They part
  // company while a page is in flight or after one failed, which is what
  // makes a failed page retryable rather than skipped: paging off
  // `loaded` asks for the same page again, and `attempt` is what makes
  // the effect re-run when the page number does not change (PR #199
  // review).
  const [page, setPage] = useState(0)
  const [loaded, setLoaded] = useState(-1)
  const [attempt, setAttempt] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
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
  if (filter !== prevFilter) {
    setPrevFilter(filter)
    setItems([])
    setPage(0)
    setLoaded(-1)
    setTotalPages(0)
  }

  // the clock the relative labels read, ticking while the page is open
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), CLOCK_TICK_MS)
    return () => clearInterval(timer)
  }, [])

  // One effect owns every list fetch: page 0 replaces (initial load and
  // filter switches), later pages append ("Load more"). Both merges
  // dedupe by id — a pushed row prepended below may already be in what
  // the server returns.
  useEffect(() => {
    let cancelled = false

    async function load() {
      if (page === 0) setLoading(true)
      else setLoadingMore(true)
      setError(null)
      try {
        const result = await listNotifications({
          page,
          size: PAGE_SIZE,
          unread: filter === 'unread',
        })
        if (cancelled) return
        setTotalPages(result.totalPages)
        setLoaded(page)
        setItems((prev) => {
          if (page === 0) {
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
  }, [page, attempt, filter])

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
    if (fresh.length > 0) {
      setItems((prev) => {
        const seen = new Set(prev.map((n) => n.id))
        // newest first, matching the list's order
        const add = fresh.filter((n) => !seen.has(n.id)).reverse()
        return add.length > 0 ? [...add, ...prev] : prev
      })
    }
  }

  async function handleRowClick(notification: NotificationDto) {
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
        // The write covered every unread row the caller had, so the
        // Unread view has no further pages. Without this the page count
        // from the last fetch outlives the rows it described and the
        // empty state renders with a "Load more" under it (browser run).
        setTotalPages(0)
      }
      setError(null)
      refreshUnreadCount()
    } catch (err) {
      setError(errorMessage(err, 'Could not mark all notifications read.'))
    } finally {
      setMarking(false)
    }
  }

  // off `loaded`, not `page`: after a failed page the button has to come
  // back pointing at that same page (PR #199 review). `items.length`
  // guards the other direction: a write that empties the list locally
  // must not leave a pager offering more of nothing (browser run).
  const hasMore = loaded >= 0 && loaded + 1 < totalPages && items.length > 0
  // The badge is best-effort — the provider swallows a failed count sync
  // and keeps the current value, which is 0 before the first one lands.
  // An unread row on screen is proof enough that there is something to
  // mark, so it must not be gated on the badge alone (PR #199 review).
  const canMarkAll = unreadCount > 0 || items.some((n) => !n.read)

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Notifications</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage your courier transactions and credit activities
          </p>
        </div>
        <button
          type="button"
          onClick={handleMarkAllRead}
          disabled={marking || !canMarkAll}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Mark all as read
        </button>
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
            onClick={() => {
              setPage(loaded + 1)
              setAttempt((a) => a + 1)
            }}
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
  now,
  onClick,
}: {
  notification: NotificationDto
  now: Date
  onClick: () => void
}) {
  const { icon, title } = notificationMessage(notification)
  const time = relativeTime(notification.occurredAt, now)

  // An unread row is a button (clicking marks it read); a read row is
  // inert — there is deliberately no mark-unread control (options Q&A).
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        disabled={notification.read}
        aria-label={
          notification.read ? title : `${title} — unread, select to mark read`
        }
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
