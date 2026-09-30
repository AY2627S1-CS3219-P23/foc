/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: POST /users/me/password request body for issue #92. The password
       rules are AccountRules' sign-up trio — that reuse IS F2.1.5's
       "validate like sign-up" — and the double-entry match (F2.1.4) is
       checked here too, server-side (decision by Leong Wei Zhi via
       options Q&A, 2026-09-30; the SPA also checks locally). No
       currentPassword: the gate code to the account's email is the
       authentication (same Q&A).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = PASSWORD_REQUIRED)
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    String newPassword,

    @NotBlank(message = "Password confirmation is required.")
    String confirmPassword,

    @NotBlank(message = "Verification code is required.")
    String otp
) {
    // true while either field is blank so @NotBlank reports alone, not
    // alongside a redundant mismatch sentence
    @AssertTrue(message = "Password entries do not match.")
    public boolean isConfirmed() {
        return newPassword == null || newPassword.isBlank()
            || confirmPassword == null || confirmPassword.isBlank()
            || newPassword.equals(confirmPassword);
    }
}
