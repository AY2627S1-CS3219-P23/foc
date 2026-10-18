// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: placeholder notification bell + unread-count badge slot
// (wireframes show a bell with a red count badge).
// 2026-10-12, Claude Code (Fable 5), issue #67: badge wired to the
// STOMP push count from useNotifications (badge-only by the author's
// scope decision). PR #185 Copilot review: the count joins the
// button's accessible name — aria-label overrides descendant text, so
// screen readers never heard the badge.
// 2026-10-16, Claude Code (Opus 5), PR #185 re-review (@Sinnez1): a
// stopped push channel is reported instead of leaving the last count
// looking live — the badge greys out and the accessible name says
// updates are paused.
// 2026-10-17, Claude Code (Fable 5), issue #66: the bell is now a Link
// to the Notification Center at /notifications (entry stays via the
// bell only, per the wireframe — no nav/tab entry added).
// Reviewed by: Leong Wei Zhi (via pull request).

import { Link } from 'react-router'
import { useNotifications } from '@/features/notifications/useNotifications'

export function NotificationBell() {
  const { unreadCount, pushStopped } = useNotifications()
  const capped = unreadCount > 99 ? '99+' : String(unreadCount)

  // The count is kept, not hidden: those notifications really did
  // arrive. What changes is the claim — once push has stopped it is the
  // last known count, not the current one, so say so in the name a
  // screen reader hears and drop the red that reads as "live"
  // (PR #185 re-review).
  const name =
    unreadCount > 0 ? `Notifications (${capped} unread)` : 'Notifications'

  return (
    <Link
      to="/notifications"
      className="relative rounded-md p-2 hover:bg-gray-100"
      // aria-label overrides descendant text, so the badge must be part
      // of the name for assistive technologies (PR #185 Copilot review)
      aria-label={
        pushStopped ? `${name} — updates paused, sign in again` : name
      }
      title={
        pushStopped
          ? 'Notification updates paused — sign in again to resume'
          : undefined
      }
    >
      <svg
        className="h-5 w-5"
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        aria-hidden="true"
      >
        <path d="M10 3a4.5 4.5 0 0 0-4.5 4.5c0 3-1 4.5-2 5.5h13c-1-1-2-2.5-2-5.5A4.5 4.5 0 0 0 10 3Z" />
        <path d="M8.5 15.5a1.5 1.5 0 0 0 3 0" />
      </svg>
      {unreadCount > 0 && (
        <span
          className={`absolute -top-0.5 -right-0.5 min-w-4 rounded-full px-1 text-center text-[10px] leading-4 font-semibold text-white ${
            pushStopped ? 'bg-gray-400' : 'bg-red-500'
          }`}
        >
          {capped}
        </span>
      )}
    </Link>
  )
}
