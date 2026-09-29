/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: PR #141 review: the field rules and messages SignupRequest and
       SetupOwnerRequest shared as copies, moved here so the two
       account-creation routes can't drift apart. Rules unchanged.
Author review: Ryan to review via the PR.
*/

package foc.user.dto;

// constants, so the request records can use them in their annotations
public final class AccountRules {

    // case-insensitive and whitespace-tolerant: the services trim and lowercase
    public static final String EMAIL_PATTERN = "^\\s*(?i)e\\d{7}@u\\.nus\\.edu\\s*$";
    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String EMAIL_MESSAGE =
        "Email must be a valid @u.nus.edu address. Email used should not be the friendly email.";

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";
    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String USERNAME_SIZE_MESSAGE = "Username must be between 3 and 30 characters";
    public static final String USERNAME_MESSAGE =
        "Username can only contain alphanumeric characters and underscores";

    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX = 50;
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String PASSWORD_SIZE_MESSAGE = "Password must be between 10 and 50 characters long";
    public static final String PASSWORD_MESSAGE =
        "Password must contain at least one uppercase letter, one lowercase letter, and one number.";

    private AccountRules() {
    }
}
