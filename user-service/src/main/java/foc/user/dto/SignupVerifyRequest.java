/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: request body for issue #88's POST /auth/signup/verify. Only
       blank-checks: a malformed code is just a wrong code, and the
       email was already validated at sign-up.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.dto;

import jakarta.validation.constraints.NotBlank;

public record SignupVerifyRequest(
    @NotBlank(message = "Email is required.")
    String email,

    @NotBlank(message = "Verification code is required.")
    String code
) {}
