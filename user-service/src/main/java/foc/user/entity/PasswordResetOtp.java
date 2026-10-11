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
     * A repeat request (past the cooldown) renews the row in place:
     * fresh code, fresh life, fresh attempt budget — unlike a pending
     * sign-up's resend, which keeps both the expiry and the count. The
     * difference is deliberate: this row holds nothing hostage (the
     * code only ever lands in the account's own inbox), so the
     * hold-the-address-for-ever and attempt-cycling concerns behind
     * {@link PendingSignup#renewCode}'s rules don't apply, and the
     * cooldown alone caps how fast budgets can be renewed. Reuse in
     * place keeps the row in its unique user_id slot
     * ({@link AccountUpdateOtp#replaceExpired}'s reasoning).
     */
    public void replace(String codeHash, Instant now, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
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
