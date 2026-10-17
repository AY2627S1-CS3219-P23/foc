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

import { useEffect, useState } from 'react'

import { listNotifications, markAllRead, setNotificationRead } from './api'
import { notificationMessage, relativeTime } from './eventMessage'
import { useNotifications } from './useNotifications'
import type { NotificationDto } from './types'
import { errorMessage } from '@/lib/api/http'

const PAGE_SIZE = 10

type Filter = 'all' | 'unread'

export function NotificationCenter() {
  const { unreadCount, latestNotification, refreshUnreadCount } =
    useNotifications()

  const [items, setItems] = useState<NotificationDto[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [marking, setMarking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Switching the filter restarts the list from page 0 — reset during
  // render (the suppliers page's prop-change pattern) so a stale page
  // of the other filter never flashes.
  const [prevFilter, setPrevFilter] = useState<Filter>(filter)
  if (filter !== prevFilter) {
    setPrevFilter(filter)
    setItems([])
    setPage(0)
  }

  // One effect owns every list fetch: page 0 replaces (initial load and
  // filter switches), later pages append ("Load more"). Appends dedupe
  // by id — a pushed row prepended below may already be in the next
  // page the server returns.
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
        setItems((prev) => {
          if (page === 0) return result.content
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
  }, [page, filter])

  // A frame pushed while the page is open joins the top of the list
  // (it is the newest stored row, which is exactly where D25's order
  // puts it). Render-phase adjust-on-prop-change, the suppliers page's
  // pattern, rather than an effect. Dedupe by id: the same row may
  // already be here from a fetch that raced the push.
  const [prevPushed, setPrevPushed] = useState<NotificationDto | null>(null)
  if (latestNotification !== prevPushed) {
    setPrevPushed(latestNotification)
    if (
      latestNotification &&
      !items.some((n) => n.id === latestNotification.id)
    ) {
      setItems((prev) =>
        prev.some((n) => n.id === latestNotification.id)
          ? prev
          : [latestNotification, ...prev],
      )
    }
  }

  async function handleRowClick(notification: NotificationDto) {
    if (notification.read || marking) return
    try {
      await setNotificationRead(notification.id, true)
      setItems((prev) =>
        prev.map((n) => (n.id === notification.id ? { ...n, read: true } : n)),
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
    try {
      await markAllRead()
      setItems((prev) => prev.map((n) => (n.read ? n : { ...n, read: true })))
      setError(null)
      refreshUnreadCount()
    } catch (err) {
      setError(errorMessage(err, 'Could not mark all notifications read.'))
    } finally {
      setMarking(false)
    }
  }

  const hasMore = page + 1 < totalPages

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
          disabled={marking || unreadCount === 0}
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

      {loading ? (
        <p className="text-sm text-gray-500">Loading notifications...</p>
      ) : items.length === 0 ? (
        <div className="rounded-lg border-2 border-dashed border-gray-200 bg-white py-12 text-center">
          <p className="font-medium text-gray-900">No new notifications</p>
          <p className="mt-1 text-sm text-gray-500">
            You&apos;re all caught up for today.
          </p>
        </div>
      ) : (
        <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
          {items.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              onClick={() => handleRowClick(notification)}
            />
          ))}
        </ul>
      )}

      {!loading && hasMore && (
        <div className="text-center">
          <button
            type="button"
            onClick={() => setPage((p) => p + 1)}
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
  onClick,
}: {
  notification: NotificationDto
  onClick: () => void
}) {
  const { icon, title } = notificationMessage(notification)
  const time = relativeTime(notification.occurredAt)

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
