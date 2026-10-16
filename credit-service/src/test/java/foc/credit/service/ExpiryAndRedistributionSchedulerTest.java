/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the scheduler with both services mocked and the clock fixed:
 * one account's failed expiry not stopping the others, the month a
 * redistribution counts as taken in Singapore time, and the startup
 * catch-up running only once the month's redistribution time has
 * passed, after an expiry run (team decision), without throwing.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import foc.credit.service.RedistributionService.RedistributionOutcome;

class ExpiryAndRedistributionSchedulerTest {

    private final LotExpiryService expiry = mock(LotExpiryService.class);
    private final RedistributionService redistribution = mock(RedistributionService.class);

    @Test
    void oneAccountThatFailsToExpireDoesNotStopTheOthers() {
        Instant now = Instant.parse("2026-10-15T16:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1", "u-2", "u-3"));
        when(expiry.expireLotsOf("u-2", now)).thenThrow(new IllegalStateException("lock timeout"));

        schedulerAt(now).expireLots();

        verify(expiry).expireLotsOf("u-1", now);
        verify(expiry).expireLotsOf("u-2", now);
        verify(expiry).expireLotsOf("u-3", now);
    }

    @Test
    void theScheduledRunCountsAsTheSingaporeMonth() {
        // 00:10 on 1 November in Singapore is still 31 October in UTC
        Instant now = Instant.parse("2026-10-31T16:10:00Z");
        ranFine();

        schedulerAt(now).redistributePool();

        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void aMissedMonthIsCaughtUpAtStartupAfterAnExpiryRun() {
        // 1 November, 00:30 in Singapore: this month's run time has passed
        Instant now = Instant.parse("2026-10-31T16:30:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1"));
        ranFine();

        schedulerAt(now).catchUpMissedRedistribution();

        // the order of a normal night: what expired meanwhile is shared out too
        InOrder order = inOrder(expiry, redistribution);
        order.verify(expiry).expireLotsOf("u-1", now);
        order.verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void laterInTheMonthTheCatchUpStillRuns() {
        Instant now = Instant.parse("2026-11-20T04:00:00Z");
        ranFine();

        schedulerAt(now).catchUpMissedRedistribution();

        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void noCatchUpBeforeTheMonthsRunTime() {
        // 1 November, 00:05 in Singapore: the scheduled run at 00:10 is still to come
        schedulerAt(Instant.parse("2026-10-31T16:05:00Z")).catchUpMissedRedistribution();

        verifyNoInteractions(expiry, redistribution);
    }

    @Test
    void aFailedExpiryBeforeTheCatchUpStillLetsTheRedistributionRun() {
        Instant now = Instant.parse("2026-11-20T04:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenThrow(new IllegalStateException("database down"));
        ranFine();

        // a ready-event listener that throws would fail the application's startup
        assertThatCode(() -> schedulerAt(now).catchUpMissedRedistribution()).doesNotThrowAnyException();

        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void aFailedRedistributionIsLoggedNotThrown() {
        when(redistribution.redistribute(any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> schedulerAt(Instant.parse("2026-11-20T04:00:00Z")).catchUpMissedRedistribution())
            .doesNotThrowAnyException();
    }

    private void ranFine() {
        when(redistribution.redistribute(any(), any()))
            .thenReturn(new RedistributionOutcome("2026-11", false, 2, 0, 0, 0));
    }

    private ExpiryAndRedistributionScheduler schedulerAt(Instant now) {
        return new ExpiryAndRedistributionScheduler(expiry, redistribution, Clock.fixed(now, ZoneOffset.UTC));
    }
}
