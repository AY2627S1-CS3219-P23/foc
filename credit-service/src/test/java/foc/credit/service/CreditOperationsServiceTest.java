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
                    when h.type in ('RESERVE', 'EXPIRY') then -h.amount
                    else 0 end), 0)
                or a.reserved <> coalesce(sum(case
                    when h.type = 'RESERVE' then h.amount
                    when h.type in ('RELEASE', 'TRANSFER_OUT') then -h.amount
                    else 0 end), 0)
            """)).as("accounts whose balances differ from their history").isEmpty();
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
    }

    @Test
    void reserveIsADuplicateOnceHeld() {
        operations.reserve(request, requester, 3);

        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.DUPLICATE);

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

        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.DUPLICATE);
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
    void transferWithNothingHeldIsInvalid() {
        String rejected = request + "-rejected";
        String released = request + "-released";
        operations.reserve(rejected, requester, 6);
        operations.reserve(released, requester, 3);
        operations.release(released, requester);

        assertThat(operations.transfer(request, courier)).as("no record").isEqualTo(SettleResult.INVALID_STATE);
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
                r -> assertThat(r).isEqualTo(ReserveResult.DUPLICATE),
                r -> assertThat(r).isInstanceOf(RuntimeException.class)));
        assertThat(statusOf(request)).isEqualTo(ReservationStatus.HELD);
        assertBalance(requester, 2, 3);
        assertThat(operations.reserve(request, requester, 3)).isEqualTo(ReserveResult.DUPLICATE);
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

    // ---- helpers ----

    private void assertBalance(String userId, int available, int reserved) {
        CreditAccount account = accounts.findById(userId).orElseThrow();
        assertThat(List.of(account.getAvailable(), account.getReserved()))
            .as("available and reserved of " + userId)
            .containsExactly(available, reserved);
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
