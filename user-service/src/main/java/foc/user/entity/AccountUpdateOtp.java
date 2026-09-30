/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: gate-code row for issue #92 (F2.1.1): POST /users/me/otp parks
       a hashed code here, mailed to the account's CURRENT email, and
       the mutating request (PATCH /users/me, POST /users/me/password)
       presents and consumes it. One pending table per operation
       (design decision by Leong Wei Zhi via options Q&A on PR #150);
       single-use gate, code carried in the mutating request (owner
       Q&A, 2026-09-30).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A gate code for an account update (design doc §5, issue #92):
 * possession of the account's current inbox, proven before any change
 * (F2.1.1). Single-use — the mutating request that matches it deletes
 * the row — and one live row per account; {@code attempts} counts
 * wrong guesses against it, resends included, exactly like a pending
 * sign-up's.
 */
@Entity
@Table(
    name = "account_update_otps",
    indexes = @Index(name = "idx_account_update_otps_user", columnList = "user_id", unique = true)
)
public class AccountUpdateOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected AccountUpdateOtp() {
    }

    public AccountUpdateOtp(Long userId, String codeHash, Instant sentAt, Instant expiresAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    /**
     * A repeat request resends: fresh code, fresh send time, nothing
     * else — expiry bounds the row's life at its original TTL and
     * attempts keep counting, {@link PendingSignup#renewCode}'s rules.
     */
    public void renewCode(String codeHash, Instant sentAt) {
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
    }

    /**
     * Takes an expired row over for a new request: everything fresh,
     * attempts reset — the code it held is dead (rejected on sight by
     * the verify path). Reuse in place keeps the row in its unique
     * index slot ({@link PendingSignup#replaceExpired}'s reasoning).
     */
    public void replaceExpired(String codeHash, Instant sentAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    /** When the current code was emailed; the resend cooldown's origin. */
    public Instant getLastSentAt() {
        return lastSentAt;
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
