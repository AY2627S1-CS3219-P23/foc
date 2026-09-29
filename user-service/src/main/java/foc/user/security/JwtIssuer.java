/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: JWT minting for login (issue #90), per design doc §3: HS256 with the
       shared JWT_SECRET, claims sub (user id), role, jti and a 1 h exp. Key
       handling and the 32-byte minimum mirror notification-service's
       JwtVerifier, so minted tokens pass it. Building it now (with #87/#89)
       was a team decision.
       PR #141 review: a TTL with no unit is read as seconds, not
       milliseconds.
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import foc.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtIssuer {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtIssuer(
            @Value("${user.jwt.secret:}") String secret,
            // a bare number is seconds (JWT_ACCESS_TOKEN_TTL=3600 is 1 h)
            @Value("${user.jwt.access-token-ttl}") @DurationUnit(ChronoUnit.SECONDS) Duration ttl) {
        this(secret, ttl, Clock.systemUTC());
    }

    JwtIssuer(String secret, Duration ttl, Clock clock) {
        // fail at startup, not on the first login, like the verifiers do
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // an empty JWT_ACCESS_TOKEN_TTL binds as null: use the design's 1 h
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
        if (this.ttl.isNegative() || this.ttl.isZero()) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL must be positive");
        }
        this.clock = clock;
    }

    // a signed access token for the user; jti is the logout denylist key
    public String issue(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("role", user.getRole().name())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .issuer("cs3219group23")
            .audience().add("cs3219group23").and()
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public Duration ttl() {
        return ttl;
    }
}
