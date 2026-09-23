/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated 404 Not Found exception for profile lookups (issue #95).
       Unknown and soft-deleted users share it, per Ryan's decision.
Author review: pending (Ryan to review before merge).
*/

package foc.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User not found");
    }
}
