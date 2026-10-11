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
// 2026-10-07, Claude Code (Opus 5), issue #112 (PR #149): OtpTimings,
// EmailChangeAccepted and PendingEmailChange, for #92's account-update
// endpoints (PR #157) now that they exist.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review:
// PendingEmailChange names the account it belongs to, so the profile
// page can tell its own snapshot from one another account left behind.
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

// What POST /users/me/otp answers with (UpdateOtpResponse): a gate code
// went to the account's CURRENT email. expiresInSeconds is what that code
// has left — less than a full OTP_TTL after a resend, which replaces the
// code but never the expiry — and resendInSeconds is the cooldown before
// another may be requested.
export interface OtpTimings {
  expiresInSeconds: number
  resendInSeconds: number
}

// The 202 body of PATCH /users/me (EmailChangePendingResponse): the email
// change parked and a confirmation code went to the NEW address. `user` is
// the account as it stands now — a username change in the same PATCH is
// already applied, the email is still the old one until the new address
// confirms.
export interface EmailChangeAccepted extends OtpTimings {
  user: AdminUser
  email: string
}

// An email change parked server-side in `pending_email_changes`, as the
// profile page remembers it. user-service has no GET for the row, so the
// page keeps this snapshot in localStorage: a reload mid-confirm still
// offers the code step instead of making the user redo the PATCH (and pay
// for a fresh gate code). Absolute times rather than the durations the API
// returns, for the reason SignupPending above gives.
//
// userId is the account the change belongs to. localStorage is
// browser-wide and a snapshot outlives the session that wrote it (logout
// and account deletion leave it there), so without an owner the page
// would offer whatever address it found to whoever signed in next, and
// act on it as theirs (PR #149 review).
export interface PendingEmailChange {
  userId: number
  email: string
  expiresAt: number // epoch ms
  resendAt: number // epoch ms
}
