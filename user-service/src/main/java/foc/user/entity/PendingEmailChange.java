/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: pending-email-change row for issue #92 (F2.1.3), the table
       pending_signups' header promised — one pending table per
       operation (design decision by Leong Wei Zhi via options Q&A on
       PR #150). An email change requested at PATCH /users/me parks
       here with the code mailed to the NEW address, hashed; nothing
       touches the users row until POST /users/me/email/verify.
       Contract decisions (owner Q&A, 2026-09-30): the row's owner may
       renew (same address) or replace (different address) it — their
       login token proves identity, so pending_signups' same-request
       rule has no analog here — and renewCode keeps expiry and
       attempts for the same anti-cycling reasons as sign-up's.
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
 * An email change awaiting confirmation from the new address (design
 * doc §5, issue #92). The account keeps its current email until the
 * code — sent to the new address, stored hashed — is verified, so a
 * typo'd address can never lock the account out. {@code attempts}
 * counts verification tries against this change, resends included.
 *
 * <p>One pending change per account ({@code user_id} unique); the new
 * email is deliberately not unique here — two accounts may pend the
 * same address, and verify's re-check against {@code users} decides
 * who gets it (the pending sign-ups' username rule).
 */
@Entity
@Table(
    name = "pending_email_changes",
    indexes = @Index(name = "idx_pending_email_changes_user", columnList = "user_id", unique = true)
)
public class PendingEmailChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "new_email", nullable = false)
    private String newEmail;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected PendingEmailChange() {
    }

    public PendingEmailChange(Long userId, String newEmail, String codeHash,
            Instant sentAt, Instant expiresAt) {
        this.userId = userId;
        this.newEmail = newEmail;
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    /**
     * A repeat request for the same address resends: a fresh code and a
     * fresh send time, nothing else. Expiry stays put so the row's life
     * is bounded by the TTL it was created with, and attempts stay so
     * the limit caps guesses per pending change, not per code — both
     * rules inherited from {@link PendingSignup#renewCode} and the
     * PR #150 reviews that shaped them.
     */
    public void renewCode(String codeHash, Instant sentAt) {
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
    }

    /**
     * Replaces this row with a change to a different address (or takes
     * over an expired row): everything fresh, attempts reset — it is a
     * genuinely new change, not a resend. Safe on a live row here,
     * unlike {@link PendingSignup#replaceExpired}: the caller's login
     * token proves the row is their own to discard, and a live pending
     * change reserves nothing from anyone else. Reuse in place keeps
     * the row in its unique index slot.
     */
    public void replace(String newEmail, String codeHash, Instant sentAt, Instant expiresAt) {
        this.newEmail = newEmail;
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

    public String getNewEmail() {
        return newEmail;
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
