/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: JWT verification for issue #67 per the author's decisions:
 * HS256 against the platform's shared JWT_SECRET, jjwt library, and
 * the provisional platform convention that the sub claim carries the
 * user ID (docs/architecture.md, Authentication note).
 * 2026-10-12, Claude Code (Fable 5), issue #67: verify() replaces
 * verifiedSubject() — exp is now required (jjwt only checks a present
 * exp; parity with the other services' verifiers, PR #91/#143) and the
 * jti is exposed for the D23 denylist check at STOMP CONNECT.
 * 2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): iat is
 * exposed too — the D23 check also needs it for the per-user cutoff
 * stamp a password reset writes (gateway decision G11).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

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
 * extracts the user ID from the {@code sub} claim — the same ID that
 * appears in domain events' {@code parties()} and notification
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
	 * The verified caller: the subject (platform user ID) that
	 * user-destination routing matches on, plus the jti and issue time
	 * the D23 denylist check looks up — the jti against
	 * {@code denylist:<jti>}, the issue time against the account's
	 * {@code denylist:user:<id>} cutoff stamp (G11). Both stay
	 * optional: every minted token carries them, but revocation is the
	 * only thing that needs them, so an absent claim skips its check
	 * rather than refusing the session (same stance as the gateway's
	 * auth.lua).
	 */
	public record VerifiedToken(String subject, String jti, Instant issuedAt) {
	}

	/**
	 * Verifies the signature and expiry and reads the caller from the
	 * claims.
	 *
	 * @throws io.jsonwebtoken.JwtException if the token is malformed,
	 *         has an invalid signature, is expired, or has no subject
	 *         or no expiry
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
		// would hold a STOMP session forever
		if (claims.getExpiration() == null) {
			throw new MalformedJwtException("token has no expiry");
		}
		Date issuedAt = claims.getIssuedAt();
		return new VerifiedToken(subject, claims.getId(),
				issuedAt == null ? null : issuedAt.toInstant());
	}
}
