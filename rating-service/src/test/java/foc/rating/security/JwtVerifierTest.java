/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
 * Scope: unit tests for the copied JwtVerifier (valid, expired,
 * malformed, wrong-key, missing-subject, missing-expiry, missing-role
 * tokens; weak-secret startup failure; non-HS256 rejection), adapted
 * from notification-service's JwtVerifierTest to order-service's
 * verifier shape (userId + role).
 * Reviewed by: [pending]
 */
package foc.rating.security;

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
				.claim("role", "REQUESTER")
				.expiration(expiration)
				.signWith(key(secret))
				.compact();
	}

	@Test
	void returnsSubjectAndRoleOfValidToken() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000), SECRET);

		JwtVerifier.VerifiedToken verified = verifier.verify(token);
		assertThat(verified.userId()).isEqualTo("usr-req-1001");
		assertThat(verified.role()).isEqualTo("REQUESTER");
	}

	@Test
	void rejectsExpiredToken() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() - 60_000), SECRET);

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejectsGarbage() {
		assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
				.isInstanceOf(MalformedJwtException.class);
	}

	@Test
	void rejectsTokenSignedWithDifferentKey() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000),
				"another-secret-0123456789abcdef0123456789abcdef");

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsTokenWithoutSubject() {
		String token = Jwts.builder()
				.claim("role", "REQUESTER")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(SECRET))
				.compact();

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(MalformedJwtException.class);
	}

	@Test
	void rejectsTokenWithoutExpiry() {
		// jjwt only validates exp when the claim exists — without this
		// check a signed no-expiry token would never expire.
		String token = Jwts.builder()
				.subject("usr-req-1001")
				.claim("role", "REQUESTER")
				.signWith(key(SECRET))
				.compact();

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(MalformedJwtException.class)
				.hasMessageContaining("expiry");
	}

	@Test
	void rejectsTokenWithoutRole() {
		String token = Jwts.builder()
				.subject("usr-req-1001")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(SECRET))
				.compact();

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
				.subject("usr-req-1001")
				.claim("role", "REQUESTER")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(longSecret))
				.compact();

		assertThatThrownBy(() -> new JwtVerifier(longSecret).verify(hs384Token))
				.isInstanceOf(MalformedJwtException.class)
				.hasMessageContaining("algorithm");
	}
}
