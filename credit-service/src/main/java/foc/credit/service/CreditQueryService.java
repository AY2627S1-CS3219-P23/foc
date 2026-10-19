/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13, issue #172.
 * Scope: the two reads behind the REST API (docs/credit-service.md
 * "Read API contract"): the balance, and a page of history filtered
 * with a JPA Specification (D5) in the contract's fixed order.
 * 2026-10-17, Claude Code (Opus 5.5), issue #196: the caller's upcoming
 * expiries, read from the lots in spend order; which credits count is
 * the team's decision (reserved and sign-up credits left out).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import foc.credit.entity.CreditAccount;
import foc.credit.entity.CreditHistoryEntry;
import foc.credit.entity.CreditLot;
import foc.credit.repository.CreditHistoryEntryRepository;
import foc.credit.repository.CreditLotRepository;
import jakarta.persistence.criteria.Predicate;

/**
 * What a user may read of their own credits. Both reads get or create
 * the account first (D6), so a first-time caller has a balance and its
 * {@code PROVISION} row rather than nothing.
 */
@Service
public class CreditQueryService {

    // the contract's order, with no sort parameter: newest first, and
    // the ID settles rows of the same instant so pages do not overlap
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final CreditOperations operations;
    private final CreditHistoryEntryRepository history;
    private final CreditLotRepository lots;

    public CreditQueryService(CreditOperations operations, CreditHistoryEntryRepository history,
            CreditLotRepository lots) {
        this.operations = operations;
        this.history = history;
        this.lots = lots;
    }

    public CreditAccount balance(String userId) {
        return operations.getOrCreate(userId);
    }

    /**
     * The lots that will expire, soonest first and by ID within the same
     * instant. The spend order is exactly that with the never-expiring
     * sign-up lot last, so it is dropped here. Drained lots are already
     * left out, and reserved credits are in no lot's {@code remaining}.
     * A lot past its expiry stays in until the next expiry run moves it.
     */
    public List<CreditLot> upcomingExpiries(String userId) {
        operations.getOrCreate(userId);
        return lots.findInSpendOrder(userId).stream()
            .filter(lot -> lot.getExpiresAt() != null)
            .toList();
    }

    /** A page past the last one is an empty page, not an error. */
    public Page<CreditHistoryEntry> history(String userId, HistoryFilter filter, int page, int size) {
        operations.getOrCreate(userId);
        return history.findAll(ownedBy(userId, filter), PageRequest.of(page, size, NEWEST_FIRST));
    }

    // always the one user's rows, then whichever filters were given
    private static Specification<CreditHistoryEntry> ownedBy(String userId, HistoryFilter filter) {
        return (entry, query, criteria) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(criteria.equal(entry.get("userId"), userId));
            if (filter.minAmount() != null) {
                conditions.add(criteria.greaterThanOrEqualTo(entry.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                conditions.add(criteria.lessThanOrEqualTo(entry.get("amount"), filter.maxAmount()));
            }
            if (filter.from() != null) {
                conditions.add(criteria.greaterThanOrEqualTo(entry.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                conditions.add(criteria.lessThan(entry.get("occurredAt"), filter.to()));
            }
            return criteria.and(conditions.toArray(Predicate[]::new));
        };
    }
}
