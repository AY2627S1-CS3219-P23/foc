// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-10-12, issue #67.
// Scope: derives the STOMP broker URL from the service's HTTP base URL
// per the author's decisions (options Q&A): native WebSocket only — no
// sockjs-client — straight to the raw-WebSocket transport of the
// server's SockJS endpoint.
// Reviewed by: Leong Wei Zhi (via pull request).

// http://localhost:8090/api/notification
//   -> ws://localhost:8090/api/notification/ws/websocket
// The /ws/websocket suffix is the SockJS endpoint's raw-WebSocket
// transport (the same path the server's own integration test connects
// to), reached through the gateway's upgrade-enabled /api/notification
// route (docs/gateway.md).
export function brokerUrl(httpBase: string): string {
  const base = httpBase.replace(/\/+$/, '')
  return `${base.replace(/^http/, 'ws')}/ws/websocket`
}
