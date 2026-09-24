/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-23.
 * Scope: entity scaffolded for issue #86 from the design doc's §2
 * erDiagram (FoC D2 Design Doc). @ManyToOne FK representation chosen
 * by Leong Wei Zhi via options Q&A. No endpoints yet — the recovery
 * and password-reset flows that write these rows arrive with issue
 * #94.
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
 * Emailed single-use token for password reset or account recovery
 * (design doc §2). Tokens are stored hashed, never plain.
 */
@Entity
@Table(name = "account_tokens")
public class AccountToken {

    /** What the token authorizes. */
    public enum Kind {
        PASSWORD_RESET,
        RECOVERY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AccountToken() {
    }

    public AccountToken(User user, String tokenHash, Kind kind, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.kind = kind;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
