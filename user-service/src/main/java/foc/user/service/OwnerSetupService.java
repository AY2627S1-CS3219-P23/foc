/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated owner bootstrap logic (advisory lock, owner guard,
       normalisation, uniqueness checks, BCrypt hashing).
       2026-09-23 (Claude, Sonnet 4.6): setup-token check added.
       2026-09-23 (Claude Code, Fable 5), issue #86: role handled via the
       Role enum following the entity's String-to-enum conversion; the
       response DTO keeps its String role (unchanged JSON shape).
       2026-09-25 (Claude Code, Opus 5.5), PR #131 review: private
       toUserResponse replaced by the shared UserResponse.from.
       2026-09-25 (Claude Code, Opus 5.5): existing-owner check (409);
       any caller with the setup token can now create an OWNER.
       setupFirstOwner renamed to setupOwner.
       2026-09-25 (Claude Code, Opus 5.5): setup token expiry added, then
       removed again (deferred by Ryan); the setup token does not expire.
       2026-09-25 (Claude Code, Opus 5.5), PR #132 review: password hashed
       before the setup lock is taken, so the lock no longer covers BCrypt.
       2026-09-29 (Claude Code, Opus 5.5), PR #141 review: normalising and
       uniqueness checks moved to NewAccountDetails, shared with sign-up;
       usernames now compared ignoring case (team decision).
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.SetupOwnerRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@Service
public class OwnerSetupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String expectedSetupToken;

    public OwnerSetupService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${owner.setup.token:}") String expectedSetupToken) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expectedSetupToken = expectedSetupToken;
    }

    // creates an OWNER account; repeatable for as long as the setup token is valid
    @Transactional
    public UserResponse setupOwner(SetupOwnerRequest request, String providedSetupToken) {
        ensureValidSetupToken(providedSetupToken);

        String email = NewAccountDetails.normalizeEmail(request.email());
        String username = NewAccountDetails.normalizeUsername(request.username());
        // hashed before the lock: BCrypt is slow and only depends on the request
        String passwordHash = passwordEncoder.encode(request.password());

        userRepository.acquireSetupLock();

        NewAccountDetails.ensureUnique(userRepository, email, username);

        User owner = createOwner(email, username, passwordHash);

        User saved = userRepository.save(owner);

        return UserResponse.from(saved);
    }

    // guards against an unauthenticated caller. fails if the deploy forgot to set
    // OWNER_SETUP_TOKEN.
    private void ensureValidSetupToken(String providedToken) {
        if (expectedSetupToken == null || expectedSetupToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is disabled: OWNER_SETUP_TOKEN is not configured"
            );
        }

        byte[] expected = expectedSetupToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedToken == null ? "" : providedToken).getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing setup token");
        }
    }

    private User createOwner(String email, String username, String passwordHash) {
        User owner = new User();

        owner.setEmail(email);
        owner.setUsername(username);
        owner.setPasswordHash(passwordHash);
        owner.setRole(Role.OWNER);
        owner.setFailedLoginAttempts(0);
        owner.setLockedUntil(null);
        owner.setDeletedAt(null);

        return owner;
    }
}
