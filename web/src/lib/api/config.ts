// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: per-service base URLs.
// 2026-10-11, Claude Code (Fable 5), issue #151: comments updated for the
// API-gateway cutover (team decision) — the values now point at the
// gateway with a per-service path prefix; the mechanism is unchanged.
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-10-31, Claude Code (Opus 5.5), N6 chat: the chat-service base URL.
// Author review: Ko-Khan, pending pull request review.

// Values are browser-visible URLs, inlined by Vite at build time from
// VITE_* env vars. Since the gateway cutover (issue #151) each one is
// the gateway origin plus the service's path prefix, e.g.
// http://localhost:8090/api/user (see .env.example and docs/gateway.md).
// Empty string = the service has no route yet (see root AGENTS.md).
export const serviceBaseUrls = {
  user: import.meta.env.VITE_USER_SERVICE_URL ?? '',
  supplier: import.meta.env.VITE_SUPPLIER_SERVICE_URL ?? '',
  order: import.meta.env.VITE_ORDER_SERVICE_URL ?? '',
  credit: import.meta.env.VITE_CREDIT_SERVICE_URL ?? '',
  notification: import.meta.env.VITE_NOTIFICATION_SERVICE_URL ?? '',
  chat: import.meta.env.VITE_CHAT_SERVICE_URL ?? '',
} as const

export type ServiceName = keyof typeof serviceBaseUrls
