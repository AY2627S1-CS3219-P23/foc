/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-08
Scope: POST /users/me/owner request body for issue #308. Contract decided
       by Leong Wei Zhi via options Q&A (2026-11-08): the owner setup
       token plus the account's gate code (#92). ownerKey carries no
       @NotBlank on purpose: a blank token is refused like a wrong one
       (403), not as a validation error.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpgradeOwnerRequest(
    String ownerKey,

    @NotBlank(message = "Verification code is required.")
    String otp
) {
}
