/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: PR #141 review: the email/username normalising and uniqueness
       checks that AuthService.signup copied from OwnerSetupService, moved
       here so both account-creation routes share them. Usernames are
       compared ignoring case (team decision, PR #141 review).
Author review: Ryan to review via the PR.
2026-09-30 (Claude Code, Fable 5), issue #92: except-self variants added for
the update flows — F2.1.2's re-validation must not refuse a user re-submitting
their own current email or username (a case-only rename must pass) — and the
taken refusals now carry problem+json type URIs (owner decision via options
Q&A: machine-readable types in scope).
*/

package foc.user.service;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import foc.user.controller.ProblemTypes;
import foc.user.repository.UserRepository;

final class NewAccountDetails {

    private NewAccountDetails() {
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    // the username keeps the case it was typed in; only lookups ignore case
    static String normalizeUsername(String username) {
        return username.trim();
    }

    // includes soft-deleted accounts: their identifiers stay reserved
    static void ensureUnique(UserRepository userRepository, String email, String username) {
        if (userRepository.existsByEmail(email)) {
            throw taken("Email is already registered", ProblemTypes.EMAIL_TAKEN);
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw taken("Username is already taken", ProblemTypes.USERNAME_TAKEN);
        }
    }

    // the update flows' checks (#92): same reservations, but the caller's
    // own row doesn't count — re-submitting your current email, or renaming
    // bob to Bob, is not a collision
    static void ensureEmailUniqueExceptSelf(UserRepository userRepository, Long selfId, String email) {
        if (userRepository.existsByEmailAndIdNot(email, selfId)) {
            throw taken("Email is already registered", ProblemTypes.EMAIL_TAKEN);
        }
    }

    static void ensureUsernameUniqueExceptSelf(UserRepository userRepository, Long selfId, String username) {
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, selfId)) {
            throw taken("Username is already taken", ProblemTypes.USERNAME_TAKEN);
        }
    }

    private static ResponseStatusException taken(String reason, URI type) {
        ResponseStatusException e = new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        e.getBody().setType(type);
        return e;
    }
}
