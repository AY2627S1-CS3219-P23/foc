/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the body of GET /credits/me, with the fields of
 * docs/credit-service.md "Read API contract".
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import foc.credit.entity.CreditAccount;

/** The caller's balance. {@code total} is computed here, never stored. */
public record BalanceResponse(int available, int reserved, int total) {

    public static BalanceResponse of(CreditAccount account) {
        return new BalanceResponse(account.getAvailable(), account.getReserved(),
            account.getAvailable() + account.getReserved());
    }
}
