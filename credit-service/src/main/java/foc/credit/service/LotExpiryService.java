/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-16, issue #173.
 * Scope: the expiry half of the scheduler, written from
 * docs/credit-service.md (D2, D4, "How lots are spent, returned and
 * expired"). One transaction per account is the team's decision; the
 * tool wrote the code to it.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.List;

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

/**
 * Moves what remains in lots past their expiry into the common pool
 * (F6.1). Each owner is one transaction: their account is locked, then
 * their lots read again and emptied, then the pool locked and credited
 * (D2), with one {@code EXPIRY} history row for the total. Held credits
 * are not in any lot's {@code remaining}, so they are untouched; a
 * release returns them to their lot, and the next run moves them.
 */
@Service
public class LotExpiryService {

    private final CreditAccountRepository accounts;
    private final CreditLotRepository lots;
    private final CommonPoolRepository pool;
    private final CreditHistoryEntryRepository history;

    public LotExpiryService(CreditAccountRepository accounts, CreditLotRepository lots, CommonPoolRepository pool,
            CreditHistoryEntryRepository history) {
        this.accounts = accounts;
        this.lots = lots;
        this.pool = pool;
        this.history = history;
    }

    // who the run will visit; each is re-checked under the lock
    @Transactional(readOnly = true)
    public List<String> ownersWithExpiredLots(Instant now) {
        return lots.findOwnersWithExpiredLots(now);
    }

    /**
     * Expires the owner's lots that are past {@code now} and returns the
     * credits moved to the pool, 0 if there were none left to move (a
     * reservation or another run took them first).
     */
    @Transactional
    public int expireLotsOf(String ownerId, Instant now) {
        CreditAccount account = accounts.findForUpdate(ownerId).orElseThrow();
        List<CreditLot> expired = lots.findExpired(ownerId, now);
        int total = 0;
        for (CreditLot lot : expired) {
            total += lot.getRemaining();
            // kept at zero: slices reference it, and a release may refill it
            lot.setRemaining(0);
        }
        if (total == 0) {
            return 0;
        }
        account.setAvailable(account.getAvailable() - total);
        CommonPool common = pool.lockPool();
        common.setBalance(common.getBalance() + total);
        history.save(new CreditHistoryEntry(ownerId, CreditHistoryType.EXPIRY, total, null, now));
        return total;
    }
}
