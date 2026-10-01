/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: the account-update flows for issue #92 (requirement F2), design
       doc §5. Contract decisions by Leong Wei Zhi via options Q&A
       (2026-09-30): gate code to the current email authorizes every
       change and travels in the mutating request, single-use; password
       has its own OTP-gated endpoint with server-side double-entry, no
       currentPassword; one PATCH may combine username (applies now)
       and email (parks pending, 202 + timings); the new address
       confirms by its own code. Transaction shapes — noRollbackFor
       attempt counters and discards, row locks, MailException → 502
       rollback, flush-race answers — mirror AuthService.signup/
       verifySignup and the PR #150 reviews that settled them.
       PR #157 Copilot review: the two post-flush collision fallbacks
       carry the same problem+json types as their pre-checks, so a
       client identifies a taken name or address in the racing case too.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.ChangePasswordRequest;
import foc.user.dto.EmailChangePendingResponse;
import foc.user.dto.UpdateAccountRequest;
import foc.user.dto.UpdateOtpResponse;
import foc.user.dto.UserResponse;
import foc.user.entity.AccountUpdateOtp;
import foc.user.entity.PendingEmailChange;
import foc.user.entity.User;
import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.exception.SignupIdentifierTakenException;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.UserRepository;

/**
 * Account updates (design doc §5, issue #92): every change is gated by a
 * code sent to the account's CURRENT email (F2.1.1), re-validated like
 * sign-up (F2.1.2/F2.1.5), and a new email additionally confirms itself
 * with a code sent to the NEW address before it applies (F2.1.3).
 *
 * <p>The gate code is single-use and consumed inside the same transaction
 * that applies the change: a failure that rolls the change back (mail
 * 502, uniqueness refusal) rolls the consumption back too, so the caller
 * retries on the code they have. Only the failures named in noRollbackFor
 * commit — a wrong guess keeps its attempt count, a spent or unusable row
 * stays deleted (AuthService.verifySignup's rules).
 */
@Service
public class AccountUpdateService {

    private static final String OTP_JUST_REQUESTED =
        "A verification code was just requested for this account; check your inbox";

    private final UserRepository userRepository;
    private final AccountUpdateOtpRepository gateOtps;
    private final PendingEmailChangeRepository pendingEmailChanges;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final OtpEmailSender otpEmailSender;
    private final Clock clock;

    @Autowired
    public AccountUpdateService(
            UserRepository userRepository,
            AccountUpdateOtpRepository gateOtps,
            PendingEmailChangeRepository pendingEmailChanges,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender) {
        this(userRepository, gateOtps, pendingEmailChanges, passwordEncoder,
            otpService, otpEmailSender, Clock.systemUTC());
    }

    AccountUpdateService(
            UserRepository userRepository,
            AccountUpdateOtpRepository gateOtps,
            PendingEmailChangeRepository pendingEmailChanges,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            Clock clock) {
        this.userRepository = userRepository;
        this.gateOtps = gateOtps;
        this.pendingEmailChanges = pendingEmailChanges;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.otpEmailSender = otpEmailSender;
        this.clock = clock;
    }

    /** What a PATCH did: the account as saved, and the parked email change if one started. */
    public record UpdateResult(UserResponse user, String pendingEmail,
            long expiresInSeconds, long resendInSeconds) {

        static UpdateResult applied(User user) {
            return new UpdateResult(UserResponse.from(user), null, 0, 0);
        }

        static UpdateResult parked(User user, String pendingEmail, Duration validity, Duration cooldown) {
            return new UpdateResult(UserResponse.from(user), pendingEmail,
                validity.toSeconds(), cooldown.toSeconds());
        }

        public boolean emailParked() {
            return pendingEmail != null;
        }
    }

    // parks (or renews) the gate code and emails it to the account's
    // CURRENT address. @Transactional so the row and its email commit or
    // roll back together (AuthService.signup's reasoning); the row lock
    // makes concurrent requests for one account queue
    @Transactional
    public UpdateOtpResponse requestOtp(Long userId) {
        User user = userRepository.getActiveUser(userId);
        AccountUpdateOtp gate = gateOtps.findWithLockByUserId(userId).orElse(null);
        Instant now = clock.instant();
        boolean expired = gate != null && !gate.getExpiresAt().isAfter(now);

        if (gate != null) {
            ensureCooldownPassed(gate.getLastSentAt(), now);
        }

        String code = otpService.generateCode();
        String codeHash = otpService.hash(code);
        if (gate == null) {
            gate = new AccountUpdateOtp(userId, codeHash, now, now.plus(otpService.ttl()));
        } else if (expired) {
            gate.replaceExpired(codeHash, now, now.plus(otpService.ttl()));
        } else {
            // the resend: fresh code, same expiry and attempts —
            // PendingSignup.renewCode's rules, for the same reasons
            gate.renewCode(codeHash, now);
        }
        // the time actually left, less than a full TTL after a resend:
        // the email and the 202 quote it, not the TTL (PR #150 review)
        Duration validity = Duration.between(now, gate.getExpiresAt());
        try {
            gateOtps.saveAndFlush(gate);
        } catch (DataIntegrityViolationException e) {
            // two first requests for one account at once: no row to lock,
            // the loser's insert hits the unique index (signup's shape)
            throw new ResponseStatusException(HttpStatus.CONFLICT, OTP_JUST_REQUESTED);
        }

        try {
            otpEmailSender.sendAccountUpdateCode(user.getEmail(), code, validity);
        } catch (MailException e) {
            // rolls the row back too: no orphan code nobody received, and
            // on a resend the previous code stays the live one
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the verification email; try again later");
        }

        return new UpdateOtpResponse(validity.toSeconds(), otpService.resendCooldown().toSeconds());
    }

    // noRollbackFor: a wrong gate code must commit its attempt counter,
    // and a spent or expired gate row must stay deleted. Deliberately not
    // the plain ResponseStatusExceptions the uniqueness checks and flush
    // races throw: those must roll back — which restores the gate row, so
    // a caller refused for a taken name retries on the code they have
    // (AuthService.verifySignup's exact reasoning about rollback-only)
    @Transactional(noRollbackFor = {OtpVerificationException.class, OtpAttemptsExceededException.class})
    public UpdateResult updateAccount(Long userId, UpdateAccountRequest request) {
        User user = userRepository.getActiveUser(userId);
        consumeGateOtp(userId, request.otp());

        String username = request.username() == null
            ? null : NewAccountDetails.normalizeUsername(request.username());
        String newEmail = request.email() == null
            ? null : NewAccountDetails.normalizeEmail(request.email());
        // asking for the email you already have changes nothing: nothing
        // parks, no code goes out, the PATCH answers with the account as-is
        if (newEmail != null && newEmail.equals(user.getEmail())) {
            newEmail = null;
        }

        if (username != null) {
            // except-self: renaming bob to Bob must pass (F2.1.2)
            NewAccountDetails.ensureUsernameUniqueExceptSelf(userRepository, userId, username);
            user.setUsername(username);
        }

        UpdateResult result;
        if (newEmail == null) {
            result = UpdateResult.applied(user);
        } else {
            NewAccountDetails.ensureEmailUniqueExceptSelf(userRepository, userId, newEmail);
            Duration validity = parkEmailChange(userId, newEmail);
            result = UpdateResult.parked(user, newEmail, validity, otpService.resendCooldown());
        }

        if (username != null) {
            try {
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                // a concurrent request took the username after the check;
                // rolls back, which also restores the gate row and drops
                // the parked change — a clean retry (verifySignup's shape).
                // Typed like the pre-check's refusal, so username-taken is
                // machine-readable racing or not (PR #157 Copilot review)
                throw NewAccountDetails.usernameTaken("Username was just taken; choose another");
            }
        }
        return result;
    }

    // parks (or renews/replaces) the pending email change under its row
    // lock and emails the confirmation code to the NEW address; returns
    // the code's remaining validity
    private Duration parkEmailChange(Long userId, String newEmail) {
        PendingEmailChange pending = pendingEmailChanges.findWithLockByUserId(userId).orElse(null);
        Instant now = clock.instant();
        boolean expired = pending != null && !pending.getExpiresAt().isAfter(now);

        if (pending != null) {
            // one send per cooldown whatever the address: replacing the
            // pending change must not be a way around the resend limit
            ensureCooldownPassed(pending.getLastSentAt(), now);
        }

        String code = otpService.generateCode();
        String codeHash = otpService.hash(code);
        if (pending == null) {
            pending = new PendingEmailChange(userId, newEmail, codeHash, now, now.plus(otpService.ttl()));
        } else if (!expired && pending.getNewEmail().equals(newEmail)) {
            // the same change again is a resend: fresh code, same expiry
            // and attempts (PendingSignup.renewCode's anti-cycling rules —
            // repeating the PATCH must not buy fresh attempt budgets)
            pending.renewCode(codeHash, now);
        } else {
            // a different address (or an expired row, whatever it held) is
            // a genuinely new change: the caller's login token proves the
            // old row is their own to discard, unlike a pending sign-up's
            pending.replace(newEmail, codeHash, now, now.plus(otpService.ttl()));
        }
        Duration validity = Duration.between(now, pending.getExpiresAt());
        try {
            pendingEmailChanges.saveAndFlush(pending);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, OTP_JUST_REQUESTED);
        }

        try {
            otpEmailSender.sendEmailChangeCode(newEmail, code, validity);
        } catch (MailException e) {
            // rolls back everything: the parked change, a username change
            // in the same PATCH, and the gate consumption — the caller's
            // code is still good for a retry
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the verification email; try again later");
        }
        return validity;
    }

    // noRollbackFor: SignupIdentifierTakenException is the discard of a
    // pending change whose address was taken while the code was in flight
    // — it must commit, or the dead row would refuse its owner until the
    // TTL ran out (the PR #150 re-review lesson)
    @Transactional(noRollbackFor = {OtpVerificationException.class, OtpAttemptsExceededException.class,
        SignupIdentifierTakenException.class})
    public UserResponse verifyEmailChange(Long userId, String code) {
        User user = userRepository.getActiveUser(userId);
        PendingEmailChange pending = pendingEmailChanges.findWithLockByUserId(userId).orElse(null);
        Instant now = clock.instant();
        // no pending row is an honest answer, not a guessing game: the
        // caller is signed in and asking about their own account, so
        // there is no existence to hide (unlike sign-up's dummy hash)
        if (pending == null) {
            throw OtpVerificationException.noPendingEmailChange(
                "No email change is pending; request the change again");
        }
        if (!pending.getExpiresAt().isAfter(now)) {
            pendingEmailChanges.delete(pending);
            throw OtpVerificationException.expired(
                "Code has expired; request the email change again");
        }
        if (!otpService.matches(code, pending.getCodeHash())) {
            pending.incrementAttempts();
            // the attempt that exhausts the codes also discards the
            // pending change, so the caller learns immediately
            if (pending.getAttempts() >= otpService.maxAttempts()) {
                pendingEmailChanges.delete(pending);
                throw new OtpAttemptsExceededException(
                    "Too many incorrect codes; request the email change again for a new code");
            }
            pendingEmailChanges.save(pending);
            throw new OtpVerificationException();
        }

        // the address was free when the change parked; re-check now in
        // case an account (or a verified sign-up) took it since
        try {
            NewAccountDetails.ensureEmailUniqueExceptSelf(userRepository, userId, pending.getNewEmail());
        } catch (ResponseStatusException e) {
            // this change can never complete: discard it (committed via
            // noRollbackFor) so its owner isn't refused for the rest of
            // the TTL over an address they already lost
            pendingEmailChanges.delete(pending);
            throw new SignupIdentifierTakenException(e);
        }

        user.setEmail(pending.getNewEmail());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // a concurrent insert took the email after the check; rolls
            // back (the row survives) and the next verify's re-check
            // discards it (verifySignup's exact race answer). Typed like
            // the pre-check's refusal (PR #157 Copilot review)
            throw NewAccountDetails.emailTaken("Email was just taken; request the change again");
        }
        pendingEmailChanges.delete(pending);
        return UserResponse.from(user);
    }

    // noRollbackFor: the delete of an expired row must commit even though
    // the request is answered with a 400
    @Transactional(noRollbackFor = OtpVerificationException.class)
    public EmailChangePendingResponse resendEmailChange(Long userId) {
        User user = userRepository.getActiveUser(userId);
        PendingEmailChange pending = pendingEmailChanges.findWithLockByUserId(userId).orElse(null);
        Instant now = clock.instant();
        if (pending == null) {
            throw OtpVerificationException.noPendingEmailChange(
                "No email change is pending; request the change again");
        }
        if (!pending.getExpiresAt().isAfter(now)) {
            pendingEmailChanges.delete(pending);
            throw OtpVerificationException.expired(
                "Code has expired; request the email change again");
        }
        ensureCooldownPassed(pending.getLastSentAt(), now);

        String code = otpService.generateCode();
        pending.renewCode(otpService.hash(code), now);
        Duration validity = Duration.between(now, pending.getExpiresAt());
        pendingEmailChanges.save(pending);
        try {
            otpEmailSender.sendEmailChangeCode(pending.getNewEmail(), code, validity);
        } catch (MailException e) {
            // rolls the renewal back: the previous code stays the live one
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the verification email; try again later");
        }
        return new EmailChangePendingResponse(UserResponse.from(user), pending.getNewEmail(),
            validity.toSeconds(), otpService.resendCooldown().toSeconds());
    }

    @Transactional(noRollbackFor = {OtpVerificationException.class, OtpAttemptsExceededException.class})
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.getActiveUser(userId);
        consumeGateOtp(userId, request.otp());
        // the match (F2.1.4) and the policy (F2.1.5) were enforced by the
        // record's annotations before this runs; only the hash is stored
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // presents the gate code (F2.1.1): single-use, so a match deletes the
    // row — inside the caller's transaction, so a change that fails to
    // commit un-consumes it. Wrong guesses count and commit; the guess
    // that exhausts the limit discards the row (verifySignup's rules)
    private void consumeGateOtp(Long userId, String code) {
        AccountUpdateOtp gate = gateOtps.findWithLockByUserId(userId).orElse(null);
        Instant now = clock.instant();
        // honest answers, not sign-up's dummy-hash guessing game: the
        // caller is signed in, their own rows are no secret to them
        if (gate == null) {
            throw OtpVerificationException.required(
                "Request a verification code first");
        }
        if (!gate.getExpiresAt().isAfter(now)) {
            gateOtps.delete(gate);
            throw OtpVerificationException.expired(
                "Code has expired; request a new code");
        }
        if (!otpService.matches(code, gate.getCodeHash())) {
            gate.incrementAttempts();
            if (gate.getAttempts() >= otpService.maxAttempts()) {
                gateOtps.delete(gate);
                throw new OtpAttemptsExceededException(
                    "Too many incorrect codes; request a new code");
            }
            gateOtps.save(gate);
            throw new OtpVerificationException();
        }
        gateOtps.delete(gate);
    }

    // one send per cooldown (flood and attempt-cycling guard, PR #150)
    private void ensureCooldownPassed(Instant lastSentAt, Instant now) {
        Instant nextSendAllowed = lastSentAt.plus(otpService.resendCooldown());
        if (nextSendAllowed.isAfter(now)) {
            throw new OtpResendTooSoonException(Duration.between(now, nextSendAllowed));
        }
    }
}
