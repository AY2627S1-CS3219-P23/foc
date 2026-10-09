// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5), 2026-10-07, issue #112 (PR #149).
// Scope: the problem+json `type` URIs user-service attaches to its OTP
// and uniqueness refusals (user-service/.../ProblemTypes.java, added by
// PR #157), and the one helper that reads them off an ApiError. They are
// the durable way to tell these failures apart: the `detail` sentence is
// human copy and may be reworded, which is exactly why the sign-up
// dialog's sentence list (PR #150) was flagged for replacement.
// 2026-10-09, Claude Code (Opus 5), PR #149 Copilot review: `about:blank`
// is reported as untyped (RFC 9457's "no type", and the value Spring puts
// on every ProblemDetail built without one), and an untyped 400 — the
// DTO validation ProblemDetailAdvice answers with — is classified as a
// refusal of the submitted values rather than of the code.
// Author review: Leong Wei Zhi (via PR #149).

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

// RFC 9457's "no type", and what Spring stamps on every ProblemDetail
// built without one: a body can carry the field and still say nothing.
// user-service's validation 400s (ProblemDetailAdvice.handleInvalidBody),
// its untyped ResponseStatusExceptions and SecurityProblemResponses all
// come through this way.
const UNTYPED = 'about:blank'

// The type URI an API failure carries, or null for anything else (a
// network blip, a 500, an untyped refusal — `about:blank` included:
// checking the field for truthiness read it as a type and skipped the
// callers' fallbacks, PR #149 review).
export function problemType(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  const type = error.problem?.type
  return type && type !== UNTYPED ? type : null
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
 *              a wrong guess, or a failure that never judged the values (a
 *              500, a mail 502, a network blip).
 * - `restart`— no usable code any more, so the card returns to its fields
 *              and asks for a new one: expired, spent (429), or never
 *              requested.
 * - `amend`  — the values submitted were refused and the code was NOT
 *              spent refusing them, so it is STILL LIVE: the card goes
 *              back to its fields holding the code, and the next Save
 *              reuses it rather than spending a resend. Two failures land
 *              here — a name or address already taken (the refusal rolls
 *              the gate consumption back with it, AccountUpdateService's
 *              rollback note), and a body the DTO's own validation
 *              rejected, which never reached the gate at all.
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
    case null:
      // An untyped 400 is the request body's own validation
      // (ProblemDetailAdvice's handleInvalidBody / handleUnreadableBody),
      // which runs before the service reads the code: a password the
      // policy refuses, a confirmation that doesn't match, a malformed
      // address. The fields are what need fixing, and the code is
      // untouched. Everything else untyped — a 500, a mail 502, a 409
      // race, a network blip — is a retry on the same code.
      return error instanceof ApiError && error.status === 400
        ? 'amend'
        : 'retry'
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
