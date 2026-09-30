/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: sign-up (issue #87) and login (issue #89), replacing PR #139's
       separate user-auth server (team decision: auth lives in
       user-service). Rules from design doc §3: username-or-email login,
       non-revealing failures, 15 min lockout after 5 consecutive failures,
       counters cleared on success; sign-up checks NUS email, password
       policy and uniqueness with exact 400 reasons. Team decisions: OTP is
       deferred, so sign-up inserts the account directly (the design's
       insert-after-verify shape, with verify skipped for now); a
       soft-deleted account is recovered by logging in within the
       retention window.
       PR #141 review: sign-up's normalising and uniqueness checks shared
       with owner setup (NewAccountDetails); usernames match ignoring case
       at sign-up and login (team decision).
       Issue #147: signup and login are no longer @Transactional, so
       bcrypt runs without holding a pooled connection (team decision);
       the attempt's counter update moved to LoginAttempts, which locks
       the row so parallel wrong guesses each count toward the lockout.
       2026-09-29, Claude Code (Fable 5), PR #142: lockout now raises
       AccountLockedException (a distinct message/status) instead of the
       generic failure — issue #145 decided by the author: the wireframe's
       lockout box wins over fully non-revealing failures, accepting that
       a locked account is thereby revealed to exist.
       2026-09-29, Claude Code (Opus 5), issue #146: each login failure
       now raises its own message (unknown account / wrong password with
       the attempts left / locked with the time left), the author's call.
       The timing-equalisation hash that made an unknown account answer as
       slowly as a wrong password went with it: the messages now say which
       happened, so equal timing hid nothing.
       PR #148 Copilot review: the retention-window check moved ahead of
       the lock and password checks, so an account past the window answers
       as gone whatever password is typed, instead of only when the
       password happened to be right.
       2026-09-29, Claude Code (Fable 5), issue #88: the deferred OTP step
       is in — sign-up now parks the request in pending_signups and emails
       a code (202), and verifySignup (POST /auth/signup/verify) is what
       inserts the users row (design doc: insert after verify). A repeat
       sign-up renews the pending row (that is the resend); a mail failure
       is a 502 and rolls the pending row back; wrong codes count attempts
       like login failures do, and exhausting them discards the pending
       sign-up. Flow decisions chosen by Leong Wei Zhi via options Q&A.
       PR #150 Copilot review: verify locks the pending row so racing
       guesses can't lose attempt increments; an unknown email pays the
       same BCrypt cost as a wrong code so timing doesn't reveal a
       pending sign-up; a code expiring exactly now is expired.
       PR #150 author review (both calls made by Leong Wei Zhi via
       options Q&A). A repeat sign-up used to overwrite the pending row's
       username and password hash, so whoever received the code verified
       it into an account holding someone else's password; a live pending
       row is now only advanced by a repeat of its own details (same
       username, password matching the stored hash), anything else is a
       409, and an expired row — dead already — may be taken over by any
       sign-up. Ordering it the other way round (first request wins, later
       ones merely resend) was tried and dropped: it only reverses who has
       to move first, since a planted pending row would then be what the
       real student's sign-up completed into.
       Also: a repeat inside the resend cooldown is a 429 (Retry-After),
       the attempt count now survives a resend so the limit caps guesses
       per pending sign-up rather than per code, and the insert-race loser
       answers with the same 409 as the check above instead of a 400 whose
       wording was a near-duplicate of it.
       2026-09-30, Claude Code (Opus 5), PR #150 Copilot review: sign-up
       reads the pending row through the locked finder, as verify does, so
       concurrent repeats for one email can't both pass the cooldown and
       overwrite each other's code.
       2026-09-30, Claude Code (Opus 5), PR #150 re-review (@Sinnez1), both
       fixes chosen by Leong Wei Zhi via options Q&A over restructuring to
       one pending row per request: (1) verify discards a pending sign-up
       whose email or username has since been taken, because such a row can
       never complete and used to hold its email — and so lock out whoever
       lost a username race — until the code expired; (2) a resend no longer
       extends expires_at, so a row's life is bounded by OTP_TTL from
       creation and always reaches the "expired, anyone may take it over"
       branch, where before, whoever pended an address first could resend
       once per cooldown for ever and keep its real owner on 409. The email
       and the 202 now quote the time actually left rather than a full TTL.
       Merged with issue #147's LoginAttempts (2026-09-29, Claude Code,
       Opus 5.5): #146's messages now come from the outcome LoginAttempts
       records under the row lock (attempts left, time left, gone), and
       the retention-window check moved there with them.
       2026-09-30, Claude Code (Opus 5), merging main into #88's branch:
       login is #147's (no transaction, LoginAttempts records the attempt);
       sign-up and verify stay @Transactional, because the pending row and
       its email must commit or roll back together — a mail failure leaves
       no row holding a code nobody received — and verify's attempt
       counter rides the same row lock. #147's reason for dropping the
       transaction (bcrypt shouldn't hold a pooled connection) is answered
       here by the finite SMTP timeouts added in PR #150's Copilot review.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.LoginRequest;
import foc.user.dto.LoginResponse;
import foc.user.dto.SignupRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.AccountLockedException;
import foc.user.exception.LoginFailedException;
import foc.user.exception.OtpAttemptsExceededException;
import foc.user.exception.OtpResendTooSoonException;
import foc.user.exception.OtpVerificationException;
import foc.user.exception.SignupIdentifierTakenException;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.security.JwtIssuer;

@Service
public class AuthService {

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
    private final JwtIssuer jwtIssuer;
    private final OtpService otpService;
    private final OtpEmailSender otpEmailSender;
    private final LoginAttempts loginAttempts;
    private final Clock clock;
    // matched against when no pending sign-up exists, so an unknown
    // email takes as long to verify as a wrong code (same trick login
    // used before #146 made its messages revealing; these stay identical)
    private final String unknownPendingCodeHash;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PendingSignupRepository pendingSignupRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            LoginAttempts loginAttempts) {
        this(userRepository, pendingSignupRepository, passwordEncoder, jwtIssuer,
            otpService, otpEmailSender, loginAttempts, Clock.systemUTC());
    }

    AuthService(
            UserRepository userRepository,
            PendingSignupRepository pendingSignupRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            OtpService otpService,
            OtpEmailSender otpEmailSender,
            LoginAttempts loginAttempts,
            Clock clock) {
        this.userRepository = userRepository;
        this.pendingSignupRepository = pendingSignupRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.otpService = otpService;
        this.otpEmailSender = otpEmailSender;
        this.loginAttempts = loginAttempts;
        this.clock = clock;
        this.unknownPendingCodeHash = otpService.hash("no-pending-signup");
    }

    // parks the request in pending_signups and emails a code; the users
    // row is only inserted by verifySignup (design doc: insert after
    // verify, so an unverified sign-up never holds the email/username).
    // @Transactional unlike #147's login: the pending row and its email
    // commit or roll back together
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = NewAccountDetails.normalizeEmail(request.email());
        String username = NewAccountDetails.normalizeUsername(request.username());
        NewAccountDetails.ensureUnique(userRepository, email, username);

        // one pending row per email, and while it is live it belongs to
        // the request that created it (PR #150 review, shape chosen by
        // Leong Wei Zhi via options Q&A). Merging a later request into it
        // was a hijack either way round: whoever received the code then
        // verified it into an account holding someone else's password
        //
        // Read under the same row lock verify uses, so two repeats for one
        // email queue instead of interleaving (PR #150 Copilot review):
        // unlocked, both could pass the cooldown check and write different
        // codes, leaving one of the two emails dead on arrival — and on
        // the expired-takeover path below, one request's credentials could
        // end up paired with the other's code. The cost is that the lock
        // is held across the send; the SMTP timeouts bound that
        PendingSignup pending = pendingSignupRepository.findWithLockByEmail(email).orElse(null);
        Instant now = clock.instant();
        // an expired row is dead — verify rejects it on sight — so it
        // guards nothing and any sign-up may take it over
        boolean expired = pending != null && !pending.getExpiresAt().isAfter(now);

        if (pending != null && !expired && !isSameRequest(request, username, pending)) {
            // refused, not merged, and not silently replaced: only the
            // request that pended this email can move it along, and it
            // proves that by repeating its own details
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIGNUP_IN_PROGRESS);
        }
        if (pending != null) {
            // one code per cooldown: without it a resend could flood the
            // inbox, and codes could be cycled fast enough to make the
            // attempt limit meaningless
            Instant nextSendAllowed = pending.getLastSentAt().plus(otpService.resendCooldown());
            if (nextSendAllowed.isAfter(now)) {
                throw new OtpResendTooSoonException(Duration.between(now, nextSendAllowed));
            }
        }

        String code = otpService.generateCode();
        String codeHash = otpService.hash(code);
        if (pending == null) {
            pending = new PendingSignup(email, username, passwordEncoder.encode(request.password()),
                codeHash, now, now.plus(otpService.ttl()));
        } else if (expired) {
            pending.replaceExpired(username, passwordEncoder.encode(request.password()),
                codeHash, now, now.plus(otpService.ttl()));
        } else {
            // the resend: a fresh code for the details already stored,
            // with the attempt count carried over, so the limit caps
            // guesses per pending sign-up rather than per code — and
            // deliberately not a fresh expiry (PR #150 review): a row that
            // renewed its own expiry could be resent indefinitely, holding
            // an address for ever and making the 409's "try again once it
            // expires" a promise nobody could collect on
            pending.renewCode(codeHash, now);
        }
        // the time actually left, which after a resend is less than a full
        // TTL: both the email and the 202 quote it rather than the TTL, so
        // neither claims a freshness the code doesn't have
        Duration validity = Duration.between(now, pending.getExpiresAt());
        try {
            pendingSignupRepository.saveAndFlush(pending);
        } catch (DataIntegrityViolationException e) {
            // two first sign-ups for one email at once: there was no row
            // to lock, so the loser's insert hits the unique index. Answered
            // as the same 409 as a pending row found up front — it is the
            // same situation, half a millisecond earlier
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIGNUP_IN_PROGRESS);
        }

        try {
            otpEmailSender.sendSignupCode(email, code, validity);
        } catch (MailException e) {
            // rolls the pending row back too: no orphan row holding a code
            // nobody received, and a retry is a clean fresh sign-up — for
            // a resend the rollback restores the previous code, which is
            // still the live one as far as the caller's inbox knows
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not send the verification email; try again later");
        }

        return new SignupResponse(email, validity.toSeconds(),
            otpService.resendCooldown().toSeconds());
    }

    // Is this repeat the very request that pended the email? Same
    // username (matched the way sign-up matches them, ignoring case) and
    // a password matching the stored hash — which nobody but that
    // requester can produce, so it is what stands in for "the same
    // person" without a token to carry between the two calls
    private boolean isSameRequest(SignupRequest request, String username, PendingSignup pending) {
        return pending.getUsername().equalsIgnoreCase(username)
            && passwordEncoder.matches(request.password(), pending.getPasswordHash());
    }

    // noRollbackFor: a wrong code must still commit its attempt counter,
    // and a spent, expired or unusable pending row must stay deleted (same
    // trick as login's failure counters). Deliberately not the plain
    // ResponseStatusException the insert race below throws: that one comes
    // after a failed flush, and a transaction Hibernate has already marked
    // rollback-only cannot be committed — asking would turn a 400 into an
    // UnexpectedRollbackException 500
    @Transactional(noRollbackFor = {OtpVerificationException.class, OtpAttemptsExceededException.class,
        SignupIdentifierTakenException.class})
    public UserResponse verifySignup(SignupVerifyRequest request) {
        String email = NewAccountDetails.normalizeEmail(request.email());
        // the row lock serializes concurrent guesses at one pending
        // sign-up, so every wrong code's attempt increment lands
        Optional<PendingSignup> found = pendingSignupRepository.findWithLockByEmail(email);
        if (found.isEmpty()) {
            // no pending row answers exactly like a wrong code — same
            // message, and the same BCrypt cost so timing reveals nothing
            otpService.matches(request.code(), unknownPendingCodeHash);
            throw new OtpVerificationException();
        }
        PendingSignup pending = found.get();

        if (!pending.getExpiresAt().isAfter(clock.instant())) {
            pendingSignupRepository.delete(pending);
            throw new OtpVerificationException("Code has expired; sign up again to get a new code");
        }

        if (!otpService.matches(request.code(), pending.getCodeHash())) {
            pending.incrementAttempts();
            // the attempt that exhausts the codes also discards the pending
            // sign-up, so the caller learns immediately (login's lockout
            // reports on the tripping attempt for the same reason)
            if (pending.getAttempts() >= otpService.maxAttempts()) {
                pendingSignupRepository.delete(pending);
                throw new OtpAttemptsExceededException();
            }
            pendingSignupRepository.save(pending);
            throw new OtpVerificationException();
        }

        // the identifiers were free at sign-up; re-check now in case an
        // account (or another verified pending sign-up) took them since
        try {
            NewAccountDetails.ensureUnique(userRepository, email, pending.getUsername());
        } catch (ResponseStatusException e) {
            // this sign-up can never complete, so it must stop holding its
            // email: leaving the row (PR #150 review) locked out whoever
            // merely lost a username race for the rest of the TTL — they
            // could neither re-sign-up under a new name (409, the stored
            // details differ) nor the old one (taken). Deleting here and
            // committing it is what the noRollbackFor above is for; the
            // lock this row is under rules out a separate transaction
            pendingSignupRepository.delete(pending);
            throw new SignupIdentifierTakenException(e.getReason());
        }
        // the hash stored at sign-up is already BCrypt: never re-encode
        User user = new User(email, pending.getUsername(), pending.getPasswordHash(), Role.USER);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // a concurrent sign-up took the email or username after the checks
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Email or username was just taken; choose another");
        }
        pendingSignupRepository.delete(pending);
        return UserResponse.from(user);
    }

    // Not @Transactional: the lookup and the bcrypt check run without
    // holding a pooled connection; LoginAttempts then records the attempt
    // in its own short transaction
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = findAccount(request.usernameOrEmail());
        if (found.isEmpty()) {
            throw LoginFailedException.unknownAccount();
        }

        User user = found.get();
        Instant now = clock.instant();

        // past the recovery window the account is waiting for the purge, so
        // it answers as if it were already gone — before the lock and the
        // password, so every attempt on it gets that one answer and none of
        // them count against a row that is on its way out
        if (loginAttempts.pastRetention(user, now)) {
            throw LoginFailedException.unknownAccount();
        }

        // locked: refused without counting the attempt, and told how long
        // is left rather than the full lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }

        // bcrypt, outside any transaction; LoginAttempts then counts the
        // attempt with the row locked, re-checking the lock a parallel
        // attempt may have set meanwhile
        boolean matched = passwordEncoder.matches(request.password(), user.getPasswordHash());
        LoginAttempts.Result result = loginAttempts.record(user.getId(), matched, now);
        return switch (result.outcome()) {
            case SUCCESS -> LoginResponse.bearer(jwtIssuer.issue(result.user()), jwtIssuer.ttl().toSeconds());
            // the attempt that trips the lock reports the lockout too, so
            // the user learns immediately rather than on the next try
            case LOCKED -> throw new AccountLockedException(result.retryAfter());
            case FAILED -> throw LoginFailedException.wrongPassword(result.attemptsLeft());
            // purged since the lookup
            case GONE -> throw LoginFailedException.unknownAccount();
        };
    }

    // usernames can't contain '@', so an '@' means an email
    private Optional<User> findAccount(String usernameOrEmail) {
        String identifier = usernameOrEmail.trim();
        return identifier.contains("@")
            ? userRepository.findByEmail(identifier.toLowerCase())
            : userRepository.findByUsernameIgnoreCase(identifier);
    }
}
