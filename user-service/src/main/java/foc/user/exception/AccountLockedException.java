/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: distinct lockout failure for login (PR #142, issue #145 decided
       by the author: the wireframe's lockout message wins over fully
       non-revealing failures). Raised when a login hits a locked
       account or when an attempt trips the lock; unlike
       LoginFailedException it deliberately reveals the lock state.
Author review: reviewed via the PR.
*/

package foc.user.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Too many failed attempts. Login disabled for 15 minutes.");
    }
}
