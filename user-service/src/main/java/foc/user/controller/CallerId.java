/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: callerId moved here from ProfileController and AdminController, which
       had identical copies (PR #135 review), so #91 only changes it once.
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

final class CallerId {

    private CallerId() {
    }

    // the principal name carries the caller's user id until JWT auth (#91);
    // a non-numeric name is treated as unauthenticated rather than a 500
    static Long from(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
