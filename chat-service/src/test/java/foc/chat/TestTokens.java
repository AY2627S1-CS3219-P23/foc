/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: test JWTs signed with the test secret, shaped like
 * user-service's (sub = user id, role, jti, iat, exp).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class TestTokens {

    // the test secret (src/test/resources/application.yaml)
    public static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private TestTokens() {
    }

    public static String user(long userId) {
        return token(userId, "USER");
    }

    public static String admin(long userId) {
        return token(userId, "ADMIN");
    }

    public static String token(long userId, String role) {
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
