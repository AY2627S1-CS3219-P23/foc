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
       2026-10-02 (Claude Code, Opus 5.5), issue #154: the 503 for an
       unconfigured setup token gives a generic reason (Ryan's request),
       since ProblemDetailAdvice now shows it to the caller.
       2026-10-10 (Claude Code, Opus 5.5), PR #162 re-review (Leong Wei
       Zhi): that case is logged server-side, naming the setting; then
       logged once per process rather than on every request.
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OwnerSetupService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String expectedSetupToken;
    private final AtomicBoolean missingTokenLogged = new AtomicBoolean();

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
    // OWNER_SETUP_TOKEN; the reason is the caller's problem+json detail, so
    // it doesn't say which setting is missing
    private void ensureValidSetupToken(String providedToken) {
        if (expectedSetupToken == null || expectedSetupToken.isBlank()) {
            // the setting is named here, for the operator, and not in the response.
            // logged once: the endpoint takes no credentials, so a caller
            // must not be able to write an ERROR line per request
            if (missingTokenLogged.compareAndSet(false, true)) {
                log.error("Owner setup unavailable: OWNER_SETUP_TOKEN is not configured");
            }
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is unavailable"
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
