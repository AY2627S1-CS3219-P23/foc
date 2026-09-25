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
       2026-09-25 (Claude Code, Opus 5.5): setup token expiry added
       (OWNER_SETUP_TOKEN_EXPIRES_AT, ISO-8601; unset -> 503, expired ->
       403)
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

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
    // null when OWNER_SETUP_TOKEN_EXPIRES_AT is unset; a malformed value
    // fails startup rather than silently disabling or opening setup
    private final Instant tokenExpiresAt;

    public OwnerSetupService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${owner.setup.token:}") String expectedSetupToken,
            @Value("${owner.setup.token-expires-at:}") String tokenExpiresAt) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expectedSetupToken = expectedSetupToken;
        this.tokenExpiresAt = (tokenExpiresAt == null || tokenExpiresAt.isBlank())
            ? null
            : Instant.parse(tokenExpiresAt.trim());
    }

    // creates an OWNER account; repeatable until the setup token expires
    @Transactional
    public UserResponse setupOwner(SetupOwnerRequest request, String providedSetupToken) {
        ensureValidSetupToken(providedSetupToken);

        userRepository.acquireSetupLock();

        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());

        ensureUserDetailsAreUnique(email, username);

        User owner = createOwner(email, username, request.password());

        User saved = userRepository.save(owner);

        return UserResponse.from(saved);
    }

    // guards against an unauthenticated caller. fails if the deploy forgot to set
    // OWNER_SETUP_TOKEN or OWNER_SETUP_TOKEN_EXPIRES_AT.
    private void ensureValidSetupToken(String providedToken) {
        if (expectedSetupToken == null || expectedSetupToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is disabled: OWNER_SETUP_TOKEN is not configured"
            );
        }

        if (tokenExpiresAt == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is disabled: OWNER_SETUP_TOKEN_EXPIRES_AT is not configured"
            );
        }

        byte[] expected = expectedSetupToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedToken == null ? "" : providedToken).getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing setup token");
        }

        // checked after the token match, so only a caller holding the token
        // learns that it has expired
        if (!Instant.now().isBefore(tokenExpiresAt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Setup token has expired");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }    


    private String normalizeUsername(String username) {
        return username.trim();
    }

    private void ensureUserDetailsAreUnique(String email, String username) {

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Email is already registered"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Username is already taken"
            );
        }
    }  
    
    
    private User createOwner(String email, String username, String password) {
        User owner = new User();

        owner.setEmail(email);
        owner.setUsername(username);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setRole(Role.OWNER);
        owner.setFailedLoginAttempts(0);
        owner.setLockedUntil(null);
        owner.setDeletedAt(null);

        return owner;
    }
}
