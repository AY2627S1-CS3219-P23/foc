/*
AI Assistance Disclosure:
2026-09-22 (Claude, Sonnet 5): Generated owner bootstrap logic (advisory lock, owner guard,
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
2026-10-19: Parts copied from AuthService which was AI-assisted
*/

package foc.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.controller.ProblemTypes;
import foc.user.dto.SignupOwnerRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.exception.SignupIdentifierTakenException;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

@Service
public class OwnerSignupService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    // the one answer for "this email already has a live pending sign-up
    // that isn't yours", whether that row was found or only collided with
    // on insert: the remedy is the same either way
    static final String SIGNUP_IN_PROGRESS =
        "A sign-up for this email is already in progress; check your inbox for the code,"
            + " or try again once it expires";

    private final UserRepository userRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final OtpEmailSender otpEmailSender;
    private final Clock clock;
    // matched against when no pending sign-up exists, so an unknown
    // email takes as long to verify as a wrong code (same trick login
    // used before #146 made its messages revealing; these stay identical)
    private final String unknownPendingCodeHash;
    private final String expectedOwnerKey;
    
    @Autowired
    public OwnerSignupService(
            UserRepository userRepository,
            PendingSignupRepository pendingSignupRepository,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            @Value("${owner.setup.token:}") String expectedOwnerKey) {
        this(userRepository, pendingSignupRepository, passwordEncoder,
            otpService, otpEmailSender, Clock.systemUTC(), expectedOwnerKey);
    }

    OwnerSignupService(
            UserRepository userRepository,
            PendingSignupRepository pendingSignupRepository,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            Clock clock,
            String expectedOwnerKey) {
        this.userRepository = userRepository;
        this.pendingSignupRepository = pendingSignupRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.otpEmailSender = otpEmailSender;
        this.clock = clock;
        this.unknownPendingCodeHash = otpService.hash("no-pending-signup");
        this.expectedOwnerKey = expectedOwnerKey;
    }

    @Transactional
    public SignupResponse signupOwner(SignupOwnerRequest request) {
        String email = NewAccountDetails.normalizeEmail(request.email());
        String username = NewAccountDetails.normalizeUsername(request.username());
        NewAccountDetails.ensureUnique(userRepository, email, username);

        PendingSignup pending = pendingSignupRepository.findWithLockByEmail(email).orElse(null);
        Instant now = clock.instant();
        // Signup expired
        boolean expired = pending != null && !pending.getExpiresAt().isAfter(now);
        // Password attempts spent
        boolean spent = pending != null && pending.getAttempts() >= otpService.maxAttempts();

        // Otp already sent
        if (pending != null && !expired && !isSameRequest(request, username, pending)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIGNUP_IN_PROGRESS);
        }
        if (pending != null) {
            Instant nextSendAllowed = pending.getLastSentAt().plus(otpService.resendCooldown());
            if (nextSendAllowed.isAfter(now)) {
                throw new OtpResendTooSoonException(Duration.between(now, nextSendAllowed));
            }
        }

        // Check if ownerKey matches, let user know in otp email
        // User can submit a different ownerKey with different otp after a delay
        boolean isOwner = checkOwnerKey(request.ownerKey());

        // Generate otp
        String code = otpService.generateCode();
        String codeHash = otpService.hash(code);
        if (pending == null) {
            pending = new PendingSignup(email, username, passwordEncoder.encode(request.password()),
                codeHash, now, now.plus(otpService.ttl()), isOwner);
        } else if (expired) {
            pending.replaceExpired(username, passwordEncoder.encode(request.password()),
                codeHash, now, now.plus(otpService.ttl()), isOwner);
        } else if (spent) {
            pending.reviveSpent(codeHash, now, isOwner);
        } else {
            pending.renewCode(codeHash, now, isOwner);
        }
        
        // Save in pending
        try {
            pendingSignupRepository.saveAndFlush(pending);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIGNUP_IN_PROGRESS);
        }

        // Send email with otp
        Duration validity = Duration.between(now, pending.getExpiresAt());
        try {
            otpEmailSender.sendSignupCode(email, code, validity, isOwner);
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the verification email; try again later");
        }

        return new SignupResponse(email, validity.toSeconds(),
            otpService.resendCooldown().toSeconds());
    }

    @Transactional(noRollbackFor = {OtpVerificationException.class, OtpAttemptsExceededException.class,
        SignupIdentifierTakenException.class})
    public UserResponse verifySignup(SignupVerifyRequest request) {
        String email = NewAccountDetails.normalizeEmail(request.email());
        Optional<PendingSignup> found = pendingSignupRepository.findWithLockByEmail(email);
        if (found.isEmpty()) {
            // no pending row answers exactly like a wrong code
            otpService.matches(request.code(), unknownPendingCodeHash);
            throw new OtpVerificationException();
        }
        PendingSignup pending = found.get();

        if (!pending.getExpiresAt().isAfter(clock.instant())) {
            pendingSignupRepository.delete(pending);
            throw OtpVerificationException.expired(
                "Code has expired; sign up again to get a new code");
        }

        // A spent row answers exactly like no row at all
        if (pending.getAttempts() >= otpService.maxAttempts()) {
            otpService.matches(request.code(), unknownPendingCodeHash);
            throw new OtpVerificationException();
        }

        // Wrong otp
        if (!otpService.matches(request.code(), pending.getCodeHash())) {
            pending.incrementAttempts();
            pendingSignupRepository.save(pending);
            if (pending.getAttempts() >= otpService.maxAttempts()) {
                Duration untilResend = Duration.between(clock.instant(),
                    pending.getLastSentAt().plus(otpService.resendCooldown()));
                throw new OtpAttemptsExceededException(
                    untilResend.isNegative() ? Duration.ZERO : untilResend);
            }
            throw new OtpVerificationException();
        }

        // Check unique
        try {
            NewAccountDetails.ensureUnique(userRepository, email, pending.getUsername());
        } catch (ResponseStatusException e) {
            pendingSignupRepository.delete(pending);
            throw new SignupIdentifierTakenException(e);
        }
        
        // Create user
        User user = new User(email, pending.getUsername(), pending.getPasswordHash(),
            pending.getIsOwner() ? Role.OWNER : Role.USER);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // A concurrent sign-up took the email or username after the checks
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Email or username was just taken; choose another");
        }
        
        pendingSignupRepository.delete(pending);
        return UserResponse.from(user);
    }

    // Intentionally block changing the ownerKey behind otp expiry
    private boolean isSameRequest(SignupOwnerRequest request, String username, PendingSignup pending) {
        return pending.getUsername().equalsIgnoreCase(username)
            && passwordEncoder.matches(request.password(), pending.getPasswordHash());
    }

    private boolean checkOwnerKey(String providedOwnerKey) {
        if (expectedOwnerKey == null || expectedOwnerKey.isBlank()) {
            ResponseStatusException e =  new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Owner setup is unavailable"
            );
            e.getBody().setType(ProblemTypes.OWNER_SETUP_TOKEN_MISSING);
            throw e;
        }

        byte[] expected = expectedOwnerKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedOwnerKey == null ? "" : providedOwnerKey).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, provided);
    }

}
