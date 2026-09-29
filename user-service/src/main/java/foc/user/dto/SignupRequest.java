/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: sign-up request body (issue #87). Same field rules and messages as
       SetupOwnerRequest (NUS email, username format, password policy), so
       both account-creation routes validate alike.
       PR #141 review: the rules now come from AccountRules, shared with
       SetupOwnerRequest.
Author review: Ryan to review via the PR.
*/

package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank(message = EMAIL_REQUIRED)
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
    String email,

    @NotBlank(message = USERNAME_REQUIRED)
    @Size(min = USERNAME_MIN, max = USERNAME_MAX, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
    String username,

    @NotBlank(message = PASSWORD_REQUIRED)
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    String password
) {}
