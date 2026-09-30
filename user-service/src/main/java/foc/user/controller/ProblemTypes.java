/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: machine-readable problem+json type URIs for issue #92 (decision
       by Leong Wei Zhi via options Q&A, 2026-09-30: in scope, and the
       existing sign-up OTP errors get them too). The SPA's OTP dialog
       currently string-matches detail sentences to tell failures that
       end a pending flow from ones that don't, and flags a stable type
       as the durable fix — these are that fix. URN scheme, so there is
       no host or path to rot.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import java.net.URI;

/**
 * The {@code type} URIs our problem+json errors carry (RFC 9457 §3.1.1).
 * Stable identifiers for machine matching — clients should key on these,
 * never on the human-readable {@code detail} sentence, whose wording may
 * change. Absent from an error means "about:blank" (no specific type).
 */
public final class ProblemTypes {

    private ProblemTypes() {
    }

    /** Wrong code (or, at sign-up, an unknown email — indistinguishable on purpose). */
    public static final URI OTP_INVALID = URI.create("urn:foc:user:otp-invalid");

    /** The code (or the pending operation holding it) has expired; request a new one. */
    public static final URI OTP_EXPIRED = URI.create("urn:foc:user:otp-expired");

    /** Too many wrong codes; the pending operation was discarded. */
    public static final URI OTP_ATTEMPTS_EXCEEDED = URI.create("urn:foc:user:otp-attempts-exceeded");

    /** Resend requested inside the cooldown; Retry-After says how long. */
    public static final URI OTP_RESEND_COOLDOWN = URI.create("urn:foc:user:otp-resend-cooldown");

    /** No gate code exists for this account; request one first (#92). */
    public static final URI OTP_REQUIRED = URI.create("urn:foc:user:otp-required");

    /** No email change is pending for this account (#92). */
    public static final URI EMAIL_CHANGE_NONE = URI.create("urn:foc:user:email-change-none");

    /** The email is already registered (uniqueness includes soft-deleted accounts). */
    public static final URI EMAIL_TAKEN = URI.create("urn:foc:user:email-taken");

    /** The username is already taken (ignoring case; includes soft-deleted accounts). */
    public static final URI USERNAME_TAKEN = URI.create("urn:foc:user:username-taken");
}
