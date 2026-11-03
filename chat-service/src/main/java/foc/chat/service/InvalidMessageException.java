/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: a message the rules of C7 refuse (400 over REST, an error frame
 * over STOMP).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }
}
