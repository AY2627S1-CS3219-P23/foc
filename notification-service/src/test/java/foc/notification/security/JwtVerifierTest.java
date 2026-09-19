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
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

	private final JwtVerifier verifier = new JwtVerifier(SECRET);

	private static SecretKey key(String secret) {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	private static String token(String subject, Date expiration, String secret) {
		return Jwts.builder()
				.subject(subject)
				.expiration(expiration)
				.signWith(key(secret))
				.compact();
	}

	@Test
	void returnsSubjectOfValidToken() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000), SECRET);

		assertThat(verifier.verifiedSubject(token)).isEqualTo("usr-req-1001");
	}

	@Test
	void rejectsExpiredToken() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() - 60_000), SECRET);

		assertThatThrownBy(() -> verifier.verifiedSubject(token))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejectsGarbage() {
		assertThatThrownBy(() -> verifier.verifiedSubject("not-a-jwt"))
				.isInstanceOf(MalformedJwtException.class);
	}

	@Test
	void rejectsTokenSignedWithDifferentKey() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000),
				"another-secret-0123456789abcdef0123456789abcdef");

		assertThatThrownBy(() -> verifier.verifiedSubject(token))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsTokenWithoutSubject() {
		String token = Jwts.builder()
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(SECRET))
				.compact();

		assertThatThrownBy(() -> verifier.verifiedSubject(token))
				.isInstanceOf(MalformedJwtException.class);
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
				.subject("usr-req-1001")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(longSecret))
				.compact();

		assertThatThrownBy(() -> new JwtVerifier(longSecret).verifiedSubject(hs384Token))
				.isInstanceOf(MalformedJwtException.class)
				.hasMessageContaining("algorithm");
	}
}
