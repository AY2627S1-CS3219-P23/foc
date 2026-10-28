/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-11, issue #169.
 * Scope: the credit operations against a real Postgres: every row of
 * docs/credit-service.md's "What each operation answers", the lot rules
 * of D4, the races its failure table names, and after each test the
 * checks of "How their consistency is maintained".
 * Same day, team review: a release returns credits to their lots, a
 * transfer to the requester themselves, the record insert called twice,
 * and the races repeated.
 * PR #175 review (Leong Wei Zhi), team decisions: a transfer with no
 * record answers NO_RESERVATION, and the transfer-against-release race
 * is restored; a transfer naming no courier and a release naming no
 * requester.
 * 2026-10-18, issue #165 (reviewer's suggestion): a redelivered
 * reservation on a HELD or REJECTED record answers what the record
 * holds, so the handler can send its reply again.
 * 2026-10-25, issue #235: resolve, the split settlement of a resolved
 * dispute (team decisions DSP-4, DSP-8, DSP-13): refund, payout, split,
 * slice order, repeats, missing fields, a repeated delivery racing, and
 * the other events on a RESOLVED record (team decision).
 * PR #247 review follow-up: a resolution racing another resolution, and
 * a transfer, in the opposite direction, which pins its lock order (D2).
 * 2026-10-28, Claude Code (Opus 5.5), issue #267: open and close by the
 * team's state table (USR-6), closure moving the balance to the pool
 * while held credits stay held, credits reaching a closed account by
 * release, transfer or dispute share going on to the pool (USR-4), and
 * get-or-create never reopening (USR-3). The books' checks replay
 * CLOSURE and hold a closed account to no available credits.
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import foc.credit.PostgresTestContainer;
import foc.credit.entity.CreditAccount;
import foc.credit.entity.ReservationStatus;
import foc.credit.entity.Reserved;
import foc.credit.repository.CreditAccountRepository;
import foc.credit.repository.ReservedRepository;

// Not @Transactional: each operation commits, as it does for the handler,
// so the races are real. Every test uses its own user and request IDs.
@SpringBootTest
class CreditOperationsServiceTest extends PostgresTestContainer {

    // each race runs several times, since one run may not interleave
    private static final int RACE_REPETITIONS = 5;

    private final String requester = "u-req-" + UUID.randomUUID();
    private final String courier = "u-cou-" + UUID.randomUUID();
    private final String request = "r-" + UUID.randomUUID();

    @Autowired
    private CreditOperations operations;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private ReservedRepository reservations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    // "How their consistency is maintained": each check must return no rows
    @AfterEach
    void theBooksBalance() {
        assertThat(jdbc.queryForList("""
            select r.request_ref from credit_reservation r
            left join credit_reservation_slice s on s.request_ref = r.request_ref
            where r.status = 'HELD'
            group by r.request_ref, r.amount
            having r.amount <> coalesce(sum(s.amount), 0)
            """)).as("HELD reservations whose amount differs from their slices").isEmpty();
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_reservation r on r.requester_id = a.user_id and r.status = 'HELD'
            group by a.user_id, a.reserved
            having a.reserved <> coalesce(sum(r.amount), 0)
            """)).as("accounts whose reserved differs from their HELD reservations").isEmpty();
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_lot l on l.owner_id = a.user_id
            group by a.user_id, a.available
            having a.available <> coalesce(sum(l.remaining), 0)
            """)).as("accounts whose available differs from their lots").isEmpty();
        // replaying the history reproduces both balances
        assertThat(jdbc.queryForList("""
            select a.user_id from credit_account a
            left join credit_history h on h.user_id = a.user_id
            group by a.user_id, a.available, a.reserved
            having a.available <> coalesce(sum(case
                    when h.type in ('PROVISION', 'RELEASE', 'TRANSFER_IN', 'REDISTRIBUTION') then h.amount
                    when h.type in ('RESERVE', 'EXPIRY', 'CLOSURE') then -h.amount
                    else 0 end), 0)
                or a.reserved <> coalesce(sum(case
                    when h.type = 'RESERVE' then h.amount
                    when h.type in ('RELEASE', 'TRANSFER_OUT') then -h.amount
                    else 0 end), 0)
            """)).as("accounts whose balances differ from their history").isEmpty();
        // a closed account's credits are all in the pool (USR-4)
        assertThat(jdbc.queryForList(
            "select user_id from credit_account where closed_at is not null and available <> 0"))
            .as("closed accounts holding available credits").isEmpty();
        // the closed economy: credits are only ever created by a provision
        assertThat(jdbc.queryForObject("""
            select (select coalesce(sum(available + reserved), 0) from credit_account)
                 + (select balance from common_pool)
                 - (select coalesce(sum(amount), 0) from credit_history where type = 'PROVISION')
            """, Long.class)).as("credits in existence minus credits provisioned").isZero();
    }

    // ---- get-or-create (D6) ----

    @Test
    void getOrCreateGrantsTheSignUpCreditsOnce() {
        CreditAccount created = operations.getOrCreate(requester);
        assertThat(created.getAvailable()).isEqualTo(5);
        assertThat(created.getReserved()).isZero();

        operations.getOrCreate(requester);

        assertBalance(requester, 5, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");
        assertThat(lotsOf(requester)).containsExactly("5 never");
    }

    @RepeatedTest(RACE_REPETITIONS)
    void concurrentFirstSightsOfAUserGrantTheSignUpCreditsOnce() throws Exception {
        List<Object> results = race(
            () -> operations.getOrCreate(requester).getAvailable(),
            () -> operations.getOrCreate(requester).getAvailable(),
            () -> operations.getOrCreate(requester).getAvailable(),
            () -> operations.getOrCreate(requester).getAvailable());

        assertThat(results).containsOnly(5);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");
    }

    // ---- reserve (D8) ----

    @Test
    void reserveMovesTheRewardFromAvailableToReserved() {
        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.RESERVED);

        assertBalance(requester, 2, 3);
        Reserved held = reservations.findById(request).orElseThrow();
        assertThat(held.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(held.getRequesterId()).isEqualTo(requester);
        assertThat(held.getAmount()).isEqualTo(3);
        assertThat(held.getSettledAt()).isNull();
        assertThat(lotsOf(requester)).containsExactly("2 never");
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3");
    }

    @Test
    void reserveOfTheWholeBalanceKeepsTheDrainedLot() {
        assertThat(operations.reserve(request, requester, 5)).isEqualTo(ReserveResult.RESERVED);

        assertBalance(requester, 0, 5);
        assertThat(lotsOf(requester)).containsExactly("0 never");
    }

    @Test
    void reserveAboveTheAvailableBalanceIsRefusedAndRecorded() {
        assertThat(operations.reserve(request, requester, 6)).isEqualTo(ReserveResult.REJECTED_INSUFFICIENT_CREDITS);

        // the account the record references is created; no history row for the refusal
        assertBalance(requester, 5, 0);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.REJECTED);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");
    }

    @Test
    void reserveBelowOneIsRefusedWhateverTheBalance() {
        String negative = request + "-negative";

        assertThat(operations.reserve(request, requester, 0)).isEqualTo(ReserveResult.REJECTED_INVALID_AMOUNT);
        assertThat(operations.reserve(negative, requester, -2)).isEqualTo(ReserveResult.REJECTED_INVALID_AMOUNT);

        assertBalance(requester, 5, 0);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.REJECTED);
        assertThat(statusOf(negative)).isEqualTo(ReservationStatus.REJECTED);

        // a redelivery answers the same refusal
        assertThat(operations.reserve(request, requester, 0))
            .isEqualTo(ReserveResult.ALREADY_REJECTED_INVALID_AMOUNT);
        assertBalance(requester, 5, 0);
    }

    @Test
    void reserveAgainOnceHeldAnswersAlreadyReserved() {
        operations.reserve(request, requester, 3);

        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.ALREADY_RESERVED);

        assertBalance(requester, 2, 3);
    }

    @Test
    void aRefusedRequestStaysRefused() {
        String other = request + "-other";
        operations.reserve(other, requester, 4);
        operations.reserve(request, requester, 3);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.REJECTED);

        // the balance grows back, but the record stands
        operations.release(other, requester);

        assertThat(operations.reserve(request, requester, 3))
            .isEqualTo(ReserveResult.ALREADY_REJECTED_INSUFFICIENT_CREDITS);
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.REJECTED);
        assertBalance(requester, 5, 0);
    }

    @Test
    void reserveSpendsTheSoonestExpiryFirstAndTheSignUpLotLast() {
        // the courier earns 3 credits that expire, on top of the 5 that never do
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String spending = request + "-spending";

        assertThat(operations.reserve(spending, courier, 4)).isEqualTo(ReserveResult.RESERVED);

        assertThat(lotsOf(courier)).containsExactly("0 expiring", "4 never");
        assertThat(jdbc.queryForList("""
            select amount || case when expires_at is null then ' never' else ' expiring' end
            from credit_reservation_slice where request_ref = ? order by id
            """, String.class, spending)).containsExactly("3 expiring", "1 never");
    }

    // ---- transfer (D9) ----

    @Test
    void transferMovesTheHeldCreditsToTheCourier() {
        operations.reserve(request, requester, 3);

        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 2, 0);
        // the courier's account is created by the transfer
        assertBalance(courier, 8, 0);
        Reserved transferred = reservations.findById(request).orElseThrow();
        assertThat(transferred.getStatus()).isEqualTo(ReservationStatus.TRANSFERRED);
        assertThat(transferred.getCourierId()).isEqualTo(courier);
        assertThat(transferred.getSettledAt()).isNotNull();
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "TRANSFER_OUT 3");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 3");

        // the earned credits are a lot of their own, expiring 3 months after they were earned
        Instant earnedAt = jdbc.queryForObject(
            "select earned_at from credit_lot where owner_id = ? and expires_at is not null",
            (rs, row) -> rs.getTimestamp(1).toInstant(), courier);
        Instant expiresAt = jdbc.queryForObject(
            "select expires_at from credit_lot where owner_id = ? and expires_at is not null",
            (rs, row) -> rs.getTimestamp(1).toInstant(), courier);
        assertThat(expiresAt).isEqualTo(earnedAt.atOffset(ZoneOffset.UTC).plusMonths(3).toInstant());
        assertThat(lotsOf(courier)).containsExactly("3 expiring", "5 never");
    }

    @Test
    void transferAgainIsADuplicateForTheSameCourierAndInvalidForAnother() {
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String another = "u-other-" + UUID.randomUUID();

        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.DUPLICATE);
        assertThat(operations.transfer(request, another)).isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
        assertThat(accounts.findById(another)).isEmpty();
    }

    @Test
    void transferToTheRequesterThemselvesReturnsTheCreditsAsEarned() {
        // the Order Service never sends this; refusing it would strand the held credits
        operations.reserve(request, requester, 3);

        assertThat(operations.transfer(request, requester)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 5, 0);
        assertThat(lotsOf(requester)).containsExactly("3 expiring", "2 never");
        assertThat(historyOf(requester))
            .containsExactly("PROVISION 5", "RESERVE 3", "TRANSFER_OUT 3", "TRANSFER_IN 3");
        assertThat(operations.transfer(request, requester)).isEqualTo(SettleResult.DUPLICATE);
    }

    @Test
    void transferWithNoRecordFindsNoReservation() {
        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.NO_RESERVATION);

        // nothing is written, unlike a release with no record
        assertThat(reservations.findById(request)).isEmpty();
        assertThat(accounts.findById(courier)).isEmpty();
    }

    @Test
    void transferNamingNoCourierIsInvalidWhateverTheRecord() {
        String held = request + "-held";
        operations.reserve(held, requester, 3);

        // with no record it is not NO_RESERVATION: a retry could never succeed
        assertThat(operations.transfer(request, null)).as("no record, null").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.transfer(request, " ")).as("no record, blank").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.transfer(held, null)).as("HELD, null").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.transfer(held, "")).as("HELD, blank").isEqualTo(SettleResult.INVALID_STATE);

        assertThat(reservations.findById(request)).isEmpty();
        assertThat(statusOf(held)).isEqualTo(ReservationStatus.HELD);
        assertBalance(requester, 2, 3);
    }

    @Test
    void transferOfAReleasedOrRejectedRecordIsInvalid() {
        String rejected = request + "-rejected";
        String released = request + "-released";
        operations.reserve(rejected, requester, 6);
        operations.reserve(released, requester, 3);
        operations.release(released, requester);

        assertThat(operations.transfer(rejected, courier)).as("REJECTED").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.transfer(released, courier)).as("RELEASED").isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 5, 0);
        assertThat(accounts.findById(courier)).isEmpty();
    }

    // ---- release (D9) ----

    @Test
    void releaseReturnsTheHeldCreditsToTheRequester() {
        operations.reserve(request, requester, 3);

        assertThat(operations.release(request, requester)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 5, 0);
        Reserved released = reservations.findById(request).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(released.getSettledAt()).isNotNull();
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "RELEASE 3");
    }

    @Test
    void releaseReturnsEachSliceToTheLotItCameFrom() {
        // the courier holds 3 expiring credits and 5 that never expire, and reserves 4 of them
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String spending = request + "-spending";
        List<Long> lotIds = lotIdsOf(courier);
        operations.reserve(spending, courier, 4);

        assertThat(operations.release(spending, courier)).isEqualTo(SettleResult.SETTLED);

        // the same two rows, refilled: the drained expiring lot included
        assertBalance(courier, 8, 0);
        assertThat(lotsOf(courier)).containsExactly("3 expiring", "5 never");
        assertThat(lotIdsOf(courier)).isEqualTo(lotIds);
    }

    @Test
    void reservingAndReleasingAgainAndAgainAddsNoLots() {
        for (int i = 0; i < 5; i++) {
            operations.reserve(request + "-" + i, requester, 2);
            operations.release(request + "-" + i, requester);
        }

        assertBalance(requester, 5, 0);
        assertThat(lotsOf(requester)).containsExactly("5 never");
    }

    @Test
    void releaseReturnsCreditsToALotThatHasPassedItsExpiry() {
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String spending = request + "-spending";
        operations.reserve(spending, courier, 3);
        // the earned lot expires while its credits are held
        jdbc.update("update credit_lot set expires_at = now() - interval '1 day'"
            + " where owner_id = ? and expires_at is not null", courier);

        assertThat(operations.release(spending, courier)).isEqualTo(SettleResult.SETTLED);

        // back in the expired lot, for the next expiry run to move to the pool
        assertBalance(courier, 8, 0);
        assertThat(lotsOf(courier)).containsExactly("3 expiring", "5 never");
    }

    @Test
    void releaseGoesToTheRequesterOnTheRecordNotTheOneTheEventNames() {
        operations.reserve(request, requester, 3);
        String someoneElse = "u-other-" + UUID.randomUUID();

        assertThat(operations.release(request, someoneElse)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 5, 0);
        assertThat(accounts.findById(someoneElse)).isEmpty();
    }

    @Test
    void releaseNamingNoRequesterIsInvalidOnlyWithNoRecord() {
        assertThat(operations.release(request, null)).as("no record, null").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.release(request, " ")).as("no record, blank").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(reservations.findById(request)).isEmpty();

        // with a record, the requester is the one on it
        operations.reserve(request, requester, 3);
        assertThat(operations.release(request, null)).as("HELD, null").isEqualTo(SettleResult.SETTLED);
        assertBalance(requester, 5, 0);
    }

    @Test
    void releaseBeforeTheReservationRecordsItSoALateReserveIsIgnored() {
        assertThat(operations.release(request, requester)).isEqualTo(SettleResult.SETTLED);

        // the requester's account is created; the amount-0 record writes no RELEASE row
        Reserved tombstone = reservations.findById(request).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(tombstone.getAmount()).isZero();
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");

        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.DUPLICATE);
        assertBalance(requester, 5, 0);
    }

    @Test
    void releaseAgainOrOfARefusedRequestIsADuplicate() {
        String rejected = request + "-rejected";
        operations.reserve(rejected, requester, 6);
        operations.reserve(request, requester, 3);
        operations.release(request, requester);

        assertThat(operations.release(request, requester)).as("RELEASED").isEqualTo(SettleResult.DUPLICATE);
        assertThat(operations.release(rejected, requester)).as("REJECTED").isEqualTo(SettleResult.DUPLICATE);

        assertBalance(requester, 5, 0);
        assertThat(statusOf(rejected)).isEqualTo(ReservationStatus.REJECTED);
    }

    @Test
    void releaseAfterTheTransferIsInvalid() {
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);

        assertThat(operations.release(request, requester)).isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
    }

    // ---- resolve (DSP-4, DSP-8, DSP-13) ----

    @Test
    void resolveWithTheWholeRewardToTheRequesterRefundsIt() {
        operations.reserve(request, requester, 3);
        // accepting an order never touches Credit, so the courier may have no account
        assertThat(accounts.findById(courier)).isEmpty();

        assertThat(operations.resolve(request, courier, 0, 3)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 5, 0);
        // the courier is on the record even with nothing, so their account is created
        assertBalance(courier, 5, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "RELEASE 3");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5");
        assertThat(lotsOf(courier)).containsExactly("5 never");
        assertResolved(courier, 0);
    }

    @Test
    void resolveWithTheWholeRewardToTheCourierPaysIt() {
        operations.reserve(request, requester, 3);

        assertThat(operations.resolve(request, courier, 3, 0)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "TRANSFER_OUT 3");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 3");
        assertThat(lotsOf(courier)).containsExactly("3 expiring", "5 never");
        assertResolved(courier, 3);
    }

    @Test
    void resolveWithASplitPaysOnePartAndRefundsTheOther() {
        operations.reserve(request, requester, 3);

        assertThat(operations.resolve(request, courier, 1, 2)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
        assertThat(historyOf(requester))
            .containsExactly("PROVISION 5", "RESERVE 3", "TRANSFER_OUT 1", "RELEASE 2");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "TRANSFER_IN 1");
        assertThat(lotsOf(requester)).containsExactly("4 never");
        assertThat(lotsOf(courier)).containsExactly("1 expiring", "5 never");
        assertResolved(courier, 1);
    }

    @Test
    void resolveRefundsTheLatestExpiringSlicesFirst() {
        // the courier holds 3 expiring credits and 5 that never expire, and
        // reserves 4: 3 from the expiring lot, then 1 from the sign-up lot
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String disputed = request + "-disputed";
        String other = "u-other-" + UUID.randomUUID();
        operations.reserve(disputed, courier, 4);

        // 2 back: the never-expiring 1 first, then 1 of the expiring 3
        assertThat(operations.resolve(disputed, other, 2, 2)).isEqualTo(SettleResult.SETTLED);

        assertBalance(courier, 6, 0);
        assertThat(lotsOf(courier)).containsExactly("1 expiring", "5 never");
        assertBalance(other, 7, 0);
    }

    @Test
    void resolveRefundsIntoALotThatHasPassedItsExpiry() {
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);
        String disputed = request + "-disputed";
        String other = "u-other-" + UUID.randomUUID();
        operations.reserve(disputed, courier, 3);
        // the earned lot expires while its credits are held
        jdbc.update("update credit_lot set expires_at = now() - interval '1 day'"
            + " where owner_id = ? and expires_at is not null", courier);

        assertThat(operations.resolve(disputed, other, 1, 2)).isEqualTo(SettleResult.SETTLED);

        // back in the expired lot, for the next expiry run to move to the pool, as on a release
        assertBalance(courier, 7, 0);
        assertThat(lotsOf(courier)).containsExactly("2 expiring", "5 never");
    }

    @Test
    void resolveToTheRequesterThemselvesKeepsTheBooksBalanced() {
        // the Order Service never sends this; the one account is locked as for a transfer
        operations.reserve(request, requester, 3);

        assertThat(operations.resolve(request, requester, 1, 2)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 5, 0);
        assertThat(lotsOf(requester)).containsExactly("1 expiring", "4 never");
    }

    @Test
    void resolveAgainIsADuplicateOnlyWithTheSameCourierAndSplit() {
        operations.reserve(request, requester, 3);
        operations.resolve(request, courier, 1, 2);
        String another = "u-other-" + UUID.randomUUID();

        assertThat(operations.resolve(request, courier, 1, 2)).as("same").isEqualTo(SettleResult.DUPLICATE);
        assertThat(operations.resolve(request, another, 1, 2)).as("another courier")
            .isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.resolve(request, courier, 2, 1)).as("another split")
            .isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.resolve(request, courier, 1, 1)).as("not adding up")
            .isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
        assertThat(accounts.findById(another)).isEmpty();
        assertResolved(courier, 1);
    }

    @Test
    void resolveAgainWithAZeroShareIsADuplicate() {
        operations.reserve(request, requester, 3);
        operations.resolve(request, courier, 0, 3);

        assertThat(operations.resolve(request, courier, 0, 3)).isEqualTo(SettleResult.DUPLICATE);

        assertBalance(requester, 5, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "RELEASE 3");
    }

    @Test
    void resolveWhoseSharesDoNotAddUpToTheRewardChangesNothing() {
        operations.reserve(request, requester, 3);

        assertThat(operations.resolve(request, courier, 1, 1)).as("short").isEqualTo(SettleResult.INVALID_STATE);
        assertThat(operations.resolve(request, courier, 3, 1)).as("over").isEqualTo(SettleResult.INVALID_STATE);

        assertThat(statusOf(request)).isEqualTo(ReservationStatus.HELD);
        assertBalance(requester, 2, 3);
        assertThat(accounts.findById(courier)).isEmpty();
    }

    @Test
    void resolveMissingAFieldIsInvalidWhateverTheRecord() {
        String held = request + "-held";
        operations.reserve(held, requester, 3);

        // with no record it is not NO_RESERVATION: a retry could never succeed
        for (String requestRef : List.of(request, held)) {
            assertThat(operations.resolve(requestRef, null, 1, 2)).as("null courier")
                .isEqualTo(SettleResult.INVALID_STATE);
            assertThat(operations.resolve(requestRef, " ", 1, 2)).as("blank courier")
                .isEqualTo(SettleResult.INVALID_STATE);
            assertThat(operations.resolve(requestRef, courier, null, 3)).as("null courier share")
                .isEqualTo(SettleResult.INVALID_STATE);
            assertThat(operations.resolve(requestRef, courier, 3, null)).as("null requester share")
                .isEqualTo(SettleResult.INVALID_STATE);
            // adds up to the reward, but a share is never negative (DSP-8)
            assertThat(operations.resolve(requestRef, courier, -1, 4)).as("negative share")
                .isEqualTo(SettleResult.INVALID_STATE);
        }

        assertThat(reservations.findById(request)).isEmpty();
        assertThat(statusOf(held)).isEqualTo(ReservationStatus.HELD);
        assertBalance(requester, 2, 3);
        assertThat(accounts.findById(courier)).isEmpty();
    }

    @Test
    void resolveWithNoRecordFindsNoReservation() {
        assertThat(operations.resolve(request, courier, 1, 2)).isEqualTo(SettleResult.NO_RESERVATION);

        assertThat(reservations.findById(request)).isEmpty();
        assertThat(accounts.findById(courier)).isEmpty();
    }

    // The event table's request.dispute-resolved row and RESOLVED column,
    // one cell each: an event matching what happened to the reservation is
    // a duplicate, one contradicting it is INVALID_STATE.

    @Test
    void resolveOfARejectedRecordIsInvalid() {
        operations.reserve(request, requester, 6);

        assertThat(operations.resolve(request, courier, 0, 6)).isEqualTo(SettleResult.INVALID_STATE);

        assertThat(statusOf(request)).isEqualTo(ReservationStatus.REJECTED);
        assertBalance(requester, 5, 0);
        assertThat(accounts.findById(courier)).isEmpty();
    }

    @Test
    void resolveOfAReleasedRecordIsInvalid() {
        operations.reserve(request, requester, 3);
        operations.release(request, requester);

        assertThat(operations.resolve(request, courier, 0, 3)).isEqualTo(SettleResult.INVALID_STATE);

        assertThat(statusOf(request)).isEqualTo(ReservationStatus.RELEASED);
        assertBalance(requester, 5, 0);
        assertThat(accounts.findById(courier)).isEmpty();
    }

    @Test
    void resolveOfATransferredRecordIsInvalid() {
        operations.reserve(request, requester, 3);
        operations.transfer(request, courier);

        // the same courier and the whole reward still contradicts a transfer
        assertThat(operations.resolve(request, courier, 3, 0)).isEqualTo(SettleResult.INVALID_STATE);

        assertThat(statusOf(request)).isEqualTo(ReservationStatus.TRANSFERRED);
        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
    }

    @Test
    void reserveOfAResolvedRecordIsADuplicate() {
        operations.reserve(request, requester, 3);
        operations.resolve(request, courier, 1, 2);

        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.DUPLICATE);

        assertBalance(requester, 4, 0);
        assertResolved(courier, 1);
    }

    @Test
    void transferOfAResolvedRecordIsInvalid() {
        operations.reserve(request, requester, 3);
        operations.resolve(request, courier, 1, 2);

        // the same courier too: a completion contradicts a resolution
        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
        assertResolved(courier, 1);
    }

    // request.cancelled and request.expired both release
    @Test
    void releaseOfAResolvedRecordIsInvalid() {
        operations.reserve(request, requester, 3);
        operations.resolve(request, courier, 1, 2);

        assertThat(operations.release(request, requester)).isEqualTo(SettleResult.INVALID_STATE);

        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
        assertResolved(courier, 1);
    }

    // ---- the record insert (D3) ----

    // The first draft saved the record, which merged over an existing one.
    // No threads: the second insert is the second of two first deliveries.
    @Test
    void aSecondInsertOfARequestsRecordFailsAndLeavesTheFirst() {
        operations.getOrCreate(requester);
        Instant now = Instant.now();
        transaction.executeWithoutResult(status ->
            reservations.insert(new Reserved(request, requester, 3, ReservationStatus.REJECTED, now)));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
            reservations.insert(new Reserved(request, requester, 4, ReservationStatus.RELEASED, now))))
            .isInstanceOf(DataIntegrityViolationException.class);

        Reserved kept = reservations.findById(request).orElseThrow();
        assertThat(kept.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(kept.getAmount()).isEqualTo(3);
    }

    // ---- races ("Failure and retry behaviour") ----

    @RepeatedTest(RACE_REPETITIONS)
    void twoReservationsRacingPastTheBalanceReserveOnce() throws Exception {
        List<Object> results = race(
            () -> operations.reserve(request + "-a", requester, 3),
            () -> operations.reserve(request + "-b", requester, 3));

        assertThat(results).containsExactlyInAnyOrder(
            ReserveResult.RESERVED, ReserveResult.REJECTED_INSUFFICIENT_CREDITS);
        assertBalance(requester, 2, 3);
    }

    @RepeatedTest(RACE_REPETITIONS)
    void twoFirstDeliveriesOfOneRequestReserveOnce() throws Exception {
        List<Object> results = race(
            () -> operations.reserve(request, requester, 3),
            () -> operations.reserve(request, requester, 3));

        // the loser either saw the record, or failed on its primary key and would be retried
        assertThat(results).filteredOn(ReserveResult.RESERVED::equals).hasSize(1);
        assertThat(results).filteredOn(r -> !ReserveResult.RESERVED.equals(r)).allSatisfy(loser ->
            assertThat(loser).satisfiesAnyOf(
                r -> assertThat(r).isEqualTo(ReserveResult.ALREADY_RESERVED),
                r -> assertThat(r).isInstanceOf(RuntimeException.class)));
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.HELD);
        assertBalance(requester, 2, 3);
        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.ALREADY_RESERVED);
    }

    @RepeatedTest(RACE_REPETITIONS)
    void twoDeliveriesOfOneCompletionPayOnce() throws Exception {
        operations.reserve(request, requester, 3);

        List<Object> results = race(
            () -> operations.transfer(request, courier),
            () -> operations.transfer(request, courier));

        assertThat(results).containsExactlyInAnyOrder(SettleResult.SETTLED, SettleResult.DUPLICATE);
        assertBalance(requester, 2, 0);
        assertBalance(courier, 8, 0);
    }

    // a completion racing a cancellation or expiry of the same request:
    // both lock the record first, so one settles it and the other finds it settled
    @RepeatedTest(RACE_REPETITIONS)
    void aTransferRacingAReleaseOfOneRequestSettlesItOnce() throws Exception {
        operations.reserve(request, requester, 3);

        List<Object> results = race(
            () -> operations.transfer(request, courier),
            () -> operations.release(request, requester));

        assertThat(results).containsExactlyInAnyOrder(SettleResult.SETTLED, SettleResult.INVALID_STATE);
        if (statusOf(request) == ReservationStatus.TRANSFERRED) {
            assertThat(results).containsExactly(SettleResult.SETTLED, SettleResult.INVALID_STATE);
            assertBalance(requester, 2, 0);
            assertBalance(courier, 8, 0);
        } else {
            assertThat(statusOf(request)).isEqualTo(ReservationStatus.RELEASED);
            assertThat(results).containsExactly(SettleResult.INVALID_STATE, SettleResult.SETTLED);
            // the losing transfer never got as far as the courier's account
            assertBalance(requester, 5, 0);
            assertThat(accounts.findById(courier)).isEmpty();
        }
    }

    @RepeatedTest(RACE_REPETITIONS)
    void twoDeliveriesOfOneResolutionSettleOnce() throws Exception {
        operations.reserve(request, requester, 3);

        List<Object> results = race(
            () -> operations.resolve(request, courier, 1, 2),
            () -> operations.resolve(request, courier, 1, 2));

        assertThat(results).containsExactlyInAnyOrder(SettleResult.SETTLED, SettleResult.DUPLICATE);
        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
    }

    @RepeatedTest(RACE_REPETITIONS)
    void transfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        String back = request + "-back";
        operations.reserve(request, requester, 3);
        operations.reserve(back, courier, 2);

        List<Object> results = race(
            () -> operations.transfer(request, courier),
            () -> operations.transfer(back, requester));

        assertThat(results).containsExactly(SettleResult.SETTLED, SettleResult.SETTLED);
        assertBalance(requester, 4, 0);
        assertBalance(courier, 6, 0);
    }

    // a resolution locks both accounts in the same order as a transfer (D2)
    @RepeatedTest(RACE_REPETITIONS)
    void resolutionsInOppositeDirectionsDoNotDeadlock() throws Exception {
        String back = request + "-back";
        operations.reserve(request, requester, 3);
        operations.reserve(back, courier, 2);

        List<Object> results = race(
            () -> operations.resolve(request, courier, 1, 2),
            () -> operations.resolve(back, requester, 1, 1));

        assertThat(results).containsExactly(SettleResult.SETTLED, SettleResult.SETTLED);
        assertBalance(requester, 5, 0);
        assertBalance(courier, 5, 0);
    }

    @RepeatedTest(RACE_REPETITIONS)
    void aResolutionAndATransferInOppositeDirectionsDoNotDeadlock() throws Exception {
        String back = request + "-back";
        operations.reserve(request, requester, 3);
        operations.reserve(back, courier, 2);

        List<Object> results = race(
            () -> operations.resolve(request, courier, 1, 2),
            () -> operations.transfer(back, requester));

        assertThat(results).containsExactly(SettleResult.SETTLED, SettleResult.SETTLED);
        assertBalance(requester, 6, 0);
        assertBalance(courier, 4, 0);
    }

    // ---- open and close (issue #267) ----

    @Test
    void openCreatesTheAccountWithTheSignUpCreditsOnce() {
        assertThat(operations.open(requester)).isEqualTo(AccountResult.CREATED);
        assertThat(operations.open(requester)).isEqualTo(AccountResult.DUPLICATE);
        // get-or-create finds it as the event left it
        operations.getOrCreate(requester);

        assertBalance(requester, 5, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");
    }

    @Test
    void openAfterGetOrCreateIsADuplicate() {
        operations.getOrCreate(requester);

        assertThat(operations.open(requester)).isEqualTo(AccountResult.DUPLICATE);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5");
    }

    @Test
    void closeMovesTheAvailableCreditsToThePoolAndKeepsTheHeldOnes() {
        operations.reserve(request, requester, 2);
        int poolBefore = poolBalance();

        assertThat(operations.close(requester)).isEqualTo(AccountResult.CLOSED);

        // the sign-up credits included; the held 2 stay held
        assertBalance(requester, 0, 2);
        assertThat(poolBalance()).isEqualTo(poolBefore + 3);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 2", "CLOSURE 3");
        assertThat(accounts.findById(requester).orElseThrow().isClosed()).isTrue();
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.HELD);
    }

    @Test
    void closeAgainIsADuplicate() {
        operations.getOrCreate(requester);
        operations.close(requester);
        int poolBefore = poolBalance();

        assertThat(operations.close(requester)).isEqualTo(AccountResult.DUPLICATE);

        assertThat(poolBalance()).isEqualTo(poolBefore);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "CLOSURE 5");
    }

    @Test
    void closeBeforeTheAccountExistsLeavesAClosedPlaceholderNothingCanFund() {
        assertThat(operations.close(requester)).isEqualTo(AccountResult.CLOSED_PLACEHOLDER);

        // a late user.created, a get-or-create and a reserve grant nothing
        assertThat(operations.open(requester)).isEqualTo(AccountResult.IGNORED_CLOSED);
        CreditAccount account = operations.getOrCreate(requester);
        assertThat(account.isClosed()).isTrue();
        assertThat(operations.reserve(request, requester, 1)).isEqualTo(ReserveResult.REJECTED_INSUFFICIENT_CREDITS);

        assertBalance(requester, 0, 0);
        assertThat(historyOf(requester)).isEmpty();
        assertThat(lotsOf(requester)).isEmpty();
    }

    @Test
    void openAfterCloseIsIgnoredAndNeverReopens() {
        operations.open(requester);
        operations.close(requester);

        assertThat(operations.open(requester)).isEqualTo(AccountResult.IGNORED_CLOSED);

        assertThat(accounts.findById(requester).orElseThrow().isClosed()).isTrue();
        assertBalance(requester, 0, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "CLOSURE 5");
    }

    @Test
    void aReleaseToAClosedAccountGoesOnToThePool() {
        operations.reserve(request, requester, 2);
        operations.close(requester);
        int poolBefore = poolBalance();

        assertThat(operations.release(request, requester)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 0, 0);
        assertThat(poolBalance()).isEqualTo(poolBefore + 2);
        assertThat(historyOf(requester))
            .containsExactly("PROVISION 5", "RESERVE 2", "CLOSURE 3", "RELEASE 2", "CLOSURE 2");
        assertThat(lotsOf(requester)).containsExactly("0 never");
    }

    @Test
    void aTransferToAClosedCourierGoesOnToThePool() {
        operations.getOrCreate(courier);
        operations.close(courier);
        operations.reserve(request, requester, 3);
        int poolBefore = poolBalance();

        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 2, 0);
        assertBalance(courier, 0, 0);
        assertThat(poolBalance()).isEqualTo(poolBefore + 3);
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "CLOSURE 5", "TRANSFER_IN 3", "CLOSURE 3");
    }

    @Test
    void aClosedRequestersHeldCreditsStillReachAnOpenCourier() {
        operations.reserve(request, requester, 3);
        operations.close(requester);

        assertThat(operations.transfer(request, courier)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 0, 0);
        assertBalance(courier, 8, 0);
        assertThat(historyOf(requester)).containsExactly("PROVISION 5", "RESERVE 3", "CLOSURE 2", "TRANSFER_OUT 3");
    }

    @Test
    void bothSharesOfAResolutionToClosedAccountsGoOnToThePool() {
        operations.getOrCreate(courier);
        operations.reserve(request, requester, 4);
        operations.close(requester);
        operations.close(courier);
        int poolBefore = poolBalance();

        assertThat(operations.resolve(request, courier, 3, 1)).isEqualTo(SettleResult.SETTLED);

        assertBalance(requester, 0, 0);
        assertBalance(courier, 0, 0);
        assertThat(poolBalance()).isEqualTo(poolBefore + 4);
        assertThat(historyOf(requester))
            .containsExactly("PROVISION 5", "RESERVE 4", "CLOSURE 1", "TRANSFER_OUT 3", "RELEASE 1", "CLOSURE 1");
        assertThat(historyOf(courier)).containsExactly("PROVISION 5", "CLOSURE 5", "TRANSFER_IN 3", "CLOSURE 3");
    }

    @RepeatedTest(RACE_REPETITIONS)
    void closeRacingAFirstGetOrCreateNeverLeavesCreditsOnAClosedAccount() throws Exception {
        List<Object> results = race(
            () -> operations.close(requester),
            () -> operations.getOrCreate(requester));

        assertThat(results).noneMatch(Exception.class::isInstance);
        // whichever ran first, the account ends closed and empty
        CreditAccount account = accounts.findById(requester).orElseThrow();
        assertThat(account.isClosed()).isTrue();
        assertBalance(requester, 0, 0);
    }

    // ---- helpers ----

    private int poolBalance() {
        return jdbc.queryForObject("select balance from common_pool", Integer.class);
    }

    private void assertBalance(String userId, int available, int reserved) {
        CreditAccount account = accounts.findById(userId).orElseThrow();
        assertThat(List.of(account.getAvailable(), account.getReserved()))
            .as("available and reserved of " + userId)
            .containsExactly(available, reserved);
    }

    private void assertResolved(String courierId, int courierShare) {
        Reserved resolved = reservations.findById(request).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ReservationStatus.RESOLVED);
        assertThat(resolved.getCourierId()).isEqualTo(courierId);
        assertThat(resolved.getCourierShare()).isEqualTo(courierShare);
        assertThat(resolved.getSettledAt()).isNotNull();
    }

    private ReservationStatus statusOf(String requestRef) {
        return reservations.findById(requestRef).orElseThrow().getStatus();
    }

    private List<String> historyOf(String userId) {
        return jdbc.queryForList(
            "select type || ' ' || amount from credit_history where user_id = ? order by id", String.class, userId);
    }

    private List<Long> lotIdsOf(String userId) {
        return jdbc.queryForList("select id from credit_lot where owner_id = ? order by id", Long.class, userId);
    }

    // in spend order, drained lots included
    private List<String> lotsOf(String userId) {
        return jdbc.queryForList("""
            select remaining || case when expires_at is null then ' never' else ' expiring' end
            from credit_lot where owner_id = ? order by expires_at asc nulls last, id asc
            """, String.class, userId);
    }

    // Starts the calls together and returns each one's result, or the exception it threw
    private static List<Object> race(Callable<?>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return call.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
