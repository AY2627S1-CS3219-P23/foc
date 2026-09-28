/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: the one login failure (issue #89). Unknown account, wrong password,
       locked account and expired deletion all raise it with the same
       message, so a failure never reveals whether an account exists.
Author review: Ryan to review via the PR.
*/

package foc.user.exception;

public class LoginFailedException extends RuntimeException {
    public LoginFailedException() {
        super("Incorrect username/email or password");
    }
}
