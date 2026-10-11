/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, issue #169.
 * Scope: the CreditOperations implementation, written from
 * docs/credit-service.md ("How each event is handled", "What each
 * operation answers", D2, D4, D6). The outcomes, lock order, lot rules
 * and history rows are the team's design; the tool wrote the code to
 * it. Where the doc is silent the tool took the plainest reading and
 * listed each one in ai/usage-log.md for the author to confirm.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.credit.entity.CreditAccount;
import foc.credit.entity.CreditHistoryEntry;
import foc.credit.entity.CreditHistoryType;
import foc.credit.entity.CreditLot;
import foc.credit.entity.CreditReservationSlice;
import foc.credit.entity.ReservationStatus;
import foc.credit.entity.Reserved;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.CreditHistoryEntryRepository;
import foc.credit.repository.CreditLotRepository;
import foc.credit.repository.CreditReservationSliceRepository;
import foc.credit.repository.ReservedRepository;

/**
 * The credit operations of docs/credit-service.md. Each runs in one
 * transaction, the caller's if there is one (the request event handler's,
 * so the reply commits with it), and takes its row locks in the fixed
 * order of D2: the request's reservation, then accounts by ascending
 * user ID.
 */
@Service
@Transactional
public class CreditOperationsService implements CreditOperations {

    // Credit F1.1
    static final int SIGN_UP_CREDITS = 5;
    // earned credits expire this long after they are earned (D4, F6.1)
    static final int EARNED_CREDIT_LIFETIME_MONTHS = 3;

    private final CreditAccountRepository accounts;
    private final ReservedRepository reservations;
    private final CreditReservationSliceRepository slices;
    private final CreditLotRepository lots;
    private final CreditHistoryEntryRepository history;
    private final EntityManager entityManager;

    public CreditOperationsService(CreditAccountRepository accounts, ReservedRepository reservations,
            CreditReservationSliceRepository slices, CreditLotRepository lots,
            CreditHistoryEntryRepository history, EntityManager entityManager) {
        this.accounts = accounts;
        this.reservations = reservations;
        this.slices = slices;
        this.lots = lots;
        this.history = history;
        this.entityManager = entityManager;
    }

    @Override
    public CreditAccount getOrCreate(String userId) {
        createIfAbsent(userId, Instant.now());
        return accounts.findById(userId).orElseThrow();
    }

    @Override
    public ReserveResult reserve(String requestRef, String requesterId, int amount) {
        if (reservations.findForUpdate(requestRef).isPresent()) {
            return ReserveResult.DUPLICATE;
        }
        Instant now = Instant.now();
        // a refusal's record references the account too
        CreditAccount account = lockAccount(requesterId, now);
        if (amount < 1) {
            reject(requestRef, requesterId, amount, now);
            return ReserveResult.REJECTED_INVALID_AMOUNT;
        }
        if (account.getAvailable() < amount) {
            reject(requestRef, requesterId, amount, now);
            return ReserveResult.REJECTED_INSUFFICIENT_CREDITS;
        }

        insert(new Reserved(requestRef, requesterId, amount, ReservationStatus.HELD, now));
        slices.saveAll(takeFromLots(requestRef, requesterId, amount));
        account.setAvailable(account.getAvailable() - amount);
        account.setReserved(account.getReserved() + amount);
        record(requesterId, CreditHistoryType.RESERVE, amount, requestRef, now);
        return ReserveResult.RESERVED;
    }

    @Override
    public SettleResult transfer(String requestRef, String courierId) {
        Optional<Reserved> found = reservations.findForUpdate(requestRef);
        if (found.isEmpty()) {
            return SettleResult.INVALID_STATE;
        }
        Reserved reservation = found.get();
        if (reservation.getStatus() == ReservationStatus.TRANSFERRED) {
            return courierId.equals(reservation.getCourierId()) ? SettleResult.DUPLICATE : SettleResult.INVALID_STATE;
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return SettleResult.INVALID_STATE;
        }

        Instant now = Instant.now();
        String requesterId = reservation.getRequesterId();
        int amount = reservation.getAmount();
        CreditAccount requester;
        CreditAccount courier;
        // ascending user ID (D2)
        if (requesterId.compareTo(courierId) <= 0) {
            requester = lockAccount(requesterId, now);
            courier = lockAccount(courierId, now);
        } else {
            courier = lockAccount(courierId, now);
            requester = lockAccount(requesterId, now);
        }

        requester.setReserved(requester.getReserved() - amount);
        courier.setAvailable(courier.getAvailable() + amount);
        lots.save(new CreditLot(courierId, amount, now, expiryOfCreditsEarnedAt(now)));
        record(requesterId, CreditHistoryType.TRANSFER_OUT, amount, requestRef, now);
        record(courierId, CreditHistoryType.TRANSFER_IN, amount, requestRef, now);
        reservation.setStatus(ReservationStatus.TRANSFERRED);
        reservation.setCourierId(courierId);
        reservation.setSettledAt(now);
        return SettleResult.SETTLED;
    }

    @Override
    public SettleResult release(String requestRef, String requesterId) {
        Instant now = Instant.now();
        Optional<Reserved> found = reservations.findForUpdate(requestRef);
        if (found.isEmpty()) {
            // the cancel arrived first: an amount-0 record, so a late
            // reserve is a duplicate
            lockAccount(requesterId, now);
            Reserved released = new Reserved(requestRef, requesterId, 0, ReservationStatus.RELEASED, now);
            released.setSettledAt(now);
            insert(released);
            return SettleResult.SETTLED;
        }
        Reserved reservation = found.get();
        if (reservation.getStatus() == ReservationStatus.TRANSFERRED) {
            return SettleResult.INVALID_STATE;
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            // RELEASED already, or REJECTED with nothing held
            return SettleResult.DUPLICATE;
        }

        // the requester on the record, not the one the event names
        String ownerId = reservation.getRequesterId();
        int amount = reservation.getAmount();
        CreditAccount account = lockAccount(ownerId, now);
        for (CreditReservationSlice slice : slices.findByRequestRef(requestRef)) {
            lots.save(new CreditLot(ownerId, slice.getAmount(), slice.getEarnedAt(), slice.getExpiresAt()));
        }
        account.setReserved(account.getReserved() - amount);
        account.setAvailable(account.getAvailable() + amount);
        record(ownerId, CreditHistoryType.RELEASE, amount, requestRef, now);
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setSettledAt(now);
        return SettleResult.SETTLED;
    }

    // the sign-up lot and PROVISION row are written only by the call that created the account
    private void createIfAbsent(String userId, Instant now) {
        if (accounts.insertIfAbsent(userId, SIGN_UP_CREDITS, now) == 1) {
            lots.save(new CreditLot(userId, SIGN_UP_CREDITS, now, null));
            record(userId, CreditHistoryType.PROVISION, SIGN_UP_CREDITS, null, now);
        }
    }

    private CreditAccount lockAccount(String userId, Instant now) {
        createIfAbsent(userId, now);
        return accounts.findForUpdate(userId).orElseThrow();
    }

    private void reject(String requestRef, String requesterId, int amount, Instant now) {
        Reserved rejected = new Reserved(requestRef, requesterId, amount, ReservationStatus.REJECTED, now);
        rejected.setSettledAt(now);
        insert(rejected);
    }

    /**
     * Inserts the request's record, never updates one. Two first
     * deliveries of one event both find no record to lock and both get
     * here; the second fails on the request_ref primary key, rolls back
     * and is retried as a duplicate. The repository's save would merge
     * instead, and overwrite the record the first one committed.
     * Flushed now, so the failure is raised here and the slices, which
     * reference the record, are inserted after it.
     */
    private void insert(Reserved record) {
        entityManager.persist(record);
        entityManager.flush();
    }

    // spends the owner's lots soonest expiry first and returns what was taken from each
    private List<CreditReservationSlice> takeFromLots(String requestRef, String ownerId, int amount) {
        List<CreditReservationSlice> taken = new ArrayList<>();
        int left = amount;
        for (CreditLot lot : lots.findInSpendOrder(ownerId)) {
            if (left == 0) {
                break;
            }
            int part = Math.min(left, lot.getRemaining());
            taken.add(new CreditReservationSlice(requestRef, part, lot.getEarnedAt(), lot.getExpiresAt()));
            if (part == lot.getRemaining()) {
                lots.delete(lot);
            } else {
                lot.setRemaining(lot.getRemaining() - part);
            }
            left -= part;
        }
        if (left > 0) {
            throw new IllegalStateException(
                "lots of " + ownerId + " hold " + left + " fewer credits than the available balance");
        }
        return taken;
    }

    private void record(String userId, CreditHistoryType type, int amount, String requestRef, Instant now) {
        history.save(new CreditHistoryEntry(userId, type, amount, requestRef, now));
    }

    private static Instant expiryOfCreditsEarnedAt(Instant earnedAt) {
        return earnedAt.atOffset(ZoneOffset.UTC).plusMonths(EARNED_CREDIT_LIFETIME_MONTHS).toInstant();
    }
}
