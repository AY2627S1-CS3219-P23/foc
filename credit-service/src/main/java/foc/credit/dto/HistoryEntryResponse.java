/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: one row of GET /credits/me/history, with the fields of
 * docs/credit-service.md "Read API contract".
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.time.Instant;

import foc.credit.entity.CreditHistoryEntry;
import foc.credit.entity.CreditHistoryType;

/**
 * One history row. {@code amount} is always positive; {@code type} says
 * whether it was a gain or a loss. The row's user is left out: it is
 * always the caller.
 */
public record HistoryEntryResponse(long id, CreditHistoryType type, int amount, String requestRef,
        Instant occurredAt) {

    public static HistoryEntryResponse of(CreditHistoryEntry entry) {
        return new HistoryEntryResponse(entry.getId(), entry.getType(), entry.getAmount(), entry.getRequestRef(),
            entry.getOccurredAt());
    }
}
