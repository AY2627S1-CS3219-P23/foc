/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: 202 body for issue #92's PATCH /users/me when an email change
       parks (decision by Leong Wei Zhi via options Q&A, 2026-09-30:
       202 + OTP timings, plus the updated user so a username change
       that applied in the same PATCH is visible). email echoes the
       normalized address the confirmation code went to, like
       SignupResponse; POST /users/me/email/resend answers with the
       same shape.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

public record EmailChangePendingResponse(
    // the account as it stands NOW: any username change is in, the email
    // is still the old one until the new address confirms
    UserResponse user,
    // the normalized address the confirmation code was sent to
    String email,
    long expiresInSeconds,
    long resendInSeconds
) {}
