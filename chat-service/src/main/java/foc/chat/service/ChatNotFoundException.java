/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: a chat the caller may not see, answered like one that does not
 * exist (404), so its existence is not revealed.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException() {
        super("No such chat");
    }
}
