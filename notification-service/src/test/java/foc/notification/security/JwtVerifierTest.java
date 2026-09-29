/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: unit tests for issue #67's JWT verifier (valid, expired,
 * malformed, wrong-key, missing-subject tokens; weak-secret startup
 * failure).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

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

import org.junit.jupiter.api.DisplayName;
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
        builder.issuer("cs3219group23").audience().add("cs3219group23").and();
        return builder.signWith(key(secret)).compact();
    }

	@Test
	@DisplayName("Returns the subject of a valid token")
	void returnsSubjectOfValidToken() {
		String token = token("usr-req-1001", "ADMIN", new Date(System.currentTimeMillis() + 60_000), SECRET);

		assertThat(verifier.verifiedSubject(token)).isEqualTo("usr-req-1001");
	}

	
    @Test
    @DisplayName("An expired token is rejected as expired")
    void rejectsExpiredToken() {
        String token = token("42", "USER", new Date(System.currentTimeMillis() - 60_000), SECRET);

        assertThatThrownBy(() -> verifier.verifiedSubject(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("A token signed with another secret is rejected")
    void rejectsTokenSignedWithDifferentKey() {
        String token = token("42", "USER", new Date(System.currentTimeMillis() + 60_000),
                "another-secret-0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> verifier.verifiedSubject(token)).isInstanceOf(JwtException.class);
    }
    
    @Test
    @DisplayName("A token signed with HS512, even with the shared secret, is rejected")
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

        assertThatThrownBy(() -> new JwtVerifier(longSecret).verifiedSubject(hs384Token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("algorithm");
    }
    @Test
    @DisplayName("An unsigned (alg none) token is rejected")
    void unsigned() {
        String token = Jwts.builder().subject("42").expiration(new Date(System.currentTimeMillis() + 60_000))
            .claim("role", "USER").issuer("cs3219group23").audience().add("cs3219group23").and()
            .compact();
        assertThatThrownBy(() -> verifier.verifiedSubject(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A garbage token is rejected")
    void rejectsGarbage() {
        assertThatThrownBy(() -> verifier.verifiedSubject("not-a-jwt")).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("A token without a sub is rejected")
    void rejectsTokenWithoutSubject() {
        String token = Jwts.builder()
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .issuer("cs3219group23")
                .audience().add("cs3219group23").and()
                .signWith(key(SECRET))
                .compact();

        assertThatThrownBy(() -> verifier.verifiedSubject(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("subject");
    }

    @Test
    @DisplayName("A token without a role is rejected")
    void rejectsTokenWithoutRole() {
        String token = token("42", null, new Date(System.currentTimeMillis() + 60_000), SECRET);

        assertThatThrownBy(() -> verifier.verifiedSubject(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("role");
    }

    
    @Test
    @DisplayName("A token without expiry is rejected")
    void rejectsTokenWithoutExpiry() {
        // no .expiration(...) at all — jjwt itself only enforces exp
        // when the claim is present, so this must be rejected explicitly.
        String token = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .issuer("cs3219group23").audience().add("cs3219group23").and()
                .signWith(key(SECRET))
                .compact();

        assertThatThrownBy(() -> verifier.verifiedSubject(token))
                .isInstanceOf(MalformedJwtException.class)
                .hasMessageContaining("expiry");
    }

    
    @Test
    @DisplayName("A token with the wrong issuer is rejected")
    void badIssuer() {
        String wrongIssuer = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .issuer("CS3219group23").audience().add("cs3219group23").and()
                .signWith(key(SECRET))
                .compact();
        String noIssuer = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .audience().add("cs3219group23").and()
                .signWith(key(SECRET))
                .compact();

        for (String token : new String[] {wrongIssuer, noIssuer}) {
            assertThatThrownBy(() -> verifier.verifiedSubject(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("issuer");
        }
    }

    @Test
    @DisplayName("A token with the wrong audience is rejected")
    void badAudience() {
        String wrongAudience = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .issuer("cs3219group23").audience().add("CS3219group23").and()
                .signWith(key(SECRET))
                .compact();
        String noAudience = Jwts.builder()
                .subject("42")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .issuer("cs3219group23")
                .signWith(key(SECRET))
                .compact();

        for (String token : new String[] {wrongAudience, noAudience}) {
            assertThatThrownBy(() -> verifier.verifiedSubject(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
        }
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails at construction")
    void failsFastOnShortSecret() {
        assertThatThrownBy(() -> new JwtVerifier("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
