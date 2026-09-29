/* 
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated boilerplate code for email, password and username fields. Also generated regex for email, username and passwords.
Author review: Ryan validated correctness and edited error messages.
2026-09-23 (Claude Code, Opus 5.5): email pattern made case-insensitive and
whitespace-tolerant (redundant @Email dropped, as it rejects
padded input and the pattern already fixes the format); password size message now states both bounds (PR #126 review).
2026-09-29 (Claude Code, Opus 5.5), PR #141 review: rules and messages
moved to AccountRules, shared with SignupRequest (unchanged).

*/


package foc.user.dto;

import static foc.user.dto.AccountRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupOwnerRequest(
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
