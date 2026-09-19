/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: session principal for issue #67's STOMP gateway.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification.messaging.stomp;

import java.security.Principal;

/**
 * Principal attached to a STOMP session at CONNECT. Its name is the
 * JWT's {@code sub} — the platform user ID — which must equal the
 * {@code recipientId} of stored notifications: user-destination
 * routing ({@code convertAndSendToUser}) matches on exactly this
 * string.
 */
record StompPrincipal(String name) implements Principal {

	@Override
	public String getName() {
		return name;
	}
}
