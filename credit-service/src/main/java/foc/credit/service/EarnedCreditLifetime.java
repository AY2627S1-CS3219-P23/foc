/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, PR #193 review (Leong Wei Zhi).
 * Scope: the expiry of earned credits (D4) in one place, so the
 * transfer and the redistribution cannot drift apart; the reviewer's
 * suggestion. The rule and its validation moved here unchanged from
 * CreditOperationsService.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How long earned credits last (D4): a lot earned at an instant expires
 * the configured number of months later. Both a transfer's lot and a
 * redistribution share are earned credits.
 */
@Component
public class EarnedCreditLifetime {

    private final int months;

    public EarnedCreditLifetime(@Value("${credit.earned-credit-lifetime-months}") int months) {
        // a lot must outlive the moment it is earned
        if (months < 1) {
            throw new IllegalArgumentException(
                "credit.earned-credit-lifetime-months must be at least 1, was " + months);
        }
        this.months = months;
    }

    public Instant expiryOfCreditsEarnedAt(Instant earnedAt) {
        return earnedAt.atOffset(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
