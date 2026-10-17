// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: shared fetch wrapper all domains use for REST calls.
// 2026-09-29, Claude Code (Fable 5), issue #112: errorMessage hoisted
// here from its three per-page copies (login, register, UsersSection).
// Reviewed by: Leong Wei Zhi (via pull request).
// 2026-09-29, Claude Code (Opus 5.5), issue #147: errorMessage added, the
// one error-to-text helper for every feature (was copied in the admin
// Users section, login, register and the suppliers page).
// 2026-09-30, Claude Code (Opus 5), issue #109 (PR #150): ApiError carries
// Retry-After, so a 429 can drive a countdown (the OTP resend cooldown;
// login's lockout could use it next). It only arrives cross-origin because
// user-service names the header in its CORS exposedHeaders — it is not
// CORS-safelisted, so exposing it there and reading it here go together.
// 2026-10-11, Claude Code (Fable 5), issue #94: a 202 may carry no body —
// POST /auth/password-reset answers an empty 202 on purpose (nothing to
// say that wouldn't reveal account existence) — so its parse failure is
// an undefined result, not an error. Sign-up's 202 still carries its
// body, which is why 202 can't share 204's skip-the-parse branch.
// 2026-10-12, Claude Code (Fable 5), issue #178: a 401 on a request that
// carried the bearer token invokes a swappable unauthorized handler
// (AuthProvider signs the session out) — owner decision via options Q&A:
// any token-bearing 401, no matching on the problem detail. Requests
// without the token (login itself) keep their inline errors.
// PR #182 Copilot review: `auth: false` opts a call out of the token
// injection — login uses it, so a stale stored session no longer rides
// along and its wrong-credentials 401 can't read as a revoked session.
// PR #182 review (@Sinnez1): the handler is given the token the refused
// request carried, so AuthProvider can ignore a 401 that answers an
// older token instead of ending whichever session is live now.
// 2026-10-14, Claude Code (Opus 5), PR #182 re-review (@Sinnez1): the
// bearer scheme is matched case-insensitively, as the gateway matches
// it, and a handler that throws no longer replaces the ApiError callers
// match on (err.status === 401, errorMessage()).
// 2026-10-16, Claude Code (Opus 5), PR #180 main sync: the #94 202 note
// above predates #182's block below it — both stand, the 202 branch
// sits after 204's and the reset calls now pass `auth: false`.
// Author review: Leong Wei Zhi to review via the PR.
// 2026-10-17, Codex (GPT-6), PR #180 review, issue #94:
// AI-generated (edited by Codex; Leong Wei Zhi review pending).
// Only an empty 202 body resolves undefined; malformed JSON still rejects.
// Author review: Leong Wei Zhi to review via PR #180.

import { serviceBaseUrls, type ServiceName } from './config'

// Swappable token source. The auth owner (user-service domain) replaces
// this from src/shared/auth/ once session handling exists.
let getToken: () => string | null = () => null

export function setTokenSource(source: () => string | null) {
  getToken = source
}

// Called when a request that carried the bearer token is answered 401 —
// the server no longer honours the session (token revoked, expired, or
// invalid), so holding on to it client-side only leaves broken pages.
// The auth owner (AuthProvider) replaces this with a forced sign-out.
// It is handed the token the refused request actually carried, so a
// reply arriving late for an older token can be told apart from one
// refusing the session that is live now.
let onUnauthorized: (token: string) => void = () => {}

export function setUnauthorizedHandler(handler: (token: string) => void) {
  onUnauthorized = handler
}

// Shape of an RFC 9457 application/problem+json error body, which the
// backends use for denial/error responses.
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null
  // seconds to wait, from the response's Retry-After; null when the
  // response carried none (or an HTTP-date, which nothing here sends)
  readonly retryAfter: number | null

  constructor(
    status: number,
    problem: ProblemDetail | null,
    retryAfter: number | null = null,
  ) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.retryAfter = retryAfter
  }
}

// Retry-After as whole seconds, or null if absent/not a number.
function retryAfterSeconds(res: Response): number | null {
  const header = res.headers?.get('Retry-After')
  if (!header) return null
  const seconds = Number(header)
  return Number.isFinite(seconds) && seconds >= 0 ? Math.ceil(seconds) : null
}

// Text to show for an apiFetch failure: the server's problem+json reason
// (ApiError), or apiFetch's own error (e.g. a missing base URL). A
// TypeError is fetch's network failure, whose message ("Failed to fetch")
// means nothing to users, so it gets the fallback.
export function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error && !(err instanceof TypeError) && err.message) {
    return err.message
  }
  return fallback
}

// RequestInit plus `auth: false` to leave the bearer token off. The
// credential endpoints want this: a stored (possibly stale) session
// must not ride along on POST /auth/login, where a 401 means wrong
// credentials — an inline form error, never a revoked session.
export interface ApiFetchInit extends RequestInit {
  auth?: boolean
}

export async function apiFetch<T>(
  service: ServiceName,
  path: string,
  init?: ApiFetchInit,
): Promise<T> {
  const baseUrl = serviceBaseUrls[service]
  if (!baseUrl) {
    throw new Error(
      `No base URL configured for the ${service} service — set VITE_${service.toUpperCase()}_SERVICE_URL`,
    )
  }

  // auth stripped so fetch sees a plain RequestInit
  const { auth, ...request } = init ?? {}

  // Headers() handles every RequestInit.headers form (plain object,
  // Headers instance, or entries array); caller-supplied values win.
  const headers = new Headers(request.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (request.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth !== false) {
    const token = getToken()
    if (token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  // the bearer token this request goes out with, read back off the
  // headers so a caller-supplied Authorization counts the same as an
  // injected one. Kept for the 401 below: the handler needs to know
  // which token was refused, not just that one was sent. The scheme is
  // matched case-insensitively, as the gateway matches it — a header
  // the gateway accepts and can answer 401 must reach the handler.
  const sentToken =
    /^bearer\s+(.+)$/i.exec(headers.get('Authorization') ?? '')?.[1] ?? null

  const res = await fetch(`${baseUrl}${path}`, { ...request, headers })

  if (!res.ok) {
    const problem = (await res.json().catch(() => null)) as ProblemDetail | null
    const error = new ApiError(res.status, problem, retryAfterSeconds(res))
    // a 401 despite a token means the session is dead (revoked by an
    // admin action or password reset, expired, or malformed) — tell the
    // auth owner before throwing, so the user is signed out instead of
    // stranded on a page whose every call fails (issue #178). Whatever
    // the handler does is its own business: callers were promised an
    // ApiError for a failed response, so its failure is logged, not
    // raised in place of one.
    if (res.status === 401 && sentToken) {
      try {
        onUnauthorized(sentToken)
      } catch (err) {
        console.error('unauthorized handler failed', err)
      }
    }
    throw error
  }
  if (res.status === 204) {
    return undefined as T
  }
  if (res.status === 202) {
    // some 202s carry a body (sign-up's timings), some none at all
    // (password reset, which answers every caller identically)
    const body = await res.text()
    return (body === '' ? undefined : JSON.parse(body)) as T
  }
  return (await res.json()) as T
}
