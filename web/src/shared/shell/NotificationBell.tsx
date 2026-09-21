// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: placeholder notification bell + unread-count badge slot
// (wireframes show a bell with a red count badge).
// Reviewed by: Leong Wei Zhi (via pull request).

// TODO(notification-service owner): wire the unread count / STOMP push
// and link the bell to the Notification Center.
export function NotificationBell() {
  return (
    <button
      type="button"
      className="relative rounded-md p-2 hover:bg-gray-100"
      aria-label="Notifications"
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
      {/* Unread-count badge slot — hidden until there is a live count */}
      <span className="absolute -top-0.5 -right-0.5 hidden min-w-4 rounded-full bg-red-500 px-1 text-center text-[10px] leading-4 font-semibold text-white" />
    </button>
  )
}
