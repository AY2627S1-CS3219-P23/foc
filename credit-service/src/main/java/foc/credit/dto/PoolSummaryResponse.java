/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: GET /credits/admin/pool's body, the fields the team chose.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.time.Instant;

import foc.credit.service.PoolQueryService.PoolSummary;

/** {@code lastRun} is null before the first redistribution. */
public record PoolSummaryResponse(int poolBalance, long accounts, long share, long remainder,
        RedistributionRunResponse lastRun, Instant nextRunAt) {

    public static PoolSummaryResponse of(PoolSummary summary) {
        return new PoolSummaryResponse(summary.balance(), summary.accounts(), summary.share(), summary.remainder(),
            summary.lastRun() == null ? null : RedistributionRunResponse.of(summary.lastRun()),
            summary.nextRunAt());
    }
}
