/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-29
Scope: pending-signup row for issue #88, implementing the design doc's
       "insert user after verify" choice: the account requested at
       POST /auth/signup lives here — with its hashed OTP code — until
       POST /auth/signup/verify inserts the real users row. One row per
       email (unique index); a repeat sign-up renews the row in place,
       which is also the resend mechanism (see the PR #150 note below
       for who is allowed to renew it). Replaces the generic otps
       table for sign-up (pending-tables design chosen by Leong Wei Zhi
       via options Q&A; email change gets its own table with #92).
       PR #150 review (renewal shape chosen by Leong Wei Zhi via options
       Q&A): renew is split in two so no request can ever complete on
       another request's credentials. renewCode is the resend — code
       only, details and attempts untouched; replaceExpired is the fresh
       start a dead row allows. A repeat that is neither (someone else's
       live pending sign-up) is refused by AuthService instead.
       last_sent_at added for the resend cooldown.
       PR #150 re-review: renewCode no longer moves expires_at, so a
       resend cannot extend the row's life (decision by Leong Wei Zhi via
       options Q&A).
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
 * verification tries against this pending sign-up — resends included,
 * so the attempt limit caps guesses per sign-up rather than per code.
 *
 * <p>Only the email is unique here: two people may pend the same
 * username, and the first to verify wins ({@code users}' own unique
 * index decides). While this row is live it belongs to the request that
 * created it: only that request's own details may resend a code for it
 * (PR #150 review), and {@code lastSentAt} is what the resend cooldown
 * measures from. Once it expires the row is dead and any sign-up may
 * take it over.
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

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected PendingSignup() {
    }

    public PendingSignup(String email, String username, String passwordHash,
            String codeHash, Instant sentAt, Instant expiresAt) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    /**
     * A repeat sign-up for the same email resends: a fresh code and a
     * fresh send time, and nothing else. Deliberately not the username,
     * the password hash or the attempt count — the request that pended
     * this email decided those, and a later request (which need not come
     * from the same person) may not rewrite them (PR #150 review).
     * Keeping the attempts means the limit caps guesses per pending
     * sign-up, not per code.
     *
     * <p>Deliberately not {@code expiresAt} either, for the same review:
     * a row that renewed its own expiry could be resent once per cooldown
     * for ever, so whoever pended an address first could hold it
     * indefinitely while its real owner kept getting the 409 that tells
     * them to try again once it expires. Left alone, the row's life is
     * bounded by the TTL it was created with.
     *
     * <p>Renewal in place rather than delete-and-insert, so the row never
     * leaves its unique index slot.
     */
    public void renewCode(String codeHash, Instant sentAt) {
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
    }

    /**
     * Takes an expired row over for a new sign-up: every field replaced
     * and the attempts reset, because the sign-up it held is dead (verify
     * rejects an expired row on sight). Reuse rather than delete-and-
     * insert keeps the row in its unique index slot, which a flush
     * ordering inserts before deletes would otherwise trip.
     *
     * <p>Only for an expired row: doing this to a live one is the
     * hijack PR #150's review found.
     */
    public void replaceExpired(String username, String passwordHash, String codeHash,
            Instant sentAt, Instant expiresAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.codeHash = codeHash;
        this.lastSentAt = sentAt;
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
