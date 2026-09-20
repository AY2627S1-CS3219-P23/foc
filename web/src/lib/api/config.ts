// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-20, issue #108.
// Scope: per-service base URLs (no API gateway — the browser talks to
// each backend directly; see root AGENTS.md).
// Reviewed by: Leong Wei Zhi (via pull request).

// Values are browser-visible URLs (host-published ports), inlined by
// Vite at build time from VITE_* env vars. Empty string = the owning
// service has not claimed a host port yet (see root AGENTS.md).
export const serviceBaseUrls = {
  user: import.meta.env.VITE_USER_SERVICE_URL ?? '',
  supplier: import.meta.env.VITE_SUPPLIER_SERVICE_URL ?? '',
  order: import.meta.env.VITE_ORDER_SERVICE_URL ?? '',
  credit: import.meta.env.VITE_CREDIT_SERVICE_URL ?? '',
  notification: import.meta.env.VITE_NOTIFICATION_SERVICE_URL ?? '',
} as const

export type ServiceName = keyof typeof serviceBaseUrls
