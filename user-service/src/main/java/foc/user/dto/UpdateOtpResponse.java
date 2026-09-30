/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: 202 body for issue #92's POST /users/me/otp — SignupResponse's
       timing fields (expiresInSeconds is the time the code has LEFT,
       less than a full OTP_TTL after a resend; resendInSeconds feeds
       the SPA's resend button, in the body because Retry-After isn't
       CORS-safelisted — PR #150 decision). No email field: the code
       goes to the caller's own current address.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

public record UpdateOtpResponse(long expiresInSeconds, long resendInSeconds) {
}
