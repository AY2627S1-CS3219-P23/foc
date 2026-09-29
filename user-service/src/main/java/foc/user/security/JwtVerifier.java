/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: JWT verification for the Spring Security filter chain (issue #91),
       per design doc §3/§4: HS256 with the shared JWT_SECRET, mirroring
       notification-service's JwtVerifier (eager key check, HS256-only,
       subject required), plus the role claim that §3 maps to authorities.
       The logout denylist check is deferred (team decision).
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import foc.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtVerifier {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtParser parser;

    public JwtVerifier(@Value("${user.jwt.secret:}") String secret) {
        // fail at startup, not on the first request, like JwtIssuer does
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.parser = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build();
    }

    // the verified caller: sub (user id) and role, as JwtIssuer mints them
    public record VerifiedToken(String subject, Role role) {
    }

    /**
     * Verifies the signature and expiry and reads the caller from the claims.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired
     * @throws io.jsonwebtoken.JwtException if the token is malformed, has an
     *         invalid signature, or lacks a numeric sub, an exp or a known role
     */
    public VerifiedToken verify(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        // the platform convention is HS256 exactly (the signature was already
        // verified against the shared key)
        if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
            throw new MalformedJwtException("unexpected signature algorithm");
        }
        Claims claims = jws.getPayload();

        String subject = claims.getSubject();
        if (!isUserId(subject)) {
            throw new MalformedJwtException("token has no numeric subject");
        }
        // jjwt only checks exp when it is present; a token without one
        // would never expire
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("token has no expiry");
        }
        return new VerifiedToken(subject, role(claims));
    }

    // digits only, and small enough for the Long user ids (CallerId)
    private static boolean isUserId(String subject) {
        if (subject == null || !subject.matches("\\d+")) {
            return false;
        }
        try {
            Long.parseLong(subject);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Role role(Claims claims) {
        Object role = claims.get("role");
        if (role instanceof String name) {
            for (Role candidate : Role.values()) {
                if (candidate.name().equals(name)) {
                    return candidate;
                }
            }
        }
        throw new MalformedJwtException("token has no known role");
    }
}
