/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, issue #169.
 * Scope: the CreditOperations implementation, written from
 * docs/credit-service.md ("How each event is handled", "What each
 * operation answers", D2, D4, D6). The outcomes, lock order, lot rules
 * and history rows are the team's design; the tool wrote the code to
 * it. Where the doc is silent the tool took the plainest reading and
 * listed each one in ai/usage-log.md for the author to confirm.
 * Same day, team review of the first draft: the sign-up amount and the
 * earned-credit lifetime read from configuration, one log line per
 * operation, a release returning credits to the lots they came from,
 * and the request's record inserted through the repository. The
 * four decisions are the team's.
 * PR #175 review (Leong Wei Zhi): a transfer with no record answers
 * NO_RESERVATION instead of INVALID_STATE (team decision). Further
 * team decisions on that review: a transfer naming no courier, or a
 * release naming no requester for a request with no record, answers
 * INVALID_STATE; and the log lines are written after the commit (the
 * hook is the team's sketch).
 * PR #189 review (Leong Wei Zhi), 2026-10-14: getOrCreate reads an
 * existing account before attempting the insert, per the reviewer's
 * comment.
 * PR #193 review (Leong Wei Zhi), 2026-10-16: the earned-credit expiry
 * moved to EarnedCreditLifetime, shared with the redistribution, per the
 * reviewer's comment.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * user ID. A user's lots are only read or changed under that user's
 * account lock.
 *
 * <p>Each operation that changes credits logs one line with the request,
 * the users it touched and its outcome, and creating an account logs
 * one. The line is written after the transaction commits, so an attempt
 * that rolls back leaves none.
 */
@Service
@Transactional
public class CreditOperationsService implements CreditOperations {

    private static final Logger log = LoggerFactory.getLogger(CreditOperationsService.class);

    private final CreditAccountRepository accounts;
    private final ReservedRepository reservations;
    private final CreditReservationSliceRepository slices;
    private final CreditLotRepository lots;
    private final CreditHistoryEntryRepository history;
    private final int signUpCredits;
    private final EarnedCreditLifetime earnedCreditLifetime;

    public CreditOperationsService(CreditAccountRepository accounts, ReservedRepository reservations,
            CreditReservationSliceRepository slices, CreditLotRepository lots,
            CreditHistoryEntryRepository history,
            @Value("${credit.sign-up-credits}") int signUpCredits,
            EarnedCreditLifetime earnedCreditLifetime) {
        // a history row's amount is at least 1
        if (signUpCredits < 1) {
            throw new IllegalArgumentException("credit.sign-up-credits must be at least 1, was " + signUpCredits);
        }
        this.accounts = accounts;
        this.reservations = reservations;
        this.slices = slices;
        this.lots = lots;
        this.history = history;
        this.signUpCredits = signUpCredits;
        this.earnedCreditLifetime = earnedCreditLifetime;
    }

    @Override
    public CreditAccount getOrCreate(String userId) {
        // an existing account is read without the insert attempt, which
        // would leave a dead tuple behind on every read
        Optional<CreditAccount> existing = accounts.findById(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        createIfAbsent(userId, Instant.now());
        return accounts.findById(userId).orElseThrow();
    }

    @Override
    public ReserveResult reserve(String requestRef, String requesterId, int amount) {
        ReserveResult result = applyReserve(requestRef, requesterId, amount);
        afterCommit(() -> log.info("reserve request={} requester={} amount={} outcome={}",
            requestRef, requesterId, amount, result));
        return result;
    }

    @Override
    public SettleResult transfer(String requestRef, String courierId) {
        // Before the lookup: with no record either, a completion naming no
        // courier would answer NO_RESERVATION and be retried to no purpose.
        if (isMissing(courierId)) {
            logTransfer(requestRef, null, courierId, null, SettleResult.INVALID_STATE);
            return SettleResult.INVALID_STATE;
        }
        Optional<Reserved> found = reservations.findForUpdate(requestRef);
        SettleResult result = found.map(reservation -> applyTransfer(reservation, courierId))
            .orElse(SettleResult.NO_RESERVATION);
        logTransfer(requestRef, found.map(Reserved::getRequesterId).orElse(null), courierId,
            found.map(Reserved::getAmount).orElse(null), result);
        return result;
    }

    @Override
    public SettleResult release(String requestRef, String requesterId) {
        Optional<Reserved> found = reservations.findForUpdate(requestRef);
        SettleResult result = found.map(this::applyRelease)
            .orElseGet(() -> recordReleasedBeforeReserved(requestRef, requesterId));
        // the requester on the record, when there is one, not the one the event names
        String loggedRequester = found.map(Reserved::getRequesterId).orElse(requesterId);
        int loggedAmount = found.map(Reserved::getAmount).orElse(0);
        afterCommit(() -> log.info("release request={} requester={} amount={} outcome={}",
            requestRef, loggedRequester, loggedAmount, result));
        return result;
    }

    private ReserveResult applyReserve(String requestRef, String requesterId, int amount) {
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

        // Two first deliveries of one event both find no record to lock
        // and both get here; the second fails on the request_ref primary
        // key, rolls back and is retried as a duplicate.
        reservations.insert(new Reserved(requestRef, requesterId, amount, ReservationStatus.HELD, now));
        slices.saveAll(takeFromLots(requestRef, requesterId, amount));
        account.setAvailable(account.getAvailable() - amount);
        account.setReserved(account.getReserved() + amount);
        record(requesterId, CreditHistoryType.RESERVE, amount, requestRef, now);
        return ReserveResult.RESERVED;
    }

    private SettleResult applyTransfer(Reserved reservation, String courierId) {
        if (reservation.getStatus() == ReservationStatus.TRANSFERRED) {
            return courierId.equals(reservation.getCourierId()) ? SettleResult.DUPLICATE : SettleResult.INVALID_STATE;
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return SettleResult.INVALID_STATE;
        }

        Instant now = Instant.now();
        String requestRef = reservation.getRequestRef();
        String requesterId = reservation.getRequesterId();
        int amount = reservation.getAmount();
        CreditAccount requester;
        CreditAccount courier;
        // Ascending user ID (D2). A courier who is the requester is the
        // same row both times: the credits end up back in their available
        // balance, as a newly earned lot.
        if (requesterId.compareTo(courierId) <= 0) {
            requester = lockAccount(requesterId, now);
            courier = lockAccount(courierId, now);
        } else {
            courier = lockAccount(courierId, now);
            requester = lockAccount(requesterId, now);
        }

        requester.setReserved(requester.getReserved() - amount);
        courier.setAvailable(courier.getAvailable() + amount);
        lots.save(new CreditLot(courierId, amount, now, earnedCreditLifetime.expiryOfCreditsEarnedAt(now)));
        record(requesterId, CreditHistoryType.TRANSFER_OUT, amount, requestRef, now);
        record(courierId, CreditHistoryType.TRANSFER_IN, amount, requestRef, now);
        reservation.setStatus(ReservationStatus.TRANSFERRED);
        reservation.setCourierId(courierId);
        reservation.setSettledAt(now);
        return SettleResult.SETTLED;
    }

    private SettleResult applyRelease(Reserved reservation) {
        if (reservation.getStatus() == ReservationStatus.TRANSFERRED) {
            return SettleResult.INVALID_STATE;
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            // RELEASED already, or REJECTED with nothing held
            return SettleResult.DUPLICATE;
        }

        Instant now = Instant.now();
        String requestRef = reservation.getRequestRef();
        String ownerId = reservation.getRequesterId();
        int amount = reservation.getAmount();
        CreditAccount account = lockAccount(ownerId, now);
        // Back to the lot each slice came from, so the credits keep their
        // expiry and spend order. A lot that has since passed its expiry
        // takes them too; the next expiry run moves them to the pool.
        for (CreditReservationSlice slice : slices.findByRequestRef(requestRef)) {
            CreditLot lot = lots.findById(slice.getLotId()).orElseThrow();
            lot.setRemaining(lot.getRemaining() + slice.getAmount());
        }
        account.setReserved(account.getReserved() - amount);
        account.setAvailable(account.getAvailable() + amount);
        record(ownerId, CreditHistoryType.RELEASE, amount, requestRef, now);
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setSettledAt(now);
        return SettleResult.SETTLED;
    }

    // the cancel arrived first: an amount-0 record, so a late reserve is a duplicate
    private SettleResult recordReleasedBeforeReserved(String requestRef, String requesterId) {
        // the record needs an account to reference, and the event names none
        if (isMissing(requesterId)) {
            return SettleResult.INVALID_STATE;
        }
        Instant now = Instant.now();
        lockAccount(requesterId, now);
        Reserved released = new Reserved(requestRef, requesterId, 0, ReservationStatus.RELEASED, now);
        released.setSettledAt(now);
        reservations.insert(released);
        return SettleResult.SETTLED;
    }

    // the sign-up lot and PROVISION row are written only by the call that created the account
    private void createIfAbsent(String userId, Instant now) {
        if (accounts.insertIfAbsent(userId, signUpCredits, now) == 1) {
            lots.save(new CreditLot(userId, signUpCredits, now, null));
            record(userId, CreditHistoryType.PROVISION, signUpCredits, null, now);
            afterCommit(() -> log.info("provision user={} amount={} outcome=CREATED", userId, signUpCredits));
        }
    }

    private CreditAccount lockAccount(String userId, Instant now) {
        createIfAbsent(userId, now);
        return accounts.findForUpdate(userId).orElseThrow();
    }

    private void reject(String requestRef, String requesterId, int amount, Instant now) {
        Reserved rejected = new Reserved(requestRef, requesterId, amount, ReservationStatus.REJECTED, now);
        rejected.setSettledAt(now);
        reservations.insert(rejected);
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
            taken.add(new CreditReservationSlice(requestRef, lot.getId(), part, lot.getEarnedAt(), lot.getExpiresAt()));
            // a lot spent to zero is kept: the slice references it
            lot.setRemaining(lot.getRemaining() - part);
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

    private static boolean isMissing(String userId) {
        return userId == null || userId.isBlank();
    }

    private static void logTransfer(String requestRef, String requesterId, String courierId, Integer amount,
            SettleResult result) {
        afterCommit(() -> log.info("transfer request={} requester={} courier={} amount={} outcome={}",
            requestRef, requesterId, courierId, amount, result));
    }

    // AI-generated (edited by Ryan Ang): the team's sketch from the PR #175 review
    // runs the log line once the surrounding transaction has committed, or at once with none
    private static void afterCommit(Runnable logLine) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logLine.run();
                }
            });
        } else {
            logLine.run();
        }
    }
}
