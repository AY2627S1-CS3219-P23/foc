/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-23.
 * Scope: issue #240: the close_reason values of team decision DSP-1
 * (docs/disputes.md), transcribed; the names and their meanings are the
 * team's.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

/**
 * Why a request ended without completing (DSP-1), stored by name in
 * {@code requests.close_reason}; the allowed names are a CHECK in the
 * Flyway migrations. Null for COMPLETED requests and rows from before
 * the column.
 */
public enum CloseReason {
    REQUESTER_CANCELLED, // the requester cancelled before any courier accepted
    RESERVATION_TIMEOUT, // no reply from the Credit Service in time
    INSUFFICIENT_CREDITS, // the Credit Service rejected: not enough credits
    INVALID_REWARD, // the Credit Service rejected: the reward was below 1
    DEADLINE_PASSED, // expired by the deadline sweep
    DISPUTE_RESOLVED // an admin resolved a dispute (set by #239)
}
