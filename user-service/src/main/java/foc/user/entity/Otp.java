/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-23.
 * Scope: entity scaffolded for issue #86 from the design doc's §2
 * erDiagram (FoC D2 Design Doc). @ManyToOne FK representation chosen
 * by Leong Wei Zhi via options Q&A. No endpoints yet — the sign-up and
 * account-update flows that write these rows arrive with issues
 * #87/#92.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One-time password issued for sign-up verification or an email
 * change (design doc §2/§5). Codes are stored hashed, never plain;
 * {@code attempts} counts verification tries against this code.
 *
 * <p>Note: the design doc's signup-OTP-timing comparison is still
 * open. If "insert user after verify" is chosen (issue #87), SIGNUP
 * rows will need to reference an email instead of a user row — that
 * schema evolution belongs to that PR.
 */
@Entity
@Table(name = "otps")
public class Otp {

    /** What the code was issued for. */
    public enum Purpose {
        SIGNUP,
        EMAIL_CHANGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    protected Otp() {
    }

    public Otp(User user, String codeHash, Purpose purpose, Instant expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.purpose = purpose;
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

    public Purpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
}
