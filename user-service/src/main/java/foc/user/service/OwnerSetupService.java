/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated owner bootstrap logic (advisory lock, owner guard,
       normalisation, uniqueness checks, BCrypt hashing).
       2026-09-23 (Claude, Sonnet 4.6): setup-token check added.
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
import foc.user.entity.User;
import foc.user.exception.OwnerAlreadySetException;
import foc.user.repository.UserRepository;

@Service
public class OwnerSetupService {

    private static final String ROLE_OWNER = "OWNER";
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

    // bootstraps first owner account, only when owners = 0
    @Transactional
    public UserResponse setupFirstOwner(SetupOwnerRequest request, String providedSetupToken) {
        ensureValidSetupToken(providedSetupToken);

        userRepository.acquireSetupLock();

        ensureSetupAvailable();

        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());

        ensureUserDetailsAreUnique(email, username);

        User owner = createOwner(email, username, request.password());

        User saved = userRepository.save(owner);

        return toUserResponse(saved);
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

    private void ensureSetupAvailable() {
        if (userRepository.countByRole(ROLE_OWNER) > 0) {
            throw new OwnerAlreadySetException();
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
        owner.setRole(ROLE_OWNER);
        owner.setFailedLoginAttempts(0);
        owner.setLockedUntil(null);
        owner.setDeletedAt(null);

        return owner;
    }
    private UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
