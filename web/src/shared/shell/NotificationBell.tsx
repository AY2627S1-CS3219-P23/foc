// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: placeholder notification bell + unread-count badge slot
// (wireframes show a bell with a red count badge).
// 2026-10-12, Claude Code (Fable 5), issue #67: badge wired to the
// STOMP push count from useNotifications (badge-only by the author's
// scope decision). PR #185 Copilot review: the count joins the
// button's accessible name — aria-label overrides descendant text, so
// screen readers never heard the badge.
// Reviewed by: Leong Wei Zhi (via pull request).

// TODO(notification-service owner): link the bell to the Notification
// Center (issue #66).
import { useNotifications } from '@/features/notifications/useNotifications'

export function NotificationBell() {
  const { unreadCount } = useNotifications()
  const capped = unreadCount > 99 ? '99+' : String(unreadCount)

  return (
    <button
      type="button"
      className="relative rounded-md p-2 hover:bg-gray-100"
      // aria-label overrides descendant text, so the badge must be part
      // of the name for assistive technologies (PR #185 Copilot review)
      aria-label={
        unreadCount > 0 ? `Notifications (${capped} unread)` : 'Notifications'
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
        <span className="absolute -top-0.5 -right-0.5 min-w-4 rounded-full bg-red-500 px-1 text-center text-[10px] leading-4 font-semibold text-white">
          {capped}
        </span>
      )}
    </button>
  )
}
