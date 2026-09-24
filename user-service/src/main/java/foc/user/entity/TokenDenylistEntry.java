/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-23.
 * Scope: entity scaffolded for issue #86 from the design doc's §2
 * erDiagram (FoC D2 Design Doc). No endpoints yet — logout writes
 * these rows under issue #90.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JWT IDs revoked at logout (design doc §2/§3): a token whose
 * {@code jti} is here is refused even though its signature and expiry
 * are still valid. {@code expires_at} mirrors the token's own expiry
 * so rows can be purged once the token would have died anyway.
 */
@Entity
@Table(name = "token_denylist")
public class TokenDenylistEntry {

    @Id
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TokenDenylistEntry() {
    }

    public TokenDenylistEntry(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
