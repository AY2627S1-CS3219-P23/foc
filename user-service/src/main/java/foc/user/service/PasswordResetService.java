/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: the password-reset flow for issue #94 (F4.2), design doc §2's
       account_tokens table. Contract decisions by Leong Wei Zhi via
       options Q&A (2026-10-11): the reset credential is an emailed
       6-digit OTP code (revisited the same day from an opaque link
       token — the OTP shape every other flow already uses, and the
       SPA's code dialog with it); the request endpoint answers 202
       whatever the email maps to — unknown, deleted-past-window, and
       inside-the-cooldown requests are all silently absorbed, because
       a 429 or 404 would reveal which addresses hold accounts; a
       soft-deleted account inside the 30-day window CAN reset (the
       deleted user who forgot their password has no other door) but
       stays deleted — recovery is login + POST /users/me/recover.
       Confirm identifies the row by the email in its body (a salted
       BCrypt code hash cannot be looked up), and EVERY confirm
       failure — wrong code, unknown email, expired, attempts spent —
       is the one reset-code-invalid 400 at the same BCrypt cost:
       unlike sign-up's verify, whose caller just created the pending
       row, this route is where an attacker probes other people's
       accounts, so even the expired/exhausted distinctions (and their
       429) would leak which addresses hold one. The attempt cap holds
       silently: a spent row refuses even the right code until a fresh
       request replaces it past the cooldown.
       Accepted and documented leaks: a MailException still answers
       502 (only reachable for real accounts — consistent with
       sign-up's convention), and the synchronous send makes a known
       email measurably slower than an unknown one.
       Codes ride OtpService whole (BCrypt for the 10^6 space, the
       user.otp.* ttl/attempts/cooldown knobs — no reset-specific
       configuration). Lock order follows the account-update flows:
       user row first, then OTP row. The storage is password_reset_otps
       (V7 remakes #86's account_tokens scaffold — owner decision via
       options Q&A: one use left, since recovery carries no emailed
       credential, so the table takes the per-operation shape and
       naming of its siblings, without the kind discriminator).
Author review: Leong Wei Zhi to review via the PR.
2026-10-11 (Claude Code, Fable 5), PR #179 Copilot review: both flows'
FIRST read is now the locked one (findWithLockByEmail) — read plain
and lock by id later, and the managed entity keeps its stale state
(LoginAttempts' refresh note), so a code could be mailed to, or
checked for, an address a concurrent email change just replaced; and
an account purged between lookups now falls through the same
dummy-match branch as an unknown email, instead of a cheaper,
distinguishable throw.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.PasswordResetConfirmRequest;
import foc.user.entity.PasswordResetOtp;
import foc.user.entity.User;
import foc.user.exception.ResetCodeInvalidException;
import foc.user.repository.PasswordResetOtpRepository;
import foc.user.repository.UserRepository;

/**
 * Password reset via emailed single-use 6-digit code (issue #94,
 * F4.2). {@code requestReset} parks a hashed code in
 * {@code password_reset_otps} and mails it; {@code confirmReset} consumes
 * it and writes the new password. Everything a caller can observe —
 * from the request endpoint's 202 to the confirm endpoint's one
 * failure answer — is identical whether or not the email has an
 * account.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository resetOtps;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final OtpEmailSender otpEmailSender;
    private final Clock clock;
    private final int retentionDays;
    // matched when there is no row to check, so an unknown email pays
    // the same BCrypt cost as a wrong code (the sign-up verify trick)
    private final String unknownCodeHash;

    @Autowired
    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetOtpRepository resetOtps,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            @Value("${user.retention.days}") int retentionDays) {
        this(userRepository, resetOtps, passwordEncoder, otpService,
            otpEmailSender, retentionDays, Clock.systemUTC());
    }

    PasswordResetService(
            UserRepository userRepository,
            PasswordResetOtpRepository resetOtps,
            PasswordEncoder passwordEncoder,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            int retentionDays,
            Clock clock) {
        this.userRepository = userRepository;
        this.resetOtps = resetOtps;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.otpEmailSender = otpEmailSender;
        this.retentionDays = retentionDays;
        this.clock = clock;
        this.unknownCodeHash = otpService.hash("no-reset-code");
    }

    /**
     * Parks a hashed reset code and emails it. Returns silently — the
     * controller's 202 — for an unknown email, an account past the
     * retention window, and a repeat inside the resend cooldown alike:
     * any distinct answer (a 404, a 429 with Retry-After) would reveal
     * which addresses hold accounts, which is the one thing this
     * endpoint must not do (owner decision; contrast #146's revealing
     * login errors, where the caller already named an account).
     */
    @Transactional
    public void requestReset(String email) {
        // the FIRST read takes the user lock (PR #179 Copilot review:
        // a plain read followed by a lock-by-id kept the entity's
        // stale state, so the code could be mailed to an address a
        // concurrent email change had just replaced). User row first,
        // then OTP row — the account-update flows' lock order; and
        // serializing on the user means two first requests can't both
        // find no OTP row, so the insert below can't trip V7's
        // per-user unique index
        User user = userRepository.findWithLockByEmail(NewAccountDetails.normalizeEmail(email))
            .orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        // a deleted account inside the window can still reset (decision
        // 3: it stays deleted; login + restore recovers it) — past the
        // window it is waiting for the purge and answers like unknown,
        // the same line login draws (LoginAttempts.pastRetention)
        if (pastRetention(user, now)) {
            return;
        }

        PasswordResetOtp otp = resetOtps.findWithLockByUserId(user.getId()).orElse(null);
        if (otp != null && now.isBefore(otp.getCreatedAt().plus(otpService.resendCooldown()))) {
            // one mail per cooldown, absorbed silently (see javadoc); the
            // code already in the inbox stays live
            return;
        }

        String code = otpService.generateCode();
        String codeHash = otpService.hash(code);
        Instant expiresAt = now.plus(otpService.ttl());
        if (otp == null) {
            otp = new PasswordResetOtp(user, codeHash, now, expiresAt);
        } else {
            // the locked row renews in place — fresh code, life and
            // attempt budget — keeping its unique per-user slot
            otp.replace(codeHash, now, expiresAt);
        }
        resetOtps.save(otp);

        try {
            otpEmailSender.sendPasswordResetCode(user.getEmail(), code, otpService.ttl());
        } catch (MailException e) {
            // rolls the token row back: no live code nobody received.
            // The 502 is observable only for real accounts — an accepted
            // existence leak, consistent with sign-up's (documented in
            // the README); swallowing it would strand a legitimate user
            // waiting on an email that never comes
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the reset email; try again later");
        }
    }

    /**
     * Consumes a reset code and writes the new password. One answer —
     * {@link ResetCodeInvalidException}, a 400, at one BCrypt cost —
     * for every failure: wrong code, unknown email, no code requested,
     * expired, attempts spent, account past the window. Any
     * distinction would let an outsider probe which addresses hold
     * accounts (see the header). On success the lockout counters clear
     * too (the reset doubles as the lockout escape hatch), but
     * {@code deletedAt} is deliberately untouched (decision 3).
     */
    // noRollbackFor: a wrong code must still commit its attempt
    // counter, and an expired row's delete must commit alongside the
    // 400 that reports it (verifySignup's trick)
    @Transactional(noRollbackFor = ResetCodeInvalidException.class)
    public void confirmReset(PasswordResetConfirmRequest request) {
        // the FIRST read takes the user lock, and it deliberately
        // includes soft-deleted accounts (a deleted account inside the
        // window resets, and stays deleted). User row first, then OTP
        // row (one lock order): the password write must not race a
        // concurrent failed-login counter into an @Version conflict,
        // and the attempt counter below needs the OTP row serialized
        User user = userRepository.findWithLockByEmail(NewAccountDetails.normalizeEmail(request.email()))
            .orElse(null);
        Instant now = clock.instant();
        if (user == null || pastRetention(user, now)) {
            // no account to have a code — unknown email, purged since
            // the caller requested, or past the window alike: one
            // answer, at the same BCrypt cost (PR #179 Copilot review
            // caught the purged case throwing without the dummy match)
            otpService.matches(request.code(), unknownCodeHash);
            throw new ResetCodeInvalidException();
        }
        PasswordResetOtp otp = resetOtps.findWithLockByUserId(user.getId()).orElse(null);
        if (otp == null) {
            otpService.matches(request.code(), unknownCodeHash);
            throw new ResetCodeInvalidException();
        }

        if (!otp.getExpiresAt().isAfter(now)) {
            // dead on sight; deleting it here (committed via
            // noRollbackFor) beats waiting for the purge sweep
            resetOtps.delete(otp);
            otpService.matches(request.code(), unknownCodeHash);
            throw new ResetCodeInvalidException();
        }
        // spent: answers like a wrong code — same message, same cost —
        // and stays until a fresh request replaces it past the cooldown
        // (createdAt still gates that), so burning the attempts neither
        // buys a fresh budget nor announces itself with a 429
        if (otp.getAttempts() >= otpService.maxAttempts()) {
            otpService.matches(request.code(), unknownCodeHash);
            throw new ResetCodeInvalidException();
        }

        if (!otpService.matches(request.code(), otp.getCodeHash())) {
            // saved even on the guess that exhausts the budget: the row
            // must survive as spent so its createdAt keeps gating the
            // next code (the PR #157 lesson from sign-up)
            otp.incrementAttempts();
            resetOtps.save(otp);
            throw new ResetCodeInvalidException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // possession of the inbox proved the owner, so the lockout a
        // guesser caused need not outlive the password they were
        // guessing at; deletedAt deliberately stays (decision 3)
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        // single use
        resetOtps.delete(otp);
    }

    private boolean pastRetention(User user, Instant now) {
        return user.getDeletedAt() != null
            && user.getDeletedAt().isBefore(now.minus(retentionDays, ChronoUnit.DAYS));
    }
}
