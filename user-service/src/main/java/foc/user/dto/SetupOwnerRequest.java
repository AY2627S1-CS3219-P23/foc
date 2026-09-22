/* 
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated boilerplate code for email, password and username fields. Also generated regex for email, username and passwords.
Author review: Ryan validated correctness and edited error messages.

*/


package foc.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupOwnerRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(regexp = "^e\\d{7}@u\\.nus\\.edu$", 
            message = "Email must be a valid @u.nus.edu address. Email used should not be the friendly email.")
    String email,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", 
            message = "Username can only contain alphanumeric characters and underscores")
    String username,

    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 50, 
            message = "Password must be at least 10 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", 
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one number.")
    String password
) {}


// i need help let me drop out