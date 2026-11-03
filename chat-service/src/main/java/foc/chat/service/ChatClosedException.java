/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: a send to a chat that is no longer open (C3-C6).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

public class ChatClosedException extends RuntimeException {

    public ChatClosedException() {
        super("This chat has ended");
    }
}
