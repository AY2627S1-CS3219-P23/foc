/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: response body for issue #88's reworked POST /auth/signup (202: a
       code was emailed, no account exists yet). Echoes the normalised
       email the code went to and the code's lifetime, which is all the
       SPA's OTP-entry screen needs.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.dto;

public record SignupResponse(String email, long expiresInSeconds) {
}
