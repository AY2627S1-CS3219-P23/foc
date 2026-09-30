/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: POST /users/me/email/verify request body for issue #92. Only a
       blank-check, SignupVerifyRequest's reasoning: a malformed code is
       just a wrong code. No email field — the pending change is keyed
       by the caller's login token, there is nothing else to name.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailChangeRequest(
    @NotBlank(message = "Verification code is required.")
    String code
) {}
