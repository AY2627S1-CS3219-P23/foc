/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: the session principal, as in notification-service, carrying
 * the caller's chat identity (user id and admin flag).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import foc.chat.security.Caller;
import java.security.Principal;

/**
 * Attached to a STOMP session at CONNECT. Its name is the platform user
 * id, which user destinations ({@code /user/queue/errors}) route on.
 */
record StompPrincipal(Caller caller) implements Principal {

    @Override
    public String getName() {
        return Long.toString(caller.userId());
    }
}
