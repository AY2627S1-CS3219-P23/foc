/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: response body for issue #88's reworked POST /auth/signup (202: a
       code was emailed, no account exists yet). Echoes the normalised
       email the code went to and the code's lifetime, which is all the
       SPA's OTP-entry screen needs.
       2026-09-30, Claude Code (Opus 5), PR #150 review: resendInSeconds
       added for the OTP dialog's resend button. It travels in the body
       rather than being read from the 429's Retry-After header because
       the SPA is cross-origin and that header is not CORS-safelisted;
       duplicating OTP_RESEND_COOLDOWN in web/ was the alternative
       (decision by Leong Wei Zhi via options Q&A).
       expiresInSeconds is the time the code has *left*, which after a
       resend is less than a full OTP_TTL.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.dto;

public record SignupResponse(String email, long expiresInSeconds, long resendInSeconds) {
}
