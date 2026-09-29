/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: OTP code policy for issue #88: generation, hashing and the
       ttl/max-attempts knobs. Storage-free on purpose so the sign-up
       flow (pending_signups) and the later email-change flow (#92) can
       share it. BCrypt over a plain digest chosen because a 6-digit
       code has only 10^6 possibilities — an unsalted hash of that
       space falls to offline brute force instantly on a DB leak, while
       BCrypt's work factor outlasts the code's lifetime; it also
       reuses the existing PasswordEncoder bean.
       PR #150 review: resend-cooldown knob added (the cooldown itself
       was chosen by Leong Wei Zhi via options Q&A); zero disables it,
       which is what the integration tests resend under.
Reviewed by: Leong Wei Zhi (via pull request).
*/

package foc.user.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and checks one-time codes (design doc §2: codes are stored
 * hashed, never plain). Holds no state beyond configuration — the
 * caller owns where the hash lives and when it expires.
 */
@Component
public class OtpService {

    static final int CODE_DIGITS = 6;

    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder passwordEncoder;
    private final Duration ttl;
    private final int maxAttempts;
    private final Duration resendCooldown;

    public OtpService(
            PasswordEncoder passwordEncoder,
            // a bare number is seconds (OTP_TTL=600 is 10 min)
            @Value("${user.otp.ttl}") @DurationUnit(ChronoUnit.SECONDS) Duration ttl,
            @Value("${user.otp.max-attempts}") int maxAttempts,
            // a bare number is seconds (OTP_RESEND_COOLDOWN=60 is 1 min)
            @Value("${user.otp.resend-cooldown}") @DurationUnit(ChronoUnit.SECONDS) Duration resendCooldown) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("OTP_TTL must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException("OTP_MAX_ATTEMPTS must be at least 1");
        }
        if (resendCooldown == null || resendCooldown.isNegative()) {
            throw new IllegalStateException("OTP_RESEND_COOLDOWN must not be negative");
        }
        this.passwordEncoder = passwordEncoder;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
    }

    /** A fresh 6-digit code, zero-padded, from a CSPRNG. */
    public String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(1_000_000));
    }

    public String hash(String code) {
        return passwordEncoder.encode(code);
    }

    public boolean matches(String code, String codeHash) {
        return passwordEncoder.matches(code, codeHash);
    }

    public Duration ttl() {
        return ttl;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * How long a pending sign-up must wait between codes. Caps both the
     * emails a repeat sign-up can send to someone's inbox and how fast
     * fresh codes can be requested; zero turns it off.
     */
    public Duration resendCooldown() {
        return resendCooldown;
    }
}
