/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: Redis denylist writer for POST /auth/logout (issue #151), per the
       team's gateway/Redis decisions (Leong Wei Zhi, 2026-10-11, options
       Q&A): Redis-only store with TTL keys the API gateway checks, and
       best-effort semantics — a failed write is logged, never surfaced,
       mirroring the gateway's fail-open stance.
       2026-10-12, Claude Code (Fable 5), issue #67: javadoc updated for
       notification-service's D23 read at STOMP CONNECT.
Author review: Leong Wei Zhi to review via the PR.
2026-10-12 (Claude Code, Fable 5), PR #179 review (@Sinnez1):
revokeUserSessions added — a password reset stamps denylist:user:<id>
so the gateway refuses the account's pre-reset tokens (decision G11,
Leong Wei Zhi via options Q&A).
2026-10-12 (Claude Code, Fable 5), PR #179 approval follow-up
(@Sinnez1): the javadoc records the reviewer-reproduced same-second
survivor and the login-racing-the-reset gap as the same accepted
leak; no behaviour change.
2026-10-17 (Claude Code, Opus 5), PR #183 review (@Sinnez1): the
javadoc states the stamp-after-commit requirement the review's
finding established; no behaviour change here.
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
 * Writes revocations into the platform Redis (design doc §3, issue
 * #151): {@code denylist:<jti>} keys that expire when the token itself
 * would have, and {@code denylist:user:<id>} cutoff stamps that outlaw
 * every token issued to the user before the stamp (PR #179 — a
 * password reset ends the old password's sessions). The API gateway
 * rejects a bearer token whose jti has a live key here or whose iat
 * predates its subject's stamp; no backend service reads the denylist
 * (team decision: gateway-only enforcement), except
 * notification-service at STOMP CONNECT (D23, issue #67) — the
 * CONNECT-frame token is the one bearer the gateway can never see.
 */
@Component
public class TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);

    static final String KEY_PREFIX = "denylist:";
    static final String USER_KEY_PREFIX = "denylist:user:";

    private final StringRedisTemplate redis;
    private final JwtIssuer jwtIssuer;
    private final Clock clock;

    @Autowired
    public TokenDenylist(StringRedisTemplate redis, JwtIssuer jwtIssuer) {
        this(redis, jwtIssuer, Clock.systemUTC());
    }

    TokenDenylist(StringRedisTemplate redis, JwtIssuer jwtIssuer, Clock clock) {
        this.redis = redis;
        this.jwtIssuer = jwtIssuer;
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

    /**
     * Revokes every outstanding token the user holds: stamps the current
     * epoch second under {@code denylist:user:<id>}; the gateway refuses
     * this subject's tokens whose {@code iat} is strictly before the
     * stamp (PR #179 review — a password reset must end the sessions the
     * old password opened). The key lives one token TTL: by then every
     * token issued before the stamp has expired on its own. The strict
     * comparison means a token minted the same second as the stamp
     * survives — sparing the owner's own immediate post-reset login —
     * and a repeat reset simply moves the stamp forward. Accepted and
     * reviewer-reproduced (PR #179 approval, @Sinnez1); the same
     * acceptance covers an old-password login racing the reset: the
     * token is minted after the login's row lock is released, so a
     * reset landing in that gap stamps before the mint and the token
     * passes with {@code iat >= stamp}. Best-effort like
     * {@link #revoke} (G9).
     *
     * <p>Callers inside a transaction must stamp only once it has
     * committed — see {@code AfterCommit} in {@code foc.user.service}.
     * A cutoff written before the commit outlaws tokens minted before
     * a change that is not yet visible, which lets a login in that gap
     * mint one carrying the old state and an {@code iat} the stamp
     * accepts (PR #183 review, @Sinnez1).
     */
    public void revokeUserSessions(Long userId) {
        String cutoff = String.valueOf(clock.instant().getEpochSecond());
        try {
            redis.opsForValue().set(USER_KEY_PREFIX + userId, cutoff, jwtIssuer.ttl());
        } catch (DataAccessException e) {
            log.warn("user denylist write failed for user {}; pre-reset tokens stay valid"
                + " up to their own exp", userId, e);
        }
    }
}
