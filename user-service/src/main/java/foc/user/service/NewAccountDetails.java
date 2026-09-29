/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: PR #141 review: the email/username normalising and uniqueness
       checks that AuthService.signup copied from OwnerSetupService, moved
       here so both account-creation routes share them. Usernames are
       compared ignoring case (team decision, PR #141 review).
Author review: Ryan to review via the PR.
*/

package foc.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is already taken");
        }
    }
}
