/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: one redistribution run as the admin API returns it.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.dto;

import java.time.Instant;

import foc.credit.entity.RedistributionRun;

/** {@code distributed} counts every credit taken from the pool, the remainder included. */
public record RedistributionRunResponse(String period, int distributed, int remainder, Instant ranAt) {

    public static RedistributionRunResponse of(RedistributionRun run) {
        return new RedistributionRunResponse(run.getPeriod(), run.getDistributed(), run.getRemainder(),
            run.getRanAt());
    }
}
