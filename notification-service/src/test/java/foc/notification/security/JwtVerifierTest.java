/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: unit tests for issue #67's JWT verifier (valid, expired,
 * malformed, wrong-key, missing-subject tokens; weak-secret startup
 * failure).
 * 2026-10-12, issue #67: call sites moved to verify(); no-expiry
 * rejection and jti exposure covered (D23).
 * 2026-10-14, PR #185 review (@Sinnez1): iat exposure covered — the
 * D23 check needs it for the per-user reset cutoff (G11).
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
import java.time.Instant;
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

		assertThat(verifier.verify(token).subject()).isEqualTo("usr-req-1001");
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
				.signWith(key(SECRET))
				.compact();

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(MalformedJwtException.class)
				.hasMessageContaining("expiry");
	}

	@Test
	void exposesJtiWhenPresent() {
		String token = Jwts.builder()
				.subject("usr-req-1001")
				.id("jti-1")
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(SECRET))
				.compact();

		assertThat(verifier.verify(token).jti()).isEqualTo("jti-1");
	}

	@Test
	void exposesIssuedAtWhenPresent() {
		Instant issuedAt = Instant.ofEpochSecond(1_760_000_000L);
		String token = Jwts.builder()
				.subject("usr-req-1001")
				.issuedAt(Date.from(issuedAt))
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key(SECRET))
				.compact();

		assertThat(verifier.verify(token).issuedAt()).isEqualTo(issuedAt);
	}

	@Test
	void issuedAtIsOptional() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000), SECRET);

		assertThat(verifier.verify(token).issuedAt()).isNull();
	}

	@Test
	void jtiIsOptional() {
		String token = token("usr-req-1001", new Date(System.currentTimeMillis() + 60_000), SECRET);

		assertThat(verifier.verify(token).jti()).isNull();
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

		assertThatThrownBy(() -> new JwtVerifier(longSecret).verify(hs384Token))
				.isInstanceOf(MalformedJwtException.class)
				.hasMessageContaining("algorithm");
	}
}
