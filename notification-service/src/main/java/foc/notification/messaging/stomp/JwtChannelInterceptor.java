/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: STOMP CONNECT authentication for issue #67 per the author's
 * decision: the handshake upgrade is open and the CONNECT frame
 * carries "Authorization: Bearer <jwt>"; invalid or missing tokens
 * reject the CONNECT (docs/notification-service.md, "Session
 * mechanics").
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

import foc.notification.security.JwtVerifier;
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
 * Authenticates STOMP sessions at CONNECT: verifies the bearer JWT
 * and attaches a {@link StompPrincipal} named by the token's
 * {@code sub} to the session, which later frames and user-destination
 * routing inherit. A throw here makes the sub-protocol handler send a
 * STOMP ERROR frame (with this exception's message) and close the
 * session — so the messages stay generic, leaking no token detail.
 */
@Component
class JwtChannelInterceptor implements ChannelInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtVerifier jwtVerifier;

	JwtChannelInterceptor(JwtVerifier jwtVerifier) {
		this.jwtVerifier = jwtVerifier;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		// The accessor from getAccessor is the mutable one on CONNECT, so
		// setUser sticks (the immutable copy is made after interception).
		StompHeaderAccessor accessor =
				MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
			return message;
		}
		String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			throw new MessagingException("missing bearer token");
		}
		try {
			String userId = jwtVerifier.verifiedSubject(header.substring(BEARER_PREFIX.length()));
			accessor.setUser(new StompPrincipal(userId));
		} catch (JwtException ex) {
			throw new MessagingException("invalid token");
		}
		return message;
	}
}
