/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: Redis denylist writer for POST /auth/logout (issue #151), per the
       team's gateway/Redis decisions (Leong Wei Zhi, 2026-10-11, options
       Q&A): Redis-only store with TTL keys the API gateway checks, and
       best-effort semantics — a failed write is logged, never surfaced,
       mirroring the gateway's fail-open stance.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes revoked token ids into the platform Redis as
 * {@code denylist:<jti>} keys that expire when the token itself would
 * have (design doc §3, issue #151). The API gateway rejects any bearer
 * token whose jti has a live key here; no backend service reads the
 * denylist (team decision: gateway-only enforcement).
 */
@Component
public class TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);

    static final String KEY_PREFIX = "denylist:";

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public TokenDenylist(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    TokenDenylist(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * Denylists the token until its own expiry. Best-effort (team decision
     * G9): a Redis failure is logged and swallowed — the token then simply
     * stays valid until {@code expiresAt}, consistent with the gateway
     * failing open when Redis is unreachable. Idempotent: re-revoking an
     * already-denylisted jti just rewrites the same key.
     */
    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        // an already-expired token needs no denylist entry (and Redis
        // rejects non-positive expirations)
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (DataAccessException e) {
            log.warn("denylist write failed for jti {}; token stays valid until {}",
                jti, expiresAt, e);
        }
    }
}
