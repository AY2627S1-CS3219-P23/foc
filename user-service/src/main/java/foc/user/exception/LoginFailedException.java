/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: the one login failure (issue #89). Unknown account, wrong password,
       locked account and expired deletion all raise it with the same
       message, so a failure never reveals whether an account exists.
       2026-09-29, Claude Code (Opus 5), issue #146: the message now names
       the cause, so the failures are no longer interchangeable. The author
       chose this wording and the attempts countdown over the previous
       non-revealing text, accepting that login now tells an attacker
       whether an account exists (sign-up's "already taken" 400s already
       did, and #145 accepted revealing the lock state).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.exception;

// 401 for every login failure that isn't a lockout; the factory names the
// cause, since each one now has its own message
public class LoginFailedException extends RuntimeException {

    private LoginFailedException(String message) {
        super(message);
    }

    // no row matched the username or email, or the account is past its
    // retention window and only waiting for the purge
    public static LoginFailedException unknownAccount() {
        return new LoginFailedException("No account found for that username or email.");
    }

    // remaining is how many more failures the account can take before
    // AuthService.MAX_FAILED_ATTEMPTS locks it
    public static LoginFailedException wrongPassword(int remaining) {
        String attempts = remaining == 1 ? "1 attempt" : remaining + " attempts";
        return new LoginFailedException("Incorrect password. " + attempts
            + " remaining before your account is temporarily locked.");
    }

    // for the paths that can't read a trustworthy counter (the loser of two
    // concurrent logins), so the message just states the cause
    public static LoginFailedException wrongPassword() {
        return new LoginFailedException("Incorrect password.");
    }
}
