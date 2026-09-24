/* 
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated boilerplate code for email, password and username fields. Also generated regex for email, username and passwords.
Author review: Ryan validated correctness and edited error messages.
2026-09-23 (Claude Code, Opus 5.5): email pattern made case-insensitive and
whitespace-tolerant (redundant @Email dropped, as it rejects
padded input and the pattern already fixes the format); password size message now states both bounds (PR #126 review).

*/


package foc.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupOwnerRequest(
    @NotBlank(message = "Email is required")
    // Case-insensitive, and tolerant of surrounding whitespace, so input the
    // service normalises (trim + lowercase) isn't rejected before it gets there.
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
