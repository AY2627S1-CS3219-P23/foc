// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-07, issue #112 (PR #149).
// Scope: the problem+json `type` URIs user-service attaches to its OTP
// and uniqueness refusals (user-service/.../ProblemTypes.java, added by
// PR #157), and the one helper that reads them off an ApiError. They are
// the durable way to tell these failures apart: the `detail` sentence is
// human copy and may be reworded, which is exactly why the sign-up
// dialog's sentence list (PR #150) was flagged for replacement.
// Reviewed by: [pending]

import { ApiError } from '@/lib/api/http'

/** Wrong code (at sign-up, also an unknown email — indistinguishable on purpose). 400 */
export const OTP_INVALID = 'urn:foc:user:otp-invalid'
/** The code, or the pending operation holding it, expired. 400 */
export const OTP_EXPIRED = 'urn:foc:user:otp-expired'
/** Too many wrong codes; the row is spent until the resend cooldown passes. 429 + Retry-After */
export const OTP_ATTEMPTS_EXCEEDED = 'urn:foc:user:otp-attempts-exceeded'
/** A code was sent too recently to send another. 429 + Retry-After */
export const OTP_RESEND_COOLDOWN = 'urn:foc:user:otp-resend-cooldown'
/** No gate code exists for this account; request one first. 400 */
export const OTP_REQUIRED = 'urn:foc:user:otp-required'
/** No email change is pending to verify or resend. 400 */
export const EMAIL_CHANGE_NONE = 'urn:foc:user:email-change-none'
/** The email is already registered. 400 */
export const EMAIL_TAKEN = 'urn:foc:user:email-taken'
/** The username is already taken. 400 */
export const USERNAME_TAKEN = 'urn:foc:user:username-taken'

// The type URI an API failure carries, or null for anything else (a
// network blip, a 500, an untyped refusal).
export function problemType(error: unknown): string | null {
  return error instanceof ApiError ? (error.problem?.type ?? null) : null
}

// Seconds the server asked the caller to wait, or null. Only the two 429s
// carry it; user-service CORS-exposes Retry-After so it survives the
// cross-origin hop.
export function retryAfter(error: unknown): number | null {
  return error instanceof ApiError ? error.retryAfter : null
}

/**
 * How a profile card should answer a failed gate-code call
 * (PATCH /users/me, POST /users/me/password):
 *
 * - `retry`  — the code step stands, the code in the inbox is still good:
 *              a wrong guess, or any untyped failure (500, network).
 * - `restart`— no usable code any more, so the card returns to its fields
 *              and asks for a new one: expired, spent (429), or never
 *              requested.
 * - `amend`  — the change itself was refused (name/address taken). The
 *              refusal rolls the gate consumption back with it, so the
 *              code is STILL LIVE — the card goes back to its fields
 *              holding the code, and the next Save reuses it rather than
 *              spending a resend (AccountUpdateService's rollback note).
 */
export type GateFailure = 'retry' | 'restart' | 'amend'

export function gateFailure(error: unknown): GateFailure {
  switch (problemType(error)) {
    case OTP_EXPIRED:
    case OTP_REQUIRED:
    case OTP_ATTEMPTS_EXCEEDED:
      return 'restart'
    case USERNAME_TAKEN:
    case EMAIL_TAKEN:
      return 'amend'
    default:
      return 'retry'
  }
}

/**
 * Whether a failed email-change call leaves nothing to confirm, so the
 * page must drop its pending snapshot: the row is gone server-side
 * (expired, never there) or can never complete (spent, address taken —
 * user-service discards that one itself). A wrong code is not in the
 * list: the code in the inbox is still good.
 */
export function endsEmailChange(error: unknown): boolean {
  switch (problemType(error)) {
    case OTP_EXPIRED:
    case OTP_ATTEMPTS_EXCEEDED:
    case EMAIL_CHANGE_NONE:
    case EMAIL_TAKEN:
      return true
    default:
      return false
  }
}
