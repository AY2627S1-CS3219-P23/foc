/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: JWT verification for issue #106, mirroring
 * notification-service's JwtVerifier (HS256 against the shared
 * JWT_SECRET, jjwt library, sub claim carries the user ID). Also
 * extracts the role claim minted by user-service's JwtIssuer (PR #141,
 * issue #90: claim "role", value one of the Role enum names) for the
 * role gate (design doc D2: CRUD endpoints admin-gated via JWT role
 * claim).
 * Reviewed by: [pending]
 */
package foc.supplier.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies platform JWTs (HS256, shared {@code JWT_SECRET}) and
 * extracts the caller's user ID ({@code sub}) and role ({@code role}),
 * as minted by user-service's {@code JwtIssuer}.
 *
 * <p>The parser is built eagerly so a missing or too-short secret
 * fails at startup, not on the first request.
 */
@Component
public class JwtVerifier {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtParser parser;

    JwtVerifier(@Value("${supplier.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    /**
     * Returns the token's verified subject (user ID) and role.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed,
     *         has an invalid signature, is expired, or is missing its
     *         subject or role claim
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
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new MalformedJwtException("token has no role");
        }
        return new VerifiedToken(subject, role);
    }

    public record VerifiedToken(String userId, String role) {
    }
}
