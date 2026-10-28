/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-26.
 * Scope: issue #239: the dispute categories of team decision DSP-7
 * (docs/disputes.md), transcribed; Order's own enum, converted from the
 * API's String at the edge and checked against RequestDisputed.CATEGORIES
 * (DSP-12, team decision).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.contracts.events.request.RequestDisputed;

import java.util.Locale;

/**
 * What went wrong with a disputed request (DSP-7), stored by name in
 * {@code request_dispute.category}; the allowed names are a CHECK in
 * Flyway's V3. The names are {@link RequestDisputed#CATEGORIES}.
 */
public enum DisputeCategory {
    NOT_DELIVERED,
    WRONG_ITEM,
    DAMAGED,
    OTHER; // needs a note

    // The category a caller sent; anything outside
    // RequestDisputed.CATEGORIES is an InvalidRequestException (400).
    public static DisputeCategory parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("category is required");
        }
        String name = value.trim().toUpperCase(Locale.ROOT);
        if (!RequestDisputed.CATEGORIES.contains(name)) {
            throw new InvalidRequestException("category must be one of NOT_DELIVERED, WRONG_ITEM, DAMAGED, OTHER");
        }
        return valueOf(name);
    }
}
