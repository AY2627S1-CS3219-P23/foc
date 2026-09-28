/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: login request body (issue #89): username or email, plus password.
       No format rules, so a malformed identifier fails like a wrong one.
Author review: Ryan to review via the PR.
*/

package foc.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Username or email is required")
    String usernameOrEmail,

    @NotBlank(message = "Password is required")
    String password
) {}
