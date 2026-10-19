/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-11, issue #94.
 * Scope: AccountToken, renamed with its table (V7) once the flows
 * settled. The #86 scaffold was generic — a kind discriminator for
 * PASSWORD_RESET and RECOVERY tokens — but recovery became login +
 * explicit restore (no emailed credential) and the reset credential
 * became a 6-digit OTP code, leaving one use; the owner chose the
 * pending-table-per-operation shape and naming the siblings use
 * (AccountUpdateOtp's parallel) via options Q&A. @ManyToOne FK
 * representation carries over from the scaffold; decisions by
 * Leong Wei Zhi via options Q&A throughout.
 * Author review: Leong Wei Zhi to review via the PR.
 * 2026-10-12 (Claude Code, Fable 5), PR #179 review (@Sinnez1):
 * replace() split into renew() (live row: attempts carried, the
 * brute-force fix the owner chose via options Q&A) and
 * replaceExpired() (dead row: fresh budget).
 * 2026-10-12 (Claude Code, Fable 5), PR #179 approval follow-up
 * (@Sinnez1): restore() added so a failed send on a renewal puts the
 * pre-renewal row back (owner decision via options Q&A; the
 * alternatives were leaving the renewed row in place or documenting
 * the budget-reset exception).
 */
package foc.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Emailed single-use 6-digit code for password reset (design doc §2,
 * issue #94). Only the code's BCrypt hash lands here — never the code,
 * and never in logs. One live row per account (V7's unique index); a
 * repeat request past the cooldown renews the row in place;
 * {@code attempts} counts wrong guesses against it, like a pending
 * sign-up's.
 */
@Entity
@Table(
    name = "password_reset_otps",
    indexes = @Index(name = "idx_password_reset_otps_user", columnList = "user_id", unique = true)
)
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected PasswordResetOtp() {
    }

    public PasswordResetOtp(User user, String codeHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * A repeat request (past the cooldown) renews the live row in
     * place — fresh code, fresh life, but the wrong-guess count
     * carries over, {@link PendingSignup#renewCode}'s rule. An earlier
     * revision handed out a fresh budget on the grounds that the
     * cooldown caps renewal speed, but the cooldown only limits how
     * often a code can be <em>read</em> from the inbox; a guesser who
     * never sees it could cycle request-then-five-guesses every minute
     * indefinitely (PR #179 review, @Sinnez1). Carrying the count
     * holds the budget to {@code OTP_MAX_ATTEMPTS} guesses per row
     * lifetime; only a dead row ({@link #replaceExpired}) starts a new
     * one. Reuse in place keeps the row in its unique user_id slot
     * ({@link AccountUpdateOtp#replaceExpired}'s reasoning).
     */
    public void renew(String codeHash, Instant now, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /**
     * Takes an expired row over for a new request: everything replaced,
     * attempts included — the code those attempts were spent against is
     * dead (confirm refuses an expired row on sight), so the count
     * protects nothing. Natural expiry is deliberately the <em>only</em>
     * way a fresh budget appears: with {@link #renew} carrying the
     * count, a guesser gets at most one budget per {@code OTP_TTL}.
     */
    public void replaceExpired(String codeHash, Instant now, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    /**
     * Puts a renewed row back to its pre-renewal state after the new
     * code's email failed to send: the delivered code (and its clock)
     * return, so the user's inbox stays truthful and the failed send
     * does not become a second budget-renewal path besides expiry
     * (PR #179 approval follow-up, @Sinnez1). {@code attempts} is
     * deliberately untouched — {@link #renew} carried it, and any
     * wrong guesses since only incremented it.
     */
    public void restore(String codeHash, Instant createdAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    /** When the current code was emailed; the resend cooldown's origin. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}
