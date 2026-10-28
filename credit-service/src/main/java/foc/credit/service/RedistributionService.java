/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the monthly redistribution, written from
 * docs/credit-service.md (D2, D4, F6.1.1, F6.1.2, the redistribution_run
 * table). A month with an empty pool still being recorded is the team's
 * decision; the tool wrote the code to it.
 * PR #193 review (Leong Wei Zhi), same day: a share's expiry from
 * EarnedCreditLifetime, shared with the transfer, per the reviewer's
 * comment.
 * 2026-10-28, Claude Code (Opus 5.5), issue #267: open accounts only (team decision USR-7).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.entity.CommonPool;
import foc.credit.entity.CreditAccount;
import foc.credit.entity.CreditHistoryEntry;
import foc.credit.entity.CreditHistoryType;
import foc.credit.entity.CreditLot;
import foc.credit.repository.CommonPoolRepository;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.CreditHistoryEntryRepository;
import foc.credit.repository.CreditLotRepository;
import foc.credit.repository.RedistributionRunRepository;

/**
 * Shares the common pool out to every account, once per month (F6.1.1,
 * F6.1.2): {@code floor(pool / accounts)} each, and the remainder one
 * credit each to accounts chosen at random. In one transaction, every
 * account is locked in ascending user ID order, then the pool (D2).
 *
 * <p>Each share is a new lot that expires the configured lifetime after
 * it is received (D4), with a {@code REDISTRIBUTION} history row. The
 * month is recorded in {@code redistribution_run} even when there was
 * nothing to share, and a second run for a recorded month changes
 * nothing; the table's primary key stops two runs that overlap.
 */
@Service
public class RedistributionService {

    private final CreditAccountRepository accounts;
    private final CreditLotRepository lots;
    private final CommonPoolRepository pool;
    private final CreditHistoryEntryRepository history;
    private final RedistributionRunRepository runs;
    private final EarnedCreditLifetime earnedCreditLifetime;
    private final Random random = new Random();

    public RedistributionService(CreditAccountRepository accounts, CreditLotRepository lots,
            CommonPoolRepository pool, CreditHistoryEntryRepository history, RedistributionRunRepository runs,
            EarnedCreditLifetime earnedCreditLifetime) {
        this.accounts = accounts;
        this.lots = lots;
        this.pool = pool;
        this.history = history;
        this.runs = runs;
        this.earnedCreditLifetime = earnedCreditLifetime;
    }

    @Transactional
    public RedistributionOutcome redistribute(YearMonth period, Instant now) {
        String key = period.toString();
        if (runs.existsById(key)) {
            return RedistributionOutcome.alreadyRan(key);
        }
        // open accounts only: a purged user's account gets no share (USR-7)
        List<CreditAccount> all = accounts.findAllOpenForUpdate();
        CommonPool common = pool.lockPool();
        // a run for this month that committed while this one waited for the locks
        if (runs.existsById(key)) {
            return RedistributionOutcome.alreadyRan(key);
        }

        int count = all.size();
        int share = count == 0 ? 0 : common.getBalance() / count;
        int remainder = count == 0 ? 0 : common.getBalance() % count;
        List<Integer> order = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        boolean[] getsOneMore = new boolean[count];
        for (int i = 0; i < remainder; i++) {
            getsOneMore[order.get(i)] = true;
        }

        Instant expiresAt = earnedCreditLifetime.expiryOfCreditsEarnedAt(now);
        for (int i = 0; i < count; i++) {
            int amount = share + (getsOneMore[i] ? 1 : 0);
            if (amount == 0) {
                continue;
            }
            CreditAccount account = all.get(i);
            account.setAvailable(account.getAvailable() + amount);
            lots.save(new CreditLot(account.getUserId(), amount, now, expiresAt));
            history.save(new CreditHistoryEntry(account.getUserId(), CreditHistoryType.REDISTRIBUTION, amount,
                null, now));
        }
        int distributed = share * count + remainder;
        common.setBalance(common.getBalance() - distributed);
        runs.insert(key, distributed, remainder, now);
        return new RedistributionOutcome(key, false, count, distributed, share, remainder);
    }

    /**
     * What one call did. {@code distributed} is every credit taken from
     * the pool, the remainder included.
     */
    public record RedistributionOutcome(String period, boolean alreadyRan, int accounts, int distributed, int share,
            int remainder) {

        static RedistributionOutcome alreadyRan(String period) {
            return new RedistributionOutcome(period, true, 0, 0, 0, 0);
        }
    }
}
