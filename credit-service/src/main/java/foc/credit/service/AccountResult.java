/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-28, issue #267.
 * Scope: what opening or closing an account did, one value per row of
 * the team's state table (USR-6). Same day, PR review (team decision):
 * NO_USER, for an event naming no user, which is dead-lettered at once.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

/**
 * What {@link CreditOperations#open} or {@link CreditOperations#close}
 * did, or {@link #NO_USER} when the handler refused the event before
 * either. Every value but {@code NO_USER} is acknowledged; they differ
 * for logs and tests.
 */
public enum AccountResult {
    // user.created with no account: created with the sign-up credits
    CREATED,
    // user.purged with an open account: closed, its credits in the pool
    CLOSED,
    // user.purged with no account: a closed account holding nothing, so
    // nothing can grant it the sign-up credits later
    CLOSED_PLACEHOLDER,
    // the account is already as the event says: open for user.created,
    // closed for user.purged; nothing changed
    DUPLICATE,
    // user.created for a closed account: the events arrived out of
    // order; ignored, never reopened
    IGNORED_CLOSED,
    // the event names no user (a blank userId): nothing was read or
    // written, and the listener dead-letters it without a retry, which
    // could never succeed
    NO_USER
}
