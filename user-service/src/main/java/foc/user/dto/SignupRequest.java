/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: sign-up request body (issue #87). Same field rules and messages as
       SetupOwnerRequest (NUS email, username format, password policy), so
       both account-creation routes validate alike.
Author review: Ryan to review via the PR.
*/

package foc.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank(message = "Email is required")
    // case-insensitive and whitespace-tolerant: the service trims and lowercases
    @Pattern(regexp = "^\\s*(?i)e\\d{7}@u\\.nus\\.edu\\s*$",
            message = "Email must be a valid @u.nus.edu address. Email used should not be the friendly email.")
    String email,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$",
            message = "Username can only contain alphanumeric characters and underscores")
    String username,

    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 50,
            message = "Password must be between 10 and 50 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one number.")
    String password
) {}
