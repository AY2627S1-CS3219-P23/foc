/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat.
 * Scope: a chat's two states (docs/chat-service.md C3-C6).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.entity;

public enum ChatStatus {
    // the request is ACCEPTED, COLLECTED or ARRIVED: both parties can chat
    OPEN,
    // the request left those states, or its courier dropped it: admins only
    CLOSED
}
