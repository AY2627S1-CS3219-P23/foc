/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the pool summary's derived figures with the repositories
 * mocked: the share preview (and none with no accounts), and the next
 * run, in Singapore time, for a recorded month, a month still missing,
 * and the first hours of a Singapore month that is still the previous
 * month in UTC.
 * 2026-10-28, issue #267: the count is the open accounts (USR-7).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import foc.credit.entity.CommonPool;
import foc.credit.entity.RedistributionRun;
import foc.credit.repository.CommonPoolRepository;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.RedistributionRunRepository;
import foc.credit.service.PoolQueryService.PoolSummary;

class PoolQueryServiceTest {

    private final CommonPoolRepository pool = mock(CommonPoolRepository.class);
    private final CreditAccountRepository accounts = mock(CreditAccountRepository.class);
    private final RedistributionRunRepository runs = mock(RedistributionRunRepository.class);
    private final PoolQueryService service = new PoolQueryService(pool, accounts, runs);

    // 20 October 2026, 12:00 in Singapore
    private static final Instant MID_OCTOBER = Instant.parse("2026-10-20T04:00:00Z");

    @Test
    void thePreviewIsTheFloorEachAndTheRemainderOneMoreEach() {
        given(37, 10, null);

        PoolSummary summary = service.summary(MID_OCTOBER);

        assertThat(summary.balance()).isEqualTo(37);
        assertThat(summary.accounts()).isEqualTo(10);
        assertThat(summary.share()).isEqualTo(3);
        assertThat(summary.remainder()).isEqualTo(7);
    }

    @Test
    void withNoAccountsThereIsNothingToPreview() {
        given(5, 0, null);

        PoolSummary summary = service.summary(MID_OCTOBER);

        assertThat(summary.share()).isZero();
        assertThat(summary.remainder()).isZero();
    }

    @Test
    void beforeTheFirstRunThereIsNoLastRunAndTheNextIsTonight() {
        given(5, 2, null);

        PoolSummary summary = service.summary(MID_OCTOBER);

        assertThat(summary.lastRun()).isNull();
        // midnight starting 21 October in Singapore
        assertThat(summary.nextRunAt()).isEqualTo(Instant.parse("2026-10-20T16:00:00Z"));
    }

    @Test
    void onceTheMonthIsRecordedTheNextIsTheFirstOfNextMonth() {
        RedistributionRun october = new RedistributionRun("2026-10", 12, 2, Instant.parse("2026-09-30T16:00:00Z"));
        given(0, 6, october);

        PoolSummary summary = service.summary(MID_OCTOBER);

        assertThat(summary.lastRun()).isSameAs(october);
        // 1 November, 00:00 in Singapore
        assertThat(summary.nextRunAt()).isEqualTo(Instant.parse("2026-10-31T16:00:00Z"));
    }

    @Test
    void aMonthStillMissingIsDueAtTheNextMidnight() {
        // September is the last recorded month; October was missed or failed
        RedistributionRun september = new RedistributionRun("2026-09", 4, 0, Instant.parse("2026-08-31T16:00:00Z"));
        given(9, 3, september);

        assertThat(service.summary(MID_OCTOBER).nextRunAt()).isEqualTo(Instant.parse("2026-10-20T16:00:00Z"));
    }

    @Test
    void theMonthIsTakenInSingaporeTime() {
        // 1 November, 01:00 in Singapore, still 31 October in UTC:
        // October is recorded, but November is not
        RedistributionRun october = new RedistributionRun("2026-10", 12, 2, Instant.parse("2026-09-30T16:00:00Z"));
        given(0, 6, october);

        PoolSummary summary = service.summary(Instant.parse("2026-10-31T17:00:00Z"));

        assertThat(summary.nextRunAt()).isEqualTo(Instant.parse("2026-11-01T16:00:00Z"));
    }

    private void given(int balance, long accountCount, RedistributionRun last) {
        CommonPool common = mock(CommonPool.class);
        when(common.getBalance()).thenReturn(balance);
        when(pool.findById(CommonPool.ID)).thenReturn(Optional.of(common));
        when(accounts.countOpen()).thenReturn(accountCount);
        when(runs.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(last == null ? List.of() : List.of(last)));
    }
}
