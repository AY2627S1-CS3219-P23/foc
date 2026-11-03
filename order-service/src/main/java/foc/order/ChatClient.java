/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: chat-service's two internal calls (C11) as a port, so the
 * dispatcher's retry rules don't depend on HTTP (HttpChatClient is the
 * adapter).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import foc.order.model.ChatCall;

public interface ChatClient {

    /**
     * Sends one call to chat-service; true once chat-service took it.
     * Never throws for an unreachable or refusing chat-service: that is a
     * false, and the call is retried.
     */
    boolean send(ChatCall call);
}
