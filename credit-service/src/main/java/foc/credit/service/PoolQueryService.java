/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-17, issue #174 (admin dashboard
 * pool box).
 * Scope: the admin reads of the common pool and the redistribution runs,
 * written to the team's decisions: a summary (balance, the accounts a
 * redistribution shares among, a preview of each one's share, the last
 * run and the next) and a paged run history, for ADMIN and OWNER. The
 * next run follows the scheduler's rule from PR #193 (D12 there): one
 * routine daily at 00:00 Singapore time redistributes a month that has
 * no recorded run.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.entity.CommonPool;
import foc.credit.entity.RedistributionRun;
import foc.credit.repository.CommonPoolRepository;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.RedistributionRunRepository;

/**
 * What an admin may read of the common pool. Nothing here locks or
 * writes: the figures are a snapshot, and the redistribution itself
 * counts again under its locks.
 */
@Service
public class PoolQueryService {

    // the zone the redistribution decides its month in (D12)
    static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    // "yyyy-MM" sorts as text in date order
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("period"));

    private final CommonPoolRepository pool;
    private final CreditAccountRepository accounts;
    private final RedistributionRunRepository runs;

    public PoolQueryService(CommonPoolRepository pool, CreditAccountRepository accounts,
            RedistributionRunRepository runs) {
        this.pool = pool;
        this.accounts = accounts;
        this.runs = runs;
    }

    @Transactional(readOnly = true)
    public PoolSummary summary(Instant now) {
        int balance = pool.findById(CommonPool.ID).orElseThrow().getBalance();
        long count = accounts.count();
        Optional<RedistributionRun> last = runs.findAll(PageRequest.of(0, 1, LATEST_FIRST)).stream().findFirst();
        return new PoolSummary(balance, count, count == 0 ? 0 : balance / count, count == 0 ? 0 : balance % count,
            last.orElse(null), nextRunAt(now, last));
    }

    /** Newest month first; a page past the last one is empty, not an error. */
    @Transactional(readOnly = true)
    public Page<RedistributionRun> runs(int page, int size) {
        return runs.findAll(PageRequest.of(page, size, LATEST_FIRST));
    }

    // Once this month is recorded, the next run is the 1st of next month
    // at 00:00. Until then the daily routine tries it at each midnight,
    // so a missed or failed month is due at the next one.
    static Instant nextRunAt(Instant now, Optional<RedistributionRun> last) {
        YearMonth month = YearMonth.from(now.atZone(ZONE));
        boolean recorded = last.map(run -> run.getPeriod().equals(month.toString())).orElse(false);
        if (recorded) {
            return month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        }
        return now.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /**
     * {@code share} and {@code remainder} preview what a redistribution
     * run now would give: {@code floor(balance / accounts)} each, and 1
     * more to {@code remainder} accounts. {@code lastRun} is null before
     * the first run.
     */
    public record PoolSummary(int balance, long accounts, long share, long remainder, RedistributionRun lastRun,
            Instant nextRunAt) {
    }
}
