/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: POST /auth/password-reset body for issue #94. Email format is
       validated (the NUS pattern is public knowledge — refusing
       garbage reveals nothing), but whether the address HAS an account
       is never revealed: the endpoint answers 202 either way (owner
       decision via options Q&A).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(
    @NotBlank(message = EMAIL_REQUIRED)
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
    String email
) {
}
