/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: rating-service's JwtVerifier (HS256 against the shared
 * JWT_SECRET, sub = user ID, role claim, exp required) with
 * notification-service's jti and issued-at added to the result, which
 * the CONNECT denylist check needs (C15).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies platform JWTs (HS256, shared {@code JWT_SECRET}) and
 * extracts the caller's user ID ({@code sub}) and role ({@code role}),
 * as minted by user-service's {@code JwtIssuer}, plus the {@code jti}
 * and issue time the STOMP CONNECT denylist check looks up.
 *
 * <p>The parser is built eagerly so a missing or too-short secret
 * fails at startup, not on the first request.
 */
@Component
public class JwtVerifier {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtParser parser;

    public JwtVerifier(@Value("${chat.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    /**
     * Returns the token's verified subject (user ID), role, jti and
     * issue time. The last two are optional: only revocation needs them,
     * so an absent claim skips its denylist lookup (notification-service's
     * stance).
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed,
     *         has an invalid signature, is expired, or is missing its
     *         subject, expiry or role claim
     */
    public VerifiedToken verify(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        // Defense in depth: the platform convention is HS256 exactly.
        // (The signature was already verified against the shared key.)
        if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
            throw new MalformedJwtException("unexpected signature algorithm");
        }
        Claims claims = jws.getPayload();
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException("token has no subject");
        }
        // jjwt only checks exp when it is present; a token without one
        // would never expire
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("token has no expiry");
        }
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new MalformedJwtException("token has no role");
        }
        Date issuedAt = claims.getIssuedAt();
        return new VerifiedToken(subject, role, claims.getId(),
                issuedAt == null ? null : issuedAt.toInstant());
    }

    public record VerifiedToken(String userId, String role, String jti, Instant issuedAt) {
    }
}
