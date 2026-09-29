/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: pending-signup row for issue #88, implementing the design doc's
       "insert user after verify" choice: the account requested at
       POST /auth/signup lives here — with its hashed OTP code — until
       POST /auth/signup/verify inserts the real users row. One row per
       email (unique index); a repeat sign-up renews the row in place,
       which is also the resend mechanism. Replaces the generic otps
       table for sign-up (pending-tables design chosen by Leong Wei Zhi
       via options Q&A; email change gets its own table with #92).
Reviewed by: Leong Wei Zhi (via pull request).
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
 * A sign-up awaiting OTP verification (design doc §3, insert-after-
 * verify). Holds everything needed to create the account once the code
 * matches; nothing is written to {@code users} until then, so an
 * unverified sign-up never reserves the unique email/username there.
 * The OTP code is stored hashed, never plain; {@code attempts} counts
 * verification tries against this code.
 *
 * <p>Only the email is unique here: two people may pend the same
 * username, and the first to verify wins ({@code users}' own unique
 * index decides).
 */
@Entity
@Table(
    name = "pending_signups",
    indexes = @Index(name = "idx_pending_signups_email", columnList = "email", unique = true)
)
public class PendingSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected PendingSignup() {
    }

    public PendingSignup(String email, String username, String passwordHash,
            String codeHash, Instant expiresAt) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    /**
     * A repeat sign-up for the same email replaces the pending details
     * in place: fresh code, fresh expiry, attempts back to zero. Renewal
     * rather than delete-and-insert, so the row never leaves its unique
     * index slot mid-transaction.
     */
    public void renew(String username, String passwordHash, String codeHash, Instant expiresAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getCodeHash() {
        return codeHash;
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
