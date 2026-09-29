/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-29.
 * Scope: unit tests for issue #106's JWT verifier, mirroring
 * notification-service's JwtVerifierTest (valid, expired, malformed,
 * wrong-key, missing-subject tokens; weak-secret startup failure) and
 * adding coverage for the role claim this service also needs.
 * PR #143 review (LeongWZ; also flagged by Copilot): added
 * rejectsTokenWithoutExpiry — jjwt only checks exp when the claim is
 * present, so a validly-signed token with none at all previously
 * verified forever.
 * Reviewed by: [pending]
 */
package foc.supplier.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String subject, String role, Date expiration, String secret) {
        var builder = Jwts.builder().subject(subject).expiration(expiration);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key(secret)).compact();
    }

    @Test
    void returnsSubjectAndRoleOfValidToken() {
        String token = token("42", "ADMIN", new Date(System.currentTimeMillis() + 60_000), SECRET);

        JwtVerifier.VerifiedToken verified = verifier.verify(token);

        assertThat(verified.userId()).isEqualTo("42");
        assertThat(verified.role()).isEqualTo("ADMIN");
    }

    @Test
    void rejectsExpiredToken() {
        String token = token("42", "USER", new Date(System.currentTimeMillis() - 60_000), SECRET);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = token("42", "USER", new Date(System.currentTimeMillis() + 60_000),
                "another-secret-0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithoutSubject() {
        String token = Jwts.builder()
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(SECRET))
                .compact();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        // no .expiration(...) at all — jjwt itself only enforces exp
        // when the claim is present, so this must be rejected explicitly.
        String token = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .signWith(key(SECRET))
                .compact();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("expiry");
    }

    @Test
    void rejectsTokenWithoutRole() {
        String token = token("42", null, new Date(System.currentTimeMillis() + 60_000), SECRET);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("role");
    }

    @Test
    void failsFastOnShortSecret() {
        assertThatThrownBy(() -> new JwtVerifier("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsNonHs256Algorithm() {
        // A 48-byte secret makes jjwt auto-select HS384, producing a
        // token whose signature verifies but whose algorithm violates
        // the platform's HS256 convention.
        String longSecret = SECRET + "-padding-to-48-bytes!";
        String hs384Token = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(longSecret))
                .compact();

        assertThatThrownBy(() -> new JwtVerifier(longSecret).verify(hs384Token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("algorithm");
    }
}
