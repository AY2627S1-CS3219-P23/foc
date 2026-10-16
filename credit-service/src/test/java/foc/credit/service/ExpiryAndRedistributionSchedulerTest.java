/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the scheduler with both services mocked and the clock fixed:
 * one account's failed expiry not stopping the others, the month a
 * redistribution counts as taken in Singapore time, and the startup
 * catch-up running only once the month's redistribution time has
 * passed, after an expiry run (team decision), without throwing.
 * PR #193 review (Leong Wei Zhi), same day: rewritten for the single
 * routine (team decision): the daily and the startup run both sweep and
 * then redistribute, a month already recorded is left alone, and a
 * failed redistribution is completed by the next day's run.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        alreadyRan();

        schedulerAt(now).runDaily();

        verify(expiry).expireLotsOf("u-1", now);
        verify(expiry).expireLotsOf("u-2", now);
        verify(expiry).expireLotsOf("u-3", now);
    }

    @Test
    void theRunOnTheFirstSweepsThenRedistributesTheSingaporeMonth() {
        // 00:00 on 1 November in Singapore is still 31 October in UTC
        Instant now = Instant.parse("2026-10-31T16:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1"));
        ranFine();

        schedulerAt(now).runDaily();

        // what expired that night is in the pool before it is shared out
        InOrder order = inOrder(expiry, redistribution);
        order.verify(expiry).expireLotsOf("u-1", now);
        order.verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void anOrdinaryDayStillSweepsAndLeavesARecordedMonthAlone() {
        Instant now = Instant.parse("2026-11-20T16:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1"));
        alreadyRan();

        schedulerAt(now).runDaily();

        // the service answers ALREADY_RAN for a recorded month; the scheduler asks every day
        verify(expiry).expireLotsOf("u-1", now);
        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void startupSweepsThenRedistributesAMissedMonth() {
        // 20 November in Singapore: the service was down on the 1st
        Instant now = Instant.parse("2026-11-20T04:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1"));
        ranFine();

        schedulerAt(now).runAtStartup();

        InOrder order = inOrder(expiry, redistribution);
        order.verify(expiry).expireLotsOf("u-1", now);
        order.verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void startupStillSweepsWhenTheMonthIsAlreadyRecorded() {
        Instant now = Instant.parse("2026-11-20T04:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenReturn(List.of("u-1"));
        alreadyRan();

        schedulerAt(now).runAtStartup();

        verify(expiry).expireLotsOf("u-1", now);
        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void aRedistributionThatFailsOnTheFirstIsCompletedTheNextDay() {
        Instant first = Instant.parse("2026-10-31T16:00:00Z");
        Instant second = Instant.parse("2026-11-01T16:00:00Z");
        when(redistribution.redistribute(YearMonth.of(2026, 11), first))
            .thenThrow(new IllegalStateException("database down"));
        when(redistribution.redistribute(YearMonth.of(2026, 11), second))
            .thenReturn(new RedistributionOutcome("2026-11", false, 2, 4, 2, 0));

        assertThatCode(() -> schedulerAt(first).runDaily()).doesNotThrowAnyException();
        schedulerAt(second).runDaily();

        // the failed run recorded nothing, so the 2nd's run asks for November again
        verify(redistribution).redistribute(YearMonth.of(2026, 11), first);
        verify(redistribution).redistribute(YearMonth.of(2026, 11), second);
    }

    @Test
    void aFailedSweepAtStartupStillLetsTheRedistributionRun() {
        Instant now = Instant.parse("2026-11-20T04:00:00Z");
        when(expiry.ownersWithExpiredLots(now)).thenThrow(new IllegalStateException("database down"));
        ranFine();

        // a ready-event listener that throws would fail the application's startup
        assertThatCode(() -> schedulerAt(now).runAtStartup()).doesNotThrowAnyException();

        verify(redistribution).redistribute(YearMonth.of(2026, 11), now);
    }

    @Test
    void aFailedRedistributionAtStartupIsLoggedNotThrown() {
        when(redistribution.redistribute(any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> schedulerAt(Instant.parse("2026-11-20T04:00:00Z")).runAtStartup())
            .doesNotThrowAnyException();
    }

    private void ranFine() {
        when(redistribution.redistribute(any(), any()))
            .thenReturn(new RedistributionOutcome("2026-11", false, 2, 0, 0, 0));
    }

    private void alreadyRan() {
        when(redistribution.redistribute(any(), any())).thenReturn(RedistributionOutcome.alreadyRan("2026-11"));
    }

    private ExpiryAndRedistributionScheduler schedulerAt(Instant now) {
        return new ExpiryAndRedistributionScheduler(expiry, redistribution, Clock.fixed(now, ZoneOffset.UTC));
    }
}
