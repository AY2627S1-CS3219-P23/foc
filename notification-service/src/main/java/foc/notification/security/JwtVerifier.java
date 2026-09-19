/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: JWT verification for issue #67 per the author's decisions:
 * HS256 against the platform's shared JWT_SECRET, jjwt library, and
 * the provisional platform convention that the sub claim carries the
 * user ID (docs/architecture.md, Authentication note).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies platform JWTs (HS256, shared {@code JWT_SECRET}) and
 * extracts the user ID from the {@code sub} claim — the same ID that
 * appears in event envelopes' {@code parties[]} and notification
 * {@code recipientId} (provisional platform convention, issue #67).
 *
 * <p>Public and transport-agnostic: the STOMP CONNECT interceptor
 * uses it now, and the REST API (issue #66) is expected to reuse it.
 * The parser is built eagerly so a missing or too-short secret fails
 * at startup with a clear message, not on the first connection.
 */
@Component
public class JwtVerifier {

	private static final int MIN_SECRET_BYTES = 32;

	private final JwtParser parser;

	JwtVerifier(@Value("${notification.jwt.secret}") String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes for HS256");
		}
		this.parser = Jwts.parser()
				.verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
				.build();
	}

	/**
	 * Returns the token's subject (the platform user ID).
	 *
	 * @throws io.jsonwebtoken.JwtException if the token is malformed,
	 *         has an invalid signature, is expired, or has no subject
	 */
	public String verifiedSubject(String token) {
		Claims claims = parser.parseSignedClaims(token).getPayload();
		String subject = claims.getSubject();
		if (subject == null || subject.isBlank()) {
			throw new MalformedJwtException("token has no subject");
		}
		return subject;
	}
}
