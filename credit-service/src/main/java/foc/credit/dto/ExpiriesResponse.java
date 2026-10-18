/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #196.
 * Scope: the body of GET /credits/me/expiries, with the fields of
 * docs/credit-service.md "Read API contract". The shape is the team's
 * decision: an object around the list, so fields can be added later,
 * and no lot IDs.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.time.Instant;
import java.util.List;

import foc.credit.entity.CreditLot;

/** The caller's credits that will expire, soonest first. */
public record ExpiriesResponse(List<Item> items) {

    /** One lot still holding credits: how many, and when they expire. */
    public record Item(int amount, Instant expiresAt) {
    }

    public static ExpiriesResponse of(List<CreditLot> lots) {
        return new ExpiriesResponse(lots.stream()
            .map(lot -> new Item(lot.getRemaining(), lot.getExpiresAt()))
            .toList());
    }
}
