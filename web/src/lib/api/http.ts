// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
// Scope: shared fetch wrapper all domains use for REST calls.
// Reviewed by: Leong Wei Zhi (via pull request).

import { serviceBaseUrls, type ServiceName } from './config'

// Swappable token source. The auth owner (user-service domain) replaces
// this from src/shared/auth/ once session handling exists.
let getToken: () => string | null = () => null

export function setTokenSource(source: () => string | null) {
  getToken = source
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

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export async function apiFetch<T>(
  service: ServiceName,
  path: string,
  init?: RequestInit,
): Promise<T> {
  const baseUrl = serviceBaseUrls[service]
  if (!baseUrl) {
    throw new Error(
      `No base URL configured for the ${service} service — set VITE_${service.toUpperCase()}_SERVICE_URL`,
    )
  }

  // Headers() handles every RequestInit.headers form (plain object,
  // Headers instance, or entries array); caller-supplied values win.
  const headers = new Headers(init?.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const res = await fetch(`${baseUrl}${path}`, { ...init, headers })

  if (!res.ok) {
    const problem = (await res.json().catch(() => null)) as ProblemDetail | null
    throw new ApiError(res.status, problem)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}
