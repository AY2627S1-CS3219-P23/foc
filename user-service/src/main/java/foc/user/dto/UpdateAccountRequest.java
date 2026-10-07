/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: PATCH /users/me request body for issue #92, an allow-list record
       (UpdateRoleRequest's pattern, design doc §5 / rubric P1.5): only
       username, email and the gate code bind, so a "role", "id" or
       "deletedAt" in the body has no field to land in. Contract
       decisions by Leong Wei Zhi via options Q&A (2026-09-30): the gate
       code travels in the mutating request, and username and email may
       be combined in one PATCH.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// both change fields are optional (null = keep); the constraints skip null
public record UpdateAccountRequest(
    @Size(min = USERNAME_MIN, max = USERNAME_MAX, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
    String username,

    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
    String email,

    @NotBlank(message = "Verification code is required.")
    String otp
) {
    // a PATCH that changes nothing is a mistake worth naming, not a no-op
    @AssertTrue(message = "At least one of username or email is required.")
    public boolean isChangeRequested() {
        return username != null || email != null;
    }
}
