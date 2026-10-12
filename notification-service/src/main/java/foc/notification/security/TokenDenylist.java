/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-12.
 * Scope: read-side denylist port for issue #67 per the author's
 * decision (D23, options Q&A): notification-service checks the
 * platform denylist at STOMP CONNECT itself, because the CONNECT-frame
 * token never reaches the gateway's access phase.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.security;

/**
 * Read side of the platform token denylist that user-service writes at
 * logout/revocation and the API gateway enforces on REST calls
 * (docs/gateway.md). This service consults it once per STOMP CONNECT
 * (D23) — the only token the gateway cannot see.
 *
 * <p>A port in the D11 sense: the Redis adapter lives in
 * {@code security.redis}, and implementations must <em>fail open</em> —
 * answer {@code false} on any store failure — matching the gateway's
 * G6 stance that an outage never locks valid users out.
 */
public interface TokenDenylist {

	/** Returns whether the given token id has been revoked. */
	boolean isRevoked(String jti);
}
