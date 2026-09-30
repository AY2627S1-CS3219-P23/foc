// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-09-23, issue #113; revised
// 2026-09-28 (list response and query types for the #96 endpoints).
// Scope: user domain types for the admin user-management screen.
// Mirrors user-service's UserResponse DTO and the Spring PagedModel body
// that GET /users returns (#96) — not a new contract.
// 2026-09-29, Claude Code (Fable 5), PR #142: LoginResponse moved here
// from login.tsx so the auth session type is shared with useAuth and
// AuthProvider (JWT wiring into apiFetch).
// 2026-09-29, Claude Code (Opus 5.5), issue #147: deletedAt and
// includeDeleted for listing removed accounts (team decision).
// 2026-09-30, Claude Code (Opus 5), issue #109 (PR #150): SignupAccepted
// and SignupPending, for the sign-up step that waits on an emailed code.
// Reviewed by: Ryan Ang

// POST /auth/login body (user-service's LoginResponse DTO) — the
// session the SPA stores after login.
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

// What POST /auth/signup answers with (user-service's SignupResponse DTO).
// expiresInSeconds is the time the code has left, so a resend late in the
// window reports less than the full OTP_TTL; resendInSeconds is the
// cooldown before another code may be requested.
export interface SignupAccepted {
  email: string
  expiresInSeconds: number
  resendInSeconds: number
}

// A sign-up parked in user-service's pending_signups table: the body that
// was sent (which a resend has to repeat exactly) and when its code dies /
// the next resend is allowed. Absolute times rather than the durations the
// API returns, so a code dialog closed and reopened later shows what is
// really left instead of restarting the countdown (PR #150 review).
export interface SignupPending {
  email: string
  username: string
  password: string
  expiresAt: number // epoch ms
  resendAt: number // epoch ms
}

// Role values as stored by user-service (User.role).
export type UserRole = 'USER' | 'ADMIN' | 'OWNER'

export interface AdminUser {
  id: number
  email: string
  username: string
  role: UserRole
  createdAt: string // ISO-8601 instant
  // ISO-8601 instant; present only on a removed (soft-deleted) account
  deletedAt?: string
}

// GET /users body (Spring Data PagedModel). page.number is 0-based.
export interface AdminUserPage {
  content: AdminUser[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface ListUsersParams {
  search?: string
  role?: UserRole
  page: number // 0-based, as the API expects
  size: number // user-service accepts 20, 50 or 100
  // also list removed accounts (still within their 30-day window)
  includeDeleted?: boolean
}
