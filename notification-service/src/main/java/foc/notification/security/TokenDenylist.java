/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: read-side denylist port for issue #67 per the author's
 * decision (D23, options Q&A): notification-service checks the
 * platform denylist at STOMP CONNECT itself, because the CONNECT-frame
 * token never reaches the gateway's access phase.
 * 2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): the
 * per-user cutoff stamp a password reset writes (G11, PR #179) is read
 * here too — checking jtis alone let a pre-reset token still open a
 * session.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

import java.time.Instant;

/**
 * Read side of the platform token denylist that user-service writes at
 * logout/revocation and the API gateway enforces on REST calls
 * (docs/gateway.md). This service consults it once per STOMP CONNECT
 * (D23) — the only token the gateway cannot see. Both of the gateway's
 * lookups are mirrored: the per-token key a logout writes (G10) and
 * the per-user cutoff stamp a password reset writes (G11).
 *
 * <p>A port in the D11 sense: the Redis adapter lives in
 * {@code security.redis}, and implementations must <em>fail open</em> —
 * answer {@code false} on any store failure — matching the gateway's
 * G6 stance that an outage never locks valid users out.
 */
public interface TokenDenylist {

	/** Returns whether the given token id has been revoked (G10). */
	boolean isRevoked(String jti);

	/**
	 * Returns whether the account's sessions were revoked wholesale
	 * after the given token was issued — a password reset stamps the
	 * subject's cutoff rather than each outstanding jti (G11). Strictly
	 * before, like the gateway: a token minted in the same second as
	 * the stamp survives.
	 */
	boolean isUserRevokedSince(String subject, Instant issuedAt);
}
