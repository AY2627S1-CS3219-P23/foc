/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: POST /auth/password-reset/confirm body for issue #94. The
       password rules are AccountRules' sign-up trio and the
       double-entry match is checked server-side — both copied from
       ChangePasswordRequest (#92), whose authorization slot the
       emailed code fills here: possession of the inbox is what proves
       the caller, no session and no current password. The email is in
       the body because a salted BCrypt code hash cannot be looked up —
       it names whose code to check (and is validated on format only;
       whether it HAS an account is never revealed, the service answers
       every failure identically).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
    @NotBlank(message = EMAIL_REQUIRED)
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
    String email,

    @NotBlank(message = "Reset code is required.")
    String code,

    @NotBlank(message = PASSWORD_REQUIRED)
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    String newPassword,

    @NotBlank(message = "Password confirmation is required.")
    String confirmPassword
) {
    // true while either field is blank so @NotBlank reports alone, not
    // alongside a redundant mismatch sentence (ChangePasswordRequest's rule)
    @AssertTrue(message = "Password entries do not match.")
    public boolean isConfirmed() {
        return newPassword == null || newPassword.isBlank()
            || confirmPassword == null || confirmPassword.isBlank()
            || newPassword.equals(confirmPassword);
    }
}
