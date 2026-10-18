/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: STOMP CONNECT authentication for issue #67 per the author's
 * decision: the handshake upgrade is open and the CONNECT frame
 * carries "Authorization: Bearer <jwt>"; invalid or missing tokens
 * reject the CONNECT (docs/notification-service.md, "Session
 * mechanics").
 * 2026-10-12, Claude Code (Fable 5), issue #67: denylist check added
 * per the author's D23 decision (options Q&A) — a revoked jti rejects
 * the CONNECT; a Redis failure fails open inside the TokenDenylist
 * port.
 * 2026-10-14, Claude Code (Fable 5), PR #185 review (@Sinnez1): the
 * per-user cutoff a password reset stamps (G11, PR #179) is checked
 * too — the jti lookup alone let a pre-reset token open a session.
 * 2026-10-16, Claude Code (Fable 5), PR #185 re-review (@Sinnez1):
 * STOMP 1.2's STOMP frame authenticated like CONNECT (it opened an
 * unauthenticated session), client SEND frames rejected, SUBSCRIBE
 * restricted to the notifications destination.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

import foc.notification.security.JwtVerifier;
import foc.notification.security.TokenDenylist;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions at connect time — the {@code CONNECT}
 * frame or STOMP 1.2's equivalent {@code STOMP} frame: verifies the
 * bearer JWT, rejects revoked tokens — logged out or outlawed by their
 * account's reset cutoff (D23 — the CONNECT-frame token is the one
 * bearer the gateway's denylist check can never see), and attaches a
 * {@link StompPrincipal} named by the token's {@code sub} to the
 * session, which later frames and user-destination routing inherit.
 *
 * <p>Later frames are policed too (PR #185 review): clients never SEND
 * here — with no application destination prefix a SEND to
 * {@code /user/{id}/queue/notifications} would be delivered verbatim
 * to that user — and may SUBSCRIBE only to the one per-user
 * notifications destination.
 *
 * <p>A throw here makes the sub-protocol handler send a STOMP ERROR
 * frame (with this exception's message) and close the session — so the
 * messages stay generic, leaking no token detail.
 */
@Component
class JwtChannelInterceptor implements ChannelInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	/** The one destination clients subscribe to ("Session mechanics"). */
	private static final String NOTIFICATIONS_DESTINATION = "/user/queue/notifications";

	private final JwtVerifier jwtVerifier;
	private final TokenDenylist tokenDenylist;

	JwtChannelInterceptor(JwtVerifier jwtVerifier, TokenDenylist tokenDenylist) {
		this.jwtVerifier = jwtVerifier;
		this.tokenDenylist = tokenDenylist;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		// The accessor from getAccessor is the mutable one on CONNECT, so
		// setUser sticks (the immutable copy is made after interception).
		StompHeaderAccessor accessor =
				MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}
		StompCommand command = accessor.getCommand();
		// command is null for non-frame messages (e.g. heartbeats): pass.
		if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
			authenticate(accessor);
		} else if (StompCommand.SEND.equals(command)) {
			throw new MessagingException("sending not allowed");
		} else if (StompCommand.SUBSCRIBE.equals(command)
				&& !NOTIFICATIONS_DESTINATION.equals(accessor.getDestination())) {
			throw new MessagingException("subscription not allowed");
		}
		return message;
	}

	private void authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			throw new MessagingException("missing bearer token");
		}
		JwtVerifier.VerifiedToken verified;
		try {
			verified = jwtVerifier.verify(header.substring(BEARER_PREFIX.length()));
		} catch (JwtException ex) {
			throw new MessagingException("invalid token");
		}
		// Outside the catch: a denylist failure already failed open to
		// false inside the port, so a throw from here is a real reject.
		if (isRevoked(verified)) {
			throw new MessagingException("token revoked");
		}
		accessor.setUser(new StompPrincipal(verified.subject()));
	}

	/**
	 * Both of the gateway's revocation lookups, in its order (auth.lua):
	 * the token's own denylist key, then the account's reset cutoff. A
	 * token missing the claim a lookup needs skips that lookup rather
	 * than being refused. Marginally stricter than the gateway, which
	 * skips the cutoff lookup too when there is no jti — unreachable
	 * either way, since user-service sets a jti on every token.
	 */
	private boolean isRevoked(JwtVerifier.VerifiedToken verified) {
		if (verified.jti() != null && tokenDenylist.isRevoked(verified.jti())) {
			return true;
		}
		return verified.issuedAt() != null
				&& tokenDenylist.isUserRevokedSince(verified.subject(), verified.issuedAt());
	}
}
